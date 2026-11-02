package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colonnes de texte vertical (japonais) : le détecteur découpe parfois une colonne en une longue boîte et de petits fragments (le point final, un
 * signe) qui, lus à part, sont perdus. Chaque fragment aligné avec une colonne et collé à elle est rattaché à cette colonne.
 */
object VerticalColumns {
    class Result(val columns: List<ScoredBox>, val rest: List<ScoredBox>)

    fun merge(boxes: List<ScoredBox>, tallRatio: Float = 2.2f): Result {
        val cols = boxes.filter { it.height > tallRatio * it.width }.map { arrayOf(it.x0, it.y0, it.x1, it.y1, it.score) }.toMutableList()
        val rest = ArrayList<ScoredBox>()
        for (b in boxes) {
            if (b.height > tallRatio * b.width) continue
            var target = -1; var bestGap = Float.MAX_VALUE
            for ((i, c) in cols.withIndex()) {
                val cw = c[2] - c[0]
                val cx = (b.x0 + b.x1) / 2f
                // fragment aligné sur la colonne (même axe vertical, pas plus large qu'elle) et collé à son extrémité
                if (cx < c[0] - 0.25f * cw || cx > c[2] + 0.25f * cw || b.width > 1.35f * cw) continue
                val gap = if (b.y0 >= c[3]) b.y0 - c[3] else if (b.y1 <= c[1]) c[1] - b.y1 else 0f
                if (gap <= 0.8f * cw && gap < bestGap) { bestGap = gap; target = i }
            }
            if (target >= 0) {
                val c = cols[target]
                c[0] = min(c[0], b.x0); c[1] = min(c[1], b.y0); c[2] = max(c[2], b.x1); c[3] = max(c[3], b.y1)
            } else rest.add(b)
        }
        return Result(cols.map { ScoredBox(it[0], it[1], it[2], it[3], it[4]) }, rest)
    }
}
