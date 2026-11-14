package app.manhwa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import app.manhwa.core.BatchContext
import app.manhwa.core.NavDecision
import app.manhwa.core.Seams
import app.manhwa.core.ReadingOrder
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
import app.manhwa.core.BlockEdit
import app.manhwa.core.BlockGrouper
import app.manhwa.core.EditApplier
import app.manhwa.core.EditKind
import app.manhwa.core.IgnoredImage
import app.manhwa.core.IgnoredMatch
import app.manhwa.core.IgnoredText
import app.manhwa.core.OcrLine
import app.manhwa.core.Protected
import app.manhwa.core.RectF as CRect
import app.manhwa.core.SeriesHint
import app.manhwa.core.TextNormalizer
import app.manhwa.core.TranslatedBlock
import app.manhwa.core.UserRegion
import app.manhwa.core.PagePipeline
import app.manhwa.core.PageResult
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

private data class Candidate(
    val id: String, val src: String, val top: Double, val scrollY: Double,
    /** Hauteur affichée de l'image et hauteur de l'écran (px CSS) au repérage ; taille d'origine de l'image. */
    val height: Double = 0.0, val viewportH: Double = 0.0, val naturalH: Int = 0,
    /** Image collée au-dessus (identifiant, adresse, hauteur d'origine) et hauteur d'origine de l'image collée dessous : pour joindre un texte coupé par le découpage du chapitre. */
    val prevId: String? = null, val prevSrc: String? = null, val prevH: Int = 0, val nextH: Int? = null,
) {
    val priority: Int
        get() = priorityAt(scrollY, viewportH)

    /** Ordre de traitement pour une position de lecture donnée : visibles d'abord (de haut en bas), puis sous l'écran, puis au-dessus. */
    fun priorityAt(viewportY: Double, vh: Double): Int =
        ReadingOrder.priority(top, if (height > 0) height else 1.0, viewportY, if (vh > 0) vh else if (viewportH > 0) viewportH else 800.0)
}

/** Bulle sélectionnée par appui long pour une correction manuelle. */
data class EditTarget(val imageId: String, val bid: Int, val src: String, val tr: String, val x: Float = -1f, val y: Float = -1f)

/** Identifiant d'un texte à ajouter à la main (zone non traduite). */
const val NEW_BLOCK_ID = -2

/** Zone non traduite touchée longuement : coordonnées dans l'image d'origine. */
class EmptySpot(val imageId: String, val x: Float, val y: Float)

/** Geste qui peut encore être défait : texte du bandeau (« Traduction supprimée ») et ce qu'il faut faire pour l'annuler. */
class UndoBanner(val message: String, val undo: () -> Unit)

/** Ce que le contrôleur garde d'une page traitée pour pouvoir corriger ses blocs : fenêtre traitée, résultat avant et après corrections, empreinte de l'image. */
class WorkInfo(
    val imageId: String, val imageSrc: String, val imageKey: String, val bytes: ByteArray, val ctxH: Int,
    val result: PageResult, val applied: EditApplier.Applied, val series: String?,
)

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
    /** Zone non traduite dont on touche longuement l'image : l'interface propose d'y ajouter un texte. */
    var peekEmpty by mutableStateOf<EmptySpot?>(null)
    private val clearPeekEmpty = Runnable { peekEmpty = null }
    /** Dernier geste qui peut être défait (suppression d'une traduction, texte ou image ignorés) : l'interface propose « Annuler » pendant [undoWindowMs]. */
    var undoBanner by mutableStateOf<UndoBanner?>(null)
    private val clearUndo = Runnable { undoBanner = null }
    /** Durée de la proposition d'annulation (3 s) ; allongée seulement par les tests automatiques pour laisser le temps de lire l'écran. */
    @Volatile var undoWindowMs = UNDO_MS
    /** Avancement à reprendre à l'ouverture de la page demandée (0 = aucun). */
    private var resumeTarget = 0
    private var resumeUrl = ""
    /** Chapitre commencé ouvert sans passer par la bibliothèque : proposition « Reprendre à X % ». */
    var resumeOffer by mutableIntStateOf(0)
    private val candidatesById = ConcurrentHashMap<String, Candidate>()
    /** Change à chaque nouvelle page : un rafraîchissement différé (file pleine) d'une page quittée est abandonné. */
    @Volatile private var pageEpoch = 0
    /** Images laissées telles quelles parce que l'utilisateur ne veut jamais les voir traduites (identifiant de l'image de la page → règle qui la vise) : retraitées si la règle est retirée. */
    private val skipped = ConcurrentHashMap<String, IgnoredImage>()
    private val keysById = ConcurrentHashMap<String, CacheKey>()
    /** Pages récemment traitées, avec de quoi corriger leurs blocs (éditeur) : les plus anciennes sont oubliées. */
    private val workInfos = object : LinkedHashMap<String, WorkInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WorkInfo>?): Boolean = size > 12
    }
    /** Correction demandée sur une page qui n'est plus en mémoire : l'éditeur s'ouvre quand elle a été retraitée. */
    @Volatile private var pendingEdit: EditTarget? = null
    fun workInfo(imageId: String): WorkInfo? = synchronized(workInfos) { workInfos[imageId] }

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

    // suivi des traitements en cours (détection d'un blocage), position de lecture actuelle, exclusion mutuelle de la lecture du texte
    private val runStart = ConcurrentHashMap<String, Long>()
    private val runStage = ConcurrentHashMap<String, String>()
    private val stallReported = ConcurrentHashMap<String, Long>()
    private val batchInfo = ConcurrentHashMap<String, Pair<Int, Long>>()
    /** Notes de diagnostic et durées de lecture propres à chaque page (deux pages sont traitées en même temps : une variable commune mélangerait leurs notes). */
    private val pageNotes = ConcurrentHashMap<String, StringBuilder>()
    private val pageTiming = ConcurrentHashMap<String, String>()
    private val ocrLock = Any()
    /** Pages dont le traitement est terminé (réussi, sans texte ou en échec) : les autres, si le travail est interrompu, sont à redemander à la page. */
    private val finishedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val failEntries = java.util.concurrent.atomic.AtomicInteger(0)
    private var idleTicks = 0
    /** L'affichage d'une page (effacement, dessin) est lourd en mémoire et en calcul : une seule à la fois. */
    private val renderLock = Any()
    @Volatile private var viewportY = 0.0
    @Volatile private var viewportH = 800.0
    /** Images récemment téléchargées (par adresse) : l'image du dessus sert de contexte à la suivante sans être redemandée. */
    private val recentBytes = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > 4
    }
    /** Tests automatiques : ignore les jonctions d'images (comme avant), pour comparer avec et sans. */
    @Volatile var testNoSeam = false
    /** Tests automatiques : la traduction ML Kit passe aussi par le regroupeur, pour exercer la file parallèle sur l'émulateur. */
    @Volatile var testBatch = false

    private val queue = PageQueue(maxPending = 4, listener = object : QueueListener {
        override fun onStarted(id: String) { runStart[id] = SystemClock.uptimeMillis(); runStage[id] = "téléchargement" }
        override fun onFinished(id: String, outcome: PageOutcome) {
            runStart.remove(id); runStage.remove(id)
            finishedIds.add(id)
            // un échec n'apparaissait nulle part dans le journal : on le note (étape et message), avec l'état de la mémoire, pour comprendre un arrêt
            if (outcome is PageOutcome.Failed && failEntries.incrementAndGet() <= 60) {
                graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "fail").put("img", id).put("stage", outcome.stage).put("message", outcome.message.take(300)).put("sys", diag()))
            }
            // un retraitement demandé après coup (correction, suppression, règle « ignorer ») n'est pas une image de plus à traduire : il ne compte pas dans l'avancement
            val extra = id.contains('#')
            main.post {
                when (outcome) {
                    is PageOutcome.Done -> if (!extra) done++
                    is PageOutcome.NoText -> if (!extra) { done++; noText++ }
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
        override fun onCancelled(id: String) { runStart.remove(id); runStage.remove(id); main.post { pump() } }
    }, workers = 4)

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
                pageEpoch++
                pageUrl = if (url.startsWith("http")) url else null
                graph.adFilters.log.newPage()
                if (translateOn && url.startsWith("http") && (testing || BrowsingLibrary.looksLikeChapter(url))) warmUp()   // les modèles se chargent pendant que la page arrive
                try { view.settings.setSupportMultipleWindows(blockerOn(url)) } catch (_: Throwable) {}   // bloqueur éteint : comportement normal du navigateur
                if (!guardAtDocumentStart) view.evaluateJavascript(AdGuardScript.JS, null)
                graph.gemma.seriesTitle = if (url.startsWith("http")) app.manhwa.core.SeriesHint.fromUrl(url) else null
                loading = true
                nearBottom = false
                barsHidden = false; pageProgress = 0; showReaderBar(false); resumeOffer = 0; peekTarget = null; undoBanner = null
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
    fun openPeekEdit() { peekTarget?.let { openEdit(it) }; peekTarget = null; main.removeCallbacks(clearPeek) }

    /**
     * Supprime la traduction touchée longuement (petit artefact pris pour du texte) sans passer par l'éditeur : l'original est rétabli à cet endroit. Le bloc reste repérable (appui long)
     * pour pouvoir être rétabli, et la suppression est conservée avec les autres corrections de l'image.
     */
    fun deletePeek() {
        val t = peekTarget ?: return
        peekTarget = null; main.removeCallbacks(clearPeek)
        val id = t.imageId.removeSuffix("~up")
        val info = workInfo(id) ?: return
        val shown = info.applied.page.blocks.firstOrNull { it.id == t.bid } ?: return
        val prev = info.applied.editByBlock[shown.id]?.takeUnless { it.id.startsWith(IGNORE_EDIT_PREFIX) }      // une règle « ignorer » n'est pas une correction : jamais enregistrée comme telle
        val orig = info.result.blocks.firstOrNull { it.id == shown.id } ?: shown
        val edit = prev?.copy(hidden = true) ?: BlockEdit(
            "e" + java.util.UUID.randomUUID().toString().take(8), EditKind.ADJUST, imageRect(orig.box, info.ctxH), orig.source, hidden = true,
        )
        graph.edits.upsert(info.imageKey, edit)
        graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", shown.source).put("before", shown.translated).put("edit", "supprimé"))
        // le patch disparaît tout de suite ; la page est ensuite redessinée sans lui (résultat en cache)
        webView?.evaluateJavascript("window.__mt&&window.__mt.dropPatch(${JSONObject.quote(t.imageId)},${t.bid})", null)
        reprocess(id)
        // la suppression reste annulable quelques secondes : un bandeau « Annuler » est proposé, puis il disparaît et la suppression est définitive
        showUndo("Traduction supprimée") {
            if (prev != null) graph.edits.upsert(info.imageKey, prev) else graph.edits.remove(info.imageKey, edit.id)
            graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", shown.source).put("edit", "suppression annulée"))
            reprocess(id)
        }
    }

    /** Propose « Annuler » pendant [undoWindowMs] ; la proposition précédente est remplacée (son geste devient définitif). */
    fun showUndo(message: String, undo: () -> Unit) {
        undoBanner = UndoBanner(message, undo)
        main.removeCallbacks(clearUndo); main.postDelayed(clearUndo, undoWindowMs)
    }

    /** Défait le dernier geste proposé par le bandeau. */
    fun runUndo() {
        val u = undoBanner ?: return
        undoBanner = null; main.removeCallbacks(clearUndo)
        u.undo()
    }

    // ---------- ignorer un texte ou une image ----------

    /** Pages dont les blocs sont gardés en mémoire (les plus récentes). */
    fun workIds(): List<String> = synchronized(workInfos) { workInfos.keys.toList() }

    /** Redessine les pages en mémoire qui portent ce texte (résultat en cache : quelques instants) : après l'ajout ou le retrait d'une règle de texte. */
    fun reprocessForText(text: String) {
        val n = IgnoredMatch.normalize(text)
        for (id in workIds()) {
            val info = workInfo(id) ?: continue
            if (info.result.blocks.any { IgnoredMatch.same(IgnoredMatch.normalize(it.source), n) }) reprocess(id)
        }
    }

    /** Redessine les images données, celles qui ont les mêmes octets et celles laissées telles quelles : après l'ajout ou le retrait d'une règle d'image. */
    fun reprocessForImages(ids: List<String> = emptyList()) {
        val keys = ids.mapNotNull { workInfo(it)?.imageKey }.toSet()
        val all = LinkedHashSet<String>(ids)
        for (id in workIds()) if (workInfo(id)?.imageKey in keys) all.add(id)
        all.addAll(skipped.keys)
        for (id in all) reprocess(id)
    }

    /** Le texte touché longuement est-il ignoré par une règle ? L'interface propose alors de le rétablir plutôt que de le corriger. */
    fun peekIgnored(): Boolean {
        val t = peekTarget ?: return false
        val info = workInfo(t.imageId.removeSuffix("~up")) ?: return false
        return info.applied.editByBlock[t.bid]?.id?.startsWith(IGNORE_EDIT_PREFIX) == true
    }

    /** Garde la proposition « Corriger / supprimer » affichée pendant le choix « Ignorer ce texte… » (sinon elle s'effacerait au bout de 7 s). */
    fun holdPeek() { main.removeCallbacks(clearPeek) }

    /** La règle peut-elle se limiter à cette œuvre ? (la page ouverte doit être reconnue comme une œuvre) */
    fun peekHasSeries(): Boolean {
        val t = peekTarget ?: return false
        return workInfo(t.imageId.removeSuffix("~up"))?.series != null
    }

    /** Ne plus traduire ce texte, dans cette œuvre ou partout : la règle est enregistrée, les pages ouvertes sont redessinées et « Annuler » est proposé. */
    fun ignorePeekText(everywhere: Boolean) {
        val t = peekTarget ?: return
        peekTarget = null; main.removeCallbacks(clearPeek)
        val info = workInfo(t.imageId.removeSuffix("~up")) ?: return
        val src = (info.applied.page.blocks.firstOrNull { it.id == t.bid }?.source ?: t.src).trim()
        val scope = if (everywhere) IgnoredText.EVERYWHERE else (info.series ?: IgnoredText.EVERYWHERE)
        if (src.isEmpty() || !graph.ignored.addText(scope, src)) return
        graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", src).put("edit", if (scope == IgnoredText.EVERYWHERE) "texte ignoré partout" else "texte ignoré dans l'œuvre"))
        webView?.evaluateJavascript("window.__mt&&window.__mt.dropPatch(${JSONObject.quote(t.imageId)},${t.bid})", null)
        reprocessForText(src)
        showUndo("Texte ignoré") { graph.ignored.removeText(scope, src); reprocessForText(src) }
    }

    /** Rétablit un texte ignoré (appui long sur son emplacement) : toutes les règles qui le visent sont retirées. */
    fun unignorePeek() {
        val t = peekTarget ?: return
        peekTarget = null; main.removeCallbacks(clearPeek)
        val info = workInfo(t.imageId.removeSuffix("~up")) ?: return
        val src = info.applied.page.blocks.firstOrNull { it.id == t.bid }?.source ?: t.src
        val n = IgnoredMatch.normalize(src)
        val hits = graph.ignored.rules.texts.filter { r -> (r.scope == IgnoredText.EVERYWHERE || r.scope == info.series) && IgnoredMatch.same(n, IgnoredMatch.normalize(r.text)) }
        if (hits.isEmpty()) return
        for (r in hits) graph.ignored.removeText(r.scope, r.text)
        reprocessForText(src)
        showUndo("Texte rétabli") { for (r in hits) graph.ignored.addText(r.scope, r.text); reprocessForText(src) }
    }

    /** Page de l'image touchée longuement, avec ou sans traduction à cet endroit. */
    private fun peekImageId(): String? = (peekTarget?.imageId ?: peekEmpty?.imageId)?.removeSuffix("~up")

    /** Ne jamais traduire cette image (bandeau de début ou de fin de chapitre) : empreinte et vignette enregistrées, la traduction qu'elle portait est retirée et « Annuler » est proposé. */
    fun ignorePeekImage() {
        val id = peekImageId() ?: return
        peekTarget = null; peekEmpty = null; main.removeCallbacks(clearPeek); main.removeCallbacks(clearPeekEmpty)
        val info = workInfo(id) ?: return
        // octets d'origine : ceux de la fenêtre traitée, sauf si elle porte le bas de l'image du dessus (on reprend alors l'image telle qu'elle a été téléchargée)
        val orig = if (info.ctxH == 0) info.bytes else synchronized(recentBytes) { recentBytes[info.imageSrc] }
        val fp = orig?.let { ImagePrint.of(it) }
        val w = fp?.width ?: info.applied.page.imageWidth
        val h = fp?.height ?: (info.applied.page.imageHeight - info.ctxH)
        val rule = IgnoredImage("g" + java.util.UUID.randomUUID().toString().take(8), info.imageKey, w, h, if (fp != null && fp.usable) fp.hex() else "", "", System.currentTimeMillis())
        graph.ignored.addImage(rule, orig?.let { ImagePrint.thumbnail(it) })
        graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", info.imageSrc.takeLast(120)).put("edit", "image ignorée ${w}×$h"))
        reprocessForImages(listOf(id))
        showUndo("Image ignorée") { graph.ignored.removeImage(rule.id); reprocessForImages() }
    }

    /** L'image touchée longuement est-elle ignorée ? Elle est alors laissée telle quelle : on propose de la rétablir plutôt que d'y ajouter une traduction. */
    fun peekImageIgnored(): Boolean = peekImageId()?.let { skipped.containsKey(it) } == true

    /** Rétablit l'image ignorée touchée longuement : sa règle est retirée, la page est traduite à nouveau et « Annuler » est proposé. */
    fun unignorePeekImage() {
        val id = peekImageId() ?: return
        peekTarget = null; peekEmpty = null; main.removeCallbacks(clearPeek); main.removeCallbacks(clearPeekEmpty)
        val rule = skipped[id] ?: return
        val thumb = try { graph.ignored.thumbFile(rule.id).takeIf { it.exists() }?.readBytes() } catch (_: Throwable) { null }
        graph.ignored.removeImage(rule.id)
        graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", rule.sha.takeLast(12)).put("edit", "image rétablie"))
        reprocessForImages(listOf(id))
        showUndo("Image rétablie") { graph.ignored.addImage(rule, thumb); reprocessForImages(listOf(id)) }
    }

    // ---------- tests automatiques (builds debug) : gestes de l'appui long rejoués sans toucher la page ----------

    fun testWorkIds(): List<String> = workIds()

    /** Nombre d'images de la page actuellement laissées telles quelles par une règle d'image. */
    fun testSkippedCount(): Int = skipped.size

    /** Redessine toutes les pages en mémoire et les images laissées telles quelles (après avoir retiré des règles « ignorer »). */
    fun testReprocessAll() { val all = LinkedHashSet<String>(workIds()); all.addAll(skipped.keys); for (id in all) reprocess(id) }

    /** Comme un appui long sur la première traduction non supprimée : la proposition « Corriger / supprimer » s'affiche 30 s. Faux s'il n'y a aucune traduction. */
    fun testPeek(ignored: Boolean = false): Boolean {
        for (id in testWorkIds()) {
            val info = workInfo(id) ?: continue
            // première traduction non supprimée ; avec [ignored], premier texte masqué par une règle « ignorer »
            val b = info.applied.page.blocks.firstOrNull { val e = info.applied.editByBlock[it.id]; if (ignored) e?.id?.startsWith(IGNORE_EDIT_PREFIX) == true else e?.hidden != true } ?: continue
            peekEmpty = null; peekTarget = EditTarget(id, b.id, b.source, b.translated)
            main.removeCallbacks(clearPeek); main.postDelayed(clearPeek, 30_000)
            return true
        }
        return false
    }

    /** Pose une traduction écrite à la main sur la première traduction non supprimée (comme l'éditeur), puis redessine la page. */
    fun testSetTranslation(text: String): Boolean {
        for (id in testWorkIds()) {
            val info = workInfo(id) ?: continue
            val shown = info.applied.page.blocks.firstOrNull { info.applied.editByBlock[it.id]?.hidden != true } ?: continue
            val orig = info.result.blocks.firstOrNull { it.id == shown.id } ?: shown
            val prev = info.applied.editByBlock[shown.id]?.takeUnless { it.id.startsWith(IGNORE_EDIT_PREFIX) }
            val edit = prev?.copy(translation = text) ?: BlockEdit("e" + java.util.UUID.randomUUID().toString().take(8), EditKind.ADJUST, imageRect(orig.box, info.ctxH), orig.source, translation = text)
            graph.edits.upsert(info.imageKey, edit); reprocess(id)
            return true
        }
        return false
    }

    /** Retire toutes les corrections des pages en mémoire et les redessine. */
    fun testClearEdits() {
        for (id in testWorkIds()) {
            val info = workInfo(id) ?: continue
            for (e in graph.edits.forImage(info.imageKey)) graph.edits.remove(info.imageKey, e.id)
            reprocess(id)
        }
    }

    /** Ajoute un texte à la main à l'endroit touché (zone non traduite) : l'éditeur s'ouvre sur un rectangle à ajuster. */
    fun openNewEdit() {
        val sp = peekEmpty ?: return
        peekEmpty = null; main.removeCallbacks(clearPeekEmpty)
        if (workInfo(sp.imageId) == null) return
        editTarget = EditTarget(sp.imageId, NEW_BLOCK_ID, "", "", sp.x, sp.y)
    }
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
        stopWork(); resetCounters(); skipped.clear(); active = false; overlaysVisible = true
        graph.gemma.resetContext()
    }

    private fun resetCounters() { total = 0; done = 0; failed = 0; noText = 0; lastError = null; finishedIds.clear() }

    private fun stopWork() {
        queue.cancelAll()
        val lost: List<String>
        synchronized(backlog) {
            lost = submitted.filter { it !in finishedIds }
            backlog.clear(); submitted.clear()
        }
        // Les images qui attendaient ou étaient en cours sont perdues pour la collecte : la page les croit déjà données. Sans cela, après un passage sur « Original »
        // puis un retour sur « Traduit », elles n'étaient jamais redemandées (la barre de progression restait bloquée sous la moitié). On les fait oublier à la page :
        // elles seront redonnées au prochain départ, et retirées du total en attendant.
        if (lost.isNotEmpty()) {
            total = (total - lost.size).coerceAtLeast(done + failed)
            webView?.evaluateJavascript("window.__mt&&window.__mt.forget(${JSONArray(lost)})", null)
        }
    }

    private fun onCandidates(raw: String?) {
        if (raw.isNullOrBlank() || raw == "null") return
        val arr = try { JSONArray(raw) } catch (e: Exception) { lastError = "page : réponse du script illisible"; return }
        var added = 0
        synchronized(backlog) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pv = o.optJSONObject("prev"); val nx = o.optJSONObject("next")
                val c = Candidate(
                    o.getString("id"), o.getString("src"), o.getDouble("top"), o.getDouble("sy"),
                    height = o.optDouble("dh", 0.0), viewportH = o.optDouble("vh", 0.0), naturalH = o.optInt("h", 0),
                    prevId = pv?.optString("id")?.ifEmpty { null }, prevSrc = pv?.optString("src")?.ifEmpty { null }, prevH = pv?.optInt("h", 0) ?: 0, nextH = nx?.optInt("h", 0)?.takeIf { it > 0 },
                )
                if (submitted.add(c.id)) { backlog.add(c); added++ }
            }
            if (arr.length() > 0) { val last = arr.getJSONObject(arr.length() - 1); viewportY = last.optDouble("sy", viewportY); last.optDouble("vh", 0.0).takeIf { it > 0 }?.let { viewportH = it } }
            backlog.sortBy { it.priorityAt(viewportY, viewportH) }
        }
        total += added
        pump()
    }

    private fun pump() {
        synchronized(backlog) {
            queue.maxPending = if (thermalHot) 1 else graph.settingsStore.settings.maxPrefetchPages + 1
            ensureWatchdog()
            backlog.sortBy { it.priorityAt(viewportY, viewportH) }
            while (backlog.isNotEmpty()) {
                val c = backlog.first()
                // la priorité est relue à chaque choix : on traite ce que le lecteur regarde maintenant, pas ce qui était proche au repérage de l'image
                if (queue.submit(PageJob(c.id, c.priorityAt(viewportY, viewportH), { c.priorityAt(viewportY, viewportH) }) { cancelled -> process(c, cancelled) })) backlog.removeAt(0) else break
            }
        }
    }

    private fun process(c: Candidate, cancelled: () -> Boolean): PageOutcome {
        val pageUrl = currentUrl
        val pageSeries = BrowsingLibrary.seriesKey(pageUrl)
        BatchContext.cancelled.set(cancelled); BatchContext.order.set(c.top); BatchContext.lastBatch.remove()
        runStage[c.id] = "téléchargement"
        val bytes = try { fetch(c, pageUrl) } catch (e: Exception) {
            val f = PageOutcome.Failed("image", e.message ?: "téléchargement impossible")
            if (testing) logForTest(c, f, null, null, 0)
            return f
        }
        if (cancelled()) return PageOutcome.Failed("annulé", "")
        // image que l'utilisateur ne veut jamais voir traduite (bandeau de début ou de fin de chapitre) : ni lecture ni traduction, et la traduction qu'elle portait est retirée
        val ignoreRules = graph.ignored.rules
        if (ignoreRules.images.isNotEmpty()) {
            val hit = ignoreRules.imageMatch(sha256Hex(bytes), app.manhwa.ImagePrint.of(bytes))
            if (hit != null) {
                candidatesById[c.id] = c; skipped[c.id] = hit
                if (!cancelled()) sendOverlay(c.id, JSONObject().put("w", hit.width).put("h", hit.height).put("patches", JSONArray()))
                return PageOutcome.NoText(hit.width, hit.height)
            }
        }
        skipped.remove(c.id)
        val s = graph.settingsStore.settings
        val t0 = System.currentTimeMillis()
        // jonction avec l'image du dessus : l'image est traitée avec le bas de celle du dessus collé au-dessus d'elle, pour lire en entier un texte que le découpage a coupé en deux ;
        // chaque texte n'est traduit que par une des deux images (voir Seams.owns)
        synchronized(recentBytes) { recentBytes[c.src] = bytes }
        val imageH = if (c.naturalH > 0) c.naturalH else boundsHeight(bytes)
        val nextTakes = !testNoSeam && Seams.nextTakesOver(c.nextH)
        // (une image ignorée du dessus, bandeau de début de chapitre par exemple, ne sert pas de contexte : son texte ne doit pas être lu avec la page)
        val ctxWanted = if (testNoSeam || c.prevSrc == null || c.prevId == null || c.prevId?.let { skipped.containsKey(it) } == true) 0 else Seams.windowContext(imageH, c.prevH.takeIf { it > 0 })
        val window: SeamWindow? = if (ctxWanted > 0) {
            runStage[c.id] = "assemblage avec l'image du dessus"
            val prevSrc = c.prevSrc!!; val prevId = c.prevId!!
            val prevBytes = synchronized(recentBytes) { recentBytes[prevSrc] }
                ?: try { fetchImage(prevId, prevSrc, pageUrl).also { b -> synchronized(recentBytes) { recentBytes[prevSrc] = b } } } catch (_: Exception) { null }
            if (prevBytes != null) SeamWindows.build(bytes, prevBytes, ctxWanted) else null
        } else null
        val workBytes = window?.bytes ?: bytes
        val ctxH = window?.ctxH ?: 0
        val seamTag = if (ctxH > 0 || nextTakes) "+sm$ctxH" + (if (nextTakes) "n" else "") else ""
        val spec = LlmModels.specFor(s.engine)
        val translator: TranslationEngine = if (spec == null) (if (testBatch) graph.mlkitBatch else MlKitTranslation(graph.models)) else graph.gemmaBatch
        val enhanced = s.enhancedOcr && graph.paddleModels.available
        val ocrEngine = CombinedOcr(s.source, MlKitOcr(s.source), if (enhanced) graph.paddle else null, Lexicon.get(ctx), parallel = !thermalHot)
        val rereadOn = spec != null && s.source.code == "en" && graph.blockReader.available
        // « pp2 » : lecture des lignes inclinées sur recadrage redressé ; « rr » : relecture des textes douteux par Gemma (résultats différents, cache à part)
        // onomatopées laissées telles quelles (anglais) : clé de cache distincte, pour ne pas reprendre d'anciens résultats où elles étaient traduites
        val skipSfx = !s.translateSfx && s.source.code != "ja" && s.source.code != "ko" && s.source.code != "zh"
        val engineId = (if (spec == null) MlKitTranslation.ID else GemmaTranslator.engineId(spec)) + (if (enhanced) "+pp2" else "") + (if (rereadOn) "+rr1" else "") + (if (skipSfx) "+sfx0" else "") + seamTag
        var debug: DebugInfo? = null
        val lexicon = Lexicon.get(ctx)
        candidatesById[c.id] = c
        keysById[c.id] = CacheKey(sha256Hex(workBytes), s.source.code, s.target.code, engineId)
        // durées mesurées pour le journal : lecture du texte, traduction (le rendu est mesuré plus bas)
        val stages = LongArray(3)
        stageInfo[c.id] = stages
        // une seule lecture du texte à la fois (elle occupe déjà tous les cœurs) ; pendant ce temps l'autre page peut attendre sa traduction ou s'afficher
        val timedOcr = OcrEngine { b ->
            runStage[c.id] = "attente de la lecture du texte"
            synchronized(ocrLock) {
                runStage[c.id] = "lecture du texte"
                val t = SystemClock.uptimeMillis()
                try { ocrEngine.recognize(b).also { pageNotes[c.id] = StringBuilder(OcrDebug.note); pageTiming[c.id] = OcrDebug.timing } } finally { stages[0] += SystemClock.uptimeMillis() - t }
            }
        }
        val timedTr = TimedTranslator(translator, { stages[1] += it }) { runStage[c.id] = "traduction" }
        val pipeline = PagePipeline(
            graph.cache, timedOcr, timedTr, engineId,
            glossary = { graph.glossary.current(pageSeries) },
            useCache = !s.diagnostic && !testing, // en diagnostic on veut voir la détection réelle, pas un résultat en cache
            debugSink = { debug = it },
            // l'anglais est lu par OCR avec beaucoup de bruit : on écarte filigranes et débris, puis on répare mots collés et chiffres parasites
            // un texte libre sans zone du détecteur est écarté s'il ne contient aucun mot reconnu, même en gros lettrage (décor lu comme du texte : « IC », « RIC », « PY »)
            junk = { t, rel, anchored -> s.source.code != "ja" && s.source.code != "ko" && s.source.code != "zh" && (JunkFilter.isWatermark(t) || ((rel < 1.6f || !anchored) && JunkFilter.isDebris(t, lexicon)) || repeatedAcrossPages(t, keysById[c.id]?.imageHash ?: c.id)) },
            isSfx = { t, kind, rel -> skipSfx && Onomatopoeia.isSfx(t, kind, rel, lexicon) },
            repair = { t -> try { OcrRepair.dropJunkTail(OcrRepair.repair(t, lexicon, graph.gemma.chapterNames()), lexicon) } catch (e: Throwable) { t } },
            junkTail = { t -> try { OcrRepair.junkTail(t, lexicon) } catch (e: Throwable) { "" } },
            reader = if (rereadOn) graph.blockReader else null,
            // part de vrais mots anglais (noms propres du chapitre compris) : repère les lectures absurdes et vérifie les relectures
            plausibility = { t -> try { val names = graph.gemma.chapterNames(); OcrMerge.plausibility(t, lexicon, names) } catch (e: Throwable) { 1f } },
            log = { msg -> pageNotes.getOrPut(c.id) { StringBuilder() }.let { if (it.length < 3000) it.append(" [").append(msg).append("]") }; android.util.Log.i("MT", msg) },
            owns = { b -> !(ctxH > 0 || nextTakes) || Seams.owns(b.box, b.container, b.zone, ctxH, imageH, nextTakes) },
        )
        val out = pipeline.process(workBytes, s.source.code, s.target.code)
        // noms qui reviennent d'une page à l'autre : proposés dans le glossaire de l'œuvre (anglais seulement, le lexique est anglais)
        if (out is PageOutcome.Done && s.source.code != "ja" && s.source.code != "ko" && s.source.code != "zh") {
            try { synchronized(graph.names) { graph.names.observe(pageSeries, c.id, out.result.blocks.map { it.source }, lexicon) } } catch (_: Throwable) {}
        }
        // corrections manuelles de l'utilisateur (zone, texte, position, taille, police) : réappliquées à chaque affichage, indépendamment du cache
        val imageKey = sha256Hex(bytes)
        // une page où rien n'a été détecté garde aussi sa fenêtre : on peut y ajouter un texte à la main
        val basePage: PageResult? = when (out) {
            is PageOutcome.Done -> out.result
            is PageOutcome.NoText -> PageResult(out.imageWidth, out.imageHeight, emptyList())
            else -> null
        }
        // textes ignorés (dans cette œuvre ou partout) : même effet qu'une suppression, sans rien écrire dans les corrections ni toucher au résultat en cache ; la règle se retire à tout moment
        val ignoreEdits = if (basePage != null && ignoreRules.texts.isNotEmpty()) {
            basePage.blocks.filter { ignoreRules.textIgnored(pageSeries, it.source) }.map { b -> BlockEdit(IGNORE_EDIT_PREFIX + b.id, EditKind.ADJUST, imageRect(b.box, ctxH), b.source, hidden = true) }
        } else emptyList()
        val applied: EditApplier.Applied? = if (basePage != null) try { EditApplier.apply(basePage, graph.edits.forImage(imageKey) + ignoreEdits, ctxH.toFloat()) } catch (e: Throwable) { null } else null
        if (basePage != null) synchronized(workInfos) {
            workInfos[c.id] = WorkInfo(c.id, c.src, imageKey, workBytes, ctxH, basePage, applied ?: EditApplier.Applied(basePage, emptyMap(), emptyList(), emptyMap()), pageSeries)
        }
        var overlayForLog: JSONObject? = null
        var overlayUpForLog: JSONObject? = null
        BatchContext.lastBatch.get()?.let { batchInfo[c.id] = it }
        runStage[c.id] = "affichage"
        if (out is PageOutcome.Done && !out.fromCache) journalPage(c, pageUrl, engineId, s.target.code, out, System.currentTimeMillis() - t0)
        if (!cancelled()) {
            val tRender = SystemClock.uptimeMillis()
            val json = synchronized(renderLock) {
                when {
                    out is PageOutcome.Done -> PageRenderer.render(fonts, workBytes, applied?.page ?: out.result, s, debug, graph.fontMemory, pageSeries, applied?.render ?: emptyMap())
                    // page sans texte détecté mais avec des textes ajoutés à la main
                    out is PageOutcome.NoText && applied != null && applied.page.blocks.isNotEmpty() -> PageRenderer.render(fonts, workBytes, applied.page, s, debug, graph.fontMemory, pageSeries, applied.render)
                    s.diagnostic && debug != null -> OverlayBuilder.debugOnly(debug!!)
                    else -> null
                }
            }
            stages[2] = SystemClock.uptimeMillis() - tRender
            json?.optJSONArray("blocks")?.let { arr ->
                val m = HashMap<Int, String>()
                for (i in 0 until arr.length()) { val r = arr.getJSONObject(i); if (r.has("bid")) m[r.getInt("bid")] = r.optString("family") }
                fontByBlock[c.id] = m
            }
            var own = json; var up: JSONObject? = null
            if (json != null && window != null && (out is PageOutcome.Done || out is PageOutcome.NoText)) {
                // la superposition couvre la fenêtre : la partie sous la jonction se pose sur cette image, celle au-dessus sur l'image du dessus
                val sp = SeamOverlay.split(json, window.ctxH, window.width, window.imageH, c.prevH, c.prevId!!)
                own = sp.own; up = sp.up
            }
            if (own != null) sendOverlay(c.id, own)
            if (up != null) sendOverlay(c.id + "~up", up, c.prevId)
            overlayForLog = own; overlayUpForLog = up
            if (own != null && out is PageOutcome.Done) journalLayout(c, pageUrl, out, own)
        }
        // correction demandée sur cette page alors qu'elle n'était plus en mémoire : l'éditeur peut s'ouvrir
        pendingEdit?.let { pe -> if (pe.imageId == c.id && basePage != null) { pendingEdit = null; main.post { editTarget = pe } } }
        if (testing) logForTest(c, out, debug, overlayForLog, System.currentTimeMillis() - t0, if (window != null) window.width to window.imageH else null, overlayUpForLog)
        // les informations propres à cette page ne servent plus
        pageNotes.remove(c.id); pageTiming.remove(c.id); batchInfo.remove(c.id); fetchInfo.remove(c.id); stageInfo.remove(c.id)
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
                        .put("family", r.optString("family")).put("fontInfo", r.optString("fontInfo")).put("fg", r.optString("fg")).put("orig", r.opt("orig")).put("cont", r.opt("cont")).put("zone", r.opt("zoneBox"))
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
                .put("ocrMs", stageInfo[c.id]?.get(0) ?: 0L).put("trMs", stageInfo[c.id]?.get(1) ?: 0L).put("ocrT", pageTiming[c.id] ?: "").put("batch", batchInfo[c.id]?.first ?: 0).put("trEngineMs", batchInfo[c.id]?.second ?: 0L).put("sys", diag())
                .put("backend", graph.gemma.backendLabel).put("gpuSetting", graph.settingsStore.settings.useGpu)
                .put("dbg", (pageNotes[c.id]?.toString() ?: "").takeLast(1500)).put("blocks", blocks),
        )
    }

    // ---------- éditeur de correction d'un texte ----------

    /** Texte lu dans une zone dessinée, sa traduction et la géométrie de ses lignes (coordonnées de la fenêtre traitée). */
    class RegionRead(val source: String, val translation: String, val lineBoxes: List<CRect>, val lineTexts: List<String>, val vertical: Boolean, val lineHeight: Float)

    /** Bloc en cours de correction : page traitée, bloc visé (dans la page corrigée), correction déjà enregistrée pour lui, nom de l'œuvre. */
    class EditSession(val info: WorkInfo, val block: TranslatedBlock, val existing: BlockEdit?, val seriesName: String?, val isNew: Boolean = false)

    /** Aperçu en direct : couches d'effacement et de texte du bloc, mises en page avec la correction en cours. À recycler après usage. */
    class EditPreview(val sink: LayerSink, val blockId: Int)

    /** Champs saisis dans l'éditeur (coordonnées de la fenêtre traitée). */
    class EditFields(
        val src: String, val translation: String, val hidden: Boolean, val region: UserRegion?, val zoneChanged: Boolean, val read: RegionRead?,
        val dx: Float, val dy: Float, val scale: Float, val fontId: String?, val precise: Boolean = false,
    )

    /** Police de la bibliothèque (aperçu des polices dans l'éditeur). */
    fun typeface(id: String): android.graphics.Typeface = fonts.family(id)

    /** Police retenue par le dernier rendu de chaque bloc (page → bloc → police) : l'aperçu de l'éditeur montre la même que la page. */
    private val fontByBlock = ConcurrentHashMap<String, Map<Int, String>>()

    /** Ouvre l'éditeur sur un texte ; si la page n'est plus en mémoire, elle est d'abord retraitée (résultat en cache : quelques secondes). */
    fun openEdit(t: EditTarget) {
        val id = t.imageId.removeSuffix("~up")      // la partie d'un texte posée sur l'image du dessus a été traitée par cette image-ci
        val tt = t.copy(imageId = id)
        if (workInfo(id) != null) { editTarget = tt; return }
        val cand = candidatesById[id]
        if (cand == null) { lastError = "Cette page n'est plus en mémoire : rouvrez-la puis recommencez."; return }
        pendingEdit = tt
        queue.submit(PageJob(cand.id + "#edit" + System.nanoTime(), 0) { cancelled -> process(cand, cancelled) })
    }

    fun editSession(t: EditTarget): EditSession? {
        val info = workInfo(t.imageId) ?: return null
        val name = currentSeriesKey?.let { SeriesHint.fromUrl(currentUrl) ?: it }
        if (t.bid == NEW_BLOCK_ID) {
            // texte à ajouter : rectangle de départ autour du point touché (coordonnées de l'image, décalées du contexte de la fenêtre)
            val iw = info.result.imageWidth.toFloat(); val ih = info.result.imageHeight.toFloat()
            val w = (iw * 0.45f).coerceIn(160f, 520f).coerceAtMost(iw); val h = kotlin.math.min(130f, ih)
            val cx = (if (t.x >= 0f) t.x else iw / 2f); val cy = (if (t.y >= 0f) t.y else ih / 2f) + info.ctxH
            val l = (cx - w / 2f).coerceIn(0f, kotlin.math.max(0f, iw - w)); val tp = (cy - h / 2f).coerceIn(0f, kotlin.math.max(0f, ih - h))
            val box = CRect(l, tp, kotlin.math.min(iw, l + w), kotlin.math.min(ih, tp + h))
            return EditSession(info, TranslatedBlock(NEW_BLOCK_ID, box, "", "", false, 40f, emptyList(), emptyList()), null, name, isNew = true)
        }
        val b = info.applied.page.blocks.firstOrNull { it.id == t.bid } ?: return null
        return EditSession(info, b, info.applied.editByBlock[b.id]?.takeUnless { it.id.startsWith(IGNORE_EDIT_PREFIX) }, name)
    }

    /** Police automatique du bloc, telle que le dernier rendu de la page l'a choisie. */
    fun autoFont(info: WorkInfo, blockId: Int): String? = fontByBlock[info.imageId]?.get(blockId)

    private fun imageRect(r: CRect, ctxH: Int) = r.offset(0f, -ctxH.toFloat())

    /** Correction correspondant aux champs saisis (coordonnées de l'image). */
    fun makeEdit(sn: EditSession, f: EditFields): BlockEdit {
        val info = sn.info; val shift = info.ctxH
        val orig = info.result.blocks.firstOrNull { it.id == sn.block.id }          // bloc détecté (null pour un bloc défini à la main)
        val prev = sn.existing
        val manual = sn.isNew || prev?.kind == EditKind.MANUAL || f.zoneChanged
        val id = prev?.id ?: ("e" + java.util.UUID.randomUUID().toString().take(8))
        val shown = sn.block
        val newSrc = f.src.trim(); val newTr = f.translation.trim()
        val boxes = (f.read?.lineBoxes ?: shown.lineBoxes).map { imageRect(it, shift) }
        val texts = f.read?.lineTexts ?: shown.lineTexts
        val matchBox = if (prev?.kind == EditKind.MANUAL) prev.match else (orig?.box?.let { imageRect(it, shift) } ?: prev?.match)
        val matchSrc = if (prev?.kind == EditKind.MANUAL) prev.matchSource else (orig?.source ?: prev?.matchSource ?: "")
        return if (manual) {
            val region = f.region?.shifted(-shift.toFloat()) ?: prev?.region ?: UserRegion(imageRect(shown.zone ?: shown.box, shift))
            BlockEdit(
                id, EditKind.MANUAL, matchBox, matchSrc, region, newSrc, newTr, f.hidden, f.dx, f.dy, f.scale, f.fontId,
                f.read?.vertical ?: shown.vertical, f.read?.lineHeight ?: shown.lineHeight, boxes, texts, shown.kind, shown.bubble?.let { imageRect(it, shift) }, f.precise,
            )
        } else {
            BlockEdit(
                id, EditKind.ADJUST, matchBox, matchSrc, null,
                if (newSrc != shown.source.trim()) newSrc else prev?.source,
                if (newTr != shown.translated.trim()) newTr.ifEmpty { null } else prev?.translation,
                f.hidden, f.dx, f.dy, f.scale, f.fontId, precise = f.precise,
            )
        }
    }

    /** Aperçu du bloc avec la correction en cours : même chemin que l'affichage définitif (corrections appliquées au résultat de la page, puis rendu du seul bloc visé). */
    fun renderEditPreview(sn: EditSession, draft: BlockEdit): EditPreview? {
        val info = sn.info
        val others = graph.edits.forImage(info.imageKey).filter { it.id != draft.id }
        val withAuto = if (draft.fontId == null) autoFont(info, sn.block.id) else null            // police automatique du dernier rendu, pour que l'aperçu soit fidèle
        val applied = EditApplier.apply(info.result, others + draft.copy(fontId = draft.fontId ?: withAuto, dx = 0f, dy = 0f), info.ctxH.toFloat())
        val id = applied.editByBlock.entries.firstOrNull { it.value.id == draft.id }?.key ?: return null
        val sink = LayerSink()
        synchronized(renderLock) { PageRenderer.render(fonts, info.bytes, applied.page, graph.settingsStore.settings, null, null, null, applied.render, only = id, sink = sink) }
        return if (sink.erase != null && sink.text != null && sink.rect != null) EditPreview(sink, id) else { sink.recycle(); null }
    }

    /** Traduction d'un texte, comme la page le ferait (corrections et glossaire de l'œuvre compris). */
    fun translateText(text: String): String {
        val s = graph.settingsStore.settings
        val g = graph.glossary.current(currentSeriesKey)
        g.correctionFor(text)?.let { return it }
        val spec = LlmModels.specFor(s.engine)
        val translator: TranslationEngine = if (spec == null) MlKitTranslation(graph.models) else graph.gemma
        val llm = translator.usesGlossaryPrompt()
        val prot = if (llm) Protected(TextNormalizer.fixCase(text), emptyMap()) else g.protect(text).let { it.copy(text = TextNormalizer.fixCase(it.text)) }
        val terms = if (llm) g.terms().filter { t -> text.contains(t.source, ignoreCase = true) } else emptyList()
        val out = (if (llm) translator.translateWithGlossary(listOf(prot.text), s.source.code, s.target.code, terms) else translator.translate(listOf(prot.text), s.source.code, s.target.code)).single()
        val restored = if (llm) out else (g.restore(out, prot) ?: translator.translate(listOf(text), s.source.code, s.target.code).single())
        return TextNormalizer.matchCase(text, restored)
    }

    /** Lit le texte d'une zone dessinée (lecture du texte sur un recadrage) puis le traduit ; null si rien n'a pu être lu. */
    fun readRegion(info: WorkInfo, region: UserRegion): RegionRead? {
        val iw = info.result.imageWidth.toFloat(); val ih = info.result.imageHeight.toFloat()
        val b = region.bounds
        val pad = kotlin.math.max(8f, 0.06f * kotlin.math.max(b.width, b.height))
        val crop = CRect(kotlin.math.max(0f, b.left - pad), kotlin.math.max(0f, b.top - pad), kotlin.math.min(iw, b.right + pad), kotlin.math.min(ih, b.bottom + pad))
        if (crop.width < 8f || crop.height < 8f) return null
        val dec = decodeCrop(info.bytes, crop, 2400) ?: return null
        var bmp = dec.first; var scale = dec.second
        // petit recadrage : agrandi, la lecture du texte est bien meilleure sur des lettres de 25 px que de 10 px
        if (kotlin.math.max(bmp.width, bmp.height) < 500) {
            val k = 2f
            val big = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
            bmp.recycle(); bmp = big; scale *= k
        }
        val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }.toByteArray()
        bmp.recycle()
        val s = graph.settingsStore.settings
        val enhanced = s.enhancedOcr && graph.paddleModels.available
        val lexicon = Lexicon.get(ctx)
        val ocr = CombinedOcr(s.source, MlKitOcr(s.source), if (enhanced) graph.paddle else null, lexicon, parallel = !thermalHot)
        val res = synchronized(ocrLock) { ocr.recognize(jpg) }
        val lines = res.lines.map { l -> l.copy(box = CRect(crop.left + l.box.left / scale, crop.top + l.box.top / scale, crop.left + l.box.right / scale, crop.top + l.box.bottom / scale)) }
            .filter { l -> l.text.any { it.isLetterOrDigit() } && region.contains(l.box.centerX, l.box.centerY) }
        if (lines.isEmpty()) return null
        val blocks = BlockGrouper().group(lines, emptyList())
        val sorted: List<OcrLine> = blocks.flatMap { it.lines }
        val raw = blocks.joinToString(" ") { it.text }.trim()
        if (raw.isEmpty()) return null
        val names = graph.gemma.chapterNames()
        val text = try { OcrRepair.dropJunkTail(OcrRepair.repair(raw, lexicon, names), lexicon) } catch (e: Throwable) { raw }
        val tr = try { translateText(text) } catch (e: Throwable) { "" }
        val vertical = blocks.firstOrNull()?.vertical ?: false
        val hs = sorted.map { if (vertical) it.box.width else it.lineHeight }.sorted()
        return RegionRead(text, tr, sorted.map { it.box }, sorted.map { it.text }, vertical, if (hs.isEmpty()) 0f else hs[hs.size / 2])
    }

    /** Enregistre la correction, les termes du glossaire, la consigne au journal, puis redessine la page. */
    fun saveEdit(sn: EditSession, edit: BlockEdit, terms: List<GlossaryEntry>, termsForSeries: Boolean) {
        val info = sn.info
        editTarget = null
        // un texte ajouté sans traduction n'a rien à afficher : on n'enregistre pas de zone vide
        if (sn.isNew && edit.translation.isNullOrBlank()) return
        graph.edits.upsert(info.imageKey, edit)
        val series = info.series
        var termAdded = false
        for (t in terms) {
            val a = t.source.trim(); val bb = t.target.trim()
            if (a.isEmpty() || bb.isEmpty()) continue
            if (termsForSeries && series != null) graph.glossary.addForSeries(series, a, bb) else graph.glossary.add(a, bb)
            termAdded = true
        }
        // la même traduction est reprise partout où le même texte est lu (comme avant) ; la correction porte sur le texte détecté, pas sur celui que l'on vient de corriger à la main
        val detected = info.result.blocks.firstOrNull { it.id == sn.block.id }?.source ?: sn.block.source
        val manualTr = edit.translation
        if (manualTr != null && manualTr.isNotBlank() && edit.kind == EditKind.ADJUST && detected.isNotBlank()) graph.glossary.addCorrection(detected, manualTr)
        val summary = buildList {
            if (edit.kind == EditKind.MANUAL) add("zone")
            if (edit.translation != null) add("traduction")
            if (edit.source != null) add("lecture")
            if (edit.hidden) add("non traduit")
            if (edit.dx != 0f || edit.dy != 0f) add("position %.0f,%.0f".format(java.util.Locale.ROOT, edit.dx, edit.dy))
            if (edit.scale != 1f) add("taille %.2f".format(java.util.Locale.ROOT, edit.scale))
            if (edit.fontId != null) add("police ${edit.fontId}")
            if (termAdded) add("glossaire")
        }.joinToString(", ")
        graph.journal.add(
            JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", sn.block.source).put("before", sn.block.translated)
                .put("after", edit.translation ?: "").put("edit", summary).put("term", terms.filter { it.source.isNotBlank() && it.target.isNotBlank() }.joinToString("; ") { "${it.source} = ${it.target}" }),
        )
        // des termes ajoutés sans traduction saisie : la page est retraduite avec eux ; sinon, le résultat en cache est repris et seul le rendu est refait (quelques instants)
        if (termAdded && edit.translation == null) keysById[info.imageId]?.let { graph.cache.remove(it) }
        reprocess(info.imageId)
    }

    /** Rétablit la détection et la traduction automatiques d'un bloc. */
    fun resetEdit(sn: EditSession) {
        val e = sn.existing ?: run { editTarget = null; return }
        editTarget = null
        graph.edits.remove(sn.info.imageKey, e.id)
        graph.journal.add(JSONObject().put("t", System.currentTimeMillis()).put("type", "correction").put("url", currentUrl).put("src", sn.block.source).put("edit", "rétabli"))
        reprocess(sn.info.imageId)
    }

    private fun reprocess(imageId: String, attempt: Int = 0, epoch: Int = pageEpoch) {
        if (epoch != pageEpoch) return   // on a changé de page entre-temps : l'ancienne image n'est plus là
        val cand = candidatesById[imageId] ?: return
        val accepted = queue.submit(PageJob(cand.id + "#fix" + System.nanoTime(), 0) { cancelled -> process(cand, cancelled) })
        // la file est pleine (le chapitre se traduit encore) : le rafraîchissement n'est pas perdu, on réessaie dans un instant (une suppression ou son annulation s'affichent alors à coup sûr)
        if (!accepted && attempt < 40) main.postDelayed({ reprocess(imageId, attempt + 1, epoch) }, 750)
    }

    /** Recadrage d'une image encodée (coordonnées de la fenêtre) ramené à [maxSide] pixels au plus : bitmap et échelle (pixels du bitmap par pixel de la fenêtre). */
    fun decodeCrop(bytes: ByteArray, crop: CRect, maxSide: Int): Pair<Bitmap, Float>? {
        return try {
            val dec = android.graphics.BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) ?: return null
            try {
                val l = crop.left.toInt().coerceIn(0, dec.width - 1); val t = crop.top.toInt().coerceIn(0, dec.height - 1)
                val r = crop.right.toInt().coerceIn(l + 1, dec.width); val b = crop.bottom.toInt().coerceIn(t + 1, dec.height)
                var sample = 1
                while (kotlin.math.max(r - l, b - t) / sample > maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = dec.decodeRegion(android.graphics.Rect(l, t, r, b), opts) ?: return null
                bmp to (bmp.width.toFloat() / (r - l))
            } finally { dec.recycle() }
        } catch (e: Throwable) { null }
    }

    fun dismissEdit() { editTarget = null }

    /** Envoie l'overlay à la page par morceaux d'environ 1,2 Mo : évite une énorme chaîne JavaScript d'un seul bloc. */
    private fun sendOverlay(id: String, json: JSONObject, target: String? = null) {
        val patches = json.optJSONArray("patches") ?: JSONArray()
        val chunks = ArrayList<JSONObject>()
        var cur = JSONArray(); var size = 0
        for (i in 0 until patches.length()) {
            val p = patches.getJSONObject(i)
            val len = p.optString("png").length
            if (size > 0 && size + len > 1_200_000) { chunks.add(JSONObject().put("w", json.get("w")).put("h", json.get("h")).put("patches", cur).also { if (target != null) it.put("target", target) }); cur = JSONArray(); size = 0 }
            cur.put(p); size += len
        }
        val first = JSONObject().put("w", json.get("w")).put("h", json.get("h")).put("patches", cur).also { if (target != null) it.put("target", target) }
        json.optJSONArray("dbg")?.let { first.put("dbg", it) }
        chunks.add(first)
        chunks.forEachIndexed { i, ch ->
            if (i > 0) ch.put("append", true)
            main.post { webView?.evaluateJavascript("window.__mt&&window.__mt.apply('$id', $ch)", null) }
        }
    }

    private fun logForTest(c: Candidate, out: PageOutcome, debug: DebugInfo?, overlay: JSONObject?, ms: Long, dims: Pair<Int, Int>? = null, overlayUp: JSONObject? = null) {
        val e = JSONObject().put("id", c.id).put("src", c.src).put("ms", ms).put("fetch", fetchInfo[c.id]?.first ?: "").put("fetchMs", fetchInfo[c.id]?.second ?: 0L).put("fetchWhy", fetchInfo[c.id]?.third ?: "")
            .put("ocrMs", stageInfo[c.id]?.get(0) ?: 0L).put("trMs", stageInfo[c.id]?.get(1) ?: 0L).put("renderMs", stageInfo[c.id]?.get(2) ?: 0L).put("ocrT", pageTiming[c.id] ?: "").put("batch", batchInfo[c.id]?.first ?: 0)
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
        if (dims != null) e.put("w", dims.first).put("h", dims.second).put("seam", true)       // fenêtre de jonction : la taille est celle de l'image, pas de la fenêtre
        if (overlay != null) e.put("overlay", overlay)
        if (overlayUp != null) e.put("overlayUp", overlayUp)
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
    private fun downloadAttempts(src: String, pageUrl: String): List<Map<String, String>> {
        val seen = synchronized(seenImageHeaders) { seenImageHeaders[src] }
        val origin = originOf(pageUrl)
        val sameOrigin = origin != null && originOf(src) == origin
        val site = if (sameOrigin) "same-origin" else if (Sites.sameSite(Sites.hostOf(src), Sites.hostOf(pageUrl))) "same-site" else "cross-site"
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
    private fun fetch(c: Candidate, pageUrl: String): ByteArray = fetchImage(c.id, c.src, pageUrl, record = true)

    private fun boundsHeight(bytes: ByteArray): Int {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outHeight
    }

    /** Octets d'une image de la page : lus dans la WebView, sinon téléchargés avec les en-têtes de la page, sinon demandés à la page. [record] : note l'origine et la durée au journal. */
    private fun fetchImage(id: String, src: String, pageUrl: String, record: Boolean = false): ByteArray {
        val t0 = SystemClock.uptimeMillis()
        if (record) { fetchInfo.remove(id); stageInfo.remove(id) }
        var why = ""
        fun got(b: ByteArray, how: String): ByteArray { if (record) fetchInfo[id] = Triple(how, SystemClock.uptimeMillis() - t0, why); return b }
        if (src == "js:") return got(fetchViaPage(id), "page (blob)")
        val scheme = src.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") throw IOException("Adresse d'image non prise en charge")
        if (!testNoDevTools) devTools.imageBytes(src, MAX_IMAGE_BYTES)?.let { return got(it, "page") }
        why = if (testNoDevTools) "lecture par la WebView coupée (test)" else devTools.lastReason
        var code = 0
        var failure: String? = null
        for (headers in downloadAttempts(src, pageUrl)) {
            try {
                val r = download(src, headers)
                if (r.first == 200 && r.second != null) return got(r.second!!, "réseau")
                code = r.first
                if (code != 401 && code != 403) break
            } catch (e: IOException) { failure = e.message; break }
        }
        try { return got(fetchViaPage(id), "page (fetch)") } catch (_: Exception) {}
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
            main.post { peekEmpty = null; peekTarget = EditTarget(id, bid.toIntOrNull() ?: -1, src, tr); main.removeCallbacks(clearPeek); main.postDelayed(clearPeek, 7000) }
        }
        // appui long sur une image sans traduction à cet endroit (texte non détecté, zone ignorée) : seulement si l'image a été traitée
        @JavascriptInterface fun onPeekEmpty(id: String, x: Double, y: Double) {
            main.post {
                if (workInfo(id.removeSuffix("~up")) != null) { peekTarget = null; peekEmpty = EmptySpot(id.removeSuffix("~up"), x.toFloat(), y.toFloat()); main.removeCallbacks(clearPeekEmpty); main.postDelayed(clearPeekEmpty, 7000) }
            }
        }
        @JavascriptInterface fun onImagesChanged() { main.post { if (active) start(false) } }
        @JavascriptInterface fun onViewport(y: Double, h: Double) { viewportY = y; if (h > 0) viewportH = h }
        @JavascriptInterface fun onB64(id: String, data: String) { b64Waiters[id]?.complete(data) }
        @JavascriptInterface fun onEdit(id: String, bid: String, src: String, tr: String) { main.post { openEdit(EditTarget(id, bid.toIntOrNull() ?: -1, src, tr)) } }
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
    private class TimedTranslator(private val inner: TranslationEngine, private val add: (Long) -> Unit, private val enter: () -> Unit = {}) : TranslationEngine {
        private inline fun <T> timed(f: () -> T): T { enter(); val t = SystemClock.uptimeMillis(); try { return f() } finally { add(SystemClock.uptimeMillis() - t) } }
        override fun translate(texts: List<String>, source: String, target: String): List<String> = timed { inner.translate(texts, source, target) }
        override fun usesGlossaryPrompt(): Boolean = inner.usesGlossaryPrompt()
        override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> = timed { inner.translateWithGlossary(texts, source, target, glossary) }
    }

    // ---------- veille des traitements ----------
    private val watchdogOn = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun ensureWatchdog() {
        if (!watchdogOn.compareAndSet(false, true)) return
        Thread({
            while (true) {
                try { Thread.sleep(10_000) } catch (_: InterruptedException) { return@Thread }
                try {
                    val now = SystemClock.uptimeMillis()
                    for ((id, t0) in runStart.entries.toList()) {
                        val el = now - t0
                        if (el > STALL_REPORT_MS && now - (stallReported[id] ?: 0L) > 120_000) { stallReported[id] = now; graph.journal.add(stallEntry(id, el)) }
                        if (el > STALL_ABANDON_MS) { runStart.remove(id); queue.abandon(id) }   // un fil bloqué est remplacé : la file continue avec les pages suivantes
                    }
                    // du travail attend mais rien ne tourne depuis 20 s : les fils manquants sont relancés, la file est relancée, et le cas est noté au journal
                    val snap = queue.snapshot()
                    val waiting = synchronized(backlog) { backlog.size }
                    if (waiting > 0 && snap.running == 0) {
                        if (++idleTicks >= 2) {
                            idleTicks = 0
                            val restarted = queue.ensureWorkers()
                            graph.journal.add(
                                JSONObject().put("t", System.currentTimeMillis()).put("type", "idle").put("backlog", waiting).put("pending", snap.pending).put("alive", snap.alive).put("restarted", restarted)
                                    .put("total", total).put("done", done).put("failed", failed).put("maxPending", queue.maxPending).put("sys", diag()),
                            )
                            main.post { pump() }
                        }
                    } else idleTicks = 0
                } catch (_: Throwable) {}
            }
        }, "mt-watchdog").also { it.isDaemon = true; it.start() }
    }

    /** Journal : une page est bloquée depuis longtemps (étape, mémoire, pile des fils de traitement) : indique où le travail est coincé. */
    private fun stallEntry(id: String, elapsedMs: Long): JSONObject {
        val threads = JSONObject()
        for ((t, frames) in Thread.getAllStackTraces()) {
            if (t.name.startsWith("page-queue") || t.name.startsWith("lecture-ocr") || t.name.startsWith("traduction-groupee") || t.name.startsWith("warm-up") || t.name.startsWith("mt-llm"))
                threads.put(t.name, JSONArray(frames.take(14).map { it.toString() }))
        }
        return JSONObject().put("t", System.currentTimeMillis()).put("type", "stall").put("page", id).put("elapsedMs", elapsedMs).put("stage", runStage[id] ?: "")
            .put("running", JSONArray(queue.runningIds())).put("sys", diag()).put("threads", threads)
    }

    /** Mémoire (Java, native, disponible) et état thermique : à joindre aux entrées du journal pour comprendre un ralentissement ou un arrêt. */
    private fun diag(): JSONObject = try {
        val rt = Runtime.getRuntime()
        val mi = android.app.ActivityManager.MemoryInfo()
        ctx.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(mi)
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        JSONObject().put("java", (rt.totalMemory() - rt.freeMemory()) shr 20).put("javaMax", rt.maxMemory() shr 20)
            .put("native", android.os.Debug.getNativeHeapAllocatedSize() shr 20).put("avail", mi.availMem shr 20).put("low", mi.lowMemory)
            .put("thermal", if (android.os.Build.VERSION.SDK_INT >= 29) (pm?.currentThermalStatus ?: -1) else -1).put("hot", thermalHot)
    } catch (_: Throwable) { JSONObject() }

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

    companion object {
        /** Une traduction supprimée d'un geste peut être rétablie pendant 3 secondes. */
        const val UNDO_MS = 3000L
        /** Les corrections issues d'un texte ignoré ne sont jamais enregistrées : leur identifiant commence ainsi. */
        const val IGNORE_EDIT_PREFIX = "ign:"
        const val MAX_IMAGE_BYTES = 30 * 1024 * 1024
        /** Une page bloquée depuis 1 min est signalée au journal ; depuis 4 min, son fil est abandonné et remplacé. */
        const val STALL_REPORT_MS = 60_000L
        const val STALL_ABANDON_MS = 240_000L
    }
}
