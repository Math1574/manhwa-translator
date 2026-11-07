package app.manhwa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.AdBlocker
import app.manhwa.core.AdVerdict
import app.manhwa.core.CdpClient
import app.manhwa.core.OcrEngine
import app.manhwa.core.TranslationEngine
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.SourceLang
import app.manhwa.core.NavDecision
import app.manhwa.core.NavigationPolicy
import app.manhwa.core.RequestTypes
import app.manhwa.core.ResType
import app.manhwa.core.Sites
import app.manhwa.core.BrowsingLibrary
import app.manhwa.core.CacheKey
import app.manhwa.core.DebugInfo
import app.manhwa.core.JunkFilter
import app.manhwa.core.OcrMerge
import app.manhwa.core.OcrRepair
import app.manhwa.core.Onomatopoeia
import app.manhwa.core.TranslationChecks
import app.manhwa.core.sha256Hex
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

/** Bulle sélectionnée par appui long pour une correction manuelle. */
data class EditTarget(val imageId: String, val bid: Int, val src: String, val tr: String)

/** Navigateur + orchestration de la traduction (collecte des images, file d'attente, overlay). */
class TranslationController(private val ctx: Context, private val graph: AppGraph) {
    private val main = Handler(Looper.getMainLooper())
    private val fonts by lazy { Typefaces(ctx) }
    private var webView: WebView? = null
    /** Lu une fois sur le thread principal : la WebView ne doit pas être appelée depuis les threads de traitement. */
    @Volatile private var userAgent: String = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"
    private val b64Waiters = ConcurrentHashMap<String, CompletableFuture<String>>()
    /** En-têtes que la WebView a elle-même envoyés pour chaque image (adresse → en-têtes) : le téléchargement séparé de secours les rejoue. */
    private val seenImageHeaders = object : LinkedHashMap<String, Map<String, String>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>) = size > 400
    }
    /** Image → (comment ses octets ont été obtenus, durée en ms) : pour le journal de diagnostic. */
    private val fetchInfo = ConcurrentHashMap<String, Triple<String, Long, String>>()
    /** Image → durées en ms de la lecture du texte, de la traduction et de l'affichage (journal de diagnostic : où passe le temps). */
    private val stageInfo = ConcurrentHashMap<String, LongArray>()
    /** Relit les images déjà chargées par la page dans la WebView elle-même (mêmes cookies, mêmes protections, aucun second téléchargement). */
    private val devTools = CdpClient({ ms -> openDevToolsSocket(ms) }, { currentUrl.ifEmpty { null } })

    // État observé par l'interface
    var currentUrl by mutableStateOf("")
    /** Vrai quand la page est lue presque jusqu'en bas (bouton « Chapitre suivant »). */
    var nearBottom by mutableStateOf(false)
    /** Titre de la page affichée (pour les favoris). */
    val pageTitle: String get() = webView?.title ?: ""
    var loading by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var pageError by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var active by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    /** Barre d'adresse masquée parce qu'on lit en descendant ; elle revient en remontant ou en touchant la page. */
    var barsHidden by mutableStateOf(false)
    /** En plein écran : menu de lecture (quitter, chapitres, original/traduit) affiché après un appui sur la page. */
    var readerBar by mutableStateOf(false)
    /** Part de la page parcourue (0..100). */
    var pageProgress by mutableIntStateOf(0)
    var overlaysVisible by mutableStateOf(true)
    var total by mutableIntStateOf(0)
    var done by mutableIntStateOf(0)
    var failed by mutableIntStateOf(0)
    var noText by mutableIntStateOf(0)
    var lastError by mutableStateOf<String?>(null)

    var editTarget by mutableStateOf<EditTarget?>(null)
    /** Le téléphone chauffe (état thermique « modéré » ou pire) : plus aucune page n'est préparée en avance, seule la page lue est traitée. */
    var thermalHot by mutableStateOf(false)
    /** Ouverture demandée par le raccourci de l'icône avant que le navigateur existe : adresse et avancement à reprendre. */
    private var pendingOpen: Pair<String, Int>? = null
    /** Incrémenté à chaque ouverture par le raccourci : l'interface revient sur l'onglet Lecture. */
    var readingTick by mutableIntStateOf(0)
    /** Œuvre de la page ouverte (clé site + titre), null si l'adresse n'en désigne pas. */
    val currentSeriesKey: String? get() = BrowsingLibrary.seriesKey(currentUrl)
    private var thermalWatching = false
    /** Traduction dont on regarde l'original (appui long) : l'interface propose de la corriger pendant quelques secondes. */
    var peekTarget by mutableStateOf<EditTarget?>(null)
    private val clearPeek = Runnable { peekTarget = null }
    /** Avancement à reprendre à l'ouverture de la page demandée (0 = aucun). */
    private var resumeTarget = 0
    private var resumeUrl = ""
    /** Chapitre commencé ouvert sans passer par la bibliothèque : proposition « Reprendre à X % ». */
    var resumeOffer by mutableIntStateOf(0)
    private val candidatesById = ConcurrentHashMap<String, Candidate>()
    private val keysById = ConcurrentHashMap<String, CacheKey>()

    // ---------- blocage des publicités ----------
    /** Ancienne liste de domaines : filet de sécurité pendant que le moteur de filtres se charge. */
    private val legacyBlocker: AdBlocker by lazy {
        AdBlocker(try { ctx.assets.open("adblock/domains.txt").bufferedReader().use { it.readLines() } } catch (e: Throwable) { emptyList() })
    }
    @Volatile private var pageUrl: String? = null
    /** Dernier vrai lien visible touché par l'utilisateur (signalé par le script de garde) : sa destination est autorisée quelques secondes. */
    @Volatile private var trustedHref: String? = null
    @Volatile private var trustedAt = 0L
    private var guardAtDocumentStart = false
    /** Tests automatiques seulement : les noms de domaine en « .test » sont servis par le faux site local (après le filtrage). */
    @Volatile var testSiteProxy = false
    val testServed = ConcurrentLinkedQueue<String>()
    /** Navigation ou pop-up refusé, proposé à l'utilisateur quelques secondes (« Ouvrir quand même »). */
    class NavBlock(val url: String, val reason: String, val popup: Boolean)
    var navBlocked by mutableStateOf<NavBlock?>(null)
    private val clearNavBlocked = Runnable { navBlocked = null }
    @Volatile private var lastPopupPill = 0L

    private fun currentSite(): String? = Sites.hostOf(currentUrl)?.let { Sites.registrable(it) }
    private fun siteOf(url: String?): String? = Sites.hostOf(url)?.let { Sites.registrable(it) }
    private fun blockerOn(page: String?): Boolean {
        val st = graph.settingsStore.settings
        val site = siteOf(page)
        return st.blockAds && (site == null || site !in st.adAllowSiteSet)
    }
    private fun sameLink(a: String, b: String) = a.substringBefore('#').removeSuffix("/") == b.substringBefore('#').removeSuffix("/")

    /** Le bloqueur est-il actif sur la page affichée ? (réglage général et exception par site) */
    fun adsActiveHere(): Boolean = blockerOn(currentUrl)
    /** Nombre d'éléments bloqués depuis l'ouverture de la page. */
    fun adCountOnPage(): Int = graph.adFilters.log.pageCount.get()
    /** Active ou désactive le bloqueur pour le site affiché (puis recharge la page). */
    fun toggleAdsHere() {
        val site = currentSite() ?: return
        graph.settingsStore.update { st ->
            val set = st.adAllowSiteSet.toMutableSet()
            if (!set.add(site)) set.remove(site)
            st.copy(adAllowSites = set.joinToString(","))
        }
        reload()
    }

    private fun showNavBlocked(nb: NavBlock) {
        navBlocked = nb
        main.removeCallbacks(clearNavBlocked); main.postDelayed(clearNavBlocked, if (nb.popup) 3000 else 7000)
    }
    fun dismissNavBlocked() { navBlocked = null; main.removeCallbacks(clearNavBlocked) }
    /** « Ouvrir quand même » : autorise ce site comme destination puis ouvre l'adresse refusée. */
    fun openBlockedNav() {
        val nb = navBlocked ?: return
        dismissNavBlocked()
        if (nb.popup || nb.url.isEmpty()) return
        val site = siteOf(nb.url) ?: return
        graph.settingsStore.update { it.copy(adAllowTargets = (it.adAllowTargetSet + site).joinToString(",")) }
        webView?.loadUrl(nb.url)
    }

    private fun legacyVerdict(url: String, type: Int, page: String?): AdVerdict =
        if (type != ResType.IMAGE && legacyBlocker.isBlocked(Sites.hostOf(url), Sites.hostOf(page))) AdVerdict(true, "liste de secours") else AdVerdict.ALLOW

    private fun testProxy(request: WebResourceRequest): WebResourceResponse? {
        if (!testSiteProxy) return null
        val host = request.url.host ?: return null
        if (!host.endsWith(".test")) return null
        return try {
            val path = request.url.encodedPath ?: "/"
            val conn = URL("http://10.0.2.2:8000/__sites/$host$path").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 10000
            val code = conn.responseCode
            testServed.add(request.url.toString())
            val mime = conn.contentType?.substringBefore(';')?.trim()?.ifEmpty { null } ?: "text/plain"
            WebResourceResponse(mime, null, code, if (code == 200) "OK" else "Not Found", mapOf("Access-Control-Allow-Origin" to "*"), if (code in 200..299) conn.inputStream else conn.errorStream)
        } catch (t: Throwable) { null }
    }
    /** Texte normalisé → images (par contenu) où il est apparu : un texte qui revient sur au moins 4 images différentes est un filigrane. */
    private val seenTexts = ConcurrentHashMap<String, MutableSet<String>>()

    private fun repeatedAcrossPages(text: String, imageId: String): Boolean {
        val key = text.lowercase().filter { it.isLetterOrDigit() }
        if (key.length < 8) return false
        val set = seenTexts.getOrPut(key) { java.util.Collections.newSetFromMap(ConcurrentHashMap()) }
        set.add(imageId)
        return set.size >= 4
    }

    // Tests automatiques (builds debug) : journal détaillé de chaque image traitée
    @Volatile var testing = false
    /** Tests automatiques : coupe la lecture des images par la WebView pour essayer le téléchargement séparé seul. */
    @Volatile var testNoDevTools = false
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

    // ---------- plein écran et lecture ----------

    private val hideReaderBar = Runnable { readerBar = false }

    fun enterFullscreen() { fullscreen = true; readerBar = false }
    fun exitFullscreen() { fullscreen = false; readerBar = false; barsHidden = false; main.removeCallbacks(hideReaderBar) }

    /** Menu de lecture du plein écran : il se referme seul après quelques secondes sans toucher. */
    fun showReaderBar(show: Boolean) {
        readerBar = show
        main.removeCallbacks(hideReaderBar)
        if (show) main.postDelayed(hideReaderBar, 5000)
    }

    private var lastBarChange = 0L
    private fun changeBars(hide: Boolean) {
        if (barsHidden != hide) { barsHidden = hide; lastBarChange = android.os.SystemClock.uptimeMillis() }
    }

    /** Appui simple sur la page : en plein écran il ouvre ou ferme le menu de lecture, sinon il montre ou masque la barre d'adresse. */
    private fun onPageTap() {
        if (fullscreen) showReaderBar(!readerBar) else if (graph.settingsStore.settings.hideBarsWhileReading) changeBars(!barsHidden)
    }

    /**
     * Défilement vers le bas : la barre d'adresse se masque ; vers le haut : elle revient. Le changement de hauteur de la page qu'elle provoque peut lui-même
     * produire un défilement : on ignore le sens pendant une demi-seconde après chaque changement, pour ne pas la faire clignoter.
     */
    private fun onScrollDirection(down: Boolean) {
        if (fullscreen) { if (down && readerBar) showReaderBar(false); return }
        if (!graph.settingsStore.settings.hideBarsWhileReading) return
        if (android.os.SystemClock.uptimeMillis() - lastBarChange < 500) return
        changeBars(down)
    }

    private fun onProgressReported(percent: Int) {
        pageProgress = percent
        if (currentUrl.startsWith("http")) graph.library.progress(currentUrl, percent)
    }

    // ---------- navigateur ----------

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebView(c: Context): WebView {
        webView?.let { (it.parent as? ViewGroup)?.removeView(it); return it }
        // sert uniquement à relire les images que la page a chargées (prise locale réservée à cette application) ; voir CdpClient
        WebView.setWebContentsDebuggingEnabled(true)
        val wv = WebView(c)
        userAgent = wv.settings.userAgentString
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)            // sinon window.open et target=_blank remplaceraient la page ; onCreateWindow refuse chaque fenêtre
            javaScriptCanOpenWindowsAutomatically = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.addJavascriptInterface(Bridge(), "AndroidMT")
        // script de garde (pop-ups, superpositions, masquage) : exécuté avant les scripts du site si la WebView le permet, sinon injecté au début de chaque page
        guardAtDocumentStart = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT).also { ok -> if (ok) WebViewCompat.addDocumentStartJavaScript(wv, AdGuardScript.JS, setOf("*")) }
        } catch (_: Throwable) { false }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val st = graph.settingsStore.settings
                val target = request.url.toString()
                val page = pageUrl
                val trusted = trustedHref?.let { SystemClock.uptimeMillis() - trustedAt < 4000 && sameLink(it, target) } == true
                val d = NavigationPolicy.decide(target, page, request.isForMainFrame, request.isRedirect, trusted, st.adAllowTargetSet, blockerOn(page), graph.adFilters.engine)
                if (d is NavDecision.Block) {
                    graph.adFilters.log.add(if (d.rule != null) "redirection (liste)" else "redirection", target, d.rule, siteOf(page))
                    if (target.startsWith("http")) main.post { showNavBlocked(NavBlock(target, d.reason, false)) }
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) {
                    // la page demandée devient la page courante dès maintenant : ses premières sous-requêtes peuvent partir avant onPageStarted
                    if (request.url.scheme?.startsWith("http") == true) pageUrl = request.url.toString()
                    return testProxy(request)
                }
                request.requestHeaders.let { h ->
                    val accept = (h["Accept"] ?: "").lowercase()
                    if (h["Sec-Fetch-Dest"] == "image" || accept.startsWith("image/") || accept.contains("image/avif") || accept.contains("image/webp")) rememberImageHeaders(request.url.toString(), h)
                }
                val h = request.requestHeaders
                // page inconnue (aucune page encore chargée) : le Referer de la requête désigne la page qui l'a émise
                val page = pageUrl ?: h["Referer"]?.takeIf { it.startsWith("http") }
                if (!blockerOn(page)) return testProxy(request)
                val url = request.url.toString()
                val type = RequestTypes.infer(false, h["Accept"], url, request.method, h["X-Requested-With"], h["Sec-Fetch-Dest"])
                graph.adFilters.awaitEngine(10_000)
                val eng = graph.adFilters.engine
                val v = if (eng != null) eng.check(url, page, type) else legacyVerdict(url, type, page)
                if (!v.blocked) return testProxy(request)
                graph.adFilters.log.add(when (type) { ResType.IMAGE -> "image"; ResType.SUBDOCUMENT -> "cadre"; ResType.SCRIPT -> "script"; else -> "requête" }, url, v.rule, siteOf(page))
                return AdFilters.blockedResponse(type, h["Origin"])
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentUrl = if (url.startsWith("http")) url else ""
                pageUrl = if (url.startsWith("http")) url else null
                graph.adFilters.log.newPage()
                if (translateOn && url.startsWith("http") && (testing || BrowsingLibrary.looksLikeChapter(url))) warmUp()   // les modèles se chargent pendant que la page arrive
                try { view.settings.setSupportMultipleWindows(blockerOn(url)) } catch (_: Throwable) {}   // bloqueur éteint : comportement normal du navigateur
                if (!guardAtDocumentStart) view.evaluateJavascript(AdGuardScript.JS, null)
                graph.gemma.seriesTitle = if (url.startsWith("http")) app.manhwa.core.SeriesHint.fromUrl(url) else null
                loading = true
                nearBottom = false
                barsHidden = false; pageProgress = 0; showReaderBar(false); resumeOffer = 0; peekTarget = null
                pageError = null
                resetSession()
                view.evaluateJavascript(PageScript.INJECT, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                if (url.startsWith("http")) graph.library.visit(url, view.title ?: "")
                view.evaluateJavascript(PageScript.INJECT, null)
                if (url.startsWith("http")) {
                    if (resumeTarget > 0 && BrowsingLibrary.normalize(url) == resumeUrl) {
                        view.evaluateJavascript("window.__mt&&window.__mt.resumeTo($resumeTarget)", null); resumeTarget = 0; resumeOffer = 0
                    } else {
                        val p = graph.library.library.progressOf(url)
                        resumeOffer = if (p != null && !p.done && p.percent in 5..93) p.percent else 0
                    }
                }
                // traduction pendant la lecture (bouton Original | Traduit) : seulement les pages de chapitre, pas les accueils ni les listes d'œuvres
                if (translateOn && url.startsWith("http") && (testing || BrowsingLibrary.looksLikeChapter(url))) start(false)
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

            /** Une page n'ouvre jamais de fenêtre : on refuse (les liens en nouvel onglet du site lui-même sont suivis dans la page par le script de garde). */
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                graph.adFilters.log.add("pop-up", "(fenêtre demandée${if (isUserGesture) " après un geste" else ""})", null, siteOf(pageUrl))
                main.post { notePopupBlocked("") }
                return false
            }
            override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean { result.confirm(); return true }
        }
        webView = wv
        val pending = pendingOpen
        if (pending != null) { pendingOpen = null; load(pending.first, pending.second) } else goHome()
        watchThermal()
        return wv
    }

    /** Protection contre la chauffe (Android 10 et plus) : on suit l'état thermique du téléphone et on suspend la préparation en avance quand il chauffe. */
    private fun watchThermal() {
        if (thermalWatching || android.os.Build.VERSION.SDK_INT < 29) return
        thermalWatching = true
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            fun apply(status: Int) { val hot = status >= android.os.PowerManager.THERMAL_STATUS_MODERATE; if (hot != thermalHot) { thermalHot = hot; pump() } }
            apply(pm.currentThermalStatus)
            pm.addThermalStatusListener(ctx.mainExecutor) { status -> apply(status) }
        } catch (_: Throwable) { thermalWatching = false }
    }

    /** Raccourci « Reprendre la lecture » de l'icône : ouvre le chapitre et se replace là où la lecture s'était arrêtée. */
    fun openFromShortcut(url: String, percent: Int) {
        readingTick++
        if (webView != null) load(url, percent) else pendingOpen = url to percent
    }

    fun load(url: String, resumeAt: Int = 0) {
        if (url.isNotEmpty()) { pageError = null; resumeTarget = resumeAt; resumeUrl = BrowsingLibrary.normalize(url) ?: url; webView?.loadUrl(url) }
    }

    /** Se replace dans le chapitre là où la lecture s'était arrêtée. */
    fun resumeNow() {
        val p = resumeOffer; resumeOffer = 0
        if (p > 0) webView?.evaluateJavascript("window.__mt&&window.__mt.resumeTo($p)", null)
    }
    fun dismissResumeOffer() { resumeOffer = 0 }

    /** Ouvre la correction de la traduction dont on vient de regarder l'original. */
    fun openPeekEdit() { peekTarget?.let { editTarget = it }; peekTarget = null; main.removeCallbacks(clearPeek) }
    fun goHome() {
        val lib = graph.library.library
        fun esc(t: String) = android.text.Html.escapeHtml(t)
        val sb = StringBuilder("<html><body style='background:#0E0F1A;color:#C9CCE8;font-family:sans-serif;padding:24px'><h2 style='color:#B388FF'>Manhwa Translator</h2>")
        val series = lib.series().take(5)
        if (series.isNotEmpty()) {
            sb.append("<h3 style='color:#B388FF'>Reprendre la lecture</h3>")
            for (e in series) sb.append("<p><a style='color:#8AB4FF' href='${esc(e.lastUrl)}'>${esc(e.title)}</a>${e.chapter?.let { " — $it" } ?: ""}</p>")
        }
        if (lib.favorites.isNotEmpty()) {
            sb.append("<h3 style='color:#B388FF'>Favoris</h3>")
            for (f in lib.favorites.take(8)) sb.append("<p><a style='color:#8AB4FF' href='${esc(f.url)}'>${esc(f.title)}</a></p>")
        }
        sb.append("<p>Saisissez l'adresse d'un site de lecture dans la barre ci-dessus, ouvrez un chapitre, puis appuyez sur « Traduire ».</p>")
        sb.append("<p>Avant la première traduction, téléchargez les modèles de langue dans l'onglet « Modèles ». L'historique et les favoris sont dans l'onglet « Bibliothèque ».</p></body></html>")
        webView?.loadDataWithBaseURL(null, sb.toString(), "text/html", "utf-8", null)
        currentUrl = ""; pageError = null; resetSession()
    }
    /** Tests automatiques : évalue du JavaScript dans la page et rend le résultat. */
    fun evalForTest(js: String, cb: (String?) -> Unit) { webView?.evaluateJavascript(js, cb) }
    /** Tests automatiques : un appui du doigt aux coordonnées de la page (pixels CSS), comme un vrai toucher. */
    fun tapForTest(xCss: Float, yCss: Float) {
        val wv = webView ?: return
        val d = wv.resources.displayMetrics.density
        val t = SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, xCss * d, yCss * d, 0); wv.dispatchTouchEvent(down); down.recycle()
        val up = android.view.MotionEvent.obtain(t, t + 70, android.view.MotionEvent.ACTION_UP, xCss * d, yCss * d, 0); wv.dispatchTouchEvent(up); up.recycle()
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
        warmUp()
        wv.evaluateJavascript(PageScript.INJECT, null)
        wv.evaluateJavascript("window.__mt.collect($force)") { r -> onCandidates(r) }
    }

    fun stop() { stopWork(); active = false }

    /** Traduire pendant la lecture (bouton Original | Traduit) : réglage mémorisé entre les lancements. */
    val translateOn: Boolean get() = graph.settingsStore.settings.readTranslate

    private fun showOverlays(visible: Boolean) {
        overlaysVisible = visible
        webView?.evaluateJavascript("window.__mt&&window.__mt.setVisible($visible)", null)
    }

    /** Active ou désactive la traduction pendant la lecture. Désactivée : le travail en cours s'arrête et l'original réapparaît ; la page garde ses traductions en cache. */
    fun setTranslate(on: Boolean) {
        graph.settingsStore.update { it.copy(readTranslate = on) }
        if (on) { showOverlays(true); start(false) } else { stopWork(); active = false; showOverlays(false) }
    }

    /** Appui sur le bouton Original | Traduit : traduit la page (puis les chapitres suivants), ou, si elle l'est déjà, revient à l'original. */
    fun onTranslateButton() {
        when {
            !translateOn -> setTranslate(true)
            !active -> { showOverlays(true); start(false) }   // activé, mais cette page n'est pas traduite (ce n'est pas un chapitre) : on la traduit maintenant
            else -> setTranslate(false)
        }
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
            queue.maxPending = if (thermalHot) 1 else graph.settingsStore.settings.maxPrefetchPages + 1
            while (backlog.isNotEmpty()) {
                val c = backlog.first()
                if (queue.submit(PageJob(c.id, c.priority) { cancelled -> process(c, cancelled) })) backlog.removeAt(0) else break
            }
        }
    }

    private fun process(c: Candidate, cancelled: () -> Boolean): PageOutcome {
        val pageUrl = currentUrl
        val pageSeries = BrowsingLibrary.seriesKey(pageUrl)
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
        val enhanced = s.enhancedOcr && graph.paddleModels.available
        val ocrEngine = CombinedOcr(s.source, MlKitOcr(s.source), if (enhanced) graph.paddle else null, Lexicon.get(ctx), parallel = !thermalHot)
        val rereadOn = spec != null && s.source.code == "en" && graph.blockReader.available
        // « pp2 » : lecture des lignes inclinées sur recadrage redressé ; « rr » : relecture des textes douteux par Gemma (résultats différents, cache à part)
        // onomatopées laissées telles quelles (anglais) : clé de cache distincte, pour ne pas reprendre d'anciens résultats où elles étaient traduites
        val skipSfx = !s.translateSfx && s.source.code != "ja" && s.source.code != "ko" && s.source.code != "zh"
        val engineId = (if (spec == null) MlKitTranslation.ID else GemmaTranslator.engineId(spec)) + (if (enhanced) "+pp2" else "") + (if (rereadOn) "+rr1" else "") + (if (skipSfx) "+sfx0" else "")
        var debug: DebugInfo? = null
        val lexicon = Lexicon.get(ctx)
        candidatesById[c.id] = c
        keysById[c.id] = CacheKey(sha256Hex(bytes), s.source.code, s.target.code, engineId)
        // durées mesurées pour le journal : lecture du texte, traduction (le rendu est mesuré plus bas)
        val stages = LongArray(3)
        stageInfo[c.id] = stages
        val timedOcr = OcrEngine { b -> val t = SystemClock.uptimeMillis(); try { ocrEngine.recognize(b) } finally { stages[0] += SystemClock.uptimeMillis() - t } }
        val timedTr = TimedTranslator(translator) { stages[1] += it }
        val pipeline = PagePipeline(
            graph.cache, timedOcr, timedTr, engineId,
            glossary = { graph.glossary.current(pageSeries) },
            useCache = !s.diagnostic && !testing, // en diagnostic on veut voir la détection réelle, pas un résultat en cache
            debugSink = { debug = it },
            // l'anglais est lu par OCR avec beaucoup de bruit : on écarte filigranes et débris, puis on répare mots collés et chiffres parasites
            // un texte libre sans zone du détecteur est écarté s'il ne contient aucun mot reconnu, même en gros lettrage (décor lu comme du texte : « IC », « RIC », « PY »)
            junk = { t, rel, anchored -> s.source.code != "ja" && s.source.code != "ko" && s.source.code != "zh" && (JunkFilter.isWatermark(t) || ((rel < 1.6f || !anchored) && JunkFilter.isDebris(t, lexicon)) || repeatedAcrossPages(t, keysById[c.id]?.imageHash ?: c.id)) },
            isSfx = { t, kind, rel -> skipSfx && Onomatopoeia.isSfx(t, kind, rel, lexicon) },
            repair = { t -> try { OcrRepair.repair(t, lexicon, graph.gemma.chapterNames()) } catch (e: Throwable) { t } },
            reader = if (rereadOn) graph.blockReader else null,
            // part de vrais mots anglais (noms propres du chapitre compris) : repère les lectures absurdes et vérifie les relectures
            plausibility = { t -> try { val names = graph.gemma.chapterNames(); OcrMerge.plausibility(t, lexicon, names) } catch (e: Throwable) { 1f } },
            log = { msg -> OcrDebug.note = (OcrDebug.note + " [" + msg + "]").takeLast(1500); android.util.Log.i("MT", msg) },
        )
        val out = pipeline.process(bytes, s.source.code, s.target.code)
        // noms qui reviennent d'une page à l'autre : proposés dans le glossaire de l'œuvre (anglais seulement, le lexique est anglais)
        if (out is PageOutcome.Done && s.source.code != "ja" && s.source.code != "ko" && s.source.code != "zh") {
            try { graph.names.observe(pageSeries, c.id, out.result.blocks.map { it.source }, lexicon) } catch (_: Throwable) {}
        }
        var overlayForLog: JSONObject? = null
        if (out is PageOutcome.Done && !out.fromCache) journalPage(c, pageUrl, engineId, s.target.code, out, System.currentTimeMillis() - t0)
        if (!cancelled()) {
            val tRender = SystemClock.uptimeMillis()
            val json = when {
                out is PageOutcome.Done -> PageRenderer.render(fonts, bytes, out.result, s, debug)
                s.diagnostic && debug != null -> OverlayBuilder.debugOnly(debug!!)
                else -> null
            }
            stages[2] = SystemClock.uptimeMillis() - tRender
            if (json != null) sendOverlay(c.id, json)
            overlayForLog = json
            if (json != null && out is PageOutcome.Done) journalLayout(c, pageUrl, out, json)
        }
        if (testing) logForTest(c, out, debug, overlayForLog, System.currentTimeMillis() - t0)
        return out
    }

    /**
     * Géométrie du rendu de chaque bloc (texte d'origine, zone détectée, bulle, mode d'effacement, taille, lignes) : permet de rejouer à l'identique un défaut
     * d'affichage signalé avec le journal, sans l'image.
     */
    private fun journalLayout(c: Candidate, pageUrl: String, out: PageOutcome.Done, json: JSONObject) {
        try {
            val rep = json.optJSONArray("blocks") ?: return
            val blocks = JSONArray()
            for (i in 0 until rep.length()) {
                val r = rep.getJSONObject(i)
                blocks.put(
                    JSONObject().put("t", r.optString("t").replace('\n', ' ').take(40)).put("kind", r.optString("kind")).put("mode", r.optString("mode"))
                        .put("fs", Math.round(r.optDouble("fs") * 10) / 10.0).put("lines", r.optInt("lines")).put("fits", r.optBoolean("fits")).put("shape", r.optBoolean("shape"))
                        .put("bubbleOk", r.optBoolean("bubbleOk")).put("rectBubble", r.optBoolean("rectBubble")).put("erase", r.optInt("erase")).put("lay", r.optString("lay"))
                        .put("orig", r.opt("orig")).put("cont", r.opt("cont")).put("zone", r.opt("zoneBox"))
                        .put("out", JSONArray(listOf("x", "y", "w", "h").map { k -> Math.round(r.optDouble(k)) })),
                )
            }
            graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "layout").put("url", pageUrl).put("img", c.id).put("renderMs", stageInfo[c.id]?.get(2) ?: 0L).put("w", out.result.imageWidth).put("h", out.result.imageHeight).put("skipped", json.optInt("skipped")).put("blocks", blocks))
        } catch (_: Throwable) {}
    }

    /** Ajoute la page au journal de traduction : texte lu, traduction et problèmes détectés par les contrôles automatiques. */
    private fun journalPage(c: Candidate, pageUrl: String, engineId: String, target: String, out: PageOutcome.Done, ms: Long) {
        val blocks = JSONArray()
        out.result.blocks.forEach { b ->
            blocks.put(
                JSONObject().put("src", b.source).put("rep", b.repaired).put("tr", b.translated)
                    .put("p", JSONArray(TranslationChecks.problems(b.source, b.translated, target).map { it.code })),
            )
        }
        graph.journal.add(
            JSONObject().put("t", System.currentTimeMillis()).put("type", "page").put("url", pageUrl).put("series", graph.gemma.seriesTitle ?: "")
                .put("engine", engineId).put("img", c.id).put("w", out.result.imageWidth).put("h", out.result.imageHeight).put("ms", ms)
                .put("fetch", fetchInfo[c.id]?.first ?: "").put("fetchMs", fetchInfo[c.id]?.second ?: 0L).put("fetchWhy", fetchInfo[c.id]?.third ?: "")
                .put("ocrMs", stageInfo[c.id]?.get(0) ?: 0L).put("trMs", stageInfo[c.id]?.get(1) ?: 0L).put("ocrT", OcrDebug.timing).put("dbg", OcrDebug.note.take(1500)).put("blocks", blocks),
        )
    }

    /** Enregistre la correction manuelle (mémorisée dans le glossaire), la consigne au journal et retraduit la page concernée. */
    fun saveCorrection(newText: String, termSource: String = "", termTarget: String = "") {
        val t = editTarget ?: return
        editTarget = null
        val clean = newText.trim()
        val ts = termSource.trim(); val tt = termTarget.trim()
        val textChanged = clean.isNotEmpty() && clean != t.tr
        val termAdded = ts.isNotEmpty() && tt.isNotEmpty()
        if (!textChanged && !termAdded) return
        if (textChanged) graph.glossary.addCorrection(t.src, clean)
        if (termAdded) graph.glossary.add(ts, tt)
        graph.journal.add(
            JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", t.src).put("before", t.tr).put("after", if (textChanged) clean else "")
                .put("term", if (termAdded) "$ts = $tt" else ""),
        )
        val cand = candidatesById[t.imageId] ?: return
        keysById[t.imageId]?.let { graph.cache.remove(it) }
        queue.submit(PageJob(cand.id + "#fix" + System.nanoTime(), 0) { cancelled -> process(cand, cancelled) })
    }

    fun dismissEdit() { editTarget = null }

    /** Envoie l'overlay à la page par morceaux d'environ 1,2 Mo : évite une énorme chaîne JavaScript d'un seul bloc. */
    private fun sendOverlay(id: String, json: JSONObject) {
        val patches = json.optJSONArray("patches") ?: JSONArray()
        val chunks = ArrayList<JSONObject>()
        var cur = JSONArray(); var size = 0
        for (i in 0 until patches.length()) {
            val p = patches.getJSONObject(i)
            val len = p.optString("png").length
            if (size > 0 && size + len > 1_200_000) { chunks.add(JSONObject().put("w", json.get("w")).put("h", json.get("h")).put("patches", cur)); cur = JSONArray(); size = 0 }
            cur.put(p); size += len
        }
        val first = JSONObject().put("w", json.get("w")).put("h", json.get("h")).put("patches", cur)
        json.optJSONArray("dbg")?.let { first.put("dbg", it) }
        chunks.add(first)
        chunks.forEachIndexed { i, ch ->
            if (i > 0) ch.put("append", true)
            main.post { webView?.evaluateJavascript("window.__mt&&window.__mt.apply('$id', $ch)", null) }
        }
    }

    private fun logForTest(c: Candidate, out: PageOutcome, debug: DebugInfo?, overlay: JSONObject?, ms: Long) {
        val e = JSONObject().put("id", c.id).put("src", c.src).put("ms", ms).put("fetch", fetchInfo[c.id]?.first ?: "").put("fetchMs", fetchInfo[c.id]?.second ?: 0L).put("fetchWhy", fetchInfo[c.id]?.third ?: "")
            .put("ocrMs", stageInfo[c.id]?.get(0) ?: 0L).put("trMs", stageInfo[c.id]?.get(1) ?: 0L).put("renderMs", stageInfo[c.id]?.get(2) ?: 0L).put("ocrT", OcrDebug.timing)
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

    private val replayedHeaders = setOf("referer", "accept", "user-agent", "accept-language", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site")

    private fun rememberImageHeaders(url: String, h: Map<String, String>) {
        val keep = HashMap<String, String>()
        for ((k, v) in h) if (k.lowercase() in replayedHeaders) keep[k] = v
        if (keep.isNotEmpty()) synchronized(seenImageHeaders) { seenImageHeaders[url] = keep }
    }

    private fun openDevToolsSocket(readTimeoutMs: Int): CdpClient.Conn {
        val sock = android.net.LocalSocket()
        try {
            sock.connect(android.net.LocalSocketAddress("webview_devtools_remote_" + android.os.Process.myPid(), android.net.LocalSocketAddress.Namespace.ABSTRACT))
            sock.soTimeout = readTimeoutMs
        } catch (e: Throwable) { try { sock.close() } catch (_: Throwable) {}; throw e }
        return CdpClient.Conn(sock.inputStream, sock.outputStream, { ms -> try { sock.soTimeout = ms } catch (_: Throwable) {} }) {
            try { sock.shutdownInput() } catch (_: Throwable) {}
            try { sock.shutdownOutput() } catch (_: Throwable) {}
            try { sock.close() } catch (_: Throwable) {}
        }
    }

    private fun originOf(url: String): String? = try { java.net.URI(url).let { if (it.scheme != null && it.authority != null) "${it.scheme}://${it.authority}" else null } } catch (_: Throwable) { null }

    /** Séries d'en-têtes à essayer pour un téléchargement séparé : d'abord exactement ceux que la WebView a envoyés, puis des variantes du Referer (origine seule, adresse complète, aucun). */
    private fun downloadAttempts(c: Candidate, pageUrl: String): List<Map<String, String>> {
        val seen = synchronized(seenImageHeaders) { seenImageHeaders[c.src] }
        val origin = originOf(pageUrl)
        val sameOrigin = origin != null && originOf(c.src) == origin
        val site = if (sameOrigin) "same-origin" else if (Sites.sameSite(Sites.hostOf(c.src), Sites.hostOf(pageUrl))) "same-site" else "cross-site"
        fun base() = java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).also {
            it["User-Agent"] = userAgent
            it["Accept"] = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
            it["Accept-Language"] = java.util.Locale.getDefault().toLanguageTag() + ",en;q=0.8"
            it["Sec-Fetch-Dest"] = "image"; it["Sec-Fetch-Mode"] = "no-cors"; it["Sec-Fetch-Site"] = site
        }
        val list = ArrayList<Map<String, String>>()
        if (seen != null) list.add(base().also { m -> for ((k, v) in seen) m[k] = v })
        list.add(base().also { if (pageUrl.isNotEmpty()) it["Referer"] = if (sameOrigin) pageUrl else (origin ?: pageUrl) + "/" })
        if (pageUrl.isNotEmpty()) list.add(base().also { it["Referer"] = pageUrl })
        list.add(base())
        return list.distinct()
    }

    /** Téléchargement séparé : code HTTP et, si 200, les octets. */
    private fun download(src: String, headers: Map<String, String>): Pair<Int, ByteArray?> {
        val conn = URL(src).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            CookieManager.getInstance().getCookie(src)?.let { conn.setRequestProperty("Cookie", it) }
            val code = conn.responseCode
            if (code != 200) return code to null
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
            return 200 to out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Récupère l'image à traduire, du plus fidèle au plus fragile :
     *  1. les octets que la page a déjà chargés, lus dans la WebView (mêmes cookies, mêmes protections anti-copie, aucun second téléchargement) ;
     *  2. un téléchargement séparé avec les en-têtes que la WebView a elle-même envoyés (puis variantes du Referer) ;
     *  3. un fetch dans la page (marche si le serveur d'images autorise la lecture inter-sites).
     * Aucune protection n'est contournée : on ne lit que ce que la page a elle-même chargé et affiche.
     */
    private fun fetch(c: Candidate, pageUrl: String): ByteArray {
        val t0 = SystemClock.uptimeMillis()
        fetchInfo.remove(c.id); stageInfo.remove(c.id)
        var why = ""
        fun got(b: ByteArray, how: String): ByteArray { fetchInfo[c.id] = Triple(how, SystemClock.uptimeMillis() - t0, why); return b }
        if (c.src == "js:") return got(fetchViaPage(c.id), "page (blob)")
        val scheme = c.src.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") throw IOException("Adresse d'image non prise en charge")
        if (!testNoDevTools) devTools.imageBytes(c.src, MAX_IMAGE_BYTES)?.let { return got(it, "page") }
        why = if (testNoDevTools) "lecture par la WebView coupée (test)" else devTools.lastReason
        var code = 0
        var failure: String? = null
        for (headers in downloadAttempts(c, pageUrl)) {
            try {
                val r = download(c.src, headers)
                if (r.first == 200 && r.second != null) return got(r.second!!, "réseau")
                code = r.first
                if (code != 401 && code != 403) break
            } catch (e: IOException) { failure = e.message; break }
        }
        try { return got(fetchViaPage(c.id), "page (fetch)") } catch (_: Exception) {}
        throw IOException(if (code != 0) "HTTP $code : le site refuse le téléchargement séparé de l'image, et la page ne l'a pas fournie ($why)" else (failure ?: "téléchargement impossible"))
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
        @JavascriptInterface fun onBottom(nb: Boolean) { main.post { nearBottom = nb } }
        @JavascriptInterface fun onScrollDir(down: Boolean) { main.post { onScrollDirection(down) } }
        @JavascriptInterface fun onProgress(percent: Int) { main.post { onProgressReported(percent) } }
        @JavascriptInterface fun onTap() { main.post { onPageTap() } }
        @JavascriptInterface fun onPeek(id: String, bid: String, src: String, tr: String) {
            main.post { peekTarget = EditTarget(id, bid.toIntOrNull() ?: -1, src, tr); main.removeCallbacks(clearPeek); main.postDelayed(clearPeek, 7000) }
        }
        @JavascriptInterface fun onImagesChanged() { main.post { if (active) start(false) } }
        @JavascriptInterface fun onB64(id: String, data: String) { b64Waiters[id]?.complete(data) }
        @JavascriptInterface fun onEdit(id: String, bid: String, src: String, tr: String) { main.post { editTarget = EditTarget(id, bid.toIntOrNull() ?: -1, src, tr) } }
        // script de garde du bloqueur
        @JavascriptInterface fun adsOn(host: String): Boolean {
            val st = graph.settingsStore.settings
            val site = if (host.isEmpty()) null else Sites.registrable(host)
            return st.blockAds && (site == null || site !in st.adAllowSiteSet)
        }
        @JavascriptInterface fun adBlocked(kind: String, url: String) {
            graph.adFilters.log.add(kind, url, null, siteOf(pageUrl))
            if (kind == "pop-up") main.post { notePopupBlocked(url) }
        }
        @JavascriptInterface fun genuineLink(href: String) { trustedHref = href; trustedAt = SystemClock.uptimeMillis() }
        @JavascriptInterface fun cosmeticCss(host: String): String = try { graph.adFilters.cosmeticCss(host) } catch (_: Throwable) { "" }
    }

    private fun notePopupBlocked(url: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPopupPill < 2500) return
        lastPopupPill = now
        showNavBlocked(NavBlock(url, "Pop-up bloqué", true))
    }

    /** Enveloppe qui mesure le temps passé dans le moteur de traduction (somme des appels de la page). */
    private class TimedTranslator(private val inner: TranslationEngine, private val add: (Long) -> Unit) : TranslationEngine {
        private inline fun <T> timed(f: () -> T): T { val t = SystemClock.uptimeMillis(); try { return f() } finally { add(SystemClock.uptimeMillis() - t) } }
        override fun translate(texts: List<String>, source: String, target: String): List<String> = timed { inner.translate(texts, source, target) }
        override fun usesGlossaryPrompt(): Boolean = inner.usesGlossaryPrompt()
        override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> = timed { inner.translateWithGlossary(texts, source, target, glossary) }
    }

    // ---------- préchargement des modèles ----------
    private val warming = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastWarm = 0L

    /**
     * Charge à l'avance, en tâche de fond, les modèles de lecture (ML Kit, PaddleOCR, détecteur de bulles) et de traduction (Gemma met 10 à 30 s à se charger) :
     * la première page d'un chapitre ne paie plus ces chargements. Sans effet s'ils sont déjà chargés.
     */
    fun warmUp() {
        val now = SystemClock.uptimeMillis()
        if (now - lastWarm < 60_000 || !warming.compareAndSet(false, true)) return
        lastWarm = now
        Thread({
            try {
                val st = graph.settingsStore.settings
                try { Recognizers.warmUp(st.source) } catch (_: Throwable) {}
                if (st.enhancedOcr && graph.paddleModels.available) try { graph.paddleModels.warmUp(when (st.source) { SourceLang.JA -> "ja"; SourceLang.KO -> "ko"; SourceLang.ZH_HANS, SourceLang.ZH_HANT -> "zh"; else -> "en" }) } catch (_: Throwable) {}
                if (LlmModels.specFor(st.engine) != null) try { graph.gemma.warmUp() } catch (_: Throwable) {}
            } finally { warming.set(false) }
        }, "warm-up").also { it.isDaemon = true; it.priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    companion object { const val MAX_IMAGE_BYTES = 30 * 1024 * 1024 }
}
