package app.manhwa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Petites icônes des sites, celles que la page annonce : gardées sur le téléphone pour les tuiles de l'accueil.
 * Rien n'est téléchargé en plus, c'est l'icône que le navigateur reçoit déjà avec la page ; sans icône, la tuile affiche la première lettre du site.
 */
class FaviconStore(private val dir: File) {
    private val memory = HashMap<String, ImageBitmap?>()

    private fun file(key: String) = File(dir, key.lowercase().replace(Regex("[^a-z0-9.-]"), "_") + ".png")

    private fun sizeOf(f: File): Int {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return maxOf(o.outWidth, o.outHeight)
    }

    /** Range l'icône d'un site, réduite à 96 px au plus ; une icône déjà rangée n'est remplacée que par une plus grande. Fil principal. */
    fun save(key: String, icon: Bitmap?) {
        if (icon == null || icon.width < 16 || icon.height < 16) return
        try {
            val f = file(key)
            val size = minOf(96, maxOf(icon.width, icon.height))
            if (f.exists() && sizeOf(f) >= size) return
            dir.mkdirs()
            val scale = 96f / maxOf(icon.width, icon.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(icon, (icon.width * scale).toInt().coerceAtLeast(1), (icon.height * scale).toInt().coerceAtLeast(1), true) else icon
            f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            memory.remove(key)
        } catch (_: Throwable) {}
    }

    fun get(key: String): ImageBitmap? = memory.getOrPut(key) {
        try { file(key).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } } catch (_: Throwable) { null }
    }
}
