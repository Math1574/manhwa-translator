package app.manhwa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.manhwa.core.BrowsingLibrary
import app.manhwa.core.CoverBook
import app.manhwa.core.CoverCandidates
import app.manhwa.core.CoverImg
import app.manhwa.core.CoverPick
import app.manhwa.core.SeriesEntry
import app.manhwa.core.Sites
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Illustrations des œuvres, rangées sur le téléphone (un petit JPEG par œuvre) avec leur registre. Rien n'est inventé : l'image est celle que la page de l'œuvre annonce
 * pour la représenter ; sans elle, l'œuvre garde sa pastille colorée.
 */
class CoverStore(private val dir: File) {
    /** Registre des illustrations. C'est un état : les écrans qui l'ont lu se redessinent quand une illustration arrive. */
    var book by mutableStateOf(CoverBook.parse(try { File(dir, "index.tsv").takeIf { it.exists() }?.readText() } catch (_: Throwable) { null }))
        private set
    private val memory = LruCache<String, ImageBitmap>(32)

    private fun file(key: String) = File(dir, CoverPick.fileKey(key) + ".jpg")
    private fun writeIndex() { try { dir.mkdirs(); File(dir, "index.tsv").writeText(book.serialize()) } catch (_: Throwable) {} }

    /** L'illustration de l'œuvre, ou null (pas encore trouvée, ou fichier illisible). Fil principal. */
    fun get(key: String): ImageBitmap? {
        if (!book.has(key)) return null
        memory.get(key)?.let { return it }
        val bmp = try { BitmapFactory.decodeFile(file(key).path)?.asImageBitmap() } catch (_: Throwable) { null } ?: return null
        memory.put(key, bmp)
        return bmp
    }

    /** Faut-il la chercher (jamais essayée, ou essai raté il y a assez longtemps) ? */
    fun wanted(key: String): Boolean = book.wanted(key, System.currentTimeMillis())

    /** Range l'illustration trouvée (déjà réduite en JPEG) ; faux si elle est refusée parce que c'est l'image du site et non celle de l'œuvre. Fil principal. */
    fun save(key: String, url: String, jpeg: ByteArray): Boolean {
        val now = System.currentTimeMillis()
        val r = book.saved(key, url, now)
        for (k in r.dropped + key) { memory.remove(k); try { file(k).delete() } catch (_: Throwable) {} }
        var next = r.book
        var kept = r.accepted
        if (kept) {
            try { dir.mkdirs(); file(key).writeBytes(jpeg) } catch (_: Throwable) { kept = false; next = next.failed(key, now) }
        }
        book = next
        writeIndex()
        return kept
    }

    /** Essai sans résultat : l'œuvre sera réessayée plus tard, quelques fois au plus. */
    fun failed(key: String) { book = book.failed(key, System.currentTimeMillis()); writeIndex() }

    /** L'œuvre est oubliée : son illustration aussi. */
    fun forget(key: String) {
        memory.remove(key)
        try { file(key).delete() } catch (_: Throwable) {}
        book = book.forget(key)
        writeIndex()
    }
}

/**
 * Va chercher les illustrations qui manquent, une œuvre à la fois, en tâche de fond (plus lentement pendant une traduction) : la page de l'œuvre est ouverte dans un navigateur caché (sans images : on n'y lit que
 * l'adresse annoncée pour le partage, « og:image »), puis l'image est téléchargée une seule fois et réduite. Comme pour le reste de l'application, tout reste sur le téléphone.
 */
class CoverFetcher(private val ctx: Context, private val store: CoverStore, private val library: () -> BrowsingLibrary, private val busy: () -> Boolean) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private class Wish(val key: String, val title: String)
    private val queue = ArrayDeque<Wish>()
    private val queued = HashSet<String>()
    private var worker: Job? = null
    private var web: WebView? = null
    /** Les délais d'attente passent par le fil principal et non par le navigateur caché : n'étant attaché à aucune fenêtre, ses propres « postDelayed » ne s'exécuteraient jamais. */
    private val handler = Handler(Looper.getMainLooper())
    /** Dernier échec expliqué en quelques mots (pour les tests automatiques) ; vide si la dernière recherche a réussi. */
    @Volatile var lastWhy: String = ""
        private set
    private val userAgent: String by lazy { try { WebSettings.getDefaultUserAgent(ctx) } catch (_: Throwable) { "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36" } }

    /** Demande les illustrations qui manquent, dans l'ordre donné (celui de l'écran). Les œuvres déjà servies, déjà demandées ou récemment essayées sont ignorées. Fil principal. */
    fun request(series: List<SeriesEntry>) {
        for (e in series) if (store.wanted(e.key) && queued.add(e.key)) queue.addLast(Wish(e.key, e.title))
        if (queue.isNotEmpty() && worker?.isActive != true) worker = scope.launch { work() }
    }

    private suspend fun work() {
        try {
            while (true) {
                val w = queue.removeFirstOrNull() ?: break
                // pendant une traduction on ne se presse pas : au plus une illustration toutes les 20 secondes environ
                var waited = 0L
                while (busy() && waited < 20_000L) { delay(2_000L); waited += 2_000L }
                try { fetch(w) } catch (e: CancellationException) { throw e } catch (_: Throwable) { store.failed(w.key) } finally { queued.remove(w.key) }
            }
        } finally { closeWeb() }
    }

    private suspend fun fetch(w: Wish) {
        val urls = library().history.filter { BrowsingLibrary.seriesKey(it.url) == w.key }.map { it.url }
        var why = "aucune page d'œuvre connue"
        for (page in CoverPick.pagesFor(urls, max = 2)) {
            val found = read(page)
            if (found == null) { why = "page illisible ou trop lente"; continue }
            val img = CoverPick.choose(found, w.title)
            if (img == null) { why = "aucune image retenue (partage ${found.shared.size}, données ${found.structured.size}, page ${found.images.size})"; continue }
            val jpeg = withContext(Dispatchers.IO) { download(img, page)?.let { shrink(it) } }
            if (jpeg == null) { why = "image non téléchargée ou inutilisable"; continue }
            lastWhy = if (store.save(w.key, img, jpeg)) "" else "image du site refusée"
            return
        }
        lastWhy = why
        store.failed(w.key)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWeb(): WebView = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.blockNetworkImage = true
        settings.mediaPlaybackRequiresUserGesture = true
    }

    private fun closeWeb() {
        val w = web ?: return
        web = null
        try { w.stopLoading(); w.destroy() } catch (_: Throwable) {}
    }

    /** Ouvre la page dans le navigateur caché et relève ce qu'elle annonce comme illustration ; null si elle ne s'ouvre pas (introuvable, refusée, trop lente). */
    private suspend fun read(page: String): CoverCandidates? {
        val w = web ?: newWeb().also { web = it }
        return withTimeoutOrNull(25_000L) {
            suspendCancellableCoroutine<CoverCandidates?> { cont ->
                var gen = 0
                var status = 0
                var done = false
                fun finish(r: CoverCandidates?) { if (!done) { done = true; if (cont.isActive) cont.resume(r) } }
                w.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { gen++; status = 0 }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) { if (request.isForMainFrame) status = errorResponse.statusCode }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) finish(null) }
                    /** Le processus d'affichage (partagé avec la page lue) a disparu : sans réponse de notre part, Android arrête toute l'application. Ce navigateur est perdu, le suivant sera neuf. */
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { finish(null); closeWeb(); return true }
                    override fun onPageFinished(view: WebView, url: String) {
                        val g = gen
                        // page renvoyée ailleurs (publicité, autre site) : ce qu'elle annonce n'est pas l'illustration de l'œuvre
                        if (!Sites.sameSite(Sites.hostOf(url), Sites.hostOf(page))) { finish(null); return }
                        // une page en erreur (404, 403…) n'annonce rien de fiable ; une page d'attente d'un site protégé se remplace d'elle-même par la vraie : on lui laisse quelques secondes
                        if (status >= 400) { handler.postDelayed(Runnable { if (g == gen) finish(null) }, 8000L); return }
                        handler.postDelayed(Runnable { if (!done && g == gen) view.evaluateJavascript(EXTRACT) { raw -> finish(parse(raw)) } }, 700L)
                    }
                }
                cont.invokeOnCancellation { handler.removeCallbacksAndMessages(null); try { w.stopLoading() } catch (_: Throwable) {} }
                w.loadUrl(page)
                // filet : quand « page chargée » tarde (une ressource lente), ce que la page annonce dans son en-tête est lisible depuis longtemps ; on le relève au bout de 12 s si rien n'a abouti
                handler.postDelayed(Runnable {
                    if (done || status >= 400) return@Runnable
                    if (!Sites.sameSite(Sites.hostOf(w.url), Sites.hostOf(page))) return@Runnable
                    w.evaluateJavascript(EXTRACT) { raw -> finish(parse(raw)) }
                }, 12_000L)
            }
        }
    }

    private fun parse(raw: String?): CoverCandidates? = try {
        val o = JSONObject(raw ?: "")
        fun strings(name: String): List<String> = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } } ?: emptyList()
        val imgs = o.optJSONArray("images")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { CoverImg(it.optString("src"), it.optString("alt"), it.optString("cls"), it.optInt("w"), it.optInt("h")) }
        } ?: emptyList()
        CoverCandidates(strings("shared"), strings("structured"), imgs)
    } catch (_: Throwable) { null }

    /** Image annoncée en « http:// » par une page en « https:// » (réglage fréquent des sites bâtis sur WordPress derrière un relais) : le navigateur la prend en https, l'application aussi, car le clair y est refusé. */
    private fun secure(url: String, page: String): String = if (page.startsWith("https://") && url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    /** Téléchargement séparé de l'image (avec la page comme Referer, puis sans) : 200 et les octets, sinon null. Fil d'arrière-plan. */
    private fun download(src: String, page: String): ByteArray? {
        for (withReferer in listOf(true, false)) {
            var url = secure(src, page)
            var code = 0
            for (hop in 0..5) {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 12_000
                    conn.readTimeout = 20_000
                    conn.setRequestProperty("User-Agent", userAgent)
                    conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    if (withReferer) conn.setRequestProperty("Referer", page)
                    CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
                    code = conn.responseCode
                    if (code in 301..308 && code != 304) {
                        val next = conn.getHeaderField("Location")?.let { URL(URL(url), it).toString() } ?: return null
                        if (!next.startsWith("http://") && !next.startsWith("https://")) return null
                        url = secure(next, page)
                        continue
                    }
                    if (code != 200) break
                    val out = ByteArrayOutputStream()
                    conn.inputStream.use { input ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) return null
                        }
                    }
                    return out.toByteArray()
                } finally {
                    conn.disconnect()
                }
            }
            if (code != 401 && code != 403) return null
        }
        return null
    }

    /** L'image téléchargée, réduite (360 px au plus) et rangée en JPEG ; null si ce n'est pas une illustration plausible (un logo, un bandeau, un pixel) ou si elle est illisible. */
    private fun shrink(bytes: ByteArray): ByteArray? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || !CoverPick.plausible(o.outWidth, o.outHeight)) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = MAX_EDGE.toFloat() / maxOf(bmp.width, bmp.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        val flat = if (small.hasAlpha()) Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888).also { b -> Canvas(b).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(small, 0f, 0f, null) } } else small
        val out = ByteArrayOutputStream()
        return if (flat.compress(Bitmap.CompressFormat.JPEG, 88, out)) out.toByteArray() else null
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_EDGE = 360

        /** Lu dans la page chargée : adresses déjà complètes (le navigateur sait de quelle page elles dépendent). */
        const val EXTRACT = """(function(){
  function abs(u){try{return new URL(u,document.baseURI).href}catch(e){return ''}}
  function metas(sel,attr){var out=[];document.querySelectorAll(sel).forEach(function(m){var c=m.getAttribute(attr)||'';if(c){var a=abs(c);if(a)out.push(a)}});return out}
  var shared=[].concat(metas('meta[property="og:image:secure_url"]','content'),metas('meta[property="og:image"]','content'),metas('meta[name="og:image"]','content'),
    metas('meta[name="twitter:image"]','content'),metas('meta[property="twitter:image"]','content'),metas('meta[name="twitter:image:src"]','content'),metas('link[rel="image_src"]','href'));
  var structured=[];
  document.querySelectorAll('script[type="application/ld+json"]').forEach(function(s){
    try{(function walk(o,d){if(!o||d>6)return;if(Array.isArray(o)){o.forEach(function(x){walk(x,d+1)});return}if(typeof o!=='object')return;
      [o.image,o.thumbnailUrl].forEach(function(im){if(im){(Array.isArray(im)?im:[im]).forEach(function(x){var u=typeof x==='string'?x:(x&&(x.url||x.contentUrl));if(u){var a=abs(u);if(a)structured.push(a)}})}});
      for(var k in o){if(o[k]&&typeof o[k]==='object')walk(o[k],d+1)}})(JSON.parse(s.textContent),0)}catch(e){}
  });
  var images=[];
  document.querySelectorAll('img').forEach(function(im,i){if(i>=60)return;
    var src=im.currentSrc||im.getAttribute('src')||im.getAttribute('data-src')||im.getAttribute('data-lazy-src')||'';
    if(!src||src.indexOf('data:')===0)return;var a=abs(src);if(!a)return;
    var cls=(typeof im.className==='string')?im.className:'';
    images.push({src:a,alt:im.getAttribute('alt')||'',cls:cls,w:im.naturalWidth||im.width||0,h:im.naturalHeight||im.height||0})});
  return {shared:shared.slice(0,6),structured:structured.slice(0,6),images:images};
})()"""
    }
}

/** Illustration d'une œuvre ; sans illustration, une pastille aux couleurs de l'œuvre avec son initiale. */
@Composable
fun CoverTile(graph: AppGraph, key: String, title: String, modifier: Modifier = Modifier) {
    val bmp = graph.covers.get(key)
    Box(modifier.clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, contentDescription = "Illustration de $title", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(seriesColor(key), seriesColor(key + "·")))), contentAlignment = Alignment.Center) {
                Text(title.trim().take(1).uppercase().ifEmpty { "?" }, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.92f))
            }
        }
    }
}
