package app.manhwa.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Choix de la police du texte traduit pour qu'elle copie le lettrage d'origine, avec des choix cohérents d'un texte à l'autre.
 *
 *  1. Descripteur de style de chaque bloc ([StyleFeatures]) projeté par la métrique apprise ([StyleModel]) : distance à chaque police candidate de la bibliothèque ([FontLibrary]).
 *  2. Les meilleures candidates sont départagées sur le texte réel (forme de chaque lettre, largeur naturelle, graisse) ; une police plus grasse que l'original est pénalisée
 *     (jamais de texte « trop gras » qui devient illisible).
 *  3. La mesure d'un bloc est bruitée (surtout sur un dessin) : le style de chaque bloc est consolidé avec celui des blocs semblables de la page (lissage par noyau, les mesures
 *     fiables comptent plus que les mesures sur dessin) : un style identique donne la même police, un style différent (autre personnage, texte hors bulle, lettrage particulier) peut
 *     en donner une autre.
 *  4. Une mémoire de série retient les polices déjà attribuées : un style déjà vu garde sa police tant qu'une autre ne fait pas nettement mieux.
 */
object StyleMatcher {
    /** Régime de mesure : texte sur fond uni (mesure précise) ou sur un dessin (mesure bruitée). */
    enum class Regime { FLAT, ART }

    /** Une police candidate classée pour un bloc : distance de style, ressemblance de forme sur le texte, écart de largeur, pénalité de graisse, score final (plus grand = mieux). */
    class Match(val id: String, val dist: Float, val shape: Float, val penalty: Float, val score: Float, val iou: Float = 0f, val widthGap: Float = 0f)

    /** Un bloc à apparier : lignes d'encre d'origine, mesure brute, projections (espace de son régime et espace commun), poids (racine de la surface d'encre), fiabilité (0..1). */
    class Block(val lines: List<LineInk>, val raw: FloatArray, val regime: Regime, val zOwn: FloatArray, val zAll: FloatArray, val weight: Double, val reliability: Float, val slant: Float)

    // --- réglages (calibrés sur le banc de la chaîne complète et sur des pages réelles)
    /** Poids de la distance de style dans le score final. */
    const val DIST_W = 0.15f
    /** Poids de la ressemblance de forme (IoU) et de l'écart de largeur naturelle (ln du rapport) : une police beaucoup plus large ou plus étroite que l'original ne lui ressemble pas. */
    const val IOU_W = 0.6f
    const val WIDTH_W = 1.0f
    /** Au-delà de cet écart de graisse (ln) au-dessus de l'original, une police est pénalisée. */
    const val HEAVY_SLACK = 0.12f
    const val HEAVY_W = 3.0f
    /** Largeur du noyau de lissage entre blocs (distance dans l'espace commun ; deux blocs de la même police sont typiquement à 3–4, deux polices différentes à 7). */
    const val KERNEL = 4.0f
    /** Fiabilité d'une mesure sur un dessin (sur fond uni : 1). */
    const val ART_RELIABILITY = 0.5f
    /** Bonus de score qu'apporte à sa police un style déjà vu dans la série (au maximum), et distance maximale pour qu'un style soit considéré comme déjà vu. */
    const val MEMORY_BONUS = 0.15f
    const val MEMORY_RADIUS = 5.0f
    /** Au-delà de cette graisse absolue (trait / hauteur de lettre, telle que la chaîne la mesure), les contreformes se referment et le texte devient pénible à lire : pénalité douce. */
    const val ABS_HEAVY = 0.21f
    const val ABS_HEAVY_W = 6.0f
    /** Deux blocs de la même page à moins de cette distance de style qui ont reçu des polices différentes sont réunis si cela coûte peu à chacun (marge de score). */
    const val SAME_RADIUS = 5.5f
    const val SAME_TOLERANCE = 0.15f
    /** Graisses mesurées plausibles (épaisseur du trait / hauteur de lettre). */
    const val MIN_WEIGHT = 0.035f
    const val MAX_WEIGHT = 0.32f
    /** Nombre de candidates départagées sur le texte réel. */
    const val SHORTLIST = 12

    private val index: Map<String, Int> = StyleModel.ids.withIndex().associate { it.value to it.index }
    /**
     * Espace de classement. Le régime « dessin » n'a pas son propre espace : mesuré sur des fonds synthétiques très hostiles, son biais (traits trop épais) ne se retrouve pas sur de vraies
     * pages (le ciel de vos captures se mesure comme un fond uni) et faisait choisir des polices trop fines. Un bloc sur dessin se mesure comme les autres, mais compte moins ([ART_RELIABILITY]).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun space(r: Regime) = StyleModel.flat

    fun project(sp: StyleModel.Space, raw: FloatArray): FloatArray {
        val n = StyleModel.DIM_IN; val m = StyleModel.DIM_OUT
        val out = FloatArray(m)
        for (i in 0 until n) {
            val x = (raw[i] - sp.mu[i]) / sp.sd[i]
            for (j in 0 until m) out[j] += x * sp.w[i * m + j]
        }
        return out
    }

    private fun dist(sp: StyleModel.Space, z: FloatArray, k: Int): Float {
        val m = StyleModel.DIM_OUT; var s = 0f
        for (j in 0 until m) { val d = z[j] - sp.protos[k * m + j]; s += d * d }
        return sqrt(s)
    }

    private fun distZ(a: FloatArray, b: FloatArray): Float { var s = 0f; for (j in a.indices) { val d = a[j] - b[j]; s += d * d }; return sqrt(s) }

    /** Graisse (épaisseur du trait / hauteur de lettre) d'une police candidate, telle que la chaîne la mesure sur un fond uni. */
    fun weightOf(id: String): Float {
        val k = index[id] ?: return 0f
        return exp(StyleModel.flat.raw[k * StyleModel.DIM_IN])
    }

    /** Décrit un bloc ; null si l'encre est trop pauvre ou trop brouillée pour être mesurée. */
    fun describe(lines: List<LineInk>, regime: Regime = Regime.FLAT): Block? {
        // mesure peu fiable (masque d'encre brouillé par le fond ou le halo) : hauteurs de lettres très incohérentes entre les lignes
        if (lines.size >= 3) { val hs = lines.map { it.letterHeight }.sorted(); if (hs.last() > 2.2f * max(1, hs[hs.size / 2]) || hs.first() < 0.45f * hs[hs.size / 2]) return null }
        val vs = lines.mapNotNull { StyleFeatures.of(it) }
        val m = StyleFeatures.mean(vs) ?: return null
        // graisse impossible (au-delà de 0,32 de la hauteur de lettre, un trait ne laisse plus de place aux contreformes) : c'est un masque brouillé, pas un lettrage
        if (exp(m.v[0]) > MAX_WEIGHT || exp(m.v[0]) < MIN_WEIGHT) return null
        val slants = lines.mapNotNull { FontMatcher.shear(it) }
        val slant = if (slants.isEmpty()) 0f else slants.sorted()[slants.size / 2]
        val rel = if (regime == Regime.FLAT) 1f else ART_RELIABILITY
        return Block(lines, m.v, regime, project(space(regime), m.v), project(StyleModel.all, m.v), m.weight, rel, slant)
    }

    /**
     * Encre d'une ligne d'origine pour l'analyse de style : le cœur des lettres (sans leur halo ni leur contour) quand il est cohérent avec l'encre complète, c'est-à-dire d'au moins 60 %
     * de sa hauteur ; sinon l'encre complète. Un cœur réduit à des fragments (couleur du texte mal estimée) donnerait des lettres de 3 ou 4 pixels, donc un texte minuscule.
     * [s] = échelle entre l'image d'origine et celle du plan d'effacement (le plan vit dans l'image échantillonnée).
     */
    fun lineInk(plan: Erasure.Plan, s: Float, b: RectF, text: String): LineInk? {
        val x0 = (b.left * s).toInt() - plan.x0; val y0 = (b.top * s).toInt() - plan.y0; val x1 = (b.right * s).toInt() - plan.x0; val y1 = (b.bottom * s).toInt() - plan.y0
        val full = plan.ink?.let { FontMatcher.lineInk(it, plan.w, plan.h, x0, y0, x1, y1, text) }
        val core = plan.core?.let { FontMatcher.lineInk(it, plan.w, plan.h, x0, y0, x1, y1, text) }
        // un « cœur » qui remplit presque toute la ligne est une plaque (contours de lettres qui se touchent, espaces et contreformes comblés), pas un lettrage
        val coreOk = core != null && boxFill(core) <= 0.72f
        return when {
            coreOk && full != null -> if (core!!.letterHeight >= 0.6f * full.letterHeight) core else full
            else -> full ?: (if (coreOk) core else null)
        }
    }

    private fun boxFill(l: LineInk): Float { var n = 0; for (v in l.mask) if (v) n++; return n.toFloat() / (l.w.toLong() * l.h).toFloat() }

    /** Pénalité de graisse : une police plus grasse que l'original de plus de [HEAVY_SLACK] (en ln) est pénalisée ; une plus fine l'est à peine. */
    private fun heavyPenalty(b: Block, k: Int): Float {
        val cand = space(b.regime).raw[k * StyleModel.DIM_IN]
        val diff = cand - b.raw[0]
        var pen = if (diff > HEAVY_SLACK) HEAVY_W * (diff - HEAVY_SLACK) else 0f
        val w = exp(cand)
        if (w > ABS_HEAVY) pen += ABS_HEAVY_W * (w - ABS_HEAVY)
        return pen
    }

    /** Mémoire de série : styles déjà vus (centres dans l'espace commun) et police attribuée. */
    class Memory(private val maxSeries: Int = 64, private val perSeries: Int = 40) {
        class Exemplar(var z: FloatArray, var id: String, var weight: Double)
        private val series = object : LinkedHashMap<String, ArrayList<Exemplar>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayList<Exemplar>>?): Boolean = size > maxSeries
        }

        /** Styles déjà vus de la série, avec leur distance à [z] (seulement ceux qui sont à moins de [MEMORY_RADIUS]). */
        @Synchronized fun neighbors(key: String?, z: FloatArray): List<Pair<Exemplar, Float>> {
            if (key == null) return emptyList()
            val list = series[key] ?: return emptyList()
            return list.mapNotNull { e -> val d = distZ(z, e.z); if (d <= MEMORY_RADIUS) e to d else null }
        }

        @Synchronized fun remember(key: String?, z: FloatArray, id: String, weight: Double) {
            if (key == null) return
            val list = series.getOrPut(key) { ArrayList() }
            var near: Exemplar? = null; var bd = MEMORY_RADIUS * 0.6f
            for (e in list) { val d = distZ(z, e.z); if (d <= bd) { bd = d; near = e } }
            if (near != null) {
                if (near.id == id) {                                      // même police : le centre suit le style (moyenne glissante)
                    val a = (weight / (near.weight + weight)).toFloat().coerceIn(0.05f, 0.4f)
                    near.z = FloatArray(z.size) { near!!.z[it] * (1 - a) + z[it] * a }; near.weight = min(near.weight + weight, 50.0)
                } else { near.id = id; near.z = z.copyOf(); near.weight = weight }
            } else {
                list.add(Exemplar(z.copyOf(), id, weight))
                if (list.size > perSeries) list.removeAt(0)
            }
        }

        @Synchronized fun clear() = series.clear()
    }

    /** Décision d'un bloc : police retenue, classement détaillé (pour le journal) et indication que la mémoire de série a pesé dans le choix. */
    class Decision(val id: String, val ranking: List<Match>, val fromMemory: Boolean)

    /**
     * Police de chaque bloc d'une page. Étape 1 : distance de style de chaque bloc à toutes les candidates, consolidée avec celle des blocs semblables de la page (lissage par noyau,
     * pondéré par le poids et la fiabilité de chacun). Étape 2 : bonus pour les polices déjà attribuées à un style semblable dans la [series]. Étape 3 : les meilleures candidates sont
     * départagées sur le texte réel du bloc (forme, largeur) et pénalisées si plus grasses que l'original. Les blocs sans encre mesurable (null) reçoivent la police du bloc
     * décidé le plus proche dans l'ordre de lecture, sinon null (l'appelant prend alors la police par défaut).
     */
    fun decide(blocks: List<Block?>, renderer: GlyphRenderer?, memory: Memory?, series: String?, allowed: ((String) -> Boolean)? = null): List<Decision?> {
        val n = blocks.size; val nc = StyleModel.ids.size
        val out = arrayOfNulls<Decision>(n)
        val live = blocks.indices.filter { blocks[it] != null }
        // distances de style de chaque bloc à chaque candidate, dans l'espace de son régime
        val d1 = HashMap<Int, FloatArray>()
        for (i in live) { val b = blocks[i]!!; val sp = space(b.regime); d1[i] = FloatArray(nc) { dist(sp, b.zOwn, it) } }
        // les blocs fiables et lourds d'abord
        val order = live.sortedByDescending { blocks[it]!!.weight * blocks[it]!!.reliability }
        // sans série connue, la mémoire n'est valable que pour cette page
        val mem = if (series != null && memory != null) memory else Memory()
        val key = series ?: "page"
        val rankings = HashMap<Int, List<Match>>(); val fromMem = HashMap<Int, Boolean>()
        for (i in order) {
            val b = blocks[i]!!
            // 1. lissage par noyau avec les blocs semblables de la page
            val sm = FloatArray(nc); var wsum = 0.0
            for (j in live) {
                val o = blocks[j]!!
                val k = if (i == j) 1.0 else kotlin.math.exp(-(distZ(b.zAll, o.zAll) / KERNEL).let { it * it }.toDouble())
                val w = k * o.weight * o.reliability
                if (w < 1e-6) continue
                val dj = d1[j]!!
                for (c in 0 until nc) sm[c] += (w * dj[c]).toFloat()
                wsum += w
            }
            for (c in 0 until nc) sm[c] = (sm[c] / wsum).toFloat()
            // 2. mémoire de série : bonus à la police des styles déjà vus
            val bonus = HashMap<String, Float>()
            for ((e, d) in mem.neighbors(key, b.zAll)) { val k = kotlin.math.exp(-((d / KERNEL) * (d / KERNEL)).toDouble()).toFloat(); bonus.merge(e.id, MEMORY_BONUS * k, ::max) }
            // 3. candidates : les plus proches en style (lissé) et celles de la mémoire, départagées sur le texte réel
            val cand = (0 until nc).filter { allowed == null || allowed(StyleModel.ids[it]) }
            val near = cand.sortedBy { sm[it] }.take(SHORTLIST).toMutableList()
            for (id in bonus.keys) { val k = index[id] ?: continue; if (k !in near && (allowed == null || allowed(id))) near.add(k) }
            val used = b.lines.sortedByDescending { it.w.toLong() * it.h }.take(3)
            rankings[i] = near.map { k ->
                val id = StyleModel.ids[k]
                var shape = 0f; var iouAvg = 0f; var gap = 0f
                if (renderer != null) {
                    var si = 0f; var sg = 0f; var c = 0
                    for (l in used) {
                        val g = renderer.render(l.text, id, 0f, 0f, l.w, l.h) ?: continue
                        var inter = 0; var uni = 0
                        for (q in l.mask.indices) { val a = l.mask[q]; val bb = g.mask[q]; if (a && bb) inter++; if (a || bb) uni++ }
                        si += if (uni == 0) 0f else inter.toFloat() / uni
                        sg += abs(ln(max(g.stretch, 1e-3f))); c++
                    }
                    // la forme et la largeur mesurées sur un dessin sont moins fiables : elles pèsent moins
                    if (c > 0) { iouAvg = si / c; gap = sg / c; shape = (IOU_W * iouAvg - WIDTH_W * gap) * (0.5f + 0.5f * b.reliability) } else shape = -1f
                }
                val pen = heavyPenalty(b, k)
                Match(id, sm[k], shape, pen, shape - DIST_W * sm[k] - pen + (bonus[id] ?: 0f), iouAvg, gap)
            }.sortedByDescending { it.score }
            fromMem[i] = bonus.isNotEmpty()
        }
        // 4. réunion : deux blocs de style très proche qui ont reçu des polices différentes prennent la même si cela coûte peu à chacun
        val chosen = HashMap<Int, String>(); for (i in live) chosen[i] = rankings[i]!!.first().id
        repeat(2) {
            for (x in order.indices) for (y in x + 1 until order.size) {
                val i = order[x]; val j = order[y]
                val ci = chosen[i]!!; val cj = chosen[j]!!
                if (ci == cj || distZ(blocks[i]!!.zAll, blocks[j]!!.zAll) > SAME_RADIUS) continue
                val ri = rankings[i]!!.associateBy { it.id }; val rj = rankings[j]!!.associateBy { it.id }
                val bi = rankings[i]!!.first().score; val bj = rankings[j]!!.first().score
                val wi = blocks[i]!!.weight * blocks[i]!!.reliability; val wj = blocks[j]!!.weight * blocks[j]!!.reliability
                var pick: String? = null; var pickTotal = -Double.MAX_VALUE
                for (c in listOf(ci, cj)) {
                    val si = ri[c]?.score ?: continue; val sj = rj[c]?.score ?: continue
                    // un bloc sur dessin (mesure moins fiable) accepte une perte plus grande pour rejoindre ses voisins
                    val tol = SAME_TOLERANCE * (2f - min(blocks[i]!!.reliability, blocks[j]!!.reliability))
                    if (bi - si > tol || bj - sj > tol) continue
                    val t = wi * si + wj * sj
                    if (t > pickTotal) { pickTotal = t; pick = c }
                }
                if (pick != null) { chosen[i] = pick; chosen[j] = pick }
            }
        }
        for (i in live) {
            val b = blocks[i]!!
            out[i] = Decision(chosen[i]!!, rankings[i]!!, fromMem[i] == true)
            mem.remember(key, b.zAll, chosen[i]!!, b.weight * b.reliability)
        }
        // blocs sans mesure : police du bloc décidé le plus proche dans l'ordre de lecture
        for (i in 0 until n) if (out[i] == null && live.isNotEmpty()) {
            val j = live.minByOrNull { abs(it - i) }!!
            out[i] = Decision(out[j]!!.id, emptyList(), true)
        }
        return out.toList()
    }
}
