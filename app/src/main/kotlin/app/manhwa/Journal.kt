package app.manhwa

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de traduction : une ligne JSON par page traduite (texte lu, traduction, problèmes détectés) et par correction manuelle.
 * Reste sur le téléphone ; l'utilisateur peut l'exporter pour signaler des erreurs. Taille limitée (les plus anciennes entrées sont supprimées).
 */
class TranslationJournal(private val file: File) {
    @Synchronized fun add(entry: JSONObject) {
        try {
            file.appendText(entry.toString() + "\n")
            if (file.length() > MAX_BYTES) trim()
        } catch (_: Throwable) {}
    }

    @Synchronized fun sizeBytes(): Long = if (file.exists()) file.length() else 0L
    @Synchronized fun entries(): Int = if (file.exists()) file.useLines { it.count() } else 0
    @Synchronized fun clear() { file.delete() }

    private fun trim() {
        val lines = file.readLines()
        file.writeText(lines.drop(lines.size / 2).joinToString("\n") + "\n")
    }

    /** Copie le journal dans le cache et ouvre le partage Android (messagerie, stockage, etc.). */
    @Synchronized fun share(ctx: Context) {
        if (!file.exists()) return
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "journal-traduction-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".txt")
        file.copyTo(out, overwrite = true)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        ctx.startActivity(Intent.createChooser(send, "Exporter le journal de traduction").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object { const val MAX_BYTES = 8L * 1024 * 1024 }
}
