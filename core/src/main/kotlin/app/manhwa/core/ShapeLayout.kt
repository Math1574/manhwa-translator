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

    /** Vrai si le point (x, y) en pixels de l'image est dans la forme. */
    fun contains(x: Float, y: Float): Boolean {
        if (y < top || y >= bottom) return false
        val r = ((y - top) / rowH).toInt().coerceIn(0, rows - 1)
        return left[r] <= right[r] && x >= left[r] && x <= right[r]
    }

    /** Intersection horizontale libre sur la bande verticale [y0, y1] ; null si une rangée est invalide ou si la bande sort de la forme. */
    fun span(y0: Float, y1: Float): FloatArray? {
        if (y0 < top - 0.001f || y1 > bottom + 0.001f) return null
        val r0 = ((y0 - top) / rowH).toInt().coerceIn(0, rows - 1)
        val r1 = (((y1 - top) / rowH).toInt()).coerceIn(r0, rows - 1)
        var l = -Float.MAX_VALUE; var r = Float.MAX_VALUE
        for (i in r0..r1) { if (left[i] > right[i]) return null; l = max(l, left[i]); r = min(r, right[i]) }
        return if (l < r) floatArrayOf(l, r) else null
    }

    /** Intersection de deux formes (mêmes coordonnées image) : rangées d'un pixel sur leur étendue verticale commune ; null s'il n'en reste aucune. */
    fun intersect(o: RowShape): RowShape? {
        val t = max(top, o.top); val b = min(bottom, o.bottom)
        val n = (b - t).toInt()
        if (n < 4) return null
        val l = FloatArray(n); val r = FloatArray(n); var valid = 0
        for (i in 0 until n) {
            val y = t + i + 0.5f
            val a = ((y - top) / rowH).toInt().coerceIn(0, rows - 1); val c = ((y - o.top) / o.rowH).toInt().coerceIn(0, o.rows - 1)
            if (left[a] > right[a] || o.left[c] > o.right[c]) { l[i] = 1f; r[i] = 0f; continue }
            val ll = max(left[a], o.left[c]); val rr = min(right[a], o.right[c])
            if (ll < rr) { l[i] = ll; r[i] = rr; valid++ } else { l[i] = 1f; r[i] = 0f }
        }
        return if (valid == 0) null else RowShape(t, 1f, l, r)
    }

    /** Réunion de deux formes (mêmes coordonnées image) : rangées d'un pixel sur l'étendue verticale totale ; une rangée présente dans une seule forme est conservée. */
    fun union(o: RowShape): RowShape {
        val t = min(top, o.top); val b = max(bottom, o.bottom)
        val n = max(1, (b - t).toInt())
        val l = FloatArray(n); val r = FloatArray(n)
        for (i in 0 until n) {
            val y = t + i + 0.5f
            val a = if (y >= top && y < bottom) ((y - top) / rowH).toInt().coerceIn(0, rows - 1) else -1
            val c = if (y >= o.top && y < o.bottom) ((y - o.top) / o.rowH).toInt().coerceIn(0, o.rows - 1) else -1
            val va = a >= 0 && left[a] <= right[a]; val vc = c >= 0 && o.left[c] <= o.right[c]
            when {
                va && vc -> { l[i] = min(left[a], o.left[c]); r[i] = max(right[a], o.right[c]) }
                va -> { l[i] = left[a]; r[i] = right[a] }
                vc -> { l[i] = o.left[c]; r[i] = o.right[c] }
                else -> { l[i] = 1f; r[i] = 0f }
            }
        }
        return RowShape(t, 1f, l, r)
    }

    companion object {
        /**
         * Rectangle dont on retire l'intérieur de bulles voisines (ellipses inscrites dans [avoid], agrandies de [margin]) : un texte hors bulle posé
         * contre une bulle ne doit pas recouvrir son contour. À chaque rangée on garde le plus large segment libre (toujours du même côté de préférence).
         */
        fun rectAvoiding(l: Float, t: Float, r: Float, b: Float, avoid: List<RectF>, margin: Float = 8f): RowShape {
            val h = max(1, (b - t).toInt()); val rowH = (b - t) / h
            fun segsAt(y: Float): List<Pair<Float, Float>> {
                var segs = listOf(Pair(l, r))
                for (a in avoid) {
                    val cy = (a.top + a.bottom) / 2f; val ry = (a.bottom - a.top) / 2f + margin
                    val dy = (y - cy) / ry
                    if (dy <= -1f || dy >= 1f) continue
                    val rx = (a.right - a.left) / 2f + margin; val half = rx * kotlin.math.sqrt(max(0f, 1f - dy * dy)); val cx = (a.left + a.right) / 2f
                    val cutL = cx - half; val cutR = cx + half
                    segs = segs.flatMap { (sl, sr) ->
                        if (cutR <= sl || cutL >= sr) listOf(Pair(sl, sr)) else listOfNotNull(if (cutL - sl > 4f) Pair(sl, cutL) else null, if (sr - cutR > 4f) Pair(cutR, sr) else null)
                    }
                }
                return segs
            }
            // un seul côté de l'obstacle pour toutes les rangées (sinon les lignes sautent de gauche à droite) : celui qui offre le plus de surface
            var leftArea = 0f; var rightArea = 0f; val mid = avoid.map { (it.left + it.right) / 2f }.average().toFloat()
            for (i in 0 until h) for ((sl, sr) in segsAt(t + rowH * (i + 0.5f))) { val w = sr - sl; if ((sl + sr) / 2f < mid) leftArea += w else rightArea += w }
            val preferLeft = leftArea > rightArea
            val left = FloatArray(h); val right = FloatArray(h)
            for (i in 0 until h) {
                val segs = segsAt(t + rowH * (i + 0.5f)).filter { it.second - it.first >= 16f }
                val onSide = segs.filter { if (preferLeft) (it.first + it.second) / 2f < mid else (it.first + it.second) / 2f >= mid }
                val best = (if (onSide.isNotEmpty()) onSide else emptyList()).maxByOrNull { it.second - it.first } ?: segs.takeIf { avoid.isEmpty() }?.maxByOrNull { it.second - it.first }
                if (best == null) { left[i] = 1f; right[i] = 0f } else { left[i] = best.first; right[i] = best.second }
            }
            return RowShape(t, rowH, left, right)
        }

        /** Ellipse inscrite dans le rectangle (repli pour une bulle détectée dont la forme n'a pas pu être relevée sur l'image). */
        fun ellipse(l: Float, t: Float, r: Float, b: Float): RowShape {
            val h = max(1, (b - t).toInt()); val rowH = (b - t) / h
            val cx = (l + r) / 2f; val cy = (t + b) / 2f; val rx = (r - l) / 2f; val ry = (b - t) / 2f
            val left = FloatArray(h); val right = FloatArray(h)
            for (i in 0 until h) {
                val dy = (t + rowH * (i + 0.5f) - cy) / ry
                val half = rx * kotlin.math.sqrt(max(0f, 1f - dy * dy))
                if (half < 2f) { left[i] = 1f; right[i] = 0f } else { left[i] = cx - half; right[i] = cx + half }
            }
            return RowShape(t, rowH, left, right)
        }

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

class ShapeFit(val size: Float, val lineStep: Float, val lines: List<PlacedLine>, val fits: Boolean) {
    /**
     * Même mise en page, agrandie ou réduite du facteur [k] autour du centre du bloc de texte : taille de la police, interligne et largeur des lignes ; les retours à la ligne et le centre
     * des lignes ne changent pas. C'est le réglage de taille choisi à la main : il agit toujours, quelles que soient les bornes de la mise en page automatique.
     */
    fun scaled(k: Float): ShapeFit {
        if (lines.isEmpty() || kotlin.math.abs(k - 1f) < 0.005f) return this
        val cy = (lines.first().top + lines.last().top + lineStep) / 2f
        return ShapeFit(size * k, lineStep * k, lines.map { PlacedLine(it.text, it.cx, cy + (it.top - cy) * k, it.width * k) }, fits)
    }
}

/** Mise en page d'une traduction dans une forme (bulle ovale, rectangle...) : taille maximale, retours à la ligne équilibrés, centrage ligne par ligne. */
object ShapeLayout {
    /** Journal des lignes placées (banc d\'essai uniquement). */
    @JvmField var trace: ((String) -> Unit)? = null

    /** Mots d'un texte et retours à la ligne voulus : [hard] [i] est vrai quand une ligne se termine obligatoirement après le mot i (retour à la ligne tapé dans la traduction). */
    internal class Words(val words: List<String>, val hard: BooleanArray) {
        val breaks: Int get() = hard.count { it }
    }

    /** Mots de [text] (séparés par des espaces ; les retours à la ligne du texte sont des fins de ligne obligatoires). */
    internal fun parseWords(text: String): Words {
        val words = ArrayList<String>(); val hard = ArrayList<Boolean>()
        for (para in text.replace("\r\n", "\n").replace('\r', '\n').trim().split('\n')) {
            val ws = para.split(' ').filter { it.isNotEmpty() }
            for ((i, w) in ws.withIndex()) { words.add(w); hard.add(i == ws.lastIndex) }
        }
        if (hard.isNotEmpty()) hard[hard.lastIndex] = false            // rien ne suit le dernier mot
        return Words(words, hard.toBooleanArray())
    }

    /**
     * @param stepRatio interligne (pas entre deux lignes / taille de police)
     * @param margin marge libre à gauche et à droite de chaque ligne, en pixels de l'image
     * @param minSize taille minimale ; en dessous, la meilleure mise en page possible est renvoyée avec fits = false
     * @param anchorX axe vertical autour duquel chaque ligne est centrée (le centre du texte d'origine) : la ligne ne s'en écarte que si la forme ne lui laisse pas la place
     *                (bord d'une bulle, voisin) ; null : chaque ligne est centrée dans l'étendue libre de sa rangée
     * @param fmt alignement et nombre de lignes choisis à la main (null : mise en page automatique) ; les retours à la ligne tapés dans [text] sont toujours respectés
     */
    fun fit(
        shape: RowShape, seedY: Float, text: String, measurer: TextMeasurer,
        maxSize: Float, minSize: Float, stepRatio: Float, margin: Float, anchorX: Float? = null, fmt: TextFormat? = null,
    ): ShapeFit {
        val pw = parseWords(text)
        if (pw.words.isEmpty()) return ShapeFit(maxSize, maxSize * stepRatio, emptyList(), true)
        var size = maxSize
        while (true) {
            val r = tryFit(shape, seedY, pw, measurer, size, stepRatio, margin, anchorX, fmt)
            if (r != null) return r
            if (size <= minSize) break
            size = max(minSize, size - max(0.5f, size * 0.03f))
        }
        // aucune mise en page ne tient : meilleure tentative à la taille minimale (peut dépasser légèrement)
        return fallback(shape, seedY, pw, measurer, minSize, stepRatio, anchorX, fmt)
    }

    /** Centre d'une ligne de largeur [lw] dans l'étendue [sl, sr] de sa rangée : sur l'axe [anchorX] quand la ligne y tient avec sa marge, sinon au plus près de cet axe. */
    internal fun rowCenter(sl: Float, sr: Float, lw: Float, margin: Float, anchorX: Float?): Float {
        val mid = (sl + sr) / 2f
        if (anchorX == null) return mid
        val lo = sl + margin + lw / 2f; val hi = sr - margin - lw / 2f
        return if (lo > hi) mid else anchorX.coerceIn(lo, hi)
    }

    /**
     * Axe unique de toutes les lignes : l'axe d'ancrage, ramené dans l'intervalle où chaque ligne tient dans l'étendue de sa rangée. Null s'il n'y a pas d'ancrage ou si aucun axe ne convient à
     * toutes les lignes (les lignes se centrent alors chacune pour son compte).
     */
    internal fun commonAxis(spanL: FloatArray, spanR: FloatArray, lws: FloatArray, margin: Float, anchorX: Float?): Float? {
        if (anchorX == null || lws.size < 2) return null
        var lo = -Float.MAX_VALUE; var hi = Float.MAX_VALUE
        for (k in lws.indices) { lo = max(lo, spanL[k] + margin + lws[k] / 2f); hi = min(hi, spanR[k] - margin - lws[k] / 2f) }
        return if (lo > hi) null else anchorX.coerceIn(lo, hi)
    }

    /**
     * Centres des lignes de largeurs [lws] alignées à gauche ou à droite : le bloc (largeur de sa plus longue ligne) est centré sur [axis] sans sortir de la colonne [colL, colR] (marge [margin]
     * comprise), puis chaque ligne est collée à son bord gauche ou droit.
     */
    internal fun alignedCenters(align: Int, lws: FloatArray, colL: Float, colR: Float, margin: Float, axis: Float?): FloatArray {
        var w = 0f; for (v in lws) w = max(w, v)
        val lo = colL + margin; val hi = colR - margin - w
        val a = axis ?: ((colL + colR) / 2f)
        val bl = if (lo > hi) a - w / 2f else (a - w / 2f).coerceIn(lo, hi)
        return FloatArray(lws.size) { k -> if (align == TextFormat.ALIGN_RIGHT) bl + w - lws[k] / 2f else bl + lws[k] / 2f }
    }

    private fun tryFit(shape: RowShape, seedY: Float, pw: Words, m: TextMeasurer, size: Float, ratio: Float, margin: Float, anchorX: Float? = null, fmt: TextFormat? = null): ShapeFit? {
        val words = pw.words
        val step = size * ratio
        val align = fmt?.align ?: TextFormat.ALIGN_CENTER
        val maxLines = min(words.size, ((shape.bottom - shape.top) / step).toInt())
        if (maxLines < 1) return null
        // retours à la ligne voulus : au moins une ligne de plus que de retours ; nombre de lignes imposé : exactement celui-là (jamais moins que les retours voulus)
        val minLines = max(1, pw.breaks + 1)
        val forced = min(fmt?.lines ?: 0, words.size)
        val loN = if (forced > 0) max(forced, minLines) else minLines
        val hiN = if (forced > 0) loN else maxLines
        if (loN > maxLines) return null
        val sp = m.width(" ", size)
        val ww = FloatArray(words.size) { m.width(words[it], size) }
        var bestCost = Float.MAX_VALUE; var best: List<PlacedLine>? = null
        for (n in loN..min(hiN, maxLines)) {
            val blockH = n * step
            val yTop = (seedY - blockH / 2f).coerceIn(shape.top, max(shape.top, shape.bottom - blockH))
            val limits = FloatArray(n); val spanL = FloatArray(n); val spanR = FloatArray(n); var ok = true
            for (k in 0 until n) {
                val sp2 = shape.span(yTop + k * step, yTop + (k + 1) * step - 0.01f)
                if (sp2 == null) { ok = false; break }
                limits[k] = sp2[1] - sp2[0] - 2 * margin; spanL[k] = sp2[0]; spanR[k] = sp2[1]
                if (limits[k] <= size * 0.5f) { ok = false; break }
            }
            if (!ok) continue
            // lignes alignées à gauche ou à droite : elles partagent une même colonne, la partie de la forme libre sur toutes les rangées du bloc
            var colL = 0f; var colR = 0f
            if (align != TextFormat.ALIGN_CENTER) {
                colL = -Float.MAX_VALUE; colR = Float.MAX_VALUE
                for (k in 0 until n) { colL = max(colL, spanL[k]); colR = min(colR, spanR[k]) }
                val lim = colR - colL - 2 * margin
                if (lim <= size * 0.5f) continue
                for (k in 0 until n) limits[k] = lim
            }
            val assign = balancedBreaks(ww, sp, limits, pw.hard) ?: continue
            var cost = 0f
            val lines = ArrayList<PlacedLine>()
            // largeur de chaque ligne, puis un axe commun à toutes les lignes quand la forme le permet : sans lui, chaque ligne collée à un bord différent décalerait les lignes les unes des autres
            val lws = FloatArray(n); run { var q = 0; for (k in 0 until n) { val cnt = assign[k]; lws[k] = ww.slice(q until q + cnt).sum() + sp * (cnt - 1); q += cnt } }
            val axis = if (align == TextFormat.ALIGN_CENTER) commonAxis(spanL, spanR, lws, margin, anchorX) else null
            val aligned = if (align == TextFormat.ALIGN_CENTER) null else alignedCenters(align, lws, colL, colR, margin, anchorX)
            if (trace != null && anchorX != null && n == 2) trace?.invoke("n=$n taille=%.0f ancre=%.0f axe=%s lignes: ".format(size, anchorX, axis?.let { "%.0f".format(it) } ?: "aucun") + (0 until n).joinToString(" ; ") { k -> "[%.0f..%.0f] l=%.0f".format(spanL[k], spanR[k], lws[k]) })
            var i = 0
            for (k in 0 until n) {
                val cnt = assign[k]; val lw = lws[k]
                cost += ((limits[k] - lw) / limits[k]).let { it * it }
                val cx = aligned?.get(k) ?: axis ?: rowCenter(spanL[k], spanR[k], lw, margin, anchorX)
                lines.add(PlacedLine(words.subList(i, i + cnt).joinToString(" "), cx, yTop + k * step, lw)); i += cnt
            }
            // légère préférence pour moins de lignes à coût égal
            cost += 0.02f * n
            if (cost < bestCost) { bestCost = cost; best = lines }
        }
        return best?.let { ShapeFit(size, step, it, true) }
    }

    /**
     * Répartit les mots sur n lignes (n = limits.size), chaque ligne ≤ sa limite, en minimisant l'écart quadratique au remplissage. Null si impossible.
     * [hard] : fin de ligne obligatoire après le mot i (retour à la ligne voulu) ; une ligne ne s'étend jamais au-delà.
     */
    private fun balancedBreaks(ww: FloatArray, sp: Float, limits: FloatArray, hard: BooleanArray? = null): IntArray? {
        val m = ww.size; val n = limits.size
        if (n > m) return null
        val inf = Float.MAX_VALUE / 4
        val dp = Array(n + 1) { FloatArray(m + 1) { inf } }; val from = Array(n + 1) { IntArray(m + 1) { -1 } }
        dp[0][0] = 0f
        for (k in 1..n) for (j in k..m) {
            var w = 0f
            for (i in j - 1 downTo k - 1) { // dernière ligne = mots i..j-1
                if (hard != null && i < j - 1 && hard[i]) break          // un retour à la ligne voulu ne peut pas tomber au milieu d'une ligne
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

    private fun fallback(shape: RowShape, seedY: Float, pw: Words, m: TextMeasurer, size: Float, ratio: Float, anchorX: Float? = null, fmt: TextFormat? = null): ShapeFit {
        val words = pw.words
        val step = size * ratio
        val forced = min(fmt?.lines ?: 0, words.size)
        // n : nombre de lignes prévu (celui d'avant les réglages manuels quand il n'y en a pas : même résultat qu'avant)
        var n = if (forced > 0) forced else max(1, min(words.size, ((shape.bottom - shape.top) / step).toInt().coerceAtLeast(1)))
        val groups: List<List<String>> = if (pw.breaks > 0) {
            // retours à la ligne voulus : chaque paragraphe garde sa ligne, même si le texte dépasse un peu
            val out = ArrayList<List<String>>(); var cur = ArrayList<String>()
            for ((i, w) in words.withIndex()) { cur.add(w); if (pw.hard[i]) { out.add(cur); cur = ArrayList() } }
            if (cur.isNotEmpty()) out.add(cur)
            n = out.size
            out
        } else words.chunked((words.size + n - 1) / n)
        val yTop = (seedY - n * step / 2f).coerceIn(shape.top, max(shape.top, shape.bottom - n * step))
        val texts = groups.map { it.joinToString(" ") }
        val lws = FloatArray(texts.size) { m.width(texts[it], size) }
        val align = fmt?.align ?: TextFormat.ALIGN_CENTER
        val aligned = if (align == TextFormat.ALIGN_CENTER) null else {
            var l = Float.MAX_VALUE; var r = -Float.MAX_VALUE
            for (i in 0 until shape.rows) if (shape.left[i] <= shape.right[i]) { l = min(l, shape.left[i]); r = max(r, shape.right[i]) }
            if (l > r) { l = shape.left[0]; r = shape.right[0] }
            alignedCenters(align, lws, l, r, 0f, anchorX)
        }
        val lines = texts.mapIndexed { k, t ->
            val sp2 = shape.span(yTop + k * step, min(shape.bottom, yTop + (k + 1) * step)) ?: floatArrayOf(shape.left[0], shape.right[0])
            PlacedLine(t, aligned?.get(k) ?: rowCenter(sp2[0], sp2[1], lws[k], 0f, anchorX), yTop + k * step, lws[k])
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
