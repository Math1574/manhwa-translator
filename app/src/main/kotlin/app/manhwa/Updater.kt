package app.manhwa

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.manhwa.core.ChecksumMismatch
import app.manhwa.core.DownloadError
import app.manhwa.core.FileDownloader
import app.manhwa.core.ReleaseEntry
import app.manhwa.core.UpdatePlanner
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

/**
 * Mise à jour intégrée : consulte les publications GitHub du projet (une par compilation, étiquette « build-N »), propose la plus récente avec ses nouveautés,
 * la télécharge (reprise possible, taille et somme de contrôle vérifiées, signature comparée à celle de l'application installée) puis ouvre l'installation d'Android.
 * Rien ne se télécharge sans appui sur le bouton ; la consultation (quelques Ko) se fait au plus toutes les six heures, plus au démarrage qui suit une mise à jour pour en afficher les nouveautés.
 */
class UpdateManager(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val dir = File(ctx.cacheDir, "updates")

    /** Numéro de compilation de l'application installée (0 pour une compilation sur un poste de développement). */
    val current: Int = BuildConfig.BUILD_NUMBER

    var state by mutableStateOf(UpdateState.IDLE); private set
    /** Versions plus récentes que celle installée, la plus récente d'abord. */
    var newer by mutableStateOf<List<ReleaseEntry>>(emptyList()); private set
    val latest: ReleaseEntry? get() = newer.firstOrNull()
    var progress by mutableFloatStateOf(0f); private set
    var receivedBytes by mutableLongStateOf(0L); private set
    var message by mutableStateOf<String?>(null); private set
    var lastCheckMs by mutableLongStateOf(prefs.getLong("lastCheck", 0L)); private set
    /** Nouveautés à montrer une fois, au premier lancement après une mise à jour. */
    var whatsNew by mutableStateOf<String?>(null); private set

    private val cancelFlag = AtomicBoolean(false)
    @Volatile private var busy = false
    @Volatile private var seenBeforeUpdate: Int? = null

    /** À appeler au lancement de l'application. */
    fun onStart() {
        if (current <= 0) return
        val seen = prefs.getInt("seenBuild", 0)
        when {
            seen == 0 || current < seen -> prefs.edit().putInt("seenBuild", current).apply()     // première installation, ou retour à une version plus ancienne : rien à raconter
            current > seen -> { seenBeforeUpdate = seen; check(manual = false) }                  // mise à jour faite : on récupère ses nouveautés
            UpdatePlanner.dueForCheck(System.currentTimeMillis(), lastCheckMs) -> check(manual = false)
        }
        thread(name = "update-clean") { cleanDir(keep = null, keepLatest = true) }
    }

    fun dismissWhatsNew() { whatsNew = null }

    /** Interroge GitHub. [manual] : l'utilisateur l'a demandé, l'état et les erreurs s'affichent ; sinon la vérification est silencieuse. */
    fun check(manual: Boolean = true) {
        if (busy) return
        busy = true
        if (manual) { state = UpdateState.CHECKING; message = null }
        thread(name = "update-check") {
            try {
                val all = parse(fetchReleases())
                val now = System.currentTimeMillis()
                prefs.edit().putLong("lastCheck", now).apply(); lastCheckMs = now
                seenBeforeUpdate?.let { seen ->
                    val installed = all.filter { r -> val n = UpdatePlanner.buildOf(r.tag); n != null && n > seen && n <= current && !r.draft }.sortedByDescending { UpdatePlanner.buildOf(it.tag) }
                    val text = UpdatePlanner.combine(installed)
                    if (text.isNotBlank()) whatsNew = text
                    prefs.edit().putInt("seenBuild", current).apply()
                    seenBeforeUpdate = null
                }
                val list = UpdatePlanner.newer(current, all)
                newer = list
                message = null
                state = when {
                    list.isEmpty() -> UpdateState.UP_TO_DATE
                    alreadyDownloaded(list.first()) -> UpdateState.READY
                    else -> UpdateState.AVAILABLE
                }
            } catch (e: Exception) {
                if (manual) { state = UpdateState.ERROR; message = "Impossible de vérifier les mises à jour : " + (e.message ?: e.javaClass.simpleName) }
            } finally {
                busy = false
            }
        }
    }

    /** Télécharge la version proposée (le fichier se reprend là où il s'est arrêté si la connexion a coupé). */
    fun download() {
        val r = latest ?: return
        val url = r.apkUrl ?: return
        if (busy) return
        busy = true
        cancelFlag.set(false)
        state = UpdateState.DOWNLOADING; progress = 0f; receivedBytes = 0L; message = null
        thread(name = "update-download") {
            try {
                val dest = fileFor(r)
                cleanDir(keep = dest, keepLatest = false)
                var lastPermille = -1
                FileDownloader.download(
                    url, dest, r.apkSize, UpdatePlanner.sha256Of(r.apkDigest),
                    onProgress = { done, total ->
                        val permille = if (total > 0) (done * 1000 / total).toInt() else 0
                        if (permille != lastPermille) { lastPermille = permille; receivedBytes = done; progress = if (total > 0) done.toFloat() / total else 0f }
                    },
                    isCancelled = { cancelFlag.get() },
                )
                val problem = packageProblem(dest)
                if (problem != null) { dest.delete(); state = UpdateState.ERROR; message = problem } else { state = UpdateState.READY }
            } catch (e: ChecksumMismatch) {
                state = UpdateState.ERROR; message = "Le fichier reçu est altéré (somme de contrôle différente) : réessayez."
            } catch (e: DownloadError) {
                if (cancelFlag.get()) { state = UpdateState.AVAILABLE; message = "Téléchargement interrompu : il reprendra là où il s'est arrêté." }
                else { state = UpdateState.ERROR; message = e.message ?: "Téléchargement impossible" }
            } catch (e: Exception) {
                state = UpdateState.ERROR; message = "Téléchargement impossible : " + (e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun cancel() { cancelFlag.set(true) }

    /** Ouvre l'installation d'Android pour la version téléchargée ; la première fois, Android demande d'abord d'autoriser l'application à installer. */
    fun install() {
        val r = latest ?: return
        val f = fileFor(r)
        if (!f.exists()) { state = UpdateState.AVAILABLE; return }
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            message = "Android demande d'abord votre accord : activez « Autoriser depuis cette source » dans la page qui s'ouvre, revenez ici puis appuyez de nouveau sur Installer."
            try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
            return
        }
        message = null
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            state = UpdateState.ERROR; message = "Impossible d'ouvrir l'installation : " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fileFor(r: ReleaseEntry) = File(dir, "ManhwaTranslator-build-${UpdatePlanner.buildOf(r.tag)}.apk")

    /** Un fichier complet d'une consultation précédente (taille exacte) est réutilisé sans le retélécharger. */
    private fun alreadyDownloaded(r: ReleaseEntry): Boolean {
        val f = fileFor(r)
        return f.exists() && f.length() == r.apkSize && packageProblem(f) == null
    }

    /**
     * Efface les anciens téléchargements ; [keep] et son fichier partiel sont conservés, ainsi que la version la plus récente déjà reçue si [keepLatest]
     * (à condition qu'elle soit plus récente que la version installée : un APK déjà installé n'a plus rien à faire dans le cache).
     */
    private fun cleanDir(keep: File?, keepLatest: Boolean) {
        val files = dir.listFiles() ?: return
        fun buildOf(f: File) = f.name.removePrefix("ManhwaTranslator-build-").removeSuffix(".apk").toIntOrNull() ?: 0
        val newest = if (keepLatest) files.filter { it.name.endsWith(".apk") && buildOf(it) > current }.maxByOrNull { buildOf(it) } else null
        for (f in files) {
            val base = f.name.removeSuffix(".part")
            if (keep != null && base == keep.name) continue
            if (newest != null && base == newest.name) continue
            f.delete()
        }
    }

    /** Raison pour laquelle ce fichier ne pourra pas s'installer par-dessus l'application (autre application, autre clé de signature), ou null s'il convient. */
    @Suppress("DEPRECATION")
    private fun packageProblem(f: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = try { pm.getPackageArchiveInfo(f.absolutePath, flags) } catch (_: Throwable) { null } ?: return "Le fichier reçu n'est pas une application valide : réessayez."
        if (info.packageName != ctx.packageName) return "Le fichier reçu n'est pas Manhwa Translator."
        val mine = try { signers(pm.getPackageInfo(ctx.packageName, flags)) } catch (_: Throwable) { emptySet() }
        val theirs = signers(info)
        if (mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isEmpty()) {
            return "Cette version est signée avec une autre clé que l'application installée : Android refuserait de l'installer par-dessus. " +
                "Désinstallez l'application une seule fois, puis installez cette version ; les suivantes se feront sans désinstaller."
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signers(p: PackageInfo): Set<String> {
        val sigs: Array<android.content.pm.Signature> = if (Build.VERSION.SDK_INT >= 28) {
            val si = p.signingInfo
            if (si == null) emptyArray() else if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else (p.signatures ?: emptyArray())
        return sigs.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    private fun fetchReleases(): String {
        val c = URL(API).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "ManhwaTranslator")
            val code = c.responseCode
            if (code == 403 || code == 429) throw IllegalStateException("GitHub limite temporairement les requêtes (code $code), réessayez dans un moment")
            if (code != 200) throw IllegalStateException("réponse inattendue de GitHub (code $code)")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String): List<ReleaseEntry> {
        val arr = JSONArray(json)
        val out = ArrayList<ReleaseEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val assets = o.optJSONArray("assets") ?: JSONArray()
            var apk: org.json.JSONObject? = null
            for (k in 0 until assets.length()) {
                val a = assets.optJSONObject(k) ?: continue
                if (a.optString("name") == UpdatePlanner.APK_NAME && a.optString("state", "uploaded") == "uploaded") { apk = a; break }
            }
            val url = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://github.com/") }
            out.add(
                ReleaseEntry(
                    tag = o.optString("tag_name"), name = if (o.isNull("name")) "" else o.optString("name"), notes = if (o.isNull("body")) "" else o.optString("body"),
                    draft = o.optBoolean("draft"), prerelease = o.optBoolean("prerelease"), publishedAt = o.optString("published_at"),
                    apkName = apk?.optString("name"), apkUrl = url, apkSize = apk?.optLong("size") ?: 0L,
                    apkDigest = if (apk != null && apk.has("digest") && !apk.isNull("digest")) apk.optString("digest") else null,
                ),
            )
        }
        return out
    }

    private companion object { const val API = "https://api.github.com/repos/Math1574/manhwa-translator/releases?per_page=30" }
}
