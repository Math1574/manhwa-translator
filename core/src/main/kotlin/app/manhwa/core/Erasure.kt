package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Accès aux pixels d'une image (adaptateur Bitmap sur Android, BufferedImage dans les tests). */
interface PixelSource {
    val width: Int
    val height: Int
    /** Pixels ARGB du rectangle (x, y, w, h), ligne par ligne. */
    fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray
}

/**
 * Plan d'effacement du texte d'origine d'un bloc :
 * - FLAT : texte sur fond uni (bulle, encadré) → les lettres sont remplacées par la couleur du fond, le contour n'est jamais touché ;
 * - ART : texte sur dessin → la zone du texte est reconstruite à partir de ses abords (bords adoucis).
 */
object Erasure {
    /** Diagnostic (bancs d'essai) : reçoit les mesures qui décident entre aplat et effacement des lettres. Nul en production. */
    @JvmField var trace: ((String) -> Unit)? = null
    /** Protège les traits fixes (contour de bulle, trait du décor) que l'effacement d'un texte sur dessin frôlerait ; sert aux tests, qui le coupent pour montrer le défaut. */
    @JvmField var protectStructure = true
    /** Nettoyage du halo clair autour des lettres effacées sur fond blanc (mode ART). Désactivable pour comparer, dans les bancs d'essai. */
    @JvmField var haloClean: Boolean = true
    /** Bancs d'essai seulement : force le mode ART (même sur un fond uni) pour mesurer ses restes. */
    @JvmField var forceArt: Boolean = false
    /** Bancs d'essai seulement : force l'aplat (même si la mesure le refuse) pour voir ce qu'il donnerait. */
    @JvmField var forceFlat: Boolean = false

    enum class Mode { FLAT, SMOOTH, ART }

    class Plan(
        val mode: Mode,
        /** Région analysée, en pixels échantillonnés de l'image. */
        val x0: Int, val y0: Int, val w: Int, val h: Int,
        /** Pixels à remplacer (w*h). */
        val mask: BooleanArray,
        /** Couleur de remplacement (ARGB) de chaque pixel de la région, alpha compris (bords adoucis en mode ART). */
        val layer: IntArray,
        val bg: Int,
        val textColor: Int,
        val outlined: Boolean,
        /** Rectangle inscrit dans la bulle (pixels de l'image d'origine), si la forme a pu être détectée. */
        val bubbleArea: RectF?,
        /** Forme libre de l'intérieur de la bulle (pixels de l'image d'origine), pour la mise en page de la traduction. */
        val shape: RowShape? = null,
        /** Épaisseur du trait des lettres d'origine, en pixels échantillonnés (0 si inconnue). */
        val strokePx: Float = 0f,
        /** Encre d'origine (pixels de texte non dilatés, w*h) : sert à choisir une police qui ressemble au lettrage. */
        val ink: BooleanArray? = null,
        /** Couleur moyenne réellement obtenue après effacement (pixels remplacés), RGB opaque ; 0 si rien n'est remplacé. */
        val fillMean: Int = 0,
        /** Couleur de la lueur entourant le lettrage (RGB opaque), 0 s'il n'y en a pas : la traduction est redessinée avec la même lueur. */
        val glow: Int = 0,
        /** Cœur des lettres (remplissage seul, sans halo ni contour, w*h) : sert à mesurer la graisse et la taille du lettrage d'origine ; null si inconnu. */
        val core: BooleanArray? = null,
        /** Couleur du remplissage des lettres (RVB opaque, 0 si inconnue) et de leur halo (0 s'il n'y en a pas) : la traduction est dessinée avec les mêmes couleurs. */
        val letterColor: Int = 0, val haloColor: Int = 0,
        /** Épaisseur du halo en pixels échantillonnés (0 si aucun). */
        val haloPx: Float = 0f,
    )

    /** Couleur moyenne (RGB opaque) des pixels effectivement remplacés ; 0 s'il n'y en a pas. */
    private fun meanFill(layer: IntArray): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (c in layer) if ((c ushr 24) >= 128) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++ }
        return if (n == 0L) 0 else (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /**
     * Restreint l'effacement d'un plan à une zone choisie à la main : [keep] (même taille que la région du plan) dit quels pixels peuvent être remplacés ; rien n'est remplacé en dehors,
     * ce que le plan avait décidé à l'intérieur reste inchangé.
     */
    fun restrict(p: Plan, keep: BooleanArray): Plan {
        val n = p.w * p.h
        if (keep.size != n) return p
        val mask = BooleanArray(n) { p.mask[it] && keep[it] }
        val layer = IntArray(n) { if (keep[it]) p.layer[it] else 0 }
        return Plan(p.mode, p.x0, p.y0, p.w, p.h, mask, layer, p.bg, p.textColor, p.outlined, p.bubbleArea, p.shape, p.strokePx, p.ink, meanFill(layer), p.glow, p.core, p.letterColor, p.haloColor, p.haloPx)
    }

    /**
     * Effacement précis : seules les lettres sont remplacées (encre d'origine élargie de [grow] pixels échantillonnés, pour les bords adoucis), le reste de la zone de texte reste intact.
     * Plus de grand rectangle clair derrière la traduction ; si l'encre est inconnue ou vide, le plan est inchangé.
     */
    fun inkOnly(p: Plan, grow: Int): Plan {
        val ink = p.ink ?: return p
        val n = p.w * p.h
        if (ink.size != n || ink.none { it }) return p
        val d = TextErase.dilate(ink, p.w, p.h, max(1, grow))
        val mask = BooleanArray(n) { p.mask[it] && d[it] }
        val layer = IntArray(n) { if (mask[it]) p.layer[it] else 0 }
        return Plan(p.mode, p.x0, p.y0, p.w, p.h, mask, layer, p.bg, p.textColor, p.outlined, p.bubbleArea, p.shape, p.strokePx, p.ink, meanFill(layer), p.glow, p.core, p.letterColor, p.haloColor, p.haloPx)
    }

    /** @param exclude lignes de texte des AUTRES blocs (pixels de l'image) : un bloc n'efface jamais ce qui appartient à un voisin. */
    /** Cœur des lettres du plan : masque, couleur, couleur du halo, épaisseur du halo (zéros si l'encre est vide ou trop pauvre). */
    private class CoreInfo(val mask: BooleanArray?, val color: Int, val halo: Int, val haloPx: Float)
    private fun coreOf(px: IntArray, w: Int, h: Int, ink: BooleanArray?, bg: Int, textColor: Int): CoreInfo {
        val lc = if (ink == null) null else try { TextErase.letterCore(px, w, h, ink, bg, textColor) } catch (e: Throwable) { null }
        // lettrage creux (fin contour autour d'un remplissage de couleur) : le remplissage fait partie des lettres, sans quoi la graisse mesurée est celle du seul contour
        val letters = (lc?.mask ?: ink)?.let { m ->
            try {
                val sol = TextErase.solidify(px, w, h, m, bg)
                // des contours qui se touchent d'une lettre à l'autre referment les espaces et les contreformes : le « remplissage » couvre alors presque toute la ligne, ce n'est plus un lettrage
                if (TextErase.boxFill(sol, w, h) > 0.70f) m else sol
            } catch (e: Throwable) { m }
        }
        return if (lc == null) CoreInfo(letters, 0, 0, 0f) else CoreInfo(letters, lc.color and 0xFFFFFF, lc.haloColor and 0xFFFFFF, lc.haloPx)
    }

    fun plan(src: PixelSource, s: Float, lineBoxes: List<RectF>, lineH: Float, imageW: Float, imageH: Float, exclude: List<RectF> = emptyList(), container: RectF? = null, protect: List<RectF> = emptyList(), free: Boolean = false, outlines: List<RectF> = emptyList(), extra: List<RectF> = emptyList(), protectRound: List<RectF> = emptyList(), confine: RectF? = null, confineRect: Boolean = false): Plan? {
        val p = planUnclipped(src, s, lineBoxes, lineH, imageW, imageH, container = container, protect = protect, free = free, outlines = outlines, extra = extra, protectRound = protectRound, confine = confine, confineRect = confineRect, exclude = exclude) ?: return null
        // contour d'une bulle voisine : les traits qui le forment ne sont jamais remplacés, quelle que soit la méthode d'effacement employée
        // seulement pour un texte sombre : un trait noir de texte qui chevauche un contour noir y est invisible. Un texte clair cerné de noir laisserait au contraire
        // des fragments de cerne visibles : son effacement reste complet.
        val textLum = (((p.textColor shr 16) and 0xFF) * 77 + ((p.textColor shr 8) and 0xFF) * 150 + (p.textColor and 0xFF) * 29) shr 8
        if (outlines.isNotEmpty() && textLum < 110) {
            val px = src.pixels(p.x0, p.y0, p.w, p.h)
            val keep = outlineKeep(px, p.w, p.h, p.mask, p.x0, p.y0, s, outlines)
            for (i in keep.indices) if (keep[i]) { p.mask[i] = false; p.layer[i] = 0 }
        }
        // intérieur et contour d'une bulle voisine ronde : jamais effacés, sauf sous le texte propre de ce bloc (texte posé sur la bulle)
        if (protectRound.isNotEmpty()) {
            val own = TextErase.rectMask(p.w, p.h, lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - p.x0, (l.top * s).toInt() - p.y0, (l.right * s).toInt() - p.x0, (l.bottom * s).toInt() - p.y0) }, 3)
            for (pe in protectRound) {
                val ecx = pe.centerX * s - p.x0; val ecy = pe.centerY * s - p.y0; val erx = pe.width / 2f * s * 1.1f; val ery = pe.height / 2f * s * 1.1f
                for (y in 0 until p.h) for (x in 0 until p.w) {
                    val i = y * p.w + x
                    if (!(p.layer[i] != 0 || p.mask[i]) || own[i]) continue
                    val ex = (x + 0.5f - ecx) / erx; val ey = (y + 0.5f - ecy) / ery
                    if (ex * ex + ey * ey <= 1f) { p.layer[i] = 0; p.mask[i] = false }
                }
            }
        }
        if (exclude.isEmpty()) return p
        var changed = false
        for (ex in exclude) {
            val ex0 = ((ex.left * s).toInt() - p.x0 + 1).coerceIn(0, p.w); val ey0 = ((ex.top * s).toInt() - p.y0 + 1).coerceIn(0, p.h)
            val ex1 = ((ex.right * s).toInt() - p.x0 - 1).coerceIn(0, p.w); val ey1 = ((ex.bottom * s).toInt() - p.y0 - 1).coerceIn(0, p.h)
            for (y in ey0 until ey1) for (x in ex0 until ex1) { val i = y * p.w + x; if (p.layer[i] != 0 || p.mask[i]) { p.layer[i] = 0; p.mask[i] = false; changed = true } }
        }
        return p
    }


    /**
     * Texte hors bulle dont les abords sont unis : on cherche la plus petite zone (lignes de texte agrandies) dont le pourtour est d'une seule couleur
     * (lueur comprise : la zone grandit jusqu'à sortir du halo) et on la remplit entièrement de cette couleur. Plus de fragments de dégradés de couleur,
     * de contours ni de lueurs fantômes. Null si aucun pourtour n'est uni (dessin, trame) : le traitement des lettres seules prend le relais.
     */
    private fun planRing(src: PixelSource, s: Float, lineBoxes0: List<RectF>, lineH: Float, imageW: Float, imageH: Float, protect: List<RectF>, outlines: List<RectF> = emptyList(), extra: List<RectF> = emptyList(), protectRound: List<RectF> = emptyList(), confine: RectF? = null, confineRect: Boolean = false, exclude: List<RectF> = emptyList()): Plan? {
        val lineBoxes = lineBoxes0 + extra
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lhPx = max(8f, lineH * s)
        val pad = max(40f, lineH * 5f)
        val x0 = (max(0f, union.left - pad) * s).toInt().coerceIn(0, src.width - 1)
        val y0 = (max(0f, union.top - pad) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + pad) * s).toInt().coerceIn(x0 + 1, src.width)
        val y1 = (min(imageH, union.bottom + pad) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 8 || h < 8) return null
        val px = src.pixels(x0, y0, w, h)
        val rects = lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
        val prot = BooleanArray(w * h)
        for (pr in protect) {
            val ax0 = ((pr.left * s).toInt() - x0).coerceIn(0, w); val ax1 = ((pr.right * s).toInt() - x0).coerceIn(0, w)
            val ay0 = ((pr.top * s).toInt() - y0).coerceIn(0, h); val ay1 = ((pr.bottom * s).toInt() - y0).coerceIn(0, h)
            for (yy in ay0 until ay1) for (xx in ax0 until ax1) prot[yy * w + xx] = true
        }
        // bulles voisines rondes : leur intérieur et leur contour sont protégés (ni échantillonnés pour le fond, ni effacés), sauf sous le texte propre du bloc
        if (protectRound.isNotEmpty()) {
            val own = TextErase.rectMask(w, h, lineBoxes0.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }, 3)
            for (pe in protectRound) {
                val ecx = pe.centerX * s - x0; val ecy = pe.centerY * s - y0; val erx = pe.width / 2f * s * 1.1f; val ery = pe.height / 2f * s * 1.1f
                for (yy in 0 until h) for (xx in 0 until w) {
                    val ex = (xx + 0.5f - ecx) / erx; val ey = (yy + 0.5f - ecy) / ery
                    if (ex * ex + ey * ey <= 1f && !own[yy * w + xx]) prot[yy * w + xx] = true
                }
            }
        }
        // bulle détectée mais non confirmée : son contour n'est jamais effacé (la zone d'effacement reste dans l'ellipse de la bulle, ou son rectangle), sauf sous le texte propre
        if (confine != null) {
            val own = TextErase.rectMask(w, h, lineBoxes0.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }, 3)
            val ecx = confine.centerX * s - x0; val ecy = confine.centerY * s - y0; val erx = confine.width / 2f * s; val ery = confine.height / 2f * s
            fun lumAt(c: Int) = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            // position réelle du contour : pic de pixels contrastés autour de d = 1. La boîte du détecteur est un peu plus grande ou plus petite que le contour réel, et le
            // contour reste à protéger là où il est, pas là où la boîte le suppose
            var dPeak = 1.0f; var outlineFound = false
            if (!confineRect) {
                val inside = ArrayList<Int>()
                for (yy in 0 until h step 3) for (xx in 0 until w step 3) {
                    val ex = (xx + 0.5f - ecx) / erx; val ey = (yy + 0.5f - ecy) / ery
                    if (ex * ex + ey * ey < 0.36f && !own[yy * w + xx]) inside.add(lumAt(px[yy * w + xx]))
                }
                if (inside.size > 30) {
                    inside.sort(); val ref = inside[inside.size / 2]
                    val hist = IntArray(30)
                    for (yy in 0 until h) for (xx in 0 until w) {
                        if (own[yy * w + xx]) continue
                        val ex = (xx + 0.5f - ecx) / erx; val ey = (yy + 0.5f - ecy) / ery; val d = kotlin.math.sqrt(ex * ex + ey * ey)
                        if (d < 0.75f || d >= 1.35f) continue
                        if (abs(lumAt(px[yy * w + xx]) - ref) > 70) hist[((d - 0.75f) / 0.02f).toInt().coerceIn(0, 29)]++
                    }
                    var best = 0; for (b in 1 until 30) if (hist[b] > hist[best]) best = b
                    // un vrai contour : pic net (au moins ¼ du pourtour en pixels contrastés et nettement au-dessus des autres couronnes). Sans cela (fausse bulle posée sur du
                    // texte ou du dessin), aucun anneau n'est protégé : on n'empêche pas d'effacer des lettres
                    val perimeter = (2.0 * Math.PI * kotlin.math.sqrt(((erx * erx + ery * ery) / 2f).toDouble())).toFloat()
                    val others = hist.filterIndexed { i, _ -> kotlin.math.abs(i - best) > 3 }.sorted()
                    val medOthers = if (others.isEmpty()) 0 else others[others.size / 2]
                    if (hist[best] >= 0.25f * perimeter * 0.02f * erx && hist[best] >= 3 * max(1, medOthers)) { dPeak = 0.75f + (best + 0.5f) * 0.02f; outlineFound = true }
                }
            }
            val lo2 = (dPeak - 0.05f) * (dPeak - 0.05f); val hi2 = (dPeak + 0.10f) * (dPeak + 0.10f)
            for (yy in 0 until h) for (xx in 0 until w) {
                var outside: Boolean; var ring: Boolean
                if (confineRect) {
                    val gx = xx + x0; val gy = yy + y0
                    outside = gx < confine.left * s + 3 || gx > confine.right * s - 3 || gy < confine.top * s + 3 || gy > confine.bottom * s - 3
                    ring = gx < confine.left * s + 8 || gx > confine.right * s - 8 || gy < confine.top * s + 8 || gy > confine.bottom * s - 8
                } else {
                    val ex = (xx + 0.5f - ecx) / erx; val ey = (yy + 0.5f - ecy) / ery; val d2 = ex * ex + ey * ey
                    outside = if (outlineFound) d2 > lo2 else d2 > 1.0f; ring = outlineFound && d2 > lo2 && d2 < hi2      // anneau du contour : jamais effacé, même sous la boîte de l'OCR (souvent plus grande que les lettres)
                }
                // la boîte du texte lu peut couper le contour (gros lettrage dans une bulle ajustée) : l'anneau du contour reste protégé partout
                if (outside && (ring || !own[yy * w + xx])) prot[yy * w + xx] = true
            }
        }
        var chosen = -1f; var bg = 0; var zone: BooleanArray? = null
        for (g in floatArrayOf(0.35f, 0.7f, 1.2f, 1.8f, 2.6f, 3.4f)) {
            val zoneRad = max(4, (lhPx * g).toInt()); val ringRad = zoneRad + max(6, (lhPx * 0.35f).toInt())
            val inner = TextErase.rectMask(w, h, rects, zoneRad); val outer = TextErase.rectMask(w, h, rects, ringRad)
            val rr = ArrayList<Int>(); val gg = ArrayList<Int>(); val bb = ArrayList<Int>()
            for (i in 0 until w * h) if (outer[i] && !inner[i] && !prot[i]) { val c = px[i]; rr.add((c shr 16) and 0xFF); gg.add((c shr 8) and 0xFF); bb.add(c and 0xFF) }
            if (rr.size < 60) continue
            rr.sort(); gg.sort(); bb.sort()
            val mr = rr[rr.size / 2]; val mg = gg[gg.size / 2]; val mb = bb[bb.size / 2]
            var near = 0; var total = 0
            for (i in 0 until w * h) if (outer[i] && !inner[i] && !prot[i]) {
                val c = px[i]; total++
                if (max(abs(((c shr 16) and 0xFF) - mr), max(abs(((c shr 8) and 0xFF) - mg), abs((c and 0xFF) - mb))) <= 28) near++
            }
            if (near.toFloat() / total >= 0.92f) {
                // la couronne lointaine est unie, mais la zone elle-même peut contenir du dessin (un grand bruitage sur un décor entouré d'une marge blanche ou noire) :
                // au-delà de 0,35 hauteur de ligne autour du texte, tout pixel nettement différent du fond est du dessin qu'un aplat effacerait à tort
                val base = TextErase.rectMask(w, h, rects, max(4, (lhPx * 0.35f).toInt()))
                var shellN = 0; var shellFar = 0; var zoneN = 0
                for (i in 0 until w * h) if (inner[i] && !prot[i]) {
                    zoneN++
                    if (!base[i]) { shellN++; val c = px[i]; if (max(abs(((c shr 16) and 0xFF) - mr), max(abs(((c shr 8) and 0xFF) - mg), abs((c and 0xFF) - mb))) > 40) shellFar++ }
                }
                if (shellN >= 120 && shellFar.toFloat() / shellN > 0.10f) return null
                // et la zone effacée reste du même ordre que le texte (jamais un panneau entier)
                var textN = 0; for (i in 0 until w * h) if (base[i]) textN++
                if (zoneN > 12 * max(1, textN)) return null
                chosen = g; bg = (0xFF shl 24) or (mr shl 16) or (mg shl 8) or mb; zone = inner; break
            }
        }
        val z = zone ?: return null
        val mask = BooleanArray(w * h) { z[it] && !prot[it] }
        if (mask.none { it }) return null
        // bords francs dans la marge du texte ou autour de la zone (dessin, panneau voisin) : un aplat les effacerait, on laisse la place au traitement des seules lettres
        run {
            val textM = TextErase.rectMask(w, h, lineBoxes0.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }, max(1, (lhPx * 0.1f).toInt()))
            // éléments voisins connus (textes d'autres blocs, bulles voisines dont le contour borde la zone) : ce n'est pas la structure du fond
            val neighboursR = (exclude + outlines + protect).map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
            val ignoreR = if (neighboursR.isEmpty()) null else TextErase.rectMask(w, h, neighboursR, max(6, (lhPx * 0.3f).toInt()))
            val st = TextErase.structure(px, w, h, mask, textM, BooleanArray(w * h) { !prot[it] && (ignoreR == null || !ignoreR[it]) })
            if (st[0] > 0.012f) return null
        }
        // bords adoucis : le remplissage se fond dans le fond restant (traces de lueur très faibles au-delà de la zone)
        val alpha = TextErase.featherAlpha(mask, w, h)
        // fond uni : aplat exact ; sinon reconstruction qui suit le fond autour de la zone (dégradé, ombrage, bruit)
        val adaptive = TextErase.zoneFill(px, w, h, mask, bg)
        val layer = IntArray(w * h) { if (mask[it]) (alpha[it] shl 24) or ((adaptive?.get(it) ?: bg) and 0xFFFFFF) else 0 }
        // couleur du texte : la plus éloignée du fond dans les lignes ; encre : pixels nettement plus éloignés du fond que la lueur éventuelle
        val core = TextErase.rectMask(w, h, rects, 0)
        val textColor = TextErase.extremeTextColor(px, core)
        val bgr = (bg shr 16) and 0xFF; val bgg = (bg shr 8) and 0xFF; val bgb = bg and 0xFF
        val dist = IntArray(w * h) { i -> val c = px[i]; val dr = ((c shr 16) and 0xFF) - bgr; val dg = ((c shr 8) and 0xFF) - bgg; val db = (c and 0xFF) - bgb; kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat()).toInt() }
        val coreD = ArrayList<Int>(); for (i in 0 until w * h) if (core[i]) coreD.add(dist[i])
        coreD.sort()
        val p95 = if (coreD.isEmpty()) 0 else coreD[(coreD.size * 0.95f).toInt().coerceAtMost(coreD.size - 1)]
        val thr = max(60, (p95 * 0.7f).toInt())
        val ink = BooleanArray(w * h) { core[it] && dist[it] >= thr }
        // lueur : pixels de la zone ni fond ni encre, assez nombreux et colorés (halo rouge autour d'un texte blanc, par exemple)
        var glowN = 0; var gr = 0L; var gg2 = 0L; var gb = 0L
        for (i in 0 until w * h) if (mask[i] && !ink[i] && dist[i] in 40 until thr) { glowN++; val c = px[i]; gr += (c shr 16) and 0xFF; gg2 += (c shr 8) and 0xFF; gb += c and 0xFF }
        val zoneN = mask.count { it }
        var glow = 0
        if (glowN > zoneN * 0.12f && glowN > 200) {
            val r = (gr / glowN).toInt(); val g2 = (gg2 / glowN).toInt(); val b2 = (gb / glowN).toInt()
            val mx = max(r, max(g2, b2)); val mn = kotlin.math.min(r, kotlin.math.min(g2, b2))
            if (mx - mn >= 40) glow = (0xFF shl 24) or (min(255, (r * 1.6f).toInt()) shl 16) or (min(255, (g2 * 1.6f).toInt()) shl 8) or min(255, (b2 * 1.6f).toInt())
        }
        val ci = coreOf(px, w, h, ink, bg, textColor)
        return Plan(Mode.FLAT, x0, y0, w, h, mask, layer, bg, textColor, false, null, null, TextErase.strokeWidth(ink, w, h), ink, bg and 0xFFFFFF, glow, ci.mask, ci.color, ci.halo, ci.haloPx)
    }

    /**
     * Contour d'une bulle voisine à ne pas entamer : traits sombres situés au bord (anneau de l'ellipse) d'une bulle confirmée et qui se prolongent
     * au-delà de la zone effacée. Un texte qui chevauche le contour garde donc le contour intact (le texte noir qui s'y fond y reste invisible).
     */
    internal fun outlineKeep(px: IntArray, w: Int, h: Int, zone: BooleanArray, x0: Int, y0: Int, s: Float, outlines: List<RectF>): BooleanArray {
        val keep = BooleanArray(w * h)
        if (outlines.isEmpty()) return keep
        val band = BooleanArray(w * h)
        var any = false
        for (o in outlines) {
            val cx = (o.left + o.right) / 2f; val cy = (o.top + o.bottom) / 2f; val rx = max(4f, (o.right - o.left) / 2f); val ry = max(4f, (o.bottom - o.top) / 2f)
            for (i in 0 until w * h) {
                val ix = (x0 + i % w + 0.5f) / s; val iy = (y0 + i / w + 0.5f) / s
                val ex = (ix - cx) / rx; val ey = (iy - cy) / ry; val d = kotlin.math.sqrt(ex * ex + ey * ey)
                if (d in 0.78f..1.14f) { band[i] = true; any = true }
            }
        }
        if (!any) return keep
        val dark = BooleanArray(w * h) { i -> band[i] && ((((px[i] shr 16) and 0xFF) * 77 + ((px[i] shr 8) and 0xFF) * 150 + (px[i] and 0xFF) * 29) shr 8) < 110 }
        // composantes sombres de l'anneau : on garde celles qui se prolongent hors de la zone d'effacement (un vrai contour, pas une lettre)
        val label = IntArray(w * h) { -1 }; val stack = IntArray(w * h)
        var next = 0
        for (st in 0 until w * h) {
            if (!dark[st] || label[st] >= 0) continue
            val members = ArrayList<Int>(); var outside = 0
            var sp = 0; stack[sp++] = st; label[st] = next
            while (sp > 0) {
                val i = stack[--sp]; members.add(i); if (!zone[i]) outside++
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (dark[j] && label[j] < 0) { label[j] = next; stack[sp++] = j }
                }
            }
            next++
            if (outside >= 30) for (i in members) keep[i] = true
        }
        return keep
    }

    private fun planUnclipped(src: PixelSource, s: Float, lineBoxes: List<RectF>, lineH: Float, imageW: Float, imageH: Float, padOverride: Float = 0f, container: RectF? = null, protect: List<RectF> = emptyList(), free: Boolean = false, outlines: List<RectF> = emptyList(), extra: List<RectF> = emptyList(), protectRound: List<RectF> = emptyList(), confine: RectF? = null, confineRect: Boolean = false, exclude: List<RectF> = emptyList()): Plan? {
        // texte hors bulle sur des abords unis (page blanche ou noire, aplat, lueur sur fond uni) : toute la zone est remplacée par la couleur des abords
        if (free && padOverride == 0f) planRing(src, s, lineBoxes, lineH, imageW, imageH, protect, outlines, extra, protectRound, confine, confineRect, exclude)?.let { return it }
        // texte lu : sert à relever la forme de la bulle ; avec la zone détectée en plus : étendue de l'effacement
        val textUnion = lineBoxes.reduce { a, c -> a.union(c) }
        val union = (lineBoxes + extra).reduce { a, c -> a.union(c) }
        val pad = max(max(10f, lineH * 0.7f), padOverride)
        val x0 = (max(0f, union.left - pad) * s).toInt().coerceIn(0, src.width - 1)
        val y0 = (max(0f, union.top - pad) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + pad) * s).toInt().coerceIn(x0 + 1, src.width)
        val y1 = (min(imageH, union.bottom + pad) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 8 || h < 8) return null
        val px = src.pixels(x0, y0, w, h)
        fun rects() = (lineBoxes + extra).map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
        val lh = lineH * s

        // blocs voisins connus (autres textes, bulles voisines) : leur encre dans le cadre d'analyse n'est pas un défaut d'uniformité du fond
        val frameIgnore: BooleanArray? = (exclude + protect + protectRound + outlines).map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
            .takeIf { it.isNotEmpty() }?.let { TextErase.rectMask(w, h, it, max(6, (lh * 0.3f).toInt())) }
        var a = TextErase.analyze(px, w, h, TextErase.rectMask(w, h, rects(), max(3, (lh * 0.2f).toInt())), grow = max(2, (lh * 0.06f).toInt()), ignoreFrame = frameIgnore)
        if (a.outlined) a = TextErase.analyze(px, w, h, TextErase.rectMask(w, h, rects(), max(6, (lh * 0.3f).toInt())), grow = max(3, (lh * 0.1f).toInt()), ignoreFrame = frameIgnore)

        // bulle : zone de texte sur fond uni ET (abords unis OU contour fermé : le remplissage depuis le texte reste borné)
        val bubble = if (!a.outlined && a.zoneFlat >= 0.9f && a.bgShare >= 0.45f) bubbleArea(src, s, textUnion, a.bg, imageW, imageH) else null
        val textRect = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        val zone = TextErase.rectMask(w, h, rects(), max(4, (lh * 0.35f).toInt()))   // toute la zone du texte, ponctuation et halo compris
        // Structure du fond autour du texte : un aplat de zone ou un dégradé lissé ne conviennent qu'à un fond lisse (blanc, couleur unie, dégradé, ombrage, flou). Quand la marge du texte
        // porte des bords francs ou une masse de dessin très différente (contours, cheveux, motifs, panneau sombre), un remplacement de toute la zone abîme le dessin et se voit comme
        // un carré : on n'efface alors que les lettres, et le reste du dessin est conservé. Dans une bulle, seul son intérieur compte (le contour n'est pas une structure du fond).
        val interiorLoose: BooleanArray? by lazy { BubbleMask.interior(px, w, h, textRect, a.bg, strict = false) }   // sans refus de fuite : sert seulement à borner l'effacement
        val interiorPre: BooleanArray? by lazy { BubbleMask.interior(px, w, h, textRect, a.bg) }   // seulement si une bulle ou l'aplat en a besoin
        val bubbleCtx = container != null || bubble != null
        val structured = run {
            if (a.outlined || (bubbleCtx && interiorPre == null)) return@run false
            val textS = TextErase.rectMask(w, h, lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }, max(1, (lh * 0.1f).toInt()))
            // les éléments voisins connus (textes d'autres blocs, bulles voisines) ne sont pas la structure du fond
            val neighbours = (exclude + protect + protectRound + outlines).map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
            val ignore = if (neighbours.isEmpty()) null else TextErase.rectMask(w, h, neighbours, max(6, (lh * 0.3f).toInt()))
            val limit = if (bubbleCtx) BooleanArray(w * h) { interiorPre!![it] && (ignore == null || !ignore[it]) } else if (ignore != null) BooleanArray(w * h) { !ignore[it] } else null
            val st = TextErase.structure(px, w, h, zone, textS, limit)
            trace?.invoke("structure : bords=${st[0]} masse=${st[2]} (seuils 0,012 / 0,10) ; voisins=${neighbours.size}")
            st[0] > 0.012f || st[2] > 0.10f
        }
        // un lettrage cerné d'un contour ou d'une lueur est traité comme du dessin : l'aplat laisserait des fragments du contour
        trace?.invoke("décision : structured=$structured outlined=${a.outlined} zoneFlat=${a.zoneFlat} bgShare=${a.bgShare} uniformity=${a.uniformity} bubble=${bubble != null} bg=${"%06X".format(a.bg and 0xFFFFFF)}")
        val flatBase = !forceArt && !structured && !a.outlined && a.zoneFlat >= 0.9f && a.bgShare >= 0.45f && (a.uniformity >= 0.85f || bubble != null)
        // Lettrage stylisé (dégradé, lueur, contour épais) isolé sur un bandeau blanc entre deux cases : sa lueur faisait classer le fond comme « non uni », ou la bordure de case voisine comme
        // « structure », et l'effacement lettre par lettre laissait la moitié grise des lettres ou des morceaux colorés sous la traduction. Quand le lettrage est entièrement isolé du dessin
        // (aucune de ses composantes ne touche le bord de la région), tout ce que la zone contient en dehors des traits francs du décor est effacé à plat, sans reste.
        val isolated = !forceArt && !flatBase && isolatedOnWhite(px, w, h, a, lineBoxes, s, x0, y0, zone)
        trace?.invoke("lettrage isolé sur blanc : $isolated")
        val flat = forceFlat || flatBase || isolated
        if (flat) {
            val allowedZone = TextErase.rectMask(w, h, rects(), max(3, (lh * 0.2f).toInt()))
            val shrink = max(1, (lh * 0.2f).toInt())
            val core = TextErase.rectMask(w, h, rects().map { r -> intArrayOf(r[0], r[1] + shrink, r[2], r[3] - shrink) }.filter { r -> r[3] > r[1] }, 0)
            val interior = interiorPre
            trace?.invoke("aplat : intérieur de bulle ${if (interior != null) "trouvé (${interior.count { it }} px)" else "absent"} ; cœur du texte ${core.count { it }} px ; région ${w}x$h")
            val thin = BooleanArray(w * h)
            val diagLimit = arrayOfNulls<BooleanArray>(1)
            // voisinage des lignes lues (pas de la zone détectée) : les pointes d'un contour « étoile » et les traits fins qui passent dans les coins de la zone ne sont pas des lettres
            val nearLinesF = TextErase.rectMask(w, h, lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }, max(4, (lh * 0.35f).toInt()))
            val rawAll = TextErase.textComponents(px, w, h, a.bg, a.threshold, allowedZone, 0, interior, core, thin, (lh * 0.6f).toInt(), reach = interior ?: interiorLoose, limitOut = diagLimit)
            val raw = TextErase.withoutHatch(rawAll, w, h, nearLinesF)
            val stroke = TextErase.strokeWidth(raw, w, h)
            // nettoyage de toute la zone : plus de « fantômes » (bruit de compression JPEG autour des lettres, points et ponctuation hors boîte OCR)
            val clean = BooleanArray(w * h)
            if (interior != null) { for (i in clean.indices) clean[i] = zone[i] && interior[i] }
            else {
                val far = TextErase.farMask(px, a.bg, a.threshold)
                // composantes isolées de la taille du texte seulement : un contour de bulle fermé qui tient dans la région (gros lettrage dans une bulle ajustée) est « isolé » lui
                // aussi, mais bien plus grand que le texte ; il est protégé, comme le contour relié au bord de la région
                val enc = TextErase.withoutHatch(TextErase.limitSize(TextErase.enclosedOnly(far, w, h), w, h, ((textRect[2] - textRect[0]) * 1.2f).toInt() + 4, ((textRect[3] - textRect[1]) * 1.2f).toInt() + 4), w, h, nearLinesF)
                // le nettoyage ne va pas au-delà de la portée du bruit de compression autour des lettres : sans contour de bulle relevé, tout le rectangle de la zone recevait l'aplat, et ses
                // coins sortaient de la tache (ou de la bulle) sur le dessin voisin, un carré net derrière la traduction
                val nearInk = TextErase.dilate(BooleanArray(w * h) { raw[it] || enc[it] }, w, h, max(4, (lh * 0.3f).toInt()))
                for (i in clean.indices) clean[i] = zone[i] && ((!far[i] && nearInk[i]) || enc[i])
            }
            val grown = TextErase.dilate(raw, w, h, max(2, (lh * 0.06f).toInt()), diagLimit[0])   // texte incliné : l'élargissement des lettres ne sort pas de la bulle (le contour de l'étiquette reste)
            // le contour fin soudé aux lettres (thin) n'est jamais effacé, ni par le nettoyage de la zone ni par la dilatation des lettres
            val mask = BooleanArray(w * h) { !thin[it] && (clean[it] || grown[it]) }
            trace?.invoke("aplat : lettres=${raw.count { it }} (avant tri des hachures ${rawAll.count { it }}) zone=${zone.count { it }} nettoyage=${clean.count { it }} élargies=${grown.count { it }} retirées(fines)=${thin.count { it }} masque=${mask.count { it }}")
            // fond uni : aplat exact (comportement d'origine) ; fond en dégradé, ombré ou légèrement bruité : reconstruction qui suit le fond autour de la zone, avec bords adoucis.
            // Le fond se juge d'abord sous le masque lui-même (hors lettres) : une zone qui atteint le contour de la bulle n'a plus de fond autour d'elle, seulement le bord flou du contour
            val uniformUnder = TextErase.uniformBackground(px, w, h, mask, grown, a.bg)
            if (uniformUnder) trace?.invoke("fond sous le masque uni (hors lettres) : aplat exact, sans reconstruction")
            val adaptiveFlat = if (uniformUnder) null else TextErase.zoneFill(px, w, h, mask, a.bg)
            val featherF = if (adaptiveFlat != null) TextErase.featherAlpha(mask, w, h) else null
            val layer = IntArray(w * h) { if (mask[it]) ((featherF?.get(it) ?: 0xFF) shl 24) or ((adaptiveFlat?.get(it) ?: a.bg) and 0xFFFFFF) else 0 }
            val shape = bubbleShape(src, s, textUnion, a.bg, imageW, imageH, container = container)
            val ci = coreOf(px, w, h, raw, a.bg, a.textColor)
            return Plan(Mode.FLAT, x0, y0, w, h, mask, layer, a.bg, a.textColor, a.outlined, bubble, shape, stroke, raw, meanFill(layer), 0, ci.mask, ci.color, ci.halo, ci.haloPx)
        }
        // bulle au fond non uni (dégradé, ombre, lueur sombre) : on remplace toute la zone du texte à l'intérieur de la bulle par un fond lissé
        if (!free && !structured) run {
            // la forme de la bulle est calculée sur une région plus large que la zone du texte (la bulle entière doit y tenir)
            // lignes lues et zone du détecteur : l'intérieur de la bulle les contient toujours (voir bubbleShape, paramètre force)
            val shape = bubbleShape(src, s, textUnion, a.bg, imageW, imageH, smooth = true, container = container, force = lineBoxes + extra)
            if (shape != null) {
                val farS = TextErase.localFarMask(px, w, h, max(5, (lh * 0.6f).toInt()), 40); val encS = TextErase.enclosedOnly(farS, w, h) // lettres isolées
                val encG = TextErase.dilate(encS, w, h, max(2, (lh * 0.06f).toInt()))   // bords des lettres compris : ils ne doivent pas servir de référence au remplissage
                // tout pixel nettement différent de son voisinage à l'intérieur d'une ligne de texte est du texte, même s'il touche le bord d'une bulle voisine
                // (sauf dans une bulle voisine détectée : on ne grignote pas son contour)
                val coreLines = TextErase.rectMask(w, h, rects(), 0)
                val prot = BooleanArray(w * h)
                for (pr in protect) {
                    val px0 = ((pr.left * s).toInt() - x0).coerceIn(0, w); val px1 = ((pr.right * s).toInt() - x0).coerceIn(0, w)
                    val py0 = ((pr.top * s).toInt() - y0).coerceIn(0, h); val py1 = ((pr.bottom * s).toInt() - y0).coerceIn(0, h)
                    for (yy in py0 until py1) for (xx in px0 until px1) prot[yy * w + xx] = true
                }
                // frange adoucie du contour des lettres : ses pixels intermédiaires sont trop proches de leur voisinage pour être « loin » et restaient en lettres fantômes
                // (contour blanc visible sous la traduction) là où l'intérieur de la bulle n'est pas détecté ; ils partent avec les lettres
                // seuls les pixels des lignes de texte elles-mêmes sont élargis (pas ceux de la zone du détecteur, qui peut toucher le contour de la bulle : il serait grignoté)
                val textOnly = TextErase.rectMask(w, h, lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }, 0)
                // seulement les composantes de la taille du texte (lettres, mots) : un arc du contour de la bulle qui passe dans la boîte d'une ligne est bien plus grand, il n'est pas élargi
                val letters = TextErase.limitSize(farS, w, h, (textUnion.width * s).toInt() + 6, (textUnion.height * s).toInt() + 6)
                val farText = BooleanArray(w * h) { textOnly[it] && letters[it] && !prot[it] }
                val farTextG = TextErase.dilate(farText, w, h, max(2, (lh * 0.1f).toInt()), zone)
                // frange des lettres soudées à un trait du décor (mèche de cheveux, contour) : leur composante dépasse la taille du texte, elle n'était donc pas élargie, et le bord adouci de
                // leurs traits (gris clair, anneau de compression) restait en « fantômes » pointillés sous la traduction. Seuls les pixels un peu différents de leur voisinage (écart 14 à 40,
                // le reste est déjà de l'encre) à moins de 2 px de l'encre effacée des lignes de texte partent avec elle : rien n'est élargi plus loin, ni autour d'un trait hors des lignes
                val soft = TextErase.localFarMask(px, w, h, max(5, (lh * 0.6f).toInt()), 14)
                val inkLines = BooleanArray(w * h) { coreLines[it] && farS[it] && !prot[it] }
                val nearInkLines = TextErase.dilate(inkLines, w, h, max(2, (lh * 0.1f).toInt()), zone)
                val mask0 = BooleanArray(w * h) { i -> zone[i] && (shape.contains((x0 + i % w + 0.5f) / s, (y0 + i / w + 0.5f) / s) || encG[i] || (coreLines[i] && farS[i] && !prot[i]) || (farTextG[i] && !prot[i]) || (soft[i] && nearInkLines[i] && !prot[i])) }
                // pixels isolés restés hors du masque au milieu de l'effacement (pointes des lettres) : bouchés, sans quoi des fragments pointillés restent sous la traduction
                val mask = TextErase.fillHoles(mask0, w, h, max(8, (lh * lh * 0.08f).toInt()))
                if (trace != null) {
                    var inShape = 0; var inkTotal = 0; var inkOut = 0
                    for (i in 0 until w * h) { if (zone[i] && shape.contains((x0 + i % w + 0.5f) / s, (y0 + i / w + 0.5f) / s)) inShape++; if (coreLines[i] && farS[i]) { inkTotal++; if (!mask[i]) inkOut++ } }
                    trace?.invoke("lissé : zone=${zone.count { it }} dans la forme=$inShape masque=${mask.count { it }} lettres dans les lignes=$inkTotal dont hors masque=$inkOut")
                }
                if (mask.count { it } > 0) {
                    // références du remplissage : seulement l'intérieur de la bulle (ni la page blanche autour, ni le contour)
                    val outside = BooleanArray(w * h) { i -> !shape.contains((x0 + i % w + 0.5f) / s, (y0 + i / w + 0.5f) / s) }
                    val unknownS = BooleanArray(w * h) { mask[it] || outside[it] }
                    // sources du remplissage : l'intérieur de la bulle, proche du fond (ni contour, ni autre texte). Initialisation par le plus proche voisin puis relaxation :
                    // l'ancienne diffusion partait de la moyenne de toute la région (contour sombre compris) et ne convergeait pas au centre d'une grande zone (teinte de « carré »)
                    val validS = BooleanArray(w * h) { !unknownS[it] && TextErase.dist(px[it], a.bg) <= 80 }
                    // reconstruction multi-échelle seulement si l'intérieur de la bulle est lisse (dégradé, ombrage) ; avec du dessin dedans (fausse bulle sur un décor), on garde l'ancienne diffusion,
                    // sans régression : la reconstruction lisse y donnerait une grande tache
                    val textSm = TextErase.rectMask(w, h, lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }, max(1, (lh * 0.1f).toInt()))
                    val neighboursSm = (exclude + protect + protectRound + outlines).map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
                    val ignoreSm = if (neighboursSm.isEmpty()) null else TextErase.rectMask(w, h, neighboursSm, max(6, (lh * 0.3f).toInt()))
                    val limitSm = BooleanArray(w * h) { !outside[it] && (ignoreSm == null || !ignoreSm[it]) }
                    val stSm = TextErase.structure(px, w, h, zone, textSm, limitSm)
                    // et si presque tous les pixels connus de la région servent de source (fond proche de la couleur de la bulle) : sinon la reconstruction manque de références
                    var knownN = 0; var validN = 0; for (i in 0 until w * h) if (!unknownS[i]) { knownN++; if (validS[i]) validN++ }
                    var smoothInterior = stSm[0] <= 0.012f && stSm[2] <= 0.10f && knownN > 0 && validN.toFloat() / knownN >= 0.9f
                    var sources = validS
                    trace?.invoke("lissé : structure bords=${stSm[0]} masse=${stSm[2]} connus=$knownN sources=$validN -> ${if (smoothInterior) "intérieur lisse (reconstruction multi-échelle)" else "fond non uni"}")
                    // Dégradé net (de la bulle entière) : ni la couleur médiane ni « proche de la couleur dominante » ne le représentent, l'intérieur passait pour du dessin et la zone recevait
                    // la diffusion depuis la moyenne (une bande grise plate au milieu du dégradé). Le fond est alors ajusté par une surface lisse (second degré) : si les pixels connus la suivent
                    // (pas de bords francs, peu d'écarts à la surface), l'intérieur est lisse et la reconstruction multi-échelle part des seuls pixels conformes à la surface.
                    if (!smoothInterior && stSm[0] <= 0.012f && knownN > 0) {
                        val usable = BooleanArray(w * h) { !unknownS[it] && !farS[it] && !encG[it] }
                        val surf = TextErase.smoothSurface(px, w, h, usable)
                        if (surf != null) {
                            val stG = TextErase.structure(px, w, h, zone, textSm, limitSm, surf)
                            val validG = BooleanArray(w * h) { !unknownS[it] && TextErase.dist(px[it], surf[it]) <= 45 }
                            val validGN = validG.count { it }
                            val okG = stG[2] <= 0.10f && validGN.toFloat() / knownN >= 0.9f
                            trace?.invoke("lissé : dégradé ajusté masse=${stG[2]} sources=$validGN/$knownN -> ${if (okG) "intérieur lisse (dégradé)" else "ancienne diffusion"}")
                            if (okG) { smoothInterior = true; sources = validG }
                        }
                    }
                    // Presque aucun pixel de référence dans la « bulle » (texte posé sur un décor, la forme trouvée ne couvre que quelques pixels de la zone) et intérieur non lisse : l'ancienne diffusion
                    // n'a rien d'où partir et remplit toute la zone d'un gris plat (un carré visible, ou les lettres restent en fantômes dessous). Le texte est alors traité comme un texte sur dessin :
                    // seules les lettres sont reconstruites (branche suivante).
                    val noReference = !smoothInterior && knownN < max(64, zone.count { it } / 20)
                    if (noReference) trace?.invoke("lissé : presque aucun pixel de référence ($knownN) -> texte sur dessin (reconstruction des lettres)")
                    else {
                        val fill = if (!smoothInterior) TextErase.inpaint(px, w, h, unknownS) else TextErase.inpaintSmooth(px, w, h, unknownS, sources)
                        val alpha = TextErase.featherAlpha(mask, w, h)
                        val layer = IntArray(w * h) { if (mask[it]) (alpha[it] shl 24) or (fill[it] and 0xFFFFFF) else 0 }
                        val fc = TextErase.extremeTextColor(px, zone)
                        val ink = TextErase.artMask(px, w, h, zone, fc, 0)
                        val ci = coreOf(px, w, h, ink, a.bg, fc)
                        return Plan(Mode.SMOOTH, x0, y0, w, h, mask, layer, a.bg, fc, false, null, shape, TextErase.strokeWidth(ink, w, h), ink, meanFill(layer), 0, ci.mask, ci.color, ci.halo, ci.haloPx)
                    }
                }
            }
        }
        // texte sur dessin : seules les lettres (remplissage + contour) sont effacées et reconstruites, pas un rectangle entier
        val zoneArt = TextErase.rectMask(w, h, rects(), min(if (a.outlined) 30 else 18, max(4, (lh * (if (a.outlined) 0.35f else 0.25f)).toInt())))
        val lineRectsPx = lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
        val tightForColor = TextErase.rectMask(w, h, lineRectsPx, 0)
        val extremeColor = TextErase.extremeTextColor(px, zoneArt, tightForColor, w, h, lh.toInt())
        // lettrage coloré sur une lueur claire : l'extrême de luminosité est la lueur, pas les lettres (voir TextErase.enrichedFill). Les boîtes des textes voisins ne comptent pas dans la comparaison.
        val neighForColor = (exclude + protect).map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
        val neighMaskColor = if (neighForColor.isEmpty()) null else TextErase.rectMask(w, h, neighForColor, 0)
        // voisinage immédiat des boîtes seulement : la région de la seconde passe s'étend loin (ciel noir sous une lueur) et fausserait la comparaison
        val nearBoxesColor = TextErase.rectMask(w, h, lineRectsPx, (lh * 0.6f).toInt().coerceIn(24, 80))
        val outsideColor = BooleanArray(w * h) { nearBoxesColor[it] && !tightForColor[it] && (neighMaskColor == null || !neighMaskColor[it]) }
        val enrichedColor = TextErase.enrichedFill(px, w, h, tightForColor, outsideColor, extremeColor)
        if (enrichedColor != null) trace?.invoke("couleur du texte : %06X absente des lignes lues -> couleur des lettres %06X (la plus fréquente dans les lignes, rare dehors)".format(extremeColor and 0xFFFFFF, enrichedColor and 0xFFFFFF))
        // La couleur enrichie ne sert qu'au masque d'effacement : la couleur déclarée du texte (peinture du texte traduit, choix de la police, corps cible) reste celle d'avant, que ces réglages
        // connaissent. Mesurée à l'échelle réelle de l'application, la couleur des lettres faisait lire le contour sombre comme le corps des traits (texte rouge sombre) et grossir l'encre.
        val fillColor = extremeColor
        val maskColor = enrichedColor ?: extremeColor
        fun lumOf(c: Int) = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        // texte sombre sur fond clair : les traits fins sombres à plus de 3 px des lignes lues sont du décor (hachures d'un contour, peigne de traits d'un panneau), jamais des lettres. La zone
        // détectée ne sert pas ici : elle déborde de plusieurs pixels sur le décor voisin (la bordure du panneau y entrait)
        val darkOnLight = lumOf(fillColor) < 100 && lumOf(a.bg) >= 200
        val thinInner = if (darkOnLight) tightForColor else null
        // lettrage à lueur : on élargit le masque pour effacer aussi la lueur, de sorte que la reconstruction vienne des abords du halo
        // masque serré : lettres et contour, élargis de 2 à 4 px seulement. Un masque élargi à 14 px (ancienne règle, proportionnelle à la hauteur de ligne) fusionnait toutes
        // les lettres en un grand rectangle reconstruit par diffusion : un flou visible sur le dessin, avec des bords nets
        val tightLines = TextErase.rectMask(w, h, rects(), 3)
        val reachCap = max(4, (lh * 0.06f).toInt())
        // le rattrapage des lettres soudées (boîtes de lecture) ne prend jamais un trait fixe : le contour d'une bulle passe dans les coins de ces boîtes. Les traits sont repérés sur les
        // lettres déjà retenues, comme pour la protection plus bas
        val structureAvoid: ((BooleanArray) -> BooleanArray?)? = if (protectStructure && a.bgShare >= 0.45f) { ink0 -> fixedStructure(px, w, h, a.bg, a.threshold, textRect, ink0)?.protect } else null
        // pointes d'un contour « étoile » et traits fins loin des lignes lues : jamais des lettres (ils passent dans les coins de la zone détectée)
        val nearLines = TextErase.rectMask(w, h, lineRectsPx, max(4, (lh * 0.35f).toInt()))
        val artInfo = TextErase.artMaskInfo(px, w, h, zoneArt, maskColor, 2, Int.MAX_VALUE, tightLines, reachCap, lineRectsPx, structureAvoid, nearLines, thinInner)
        // fond blanc : le halo clair collé aux lettres (compression, lueur) est effacé avec elles, sans quoi il reste un fantôme de leur forme
        val plainWhite = haloClean && TextErase.ringIsPlainWhite(px, w, h, artInfo.mask, a.bg, frameIgnore)
        val mask = if (plainWhite) TextErase.lightHalo(px, w, h, artInfo.mask, a.bg, max(6, (lh * 0.12f).toInt()), frameIgnore) else artInfo.mask
        // texte sur dessin : région élargie pour trouver de la vraie texture à copier à côté du trou (second passage)
        if (padOverride == 0f) planUnclipped(src, s, lineBoxes, lineH, imageW, imageH, max(40f, lineH * 1.6f), container, protect, free, outlines, extra, protectRound, confine, confineRect, exclude)?.let { if (it.mode == Mode.ART) return it }
        // texte sur dessin (fond non blanc uni) : fond propre estimé par interpolation lisse, lueur et frange absorbées, raccord continu (voir ArtFill)
        if (ArtFill.enabled && !plainWhite) {
            val maxGrow = (lh * 0.3f).toInt().coerceIn(6, 24)
            val neigh = (exclude + protect).map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
            val neighRaw = if (neigh.isEmpty()) null else TextErase.rectMask(w, h, neigh, 0)
            // références du remplissage : ni les textes voisins ni leur halo immédiat (un halo sombre collé au texte voisin assombrissait le fond reconstruit)
            val avoidN0 = if (neigh.isEmpty()) null else TextErase.rectMask(w, h, neigh, max(3, (lh * 0.12f).toInt()))
            // contour de bulle ou trait du décor qui traverse la zone, sur un fond dominant uni : jamais effacé (trou dans le contour) ni utilisé comme référence (tache grise le long du trait)
            val fixed = if (protectStructure && a.bgShare >= 0.45f) fixedStructure(px, w, h, a.bg, a.threshold, textRect, mask) else null
            ArtFill.trace?.invoke("structure fixe : ${fixed?.protect?.count { it } ?: 0} px dont ${fixed?.drop?.count { it } ?: 0} retirés du masque (part du fond ${a.bgShare}, texte ${textRect.toList()}, région ${w}x$h)")
            val avoidN = if (fixed == null) avoidN0 else BooleanArray(w * h) { fixed.protect[it] || (avoidN0 != null && avoidN0[it]) }
            // les quelques pixels de zone qui mordent sur le trait fixe ne sont pas du texte : on les laisse (sinon un trou dans le contour)
            val dropped = fixed?.drop
            val core0 = if (dropped == null) mask else BooleanArray(w * h) { mask[it] && !dropped[it] }
            // zone où la lueur peut être effacée : les lignes de texte élargies, jamais les textes des blocs voisins
            fun limitGlowFor(grow: Int): BooleanArray {
                val lg = TextErase.rectMask(w, h, rects(), grow + 6)
                for (i in lg.indices) if (zoneArt[i]) lg[i] = true
                if (neighRaw != null) for (i in lg.indices) if (neighRaw[i]) lg[i] = false
                if (fixed != null) for (i in lg.indices) if (fixed.protect[i]) lg[i] = false
                return lg
            }
            // hachures, lignes de vitesse, pointes d'étoile : traits fins sombres sur fond clair. Ni références du remplissage (la moyenne des bords en faisait une tache grise), ni lueur
            // (un trait ne s'estompe pas avec la distance : l'épaisseur mesurée saturait, la recherche était élargie et le masque prenait la forme des lettres élargies, hachures comprises)
            // seulement pour un texte sombre sur fond clair : sur un lettrage clair à lueur, les creux sombres entre les lettres seraient pris pour des traits
            val hatch = if (darkOnLight) thinDarkOnLight(px, w, h, (lh * 0.06f).toInt().coerceIn(2, 4)) else BooleanArray(w * h)
            val busy = busyRing(core0, hatch, fixed?.protect, w, h, maxGrow)
            ArtFill.trace?.invoke("décor net autour du texte : ${if (busy) "oui (pas de recherche élargie)" else "non"}")
            var rec = ArtFill.reconstruct(px, w, h, core0, limitGlowFor(maxGrow), avoidN, maxGrow, hatch = hatch)
            // halo diffus (lueur épaisse, qui occupe au moins 60 % de la recherche) : le fond « propre » estimé depuis le bord de la bande restait trop clair, et le remplissage finissait en
            // carré visible. La recherche est refaite plus large, avec un fondu extérieur plus doux. Un fond détaillé (cheveux, traits) ou une lueur mince ne donnent pas ce profil : jamais élargis.
            val wideGrow = (lh * 0.6f).toInt().coerceIn(maxGrow, 40)
            if (!busy && rec != null && rec.thick >= 6 && rec.thick * 10 >= maxGrow * 6 && wideGrow > maxGrow + 2) {
                val wide = ArtFill.reconstruct(px, w, h, core0, limitGlowFor(wideGrow), avoidN, wideGrow, ring = (lh * 0.2f).toInt().coerceIn(6, 12), robust = (lh * 0.12f).toInt().coerceIn(4, 10), hatch = hatch)
                ArtFill.trace?.invoke("halo diffus : épaisseur ${rec.thick} (limite $maxGrow) -> recherche élargie à $wideGrow : ${wide?.thick}")
                if (wide != null) rec = wide
            }
            if (rec != null) {
                val smooth = TextErase.periodicFill(px, w, h, rec.safe, rec.fill) ?: ArtFill.addGrain(rec.fill, rec.mask, rec.sigma)
                val layerN = IntArray(w * h) { if (rec.alpha[it] > 0) (rec.alpha[it] shl 24) or (smooth[it] and 0xFFFFFF) else 0 }
                val inkN = TextErase.artMask(px, w, h, zoneArt, fillColor, 0, Int.MAX_VALUE, tightLines, reachCap, nearLines = nearLines, inner = thinInner)
                val ciN = coreOf(px, w, h, inkN, a.bg, fillColor)
                return Plan(Mode.ART, x0, y0, w, h, rec.mask, layerN, a.bg, fillColor, a.outlined || artInfo.outlined, null, null, TextErase.strokeWidth(inkN, w, h), inkN, meanFill(layerN), 0, ciN.mask, ciN.color, ciN.halo, ciN.haloPx)
            }
        }
        // texte sur dessin : couleur du plus proche voisin plutôt que diffusion (la diffusion mélange le blanc d'une bulle et le fond sombre en un gris voilé)
        val diffused = TextErase.inpaintNearest(px, w, h, mask)
        // trame de points ou motif répété autour du trou : on recopie le motif plutôt que de le lisser en un voile gris
        val fill = TextErase.periodicFill(px, w, h, mask, diffused) ?: TextErase.addGrain(TextErase.inpaintClone(px, w, h, mask, diffused), px, w, h, mask)
        val alpha = TextErase.featherAlpha(mask, w, h)
        // blanc franc tout autour : un remplissage qui n'en diffère que par du bruit de compression est ramené au blanc exact (sinon des stries d'un blanc légèrement gris restent visibles)
        val bgPure = a.bg and 0xFFFFFF
        val layer = IntArray(w * h) {
            if (mask[it]) {
                val f = fill[it] and 0xFFFFFF
                (alpha[it] shl 24) or (if (plainWhite && TextErase.dist(f, bgPure) <= 14) bgPure else f)
            } else 0
        }
        val inkArt = TextErase.artMask(px, w, h, zoneArt, fillColor, 0, Int.MAX_VALUE, tightLines, reachCap, nearLines = nearLines, inner = thinInner)
        val ci = coreOf(px, w, h, inkArt, a.bg, fillColor)
        return Plan(Mode.ART, x0, y0, w, h, mask, layer, a.bg, fillColor, a.outlined || artInfo.outlined, null, null, TextErase.strokeWidth(inkArt, w, h), inkArt, meanFill(layer), 0, ci.mask, ci.color, ci.halo, ci.haloPx)
    }

    /**
     * Traits fins sombres sur fond clair (hachures, lignes de vitesse, pointes d'étoile) : pixels nettement plus sombres que la même image débarrassée de ses traits fins (fermeture, voir
     * [ArtFill.withoutThinLines]) dont celle-ci est claire. Un trait plus épais que 2·[radius]+1 px (bordure de case, grande masse sombre) n'en est pas un.
     */
    internal fun thinDarkOnLight(px: IntArray, w: Int, h: Int, radius: Int): BooleanArray {
        val flat = ArtFill.withoutThinLines(px, w, h, radius)
        fun lum(c: Int) = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        return BooleanArray(w * h) { val c = px[it]; val f = flat[it]; TextErase.dist(c, f) > HATCH_DIFF && lum(f) >= 175 && lum(f) - lum(c) >= 40 }
    }

    /** Écart minimal (par canal) entre un pixel et la même image sans traits fins pour qu'il compte comme trait fin du décor. */
    private const val HATCH_DIFF = 40

    /**
     * Vrai si l'anneau de [reach] px autour des lettres [core] est chargé de décor net : au moins [BUSY_SHARE] de ses pixels sont des traits fins ([hatch]) ou des traits fixes ([fixed], bordure
     * de case, masse sombre voisine). La lueur diffuse n'a pas d'arêtes : un écart au fond propre qui ne retombe pas, dans un tel anneau, est du dessin, pas de la lueur.
     */
    internal fun busyRing(core: BooleanArray, hatch: BooleanArray, fixed: BooleanArray?, w: Int, h: Int, reach: Int): Boolean {
        val ring = TextErase.dilate(core, w, h, reach)
        var n = 0; var b = 0
        for (i in 0 until w * h) {
            if (!ring[i] || core[i]) continue
            n++
            if (hatch[i] || (fixed != null && fixed[i])) b++
        }
        return n >= 100 && b >= BUSY_SHARE * n
    }

    /** Part de l'anneau de recherche occupée par du décor net à partir de laquelle la recherche élargie d'un halo diffus est écartée. */
    private const val BUSY_SHARE = 0.04f

    /** Traits fixes d'une région : [protect] = pixels que l'effacement ne doit ni élargir ni prendre pour références ; [drop] = parmi eux, ceux que la zone de texte mordait par erreur (à retirer du masque). */
    internal class Fixed(val protect: BooleanArray, val drop: BooleanArray?)

    /**
     * Traits fixes de la région, sur un fond dominant [bg] : composantes de pixels différents du fond qui touchent le bord de la région (contour de bulle, bordure de case, trait du
     * décor : elles se prolongent au-delà de la zone) ou dépassent la taille du texte lu ([textRect]), et que les lettres ([ink]) n'effleurent que très peu. Une composante qui porte du
     * texte (lettrage posé sur un dessin, sur un trait : plus de 8 % de ses pixels) n'en fait pas partie : le texte y est effacé comme partout et ses pixels servent de références.
     * Si les lettres n'en prennent presque rien (au plus 1 %, un angle de zone qui mord sur le contour), ces pixels sont retirés du masque (sinon un trou dans le contour) ;
     * sinon le masque reste tel quel mais la marge de sécurité ne grignote pas le trait. Élargis d'un pixel (lissage des bords). Rend null s'il n'y en a pas.
     */
    internal fun fixedStructure(px: IntArray, w: Int, h: Int, bg: Int, thr: Int, textRect: IntArray, ink: BooleanArray): Fixed? {
        val far = TextErase.farMask(px, bg, thr)
        val maxW = ((textRect[2] - textRect[0]) * 1.2f).toInt() + 4; val maxH = ((textRect[3] - textRect[1]) * 1.2f).toInt() + 4
        val seen = BooleanArray(w * h); val keepAll = BooleanArray(w * h); val keepClean = BooleanArray(w * h); val queue = IntArray(w * h)
        var any = false; var anyClean = false
        for (start in 0 until w * h) {
            if (!far[start] || seen[start]) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
            var border = false; var inkN = 0
            var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border = true
                if (ink[i]) inkN++
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (far[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (!(border || x1 - x0 + 1 > maxW || y1 - y0 + 1 > maxH) || inkN > tail / 12) continue
            any = true
            val clean = inkN <= max(8, tail / 100)
            for (k in 0 until tail) { keepAll[queue[k]] = true; if (clean) keepClean[queue[k]] = true }
            if (clean) anyClean = true
        }
        if (!any) return null
        val protect = TextErase.dilate(keepAll, w, h, 1)
        val drop = if (anyClean) TextErase.dilate(keepClean, w, h, 1) else null
        // les pixels de lettres des composantes non propres restent à effacer : ils ne sont pas protégés
        for (i in protect.indices) if (ink[i] && !(drop != null && drop[i])) protect[i] = false
        return Fixed(protect, drop)
    }

    /**
     * Vrai si le texte est un lettrage isolé sur un fond blanc : fond blanc franc, aucun pixel franc des lignes de texte ne fait partie d'une composante qui touche le bord de la région
     * (le lettrage, sa lueur et son contour ne sont soudés à aucun dessin) et le cadre extérieur de la zone est du blanc, sans dessin qu'un aplat découperait en carré.
     */
    private fun isolatedOnWhite(px: IntArray, w: Int, h: Int, a: TextErase.Analysis, lineBoxes: List<RectF>, s: Float, x0: Int, y0: Int, zone: BooleanArray): Boolean {
        val bg = a.bg
        if (((bg shr 16) and 0xFF) < 240 || ((bg shr 8) and 0xFF) < 240 || (bg and 0xFF) < 240 || a.bgShare < 0.45f) return false
        val far = TextErase.farMask(px, bg, a.threshold)
        val textRects = lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
        val text = TextErase.rectMask(w, h, textRects, 0)
        val union = lineBoxes.reduce { x, y -> x.union(y) }
        val enc = TextErase.limitSize(TextErase.enclosedOnly(far, w, h), w, h, (union.width * s * 1.25f).toInt() + 6, (union.height * s * 1.25f).toInt() + 6)
        var inText = 0; var loose = 0
        for (i in far.indices) if (far[i] && text[i]) { inText++; if (!enc[i]) loose++ }
        if (inText < 40 || loose > 0.01f * inText) return false
        // cadre de 6 px autour de la zone : hors traits francs (bordures de case, qui sont conservées), il doit être blanc ; un dégradé ou un dessin pâle y serait coupé en carré par l'aplat
        val outer = TextErase.dilate(zone, w, h, 6)
        var n = 0; var plain = 0
        for (i in far.indices) if (outer[i] && !zone[i] && !far[i]) { n++; if (TextErase.dist(px[i], bg) <= 14) plain++ }
        return n == 0 || plain >= 0.93f * n
    }

    /**
     * Forme libre de l'intérieur de la bulle autour du texte (remplissage borné par le contour, lettres comprises), en pixels de l'image.
     * Région d'analyse plus large que pour le rectangle inscrit : la bulle entière doit y tenir.
     */
    private fun bubbleShape(src: PixelSource, s: Float, union: RectF, bg: Int, imageW: Float, imageH: Float, tol: Int = 42, smooth: Boolean = false, container: RectF? = null, force: List<RectF> = emptyList()): RowShape? {
        val gx = union.width * 1.1f + 20f; val gy = union.height * 1.1f + 20f
        val x0 = (max(0f, union.left - gx) * s).toInt().coerceIn(0, src.width - 1); val y0 = (max(0f, union.top - gy) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + gx) * s).toInt().coerceIn(x0 + 1, src.width); val y1 = (min(imageH, union.bottom + gy) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 16 || w.toLong() * h > 2_000_000L) return null
        val px = src.pixels(x0, y0, w, h)
        val text = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        // bulle ronde détectée à fond non uni : remplissage borné à son ellipse, sans limite de couleur. Un dégradé blanc-noir dépasse l'écart toléré par rapport à la couleur autour du texte
        // (le haut clair de la bulle restait hors de la forme : lignes poussées de côté, forme étroite) ; l'ellipse borne la fuite par le contour flou. Sinon, comportement d'origine.
        var ellipse: BooleanArray? = null
        val bounded: BooleanArray? = if (smooth && container != null && !BubbleVerifier.isRectangular(src, s, container, imageW, imageH)) {
            val cx = (container.left + container.right) / 2f; val cy = (container.top + container.bottom) / 2f
            val rx = container.width / 2f * 0.98f; val ry = container.height / 2f * 0.98f
            val area = BooleanArray(w * h) { i -> val ex = ((x0 + i % w + 0.5f) / s - cx) / rx; val ey = ((y0 + i / w + 0.5f) / s - cy) / ry; ex * ex + ey * ey <= 1f }
            ellipse = area
            BubbleMask.interiorSmooth(px, w, h, text, maxFill = 0.97f, farLimit = 255, allowed = area)
        } else null
        val mask = bounded ?: (if (smooth) BubbleMask.interiorSmooth(px, w, h, text, maxFill = 0.97f) else BubbleMask.interior(px, w, h, text, bg, tol = tol, maxFill = 0.97f)) ?: return null
        // Le texte est dans la bulle par définition : tout ce que contiennent les lignes lues et la zone du détecteur fait partie de l'intérieur. Le remplissage depuis le pourtour du texte
        // laisse en dehors les poches qu'un trait fin relie au contour (une griffure du dessin qui traverse le lettrage) : un mot sombre cerné de noir, peu contrasté avec le fond (« DEMONS. »
        // en rouge sombre) restait alors hors de la forme, donc hors de l'effacement, et réapparaissait sous la traduction. Borné à l'ellipse de la bulle ronde.
        if (smooth && force.isNotEmpty()) {
            for (r in force) {
                val fl = ((r.left * s).toInt() - x0).coerceIn(0, w); val fr = ((r.right * s).toInt() - x0).coerceIn(0, w)
                val ft = ((r.top * s).toInt() - y0).coerceIn(0, h); val fb = ((r.bottom * s).toInt() - y0).coerceIn(0, h)
                for (yy in ft until fb) for (xx in fl until fr) { val i = yy * w + xx; if (ellipse == null || ellipse[i]) mask[i] = true }
            }
        }
        // bulle détectée : le remplissage ne doit pas déborder de sa boîte (fuite vers le décor ou une bulle voisine)
        if (container != null) {
            val c = container.inflate(4f, 4f)
            for (i in mask.indices) if (mask[i]) { val ix = (x0 + i % w + 0.5f) / s; val iy = (y0 + i / w + 0.5f) / s; if (ix < c.left || ix > c.right || iy < c.top || iy > c.bottom) mask[i] = false }
            // fuite : une bulle ronde dont le contour est ouvert (il se fond dans la page) laisse le remplissage couler jusqu'aux coins de la boîte. Si une part
            // du remplissage est hors de l'ellipse de la bulle, on le borne à l'ellipse (légèrement réduite : le texte ne doit pas toucher le contour).
            // Un cartouche rectangulaire (trait ou fond tranché aux quatre coins de la boîte) garde sa forme.
            val cx = (container.left + container.right) / 2f; val cy = (container.top + container.bottom) / 2f
            val total = max(1, mask.count { it })
            var outsideN = 0
            run {
                val rx = container.width / 2f; val ry = container.height / 2f
                for (i in mask.indices) if (mask[i]) { val ex = ((x0 + i % w + 0.5f) / s - cx) / rx; val ey = ((y0 + i / w + 0.5f) / s - cy) / ry; if (ex * ex + ey * ey > 1f) outsideN++ }
            }
            if (outsideN.toFloat() / total > 0.02f && !BubbleVerifier.isRectangular(src, s, container, imageW, imageH)) {
                val rx = container.width / 2f * 0.98f; val ry = container.height / 2f * 0.98f
                for (i in mask.indices) if (mask[i]) {
                    val ex = ((x0 + i % w + 0.5f) / s - cx) / rx; val ey = ((y0 + i / w + 0.5f) / s - cy) / ry
                    if (ex * ex + ey * ey > 1f) mask[i] = false
                }
            }
        }
        val seedX = (text[0] + text[2]) / 2; val seedY = (text[1] + text[3]) / 2
        return RowShape.fromMask(mask, w, h, seedX, seedY, s, x0, y0)
    }

    /** Rectangle inscrit dans la bulle (forme obtenue par remplissage de la zone de même couleur), en pixels de l'image. */
    private fun bubbleArea(src: PixelSource, s: Float, union: RectF, bg: Int, imageW: Float, imageH: Float): RectF? {
        val gx = union.width * 0.7f; val gy = union.height * 0.7f
        val x0 = (max(0f, union.left - gx) * s).toInt().coerceIn(0, src.width - 1); val y0 = (max(0f, union.top - gy) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + gx) * s).toInt().coerceIn(x0 + 1, src.width); val y1 = (min(imageH, union.bottom + gy) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 16 || w.toLong() * h > 1_200_000L) return null
        val px = src.pixels(x0, y0, w, h)
        val text = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        val mask = BubbleMask.compute(px, w, h, text, bg) ?: return null
        val bnd = BubbleMask.bounds(mask, w, h) ?: return null
        val bw = (bnd[2] - bnd[0]).toFloat(); val bh = (bnd[3] - bnd[1]).toFloat()
        val l = (x0 + bnd[0] + bw * 0.146f) / s; val t = (y0 + bnd[1] + bh * 0.146f) / s
        return RectF(l, t, l + bw * 0.708f / s, t + bh * 0.708f / s)
    }
}
