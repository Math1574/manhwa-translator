package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/** Accès aux pixels d'une image (adaptateur Bitmap sur Android, BufferedImage dans les tests). */
interface PixelSource {
    val width: Int
    val height: Int
    /** Pixels ARGB du rectangle (x, y, w, h), ligne par ligne. */
    fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray
}

/**
 * Plan d'effacement du texte d'origine d'un bloc :
 * - FLAT : texte sur fond uni (bulle, encadré) → les lettres sont remplacées par la couleur du fond, le contour n'est jamais touché ;
 * - ART : texte sur dessin → la zone du texte est reconstruite à partir de ses abords (bords adoucis).
 */
object Erasure {
    enum class Mode { FLAT, ART }

    class Plan(
        val mode: Mode,
        /** Région analysée, en pixels échantillonnés de l'image. */
        val x0: Int, val y0: Int, val w: Int, val h: Int,
        /** Pixels à remplacer (w*h). */
        val mask: BooleanArray,
        /** Couleur de remplacement (ARGB) de chaque pixel de la région, alpha compris (bords adoucis en mode ART). */
        val layer: IntArray,
        val bg: Int,
        val textColor: Int,
        val outlined: Boolean,
        /** Rectangle inscrit dans la bulle (pixels de l'image d'origine), si la forme a pu être détectée. */
        val bubbleArea: RectF?,
        /** Forme libre de l'intérieur de la bulle (pixels de l'image d'origine), pour la mise en page de la traduction. */
        val shape: RowShape? = null,
        /** Épaisseur du trait des lettres d'origine, en pixels échantillonnés (0 si inconnue). */
        val strokePx: Float = 0f,
    )

    fun plan(src: PixelSource, s: Float, lineBoxes: List<RectF>, lineH: Float, imageW: Float, imageH: Float): Plan? {
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val pad = max(10f, lineH * 0.7f)
        val x0 = (max(0f, union.left - pad) * s).toInt().coerceIn(0, src.width - 1)
        val y0 = (max(0f, union.top - pad) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + pad) * s).toInt().coerceIn(x0 + 1, src.width)
        val y1 = (min(imageH, union.bottom + pad) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 8 || h < 8) return null
        val px = src.pixels(x0, y0, w, h)
        fun rects() = lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
        val lh = lineH * s

        var a = TextErase.analyze(px, w, h, TextErase.rectMask(w, h, rects(), max(3, (lh * 0.2f).toInt())), grow = max(2, (lh * 0.06f).toInt()))
        if (a.outlined) a = TextErase.analyze(px, w, h, TextErase.rectMask(w, h, rects(), max(6, (lh * 0.3f).toInt())), grow = max(3, (lh * 0.1f).toInt()))

        // bulle : zone de texte sur fond uni ET (abords unis OU contour fermé : le remplissage depuis le texte reste borné)
        val bubble = if (!a.outlined && a.zoneFlat >= 0.9f && a.bgShare >= 0.45f) bubbleArea(src, s, union, a.bg, imageW, imageH) else null
        // un lettrage cerné d'un contour ou d'une lueur est traité comme du dessin : l'aplat laisserait des fragments du contour
        val flat = !a.outlined && a.zoneFlat >= 0.9f && a.bgShare >= 0.45f && (a.uniformity >= 0.85f || bubble != null)
        if (flat) {
            val allowedZone = TextErase.rectMask(w, h, rects(), max(3, (lh * 0.2f).toInt()))
            // cœur des lignes : boîtes de l'OCR rognées en haut et en bas pour ne pas entamer le contour de la bulle
            val shrink = max(1, (lh * 0.2f).toInt())
            val core = TextErase.rectMask(w, h, rects().map { r -> intArrayOf(r[0], r[1] + shrink, r[2], r[3] - shrink) }.filter { r -> r[3] > r[1] }, 0)
            val textRect = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
            val interior = BubbleMask.interior(px, w, h, textRect, a.bg)
            val raw = TextErase.textComponents(px, w, h, a.bg, a.threshold, allowedZone, 0, interior, core)
            val stroke = TextErase.strokeWidth(raw, w, h)
            val mask = TextErase.dilate(raw, w, h, max(2, (lh * 0.06f).toInt()))
            val layer = IntArray(w * h) { if (mask[it]) (0xFF shl 24) or (a.bg and 0xFFFFFF) else 0 }
            val shape = bubbleShape(src, s, union, a.bg, imageW, imageH)
            return Plan(Mode.FLAT, x0, y0, w, h, mask, layer, a.bg, a.textColor, a.outlined, bubble, shape, stroke)
        }
        // texte sur dessin : seules les lettres (remplissage + contour) sont effacées et reconstruites, pas un rectangle entier
        val zone = TextErase.rectMask(w, h, rects(), max(4, (lh * (if (a.outlined) 0.35f else 0.25f)).toInt()))
        val fillColor = TextErase.extremeTextColor(px, zone)
        val mask = TextErase.artMask(px, w, h, zone, fillColor, max(2, (lh * (if (a.outlined) 0.12f else 0.07f)).toInt()))
        val fill = TextErase.inpaint(px, w, h, mask)
        val alpha = TextErase.featherAlpha(mask, w, h)
        val layer = IntArray(w * h) { if (mask[it]) (alpha[it] shl 24) or (fill[it] and 0xFFFFFF) else 0 }
        return Plan(Mode.ART, x0, y0, w, h, mask, layer, a.bg, fillColor, a.outlined, null, null, TextErase.strokeWidth(mask, w, h))
    }

    /**
     * Forme libre de l'intérieur de la bulle autour du texte (remplissage borné par le contour, lettres comprises), en pixels de l'image.
     * Région d'analyse plus large que pour le rectangle inscrit : la bulle entière doit y tenir.
     */
    private fun bubbleShape(src: PixelSource, s: Float, union: RectF, bg: Int, imageW: Float, imageH: Float): RowShape? {
        val gx = union.width * 1.1f + 20f; val gy = union.height * 1.1f + 20f
        val x0 = (max(0f, union.left - gx) * s).toInt().coerceIn(0, src.width - 1); val y0 = (max(0f, union.top - gy) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + gx) * s).toInt().coerceIn(x0 + 1, src.width); val y1 = (min(imageH, union.bottom + gy) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 16 || w.toLong() * h > 2_000_000L) return null
        val px = src.pixels(x0, y0, w, h)
        val text = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        val mask = BubbleMask.interior(px, w, h, text, bg, tol = 42, maxFill = 0.97f) ?: return null
        val seedX = (text[0] + text[2]) / 2; val seedY = (text[1] + text[3]) / 2
        return RowShape.fromMask(mask, w, h, seedX, seedY, s, x0, y0)
    }

    /** Rectangle inscrit dans la bulle (forme obtenue par remplissage de la zone de même couleur), en pixels de l'image. */
    private fun bubbleArea(src: PixelSource, s: Float, union: RectF, bg: Int, imageW: Float, imageH: Float): RectF? {
        val gx = union.width * 0.7f; val gy = union.height * 0.7f
        val x0 = (max(0f, union.left - gx) * s).toInt().coerceIn(0, src.width - 1); val y0 = (max(0f, union.top - gy) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + gx) * s).toInt().coerceIn(x0 + 1, src.width); val y1 = (min(imageH, union.bottom + gy) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 16 || w.toLong() * h > 1_200_000L) return null
        val px = src.pixels(x0, y0, w, h)
        val text = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        val mask = BubbleMask.compute(px, w, h, text, bg) ?: return null
        val bnd = BubbleMask.bounds(mask, w, h) ?: return null
        val bw = (bnd[2] - bnd[0]).toFloat(); val bh = (bnd[3] - bnd[1]).toFloat()
        val l = (x0 + bnd[0] + bw * 0.146f) / s; val t = (y0 + bnd[1] + bh * 0.146f) / s
        return RectF(l, t, l + bw * 0.708f / s, t + bh * 0.708f / s)
    }
}
