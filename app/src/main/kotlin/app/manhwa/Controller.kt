package app.manhwa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.DebugInfo
import app.manhwa.core.LlmModels
import app.manhwa.core.PageJob
import app.manhwa.core.PageOutcome
import app.manhwa.core.PagePipeline
import app.manhwa.core.PageQueue
import app.manhwa.core.QueueListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private data class Candidate(val id: String, val src: String, val top: Double, val scrollY: Double) {
    val priority: Int
        get() = if (top >= scrollY) (top - scrollY).toInt() else 1_000_000 + abs(scrollY - top).toInt()
}

/** Navigateur + orchestration de la traduction (collecte des images, file d'attente, overlay). */
class TranslationController(private val ctx: Context, private val graph: AppGraph) {
    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    /** Lu une fois sur le thread principal : la WebView ne doit pas être appelée depuis les threads de traitement. */
    @Volatile private var userAgent: String = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"
    private val b64Waiters = ConcurrentHashMap<String, CompletableFuture<String>>()

    // État observé par l'interface
    var currentUrl by mutableStateOf("")
    var loading by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var pageError by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var active by mutableStateOf(false)
    var overlaysVisible by mutableStateOf(true)
    var total by mutableIntStateOf(0)
    var done by mutableIntStateOf(0)
    var failed by mutableIntStateOf(0)
    var noText by mutableIntStateOf(0)
    var lastError by mutableStateOf<String?>(null)

    // Tests automatiques (builds debug) : journal détaillé de chaque image traitée
    @Volatile var testing = false
    val testLog = ConcurrentLinkedQueue<JSONObject>()
    fun isReady(): Boolean = webView != null

    private val backlog = ArrayList<Candidate>()
    private val submitted = HashSet<String>()

    private val queue = PageQueue(maxPending = 4, listener = object : QueueListener {
        override fun onFinished(id: String, outcome: PageOutcome) {
            main.post {
                when (outcome) {
                    is PageOutcome.Done -> done++
                    is PageOutcome.NoText -> { done++; noText++ }
                    is PageOutcome.Failed -> {
                        failed++; lastError = "${outcome.stage} : ${outcome.message}"
                        if (outcome.message.contains("Modèle de traduction")) {
                            // Inutile d'enchaîner des dizaines d'erreurs identiques : on arrête et on l'indique.
                            stopWork(); total = done + failed
                        }
                    }
                }
            }
            pump()
        }
        override fun onCancelled(id: String) { main.post { pump() } }
    })

    // ---------- navigateur ----------

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebView(c: Context): WebView {
        webView?.let { (it.parent as? ViewGroup)?.removeView(it); return it }
        val wv = WebView(c)
        userAgent = wv.settings.userAgentString
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.addJavascriptInterface(Bridge(), "AndroidMT")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https" // intent:, market:, etc. : ignorés
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentUrl = if (url.startsWith("http")) url else ""
                loading = true
                pageError = null
                resetSession()
                view.evaluateJavascript(PageScript.INJECT, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                view.evaluateJavascript(PageScript.INJECT, null)
                if (graph.settingsStore.settings.autoTranslate && url.startsWith("http")) start(false)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                canGoBack = view.canGoBack(); canGoForward = view.canGoForward()
                if (url.startsWith("http")) currentUrl = url
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) pageError = "Chargement impossible : ${error.description} (code ${error.errorCode})"
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) pageError = "Le site a répondu avec l'erreur HTTP ${errorResponse.statusCode}"
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
        }
        webView = wv
        goHome()
        return wv
    }

    fun load(url: String) { if (url.isNotEmpty()) { pageError = null; webView?.loadUrl(url) } }
    fun goHome() {
        webView?.loadDataWithBaseURL(
            null,
            "<html><body style='background:#0E0F1A;color:#C9CCE8;font-family:sans-serif;padding:24px'><h2 style='color:#B388FF'>Manhwa Translator</h2>" +
                "<p>Saisissez l'adresse d'un site de lecture dans la barre ci-dessus, ouvrez un chapitre, puis appuyez sur « Traduire ».</p>" +
                "<p>Avant la première traduction, téléchargez les modèles de langue dans l'onglet « Modèles ».</p></body></html>",
            "text/html", "utf-8", null,
        )
        currentUrl = ""; pageError = null; resetSession()
    }
    fun back() { webView?.goBack() }
    fun forward() { webView?.goForward() }
    fun reload() { pageError = null; webView?.reload() }
    fun stopLoading() { webView?.stopLoading() }

    // ---------- traduction ----------

    /** Lance la traduction des images de la page ; force = retraiter aussi les images déjà vues (réglages modifiés). */
    fun start(force: Boolean) {
        val wv = webView ?: return
        if (currentUrl.isEmpty()) return
        if (force) { stopWork(); resetCounters() }
        active = true
        wv.evaluateJavascript(PageScript.INJECT, null)
        wv.evaluateJavascript("window.__mt.collect($force)") { r -> onCandidates(r) }
    }

    fun stop() { stopWork(); active = false }

    fun toggleOverlays() {
        overlaysVisible = !overlaysVisible
        webView?.evaluateJavascript("window.__mt&&window.__mt.setVisible($overlaysVisible)", null)
    }

    fun clearOverlays() {
        webView?.evaluateJavascript("window.__mt&&window.__mt.clear()", null)
    }

    private fun resetSession() {
        stopWork(); resetCounters(); active = false; overlaysVisible = true
        graph.gemma.resetContext()
    }

    private fun resetCounters() { total = 0; done = 0; failed = 0; noText = 0; lastError = null }

    private fun stopWork() {
        queue.cancelAll()
        synchronized(backlog) { backlog.clear(); submitted.clear() }
    }

    private fun onCandidates(raw: String?) {
        if (raw.isNullOrBlank() || raw == "null") return
        val arr = try { JSONArray(raw) } catch (e: Exception) { lastError = "page : réponse du script illisible"; return }
        var added = 0
        synchronized(backlog) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = Candidate(o.getString("id"), o.getString("src"), o.getDouble("top"), o.getDouble("sy"))
                if (submitted.add(c.id)) { backlog.add(c); added++ }
            }
            backlog.sortBy { it.priority }
        }
        total += added
        pump()
    }

    private fun pump() {
        synchronized(backlog) {
            queue.maxPending = graph.settingsStore.settings.maxPrefetchPages + 1
            while (backlog.isNotEmpty()) {
                val c = backlog.first()
                if (queue.submit(PageJob(c.id, c.priority) { cancelled -> process(c, cancelled) })) backlog.removeAt(0) else break
            }
        }
    }

    private fun process(c: Candidate, cancelled: () -> Boolean): PageOutcome {
        val pageUrl = currentUrl
        val bytes = try { fetch(c, pageUrl) } catch (e: Exception) {
            val f = PageOutcome.Failed("image", e.message ?: "téléchargement impossible")
            if (testing) logForTest(c, f, null, null, 0)
            return f
        }
        if (cancelled()) return PageOutcome.Failed("annulé", "")
        val s = graph.settingsStore.settings
        val t0 = System.currentTimeMillis()
        val spec = LlmModels.specFor(s.engine)
        val translator = if (spec == null) MlKitTranslation(graph.models) else graph.gemma
        val engineId = if (spec == null) MlKitTranslation.ID else GemmaTranslator.engineId(spec)
        var debug: DebugInfo? = null
        val pipeline = PagePipeline(
            graph.cache, MlKitOcr(s.source), translator, engineId,
            glossary = { graph.glossary.current() },
            useCache = !s.diagnostic && !testing, // en diagnostic on veut voir la détection réelle, pas un résultat en cache
            debugSink = { debug = it },
        )
        val out = pipeline.process(bytes, s.source.code, s.target.code)
        var overlayForLog: JSONObject? = null
        if (!cancelled()) {
            val json = when {
                out is PageOutcome.Done -> OverlayBuilder.build(bytes, out.result, s, debug)
                s.diagnostic && debug != null -> OverlayBuilder.debugOnly(debug!!)
                else -> null
            }
            if (json != null) main.post { webView?.evaluateJavascript("window.__mt&&window.__mt.apply('${c.id}', $json)", null) }
            overlayForLog = json
        }
        if (testing) logForTest(c, out, debug, overlayForLog, System.currentTimeMillis() - t0)
        return out
    }

    private fun logForTest(c: Candidate, out: PageOutcome, debug: DebugInfo?, overlay: JSONObject?, ms: Long) {
        val e = JSONObject().put("id", c.id).put("src", c.src).put("ms", ms)
        when (out) {
            is PageOutcome.Done -> {
                e.put("outcome", if (out.fromCache) "done-cache" else "done").put("w", out.result.imageWidth).put("h", out.result.imageHeight)
                val bl = JSONArray()
                out.result.blocks.forEach { b ->
                    bl.put(JSONObject().put("id", b.id).put("x0", b.box.left.toDouble()).put("y0", b.box.top.toDouble()).put("x1", b.box.right.toDouble()).put("y1", b.box.bottom.toDouble())
                        .put("src", b.source).put("tr", b.translated).put("vertical", b.vertical).put("lineH", b.lineHeight.toDouble()))
                }
                e.put("blocks", bl)
            }
            is PageOutcome.NoText -> e.put("outcome", "notext").put("w", out.imageWidth).put("h", out.imageHeight)
            is PageOutcome.Failed -> e.put("outcome", "failed").put("stage", out.stage).put("message", out.message)
        }
        if (overlay != null) e.put("overlay", overlay)
        if (debug != null) {
            val ocr = JSONArray()
            debug.lines.forEach { l -> ocr.put(JSONObject().put("x0", l.box.left.toDouble()).put("y0", l.box.top.toDouble()).put("x1", l.box.right.toDouble()).put("y1", l.box.bottom.toDouble()).put("t", l.text).put("c", l.confidence.toDouble()).put("s", l.status)) }
            e.put("ocr", ocr).put("w", debug.imageWidth).put("h", debug.imageHeight)
        }
        testLog.add(e)
    }

    /** Récupère l'image comme le ferait le navigateur (mêmes cookies, même Referer), sans contourner aucune protection. */
    private fun fetch(c: Candidate, pageUrl: String): ByteArray {
        if (c.src == "js:") return fetchViaPage(c.id)
        val scheme = c.src.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") throw IOException("Adresse d'image non prise en charge")
        val conn = URL(c.src).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
            if (pageUrl.isNotEmpty()) conn.setRequestProperty("Referer", pageUrl)
            CookieManager.getInstance().getCookie(c.src)?.let { conn.setRequestProperty("Cookie", it) }
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_IMAGE_BYTES) throw IOException("Image trop volumineuse")
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    /** Images blob: ou data: : le script de la page renvoie les octets en base64. */
    private fun fetchViaPage(id: String): ByteArray {
        val future = CompletableFuture<String>()
        b64Waiters[id] = future
        main.post { webView?.evaluateJavascript("window.__mt.getB64('$id')", null) }
        try {
            val b64 = future.get(30, TimeUnit.SECONDS)
            if (b64.isEmpty()) throw IOException("Image de la page illisible")
            return Base64.decode(b64, Base64.DEFAULT)
        } finally {
            b64Waiters.remove(id)
        }
    }

    private inner class Bridge {
        @JavascriptInterface fun onImagesChanged() { main.post { if (active) start(false) } }
        @JavascriptInterface fun onB64(id: String, data: String) { b64Waiters[id]?.complete(data) }
    }

    companion object { const val MAX_IMAGE_BYTES = 30 * 1024 * 1024 }
}
