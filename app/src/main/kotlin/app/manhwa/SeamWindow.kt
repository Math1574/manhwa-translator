package app.manhwa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Fenêtre de traitement d'une image collée à celle du dessus : le bas de cette dernière ([ctxH] px) est joint au-dessus de l'image elle-même ([imageH] px). */
class SeamWindow(val bytes: ByteArray, val ctxH: Int, val width: Int, val imageH: Int)

object SeamWindows {
    /**
     * Assemble la fenêtre (sans perte : l'effacement et le dessin se font sur les vrais pixels). Retourne null si les images ne s'assemblent pas
     * (illisibles, largeurs différentes) : l'image est alors traitée seule.
     */
    fun build(imageBytes: ByteArray, prevBytes: ByteArray, wantedCtx: Int): SeamWindow? {
        val bi = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bi)
        val bp = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(prevBytes, 0, prevBytes.size, bp)
        val w = bi.outWidth; val h = bi.outHeight
        if (w <= 0 || h <= 0 || bp.outWidth != w || bp.outHeight <= 0) return null
        val ctx = min(wantedCtx, bp.outHeight)
        if (ctx <= 0) return null
        var img: Bitmap? = null; var band: Bitmap? = null; var out: Bitmap? = null
        try {
            img = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
            @Suppress("DEPRECATION")
            val dec = BitmapRegionDecoder.newInstance(prevBytes, 0, prevBytes.size, false) ?: return null
            try { band = dec.decodeRegion(Rect(0, bp.outHeight - ctx, w, bp.outHeight), BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) } finally { dec.recycle() }
            val b = band ?: return null
            out = Bitmap.createBitmap(w, ctx + h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(b, 0f, 0f, null)
            canvas.drawBitmap(img, 0f, ctx.toFloat(), null)
            val bos = ByteArrayOutputStream(1 shl 20)
            out.compress(Bitmap.CompressFormat.PNG, 100, bos)
            return SeamWindow(bos.toByteArray(), ctx, w, h)
        } catch (_: Throwable) {
            return null
        } finally {
            img?.recycle(); band?.recycle(); out?.recycle()
        }
    }
}
