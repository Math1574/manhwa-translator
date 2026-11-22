package app.manhwa.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Le modèle de retouche par IA, vu du noyau (LaMa entraîné sur des mangas) : [image] = plans R, G, B (h × w chacun) dans [0, 1], les pixels du trou mis à 0 ;
 * [mask] = h × w, 1 pour un pixel du trou ; h et w sont des multiples de 8, au plus [AiFill.SIDE] arrondi au multiple de 8. Rend les plans R, G, B reconstruits dans [0, 1], ou null.
 * L'application l'exécute avec ONNX Runtime ; les tests et les bancs d'essai passent un faux modèle.
 */
fun interface AiInpainter {
    fun run(image: FloatArray, mask: FloatArray, w: Int, h: Int): FloatArray?
    /** Faux quand le modèle n'est pas utilisable maintenant (module absent, réglage coupé, ouverture ratée) : la méthode classique sert, sans préparation inutile. */
    fun ready(): Boolean = true
}

/**
 * Retouche par IA du dessin sous les lettres effacées (texte posé sur un dessin, mode ART).
 *
 * La reconstruction classique ([ArtFill]) lisse le trou à partir de ses abords : sur un décor détaillé (cheveux, contours, texture d'encre), il en reste une tache floue.
 * Un modèle entraîné sur des mangas prolonge le dessin. Essai sur 106 régions réelles : LaMa « manga » le plus propre ; MI-GAN (entraîné sur des photos) invente des objets
 * dans les grands trous ; AOT laisse des fantômes de lettres.
 *
 * Une seule passe du modèle par texte, pour borner le temps (une passe de 512 × 512 prend environ 2 s sur le processeur) et la mémoire :
 * - le modèle voit le rectangle du trou élargi de [CONTEXT] px de dessin (pas toute la région d'effacement, plus large) ;
 * - jusqu'à [SIDE] px de côté, à la résolution d'origine (pas de flou) ; au-delà (grand lettrage), réduit à [SIDE] px sur le grand côté, puis agrandi ;
 * - complété au multiple de 8 par réflexion ; seuls les pixels du trou changent.
 */
object AiFill {
    const val SIDE = 640
    const val CONTEXT = 48
    /** Au-delà (en pixels de la région), la retouche par IA est laissée de côté : la reconstruction classique sert. */
    const val MAX_AREA = 4_000_000

    /** Pixels de la région [px] (w × h, ARGB) : ceux de [hole] reconstruits par le modèle, les autres inchangés. Null si rien n'est à reconstruire, si la région est démesurée ou si le modèle échoue. */
    fun fill(px: IntArray, w: Int, h: Int, hole: BooleanArray, model: AiInpainter): IntArray? {
        if (w < 2 || h < 2 || px.size < w * h || hole.size < w * h || w.toLong() * h > MAX_AREA) return null
        var hx0 = w; var hy0 = h; var hx1 = -1; var hy1 = -1
        for (y in 0 until h) for (x in 0 until w) if (hole[y * w + x]) { hx0 = min(hx0, x); hx1 = max(hx1, x); hy0 = min(hy0, y); hy1 = max(hy1, y) }
        if (hx1 < 0) return null
        // rectangle vu par le modèle : le trou et un peu de dessin autour
        val cx0 = max(0, hx0 - CONTEXT); val cy0 = max(0, hy0 - CONTEXT)
        val cw = min(w - 1, hx1 + CONTEXT) - cx0 + 1; val ch = min(h - 1, hy1 + CONTEXT) - cy0 + 1
        val scale = min(1.0, SIDE.toDouble() / max(cw, ch))
        val sw = max(2, min(cw, ceil(cw * scale - 1e-9).toInt())); val sh = max(2, min(ch, ceil(ch * scale - 1e-9).toInt()))
        // version réduite (ou copie) : moyenne des pixels connus de chaque case ; une case qui touche le trou est un trou
        val small = IntArray(sw * sh); val smallHole = BooleanArray(sw * sh)
        for (y in 0 until sh) {
            val ya = cy0 + (y.toLong() * ch / sh).toInt(); val yb = max(ya + 1, cy0 + ((y + 1).toLong() * ch / sh).toInt())
            for (x in 0 until sw) {
                val xa = cx0 + (x.toLong() * cw / sw).toInt(); val xb = max(xa + 1, cx0 + ((x + 1).toLong() * cw / sw).toInt())
                var r = 0; var g = 0; var b = 0; var n = 0; var holed = false
                for (yy in ya until yb) for (xx in xa until xb) {
                    val i = yy * w + xx
                    if (hole[i]) { holed = true; continue }
                    val c = px[i]; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                val o = y * sw + x
                if (holed || n == 0) smallHole[o] = true else small[o] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        val pw = pad8(sw); val ph = pad8(sh); val pp = pw * ph
        val image = FloatArray(3 * pp); val mask = FloatArray(pp)
        for (y in 0 until ph) {
            val sy = mirror(y, sh)
            for (x in 0 until pw) {
                val i = sy * sw + mirror(x, sw)
                val o = y * pw + x
                if (smallHole[i]) { mask[o] = 1f; continue }
                val c = small[i]
                image[o] = ((c shr 16) and 0xFF) / 255f; image[pp + o] = ((c shr 8) and 0xFF) / 255f; image[2 * pp + o] = (c and 0xFF) / 255f
            }
        }
        val out = try { model.run(image, mask, pw, ph) } catch (_: Throwable) { null } ?: return null
        if (out.size < 3 * pp) return null
        fun at(c: Int, x: Int, y: Int): Float = out[c * pp + y.coerceIn(0, sh - 1) * pw + x.coerceIn(0, sw - 1)]
        val res = px.copyOf(w * h)
        val fx = sw.toFloat() / cw; val fy = sh.toFloat() / ch
        for (y in hy0..hy1) for (x in hx0..hx1) {
            val i = y * w + x
            if (!hole[i]) continue
            // centre du pixel d'origine dans la version réduite, lecture bilinéaire
            val u = (x - cx0 + 0.5f) * fx - 0.5f; val v = (y - cy0 + 0.5f) * fy - 0.5f
            val x0 = floor(u).toInt(); val y0 = floor(v).toInt(); val ax = u - x0; val ay = v - y0
            var rgb = 0
            for (c in 0 until 3) {
                val s = (at(c, x0, y0) * (1 - ax) + at(c, x0 + 1, y0) * ax) * (1 - ay) + (at(c, x0, y0 + 1) * (1 - ax) + at(c, x0 + 1, y0 + 1) * ax) * ay
                rgb = (rgb shl 8) or (s * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
            res[i] = (0xFF shl 24) or rgb
        }
        return res
    }

    internal fun pad8(n: Int): Int = (n + 7) / 8 * 8

    /** Indice réfléchi dans [0, n) (…2 1 0 1 2… n−2 n−1 n−2…), pour compléter l'entrée au multiple de 8 sans bord artificiel. */
    internal fun mirror(i: Int, n: Int): Int {
        if (n <= 1) return 0
        val p = 2 * n - 2
        var k = i % p; if (k < 0) k += p
        return if (k < n) k else p - k
    }
}
