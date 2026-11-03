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
    )

    /** Couleur moyenne (RGB opaque) des pixels effectivement remplacés ; 0 s'il n'y en a pas. */
    private fun meanFill(layer: IntArray): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (c in layer) if ((c ushr 24) >= 128) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++ }
        return if (n == 0L) 0 else (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /** @param exclude lignes de texte des AUTRES blocs (pixels de l'image) : un bloc n'efface jamais ce qui appartient à un voisin. */
    fun plan(src: PixelSource, s: Float, lineBoxes: List<RectF>, lineH: Float, imageW: Float, imageH: Float, exclude: List<RectF> = emptyList(), container: RectF? = null, protect: List<RectF> = emptyList(), free: Boolean = false, outlines: List<RectF> = emptyList()): Plan? {
        val p = planUnclipped(src, s, lineBoxes, lineH, imageW, imageH, container = container, protect = protect, free = free, outlines = outlines) ?: return null
        // contour d'une bulle voisine : les traits qui le forment ne sont jamais remplacés, quelle que soit la méthode d'effacement employée
        if (outlines.isNotEmpty()) {
            val px = src.pixels(p.x0, p.y0, p.w, p.h)
            val keep = outlineKeep(px, p.w, p.h, p.mask, p.x0, p.y0, s, outlines)
            for (i in keep.indices) if (keep[i]) { p.mask[i] = false; p.layer[i] = 0 }
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
    private fun planRing(src: PixelSource, s: Float, lineBoxes: List<RectF>, lineH: Float, imageW: Float, imageH: Float, protect: List<RectF>, outlines: List<RectF> = emptyList()): Plan? {
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
            if (near.toFloat() / total >= 0.92f) { chosen = g; bg = (0xFF shl 24) or (mr shl 16) or (mg shl 8) or mb; zone = inner; break }
        }
        val z = zone ?: return null
        val mask = BooleanArray(w * h) { z[it] && !prot[it] }
        if (mask.none { it }) return null
        // bords adoucis : le remplissage se fond dans le fond restant (traces de lueur très faibles au-delà de la zone)
        val alpha = TextErase.featherAlpha(mask, w, h)
        val layer = IntArray(w * h) { if (mask[it]) (alpha[it] shl 24) or (bg and 0xFFFFFF) else 0 }
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
        return Plan(Mode.FLAT, x0, y0, w, h, mask, layer, bg, textColor, false, null, null, TextErase.strokeWidth(ink, w, h), ink, bg and 0xFFFFFF, glow)
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

    private fun planUnclipped(src: PixelSource, s: Float, lineBoxes: List<RectF>, lineH: Float, imageW: Float, imageH: Float, padOverride: Float = 0f, container: RectF? = null, protect: List<RectF> = emptyList(), free: Boolean = false, outlines: List<RectF> = emptyList()): Plan? {
        // texte hors bulle sur des abords unis (page blanche ou noire, aplat, lueur sur fond uni) : toute la zone est remplacée par la couleur des abords
        if (free && padOverride == 0f) planRing(src, s, lineBoxes, lineH, imageW, imageH, protect, outlines)?.let { return it }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val pad = max(max(10f, lineH * 0.7f), padOverride)
        val x0 = (max(0f, union.left - pad) * s).toInt().coerceIn(0, src.width - 1)
        val y0 = (max(0f, union.top - pad) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + pad) * s).toInt().coerceIn(x0 + 1, src.width)
        val y1 = (min(imageH, union.bottom + pad) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 8 || h < 8) return null
        val px = src.pixels(x0, y0, w, h)
        fun rects() = lineBoxes.map { l -> intArrayOf((l.left * s).toInt() - x0, (l.top * s).toInt() - y0, (l.right * s).toInt() - x0, (l.bottom * s).toInt() - y0) }
        val lh = lineH * s

        var a = TextErase.analyze(px, w, h, TextErase.rectMask(w, h, rects(), max(3, (lh * 0.2f).toInt())), grow = max(2, (lh * 0.06f).toInt()))
        if (a.outlined) a = TextErase.analyze(px, w, h, TextErase.rectMask(w, h, rects(), max(6, (lh * 0.3f).toInt())), grow = max(3, (lh * 0.1f).toInt()))

        // bulle : zone de texte sur fond uni ET (abords unis OU contour fermé : le remplissage depuis le texte reste borné)
        val bubble = if (!a.outlined && a.zoneFlat >= 0.9f && a.bgShare >= 0.45f) bubbleArea(src, s, union, a.bg, imageW, imageH) else null
        // un lettrage cerné d'un contour ou d'une lueur est traité comme du dessin : l'aplat laisserait des fragments du contour
        val flat = !a.outlined && a.zoneFlat >= 0.9f && a.bgShare >= 0.45f && (a.uniformity >= 0.85f || bubble != null)
        val textRect = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        val zone = TextErase.rectMask(w, h, rects(), max(4, (lh * 0.35f).toInt()))   // toute la zone du texte, ponctuation et halo compris
        if (flat) {
            val allowedZone = TextErase.rectMask(w, h, rects(), max(3, (lh * 0.2f).toInt()))
            val shrink = max(1, (lh * 0.2f).toInt())
            val core = TextErase.rectMask(w, h, rects().map { r -> intArrayOf(r[0], r[1] + shrink, r[2], r[3] - shrink) }.filter { r -> r[3] > r[1] }, 0)
            val interior = BubbleMask.interior(px, w, h, textRect, a.bg)
            val raw = TextErase.textComponents(px, w, h, a.bg, a.threshold, allowedZone, 0, interior, core)
            val stroke = TextErase.strokeWidth(raw, w, h)
            // nettoyage de toute la zone : plus de « fantômes » (bruit de compression JPEG autour des lettres, points et ponctuation hors boîte OCR)
            val clean = BooleanArray(w * h)
            if (interior != null) { for (i in clean.indices) clean[i] = zone[i] && interior[i] }
            else {
                val far = TextErase.farMask(px, a.bg, a.threshold); val enc = TextErase.enclosedOnly(far, w, h)
                for (i in clean.indices) clean[i] = zone[i] && (!far[i] || enc[i]) // le contour et le dessin (reliés au bord) sont protégés
            }
            val grown = TextErase.dilate(raw, w, h, max(2, (lh * 0.06f).toInt()))
            val mask = BooleanArray(w * h) { clean[it] || grown[it] }
            val layer = IntArray(w * h) { if (mask[it]) (0xFF shl 24) or (a.bg and 0xFFFFFF) else 0 }
            val shape = bubbleShape(src, s, union, a.bg, imageW, imageH, container = container)
            return Plan(Mode.FLAT, x0, y0, w, h, mask, layer, a.bg, a.textColor, a.outlined, bubble, shape, stroke, raw, meanFill(layer))
        }
        // bulle au fond non uni (dégradé, ombre, lueur sombre) : on remplace toute la zone du texte à l'intérieur de la bulle par un fond lissé
        if (!free) run {
            // la forme de la bulle est calculée sur une région plus large que la zone du texte (la bulle entière doit y tenir)
            val shape = bubbleShape(src, s, union, a.bg, imageW, imageH, smooth = true, container = container)
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
                val mask = BooleanArray(w * h) { i -> zone[i] && (shape.contains((x0 + i % w + 0.5f) / s, (y0 + i / w + 0.5f) / s) || encG[i] || (coreLines[i] && farS[i] && !prot[i])) }
                if (mask.count { it } > 0) {
                    // références du remplissage : seulement l'intérieur de la bulle (ni la page blanche autour, ni le contour)
                    val outside = BooleanArray(w * h) { i -> !shape.contains((x0 + i % w + 0.5f) / s, (y0 + i / w + 0.5f) / s) }
                    val fill = TextErase.inpaint(px, w, h, BooleanArray(w * h) { mask[it] || outside[it] })
                    val alpha = TextErase.featherAlpha(mask, w, h)
                    val layer = IntArray(w * h) { if (mask[it]) (alpha[it] shl 24) or (fill[it] and 0xFFFFFF) else 0 }
                    val fc = TextErase.extremeTextColor(px, zone)
                    val ink = TextErase.artMask(px, w, h, zone, fc, 0)
                    return Plan(Mode.SMOOTH, x0, y0, w, h, mask, layer, a.bg, fc, false, null, shape, TextErase.strokeWidth(ink, w, h), ink, meanFill(layer))
                }
            }
        }
        // texte sur dessin : seules les lettres (remplissage + contour) sont effacées et reconstruites, pas un rectangle entier
        val zoneArt = TextErase.rectMask(w, h, rects(), min(if (a.outlined) 30 else 18, max(4, (lh * (if (a.outlined) 0.35f else 0.25f)).toInt())))
        val fillColor = TextErase.extremeTextColor(px, zoneArt)
        // lettrage à lueur : on élargit le masque pour effacer aussi la lueur, de sorte que la reconstruction vienne des abords du halo
        val mask = TextErase.artMask(px, w, h, zoneArt, fillColor, max(2, (lh * (if (a.outlined) 0.14f else 0.07f)).toInt()))
        // texte sur dessin : région élargie pour trouver de la vraie texture à copier à côté du trou (second passage)
        if (padOverride == 0f) planUnclipped(src, s, lineBoxes, lineH, imageW, imageH, max(40f, lineH * 1.6f), container, protect, free, outlines)?.let { if (it.mode == Mode.ART) return it }
        val diffused = TextErase.inpaint(px, w, h, mask)
        // trame de points ou motif répété autour du trou : on recopie le motif plutôt que de le lisser en un voile gris
        val fill = TextErase.periodicFill(px, w, h, mask, diffused) ?: TextErase.addGrain(TextErase.inpaintClone(px, w, h, mask, diffused), px, w, h, mask)
        val alpha = TextErase.featherAlpha(mask, w, h)
        val layer = IntArray(w * h) { if (mask[it]) (alpha[it] shl 24) or (fill[it] and 0xFFFFFF) else 0 }
        val inkArt = TextErase.artMask(px, w, h, zoneArt, fillColor, 0)
        return Plan(Mode.ART, x0, y0, w, h, mask, layer, a.bg, fillColor, a.outlined, null, null, TextErase.strokeWidth(inkArt, w, h), inkArt, meanFill(layer))
    }

    /**
     * Forme libre de l'intérieur de la bulle autour du texte (remplissage borné par le contour, lettres comprises), en pixels de l'image.
     * Région d'analyse plus large que pour le rectangle inscrit : la bulle entière doit y tenir.
     */
    private fun bubbleShape(src: PixelSource, s: Float, union: RectF, bg: Int, imageW: Float, imageH: Float, tol: Int = 42, smooth: Boolean = false, container: RectF? = null): RowShape? {
        val gx = union.width * 1.1f + 20f; val gy = union.height * 1.1f + 20f
        val x0 = (max(0f, union.left - gx) * s).toInt().coerceIn(0, src.width - 1); val y0 = (max(0f, union.top - gy) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, union.right + gx) * s).toInt().coerceIn(x0 + 1, src.width); val y1 = (min(imageH, union.bottom + gy) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 16 || w.toLong() * h > 2_000_000L) return null
        val px = src.pixels(x0, y0, w, h)
        val text = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        val mask = (if (smooth) BubbleMask.interiorSmooth(px, w, h, text, maxFill = 0.97f) else BubbleMask.interior(px, w, h, text, bg, tol = tol, maxFill = 0.97f)) ?: return null
        // bulle détectée : le remplissage ne doit pas déborder de sa boîte (fuite vers le décor ou une bulle voisine)
        if (container != null) {
            val c = container.inflate(4f, 4f)
            for (i in mask.indices) if (mask[i]) { val ix = (x0 + i % w + 0.5f) / s; val iy = (y0 + i / w + 0.5f) / s; if (ix < c.left || ix > c.right || iy < c.top || iy > c.bottom) mask[i] = false }
            // fuite : si le remplissage déborde nettement de l'ellipse de la bulle (trou du contour, queue ouverte), on le borne à l'ellipse ; sinon on garde sa forme
            // exacte (nuage, bulle à pointes, cartouche). Un remplissage qui occupe presque toute la boîte est un rectangle : jamais borné.
            val boxArea = max(1f, container.width * container.height * s * s)
            val fillRatio = mask.count { it } / boxArea
            if (fillRatio < 0.84f) {
                val cx = (container.left + container.right) / 2f; val cy = (container.top + container.bottom) / 2f
                fun outside(scale: Float): Int {
                    val rx = container.width / 2f * scale; val ry = container.height / 2f * scale; var n = 0
                    for (i in mask.indices) if (mask[i]) { val ex = ((x0 + i % w + 0.5f) / s - cx) / rx; val ey = ((y0 + i / w + 0.5f) / s - cy) / ry; if (ex * ex + ey * ey > 1f) n++ }
                    return n
                }
                val total = max(1, mask.count { it })
                if (outside(1.12f).toFloat() / total > 0.05f) {
                    val rx = container.width / 2f * 1.05f; val ry = container.height / 2f * 1.05f
                    for (i in mask.indices) if (mask[i]) {
                        val ex = ((x0 + i % w + 0.5f) / s - cx) / rx; val ey = ((y0 + i / w + 0.5f) / s - cy) / ry
                        if (ex * ex + ey * ey > 1f) mask[i] = false
                    }
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
