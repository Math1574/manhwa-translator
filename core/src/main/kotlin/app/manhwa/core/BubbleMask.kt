package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max

/** Détection de la forme d'une bulle par remplissage de la zone de couleur uniforme qui entoure le texte (limitée par le contour). */
object BubbleMask {

    /**
     * @param pixels région en ARGB (largeur [w], hauteur [h])
     * @param text rectangle du texte dans la région : x0, y0, x1, y1 (x1 et y1 exclus)
     * @param bg couleur de fond de la bulle (médiane de l'anneau autour du texte)
     * @return masque (w*h) de l'intérieur de la bulle, texte compris ; null si la forme n'est pas fiable
     *         (contour ouvert : le remplissage envahit la région, ou aucune zone uniforme autour du texte)
     */
    fun compute(pixels: IntArray, w: Int, h: Int, text: IntArray, bg: Int, tol: Int = 42, maxFill: Float = 0.8f): BooleanArray? {
        if (w < 8 || h < 8) return null
        val x0 = text[0].coerceIn(0, w - 1); val y0 = text[1].coerceIn(0, h - 1)
        val x1 = text[2].coerceIn(x0 + 1, w); val y1 = text[3].coerceIn(y0 + 1, h)
        fun near(c: Int): Boolean =
            maxOf(abs(((c shr 16) and 0xFF) - ((bg shr 16) and 0xFF)), abs(((c shr 8) and 0xFF) - ((bg shr 8) and 0xFF)), abs((c and 0xFF) - (bg and 0xFF))) <= tol

        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun push(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= w || y >= h) return
            val i = y * w + x
            if (!seen[i] && near(pixels[i])) { seen[i] = true; queue[tail++] = i }
        }
        // graines : anneau de 2 px juste autour du texte
        val r = 2
        for (x in x0 - r until x1 + r) { for (d in 1..r) { push(x, y0 - d); push(x, y1 - 1 + d) } }
        for (y in y0 - r until y1 + r) { for (d in 1..r) { push(x0 - d, y); push(x1 - 1 + d, y) } }
        if (tail == 0) return null
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            push(x + 1, y); push(x - 1, y); push(x, y + 1); push(x, y - 1)
        }
        val textArea = (x1 - x0) * (y1 - y0)
        val filled = tail
        if (filled > maxFill * (w * h - textArea)) return null // fuite : le contour de la bulle n'est pas fermé
        val mask = BooleanArray(w * h)
        for (k in 0 until tail) mask[queue[k]] = true
        for (y in y0 until y1) for (x in x0 until x1) mask[y * w + x] = true
        // dilatation de 1 px : couvre le halo d'antialiasing du texte et le bord intérieur du contour
        val out = mask.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (mask[y * w + x]) continue
            if ((x > 0 && mask[y * w + x - 1]) || (x < w - 1 && mask[y * w + x + 1]) || (y > 0 && mask[(y - 1) * w + x]) || (y < h - 1 && mask[(y + 1) * w + x])) out[y * w + x] = true
        }
        return out
    }

    /** Boîte englobante du masque : x0, y0, x1, y1 (x1, y1 exclus), ou null si le masque est vide. */
    fun bounds(mask: BooleanArray, w: Int, h: Int): IntArray? {
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x]) { minX = minOf(minX, x); maxX = max(maxX, x); minY = minOf(minY, y); maxY = max(maxY, y) }
        return if (maxX < 0) null else intArrayOf(minX, minY, maxX + 1, maxY + 1)
    }
}
