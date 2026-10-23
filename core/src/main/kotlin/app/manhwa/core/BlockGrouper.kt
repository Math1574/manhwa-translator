package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

data class GrouperConfig(
    val minConfidence: Float = 0.35f,
    /** Écart max entre deux lignes d'un même bloc, en multiples de la hauteur (ou largeur si vertical). */
    val gapFactor: Float = 0.9f,
    val minOverlap: Float = 0.3f,
    val minSidePx: Float = 6f,
)

/**
 * Regroupe les lignes OCR en blocs (bulles). Méthode purement géométrique :
 * elle peut fusionner à tort deux bulles très proches ou séparer une bulle aux lignes très espacées.
 */
class BlockGrouper(private val cfg: GrouperConfig = GrouperConfig()) {

    fun group(lines: List<OcrLine>): List<TextBlock> {
        val kept = dedupe(lines.filter { isUsable(it) })
        if (kept.isEmpty()) return emptyList()

        val parent = IntArray(kept.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        fun join(a: Int, b: Int) { parent[find(a)] = find(b) }

        for (i in kept.indices) for (j in i + 1 until kept.size) {
            if (shouldJoin(kept[i], kept[j])) join(i, j)
        }

        val groups = kept.indices.groupBy { find(it) }.values
        val blocks = groups.map { idxs ->
            val ls = idxs.map { kept[it] }
            val vertical = ls.count { isVertical(it) } * 2 > ls.size
            val sorted = if (vertical) {
                ls.sortedWith(compareByDescending<OcrLine> { it.box.centerX }.thenBy { it.box.top })
            } else {
                ls.sortedWith(compareBy<OcrLine> { it.box.centerY }.thenBy { it.box.left })
            }
            val box = sorted.map { it.box }.reduce { a, b -> a.union(b) }
            Triple(sorted, box, vertical)
        }.sortedWith(compareBy({ it.second.top }, { it.second.left }))

        return blocks.mapIndexed { i, (ls, box, vertical) ->
            TextBlock(i, ls, box, joinText(ls.map { it.text }), vertical)
        }
    }

    /** Motif de rejet d'une ligne OCR (null si elle est utilisable) : sert au regroupement et au mode diagnostic. */
    fun dropReason(l: OcrLine): String? {
        val t = l.text.trim()
        if (t.isEmpty()) return "vide"
        if (l.confidence < cfg.minConfidence) return "confiance"
        if (l.box.isEmpty || min(l.box.width, l.box.height) < cfg.minSidePx) return "trop petit"
        if (!t.any { it.isLetterOrDigit() || it.isCjk() }) return "ponctuation"
        return null
    }

    private fun isUsable(l: OcrLine): Boolean = dropReason(l) == null

    private fun dedupe(lines: List<OcrLine>): List<OcrLine> {
        val out = ArrayList<OcrLine>()
        for (l in lines.sortedByDescending { it.confidence }) {
            val dup = out.any { it.box.overlapRatio(l.box) > 0.8f && similar(it.text, l.text) }
            if (!dup) out.add(l)
        }
        return out
    }

    private fun similar(a: String, b: String): Boolean {
        val x = a.filter { !it.isWhitespace() }
        val y = b.filter { !it.isWhitespace() }
        return x == y || x.contains(y) || y.contains(x)
    }

    private fun isVertical(l: OcrLine) =
        l.vertical || (l.box.height >= 2f * l.box.width && l.text.trim().length >= 2)

    private fun shouldJoin(a: OcrLine, b: OcrLine): Boolean {
        val va = isVertical(a)
        val vb = isVertical(b)
        if (va != vb) return false
        return if (va) {
            val w = (a.box.width + b.box.width) / 2f
            val gap = max(a.box.left, b.box.left) - min(a.box.right, b.box.right)
            val ov = overlap1d(a.box.top, a.box.bottom, b.box.top, b.box.bottom)
            gap <= cfg.gapFactor * w && ov >= cfg.minOverlap
        } else {
            val h = (a.box.height + b.box.height) / 2f
            val gapY = max(a.box.top, b.box.top) - min(a.box.bottom, b.box.bottom)
            val ovX = overlap1d(a.box.left, a.box.right, b.box.left, b.box.right)
            val ovY = overlap1d(a.box.top, a.box.bottom, b.box.top, b.box.bottom)
            val gapX = max(a.box.left, b.box.left) - min(a.box.right, b.box.right)
            val stacked = gapY <= cfg.gapFactor * h && ovX >= cfg.minOverlap
            val sameRow = ovY >= 0.5f && gapX <= h
            stacked || sameRow
        }
    }

    /** Recouvrement 1D rapporté au plus petit segment (peut être négatif si disjoints). */
    private fun overlap1d(a0: Float, a1: Float, b0: Float, b1: Float): Float {
        val inter = min(a1, b1) - max(a0, b0)
        val m = min(a1 - a0, b1 - b0)
        return if (m <= 0f) 0f else inter / m
    }

    private fun joinText(parts: List<String>): String {
        val sb = StringBuilder()
        for (p in parts.map { it.trim() }.filter { it.isNotEmpty() }) {
            if (sb.isNotEmpty() && !(sb.last().isCjk() && p.first().isCjk())) sb.append(' ')
            sb.append(p)
        }
        return sb.toString()
    }
}
