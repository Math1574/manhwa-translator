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

    /**
     * Intérieur de la bulle autour du texte : zone de couleur uniforme atteinte depuis le texte, plus les « trous » enfermés
     * (les lettres). Le contour et le dessin extérieur ne font pas partie du résultat. Null si le contour n'est pas fermé.
     */
    fun interior(pixels: IntArray, w: Int, h: Int, text: IntArray, bg: Int, tol: Int = 42, maxFill: Float = 0.8f): BooleanArray? {
        if (w < 8 || h < 8) return null
        val x0 = text[0].coerceIn(0, w - 1); val y0 = text[1].coerceIn(0, h - 1)
        val x1 = text[2].coerceIn(x0 + 1, w); val y1 = text[3].coerceIn(y0 + 1, h)
        fun near(c: Int): Boolean =
            maxOf(abs(((c shr 16) and 0xFF) - ((bg shr 16) and 0xFF)), abs(((c shr 8) and 0xFF) - ((bg shr 8) and 0xFF)), abs((c and 0xFF) - (bg and 0xFF))) <= tol
        val flood = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun push(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= w || y >= h) return
            val i = y * w + x
            if (!flood[i] && near(pixels[i])) { flood[i] = true; queue[tail++] = i }
        }
        val r = 2
        for (x in x0 - r until x1 + r) { for (d in 1..r) { push(x, y0 - d); push(x, y1 - 1 + d) } }
        for (y in y0 - r until y1 + r) { for (d in 1..r) { push(x0 - d, y); push(x1 - 1 + d, y) } }
        // les pixels de la zone de texte proches du fond servent aussi de graines (centre de la bulle)
        for (y in y0 until y1) for (x in x0 until x1) push(x, y)
        if (tail == 0) return null
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            push(x + 1, y); push(x - 1, y); push(x, y + 1); push(x, y - 1)
        }
        if (tail > maxFill * (w * h)) return null
        // fuite : le remplissage touche le bord de la région sur une grande partie de son pourtour (contour ouvert ou bulle plus grande que la région)
        var edge = 0
        for (x in 0 until w) { if (flood[x]) edge++; if (flood[(h - 1) * w + x]) edge++ }
        for (y in 0 until h) { if (flood[y * w]) edge++; if (flood[y * w + w - 1]) edge++ }
        if (edge > 0.55f * (2 * (w + h))) return null
        // trous : pixels non remplis qui ne communiquent pas avec le bord de la région
        val outside = BooleanArray(w * h)
        var oh = 0; var ot = 0
        fun pushO(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= w || y >= h) return
            val i = y * w + x
            if (!flood[i] && !outside[i]) { outside[i] = true; queue[ot++] = i }
        }
        ot = 0; oh = 0
        for (x in 0 until w) { pushO(x, 0); pushO(x, h - 1) }
        for (y in 0 until h) { pushO(0, y); pushO(w - 1, y) }
        while (oh < ot) {
            val i = queue[oh++]; val x = i % w; val y = i / w
            pushO(x + 1, y); pushO(x - 1, y); pushO(x, y + 1); pushO(x, y - 1)
        }
        val out = BooleanArray(w * h)
        for (i in out.indices) out[i] = flood[i] || !outside[i]
        return out
    }

    /** Boîte englobante du masque : x0, y0, x1, y1 (x1, y1 exclus), ou null si le masque est vide. */
    fun bounds(mask: BooleanArray, w: Int, h: Int): IntArray? {
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x]) { minX = minOf(minX, x); maxX = max(maxX, x); minY = minOf(minY, y); maxY = max(maxY, y) }
        return if (maxX < 0) null else intArrayOf(minX, minY, maxX + 1, maxY + 1)
    }

    /**
     * Intérieur d'une bulle à fond non uni (dégradé, ombre, lueur sombre) : remplissage par proximité locale. Un pixel rejoint la zone s'il
     * ressemble au pixel voisin déjà retenu (écart ≤ [step]) : les dégradés lents sont suivis, les contours et les lettres (sauts francs) arrêtent
     * le remplissage ; les lettres entourées deviennent des « trous » ajoutés à l'intérieur. Null si le remplissage s'échappe de la région.
     */
    fun interiorSmooth(pixels: IntArray, w: Int, h: Int, text: IntArray, step: Int = 14, maxFill: Float = 0.85f): BooleanArray? {
        if (w < 8 || h < 8) return null
        val x0 = text[0].coerceIn(0, w - 1); val y0 = text[1].coerceIn(0, h - 1); val x1 = text[2].coerceIn(x0 + 1, w); val y1 = text[3].coerceIn(y0 + 1, h)
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
        fun close(a: Int, b: Int) = maxOf(abs(ch(a, 16) - ch(b, 16)), abs(ch(a, 8) - ch(b, 8)), abs(ch(a, 0) - ch(b, 0))) <= step
        val flood = BooleanArray(w * h); val queue = IntArray(w * h); var head = 0; var tail = 0
        // graines : anneau de 3 px autour du texte
        fun seed(x: Int, y: Int) { if (x in 0 until w && y in 0 until h && !flood[y * w + x]) { flood[y * w + x] = true; queue[tail++] = y * w + x } }
        for (x in x0 - 3 until x1 + 3) { for (d in 1..3) { seed(x, y0 - d); seed(x, y1 - 1 + d) } }
        for (y in y0 - 3 until y1 + 3) { for (d in 1..3) { seed(x0 - d, y); seed(x1 - 1 + d, y) } }
        if (tail == 0) return null
        // les graines doivent être cohérentes entre elles (même teinte générale) : on retire celles qui sont des lettres
        val med = ColorMath.median(IntArray(tail) { pixels[queue[it]] })
        var kept = 0
        for (k in 0 until tail) { val i = queue[k]; if (maxOf(abs(ch(pixels[i], 16) - ch(med, 16)), abs(ch(pixels[i], 8) - ch(med, 8)), abs(ch(pixels[i], 0) - ch(med, 0))) <= 60) queue[kept++] = i else flood[i] = false }
        tail = kept; if (tail == 0) return null
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            for (d in 0..3) {
                val nx = x + (if (d == 0) 1 else if (d == 1) -1 else 0); val ny = y + (if (d == 2) 1 else if (d == 3) -1 else 0)
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (!flood[j] && close(pixels[i], pixels[j]) && maxOf(abs(ch(pixels[j], 16) - ch(med, 16)), abs(ch(pixels[j], 8) - ch(med, 8)), abs(ch(pixels[j], 0) - ch(med, 0))) <= 140) { flood[j] = true; queue[tail++] = j }
            }
        }
        if (tail > maxFill * w * h) return null
        var edge = 0
        for (x in 0 until w) { if (flood[x]) edge++; if (flood[(h - 1) * w + x]) edge++ }
        for (y in 0 until h) { if (flood[y * w]) edge++; if (flood[y * w + w - 1]) edge++ }
        if (edge > 0.55f * (2 * (w + h))) return null
        // trous : zones non retenues qui ne touchent pas le bord (lettres et leurs halos)
        val notFlood = BooleanArray(w * h) { !flood[it] }
        val holes = TextErase.enclosedOnly(notFlood, w, h)
        return BooleanArray(w * h) { flood[it] || holes[it] }
    }
}
