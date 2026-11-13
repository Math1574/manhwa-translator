package app.manhwa.core

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Mises à jour de l'application : une publication GitHub réduite à ce qui sert à décider (l'analyse du JSON se fait côté application, le cœur n'a pas de bibliothèque JSON),
 * le choix de la version à proposer, le nettoyage des « nouveautés » et le téléchargement vérifié et reprenable du fichier.
 */
data class ReleaseEntry(
    val tag: String,
    val name: String,
    val notes: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: String,
    /** Nom du fichier APK joint à la publication, null s'il n'y en a pas. */
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long,
    /** Somme de contrôle telle que GitHub la donne (« sha256:… »), ou null. */
    val apkDigest: String?,
)

object UpdatePlanner {
    /** Nom du fichier APK publié par la compilation en ligne. */
    const val APK_NAME = "ManhwaTranslator-debug.apk"
    private val TAG = Regex("^build-(\\d{1,9})$")
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** Numéro de compilation d'une étiquette « build-123 » ; null pour toute autre étiquette (modèles, brouillons, etc.). */
    fun buildOf(tag: String): Int? = TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Publications installables plus récentes que la version [current], la plus récente d'abord (une seule par numéro). */
    fun newer(current: Int, all: List<ReleaseEntry>): List<ReleaseEntry> =
        all.filter { r ->
            val n = buildOf(r.tag)
            n != null && n > current && !r.draft && !r.prerelease && r.apkName == APK_NAME && !r.apkUrl.isNullOrBlank() && r.apkSize > 0
        }.sortedByDescending { buildOf(it.tag) }.distinctBy { buildOf(it.tag) }

    /** Somme SHA-256 en hexadécimal minuscule extraite de « sha256:… » ; null si absente ou mal formée. */
    fun sha256Of(digest: String?): String? {
        val h = digest?.trim()?.lowercase()?.removePrefix("sha256:") ?: return null
        return h.takeIf { HEX64.matches(it) }
    }

    private val TRAILER = Regex("^(co-authored-by|claude-session|signed-off-by|generated with|reviewed-by)\\b.*", RegexOption.IGNORE_CASE)

    /**
     * Texte des nouveautés d'une publication : les mentions techniques de fin de message (coauteur, session…) et les lignes de lien sont retirées,
     * les lignes vides répétées réduites à une seule ; coupé à [maxChars] sur une fin de ligne, avec « … » quand il y a une suite.
     */
    fun cleanNotes(raw: String, maxChars: Int = 900): String {
        val kept = ArrayList<String>()
        for (l in raw.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val t = l.trimEnd()
            val s = t.trim()
            if (TRAILER.matches(s) || s.startsWith("🤖") || (s.startsWith("http") && !s.contains(' '))) continue
            if (s.isEmpty() && (kept.isEmpty() || kept.last().isEmpty())) continue
            kept.add(t)
        }
        while (kept.isNotEmpty() && kept.last().isEmpty()) kept.removeAt(kept.size - 1)
        val whole = kept.joinToString("\n")
        if (whole.length <= maxChars) return whole
        val sb = StringBuilder()
        for (l in kept) {
            val extra = (if (sb.isEmpty()) 0 else 1) + l.length
            if (sb.length + extra > maxChars) {
                if (sb.isEmpty()) sb.append(l.take(maxChars).trimEnd())
                break
            }
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(l)
        }
        return sb.toString().trimEnd() + " …"
    }

    /** Nouveautés de plusieurs publications (la plus récente d'abord), chacune sous son titre « Version N » ; le total est borné par [maxChars]. */
    fun combine(releases: List<ReleaseEntry>, perRelease: Int = 700, maxChars: Int = 2600): String {
        val sb = StringBuilder()
        var shown = 0
        for (r in releases) {
            val n = buildOf(r.tag) ?: continue
            val notes = cleanNotes(r.notes, perRelease).ifBlank { "Pas de détail pour cette version." }
            val block = "Version $n\n$notes"
            if (sb.isNotEmpty() && sb.length + block.length + 2 > maxChars) break
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(block); shown++
        }
        val hidden = releases.count { buildOf(it.tag) != null } - shown
        if (hidden > 0) sb.append("\n\n… et ").append(hidden).append(if (hidden > 1) " versions plus anciennes." else " version plus ancienne.")
        return sb.toString()
    }

    /** Vrai s'il faut interroger GitHub : jamais fait, délai écoulé, ou horloge revenue en arrière. */
    fun dueForCheck(nowMs: Long, lastMs: Long, intervalMs: Long = 6L * 3600 * 1000): Boolean = lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= intervalMs
}

/** Un fichier d'un module téléchargeable (par exemple le lecteur de japonais) : nom, taille, somme de contrôle et adresse. */
data class PackFile(val name: String, val size: Long, val sha256: String, val url: String)

object PackManifest {
    private val NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,80}$")
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    /**
     * Lignes « nom taille sha256 adresse », séparées par des espaces ; les lignes vides ou commençant par # sont ignorées.
     * Une ligne mal formée (nom avec chemin, taille nulle, somme invalide, adresse non sécurisée) rend tout le manifeste invalide : liste vide.
     */
    fun parse(text: String?): List<PackFile> {
        if (text == null) return emptyList()
        val out = ArrayList<PackFile>()
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty() || l.startsWith("#")) continue
            val p = l.split(Regex("\\s+"))
            if (p.size != 4) return emptyList()
            val size = p[1].toLongOrNull() ?: return emptyList()
            if (!NAME.matches(p[0]) || size <= 0 || !HEX64.matches(p[2]) || !p[3].startsWith("https://") || out.any { it.name == p[0] }) return emptyList()
            out.add(PackFile(p[0], size, p[2].lowercase(), p[3]))
        }
        return out
    }
}

/** Téléchargement d'un fichier avec reprise (en-tête Range), suivi des redirections, contrôle de la taille et de la somme SHA-256. */
object FileDownloader {
    /**
     * @param expectedSize taille attendue en octets (0 : inconnue, pas de contrôle)
     * @param expectedSha256 somme attendue, ou null (pas de contrôle)
     * @throws DownloadError, ChecksumMismatch
     */
    fun download(
        url: String, dest: File, expectedSize: Long, expectedSha256: String?, requireHttps: Boolean = true,
        onProgress: (Long, Long) -> Unit = { _, _ -> }, isCancelled: () -> Boolean = { false },
    ) {
        if (requireHttps && !url.startsWith("https://")) throw DownloadError("URL non sécurisée refusée")
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        var offset = if (part.exists()) part.length() else 0L
        if (expectedSize > 0 && offset > expectedSize) { part.delete(); offset = 0L }
        if (!(expectedSize > 0 && offset == expectedSize)) fetch(url, part, offset, expectedSize, onProgress, isCancelled)
        if (expectedSize > 0 && part.length() != expectedSize) {
            val got = part.length()
            if (got > expectedSize) part.delete()            // trop long : ce n'est pas le bon fichier ; trop court : on garde le morceau pour reprendre
            throw DownloadError("Fichier incomplet ($got/$expectedSize octets)")
        }
        if (expectedSha256 != null) {
            val actual = sha256(part)
            if (!actual.equals(expectedSha256, ignoreCase = true)) { part.delete(); throw ChecksumMismatch(expectedSha256, actual) }
        }
        dest.delete()
        if (!part.renameTo(dest)) throw DownloadError("Impossible de finaliser le fichier")
    }

    private fun fetch(url: String, part: File, start: Long, expectedSize: Long, onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean) {
        var offset = start
        val conn = try { URL(url).openConnection() as HttpURLConnection } catch (e: IOException) { throw DownloadError("URL invalide", e) }
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "ManhwaTranslator")
            if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
            val code = try { conn.responseCode } catch (e: IOException) { throw DownloadError("Connexion impossible : ${e.message}", e) }
            when (code) {
                200 -> { offset = 0; part.delete() }            // le serveur ignore Range : on repart de zéro
                206 -> {}
                416 -> { part.delete(); throw DownloadError("Reprise impossible, relancez le téléchargement") }
                else -> throw DownloadError("Erreur HTTP $code")
            }
            val total = if (expectedSize > 0) expectedSize else (conn.contentLengthLong.takeIf { it > 0 }?.plus(offset) ?: 0L)
            RandomAccessFile(part, "rw").use { raf ->
                raf.setLength(offset)
                raf.seek(offset)
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) throw DownloadError("Téléchargement annulé")
                        val n = try { input.read(buf) } catch (e: IOException) { throw DownloadError("Connexion interrompue : ${e.message}", e) }
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        offset += n
                        onProgress(offset, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
