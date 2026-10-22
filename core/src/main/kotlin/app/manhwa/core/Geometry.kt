package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/** Rectangle en pixels de l'image d'origine (origine en haut à gauche). */
data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = max(0f, width) * max(0f, height)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    fun intersection(o: RectF): RectF? {
        val r = RectF(max(left, o.left), max(top, o.top), min(right, o.right), min(bottom, o.bottom))
        return if (r.isEmpty) null else r
    }

    fun intersects(o: RectF): Boolean = intersection(o) != null

    fun union(o: RectF) = RectF(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    fun inflate(dx: Float, dy: Float) = RectF(left - dx, top - dy, right + dx, bottom + dy)

    fun offset(dx: Float, dy: Float) = RectF(left + dx, top + dy, right + dx, bottom + dy)

    /** Aire d'intersection rapportée à la plus petite des deux aires (0 si pas d'intersection). */
    fun overlapRatio(o: RectF): Float {
        val i = intersection(o) ?: return 0f
        val m = min(area, o.area)
        return if (m <= 0f) 0f else i.area / m
    }

    fun isInside(w: Float, h: Float, eps: Float = 0.01f) =
        left >= -eps && top >= -eps && right <= w + eps && bottom <= h + eps
}

/** Ligne de texte reconnue par l'OCR. */
data class OcrLine(
    val text: String,
    val box: RectF,
    val confidence: Float = 1f,
    val vertical: Boolean = false,
)

data class OcrResult(val imageWidth: Int, val imageHeight: Int, val lines: List<OcrLine>)

/** Groupe de lignes formant une bulle ou une zone de texte. */
data class TextBlock(
    val id: Int,
    val lines: List<OcrLine>,
    val box: RectF,
    val text: String,
    val vertical: Boolean,
)

fun Char.isCjk(): Boolean {
    val c = this.code
    return (c in 0x3040..0x30FF) || (c in 0x3400..0x4DBF) || (c in 0x4E00..0x9FFF) ||
        (c in 0xAC00..0xD7AF) || (c in 0x1100..0x11FF) || (c in 0xF900..0xFAFF) ||
        (c in 0xFF00..0xFFEF) || (c in 0x3000..0x303F)
}
