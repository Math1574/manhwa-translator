package app.manhwa

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebResourceResponse
import app.manhwa.core.ResType
import app.manhwa.core.BlockLog
import app.manhwa.core.FilterEngine
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Listes de filtres du bloqueur : une copie est embarquée dans l'application (assets/adblock/filters.txt, assemblée par tools/adblock/build_lists.py),
 * et l'application télécharge la version la plus récente du même fichier (publiée dans le dépôt, mise à jour chaque semaine) tous les quelques jours.
 * Le moteur se construit en arrière-plan au démarrage ; tant qu'il n'est pas prêt, seule l'ancienne liste de domaines sert de filet.
 */
class AdFilters(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "adblock")
    private val main = Handler(Looper.getMainLooper())

    @Volatile var engine: FilterEngine? = null; private set
    @Volatile var summary: String = "Chargement des listes de filtres…"; private set
    @Volatile var updating: Boolean = false; private set
    /** Règles supplémentaires (tests automatiques uniquement). */
    @Volatile var testRules: List<String> = emptyList()
    val log = BlockLog()

    fun start() {
        Thread({
            loadEngine()
            try { if (System.currentTimeMillis() - File(dir, "stamp").lastModified() > UPDATE_EVERY_MS) downloadUpdate() } catch (_: Throwable) {}
        }, "adfilters").also { it.isDaemon = true; it.priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    /** Reconstruit le moteur (après un téléchargement, ou quand les tests ajoutent des règles). */
    fun rebuild() { Thread({ loadEngine() }, "adfilters-rebuild").also { it.isDaemon = true }.start() }

    private fun source(): () -> Sequence<String> {
        val downloaded = File(dir, "filters.txt")
        val extra = testRules
        return {
            val fromFile = downloaded.exists() && downloaded.length() > 500_000
            sequence {
                val r = (if (fromFile) downloaded.inputStream() else ctx.assets.open("adblock/filters.txt")).bufferedReader()
                r.use { rd -> var l = rd.readLine(); while (l != null) { yield(l); l = rd.readLine() } }
                for (x in extra) yield(x)
            }
        }
    }

    private val generation = java.util.concurrent.atomic.AtomicInteger()
    private val ready = java.util.concurrent.CountDownLatch(1)

    /** Attend au plus [ms] ms que le moteur soit prêt : la toute première page après le démarrage ne doit pas se charger sans filtre. */
    fun awaitEngine(ms: Long) {
        if (engine != null) return
        try { ready.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: InterruptedException) {}
    }

    private fun loadEngine() {
        // plusieurs chargements peuvent se chevaucher (démarrage, téléchargement, règles de test) : seul le plus récent, qui a lu les règles les plus récentes, est retenu
        val gen = generation.incrementAndGet()
        try {
            val t0 = System.currentTimeMillis()
            val e = FilterEngine.build(source())
            if (gen != generation.get()) return
            engine = e
            ready.countDown()
            val downloaded = File(dir, "filters.txt")
            val whenText = if (downloaded.exists() && downloaded.length() > 500_000) "mises à jour le " + SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(Date(downloaded.lastModified()))
            else "version livrée avec l'application"
            summary = "${e.networkRules} règles réseau et ${e.cosmeticRules} règles de masquage, $whenText (chargées en ${(System.currentTimeMillis() - t0) / 1000.0} s)"
        } catch (t: Throwable) {
            summary = "Listes indisponibles (${t.javaClass.simpleName}) : seule la liste de secours est active"
        }
    }

    /** Télécharge la dernière version des listes ; renvoie un message pour l'utilisateur. Appelée hors du fil principal. */
    fun downloadUpdate(): String {
        updating = true
        try {
            dir.mkdirs()
            val etagFile = File(dir, "etag")
            val c = URL(UPDATE_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000
            if (File(dir, "filters.txt").exists() && etagFile.exists()) c.setRequestProperty("If-None-Match", etagFile.readText())
            val code = c.responseCode
            if (code == 304) { File(dir, "stamp").writeText(System.currentTimeMillis().toString()); return "Les listes sont déjà à jour." }
            if (code != 200) return "Mise à jour impossible (réponse $code du serveur)."
            val tmp = File(dir, "filters.tmp")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val head = tmp.bufferedReader().use { it.readLine() ?: "" }
            if (tmp.length() < 500_000 || !head.startsWith("! Manhwa Translator")) { tmp.delete(); return "Fichier reçu invalide : l'ancienne version est conservée." }
            tmp.renameTo(File(dir, "filters.txt"))
            c.getHeaderField("ETag")?.let { etagFile.writeText(it) }
            File(dir, "stamp").writeText(System.currentTimeMillis().toString())
            loadEngine()
            return "Listes mises à jour : $summary"
        } catch (t: Throwable) {
            return "Mise à jour impossible : ${t.message ?: t.javaClass.simpleName}"
        } finally { updating = false }
    }

    fun updateAsync(done: (String) -> Unit) {
        if (updating) return
        Thread({ val msg = downloadUpdate(); main.post { done(msg) } }, "adfilters-update").also { it.isDaemon = true }.start()
    }

    /** Feuille de style de masquage pour ce site : règles des listes + quelques masquages génériques sûrs. */
    fun cosmeticCss(host: String): String {
        val site = engine?.cosmeticCss(host) ?: ""
        return GENERIC_CSS + site
    }

    companion object {
        private val PIXEL = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)

        /**
         * Réponse donnée à une requête bloquée : toujours un code 200 avec un corps vide du bon type (un pixel transparent pour une image), jamais une erreur
         * ni une redirection, pour que les scripts de la page continuent sans se casser ; l'origine est répétée pour que les requêtes inter-sites ne déclenchent pas d'erreur CORS.
         */
        fun blockedResponse(type: Int, origin: String?): WebResourceResponse {
            val mime = when (type) {
                ResType.IMAGE -> "image/gif"; ResType.SCRIPT -> "text/javascript"; ResType.STYLESHEET -> "text/css"; ResType.SUBDOCUMENT -> "text/html"; else -> "text/plain"
            }
            val headers = HashMap<String, String>()
            headers["Cache-Control"] = "no-store"
            headers["Access-Control-Allow-Origin"] = origin ?: "*"
            if (origin != null) headers["Access-Control-Allow-Credentials"] = "true"
            return WebResourceResponse(mime, "utf-8", 200, "OK", headers, java.io.ByteArrayInputStream(if (type == ResType.IMAGE) PIXEL else ByteArray(0)))
        }

        const val UPDATE_URL = "https://raw.githubusercontent.com/Math1574/manhwa-translator/main/app/src/main/assets/adblock/filters.txt"
        private const val UPDATE_EVERY_MS = 3L * 24 * 3600 * 1000
        /** Encarts publicitaires reconnaissables partout, sans risque de masquer du contenu. */
        private const val GENERIC_CSS = "ins.adsbygoogle,[id^=\"google_ads_iframe\"],iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication.com\"],iframe[src*=\"adsterra\"]{display:none!important}"
    }
}
