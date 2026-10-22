package app.manhwa.core

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ModelSpec(val id: String, val url: String, val sizeBytes: Long, val sha256: String, val fileName: String)

sealed class ModelStatus {
    object Missing : ModelStatus()
    data class Downloading(val downloaded: Long, val total: Long) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

class ConfirmationRequired(val sizeBytes: Long) : Exception("Téléchargement de ${sizeBytes / 1_000_000} Mo : confirmation explicite requise")
class InsufficientSpace(val needed: Long, val free: Long) : Exception("Espace insuffisant : ${needed / 1_000_000} Mo nécessaires, ${free / 1_000_000} Mo libres")
class ChecksumMismatch(val expected: String, val actual: String) : Exception("Somme de contrôle invalide")
class DownloadError(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Téléchargement vérifié et reprenable des modèles ; stockage dans le dossier privé de l'application. */
class ModelStore(
    private val dir: File,
    private val freeSpace: () -> Long = { dir.usableSpace },
    private val requireHttps: Boolean = true,
    private val largeThresholdBytes: Long = 100L * 1024 * 1024,
) {
    private val errors = HashMap<String, String>()
    private val progress = HashMap<String, Pair<Long, Long>>()

    init { dir.mkdirs() }

    fun fileOf(spec: ModelSpec) = File(dir, spec.fileName)
    private fun partOf(spec: ModelSpec) = File(dir, spec.fileName + ".part")
    private fun markerOf(spec: ModelSpec) = File(dir, spec.fileName + ".ok")

    @Synchronized fun status(spec: ModelSpec): ModelStatus {
        progress[spec.id]?.let { return ModelStatus.Downloading(it.first, it.second) }
        errors[spec.id]?.let { return ModelStatus.Error(it) }
        val f = fileOf(spec)
        val ok = markerOf(spec)
        return if (f.exists() && f.length() == spec.sizeBytes && ok.exists() && ok.readText().trim() == spec.sha256) ModelStatus.Ready
        else ModelStatus.Missing
    }

    fun delete(spec: ModelSpec) {
        fileOf(spec).delete(); partOf(spec).delete(); markerOf(spec).delete()
        synchronized(this) { errors.remove(spec.id) }
    }

    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * @param confirmed l'utilisateur a explicitement accepté un téléchargement volumineux.
     * @throws ConfirmationRequired, InsufficientSpace, ChecksumMismatch, DownloadError
     */
    fun download(spec: ModelSpec, confirmed: Boolean, onProgress: (Long, Long) -> Unit = { _, _ -> }, isCancelled: () -> Boolean = { false }) {
        if (status(spec) == ModelStatus.Ready) return
        if (spec.sizeBytes >= largeThresholdBytes && !confirmed) throw ConfirmationRequired(spec.sizeBytes)
        if (requireHttps && !spec.url.startsWith("https://")) throw DownloadError("URL non sécurisée refusée")

        val part = partOf(spec)
        val already = if (part.exists()) part.length() else 0L
        val needed = (spec.sizeBytes - already) + spec.sizeBytes / 10 + 50L * 1024 * 1024 // marge de 10 % + 50 Mo
        val free = freeSpace()
        if (free < needed) throw InsufficientSpace(needed, free)

        synchronized(this) { errors.remove(spec.id); progress[spec.id] = already to spec.sizeBytes }
        try {
            fetch(spec, part, already, onProgress, isCancelled)
            val actual = sha256File(part)
            if (!actual.equals(spec.sha256, ignoreCase = true)) {
                part.delete()
                throw ChecksumMismatch(spec.sha256, actual)
            }
            val dst = fileOf(spec)
            dst.delete()
            if (!part.renameTo(dst)) throw DownloadError("Impossible de finaliser le fichier")
            markerOf(spec).writeText(spec.sha256)
        } catch (e: Exception) {
            synchronized(this) { errors[spec.id] = e.message ?: e.javaClass.simpleName }
            throw e
        } finally {
            synchronized(this) { progress.remove(spec.id) }
        }
    }

    private fun fetch(spec: ModelSpec, part: File, start: Long, onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean) {
        var offset = start
        val conn = try { URL(spec.url).openConnection() as HttpURLConnection } catch (e: IOException) { throw DownloadError("URL invalide", e) }
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
            val code = try { conn.responseCode } catch (e: IOException) { throw DownloadError("Connexion impossible : ${e.message}", e) }
            when (code) {
                200 -> { offset = 0; part.delete() }          // le serveur ignore Range : on repart de zéro
                206 -> {}
                416 -> { part.delete(); throw DownloadError("Reprise impossible, relancez le téléchargement") }
                else -> throw DownloadError("Erreur HTTP $code")
            }
            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(offset)
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) throw DownloadError("Téléchargement annulé")
                        val n = try { input.read(buf) } catch (e: IOException) { throw DownloadError("Connexion interrompue : ${e.message}", e) }
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        offset += n
                        synchronized(this) { progress[spec.id] = offset to spec.sizeBytes }
                        onProgress(offset, spec.sizeBytes)
                    }
                }
            }
            if (offset != spec.sizeBytes) throw DownloadError("Fichier incomplet ($offset/${spec.sizeBytes} octets)")
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256File(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
