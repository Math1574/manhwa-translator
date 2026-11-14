package app.manhwa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateListOf
import app.manhwa.core.Fingerprint
import app.manhwa.core.IgnoreRules
import app.manhwa.core.IgnoredImage
import app.manhwa.core.IgnoredText
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Textes et images que l'utilisateur ne veut plus voir traduits (voir [IgnoreRules]), enregistrés dans un fichier. Une petite vignette de chaque image ignorée est gardée à côté,
 * pour la reconnaître dans la liste des réglages.
 */
class IgnoreStore(private val file: File, private val thumbDir: File) {
    /** Règles en vigueur : copie immuable, sûre à lire depuis les fils de traitement. */
    @Volatile var rules: IgnoreRules = try { if (file.exists()) IgnoreRules.parse(file.readText()) else IgnoreRules() } catch (_: Throwable) { IgnoreRules() }
        private set

    /** Pour l'interface : se mettent à jour à chaque changement. */
    val texts = mutableStateListOf<IgnoredText>().apply { addAll(rules.texts) }
    val images = mutableStateListOf<IgnoredImage>().apply { addAll(rules.images) }

    /** Vrai si la règle est nouvelle. */
    @Synchronized fun addText(scope: String, text: String): Boolean {
        val next = rules.withText(scope, text)
        if (next === rules) return false
        commit(next); return true
    }

    @Synchronized fun removeText(scope: String, text: String) { commit(rules.withoutText(scope, text)) }

    @Synchronized fun addImage(img: IgnoredImage, thumb: ByteArray?) {
        if (thumb != null) try { thumbDir.mkdirs(); File(thumbDir, img.id + ".jpg").writeBytes(thumb) } catch (_: Throwable) {}
        commit(rules.withImage(img))
    }

    @Synchronized fun removeImage(id: String) {
        try { File(thumbDir, "$id.jpg").delete() } catch (_: Throwable) {}
        commit(rules.withoutImage(id))
    }

    fun thumbFile(id: String): File = File(thumbDir, "$id.jpg")

    private fun commit(next: IgnoreRules) {
        rules = next
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(next.serialize())
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        } catch (_: Throwable) {}
        texts.clear(); texts.addAll(next.texts)
        images.clear(); images.addAll(next.images)
    }
}

/** Empreinte visuelle et vignette d'une image encodée (voir [Fingerprint]). */
object ImagePrint {
    /** Image décodée au plus près de 256 à 512 points sur son grand côté (assez pour l'empreinte, sans charger toute l'image) ; null si elle ne se lit pas. */
    private fun decodeSmall(bytes: ByteArray): Triple<Bitmap, Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        val w = o.outWidth; val h = o.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= 256) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return Triple(bmp, w, h)
    }

    /** Empreinte de l'image : luminosité moyenne de chaque case d'une grille de 17 × 16 (une moyenne, pas un échantillon : l'empreinte ne dépend presque pas de la taille de l'image). */
    fun of(bytes: ByteArray): Fingerprint? = try {
        val (bmp, w, h) = decodeSmall(bytes) ?: return null
        val bw = bmp.width; val bh = bmp.height
        val px = IntArray(bw * bh); bmp.getPixels(px, 0, bw, 0, 0, bw, bh); bmp.recycle()
        val cols = Fingerprint.COLS; val rows = Fingerprint.ROWS
        val sum = LongArray(cols * rows); val cnt = IntArray(cols * rows)
        for (y in 0 until bh) {
            val cy = y * rows / bh
            for (x in 0 until bw) {
                val c = px[y * bw + x]; val cx = x * cols / bw
                sum[cy * cols + cx] += (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
                cnt[cy * cols + cx]++
            }
        }
        Fingerprint.fromGrid(w, h, IntArray(cols * rows) { if (cnt[it] > 0) (sum[it] / cnt[it]).toInt() else 255 })
    } catch (_: Throwable) { null }

    /** Vignette JPEG de 160 points au plus sur le grand côté. */
    fun thumbnail(bytes: ByteArray): ByteArray? = try {
        val (bmp, _, _) = decodeSmall(bytes) ?: return null
        val k = 160f / maxOf(bmp.width, bmp.height)
        val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * k).toInt()), maxOf(1, (bmp.height * k).toInt()), true) else bmp
        val out = ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.JPEG, 70, out)
        if (small !== bmp) small.recycle(); bmp.recycle()
        out.toByteArray()
    } catch (_: Throwable) { null }
}
