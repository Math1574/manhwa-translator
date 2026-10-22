package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

fun interface TextMeasurer {
    fun width(text: String, sizePx: Float): Float
}

/** Estimation sans Android ; sur le téléphone on fournit un TextMeasurer basé sur Paint. */
object EstimatedMeasurer : TextMeasurer {
    override fun width(text: String, sizePx: Float): Float =
        text.sumOf { c ->
            when {
                c.isCjk() -> 1.0
                c == ' ' -> 0.3
                else -> 0.55
            }
        }.toFloat() * sizePx
}

data class TranslatedBlock(val id: Int, val box: RectF, val source: String, val translated: String, val vertical: Boolean = false)

data class PlacedBlock(
    val id: Int,
    val text: String,
    val box: RectF,
    val fontSizePx: Float,
    val lines: List<String>,
)

data class LayoutConfig(
    val paddingRatio: Float = 0.06f,
    val minFontPx: Float = 9f,
    val lineSpacing: Float = 1.15f,
    val maxGrowRatio: Float = 1.6f,
)

object OverlayLayout {

    fun wrap(text: String, maxWidth: Float, size: Float, m: TextMeasurer): List<String> {
        val out = ArrayList<String>()
        var cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out.add(cur.toString().trimEnd()); cur = StringBuilder() } }
        for (word in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (m.width(candidate, size) <= maxWidth) { cur = StringBuilder(candidate); continue }
            flush()
            if (m.width(word, size) <= maxWidth) { cur = StringBuilder(word); continue }
            for (ch in word) { // mot plus large que la zone : coupure par caractère
                if (cur.isNotEmpty() && m.width(cur.toString() + ch, size) > maxWidth) flush()
                cur.append(ch)
            }
        }
        flush()
        return out
    }

    /**
     * Place les traductions dans l'image : reste dans les limites, ajuste la taille du texte,
     * agrandit un peu la zone si nécessaire et écarte les blocs qui se chevauchent.
     */
    fun place(
        blocks: List<TranslatedBlock>,
        imageW: Int,
        imageH: Int,
        userScale: Float = 1f,
        measurer: TextMeasurer = EstimatedMeasurer,
        cfg: LayoutConfig = LayoutConfig(),
    ): List<PlacedBlock> {
        val w = imageW.toFloat()
        val h = imageH.toFloat()
        val scale = userScale.coerceIn(0.5f, 2.0f)

        var placed = blocks.filter { it.translated.isNotBlank() }.map { fit(it, w, h, scale, measurer, cfg) }
        placed = resolveOverlaps(placed, w, h)
        return placed
    }

    private fun fit(b: TranslatedBlock, w: Float, h: Float, scale: Float, m: TextMeasurer, cfg: LayoutConfig): PlacedBlock {
        val padX = b.box.width * cfg.paddingRatio
        val padY = b.box.height * cfg.paddingRatio
        var box = clamp(b.box.inflate(padX, padY), w, h)
        val avgLine = if (b.vertical) b.box.width else b.box.height
        val maxFont = max(cfg.minFontPx, min(box.height, avgLine) * 0.9f)
        val minFont = cfg.minFontPx
        val target = (maxFont * scale).coerceAtLeast(minFont)

        var size = target
        var lines = wrap(b.translated, box.width, size, m)
        fun neededH(s: Float, n: Int) = n * s * cfg.lineSpacing
        val cap = min(box.height * cfg.maxGrowRatio, h)
        while (size > minFont && neededH(size, lines.size) > cap) {
            size = max(minFont, size - 1f)
            lines = wrap(b.translated, box.width, size, m)
        }
        val need = neededH(size, lines.size)
        if (need > box.height) {
            val grown = min(need, box.height * cfg.maxGrowRatio)
            val extra = grown - box.height
            box = clamp(RectF(box.left, box.top - extra / 2f, box.right, box.bottom + extra / 2f), w, h)
            // si l'image est trop petite pour tout contenir, on réduit encore la police
            while (size > 4f && neededH(size, lines.size) > box.height) {
                size -= 0.5f
                lines = wrap(b.translated, box.width, size, m)
            }
        }
        return PlacedBlock(b.id, b.translated, box, size, lines)
    }

    private fun resolveOverlaps(input: List<PlacedBlock>, w: Float, h: Float): List<PlacedBlock> {
        val list = input.sortedBy { it.box.top }.toMutableList()
        repeat(4) {
            var changed = false
            for (i in list.indices) for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                val inter = a.box.intersection(b.box) ?: continue
                if (inter.height < 1f) continue
                val dy = inter.height
                var nb = b.box.offset(0f, dy)
                if (nb.bottom <= h) {
                    list[j] = b.copy(box = nb)
                } else {
                    // plus de place en bas : on remonte le bloc du dessus
                    val na = a.box.offset(0f, -dy)
                    if (na.top >= 0f) list[i] = a.copy(box = na)
                    else {
                        // dernier recours : réduire la hauteur du bloc du bas
                        nb = RectF(b.box.left, min(max(b.box.top, a.box.bottom), h - 1f), b.box.right, b.box.bottom)
                        list[j] = b.copy(box = nb, fontSizePx = max(4f, b.fontSizePx * 0.8f))
                    }
                }
                changed = true
            }
            if (!changed) return list.sortedBy { it.id }
        }
        return list.sortedBy { it.id }
    }

    private fun clamp(r: RectF, w: Float, h: Float): RectF {
        val bw = min(r.width, w)
        val bh = min(r.height, h)
        val l = r.left.coerceIn(0f, w - bw)
        val t = r.top.coerceIn(0f, h - bh)
        return RectF(l, t, l + bw, t + bh)
    }
}
