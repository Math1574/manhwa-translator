package app.manhwa.core

import app.manhwa.core.TextErase.FontStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Entrée de la mise en page d'un bloc de texte traduit : le bloc, son plan d'effacement, la forme disponible (bulle, obstacles) et la police retenue.
 * Pur noyau : la mise en page se teste sans téléphone (voir BlockLayout).
 */
class BlockPrep(
    val block: TranslatedBlock,
    val plan: Erasure.Plan?,       // null si l'image n'a pas pu être décodée
    val style: FontStyle,
    val text: String,              // traduction avec typographie
    val bubble: RectF? = null,     // bulle détectée ET confirmée par son contour ; null = texte hors bulle
    val obstacles: List<RectF> = emptyList(),   // emprises des textes voisins (le texte hors bulle ne doit pas les recouvrir)
    val neighborBubbles: List<RectF> = emptyList(),   // bulles voisines confirmées : un texte hors bulle ne doit pas recouvrir leur contour
    val hardObstacles: List<RectF> = emptyList(),     // texte déjà placé d'autres blocs : toujours évité
    val bubbleRect: Boolean = false,                  // la bulle (confirmée, sinon la boîte du détecteur) est un cartouche rectangulaire ; sinon bulle ronde, bornée par son ellipse
    val container: RectF? = null,                     // boîte de bulle du détecteur NON confirmée par un contour : elle borne quand même la mise en page (jamais de texte libre dans une bulle)
    val font: String? = null,                         // police de la bibliothèque qui copie le lettrage d'origine (voir StyleMatcher) ; null : police par défaut
    val fontSlant: Float = 0f,                        // inclinaison mesurée du lettrage d'origine (0 = droit, environ −0,2 = italique)
    val fontInfo: String = "",                        // résumé de la décision de police, pour le journal
    val sizeHint: Float = 0f,                         // hauteur de lettre de référence (pixels de l'image d'origine) harmonisée avec les blocs de même taille d'origine ; 0 : mesure propre au bloc
    val paint: TextPaint.Spec? = null,                // couleur et contour de la traduction déduits du lettrage d'origine ; null : couleur extrême du plan (ancien comportement)
    val tilt: Float = 0f,                             // rotation du texte d'origine en degrés (> 0 : la ligne descend vers la droite), 0 = droit ; seulement pour un texte hors bulle
    val format: TextFormat = TextFormat(),            // mise en forme choisie à la main (alignement, lignes, interligne, casse, rotation) ; par défaut : mise en page automatique
    val look: TextLook = TextLook(),                  // apparence choisie à la main (couleur, contour, lueur) ; par défaut : celle mesurée sur l'original
) {
    fun withHard(h: List<RectF>) = BlockPrep(block, plan, style, text, bubble, obstacles, neighborBubbles, h, bubbleRect, container, font, fontSlant, fontInfo, sizeHint, paint, tilt, format, look)
    fun withFont(f: String?, slant: Float, info: String, size: Float) = BlockPrep(block, plan, style, text, bubble, obstacles, neighborBubbles, hardObstacles, bubbleRect, container, f, slant, info, size, paint, tilt, format, look)
}

object BlockLayout {
    /** Journal de la mise en page (banc d\'essai uniquement). */
    @JvmField var trace: ((String) -> Unit)? = null

    fun lineHeightOf(b: TranslatedBlock): Float =
        if (b.lineHeight > 0f) b.lineHeight else if (b.vertical) b.box.width else b.box.height

    fun prepare(b: TranslatedBlock, src: PixelSource?, s: Float, iw: Float, ih: Float, medianLine: Float, target: String, others: List<RectF>, blocksAll: List<TranslatedBlock>, verified: Map<Int, RectF?>, isRect: (RectF) -> Boolean, edit: RenderEdit? = null): BlockPrep {
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val bubble = verified[b.id]
        // zone de texte détectée : tout ce qu'elle contient est effacé, y compris une ligne que la lecture n'a pas trouvée (sinon il en reste un morceau)
        // zone dessinée par l'utilisateur : elle remplace la zone du détecteur, sans les garde-fous de taille
        val zone = edit?.region?.bounds ?: b.zone?.takeIf { z -> z.area <= 3.5f * union.area && z.area >= 0.8f * union.area && z.contains(union.centerX, union.centerY) }
        // bulles voisines (autres que celle qui contient ce bloc) : le texte libre qui les chevauche ne doit entamer ni leur intérieur ni leur contour.
        // « Contient ce bloc » se mesure sur l'ellipse pour une bulle ronde : le coin de sa boîte n'est pas dans la bulle.
        fun containsMe(nb: RectF): Boolean = if (isRect(nb)) nb.contains(union.centerX, union.centerY) else {
            val ex = (union.centerX - nb.centerX) / (nb.width / 2f * 1.06f); val ey = (union.centerY - nb.centerY) / (nb.height / 2f * 1.06f); ex * ex + ey * ey <= 1f
        }
        val neighborAll = blocksAll.filter { it !== b }.mapNotNull { verified[it.id] ?: it.bubble }.distinct()
        val neighborBubbles = neighborAll.filter { nb -> !containsMe(nb) || bubble == null }
        val outside = neighborAll.filter { nb -> !containsMe(nb) }
        val plan0 = if (src != null) try { Erasure.plan(src, s, lineBoxes, lineH, iw, ih, others, bubble, outside.filter { isRect(it) }, free = bubble == null, outlines = if (bubble == null) neighborBubbles else emptyList(), extra = listOfNotNull(zone), protectRound = outside.filter { !isRect(it) }, confine = if (bubble == null) b.bubble?.takeIf { it.area >= 1.6f * lineBoxes.reduce { x, y -> x.union(y) }.area } else null, confineRect = b.bubble?.let { isRect(it) } ?: false) } catch (e: Throwable) { null } else null
        // zone dessinée par l'utilisateur : rien n'est effacé en dehors
        val userRegion = edit?.region
        val plan1 = if (plan0 != null && userRegion != null) try { Erasure.restrict(plan0, userRegion.mask(plan0.x0, plan0.y0, plan0.w, plan0.h, s)) } catch (e: Throwable) { plan0 } else plan0
        // zone effacée en entier (carré d'effacement) : tout ce qu'elle contient est remplacé par ce qui l'entoure, texte détecté ou non ; sinon effacement précis des seules lettres
        val wiped = if (edit?.wipe == true && userRegion != null && src != null) try { Erasure.wipe(src, s, userRegion, plan0) } catch (e: Throwable) { null } else null
        val plan = if (wiped != null) wiped
            else if (plan1 != null && edit?.precise == true) try { Erasure.inkOnly(plan1, max(2, (lineH * s * 0.08f).toInt())) } catch (e: Throwable) { plan1 } else plan1
        val flat = plan?.mode == Erasure.Mode.FLAT
        val rectangular = flat && plan?.shape == null && union.width > 2.2f * union.height && lineBoxes.size <= 2
        // boîte de bulle non confirmée : retenue seulement si elle laisse de la marge autour du texte. Un texte libre que le détecteur a aussi marqué « bulle » a une boîte qui
        // épouse son texte : l'ellipse inscrite le rognerait et le réduirait
        val container0 = if (bubble == null) b.bubble?.takeIf { it.area >= 1.6f * union.area } else null
        // zone ovale tracée à la main (sans traduction à poser, rien à borner) : le texte reste dans l'ovale, comme dans une bulle ronde
        val ovalZone = if (bubble == null && container0 == null && edit?.region?.shape == ZoneShape.ELLIPSE && !edit.region.painted && b.translated.isNotBlank()) edit.region.bounds else null
        val container = container0 ?: ovalZone
        // rotation du texte d'origine (hors bulle seulement : un texte dans une bulle reste droit)
        val boxesPlan = if (plan != null) lineBoxes.map { r -> intArrayOf((r.left * s).toInt() - plan.x0, (r.top * s).toInt() - plan.y0, (r.right * s).toInt() - plan.x0, (r.bottom * s).toInt() - plan.y0) } else emptyList()
        val tiltDeg = if (plan != null && plan.ink != null && !b.vertical && bubble == null && container == null && plan.shape == null && edit?.manual != true) try { TextTilt.estimate(plan.letterInk ?: plan.ink, plan.w, plan.h, boxesPlan)?.degrees ?: 0f } catch (e: Throwable) { 0f } else 0f
        Erasure.trace?.invoke("angle du bloc ${b.id} : ${"%.1f".format(tiltDeg)}° (encre ${plan?.ink?.count { it } ?: 0} px, lettres seules ${plan?.letterInk?.count { it } ?: 0} px, cœur ${plan?.core?.count { it } ?: 0} px dont ${plan?.letterCore?.count { it } ?: 0} fermés, bulle=${bubble != null} cadre=${container != null} forme=${plan?.shape != null})")
        val drop = if (tiltDeg == 0f) 0f else lineBoxes.map { it.width }.sorted().let { it[it.size / 2] } * abs(Math.tan(Math.toRadians(tiltDeg.toDouble()))).toFloat()
        // hauteur de ligne pour le choix du style : mesurée sur les lettres (les boîtes d'une ligne inclinée sont gonflées de largeur × tan(angle))
        val inkH = lessTilt(measuredInkHeight(plan, b, s), drop)
        val lineFree = lineHeightFree(b, drop)
        val lineHStyle = if (inkH > 0f && !b.vertical && inkH >= 0.49f * lineH) min(lineFree, inkH / 0.8f) else lineFree
        val style = TextErase.chooseFont(b.source, lineHStyle, medianLine, flat, plan?.outlined ?: false, rectangular)
        // retours à la ligne : ceux tapés par l'utilisateur sont respectés, ceux d'une traduction automatique deviennent des espaces
        val fmt = (edit?.format ?: TextFormat()).sane()
        val cased = fmt.applyCase(if (edit?.breaks == true) b.translated else b.translated.replace('\n', ' '))
        val text = if (target == "fr") Typography.french(cased) else cased
        val obstacles = blocksAll.filter { it !== b && (it.id < EditApplier.MANUAL_ID_BASE || it.translated.isNotBlank()) }.map { o -> o.lineBoxes.ifEmpty { listOf(o.box) }.reduce { x, y -> x.union(y) } }
        // peinture de la traduction (couleur, contour) d'après les pixels d'origine
        val paint = if (plan != null && src != null && plan.ink != null) try { paintOf(plan, src, boxesPlan, if (inkH > 0f) inkH * s else lineH * s * 0.7f) } catch (e: Throwable) { null } else null
        return BlockPrep(b, plan, style, text, bubble, obstacles, neighborBubbles, emptyList(), (if (ovalZone != null) false else (bubble ?: container)?.let { isRect(it) } ?: false), container, paint = paint, tilt = tiltDeg, format = fmt, look = edit?.look ?: TextLook())
    }

    /** Peinture de la traduction d'après les pixels d'origine : couleur du corps des traits, contour coloré mesuré (voir TextPaint). [letterPx] : hauteur des lettres d'origine en pixels échantillonnés. */
    private fun paintOf(plan: Erasure.Plan, src: PixelSource, boxes: List<IntArray>, letterPx: Float): TextPaint.Spec? {
        val ink = plan.ink ?: return null
        val core = plan.core
        var nInk = 0; for (v in ink) if (v) nInk++
        var nCore = 0; if (core != null) for (v in core) if (v) nCore++
        val mask = if (core != null && nCore >= 0.4f * nInk) core else ink
        val px = src.pixels(plan.x0, plan.y0, plan.w, plan.h)
        val bgText = if (plan.fillMean != 0) plan.fillMean else plan.bg
        val dark = ColorMath.luminance(plan.textColor) < ColorMath.luminance(bgText)
        val lines = TextPaint.measure(px, plan.w, plan.h, mask, boxes, dark)
        return TextPaint.decide(plan.mode, plan.bg, plan.fillMean, plan.textColor, plan.outlined, plan.haloColor, plan.haloPx, letterPx, lines)
    }


    /**
     * Encre d'une ligne d'origine : le cœur des lettres (sans leur halo ni leur contour) quand il est cohérent avec l'encre complète, c'est-à-dire d'au moins 60 % de sa hauteur ;
     * sinon l'encre complète. Un cœur réduit à des fragments (couleur du texte mal estimée) donnerait des lettres de 3 ou 4 pixels, donc un texte minuscule.
     */
    private fun lineInkOf(plan: Erasure.Plan, s: Float, b: RectF, text: String): app.manhwa.core.LineInk? = StyleMatcher.lineInk(plan, s, b, text)

    /** Hauteur médiane des lettres d'origine (pixels de l'image d'origine), 0 si inconnue. */
    private fun measuredInkHeight(plan: Erasure.Plan?, b: TranslatedBlock, s: Float): Float {
        if (plan == null) return 0f
        val boxes = b.lineBoxes; val texts = b.lineTexts
        if (boxes.isEmpty() || texts.size != boxes.size) return 0f
        val hs = boxes.indices.mapNotNull { i -> lineInkOf(plan, s, boxes[i], texts[i])?.letterHeight }.sorted()
        return if (hs.isEmpty()) 0f else min(hs[hs.size / 2] / s, if (!b.vertical && b.lineHeight > 0f) b.lineHeight / 1.12f else Float.MAX_VALUE)
    }


    /** Encre de chaque ligne d'origine (recadrée au plus juste) à partir du plan d'effacement. */
    fun lineInks(p: BlockPrep, s: Float): List<app.manhwa.core.LineInk> {
        // cœur des lettres (sans leur halo ni leur contour) quand il est cohérent : la graisse et la taille ne sont pas faussées par la lueur
        val plan = p.plan ?: return emptyList()
        val boxes = p.block.lineBoxes; val texts = p.block.lineTexts
        if (boxes.isEmpty() || texts.size != boxes.size) return emptyList()
        return boxes.indices.mapNotNull { i -> lineInkOf(plan, s, boxes[i], texts[i]) }
    }

    /**
     * Hauteur de lettre de référence d'un bloc (pixels de l'image d'origine) : celle de l'encre, bornée par la hauteur de ligne. Une encre beaucoup plus basse que la ligne
     * (moins de 55 % de la hauteur de capitale permise) est une mesure ratée (fragments, contour, lueur) : on se fie alors à la hauteur de ligne. Sans cela, des lettres de 22 px
     * étaient traduites en 11 px.
     */
    private fun baseHeightOf(inkH: Float, capLimit: Float, lineH: Float): Float = when {
        inkH <= 0f -> lineH * 0.8f
        capLimit == Float.MAX_VALUE -> inkH
        inkH < 0.55f * capLimit -> 0.9f * capLimit
        else -> min(inkH, capLimit)
    }

    private fun inkHeightOf(p: BlockPrep, s: Float): Float {
        val inks = lineInks(p, s); val b = p.block
        return if (inks.isEmpty()) 0f else (if (b.vertical) inks.map { it.w } else inks.map { it.letterHeight }).sorted().let { it[it.size / 2] } / s
    }

    /**
     * Encre des seules lettres : les formes fermées de la région (le bord d'une case, une ligne du décor ou une marge qui traversent la zone du texte de part en part en sont retirés).
     * Texte penché posé à cheval sur le bord noir d'une case (journal du 23/11) : la longue ligne droite du bord, dans l'encre, l'emportait sur les lettres et l'angle mesuré était nul.
     * L'encre entière quand il ne reste presque rien (texte coupé par le bord de l'image).
     */
    internal fun lettersOnly(ink: BooleanArray, w: Int, h: Int): BooleanArray {
        val closed = TextErase.enclosedIn(ink, BooleanArray(w * h) { true }, w, h)
        var n = 0; for (v in closed) if (v) n++
        var all = 0; for (v in ink) if (v) all++
        return if (n >= TextTilt.MIN_INK && n >= all / 3) closed else ink
    }

    /** Hauteur (pixels de l'image) dont la rotation du texte gonfle la hauteur mesurée d'une ligne : largeur de ligne × tan(angle). 0 pour un texte droit. */
    internal fun tiltDrop(p: BlockPrep): Float {
        if (p.tilt == 0f) return 0f
        val w = p.block.lineBoxes.ifEmpty { listOf(p.block.box) }.map { it.width }.sorted().let { it[it.size / 2] }
        return w * abs(Math.tan(Math.toRadians(p.tilt.toDouble()))).toFloat()
    }

    /** [v] (hauteur mesurée sur des lignes penchées) moins le gonflement [drop] dû à la rotation, sans jamais passer sous 40 % de la mesure. */
    internal fun lessTilt(v: Float, drop: Float): Float = if (drop <= 0f || v <= 0f) v else max(0.4f * v, v - drop)

    /**
     * Hauteur de ligne d'origine sans le gonflement dû à la rotation. Une hauteur mesurée dans l'axe du texte (épaisseur de la ligne : nettement plus basse que la boîte) est déjà celle des
     * lettres : la corriger encore la réduisait deux fois (un mot penché, « Unbelievable. », était rendu à moins de la moitié de sa taille). Sans épaisseur mesurée, la hauteur est celle de
     * la boîte, gonflée, et la correction s'applique.
     */
    internal fun lineHeightFree(b: TranslatedBlock, drop: Float): Float {
        val h = lineHeightOf(b)
        if (drop <= 0f || h <= 0f || b.vertical) return h
        val boxes = b.lineBoxes.ifEmpty { listOf(b.box) }.map { it.height }.sorted()
        val boxH = boxes[boxes.size / 2]
        return if (b.lineHeight > 0f && b.lineHeight < 0.85f * boxH) h else lessTilt(h, drop)
    }

    /** Hauteurs de référence de tous les blocs, harmonisées : les blocs de même taille d'origine (écart de 18 % au plus) prennent la hauteur médiane de leur groupe. Texte vertical : 0 (mesure propre). */
    fun harmonizedHeights(preps: List<BlockPrep>, s: Float): FloatArray {
        val raw = FloatArray(preps.size) { i ->
            val b = preps[i].block
            if (b.vertical || preps[i].text.isBlank()) 0f else { val drop = tiltDrop(preps[i]); val lineH = lineHeightFree(b, drop); baseHeightOf(lessTilt(inkHeightOf(preps[i], s), drop), if (lineH > 0f) lineH / 1.12f else Float.MAX_VALUE, lineH) }
        }
        val out = raw.copyOf()
        val order = raw.indices.filter { raw[it] > 0f }.sortedBy { raw[it] }
        var k = 0
        while (k < order.size) {
            val first = raw[order[k]]; var e = k
            while (e + 1 < order.size && raw[order[e + 1]] <= first * 1.18f) e++
            if (e > k) { val med = (k..e).map { raw[order[it]] }.sorted().let { it[it.size / 2] }; for (q in k..e) out[order[q]] = med }
            k = e + 1
        }
        return out
    }


    /**
     * Corps visé par la traduction. Quand la mesure d'encre est fiable : la taille du texte d'origine (au plus 5 % de plus, et jamais plus haute que la ligne d'origine) ; c'est la bulle
     * ou la place libre qui la fait rétrécir, et non une borne tirée de l'emprise d'origine (qui rendait le texte plus petit que l'original alors qu'il restait de la place). Sinon : [emRaw].
     */
    internal fun targetEm(emInk: Float, lineCap: Float, inkTrusted: Boolean, emRaw: Float): Float =
        if (inkTrusted) min(emInk * 1.05f, lineCap).coerceAtLeast(5f) else emRaw

    /** Décalage des lignes les unes par rapport aux autres : écart entre le centre le plus à gauche et le plus à droite, rapporté à la ligne la plus large (0 = lignes alignées sur un même axe). */
    internal fun stagger(f: ShapeFit): Float {
        if (f.lines.size < 2) return 0f
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var wide = 1f
        for (ln in f.lines) { lo = min(lo, ln.cx); hi = max(hi, ln.cx); wide = max(wide, ln.width) }
        return (hi - lo) / wide
    }

    /** Valeur d'une mise en page pour départager deux candidats : la taille, réduite quand les lignes sont décalées les unes des autres (un texte en escalier se lit mal et ne ressemble pas à l'original). */
    internal fun quality(f: ShapeFit): Float = f.size * (1f - min(0.25f, 1.2f * stagger(f)))

    /** Mise en page sans aucune ligne : bloc dont on n'efface que la zone (carré d'effacement, sans traduction). */
    fun emptyResult(): Result = Result(ShapeFit(10f, 12f, emptyList(), true), false, false, 10f, 1.18f)

    /** Rectangle occupé par les lignes d'une mise en page (nul s'il n'y en a aucune). */
    internal fun placedOf(f: ShapeFit): RectF {
        if (f.lines.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (ln in f.lines) { l = min(l, ln.cx - ln.width / 2f); r = max(r, ln.cx + ln.width / 2f); t = min(t, ln.top); b = max(b, ln.top + f.lineStep) }
        return RectF(l, t, r, b)
    }

    /** Le texte placé ne sort jamais de l'image : chaque ligne est ramenée dans la page (marge de 2 px), le bloc entier aussi verticalement. Une ligne plus large que la page n'est pas touchée. */
    internal fun clampToImage(f: ShapeFit, iw: Float, ih: Float): ShapeFit {
        if (f.lines.isEmpty()) return f
        val m = 2f
        val r = placedOf(f)
        var dy = 0f
        if (r.height <= ih - 2 * m) { if (r.top < m) dy = m - r.top else if (r.bottom > ih - m) dy = ih - m - r.bottom }
        var moved = dy != 0f
        val lines = f.lines.map { ln ->
            var cx = ln.cx
            if (ln.width <= iw - 2 * m) { val lo = m + ln.width / 2f; val hi = iw - m - ln.width / 2f; cx = cx.coerceIn(lo, hi) }
            if (cx != ln.cx) moved = true
            PlacedLine(ln.text, cx, ln.top + dy, ln.width)
        }
        return if (!moved) f else ShapeFit(f.size, f.lineStep, lines, f.fits)
    }

    /** Résultat de la mise en page d'un bloc : lignes placées, corps cible, interligne, journal. */
    class Result(val fit: ShapeFit, val art: Boolean, val allCaps: Boolean, val em: Float, val stepRatio: Float, val lay: String = "", val tilt: Float = 0f) {
        /** Même mise en page, agrandie ou réduite du facteur [k] autour du centre du texte (taille choisie à la main). */
        fun scaled(k: Float): Result = Result(fit.scaled(k), art, allCaps, em * k, stepRatio, lay, tilt)

        /** Même mise en page, texte décalé de ([dx], [dy]) pixels de l'image. */
        fun shifted(dx: Float, dy: Float): Result = Result(ShapeFit(fit.size, fit.lineStep, fit.lines.map { PlacedLine(it.text, it.cx + dx, it.top + dy, it.width) }, fit.fits), art, allCaps, em, stepRatio, lay, tilt)

        /** Rectangle droit occupé par les lignes avant rotation ; son centre est le pivot de la rotation du bloc. */
        fun flat(): RectF = placedOf(fit)

        /** Rectangle englobant de [r] tourné de [tilt] degrés autour de ([cx], [cy]). */
        private fun rotated(r: RectF, cx: Float, cy: Float): RectF {
            if (tilt == 0f) return r
            val a = Math.toRadians(tilt.toDouble()); val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var rr = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for ((x, y) in listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)) {
                val dx = x - cx; val dy = y - cy
                val nx = cx + dx * c - dy * s; val ny = cy + dx * s + dy * c
                l = min(l, nx); rr = max(rr, nx); t = min(t, ny); b = max(b, ny)
            }
            return RectF(l, t, rr, b)
        }

        /** Rectangle de chaque ligne placée (tourné avec le bloc) : sert à ne pas recouvrir le texte d'un autre bloc avec un aplat d'effacement. */
        fun lineRects(margin: Float): List<RectF> {
            val f = flat()
            return fit.lines.map { ln -> rotated(RectF(ln.cx - ln.width / 2f, ln.top, ln.cx + ln.width / 2f, ln.top + fit.lineStep), f.centerX, f.centerY).inflate(margin, margin) }
        }

        /** Rectangle occupé par le texte placé (lignes, tourné avec le bloc) : sert à détecter les collisions entre blocs. */
        fun placed(margin: Float): RectF {
            val f = flat()
            return rotated(f, f.centerX, f.centerY).inflate(margin, margin)
        }
    }

    /**
     * Mise en page d'un bloc : corps et emplacement de chaque ligne de la traduction. [measurer] mesure le texte dans la police retenue (corps en pixels de l'image d'origine),
     * [cap] est la hauteur de capitale de cette police rapportée à son corps.
     */
    fun layout(p: BlockPrep, s: Float, iw: Float, ih: Float, userScale: Float, src: PixelSource?, measurer: TextMeasurer, cap: Float): Result? {
        val b = p.block; val plan = p.plan
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val drop = tiltDrop(p)                       // texte penché : les hauteurs mesurées sur les boîtes (et l'encre) sont gonflées de largeur × tan(angle)
        val lineH = lineHeightFree(b, drop)
        val allCaps = TextNormalizer.isAllCaps(b.source)

        // police assortie au lettrage d'origine ; corps cible : même hauteur de capitales que l'encre d'origine
        val inks = lineInks(p, s)
        // texte vertical : la taille d'un caractère est la LARGEUR de la colonne (et non sa hauteur, qui est la longueur du texte)
        val inkH = lessTilt(if (inks.isEmpty()) 0f else (if (b.vertical) inks.map { it.w } else inks.map { it.letterHeight }).sorted().let { it[it.size / 2] } / s, if (b.vertical) 0f else drop)       // pixels de l'image d'origine
        // la hauteur de ligne mesurée dans l'axe du texte borne la hauteur d'encre (lettres collées à un contour ou à une autre ligne)
        val capLimit = if (!b.vertical && lineH > 0f) lineH / 1.12f else Float.MAX_VALUE
        val baseH = if (p.sizeHint > 0f) p.sizeHint else baseHeightOf(inkH, capLimit, lineH)
        // mesure de l'encre fiable (pas ratée) : la traduction ne dépasse pas nettement la taille d'origine, même si elle est courte et tiendrait en plus grand
        val inkTrusted = inkH > 0f && !b.vertical && (capLimit == Float.MAX_VALUE || inkH >= 0.55f * capLimit)
        val emInk = (if (allCaps && !b.vertical) baseH / cap else if (b.vertical) baseH / 0.95f else baseH / 0.85f) * userScale.coerceIn(0.5f, 2f)

        // interligne d'origine : pas entre deux lignes / corps
        val tops = lineBoxes.map { it.top }.sorted()
        val pitch = if (tops.size >= 2) tops.zipWithNext { a, c -> c - a }.sorted().let { it[it.size / 2] } else 0f
        val stepRatio = if (pitch > 0f && !b.vertical) (pitch / (if (allCaps) baseH / cap else baseH / 0.85f)).coerceIn(1.0f, 1.6f) else 1.18f

        // forme disponible : intérieur de la bulle relevé sur l'image ; sinon ellipse inscrite dans la bulle confirmée ; sinon (texte hors bulle) l'emprise
        // d'origine progressivement agrandie, sans sortir de l'image ni recouvrir un voisin, jusqu'à ce que la traduction y tienne à une taille proche de l'original
        val art = plan?.mode == Erasure.Mode.ART
        // une bulle confirmée par son contour borne toujours le texte : ellipse inscrite (cartouche : rectangle), marge réduite si le texte d'origine la remplissait déjà.
        // Sans cela, une bulle presque pleine retombait sur la mise en page d'un texte libre, qui n'a aucune limite de contour.
        // boîte limite : bulle confirmée, sinon boîte du détecteur (fond non dessiné). Un texte placé dans une bulle y reste, que son contour ait été retrouvé ou non.
        val limitBox: RectF? = p.bubble ?: p.container?.takeIf { plan != null && plan.mode != Erasure.Mode.ART }
        val bubbleShape = limitBox?.let { c ->
            val k = if (c.width * 0.88f < union.width * 1.05f || c.height * 0.88f < union.height * 1.05f) 0.025f else 0.06f
            val inset = RectF(c.left + c.width * k, c.top + c.height * k, c.right - c.width * k, c.bottom - c.height * k)
            if (p.bubbleRect) RowShape.rect(inset.left, inset.top, inset.right, inset.bottom) else RowShape.ellipse(inset.left, inset.top, inset.right, inset.bottom)
        }
        // majuscules accentuées : l'accent d'une ligne ne doit pas toucher la ligne du dessus
        val accents = TextNormalizer.isAllCaps(p.text) && p.text.any { it in "ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇŒŸ" }
        val fmt: TextFormat? = p.format.takeUnless { it.isDefault }       // réglages manuels : null = mise en page automatique, exactement comme avant
        val step = (if (accents) max(stepRatio, 1.14f) else stepRatio) * (fmt?.spacing ?: 1f)
        val centerY = (union.top + union.bottom) / 2f
        // Taille : la traduction doit ressembler au texte d'origine par la taille et l'emplacement. Deux estimations indépendantes : la hauteur des lettres mesurée sur l'encre
        // (fragile : contours, texte incliné, graisse) et la taille à laquelle la traduction TIENT dans l'emprise d'origine (un peu agrandie). On retient la première, bornée
        // entre 85 % et 110 % de la seconde, et jamais plus haute que la ligne d'origine : plus d'agrandissement de la zone pour garder des lettres hautes, plus de texte minuscule.
        val origPadX = union.width * 0.05f + 4f; val origPadY = union.height * 0.08f + 4f
        val origRect = RowShape.rect(union.left - origPadX, union.top - origPadY, union.right + origPadX, union.bottom + origPadY)
        val marginInk = max(4f, emInk * 0.14f)
        val fpFit = if (b.vertical) null else ShapeLayout.fit(origRect, centerY, p.text, measurer, emInk * 3f, max(5f, emInk * 0.3f), step, marginInk)
        val sFp = if (fpFit != null && fpFit.fits && fpFit.lines.isNotEmpty()) fpFit.size else 0f
        val lineCap = 1.05f * max(max(lineH, pitch), 1f)
        val emRaw = if (sFp > 0f) min(min(max(emInk, 0.85f * sFp), 1.1f * sFp), lineCap).coerceAtLeast(5f) else emInk
        val fixedShape = plan?.shape ?: bubbleShape
        // garde-fou indépendant de la détection : forme relevée sur les pixels (premier trait sombre de chaque côté du texte), pour les fonds unis. Le texte n'est jamais
        // posé sur un contour ou un dessin, même si la bulle n'a pas été reconnue ou si le remplissage de la bulle a fui par un trou du contour.
        val guard: RowShape? = if (src != null && plan != null && plan.mode == Erasure.Mode.FLAT) try {
            val c = limitBox
            val own = lineBoxes + listOfNotNull(b.zone?.takeIf { z -> z.area <= 3.5f * union.area })
            val padX = union.width * 1.0f + 20f; val padY = union.height * 1.0f + 20f
            BarrierShape.build(
                src, s, plan.bg, plan.layer, plan.x0, plan.y0, plan.w, plan.h, own, union.centerX,
                if (c != null) min(c.top, union.top - 8f) else union.top - padY, if (c != null) max(c.bottom, union.bottom + 8f) else union.bottom + padY,
                if (c != null) min(c.left, union.left - 8f) else union.left - padX, if (c != null) max(c.right, union.right + 8f) else union.right + padX, iw, ih,
            )
        } catch (e: Throwable) { null } else null
        // Corps visé. La place disponible est connue (bulle, forme relevée, limites relevées sur les pixels) : la traduction vise la taille du texte d'origine et c'est cette place qui la fait
        // rétrécir. Texte libre sur un dessin : rien ne dit où s'arrête le décor (bord d'un panneau, visage), on reste dans l'emprise d'origine comme avant.
        val roomKnown = fixedShape != null || guard != null
        val em = if (roomKnown) targetEm(emInk, lineCap, inkTrusted, emRaw) else emRaw.let { r -> if (inkTrusted) min(r, 1.15f * emInk).coerceAtLeast(5f) else r }
        val margin = max(4f, em * 0.14f)
        val anchorX = union.centerX            // axe du texte d'origine : la traduction s'y centre tant que la forme lui laisse la place
        // bulle ronde confirmée : jamais au-delà de son ellipse (un contour ouvert laisse la forme relevée déborder sur la page)
        val ellipseLimit: RowShape? = limitBox?.takeIf { !p.bubbleRect }?.let { c -> RowShape.ellipse(c.left + c.width * 0.015f, c.top + c.height * 0.015f, c.right - c.width * 0.015f, c.bottom - c.height * 0.015f) }
        var freeInfo = ""   // journal : quelle zone libre a été retenue pour un texte hors bulle (niveau d'agrandissement, forme, bulles voisines proches)
        val fit0: ShapeFit = if (fixedShape != null) {
            // l'emprise d'origine reste toujours disponible dans la bulle (le texte d'origine y tenait), bornée par l'ellipse un peu élargie : la forme relevée, parfois trop
            // étroite (bords en dégradé, lueur), ne doit pas imposer un texte plus petit que l'original
            val origInBubble = limitBox?.takeIf { !p.bubbleRect }?.let { c -> origRect.intersect(RowShape.ellipse(c.left - c.width * 0.06f, c.top - c.height * 0.06f, c.right + c.width * 0.06f, c.bottom + c.height * 0.06f)) } ?: origRect
            val widened = fixedShape.union(origInBubble)
            val base = ShapeLayout.fit(widened, centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin, anchorX, fmt)
            var constrained: RowShape? = widened
            if (ellipseLimit != null) constrained = constrained?.intersect(ellipseLimit)
            if (guard != null) constrained = constrained?.intersect(guard)
            val alt = if (constrained != null && constrained !== widened) ShapeLayout.fit(constrained, centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin, anchorX, fmt) else null
            // on garde la mise en page contrainte si elle tient (ou si l'autre ne tenait pas) sans rétrécir le texte de plus de moitié
            if (alt != null && alt.lines.isNotEmpty() && (alt.fits || !base.fits) && alt.size >= 0.55f * base.size) alt else base
        } else {
            var best: ShapeFit? = null
            // un texte posé contre une bulle voisine ne doit pas aller se loger loin de son emplacement d'origine (au-dessous de la bulle, de l'autre côté de la page) :
            // il resterait à côté du mauvais dessin. Un bloc compact autour de la bulle n'est retenu que s'il recouvre encore l'emplacement d'origine.
            for (g in floatArrayOf(if (art) 0.08f else 0.05f, 0.16f, 0.3f, 0.55f)) {
                val area = LayoutArea.expand(union, g, p.obstacles, iw, ih, p.hardObstacles)
                val near = p.neighborBubbles.filter { nb -> nb.left < area.right && nb.right > area.left && nb.top < area.bottom && nb.bottom > area.top }
                // contre une bulle voisine : lignes réparties autour de l'ellipse (forme par rangées) OU bloc compact au-dessous / à côté de la bulle
                val shapes = ArrayList<RowShape>()
                if (near.isEmpty()) shapes.add(RowShape.rect(area.left, area.top, area.right, area.bottom))
                else {
                    val gap = max(6f, em * 0.2f)
                    // l'emprise d'origine reste toujours disponible : le texte d'origine y était, donc ce n'est pas l'intérieur de la bulle voisine (sa boîte, qui englobe sa queue, ne l'est pas non plus)
                    shapes.add(RowShape.rectAvoiding(area.left, area.top, area.right, area.bottom, near, gap).union(origRect))
                    for (nb in near) for (r in LayoutArea.candidatesAround(area, nb, gap, union.height * 1.4f, iw, ih)) if (near.none { o -> o !== nb && o.left < r.right && o.right > r.left && o.top < r.bottom && o.bottom > r.top }) shapes.add(RowShape.rect(r.left, r.top, r.right, r.bottom))
                }
                for ((si, areaShape0) in shapes.withIndex()) {
                    val areaShape = if (guard != null) (areaShape0.intersect(guard) ?: continue) else areaShape0
                    val sy = if (centerY in areaShape.top..areaShape.bottom) centerY else (areaShape.top + areaShape.bottom) / 2f
                    val cand = ShapeLayout.fit(areaShape, sy, p.text, measurer, em, max(5f, em * 0.5f), step, margin, anchorX, fmt)
                    trace?.invoke("bloc ${b.id} g=$g forme#$si zone=[%.0f,%.0f,%.0f,%.0f] près=${near.size} sy=%.0f -> taille=%.0f tient=${cand.fits} lignes=${cand.lines.size} centre=(%.0f,%.0f)".format(areaShape.top.let { area.left }, area.top, area.right, area.bottom, sy, cand.size, if (cand.lines.isEmpty()) 0f else placedOf(cand).centerX, if (cand.lines.isEmpty()) 0f else placedOf(cand).centerY))
                    if (cand.lines.isEmpty()) continue
                    // formes « autour de la bulle » (les suivantes de la liste) : le bloc doit encore recouvrir l'emplacement d'origine (centres éloignés de moins de 40 % de la somme des dimensions)
                    if (near.isNotEmpty() && si > 0) { val pr = placedOf(cand); if (abs(pr.centerY - centerY) > 0.4f * (pr.height + union.height) || abs(pr.centerX - union.centerX) > 0.4f * (pr.width + union.width)) continue }
                    val cur = best
                    if (cur == null || (cand.fits && !cur.fits) || (cand.fits == cur.fits && quality(cand) > quality(cur) * 1.02f)) { best = cand; freeInfo = "g=%.2f forme#%d près=%d".format(g, si, near.size) }
                }
                // rien ne tient entre la bulle voisine et la place d'origine : on reste en place, sur le rectangle simple (le chevauchement léger d'une boîte de bulle vaut mieux qu'un texte relogé ailleurs)
                if (near.isNotEmpty() && best?.fits != true) {
                    val sy = if (centerY in area.top..area.bottom) centerY else (area.top + area.bottom) / 2f
                    val shp = RowShape.rect(area.left, area.top, area.right, area.bottom).let { r -> if (guard != null) (r.intersect(guard) ?: r) else r }
                    val cand = ShapeLayout.fit(shp, sy, p.text, measurer, em, max(5f, em * 0.5f), step, margin, anchorX, fmt)
                    trace?.invoke("bloc ${b.id} g=$g repli rect -> taille=%.0f tient=${cand.fits}".format(cand.size))
                    if (cand.lines.isNotEmpty() && cand.fits) { best = cand; freeInfo = "g=%.2f repli près=%d".format(g, near.size) }
                }
                val bestNow = best
                // premier niveau : on s'arrête dès que le texte atteint 90 % du corps visé ; ensuite 72 % (comme avant) : la zone ne s'agrandit que si le texte reste trop petit
                if (bestNow != null && bestNow.fits && bestNow.size >= em * (if (g <= 0.081f) 0.9f else 0.72f)) break
            }
            best ?: ShapeLayout.fit(RowShape.rect(union.left, union.top, union.right, union.bottom), centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin, null, fmt)
        }
        if (fit0.lines.isEmpty()) return null
        val fit = clampToImage(fit0, iw, ih)
        val lay = "encre=%.0f emprise=%.0f ligne=%.0f pas=%.0f cible=%.0f taille=%.0f forme=%s".format(emInk, sFp, lineH, pitch, em, fit.size, if (fixedShape == null) "libre" else if (plan?.shape != null) "relevée" else "ellipse") +
            (if (fixedShape == null) " zone[$freeInfo obst=${p.obstacles.size} voisines=${p.neighborBubbles.size} durs=${p.hardObstacles.size}]" else "")
        return Result(fit, art, allCaps, em, stepRatio, lay, p.tilt + (fmt?.angle ?: 0f))
    }

    /**
     * Collisions : un texte hors bulle (forme libre) dont le texte placé recouvre celui d'un autre bloc est replacé en évitant le texte réellement placé des autres (leur traduction est
     * plus longue que l'original, que chaque bloc évitait seul). [layoutFn] remet un bloc en page à l'échelle donnée (1 = taille normale) ; [pinned] : position choisie par l'utilisateur.
     *
     * On cherche d'abord à rester en place : le texte rétrécit un peu (jusqu'à 60 %) plutôt que de partir loin de son emplacement d'origine. Un texte replacé loin tombe sur un autre dessin
     * (« beaucoup plus bas que l'original ») ; le chevauchement tolérable est préférable au déplacement lointain.
     */
    fun resolveCollisions(preps: List<BlockPrep>, laids: MutableList<Result?>, pinned: (BlockPrep) -> Boolean, layoutFn: (BlockPrep, Float) -> Result?) {
        for (round in 0 until 2) {
            var changed = false
            for (i in preps.indices) {
                val li = laids[i] ?: continue
                val pi = preps[i]
                if (pi.bubble != null || pi.container != null || pi.plan?.shape != null) continue            // les bulles gardent leur place : ce sont les textes libres qui s'adaptent
                if (pinned(pi)) continue                                                                       // position choisie par l'utilisateur : on n'y touche pas
                val mine = li.placed(2f)
                val others = preps.indices.filter { it != i }.mapNotNull { j -> laids[j]?.placed(4f) }
                if (others.none { o -> (mine.intersection(o)?.area ?: 0f) > 0.04f * mine.area }) continue
                val u = pi.block.lineBoxes.ifEmpty { listOf(pi.block.box) }.reduce { a, c -> a.union(c) }
                fun overlaps(r: Result): Boolean { val m = r.placed(2f); return others.any { o -> (m.intersection(o)?.area ?: 0f) > 0.04f * m.area } }
                fun moved(r: Result): Float { val a = r.placed(0f); return Math.hypot((a.centerX - u.centerX).toDouble(), (a.centerY - u.centerY).toDouble()).toFloat() }
                // ordre de lecture : un bloc replacé de l'autre côté d'un voisin inverserait les phrases (« …COMPRENDRE LE BONHEUR » avant « J'AVAIS UNE MAISON… »).
                val crosses = { r: Result -> val b0 = li.placed(0f); val a0 = r.placed(0f); preps.indices.any { j -> j != i && laids[j]?.let { o -> ReadingOrder.crossesNeighbour(b0, a0, o.placed(0f)) } == true } }
                val maxMove = max(0.7f * u.height, 2.5f * li.fit.size)
                var chosen: Result? = null
                // 1) en place : corps normal puis de plus en plus petit, en évitant le texte déjà placé des voisins, sans s'éloigner ni passer de l'autre côté d'un voisin
                for (k in floatArrayOf(1f, 0.92f, 0.84f, 0.76f, 0.68f, 0.6f)) {
                    val cand = layoutFn(pi.withHard(others), k) ?: continue
                    if (!overlaps(cand) && moved(cand) <= maxMove && !crosses(cand)) { chosen = cand; break }
                }
                // 2) à défaut, comportement précédent : mise en page avec obstacles durs ; si elle inverse l'ordre de lecture, réduction de la taille sans obstacles
                if (chosen == null) {
                    var relaid = layoutFn(pi.withHard(others), 1f) ?: continue
                    if (crosses(relaid)) {
                        var alt: Result? = null
                        for (k in listOf(0.9f, 0.8f, 0.7f)) {
                            val cand = layoutFn(pi, k) ?: continue
                            if (!overlaps(cand) && !crosses(cand)) { alt = cand; break }
                        }
                        relaid = alt ?: continue   // aucune taille ne convient sans inverser l'ordre : on garde la première mise en page
                    }
                    // jamais un déplacement lointain : le chevauchement reste préférable à un texte posé sur un autre dessin
                    if (moved(relaid) > 2f * maxMove) continue
                    chosen = relaid
                }
                laids[i] = chosen; changed = true
            }
            if (!changed) break
        }
        separateStacked(preps, laids, pinned, layoutFn)
    }

    /** Bloc rangé dans une bulle, un cadre ou une forme relevée : la résolution des collisions ci-dessus n'y touche pas (ils gardent leur place). */
    private fun bubbleLike(p: BlockPrep): Boolean = p.bubble != null || p.container != null || p.plan?.shape != null

    /**
     * Plus fort recouvrement entre une ligne de [a] et une ligne de [b] (lignes de texte placées, tournées avec leur bloc), rapporté à la plus petite des deux lignes : 0 quand aucune ligne
     * n'en touche une autre, 1 quand l'une est entièrement sur l'autre. La hauteur d'une ligne comprend son interligne : un recouvrement de quelques pour cent n'est pas encore du texte sur du texte.
     */
    internal fun textOverlap(a: Result, b: Result): Float {
        val ra = a.lineRects(0f); val rb = b.lineRects(0f)
        var worst = 0f
        for (x in ra) for (y in rb) {
            val i = x.intersection(y) ?: continue
            worst = max(worst, i.area / max(1f, min(x.area, y.area)))
        }
        return worst
    }

    /** Recouvrement à partir duquel deux textes de bulle sont séparés, et recouvrement toléré une fois séparés (voir [textOverlap]). */
    internal const val STACK_TRIGGER = 0.25f
    internal const val STACK_TARGET = 0.08f

    /**
     * Deux blocs de bulle ou de cadre dont les lignes de texte se recouvrent : le titre et le paragraphe d'un même cadre (la traduction est plus longue que l'original et chaque bloc est mis en page
     * seul dans la même forme : le paragraphe grandit vers le titre, qui lui-même passe sur deux lignes). Les deux restent dans leur bulle : on réduit leur corps (jusqu'à 80 %) et on garde la
     * combinaison qui sépare les textes en réduisant le moins possible, la plus petite des deux réductions d'abord. Un texte un peu plus petit vaut mieux que deux textes l'un sur l'autre.
     * Sans combinaison qui convienne, ou pour un bloc dont la position a été choisie, rien ne change.
     */
    internal fun separateStacked(preps: List<BlockPrep>, laids: MutableList<Result?>, pinned: (BlockPrep) -> Boolean, layoutFn: (BlockPrep, Float) -> Result?) {
        val steps = floatArrayOf(1f, 0.95f, 0.9f, 0.85f, 0.8f)
        for (round in 0 until 2) {
            var changed = false
            for (i in preps.indices) for (j in i + 1 until preps.size) {
                val li = laids[i] ?: continue; val lj = laids[j] ?: continue
                val pi = preps[i]; val pj = preps[j]
                if (!bubbleLike(pi) || !bubbleLike(pj) || pinned(pi) || pinned(pj)) continue
                if (li.placed(0f).intersection(lj.placed(0f)) == null || textOverlap(li, lj) < STACK_TRIGGER) continue
                val ci = steps.map { k -> if (k == 1f) li else layoutFn(pi, k) }
                val cj = steps.map { k -> if (k == 1f) lj else layoutFn(pj, k) }
                var best: Triple<Result, Result, Float>? = null
                for (a in steps.indices) for (b in steps.indices) {
                    if (a == 0 && b == 0) continue
                    val ri = ci[a] ?: continue; val rj = cj[b] ?: continue
                    if (textOverlap(ri, rj) > STACK_TARGET) continue
                    val score = min(steps[a], steps[b]) * 10f + steps[a] * steps[b]
                    if (best == null || score > best.third) best = Triple(ri, rj, score)
                }
                val chosen = best ?: continue
                laids[i] = chosen.first; laids[j] = chosen.second; changed = true
            }
            if (!changed) break
        }
    }
}
