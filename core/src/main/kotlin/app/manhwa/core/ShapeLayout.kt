package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/**
 * Forme d'une zone de texte : étendue horizontale libre [left, right] de chaque rangée de pixels, de haut en bas.
 * Une rangée invalide a left > right. Coordonnées en pixels de l'image d'origine.
 */
class RowShape(val top: Float, val rowH: Float, val left: FloatArray, val right: FloatArray) {
    val rows: Int get() = left.size
    val bottom: Float get() = top + rowH * rows

    /** Intersection horizontale libre sur la bande verticale [y0, y1] ; null si une rangée est invalide ou si la bande sort de la forme. */
    fun span(y0: Float, y1: Float): FloatArray? {
        if (y0 < top - 0.001f || y1 > bottom + 0.001f) return null
        val r0 = ((y0 - top) / rowH).toInt().coerceIn(0, rows - 1)
        val r1 = (((y1 - top) / rowH).toInt()).coerceIn(r0, rows - 1)
        var l = -Float.MAX_VALUE; var r = Float.MAX_VALUE
        for (i in r0..r1) { if (left[i] > right[i]) return null; l = max(l, left[i]); r = min(r, right[i]) }
        return if (l < r) floatArrayOf(l, r) else null
    }

    companion object {
        fun rect(l: Float, t: Float, r: Float, b: Float): RowShape {
            val h = max(1, (b - t).toInt())
            return RowShape(t, (b - t) / h, FloatArray(h) { l }, FloatArray(h) { r })
        }

        /**
         * Forme issue d'un masque d'intérieur de bulle (w*h pixels échantillonnés, échelle [s] vers l'image, origine (x0, y0) en pixels échantillonnés).
         * Pour chaque rangée, on prend le segment continu du masque qui contient (ou est le plus proche de) la colonne du point d'ancrage.
         * Seules les rangées continues autour de l'ancrage sont conservées.
         */
        fun fromMask(mask: BooleanArray, w: Int, h: Int, seedX: Int, seedY: Int, s: Float, x0: Int, y0: Int): RowShape? {
            val sy = seedY.coerceIn(0, h - 1); val sx = seedX.coerceIn(0, w - 1)
            val l = IntArray(h) { -1 }; val r = IntArray(h) { -1 }
            var prevCx = sx
            fun scanRow(y: Int, cx: Int): Boolean {
                var x = cx
                if (!mask[y * w + x]) { // chercher le pixel intérieur le plus proche sur la rangée (±12 % de la largeur)
                    var found = -1
                    for (d in 1..max(4, w / 8)) { if (cx - d >= 0 && mask[y * w + cx - d]) { found = cx - d; break }; if (cx + d < w && mask[y * w + cx + d]) { found = cx + d; break } }
                    if (found < 0) return false
                    x = found
                }
                var a = x; var b = x
                while (a > 0 && mask[y * w + a - 1]) a--
                while (b < w - 1 && mask[y * w + b + 1]) b++
                l[y] = a; r[y] = b; prevCx = (a + b) / 2
                return true
            }
            if (!scanRow(sy, sx)) return null
            var top = sy; var bottom = sy
            prevCx = (l[sy] + r[sy]) / 2
            for (y in sy - 1 downTo 0) { if (!scanRow(y, prevCx)) break; top = y }
            prevCx = (l[sy] + r[sy]) / 2
            for (y in sy + 1 until h) { if (!scanRow(y, prevCx)) break; bottom = y }
            val n = bottom - top + 1
            if (n < 4) return null
            return RowShape((y0 + top) / s, 1f / s, FloatArray(n) { (x0 + l[top + it]) / s }, FloatArray(n) { (x0 + r[top + it] + 1) / s })
        }
    }
}

data class PlacedLine(val text: String, val cx: Float, val top: Float, val width: Float)

class ShapeFit(val size: Float, val lineStep: Float, val lines: List<PlacedLine>, val fits: Boolean)

/** Mise en page d'une traduction dans une forme (bulle ovale, rectangle...) : taille maximale, retours à la ligne équilibrés, centrage ligne par ligne. */
object ShapeLayout {

    /**
     * @param stepRatio interligne (pas entre deux lignes / taille de police)
     * @param margin marge libre à gauche et à droite de chaque ligne, en pixels de l'image
     * @param minSize taille minimale ; en dessous, la meilleure mise en page possible est renvoyée avec fits = false
     */
    fun fit(
        shape: RowShape, seedY: Float, text: String, measurer: TextMeasurer,
        maxSize: Float, minSize: Float, stepRatio: Float, margin: Float,
    ): ShapeFit {
        val words = text.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return ShapeFit(maxSize, maxSize * stepRatio, emptyList(), true)
        var size = maxSize
        var best: ShapeFit? = null
        while (true) {
            val r = tryFit(shape, seedY, words, measurer, size, stepRatio, margin)
            if (r != null) return r
            if (size <= minSize) break
            size = max(minSize, size - max(0.5f, size * 0.03f))
        }
        // aucune mise en page ne tient : meilleure tentative à la taille minimale (peut dépasser légèrement)
        best = fallback(shape, seedY, words, measurer, minSize, stepRatio)
        return best
    }

    private fun tryFit(shape: RowShape, seedY: Float, words: List<String>, m: TextMeasurer, size: Float, ratio: Float, margin: Float): ShapeFit? {
        val step = size * ratio
        val maxLines = min(words.size, ((shape.bottom - shape.top) / step).toInt())
        if (maxLines < 1) return null
        val sp = m.width(" ", size)
        val ww = FloatArray(words.size) { m.width(words[it], size) }
        var bestCost = Float.MAX_VALUE; var best: List<PlacedLine>? = null
        for (n in 1..maxLines) {
            val blockH = n * step
            val yTop = (seedY - blockH / 2f).coerceIn(shape.top, max(shape.top, shape.bottom - blockH))
            val limits = FloatArray(n); val centers = FloatArray(n); var ok = true
            for (k in 0 until n) {
                val sp2 = shape.span(yTop + k * step, yTop + (k + 1) * step - 0.01f)
                if (sp2 == null) { ok = false; break }
                limits[k] = sp2[1] - sp2[0] - 2 * margin; centers[k] = (sp2[0] + sp2[1]) / 2f
                if (limits[k] <= size * 0.5f) { ok = false; break }
            }
            if (!ok) continue
            val assign = balancedBreaks(ww, sp, limits) ?: continue
            var cost = 0f
            val lines = ArrayList<PlacedLine>()
            var i = 0
            for (k in 0 until n) {
                val cnt = assign[k]; val lw = ww.slice(i until i + cnt).sum() + sp * (cnt - 1)
                cost += ((limits[k] - lw) / limits[k]).let { it * it }
                lines.add(PlacedLine(words.subList(i, i + cnt).joinToString(" "), centers[k], yTop + k * step, lw)); i += cnt
            }
            // légère préférence pour moins de lignes à coût égal
            cost += 0.02f * n
            if (cost < bestCost) { bestCost = cost; best = lines }
        }
        return best?.let { ShapeFit(size, step, it, true) }
    }

    /** Répartit les mots sur n lignes (n = limits.size), chaque ligne ≤ sa limite, en minimisant l'écart quadratique au remplissage. Null si impossible. */
    private fun balancedBreaks(ww: FloatArray, sp: Float, limits: FloatArray): IntArray? {
        val m = ww.size; val n = limits.size
        if (n > m) return null
        val inf = Float.MAX_VALUE / 4
        val dp = Array(n + 1) { FloatArray(m + 1) { inf } }; val from = Array(n + 1) { IntArray(m + 1) { -1 } }
        dp[0][0] = 0f
        for (k in 1..n) for (j in k..m) {
            var w = 0f
            for (i in j - 1 downTo k - 1) { // dernière ligne = mots i..j-1
                w += ww[i] + (if (i < j - 1) sp else 0f)
                if (w > limits[k - 1]) break
                if (dp[k - 1][i] >= inf) continue
                val c = dp[k - 1][i] + ((limits[k - 1] - w) / limits[k - 1]).let { it * it }
                if (c < dp[k][j]) { dp[k][j] = c; from[k][j] = i }
            }
        }
        if (dp[n][m] >= inf) return null
        val out = IntArray(n); var j = m
        for (k in n downTo 1) { val i = from[k][j]; out[k - 1] = j - i; j = i }
        return out
    }

    private fun fallback(shape: RowShape, seedY: Float, words: List<String>, m: TextMeasurer, size: Float, ratio: Float): ShapeFit {
        val step = size * ratio
        val n = max(1, min(words.size, ((shape.bottom - shape.top) / step).toInt().coerceAtLeast(1)))
        val yTop = (seedY - n * step / 2f).coerceIn(shape.top, max(shape.top, shape.bottom - n * step))
        val per = (words.size + n - 1) / n
        val lines = words.chunked(per).mapIndexed { k, ws ->
            val sp2 = shape.span(yTop + k * step, min(shape.bottom, yTop + (k + 1) * step)) ?: floatArrayOf(shape.left[0], shape.right[0])
            val t = ws.joinToString(" ")
            PlacedLine(t, (sp2[0] + sp2[1]) / 2f, yTop + k * step, m.width(t, size))
        }
        return ShapeFit(size, step, lines, false)
    }
}

/** Typographie française : espaces insécables avant ? ! : ; » et après «, pour qu'une ponctuation ne se retrouve jamais seule en début de ligne. */
object Typography {
    private const val NBSP = '\u00A0'
    fun french(text: String): String {
        var s = text.replace(Regex("\\s+([?!:;»])"), "$NBSP$1").replace(Regex("([«])\\s+"), "$1$NBSP")
        s = s.replace(Regex("(?<=[A-Za-zÀ-ÿ0-9])([?!:;])(?=\\S)"), "$1") // pas d'espace ajouté devant une ponctuation collée à un mot (déjà gérée par les règles ci-dessus)
        return s
    }
}
