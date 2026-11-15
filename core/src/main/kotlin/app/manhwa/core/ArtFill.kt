package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/**
 * Texte posé sur un dessin (mode ART) : reconstruction du fond manquant.
 *
 * Ancienne méthode : chaque pixel effacé prenait la couleur du pixel conservé le plus proche (mosaïque de losanges et de triangles sur un fond en dégradé), le masque ne couvrait que les lettres
 * et un mince contour (la lueur qui les entoure restait, en « fantôme »), et les bords adoucis mordaient sur le masque lui-même (frange grise résiduelle).
 *
 * Méthode actuelle :
 * 1. le fond propre sous le texte est estimé par une interpolation lisse multi-échelle, à partir des seuls pixels éloignés des lettres ;
 * 2. l'écart entre l'image et ce fond propre, mesuré anneau par anneau autour des lettres, donne l'épaisseur de la lueur et de la frange : le masque grandit de cette épaisseur seulement
 *    (un dessin détaillé autour du texte n'est jamais effacé au-delà) ;
 * 3. le remplissage final est une interpolation lisse qui part des pixels intacts voisins du masque : raccord continu, sans losange ni bord franc ;
 * 4. les bords adoucis sont à l'extérieur du masque (les pixels déjà propres), jamais sur les lettres : plus de contour fantôme.
 */
object ArtFill {
    /** Bancs d'essai : false rétablit l'ancienne reconstruction (plus proche voisin + tampon de duplication), pour comparer. */
    @JvmField var enabled: Boolean = true
    /** Diagnostic (bancs d'essai) : reçoit les mesures de chaque reconstruction. Nul en production. */
    @JvmField var trace: ((String) -> Unit)? = null

    /**
     * @param mask pixels dont l'alpha est non nul (masque sûr + couronne adoucie)
     * @param safe pixels remplacés à 100 % (lettres, contour, lueur et marge de sécurité)
     * @param fill reconstruction lisse (RVB opaque) de [mask] ; le reste de l'image est inchangé
     * @param alpha opacité du remplissage (255 sur [safe], dégradé sur la couronne extérieure, 0 ailleurs)
     * @param grown pixels de lueur ajoutés au masque de départ
     * @param sigma bruit (écart type) du fond autour du texte, pour le grain du remplissage
     * @param thick épaisseur de lueur mesurée (pixels) ; égale à la limite de recherche quand la lueur ne retombe pas dans cette limite (halo diffus)
     */
    class Result(val mask: BooleanArray, val safe: BooleanArray, val fill: IntArray, val alpha: IntArray, val grown: Int, val sigma: Float, val thick: Int = 0)

    /** Opacité de la couronne extérieure au pixel 1, 2, 3 du masque sûr : le fondu se fait sur du fond déjà propre. */
    private val RING_ALPHA = intArrayOf(255, 205, 135, 65)

    private fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF

    /**
     * Bruit du fond : écart type estimé à partir du 40e centile de l'écart absolu entre l'image et sa version floutée 5×5, sur les pixels [where]. Un quantile bas ignore les bords du dessin et
     * les textes voisins (qui gonflaient la médiane et donnaient un grain de « neige » beaucoup trop fort) ; 0 si trop peu de pixels.
     */
    fun grainSigma(px: IntArray, w: Int, h: Int, where: BooleanArray): Float {
        val gray = IntArray(w * h) { val c = px[it]; (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8 }
        val integ = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) { var row = 0L; for (x in 0 until w) { row += gray[y * w + x]; integ[(y + 1) * (w + 1) + x + 1] = integ[y * (w + 1) + x + 1] + row } }
        val devs = ArrayList<Float>()
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            val i = y * w + x
            if (!where[i]) continue
            val xa = x - 2; val xb = x + 3; val ya = y - 2; val yb = y + 3
            val sum = integ[yb * (w + 1) + xb] - integ[ya * (w + 1) + xb] - integ[yb * (w + 1) + xa] + integ[ya * (w + 1) + xa]
            devs.add(kotlin.math.abs(gray[i] - sum.toFloat() / 25f))
        }
        if (devs.size < 300) return 0f
        devs.sort()
        return devs[(devs.size * 4) / 10] / 0.524f
    }

    /** Grain : bruit gaussien (gris) d'écart type [sigma] ajouté aux pixels [where] ; sans effet sur un fond lisse (sigma < 2). */
    fun addGrain(fill: IntArray, where: BooleanArray, sigma: Float, seed: Long = 7L): IntArray {
        if (sigma < 2f) return fill
        val rnd = java.util.Random(seed); val out = fill.copyOf(); val amp = 0.9 * min(sigma, 12f)
        for (i in out.indices) if (where[i]) {
            val d = (rnd.nextGaussian() * amp).toInt(); val c = fill[i]
            out[i] = (0xFF shl 24) or ((ch(c, 16) + d).coerceIn(0, 255) shl 16) or ((ch(c, 8) + d).coerceIn(0, 255) shl 8) or (ch(c, 0) + d).coerceIn(0, 255)
        }
        return out
    }

    /**
     * Copie de [px] sans les traits fins : par canal, une ouverture (retire ce qui est plus clair que le fond et plus étroit que 2·[radius]+1 px) ou une fermeture (retire ce qui est plus
     * sombre), selon celle qui s'écarte le plus du pixel. Un dégradé, un grand aplat et un bord franc entre deux grandes zones restent tels quels ; une ligne lumineuse fine (trait de
     * lumière, étoile, fil) disparaît. Sert de référence au remplissage : sans cela, les lignes qui touchent le trou en éclaircissaient tout l'intérieur (moyenne des bords).
     */
    internal fun withoutThinLines(px: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val n = w * h
        val out = IntArray(n)
        val chans = IntArray(n)
        val low = IntArray(n); val high = IntArray(n)
        val res = Array(3) { IntArray(n) }
        for (k in 0..2) {
            val sh = 16 - 8 * k
            for (i in 0 until n) chans[i] = (px[i] shr sh) and 0xFF
            // ouverture = dilatation de l'érosion ; fermeture = érosion de la dilatation
            val er = slide(slide(chans, w, h, radius, false, true), w, h, radius, false, false)
            val open = slide(slide(er, w, h, radius, true, true), w, h, radius, true, false)
            val di = slide(slide(chans, w, h, radius, true, true), w, h, radius, true, false)
            val close = slide(slide(di, w, h, radius, false, true), w, h, radius, false, false)
            for (i in 0 until n) { val v = chans[i]; low[i] = open[i]; high[i] = close[i]; res[k][i] = if (v - low[i] >= high[i] - v) low[i] else high[i] }
        }
        for (i in 0 until n) out[i] = (0xFF shl 24) or (res[0][i] shl 16) or (res[1][i] shl 8) or res[2][i]
        return out
    }

    /** Minimum ([max] faux) ou maximum glissant de demi-largeur [r], le long des lignes ([rows]) ou des colonnes ; file monotone, temps linéaire. */
    private fun slide(src: IntArray, w: Int, h: Int, r: Int, max: Boolean, rows: Boolean): IntArray {
        val out = IntArray(src.size)
        val len = if (rows) w else h; val lines = if (rows) h else w
        val dq = IntArray(len + 1)
        for (l in 0 until lines) {
            var head = 0; var tail = 0; var next = 0
            for (k in 0 until len) {
                val hi = min(len - 1, k + r)
                while (next <= hi) {
                    val v = src[if (rows) l * w + next else next * w + l]
                    while (tail > head) {
                        val last = src[if (rows) l * w + dq[tail - 1] else dq[tail - 1] * w + l]
                        if (if (max) last <= v else last >= v) tail-- else break
                    }
                    dq[tail++] = next; next++
                }
                while (dq[head] < k - r) head++
                out[if (rows) l * w + k else k * w + l] = src[if (rows) l * w + dq[head] else dq[head] * w + l]
            }
        }
        return out
    }

    /**
     * @param px pixels de la région (w×h)
     * @param core masque serré des lettres (remplissage, contour et frange adoucie)
     * @param limit pixels que l'on a le droit d'effacer (zone du texte élargie, sans les textes voisins)
     * @param avoid pixels qui ne servent jamais de référence (textes voisins) ; null si aucun
     * @param maxGrow épaisseur maximale (pixels) de lueur cherchée autour des lettres
     * @param ring largeur (pixels) du fondu extérieur : 3 en général ; plus large pour un halo diffus, que l'on ne doit pas voir finir en carré
     * @param robust 0 : les pixels voisins servent tels quels de référence ; sinon, demi-largeur (pixels) des traits fins que l'on en retire d'abord (voir [withoutThinLines])
     * @return null si la région ne contient pas assez de fond propre pour une reconstruction fiable (l'appelant garde l'ancienne méthode)
     */
    fun reconstruct(px: IntArray, w: Int, h: Int, core: BooleanArray, limit: BooleanArray, avoid: BooleanArray?, maxGrow: Int, ring: Int = 3, robust: Int = 0): Result? {
        val ringAlpha = if (ring <= 3) RING_ALPHA else IntArray(ring + 1) { d -> if (d == 0) 255 else (255 * (Math.cos(Math.PI * d / (ring + 1)) + 1) / 2).toInt() }
        val bgRef = if (robust > 0) withoutThinLines(px, w, h, robust) else px
        val n = w * h
        var coreN = 0; for (v in core) if (v) coreN++
        if (coreN == 0) return null
        val grow = max(1, maxGrow)
        // 1) fond propre estimé sous toute la bande de recherche de la lueur
        val band = TextErase.dilate(core, w, h, grow, limit)
        val validBand = BooleanArray(n) { !band[it] && (avoid == null || !avoid[it]) }
        var known = 0; for (v in validBand) if (v) known++
        if (known < 300 || known < coreN / 8) return null
        val clean = TextErase.inpaintSmooth(bgRef, w, h, band, validBand)
        // 2) bruit du fond : un fond granuleux ou compressé ne doit pas être pris pour de la lueur
        val near = TextErase.dilate(band, w, h, 24)
        val sel = BooleanArray(n) { near[it] && validBand[it] }
        val sigma = grainSigma(px, w, h, sel)
        val tau = max(10, (2.4f * sigma).toInt())
        // 3) profil de l'écart au fond propre, anneau par anneau autour des lettres : l'épaisseur de la lueur est la distance à laquelle l'écart médian retombe
        val dcore = IntArray(n) { if (core[it]) 0 else -1 }
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in 0 until n) if (core[i]) queue[tail++] = i
        while (head < tail) {
            val i = queue[head++]; val d = dcore[i]; if (d >= grow) continue
            val x = i % w; val y = i / w
            for (k in 0..3) {
                val nx = x + (if (k == 0) 1 else if (k == 1) -1 else 0); val ny = y + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (dcore[j] >= 0 || !band[j]) continue
                dcore[j] = d + 1; queue[tail++] = j
            }
        }
        val hist = IntArray((grow + 1) * 256); val ringN = IntArray(grow + 1)
        for (i in 0 until n) { val d = dcore[i]; if (d < 1 || (avoid != null && avoid[i])) continue; hist[d * 256 + min(255, TextErase.dist(px[i], clean[i]))]++; ringN[d]++ }
        fun med(d: Int): Int { if (d < 1 || d > grow) return -1; val tot = ringN[d]; if (tot < 8) return -1; var acc = 0; for (v in 0..255) { acc += hist[d * 256 + v]; if (acc * 2 >= tot) return v }; return 255 }
        val near1 = max(med(1), med(2))
        // la lueur finit quand l'écart médian retombe sous le seuil (bruit du fond, ou 12 % de l'écart au bord des lettres) ; un écart qui ne décroît plus (palier) est du dessin, pas de la lueur
        val thr = max(tau, (0.12f * near1).toInt())
        var thick = 0
        if (near1 > tau) {
            thick = grow
            var d = 1
            while (d <= grow) {
                val m = med(d)
                if (m in 0..thr && (d == grow || med(d + 1).let { it in 0..thr || it < 0 })) { thick = d - 1; break }
                if (d >= 3 && m >= 0 && m < 0.6f * near1) { val m2 = med(d + 2); if (m2 >= 0 && m2 >= 0.9f * m) { thick = d - 1; break } }
                d++
            }
        }
        // 4) la lueur gagne, dans cette épaisseur, les pixels qui s'écartent encore du fond propre (moyenne locale hors lettres : la texture fine n'en est pas)
        val ref = IntArray(n) { if (core[it]) 0 else 1 }
        val integ = Array(4) { IntArray((w + 1) * (h + 1)) }
        for (y in 0 until h) {
            val rs = IntArray(4)
            for (x in 0 until w) {
                val i = y * w + x; val c = px[i]; val wt = ref[i]
                rs[0] += wt; rs[1] += wt * ch(c, 16); rs[2] += wt * ch(c, 8); rs[3] += wt * ch(c, 0)
                for (k in 0..3) integ[k][(y + 1) * (w + 1) + x + 1] = integ[k][y * (w + 1) + x + 1] + rs[k]
            }
        }
        val m = core.copyOf(); var added = 0
        val lowTau = max(7, (1.6f * sigma).toInt())
        if (thick > 0) for (i in 0 until n) {
            val d = dcore[i]; if (d < 1 || d > thick || m[i]) continue
            val x = i % w; val y = i / w
            val xa = max(0, x - 2); val xb = min(w, x + 3); val ya = max(0, y - 2); val yb = min(h, y + 3)
            fun box(k: Int) = integ[k][yb * (w + 1) + xb] - integ[k][ya * (w + 1) + xb] - integ[k][yb * (w + 1) + xa] + integ[k][ya * (w + 1) + xa]
            val cnt = box(0)
            val c = if (cnt >= 6) (0xFF shl 24) or ((box(1) / cnt) shl 16) or ((box(2) / cnt) shl 8) or (box(3) / cnt) else px[i]
            if (TextErase.dist(c, clean[i]) > lowTau) { m[i] = true; added++ }
        }
        // 5) fermeture (trous de 1 à 2 pixels dans le masque), puis marge de sécurité
        val closed = TextErase.erode(TextErase.dilate(m, w, h, 2), w, h, 2)
        for (i in 0 until n) if (closed[i] && limit[i]) m[i] = true
        val safe = TextErase.dilate(m, w, h, 2, limit)
        val outer = TextErase.dilate(safe, w, h, ringAlpha.size - 1, limit)
        // 6) remplissage lisse à partir des pixels intacts qui touchent la couronne
        val validF = BooleanArray(n) { !outer[it] && (avoid == null || !avoid[it]) }
        val fill = TextErase.inpaintSmooth(bgRef, w, h, outer, validF)
        // 7) opacité : pleine sur le masque sûr, en dégradé sur la couronne extérieure
        val alpha = IntArray(n)
        val dd = IntArray(n) { if (safe[it]) 0 else -1 }
        var qh = 0; var qt = 0
        for (i in 0 until n) if (safe[i]) { queue[qt++] = i; alpha[i] = ringAlpha[0] }
        while (qh < qt) {
            val i = queue[qh++]; val d = dd[i]; if (d >= ringAlpha.size - 1) continue
            val x = i % w; val y = i / w
            for (k in 0..3) {
                val nx = x + (if (k == 0) 1 else if (k == 1) -1 else 0); val ny = y + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (dd[j] >= 0 || !outer[j]) continue
                dd[j] = d + 1; alpha[j] = ringAlpha[d + 1]; queue[qt++] = j
            }
        }
        trace?.invoke("ArtFill : lettres=$coreN écart_proche=$near1 épaisseur_lueur=$thick (max $grow) lueur=+$added seuil=$tau bruit=%.1f sûr=${safe.count { it }} couronne=${outer.count { it } - safe.count { it }}".format(sigma))
        return Result(outer, safe, fill, alpha, added, sigma, thick)
    }
}
