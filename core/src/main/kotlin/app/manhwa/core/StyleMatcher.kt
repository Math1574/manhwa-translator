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
 *  1. Seules les polices robustes de la bibliothèque ([FontLibrary.Spec.auto]) sont candidates : le build 110 choisissait aussi des écritures décoratives ou fines (Rock Salt, Covered By
 *     Your Grace…) pour des bulles au lettrage gras, avec une police différente d'une bulle à l'autre alors que l'original a la même.
 *  2. Les blocs d'apparence identique (même type de bloc, même couleur de texte, même contour, taille voisine) forment un groupe qui reçoit UNE police ; la graisse et le style du groupe
 *     sont ceux de ses blocs, pondérés par leur fiabilité (une mesure sur dessin est bruitée).
 *  3. Score d'une police : proximité de graisse avec l'original (avec un léger biais vers le gras), distance de style apprise ([StyleModel]), forme et largeur sur le texte réel, pénalités
 *     (plus grasse que l'original, police moins lisible), bonus des polices déjà attribuées à un style semblable dans la série.
 */
object StyleMatcher {
    /** Régime de mesure : texte sur fond uni (mesure précise) ou sur un dessin (mesure bruitée). */
    enum class Regime { FLAT, ART }

    /** Une police candidate classée pour un bloc : distance de style, ressemblance de forme sur le texte, écart de largeur, pénalité de graisse, score final (plus grand = mieux). */
    class Match(val id: String, val dist: Float, val shape: Float, val penalty: Float, val score: Float, val iou: Float = 0f, val widthGap: Float = 0f)

    /**
     * Un bloc à apparier : lignes d'encre d'origine, mesure brute, projections (espace de son régime et espace commun), poids (racine de la surface d'encre), fiabilité (0..1),
     * apparence ([ident] : type de bloc, couleur du texte, contour ; vide = inconnue) et hauteur de lettre ([size], pixels de l'image).
     */
    class Block(val lines: List<LineInk>, val raw: FloatArray, val regime: Regime, val zOwn: FloatArray, val zAll: FloatArray, val weight: Double, val reliability: Float, val slant: Float,
                val ident: String = "", val size: Float = 0f)

    // --- réglages (calibrés sur le banc de la chaîne complète et sur des pages réelles)
    /** Poids de la distance de style dans le score final. */
    const val DIST_W = 0.03f
    /** Proximité de graisse (écart en ln entre la graisse de la police et celle de l'original, majorée de [WEIGHT_BIAS] : on préfère un peu plus gras, jamais trop fin). */
    const val WEIGHT_W = 1.2f
    const val WEIGHT_BIAS = 1.15f
    /** Une police plus fine que la graisse visée coûte [LIGHT_FACTOR] fois plus qu'une police plus grasse du même écart : retour d'usage, le texte traduit manquait de gras. */
    const val LIGHT_FACTOR = 1.5f
    /** Deux blocs de même apparence sont du même groupe si leur hauteur de lettre est dans ce rapport et leur style à moins de [GROUP_DIST] (espace commun). */
    const val SIZE_RATIO = 1.35f
    /** Deux groupes de même famille de style dont la hauteur de lettre est dans ce rapport reçoivent la même police. */
    const val FAMILY_SIZE_RATIO = 1.6f
    const val GROUP_DIST = 7.0f
    /** Distance maximale pour qu'un bloc sur dessin (mesure bruitée) rejoigne un groupe fiable. */
    const val ART_GROUP_DIST = 9.0f
    /** Une police à empattement n'est candidate que si les montants sont nettement plus épais que les barres (ln du rapport, mesure du groupe). */
    const val SERIF_CONTRAST = 0.45f
    /** Bonus (au maximum) aux polices choisies pour un autre groupe fiable de la même page, proches en style : un bloc sur dessin se range volontiers derrière ses voisins. */
    const val PEER_BONUS = 0.10f
    const val PEER_KERNEL = 6.0f
    /** Poids de la ressemblance de forme (IoU) et de l'écart de largeur naturelle (ln du rapport) : une police beaucoup plus large ou plus étroite que l'original ne lui ressemble pas. */
    const val IOU_W = 0.6f
    const val WIDTH_W = 1.5f
    /** Au-delà de cet écart de graisse (ln) au-dessus de l'original, une police est pénalisée. */
    const val HEAVY_SLACK = 0.15f
    const val HEAVY_W = 3.0f
    /** Largeur du noyau de lissage entre blocs (distance dans l'espace commun ; deux blocs de la même police sont typiquement à 3–4, deux polices différentes à 7). */
    const val KERNEL = 4.0f
    /** Fiabilité d'une mesure sur un dessin (sur fond uni : 1). */
    const val ART_RELIABILITY = 0.5f
    /** Bonus de score qu'apporte à sa police un style déjà vu dans la série (au maximum), et distance maximale pour qu'un style soit considéré comme déjà vu. */
    const val MEMORY_BONUS = 0.20f
    const val MEMORY_RADIUS = 5.0f
    /** Au-delà de cette graisse absolue (trait / hauteur de lettre, telle que la chaîne la mesure), les contreformes se referment et le texte devient pénible à lire : pénalité douce. */
    const val ABS_HEAVY = 0.21f
    const val ABS_HEAVY_W = 6.0f
    /** Deux blocs de la même page à moins de cette distance de style qui ont reçu des polices différentes sont réunis si cela coûte peu à chacun (marge de score). */
    const val SAME_RADIUS = 5.5f
    const val SAME_TOLERANCE = 0.15f
    /**
     * Famille de style : la mesure de la classe de style (distance minimale aux polices de la bibliothèque de chaque classe) est fiable, même sur des blocs bruités, bien plus que le choix d'une
     * police précise. SCRIPT = comic, écriture et brush (lettrage de bulle) ; CONDENSED = condensés et titres gras ; SANS ; SERIF = empattement et machine à écrire.
     */
    enum class Family { SCRIPT, CONDENSED, SANS, SERIF }
    fun familyOf(cls: String?): Family = when (cls) { "comic", "hand", "brush" -> Family.SCRIPT; "condensed", "display" -> Family.CONDENSED; "serif", "mono" -> Family.SERIF; else -> Family.SANS }
    /** Préférence pour la famille SCRIPT (en distance) : lettrage de bulle le plus répandu, elle l'emporte si une autre famille n'est pas nettement plus proche. */
    const val SCRIPT_PREFERENCE = 0.5f
    /** Préférence pour la famille déjà attribuée à un style semblable dans la série. */
    const val FAMILY_MEMORY = 0.6f
    /** Police par défaut (le lettrage de bulle le plus répandu) et distance de style au-delà de laquelle la mesure est hors norme (texte en dégradé, lueur, fond chargé) : on ne s'y fie plus. */
    const val DEFAULT = "comicneue-bold"
    const val OOD_DIST = 10.0f
    const val DEFAULT_BONUS = 0.25f
    /** Bonus permanent de la police par défaut : à mesure équivalente, le lettrage de bulle le plus répandu l'emporte. */
    const val DEFAULT_PRIOR = 0.05f
    /** Graisses mesurées plausibles (épaisseur du trait / hauteur de lettre). */
    const val MIN_WEIGHT = 0.035f
    const val MAX_WEIGHT = 0.32f
    /** Nombre de candidates départagées sur le texte réel. */
    const val SHORTLIST = 16
    /** Nombre de lignes (les plus grandes) sur lesquelles on dessine chaque candidate : la décision coûte du dessin de texte, et elle est faite à chaque page. */
    const val SHAPE_LINES = 2

    private val index: Map<String, Int> = StyleModel.ids.withIndex().associate { it.value to it.index }
    /**
     * Espace de classement. Le régime « dessin » n'a pas son propre espace : mesuré sur des fonds synthétiques très hostiles, son biais (traits trop épais) ne se retrouve pas sur de vraies
     * pages (le ciel de vos captures se mesure comme un fond uni) et faisait choisir des polices trop fines. Un bloc sur dessin se mesure comme les autres, mais compte moins ([ART_RELIABILITY]).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun space(r: Regime) = StyleModel.flat

    /**
     * Apparence d'un bloc : type (dans une bulle ou texte libre), couleur du texte (clair ou foncé) et contour. Dans un même chapitre, des blocs de même apparence ont presque toujours le même
     * lettrage dans l'original (même personnage, même rôle) : ils doivent recevoir la même police, quelle que soit la précision de la mesure de chacun.
     */
    fun identOf(inBubble: Boolean, textColor: Int, background: Int, outlined: Boolean): String {
        fun lum(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        // texte clair seulement s'il est franchement plus clair que le fond : la couleur détectée d'un texte noir sur fond blanc est parfois le blanc du fond
        val light = lum(textColor) > 140 && lum(textColor) > lum(background) + 25
        return (if (inBubble) "B" else "F") + (if (light) "L" else "D") + (if (outlined) "O" else "P")
    }

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
    fun describe(lines: List<LineInk>, regime: Regime = Regime.FLAT, ident: String = ""): Block? {
        // mesure peu fiable (masque d'encre brouillé par le fond ou le halo) : hauteurs de lettres très incohérentes entre les lignes
        if (lines.size >= 3) { val hs = lines.map { it.letterHeight }.sorted(); if (hs.last() > 2.2f * max(1, hs[hs.size / 2]) || hs.first() < 0.45f * hs[hs.size / 2]) return null }
        val vs = lines.mapNotNull { StyleFeatures.of(it) }
        val m = StyleFeatures.mean(vs) ?: return null
        // graisse impossible (au-delà de 0,32 de la hauteur de lettre, un trait ne laisse plus de place aux contreformes) : c'est un masque brouillé, pas un lettrage
        if (exp(m.v[0]) > MAX_WEIGHT || exp(m.v[0]) < MIN_WEIGHT) return null
        val slants = lines.mapNotNull { FontMatcher.shear(it) }
        val slant = if (slants.isEmpty()) 0f else slants.sorted()[slants.size / 2]
        val rel = if (regime == Regime.FLAT) 1f else ART_RELIABILITY
        val size = lines.map { it.letterHeight }.sorted().let { it[it.size / 2] }.toFloat()
        return Block(lines, m.v, regime, project(space(regime), m.v), project(StyleModel.all, m.v), m.weight, rel, slant, ident, size)
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

    /** Écart de graisse (en ln) entre une police et la graisse visée, majoré quand la police est plus fine. */
    private fun weightGap(candLn: Float, targetLn: Float): Float = if (candLn < targetLn) (targetLn - candLn) * LIGHT_FACTOR else candLn - targetLn

    /** Pénalité de graisse : une police plus grasse que l'original de plus de [HEAVY_SLACK] (en ln) est pénalisée, ainsi qu'une police très grasse en valeur absolue ; une plus fine l'est ailleurs (voir [WEIGHT_W]). */
    private fun heavyPenalty(measuredLn: Float, k: Int): Float {
        val cand = StyleModel.flat.raw[k * StyleModel.DIM_IN]
        val diff = cand - (measuredLn + ln(WEIGHT_BIAS))        // la mesure brute sous-estime la graisse d'environ 15 % : on compare à la graisse visée
        var pen = if (diff > HEAVY_SLACK) HEAVY_W * (diff - HEAVY_SLACK) else 0f
        val w = exp(cand)
        if (w > ABS_HEAVY) pen += ABS_HEAVY_W * (w - ABS_HEAVY)
        return pen
    }

    /** Mémoire de série : styles déjà vus (centres dans l'espace commun, avec leur apparence) et police attribuée. */
    class Memory(private val maxSeries: Int = 64, private val perSeries: Int = 40) {
        class Exemplar(var z: FloatArray, var id: String, var weight: Double, val ident: String)
        private val series = object : LinkedHashMap<String, ArrayList<Exemplar>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayList<Exemplar>>?): Boolean = size > maxSeries
        }

        /** Styles déjà vus de la série, de même apparence que [ident] (si connue), avec leur distance à [z] (seulement ceux qui sont à moins de [MEMORY_RADIUS]). */
        @Synchronized fun neighbors(key: String?, z: FloatArray, ident: String = ""): List<Pair<Exemplar, Float>> {
            if (key == null) return emptyList()
            val list = series[key] ?: return emptyList()
            return list.mapNotNull { e -> val d = distZ(z, e.z); if (d <= MEMORY_RADIUS && (ident.isEmpty() || e.ident == ident)) e to d else null }
        }

        @Synchronized fun remember(key: String?, z: FloatArray, id: String, weight: Double, ident: String = "") {
            if (key == null) return
            val list = series.getOrPut(key) { ArrayList() }
            var near: Exemplar? = null; var bd = MEMORY_RADIUS * 0.6f
            for (e in list) { if (e.ident != ident) continue; val d = distZ(z, e.z); if (d <= bd) { bd = d; near = e } }
            if (near != null) {
                if (near.id == id) {                                      // même police : le centre suit le style (moyenne glissante)
                    val a = (weight / (near.weight + weight)).toFloat().coerceIn(0.05f, 0.4f)
                    near.z = FloatArray(z.size) { near!!.z[it] * (1 - a) + z[it] * a }; near.weight = min(near.weight + weight, 50.0)
                } else { near.id = id; near.z = z.copyOf(); near.weight = weight }
            } else {
                list.add(Exemplar(z.copyOf(), id, weight, ident))
                if (list.size > perSeries) list.removeAt(0)
            }
        }

        @Synchronized fun clear() = series.clear()
    }

    /** Décision d'un bloc : police retenue, classement détaillé du groupe (pour le journal), indication que la mémoire de série a pesé dans le choix, famille de style retenue. */
    class Decision(val id: String, val ranking: List<Match>, val fromMemory: Boolean, val family: String = "")

    /**
     * Police de chaque bloc d'une page.
     *  1. Groupes : les blocs de style voisin (distance dans l'espace commun) et de hauteur de lettre voisine forment un groupe qui reçoit UNE police, que les blocs soient dans une bulle ou sur
     *     un dessin : un même personnage garde sa police partout. Les blocs fiables (fond uni) fixent les groupes, un bloc sur dessin (mesure bruitée) s'y rattache s'il n'est pas trop éloigné.
     *  2. Famille de style (script/comic, condensé, sans empattement, empattement) : distance minimale du groupe aux polices de la bibliothèque de chaque famille, avec une préférence pour la
     *     famille SCRIPT (lettrage de bulle) et pour la famille déjà attribuée à un style semblable dans la série. Mesure fiable même quand le choix d'une police précise ne l'est pas.
     *  3. Dans la famille : proximité de graisse (jamais nettement plus fin, jamais illisiblement gras), distance de style, bonus de la mémoire de série et de la police par défaut, puis
     *     départage sur le texte réel (forme, largeur) de tous les blocs du groupe.
     * Les blocs sans encre mesurable (null) reçoivent la police du bloc décidé le plus proche dans l'ordre de lecture, sinon null (l'appelant prend alors la police par défaut).
     */
    fun decide(blocks: List<Block?>, renderer: GlyphRenderer?, memory: Memory?, series: String?, allowed: ((String) -> Boolean)? = null): List<Decision?> {
        val n = blocks.size; val nc = StyleModel.ids.size
        val out = arrayOfNulls<Decision>(n)
        val live = blocks.indices.filter { blocks[it] != null }
        // sans série connue, la mémoire n'est valable que pour cette page
        val mem = if (series != null && memory != null) memory else Memory()
        val key = series ?: "page"
        val famOf = Array(nc) { familyOf(FontLibrary.spec(StyleModel.ids[it])?.cls) }
        var eligible = BooleanArray(nc) { FontLibrary.spec(StyleModel.ids[it])?.auto == true && (allowed == null || allowed(StyleModel.ids[it])) }
        if (eligible.none { it }) eligible = BooleanArray(nc) { allowed == null || allowed(StyleModel.ids[it]) }
        // 1. groupes : d'abord les blocs fiables et lourds
        class Group(var z: FloatArray, var w: Double, var size: Float, var reliable: Boolean, val members: ArrayList<Int>)
        val groups = ArrayList<Group>()
        for (i in live.sortedWith(compareByDescending<Int> { blocks[it]!!.reliability }.thenByDescending { blocks[it]!!.weight })) {
            val b = blocks[i]!!
            var best: Group? = null; var bd = Float.MAX_VALUE
            for (g in groups) {
                val ratio = if (g.size > 0f && b.size > 0f) max(g.size, b.size) / min(g.size, b.size) else 1f
                val limit = if (b.reliability >= 0.99f && g.reliable) GROUP_DIST else ART_GROUP_DIST
                val d = distZ(b.zAll, g.z)
                if (ratio <= SIZE_RATIO && d <= limit && d < bd) { bd = d; best = g }
            }
            if (best == null) groups.add(Group(b.zAll.copyOf(), b.weight, b.size, b.reliability >= 0.99f, arrayListOf(i)))
            else {
                val a = (b.weight / (best.w + b.weight)).toFloat()
                best.z = FloatArray(best.z.size) { best!!.z[it] * (1 - a) + b.zAll[it] * a }; best.w += b.weight; best.members.add(i)
            }
        }
        // 2. famille de style de chaque groupe
        class Fam(val family: Family, val ood: Boolean, val memHit: Boolean)
        val bonusOf = HashMap<Group, HashMap<String, Float>>()
        val famOfGroup = HashMap<Group, Fam>()
        for (g in groups) {
            val ms = g.members.map { blocks[it]!! }
            val wsum = ms.sumOf { it.weight * it.reliability }
            val contrast = (ms.sumOf { it.weight * it.reliability * it.raw[3] } / wsum).toFloat()
            val dGroup = FloatArray(nc) { c -> var d = 0.0; for (b in ms) d += b.weight * b.reliability * dist(space(b.regime), b.zOwn, c); (d / wsum).toFloat() }
            val famDist = FloatArray(Family.values().size) { Float.MAX_VALUE }
            for (c in 0 until nc) { val f = famOf[c].ordinal; if (dGroup[c] < famDist[f]) famDist[f] = dGroup[c] }
            val ood = famDist.min() > OOD_DIST
            val bonus = HashMap<String, Float>(); var memHit = false
            for ((e, d) in mem.neighbors(key, g.z)) {
                val k = kotlin.math.exp(-((d / KERNEL) * (d / KERNEL)).toDouble()).toFloat(); bonus.merge(e.id, MEMORY_BONUS * k, ::max); memHit = true
                index[e.id]?.let { famDist[famOf[it].ordinal] -= FAMILY_MEMORY * k }
            }
            famDist[Family.SCRIPT.ordinal] -= SCRIPT_PREFERENCE
            if (contrast < SERIF_CONTRAST) famDist[Family.SERIF.ordinal] = Float.MAX_VALUE
            famOfGroup[g] = Fam(if (ood) Family.SCRIPT else Family.values().minByOrNull { famDist[it.ordinal] }!!, ood, memHit)
            bonusOf[g] = bonus
        }
        // 3. les groupes de même famille et de hauteur de lettre voisine reçoivent la même police (un chapitre n'a en général qu'un lettrage de dialogue) : fusion
        class Cluster(val family: Family, val groups: ArrayList<Group>, var size: Float)
        val clusters = ArrayList<Cluster>()
        for (g in groups.sortedBy { it.size }) {
            val f = famOfGroup[g]!!.family
            val cl = clusters.firstOrNull { it.family == f && (it.size <= 0f || g.size <= 0f || g.size / it.size <= FAMILY_SIZE_RATIO) }
            if (cl == null) clusters.add(Cluster(f, arrayListOf(g), g.size)) else cl.groups.add(g)
        }
        for (cl in clusters.sortedByDescending { c -> c.groups.sumOf { it.w } }) {
            val family = cl.family
            val members = cl.groups.flatMap { it.members }
            val ms = members.map { blocks[it]!! }
            val wsum = ms.sumOf { it.weight * it.reliability }
            val relAvg = (wsum / ms.sumOf { it.weight }).toFloat()
            val measuredLn = (ms.sumOf { it.weight * it.reliability * it.raw[0] } / wsum).toFloat()
            val wo = exp(measuredLn) * WEIGHT_BIAS
            val ood = cl.groups.all { famOfGroup[it]!!.ood }
            val memHit = cl.groups.any { famOfGroup[it]!!.memHit }
            val bonus = HashMap<String, Float>(); for (g in cl.groups) for ((id, v) in bonusOf[g]!!) bonus.merge(id, v, ::max)
            val dGroup = FloatArray(nc) { c -> var d = 0.0; for (b in ms) d += b.weight * b.reliability * dist(space(b.regime), b.zOwn, c); (d / wsum).toFloat() }
            val base = FloatArray(nc) { Float.NEGATIVE_INFINITY }
            for (c in 0 until nc) {
                if (!eligible[c] || famOf[c] != family) continue
                val id = StyleModel.ids[c]; val spec = FontLibrary.spec(id)
                val wTerm = weightGap(StyleModel.flat.raw[c * StyleModel.DIM_IN], ln(wo)) * WEIGHT_W * (0.5f + 0.5f * relAvg)
                base[c] = (if (ood) 0f else -DIST_W * dGroup[c]) - (if (ood) 0.5f else 1f) * wTerm - heavyPenalty(measuredLn, c) - (spec?.penalty ?: 0f) + (bonus[id] ?: 0f) +
                    (if (id == DEFAULT) (if (ood) DEFAULT_BONUS else DEFAULT_PRIOR) else 0f)
            }
            if (base.all { it == Float.NEGATIVE_INFINITY }) for (c in 0 until nc) if (eligible[c]) base[c] = -DIST_W * dGroup[c]
            val near = (0 until nc).filter { base[it] > Float.NEGATIVE_INFINITY }.sortedByDescending { base[it] }.take(SHORTLIST)
            val ranked = near.map { c ->
                val id = StyleModel.ids[c]
                var shape = 0f; var iouAvg = 0f; var gap = 0f
                if (renderer != null) {
                    var sh = 0.0; var si = 0.0; var sg = 0.0; var wt = 0.0
                    for (b in ms) {
                        val used = b.lines.sortedByDescending { it.w.toLong() * it.h }.take(SHAPE_LINES)
                        var li = 0f; var lg = 0f; var cnt = 0
                        for (l in used) {
                            val gl = renderer.render(l.text, id, 0f, 0f, l.w, l.h) ?: continue
                            var inter = 0; var uni = 0
                            for (q in l.mask.indices) { val a = l.mask[q]; val bb = gl.mask[q]; if (a && bb) inter++; if (a || bb) uni++ }
                            li += if (uni == 0) 0f else inter.toFloat() / uni
                            lg += abs(ln(max(gl.stretch, 1e-3f))); cnt++
                        }
                        if (cnt == 0) continue
                        val w = b.weight * b.reliability
                        si += w * li / cnt; sg += w * lg / cnt
                        sh += w * (IOU_W * li / cnt - WIDTH_W * lg / cnt) * (0.5f + 0.5f * b.reliability); wt += w
                    }
                    if (wt > 0.0) { shape = (sh / wt).toFloat(); iouAvg = (si / wt).toFloat(); gap = (sg / wt).toFloat() } else shape = -1f
                }
                val pen = heavyPenalty(measuredLn, c) + (FontLibrary.spec(id)?.penalty ?: 0f)
                Match(id, dGroup[c], shape, pen, base[c] + shape, iouAvg, gap)
            }.sortedByDescending { it.score }
            val best = ranked.first()
            for (i in members) out[i] = Decision(best.id, ranked, memHit && bonus.containsKey(best.id), family.name)
            for (g in cl.groups) mem.remember(key, g.z, best.id, g.w)
        }
        // 4. blocs sans mesure : police du bloc décidé le plus proche dans l'ordre de lecture
        for (i in 0 until n) if (out[i] == null && live.isNotEmpty()) {
            val j = live.minByOrNull { abs(it - i) }!!
            out[i] = Decision(out[j]!!.id, emptyList(), true, out[j]!!.family)
        }
        return out.toList()
    }
}
