package app.manhwa.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Polices stylisées ([FontLibrary.styled] : technique, gothique, horreur, script, pochoir, titres percutants), choisies automatiquement mais SEULEMENT quand c'est nécessaire.
 *
 * Le choix ordinaire ([StyleMatcher]) reste la règle : une police stylisée y a un coût (si elle est choisie à tort, tout un chapitre change d'allure). Deux cas seulement la font entrer :
 *  A. fenêtre « système » : texte entre crochets, clair sur fond sombre (« [Quête] », « [Niveau supérieur] ») → police technique ([SYSTEM_FONT]) ;
 *  B. lettrage d'origine que AUCUNE police ordinaire ne copie (la meilleure ne recouvre pas l'encre, [REF_MAX]) alors qu'une police stylisée la recouvre nettement mieux
 *     ([MIN_IOU], [IOU_MARGIN]), sans déformation exagérée ([MAX_GAP]), sur un texte assez long pour que la mesure compte ([MIN_LETTERS]), sur fond uni (la mesure sur un dessin est bruitée).
 *     Seules les polices vraiment distinctives sont candidates ([candidates] : gothique, horreur, script, Knewave) ; le pochoir, les titres épais et la technique ressemblent trop à des
 *     polices ordinaires grasses (ils les remplaçaient à tort sur le banc d'essai) : ils ne s'obtiennent qu'à la main (éditeur, onglet « Police »), ou par le cas A pour la technique.
 * La décision est prise pour tout un groupe (les blocs qui ont reçu la même police ordinaire, voir [StyleMatcher.Decision]) : un même lettrage garde la même police partout.
 * Seuils réglés sur le banc d'essai de la chaîne (tools/fontlib/harness/StyledBench.kt, 1144 échantillons de polices ordinaires privées de toute leur famille) : aucune fausse décision ;
 * quand le lettrage est celui d'une candidate, elle est retrouvée dans près de la moitié des cas (la plupart des autres sont ceux où une police ordinaire convient aussi).
 */
object StyledFonts {
    /** Ce que le choix ordinaire ne connaît pas d'un bloc : texte d'origine, texte clair sur fond sombre, bloc dans une bulle. */
    class Hint(val source: String, val lightOnDark: Boolean, val inBubble: Boolean = false)

    /** Police stylisée retenue pour un bloc, et la raison (journal de diagnostic). */
    class Pick(val id: String, val reason: String)

    /** Une candidate stylisée : recouvrement de l'encre d'origine (IoU), écart de largeur (|ln étirement|), score de forme (même formule que [StyleMatcher]). */
    class Cand(val id: String, val iou: Float, val gap: Float) {
        val score: Float get() = StyleMatcher.IOU_W * iou - StyleMatcher.WIDTH_W * gap
    }

    /** Mesure d'un groupe : meilleure police ordinaire (recouvrement, score de forme) et candidates stylisées classées. */
    class Verdict(val refIou: Float, val refScore: Float, val cands: List<Cand>, val letters: Int, val flat: Boolean)

    /** Police des fenêtres système. */
    const val SYSTEM_FONT = "rajdhani-bold"
    /** Au-delà de ce recouvrement, une police ordinaire copie déjà bien le lettrage : on ne cherche pas plus loin. */
    const val REF_MAX = 0.60f
    /** Recouvrement minimal de la police stylisée, et avance minimale sur la meilleure police ordinaire. */
    const val MIN_IOU = 0.60f
    const val IOU_MARGIN = 0.12f
    /** Écart de largeur maximal (|ln étirement|) : une police stylisée qu'il faut étirer de plus de 20 % ne ressemble pas au lettrage. */
    const val MAX_GAP = 0.20f
    /** Nombre minimal de lettres lues dans le groupe (un mot de deux lettres ne dit rien du lettrage). */
    const val MIN_LETTERS = 10
    /** Part minimale (en poids) de blocs sur fond uni dans le groupe. */
    const val FLAT_SHARE = 0.6f

    /** Polices stylisées que le cas B peut choisir : les classes distinctives et Knewave (brush) ; le reste de [FontLibrary.styled] est réservé au choix manuel (et au cas A). */
    val candidates: List<FontLibrary.Spec> by lazy { FontLibrary.styled.filter { it.cls in setOf("gothic", "horror", "script") || it.id == "knewave" } }

    private val SYSTEM_HEAD = Regex("^\\s*[\\[【][^\\]\\[】]{2,}[\\]】]")

    /** Vrai si [source] commence par un titre entre crochets (« [Quête] ») : la marque des fenêtres système des webtoons. */
    fun bracketed(source: String): Boolean = SYSTEM_HEAD.containsMatchIn(source) && source.count { it.isLetter() } >= 4

    /** Vrai pour une fenêtre système : texte entre crochets, clair sur fond sombre. */
    fun isSystemWindow(h: Hint): Boolean = h.lightOnDark && bracketed(h.source)

    /** Mesure les candidates stylisées d'un groupe de blocs, face à la meilleure police ordinaire [d] qu'ils ont reçue ; null si rien n'est mesurable. */
    fun evaluate(ms: List<StyleMatcher.Block>, d: StyleMatcher.Decision, renderer: GlyphRenderer, candidates: List<FontLibrary.Spec> = StyledFonts.candidates): Verdict? {
        if (ms.isEmpty() || d.ranking.isEmpty()) return null
        val refIou = d.ranking.maxOf { it.iou }
        val refScore = d.ranking.maxOf { StyleMatcher.IOU_W * it.iou - StyleMatcher.WIDTH_W * it.widthGap }
        var flatW = 0.0; var allW = 0.0
        for (b in ms) { allW += b.weight; if (b.regime == StyleMatcher.Regime.FLAT) flatW += b.weight }
        val letters = ms.sumOf { b -> b.lines.sumOf { l -> l.text.count { it.isLetter() } } }
        val cands = ArrayList<Cand>()
        for (sp in candidates) {
            var si = 0.0; var sg = 0.0; var wt = 0.0
            for (b in ms) {
                val used = b.lines.sortedByDescending { it.w.toLong() * it.h }.take(StyleMatcher.SHAPE_LINES)
                var li = 0f; var lg = 0f; var cnt = 0
                for (l in used) {
                    val gl = renderer.render(l.text, sp.id, 0f, 0f, l.w, l.h) ?: continue
                    var inter = 0; var uni = 0
                    for (q in l.mask.indices) { val a = l.mask[q]; val c = gl.mask[q]; if (a && c) inter++; if (a || c) uni++ }
                    li += if (uni == 0) 0f else inter.toFloat() / uni
                    lg += abs(ln(max(gl.stretch, 1e-3f))); cnt++
                }
                if (cnt == 0) continue
                val w = b.weight * b.reliability
                si += w * li / cnt; sg += w * lg / cnt; wt += w
            }
            if (wt > 0.0) cands.add(Cand(sp.id, (si / wt).toFloat(), (sg / wt).toFloat()))
        }
        if (cands.isEmpty()) return null
        return Verdict(refIou, refScore, cands.sortedByDescending { it.score }, letters, allW > 0.0 && flatW / allW >= FLAT_SHARE)
    }

    /** Vrai si la meilleure candidate du verdict remplit toutes les conditions du cas B. */
    fun passes(v: Verdict): Boolean {
        val best = v.cands.firstOrNull() ?: return false
        return v.flat && v.letters >= MIN_LETTERS && v.refIou < REF_MAX && best.iou >= MIN_IOU && best.iou - v.refIou >= IOU_MARGIN && best.gap <= MAX_GAP &&
            best.score - v.refScore >= StyleMatcher.IOU_W * IOU_MARGIN
    }

    /**
     * Police stylisée de chaque bloc (null : la police ordinaire reste). [blocks] et [decisions] sont ceux de [StyleMatcher.describe] et [StyleMatcher.decide] (même ordre) ; [hints] décrit
     * chaque bloc ; [renderer] null (pas de dessin possible) : seul le cas A s'applique.
     */
    fun refine(blocks: List<StyleMatcher.Block?>, decisions: List<StyleMatcher.Decision?>, hints: List<Hint?>, renderer: GlyphRenderer?): List<Pick?> {
        val out = arrayOfNulls<Pick>(blocks.size)
        // un groupe = les blocs qui ont reçu la même décision ordinaire (même lettrage, même police)
        val groups = LinkedHashMap<StyleMatcher.Decision, ArrayList<Int>>()
        for (i in blocks.indices) {
            val d = decisions.getOrNull(i) ?: continue
            if (blocks[i] == null || d.ranking.isEmpty()) continue
            groups.getOrPut(d) { ArrayList() }.add(i)
        }
        for ((d, members) in groups) {
            val hs = members.map { hints.getOrNull(it) }
            // A. fenêtre système : au moins un bloc entre crochets et tous les blocs du groupe clairs sur fond sombre
            if (hs.any { it != null && isSystemWindow(it) } && hs.all { it != null && it.lightOnDark }) {
                val pick = Pick(SYSTEM_FONT, "fenêtre système")
                for (i in members) out[i] = pick
                continue
            }
            // B. lettrage que aucune police ordinaire ne copie
            if (renderer == null) continue
            val v = evaluate(members.map { blocks[it]!! }, d, renderer) ?: continue
            if (!passes(v)) continue
            val best = v.cands.first()
            val pick = Pick(best.id, "lettrage stylisé · recouvrement %.2f (ordinaire %.2f) · largeur %.2f".format(java.util.Locale.ROOT, best.iou, v.refIou, best.gap))
            for (i in members) out[i] = pick
        }
        return out.toList()
    }
}
