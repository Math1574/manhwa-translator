package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Forme libre autour du texte relevée directement sur les pixels : pour chaque rangée, on part de la colonne du texte et on avance de chaque côté jusqu'au premier
 * trait nettement différent du fond (contour de bulle, dessin, texte voisin). Indépendante du détecteur et du remplissage de bulle : elle garantit qu'une
 * traduction ne sera pas posée sur un contour, même si la bulle n'a pas été reconnue ou si son remplissage a fui par un trou du contour.
 * Les pixels du texte à remplacer (boîtes des lignes et zone détectée) et ceux que l'effacement va recouvrir ne sont pas des obstacles.
 */
object BarrierShape {
    private const val DIFF = 70

    fun build(
        src: PixelSource, s: Float, bg: Int, erasedLayer: IntArray?, layerX0: Int, layerY0: Int, layerW: Int, layerH: Int,
        own: List<RectF>, seedX: Float, yTop: Float, yBottom: Float, xMin: Float, xMax: Float, imageW: Float, imageH: Float,
    ): RowShape? {
        val x0 = (max(0f, xMin) * s).toInt().coerceIn(0, src.width - 1); val x1 = (min(imageW, xMax) * s).toInt().coerceIn(x0 + 1, src.width)
        val y0 = (max(0f, yTop) * s).toInt().coerceIn(0, src.height - 1); val y1 = (min(imageH, yBottom) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 8 || w.toLong() * h > 3_000_000L) return null
        val px = src.pixels(x0, y0, w, h)
        val br = (bg shr 16) and 0xFF; val bgc = (bg shr 8) and 0xFF; val bb = bg and 0xFF
        val barrier = BooleanArray(w * h)
        for (i in barrier.indices) {
            val c = px[i]
            if (max(abs(((c shr 16) and 0xFF) - br), max(abs(((c shr 8) and 0xFF) - bgc), abs((c and 0xFF) - bb))) >= DIFF) barrier[i] = true
        }
        // pixels que l'effacement va recouvrir : le texte d'origine, pas un obstacle
        if (erasedLayer != null) for (i in barrier.indices) if (barrier[i]) {
            val gx = x0 + i % w - layerX0; val gy = y0 + i / w - layerY0
            if (gx in 0 until layerW && gy in 0 until layerH && erasedLayer[gy * layerW + gx] != 0) barrier[i] = false
        }
        // texte propre du bloc (boîtes de lignes et zone, élargies de quelques pixels)
        val m = 3
        for (r in own) {
            val a0 = ((r.left * s).toInt() - x0 - m).coerceIn(0, w); val a1 = ((r.right * s).toInt() - x0 + m).coerceIn(0, w)
            val b0 = ((r.top * s).toInt() - y0 - m).coerceIn(0, h); val b1 = ((r.bottom * s).toInt() - y0 + m).coerceIn(0, h)
            for (yy in b0 until b1) for (xx in a0 until a1) barrier[yy * w + xx] = false
        }
        val sx = ((seedX * s).toInt() - x0).coerceIn(0, w - 1)
        val left = FloatArray(h); val right = FloatArray(h)
        for (r in 0 until h) {
            if (barrier[r * w + sx]) { left[r] = 1f; right[r] = 0f; continue }
            var l = sx; while (l > 0 && !barrier[r * w + l - 1]) l--
            var rr = sx; while (rr < w - 1 && !barrier[r * w + rr + 1]) rr++
            // marge de 2 pixels de part et d'autre du trait
            val lImg = (x0 + l + (if (l > 0) 2 else 0)) / s; val rImg = (x0 + rr + 1 - (if (rr < w - 1) 2 else 0)) / s
            if (rImg - lImg < 4f) { left[r] = 1f; right[r] = 0f } else { left[r] = lImg; right[r] = rImg }
        }
        return RowShape(y0 / s, 1f / s, left, right)
    }
}
