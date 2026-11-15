package app.manhwa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import app.manhwa.core.AniListCovers
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

    /** Demande de l'utilisateur : une œuvre sans illustration est de nouveau à chercher tout de suite. */
    fun again(key: String) { book = book.again(key); writeIndex() }

    /** L'œuvre est oubliée : son illustration aussi. */
    fun forget(key: String) {
        memory.remove(key)
        try { file(key).delete() } catch (_: Throwable) {}
        book = book.forget(key)
        writeIndex()
    }
}

/**
 * Va chercher les illustrations qui manquent, une œuvre à la fois, en tâche de fond (plus lentement pendant une traduction).
 *  1. La page de l'œuvre, sur le site lu, est ouverte dans un navigateur caché (sans images) : on y relève ce qu'elle annonce comme illustration (« og:image », données structurées, image au titre de l'œuvre),
 *     en attendant le temps qu'il faut aux pages que leur propre script construit ; les images candidates sont essayées de la meilleure à la moins bonne, et téléchargées une seule fois.
 *  2. Sinon, et si le réglage le permet, l'annuaire public AniList : seul le titre de l'œuvre lui est envoyé, et l'image n'est gardée que si l'un des titres de l'annuaire est exactement celui de l'œuvre.
 * Sans résultat, l'œuvre garde sa pastille et sera cherchée de nouveau plus tard (d'abord vite). Chaque essai laisse une ligne au journal (Réglages › Avancé), avec la raison de l'échec.
 */
class CoverFetcher(
    private val ctx: Context,
    private val store: CoverStore,
    private val library: () -> BrowsingLibrary,
    private val busy: () -> Boolean,
    private val online: () -> Boolean = { false },
    private val log: (JSONObject) -> Unit = {},
) {
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
    /** D'où vient la dernière illustration trouvée : « site » ou « annuaire » (tests automatiques). */
    @Volatile var lastSource: String = ""
        private set
    /** Adresse de l'annuaire : celle d'AniList, remplacée par un faux serveur dans les tests automatiques. */
    @Volatile var directory: String = AniListCovers.ENDPOINT
    /** Par site : échecs d'affilée de la page de l'œuvre et heure du dernier ; après quelques échecs, le site est laissé de côté un moment (inutile d'attendre à chaque œuvre une page qui ne s'ouvre pas). */
    private val siteMisses = HashMap<String, Pair<Int, Long>>()
    /** Annuaire en pause (réponse « trop de demandes ») jusqu'à cette heure. */
    @Volatile private var directoryPausedUntil = 0L
    private val userAgent: String by lazy { try { WebSettings.getDefaultUserAgent(ctx) } catch (_: Throwable) { "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36" } }

    /** Demande les illustrations qui manquent, dans l'ordre donné (celui de l'écran). Les œuvres déjà servies, déjà demandées ou récemment essayées sont ignorées. Fil principal. */
    fun request(series: List<SeriesEntry>) {
        for (e in series) if (store.wanted(e.key) && queued.add(e.key)) queue.addLast(Wish(e.key, e.title))
        if (queue.isNotEmpty() && worker?.isActive != true) worker = scope.launch { work() }
    }

    /** Demande de l'utilisateur : ces œuvres sont cherchées tout de suite, même si on avait renoncé ou si l'essai est récent ; celles qui ont déjà leur illustration la gardent. Fil principal. */
    fun retry(series: List<SeriesEntry>) {
        for (e in series) store.again(e.key)
        siteMisses.clear(); directoryPausedUntil = 0L
        request(series)
    }

    private suspend fun work() {
        try {
            while (true) {
                val w = queue.removeFirstOrNull() ?: break
                // pendant une traduction on ne se presse pas : au plus une illustration toutes les 20 secondes environ
                var waited = 0L
                while (busy() && waited < 20_000L) { delay(2_000L); waited += 2_000L }
                try { fetch(w) } catch (e: CancellationException) { throw e } catch (t: Throwable) { lastWhy = "erreur : ${t.javaClass.simpleName}"; store.failed(w.key) } finally { queued.remove(w.key) }
            }
        } finally { closeWeb() }
    }

    private fun siteWorthTrying(host: String): Boolean {
        val m = siteMisses[host] ?: return true
        return m.first < SITE_MISSES_MAX || System.currentTimeMillis() - m.second > SITE_PAUSE_MS
    }

    private fun noteSite(host: String, ok: Boolean) {
        if (ok) siteMisses.remove(host) else { val m = siteMisses[host]; siteMisses[host] = ((m?.first ?: 0) + 1) to System.currentTimeMillis() }
    }

    private fun report(w: Wish, ok: Boolean, source: String, notes: List<String>, page: String?) {
        lastWhy = if (ok) "" else notes.joinToString(" ; ")
        if (ok) lastSource = source
        log(JSONObject().put("t", System.currentTimeMillis()).put("type", "cover").put("series", w.title).put("ok", ok).put("source", source).put("why", notes.joinToString(" ; ")).put("page", page ?: ""))
    }

    /** Télécharge et range l'image [url] pour l'œuvre ; faux (avec la raison dans [notes]) si elle ne se télécharge pas, n'est pas plausible, ou a déjà été donnée à une autre œuvre. */
    private suspend fun keep(w: Wish, url: String, page: String, evidenced: Boolean, label: String, notes: MutableList<String>): Boolean {
        if (url in store.book.banned) { notes.add("$label : image déjà vue pour d'autres œuvres"); return false }
        val jpeg = withContext(Dispatchers.IO) { download(url, page)?.let { shrink(it, evidenced) } }
        if (jpeg == null) { notes.add("$label : image non téléchargée ou inutilisable"); return false }
        if (store.save(w.key, url, jpeg)) return true
        notes.add("$label : image refusée (celle d'une autre œuvre)")
        return false
    }

    /**
     * Cherche l'illustration d'une œuvre, de la plus sûre à la moins sûre :
     *  1. sur la page de l'œuvre du site, une image qui porte le titre de l'œuvre (texte alternatif ou adresse) ;
     *  2. dans l'annuaire, si le réglage le permet : un titre exactement identique ;
     *  3. enfin ce que la page du site annonce pour le partage sans que rien dise que c'est l'œuvre (ce peut être l'image du site : voir [CoverBook.saved], qui l'écarte quand elle sert à plusieurs œuvres).
     */
    private suspend fun fetch(w: Wish) {
        val urls = library().history.filter { BrowsingLibrary.seriesKey(it.url) == w.key }.map { it.url }
        val host = w.key.substringBefore('|')
        val notes = ArrayList<String>()
        val pages = CoverPick.pagesFor(urls, max = 2)
        if (pages.isEmpty()) notes.add("aucune page d'œuvre connue")
        val weak = ArrayList<Pair<String, String>>()      // (image, page) annoncées sans preuve que ce soit l'œuvre
        // 1) la page de l'œuvre sur le site
        if (pages.isNotEmpty() && siteWorthTrying(host)) {
            var readable = false
            for (page in pages) {
                val found = read(page, w.title)
                if (found == null) { notes.add("site : page illisible ou trop lente"); continue }
                if (CoverPick.looksLikeChallenge(found.pageTitle)) { notes.add("site : page d'attente anti-robot « ${found.pageTitle.take(40)} »"); continue }
                readable = true
                val ranked = CoverPick.rank(found, w.title)
                if (ranked.isEmpty()) { notes.add("site : aucune image retenue (partage ${found.shared.size}, données ${found.structured.size}, page ${found.images.size})"); continue }
                for (cand in ranked) if (!cand.evidenced) weak.add(cand.url to page)
                for (cand in ranked.filter { it.evidenced }.take(3)) {
                    if (keep(w, cand.url, page, true, "site", notes)) { noteSite(host, true); report(w, true, "site", notes, page); return }
                }
            }
            noteSite(host, readable)
        } else if (pages.isNotEmpty()) notes.add("site : laissé de côté un moment (échecs répétés)")
        // 2) l'annuaire public
        if (online()) {
            if (SystemClock.elapsedRealtime() < directoryPausedUntil) notes.add("annuaire : en pause (trop de demandes)")
            else {
                val r = withContext(Dispatchers.IO) { directoryCover(w.title) }
                val cover = r.first
                if (cover == null) notes.add("annuaire : ${r.second}")
                else if (keep(w, cover, "", true, "annuaire", notes)) { report(w, true, "annuaire", notes, null); return }
            }
        }
        // 3) en dernier recours, l'image annoncée par la page du site sans preuve
        for ((url, page) in weak.distinctBy { it.first }.take(2)) {
            if (keep(w, url, page, false, "site (sans preuve)", notes)) { report(w, true, "site", notes, page); return }
        }
        report(w, false, "", notes, pages.firstOrNull())
        store.failed(w.key)
    }

    /** L'annuaire : l'adresse de la couverture de l'œuvre, ou null avec la raison. Fil d'arrière-plan. */
    private fun directoryCover(title: String): Pair<String?, String> {
        val conn = try { URL(directory).openConnection() as HttpURLConnection } catch (_: Throwable) { return null to "adresse de l'annuaire illisible" }
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", userAgent)
            conn.outputStream.use { it.write(AniListCovers.requestBody(title).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 429) { directoryPausedUntil = SystemClock.elapsedRealtime() + 90_000L; return null to "trop de demandes (pause)" }
            if (code != 200) return null to "réponse HTTP $code"
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 600_000) return null to "réponse trop longue"
                }
            }
            val url = AniListCovers.cover(out.toString("UTF-8"), title, allowHttp = !directory.startsWith("https://")) ?: return null to "titre absent de l'annuaire"
            return url to ""
        } catch (e: Exception) {
            return null to "annuaire injoignable"
        } finally {
            conn.disconnect()
        }
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

    /**
     * Ouvre la page dans le navigateur caché et relève ce qu'elle annonce comme illustration ; null si elle ne s'ouvre pas (introuvable, refusée, trop lente).
     * Une page que son propre script construit n'a parfois rien à lire tout de suite : on relit toutes les secondes environ, jusqu'à ce qu'une image convienne ([title] est celui de l'œuvre) ou qu'une dizaine de lectures aient passé.
     */
    private suspend fun read(page: String, title: String): CoverCandidates? {
        val w = web ?: newWeb().also { web = it }
        return withTimeoutOrNull(32_000L) {
            suspendCancellableCoroutine<CoverCandidates?> { cont ->
                var gen = 0
                var status = 0
                var done = false
                var polling = -1
                var last: CoverCandidates? = null
                fun finish(r: CoverCandidates?) { if (!done) { done = true; if (cont.isActive) cont.resume(r) } }
                fun poll(g: Int, attempt: Int) {
                    handler.postDelayed(Runnable {
                        if (done || g != gen) return@Runnable
                        w.evaluateJavascript(EXTRACT) { raw ->
                            if (done || g != gen) return@evaluateJavascript
                            val p = parse(raw)
                            if (p != null) last = p
                            if (p != null && CoverPick.rank(p, title).isNotEmpty()) finish(p)
                            else if (attempt >= POLLS_MAX) finish(last)
                            else poll(g, attempt + 1)
                        }
                    }, if (attempt == 0) 600L else 900L)
                }
                fun startPolling(g: Int) { if (polling != g) { polling = g; poll(g, 0) } }
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
                        startPolling(g)
                    }
                }
                cont.invokeOnCancellation { handler.removeCallbacksAndMessages(null); try { w.stopLoading() } catch (_: Throwable) {} }
                w.loadUrl(page)
                // filet : quand « page chargée » tarde (une ressource lente), ce que la page annonce dans son en-tête est lisible depuis longtemps ; on commence à le relever au bout de 12 s si rien n'a commencé
                handler.postDelayed(Runnable {
                    if (done || status >= 400 || polling >= 0) return@Runnable
                    if (!Sites.sameSite(Sites.hostOf(w.url), Sites.hostOf(page))) return@Runnable
                    startPolling(gen)
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
        CoverCandidates(strings("shared"), strings("structured"), imgs, o.optString("title"))
    } catch (_: Throwable) { null }

    /** Image annoncée en « http:// » par une page en « https:// » (réglage fréquent des sites bâtis sur WordPress derrière un relais) : le navigateur la prend en https, l'application aussi, car le clair y est refusé. */
    private fun secure(url: String, page: String): String = if (page.startsWith("https://") && url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private fun originOf(url: String): String? = try { java.net.URI(url).let { if (it.scheme != null && it.authority != null) "${it.scheme}://${it.authority}" else null } } catch (_: Throwable) { null }

    /**
     * Téléchargement séparé de l'image : 200 et les octets, sinon null. Avec les en-têtes qu'envoie un navigateur pour une image, d'abord avec la page comme Referer, puis l'origine seule, puis sans :
     * certains serveurs d'images refusent (403) selon l'un ou l'autre. [page] vide : image d'un annuaire, sans Referer. Fil d'arrière-plan.
     */
    private fun download(src: String, page: String): ByteArray? {
        val referers = if (page.isEmpty()) listOf<String?>(null) else listOf<String?>(page, originOf(page)?.let { "$it/" }, null).distinct()
        for (referer in referers) {
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
                    conn.setRequestProperty("Accept-Language", java.util.Locale.getDefault().toLanguageTag() + ",en;q=0.8")
                    if (page.isNotEmpty()) {
                        conn.setRequestProperty("Sec-Fetch-Dest", "image"); conn.setRequestProperty("Sec-Fetch-Mode", "no-cors")
                        conn.setRequestProperty("Sec-Fetch-Site", if (originOf(url) == originOf(page)) "same-origin" else if (Sites.sameSite(Sites.hostOf(url), Sites.hostOf(page))) "same-site" else "cross-site")
                    }
                    if (referer != null) conn.setRequestProperty("Referer", referer)
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

    /** L'image téléchargée, réduite (360 px au plus) et rangée en JPEG ; null si ce n'est pas une illustration plausible (un logo, un bandeau, un pixel) ou si elle est illisible. [evidenced] : son adresse ou son texte alternatif portait le titre. */
    private fun shrink(bytes: ByteArray, evidenced: Boolean): ByteArray? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || !CoverPick.fits(o.outWidth, o.outHeight, evidenced)) return null
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
        /** Relevés de la page de l'œuvre, d'une seconde environ, avant de rendre ce qu'on a. */
        const val POLLS_MAX = 9
        /** Après autant d'échecs d'affilée sur un site, on le laisse de côté ce temps (puis on réessaie). */
        const val SITE_MISSES_MAX = 3
        const val SITE_PAUSE_MS = 30L * 60_000L

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
  function biggest(set){var u='',w=-1;String(set||'').split(',').forEach(function(p){var t=p.trim().split(/\s+/);if(!t[0])return;var n=parseFloat(t[1])||0;if(n>=w){w=n;u=t[0]}});return u}
  var images=[];
  document.querySelectorAll('img').forEach(function(im,i){if(i>=80)return;
    var src=im.currentSrc||im.getAttribute('src')||im.getAttribute('data-src')||im.getAttribute('data-lazy-src')||im.getAttribute('data-original')||biggest(im.getAttribute('srcset')||im.getAttribute('data-srcset'))||'';
    if(!src||src.indexOf('data:')===0)return;var a=abs(src);if(!a)return;
    var cls=(typeof im.className==='string')?im.className:'';
    images.push({src:a,alt:im.getAttribute('alt')||im.getAttribute('title')||'',cls:cls,w:im.naturalWidth||im.width||0,h:im.naturalHeight||im.height||0})});
  document.querySelectorAll('[style*="background-image"]').forEach(function(el,i){if(i>=20)return;
    var m=/url\(["']?([^"')]+)["']?\)/.exec(el.getAttribute('style')||'');if(!m)return;var a=abs(m[1]);if(!a)return;
    images.push({src:a,alt:el.getAttribute('aria-label')||el.getAttribute('title')||el.getAttribute('data-title')||'',cls:(typeof el.className==='string')?el.className:'',w:el.clientWidth||0,h:el.clientHeight||0})});
  return {title:document.title||'',shared:shared.slice(0,6),structured:structured.slice(0,6),images:images};
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
