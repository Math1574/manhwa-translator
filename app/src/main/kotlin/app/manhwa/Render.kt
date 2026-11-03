package app.manhwa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import app.manhwa.core.AppSettings
import app.manhwa.core.BubbleVerifier
import app.manhwa.core.LayoutArea
import app.manhwa.core.Erasure
import app.manhwa.core.PixelSource
import app.manhwa.core.ColorMath
import app.manhwa.core.DebugInfo
import app.manhwa.core.FontMatcher
import app.manhwa.core.GlyphRender
import app.manhwa.core.GlyphRenderer
import app.manhwa.core.PageResult
import app.manhwa.core.RectF
import app.manhwa.core.RowShape
import app.manhwa.core.ShapeFit
import app.manhwa.core.ScriptDetector
import app.manhwa.core.ShapeLayout
import app.manhwa.core.TextMeasurer
import app.manhwa.core.TextNormalizer
import app.manhwa.core.Typography
import app.manhwa.core.TextErase
import app.manhwa.core.TextErase.FontStyle
import app.manhwa.core.TranslatedBlock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Rendu des traductions : pour chaque zone de texte, effacement des pixels du texte d'origine (aplat dans les bulles,
 * reconstruction du fond sur les dessins), puis dessin de la traduction avec une police proche du lettrage d'origine.
 * Le résultat est un petit patch PNG par zone, posé exactement sur l'image dans la page.
 */
class Typefaces(private val ctx: Context) {
    private val cache = HashMap<String, Typeface>()

    /** Familles candidates pour la correspondance avec le lettrage d'origine (polices libres SIL OFL). */
    val comicFamilies = listOf(
        "comicneue", "comicneue-italic", "patrickhandsc", "kalam", "londrina", "lilita", "caveat", "anton",
        "noto-500-100", "noto-700-100", "noto-800-100", "noto-700-80", "noto-800-70", // sans-serif propre : lettrage numérique ou texte libre
    )

    fun family(id: String): Typeface {
        if (id.startsWith("noto-")) { val p = id.split('-'); return noto(p[1].toInt(), p[2].toFloat()) }
        return familyStatic(id)
    }

    private fun familyStatic(id: String): Typeface = synchronized(cache) {
        cache.getOrPut(id) {
            val file = when (id) {
                "comicneue" -> "ComicNeue-Bold.ttf"; "comicneue-italic" -> "ComicNeue-BoldItalic.ttf"; "patrickhandsc" -> "PatrickHandSC-Regular.ttf"
                "kalam" -> "Kalam-Bold.ttf"; "bangers" -> "Bangers-Regular.ttf"; "londrina" -> "LondrinaSolid-Black.ttf"
                "lilita" -> "LilitaOne-Regular.ttf"; "caveat" -> "CaveatBrush-Regular.ttf"; "anton" -> "Anton-Regular.ttf"
                else -> "ComicNeue-Bold.ttf"
            }
            try { Typeface.createFromAsset(ctx.assets, "fonts/$file") } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
        }
    }

    fun shout(): Typeface = family("bangers")

    /** Noto Sans variable : épaisseur (wght 100-900) et largeur (wdth 62,5-100) ; pour les sources CJK, sans lettrage latin à imiter. */
    fun noto(wght: Int, wdth: Float): Typeface {
        val w = ((wght + 25) / 50) * 50
        val d = (kotlin.math.round(wdth / 2.5f) * 2.5f).coerceIn(62.5f, 100f)
        return synchronized(cache) {
            cache.getOrPut("noto/$w/$d") {
                try {
                    Typeface.Builder(ctx.assets, "fonts/NotoSans-Variable.ttf")
                        .setFontVariationSettings("'wght' $w, 'wdth' $d").build() ?: Typeface.DEFAULT_BOLD
                } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
            }
        }
    }
}

/** Dessine un texte avec une famille candidate, mis à l'échelle pour remplir w × h : sert à comparer à l'encre d'origine. */
private class AndroidGlyphRenderer(private val fonts: Typefaces) : GlyphRenderer {
    override fun render(text: String, family: String, embolden: Float, skew: Float, w: Int, h: Int): GlyphRender? {
        val size = 120f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.family(family); textSize = size; textSkewX = skew
            if (embolden > 0f) { style = Paint.Style.FILL_AND_STROKE; strokeWidth = embolden * size; strokeJoin = Paint.Join.ROUND }
        }
        val path = android.graphics.Path(); paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        val b = android.graphics.RectF(); path.computeBounds(b, true)
        val stroke = embolden * size
        val bw = b.width() + stroke; val bh = b.height() + stroke
        if (bw < 2f || bh < 2f || w < 4 || h < 4) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            c.scale(w / bw, h / bh); c.translate(-b.left + stroke / 2f, -b.top + stroke / 2f)
            paint.color = 0xFFFFFFFF.toInt(); c.drawText(text, 0f, 0f, paint)
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            return GlyphRender(BooleanArray(w * h) { (px[it] ushr 24) > 127 }, (w / bw) / (h / bh))
        } finally { bmp.recycle() }
    }
}

/** Adaptateur Bitmap → accès aux pixels pour le moteur d'effacement du cœur. */
private class BitmapSource(private val bmp: Bitmap) : PixelSource {
    override val width: Int get() = bmp.width
    override val height: Int get() = bmp.height
    override fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray = IntArray(w * h).also { bmp.getPixels(it, 0, w, x, y, w, h) }
}

/** Police retenue pour un bloc : famille, réglages variables et espacement des lettres. */
private class FontChoice(
    val typeface: Typeface, val letterSpacing: Float, val wght: Int, val wdth: Float,
    val scaleX: Float = 1f, val skew: Float = 0f, val embolden: Float = 0f, val family: String = "noto",
)

object PageRenderer {
    /** Double résolution pour un texte net quand la page est agrandie à l'écran. */
    private const val SUPERSAMPLE = 2f
    private const val CAP_RATIO = 0.714f   // hauteur des capitales / corps, Noto Sans

    private class Prep(
        val block: TranslatedBlock,
        val plan: Erasure.Plan?,       // null si l'image n'a pas pu être décodée
        val style: FontStyle,
        val text: String,              // traduction avec typographie
        val bubble: RectF? = null,     // bulle détectée ET confirmée par son contour ; null = texte hors bulle
        val obstacles: List<RectF> = emptyList(),   // emprises des textes voisins (le texte hors bulle ne doit pas les recouvrir)
        val neighborBubbles: List<RectF> = emptyList(),   // bulles voisines confirmées : un texte hors bulle ne doit pas recouvrir leur contour
        val hardObstacles: List<RectF> = emptyList(),     // texte déjà placé d'autres blocs : toujours évité
    ) {
        fun withHard(h: List<RectF>) = Prep(block, plan, style, text, bubble, obstacles, neighborBubbles, h)
    }

    fun render(fonts: Typefaces, imageBytes: ByteArray, result: PageResult, settings: AppSettings, debug: DebugInfo?): JSONObject {
        val bmp = decodeSampled(imageBytes, result.imageWidth, result.imageHeight)
        val s = if (bmp == null) 1f else bmp.width.toFloat() / result.imageWidth
        val iw = result.imageWidth.toFloat(); val ih = result.imageHeight.toFloat()
        val blocks = result.blocks.filter { it.translated.isNotBlank() }
        val lineHs = blocks.map { lineHeightOf(it) }.sorted()
        val medianLine = if (lineHs.isEmpty()) 0f else lineHs[lineHs.size / 2]
        val patches = JSONArray(); val report = JSONArray()
        try {
            val src = bmp?.let { BitmapSource(it) }
            // bulles annoncées par le détecteur et confirmées par un contour visible (le texte libre sur page blanche ou noire n'en a pas)
            val verified = HashMap<Int, RectF?>()
            for (b in blocks) {
                val bb = b.bubble ?: continue
                val u = b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { x, y -> x.union(y) }
                verified[b.id] = if (src == null) bb else try { if (BubbleVerifier.verify(src, s, bb, u, iw, ih)) bb else null } catch (e: Throwable) { null }
            }
            // 1) préparation (effacement) et mise en page de chaque bloc
            val preps = ArrayList<Prep>(); val laids = ArrayList<Laid?>()
            for ((bi, b) in blocks.withIndex()) {
                // lignes des autres blocs : ce bloc ne doit pas les effacer
                val others = blocks.withIndex().filter { it.index != bi }.flatMap { it.value.lineBoxes.ifEmpty { listOf(it.value.box) } }
                val prep = prepare(b, src, s, iw, ih, medianLine, settings.target.code, others, blocks, verified)
                preps.add(prep)
                laids.add(try { layoutBlock(fonts, s, prep, iw, ih, settings.textScale) } catch (e: Throwable) { null })
            }
            // 2) collisions : un texte hors bulle (forme libre) dont le texte placé recouvre celui d'un autre bloc est replacé en évitant le texte réellement placé
            //    des autres (leur traduction est plus longue que l'original, que chaque bloc évitait seul)
            for (round in 0 until 2) {
                var changed = false
                for (i in preps.indices) {
                    val li = laids[i] ?: continue
                    val pi = preps[i]
                    if (pi.bubble != null || pi.plan?.shape != null) continue            // les bulles gardent leur place : ce sont les textes libres qui s'adaptent
                    val mine = li.placed(2f)
                    val others = preps.indices.filter { it != i }.mapNotNull { j -> laids[j]?.placed(4f) }
                    if (others.none { o -> (mine.intersection(o)?.area ?: 0f) > 0.04f * mine.area }) continue
                    val relaid = try { layoutBlock(fonts, s, pi.withHard(others), iw, ih, settings.textScale) } catch (e: Throwable) { null } ?: continue
                    laids[i] = relaid; changed = true
                }
                if (!changed) break
            }
            // 3) dessin
            for (i in preps.indices) {
                val laid = laids[i] ?: continue
                val rendered = try { paintBlock(s, preps[i], laid, iw, ih) } catch (e: Throwable) { null } ?: continue
                patches.put(rendered.first); report.put(rendered.second)
            }
            val json = JSONObject().put("w", result.imageWidth).put("h", result.imageHeight).put("patches", patches).put("blocks", report)
            if (debug != null && settings.diagnostic) json.put("dbg", OverlayBuilder.debugJson(debug))
            return json
        } finally {
            bmp?.recycle()
        }
    }

    private fun lineHeightOf(b: TranslatedBlock): Float =
        if (b.lineHeight > 0f) b.lineHeight else if (b.vertical) b.box.width else b.box.height

    /** Bulles voisines détectées (celles qui ne contiennent pas ce bloc) : leur contour ne doit pas être entamé par l'effacement. */
    private fun protectOf(b: TranslatedBlock, all: List<TranslatedBlock>, verified: Map<Int, RectF?>): List<RectF> {
        val u = b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { x, y -> x.union(y) }
        return all.filter { it !== b }.mapNotNull { verified[it.id] }.filter { !it.contains(u.centerX, u.centerY) }
    }

    private fun prepare(b: TranslatedBlock, src: PixelSource?, s: Float, iw: Float, ih: Float, medianLine: Float, target: String, others: List<RectF>, blocksAll: List<TranslatedBlock>, verified: Map<Int, RectF?>): Prep {
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val bubble = verified[b.id]
        // zone de texte détectée : tout ce qu'elle contient est effacé, y compris une ligne que la lecture n'a pas trouvée (sinon il en reste un morceau)
        val zone = b.zone?.takeIf { z -> z.area <= 3.5f * union.area && z.area >= 0.8f * union.area && z.contains(union.centerX, union.centerY) }
        // contours des bulles voisines : le texte libre qui les chevauche ne doit pas les entamer
        val neighborBubbles = blocksAll.filter { it !== b }.mapNotNull { verified[it.id] }.filter { nb -> !nb.contains(union.centerX, union.centerY) || bubble == null }
        val plan = if (src != null) try { Erasure.plan(src, s, lineBoxes, lineH, iw, ih, others, bubble, protectOf(b, blocksAll, verified), free = bubble == null, outlines = if (bubble == null) neighborBubbles else emptyList(), extra = listOfNotNull(zone)) } catch (e: Throwable) { null } else null
        val flat = plan?.mode == Erasure.Mode.FLAT
        val rectangular = flat && plan?.shape == null && union.width > 2.2f * union.height && lineBoxes.size <= 2
        // hauteur de ligne pour le choix du style : mesurée sur les lettres (les boîtes d'une ligne inclinée sont gonflées)
        val inkH = measuredInkHeight(plan, b, s)
        val lineHStyle = if (inkH > 0f && !b.vertical) min(lineH, inkH / 0.8f) else lineH
        val style = TextErase.chooseFont(b.source, lineHStyle, medianLine, flat, plan?.outlined ?: false, rectangular)
        val text = if (target == "fr") Typography.french(b.translated) else b.translated
        val obstacles = blocksAll.filter { it !== b }.map { o -> o.lineBoxes.ifEmpty { listOf(o.box) }.reduce { x, y -> x.union(y) } }
        return Prep(b, plan, style, text, bubble, obstacles, neighborBubbles)
    }

    /** Hauteur médiane des lettres d'origine (pixels de l'image d'origine), 0 si inconnue. */
    private fun measuredInkHeight(plan: Erasure.Plan?, b: TranslatedBlock, s: Float): Float {
        val ink = plan?.ink ?: return 0f
        val boxes = b.lineBoxes; val texts = b.lineTexts
        if (boxes.isEmpty() || texts.size != boxes.size) return 0f
        val hs = boxes.indices.mapNotNull { i ->
            val bx = boxes[i]
            FontMatcher.lineInk(ink, plan.w, plan.h, (bx.left * s).toInt() - plan.x0, (bx.top * s).toInt() - plan.y0, (bx.right * s).toInt() - plan.x0, (bx.bottom * s).toInt() - plan.y0, texts[i])?.letterHeight
        }.sorted()
        return if (hs.isEmpty()) 0f else min(hs[hs.size / 2] / s, if (!b.vertical && b.lineHeight > 0f) b.lineHeight / 1.12f else Float.MAX_VALUE)
    }


    /** Hauteur des capitales / corps de police, mesurée sur la police réelle. */
    private fun capRatio(tf: Typeface): Float {
        val p = Paint().apply { typeface = tf; textSize = 100f }
        val r = Rect(); p.getTextBounds("H", 0, 1, r)
        return (r.height() / 100f).coerceIn(0.4f, 0.95f)
    }

    /** Encre de chaque ligne d'origine (recadrée au plus juste) à partir du plan d'effacement. */
    private fun lineInks(p: Prep, s: Float): List<app.manhwa.core.LineInk> {
        val plan = p.plan ?: return emptyList(); val ink = plan.ink ?: return emptyList()
        val boxes = p.block.lineBoxes; val texts = p.block.lineTexts
        if (boxes.isEmpty() || texts.size != boxes.size) return emptyList()
        return boxes.indices.mapNotNull { i ->
            val b = boxes[i]
            FontMatcher.lineInk(ink, plan.w, plan.h, (b.left * s).toInt() - plan.x0, (b.top * s).toInt() - plan.y0, (b.right * s).toInt() - plan.x0, (b.bottom * s).toInt() - plan.y0, texts[i])
        }
    }

    /**
     * Choisit la police qui ressemble le plus au lettrage d'origine : on dessine le texte d'origine avec chaque famille candidate
     * (épaississement, inclinaison réglés ensuite) et on garde la forme qui recouvre le mieux l'encre. Sources CJK : Noto Sans calibrée.
     */
    private fun matchFont(fonts: Typefaces, p: Prep, s: Float, inks: List<app.manhwa.core.LineInk>): FontChoice {
        if (p.style == FontStyle.SHOUT) return FontChoice(fonts.shout(), 0f, 400, 100f, family = "bangers")
        val src = p.block.source
        val cjk = ScriptDetector.dominant(src) != "en"
        val outlined = p.plan?.outlined ?: false
        if (cjk) {
            val wght = if (outlined) 800 else 650
            return FontChoice(fonts.noto(wght, 100f), 0f, wght, 100f)
        }
        val m = if (inks.isNotEmpty()) FontMatcher.best(inks, fonts.comicFamilies, AndroidGlyphRenderer(fonts)) else null
        if (m == null) return FontChoice(fonts.family("comicneue"), 0f, 700, 100f, family = "comicneue")
        // aucune police comique ne ressemble (lettrage très particulier) : police neutre de même graisse plutôt qu'une police manuscrite étirée
        if (m.score < 0.12f) {
            // aucune police comique ne ressemble : on garde l'esprit du lettrage de BD (fin ou gras selon le trait d'origine) plutôt qu'un Noto neutre
            val fam = if (FontMatcher.weightOf(inks) < 600) "patrickhandsc" else "comicneue"
            return FontChoice(fonts.family(fam), 0f, 700, 100f, family = fam)
        }
        return FontChoice(fonts.family(m.family), 0f, 700, 100f, scaleX = m.stretch.coerceIn(0.72f, 1.25f), skew = m.skew, embolden = m.embolden, family = m.family)
    }

    /** Résultat de la mise en page d'un bloc : police, pinceau de mesure, lignes placées. */
    private class Laid(val font: FontChoice, val paint: Paint, val fit: ShapeFit, val art: Boolean, val allCaps: Boolean, val em: Float, val stepRatio: Float) {
        /** Rectangle occupé par le texte placé (lignes) : sert à détecter les collisions entre blocs. */
        fun placed(margin: Float): RectF {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (ln in fit.lines) { l = min(l, ln.cx - ln.width / 2f); r = max(r, ln.cx + ln.width / 2f); t = min(t, ln.top); b = max(b, ln.top + fit.lineStep) }
            return RectF(l - margin, t - margin, r + margin, b + margin)
        }
    }

    private fun layoutBlock(fonts: Typefaces, s: Float, p: Prep, iw: Float, ih: Float, userScale: Float): Laid? {
        val b = p.block; val plan = p.plan
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val allCaps = TextNormalizer.isAllCaps(b.source)

        // police assortie au lettrage d'origine ; corps cible : même hauteur de capitales que l'encre d'origine
        val inks = lineInks(p, s)
        val font = matchFont(fonts, p, s, inks)
        // texte vertical : la taille d'un caractère est la LARGEUR de la colonne (et non sa hauteur, qui est la longueur du texte)
        val inkH = if (inks.isEmpty()) 0f else (if (b.vertical) inks.map { it.w } else inks.map { it.letterHeight }).sorted().let { it[it.size / 2] } / s       // pixels de l'image d'origine
        val cap = capRatio(font.typeface)
        // la hauteur de ligne mesurée dans l'axe du texte borne la hauteur d'encre (lettres collées à un contour ou à une autre ligne)
        val capLimit = if (!b.vertical && lineH > 0f) lineH / 1.12f else Float.MAX_VALUE
        val baseH = if (inkH > 0f) min(inkH, capLimit) else lineH * 0.8f
        val em = (if (allCaps && !b.vertical) baseH / cap else if (b.vertical) baseH / 0.95f else baseH / 0.85f) * userScale.coerceIn(0.5f, 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = font.typeface; letterSpacing = font.letterSpacing; textScaleX = font.scaleX; textSkewX = font.skew
        }
        val measurer = TextMeasurer { text, size -> paint.textSize = size; paint.measureText(text) * 1.02f }

        // interligne d'origine : pas entre deux lignes / corps
        val tops = lineBoxes.map { it.top }.sorted()
        val pitch = if (tops.size >= 2) tops.zipWithNext { a, c -> c - a }.sorted().let { it[it.size / 2] } else 0f
        val stepRatio = if (pitch > 0f && !b.vertical) (pitch / (if (allCaps) baseH / cap else baseH / 0.85f)).coerceIn(1.0f, 1.6f) else 1.18f

        // forme disponible : intérieur de la bulle relevé sur l'image ; sinon ellipse inscrite dans la bulle confirmée ; sinon (texte hors bulle) l'emprise
        // d'origine progressivement agrandie, sans sortir de l'image ni recouvrir un voisin, jusqu'à ce que la traduction y tienne à une taille proche de l'original
        val art = plan?.mode == Erasure.Mode.ART
        val bubbleShape = p.bubble?.let { c ->
            val inset = RectF(c.left + c.width * 0.06f, c.top + c.height * 0.06f, c.right - c.width * 0.06f, c.bottom - c.height * 0.06f)
            if (inset.width >= union.width * 0.9f && inset.height >= union.height * 0.9f) RowShape.ellipse(inset.left, inset.top, inset.right, inset.bottom) else null
        }
        val margin = max(4f, em * 0.14f)
        // majuscules accentuées : l'accent d'une ligne ne doit pas toucher la ligne du dessus
        val accents = TextNormalizer.isAllCaps(p.text) && p.text.any { it in "ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇŒŸ" }
        val step = if (accents) max(stepRatio, 1.14f) else stepRatio
        val centerY = (union.top + union.bottom) / 2f
        val fixedShape = plan?.shape ?: bubbleShape
        val fit: ShapeFit = if (fixedShape != null) {
            ShapeLayout.fit(fixedShape, centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin)
        } else {
            var best: ShapeFit? = null
            for (g in floatArrayOf(if (art) 0.12f else 0.06f, 0.3f, 0.55f, 0.9f)) {
                val area = LayoutArea.expand(union, g, p.obstacles, iw, ih, p.hardObstacles)
                val near = p.neighborBubbles.filter { nb -> nb.left < area.right && nb.right > area.left && nb.top < area.bottom && nb.bottom > area.top }
                // contre une bulle voisine : lignes réparties autour de l'ellipse (forme par rangées) OU bloc compact au-dessous / à côté de la bulle
                val shapes = ArrayList<RowShape>()
                if (near.isEmpty()) shapes.add(RowShape.rect(area.left, area.top, area.right, area.bottom))
                else {
                    val gap = max(6f, em * 0.2f)
                    shapes.add(RowShape.rectAvoiding(area.left, area.top, area.right, area.bottom, near, gap))
                    for (nb in near) for (r in LayoutArea.candidatesAround(area, nb, gap, union.height * 1.4f, iw, ih)) if (near.none { o -> o !== nb && o.left < r.right && o.right > r.left && o.top < r.bottom && o.bottom > r.top }) shapes.add(RowShape.rect(r.left, r.top, r.right, r.bottom))
                }
                for (areaShape in shapes) {
                    val sy = if (centerY in areaShape.top..areaShape.bottom) centerY else (areaShape.top + areaShape.bottom) / 2f
                    val cand = ShapeLayout.fit(areaShape, sy, p.text, measurer, em, max(5f, em * 0.5f), step, margin)
                    if (cand.lines.isEmpty()) continue
                    val cur = best
                    if (cur == null || (cand.fits && !cur.fits) || (cand.fits == cur.fits && cand.size > cur.size * 1.02f)) best = cand
                }
                val bestNow = best
                if (bestNow != null && bestNow.fits && bestNow.size >= em * 0.92f) break
            }
            best ?: ShapeLayout.fit(RowShape.rect(union.left, union.top, union.right, union.bottom), centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin)
        }
        if (fit.lines.isEmpty()) return null
        return Laid(font, paint, fit, art, allCaps, em, stepRatio)
    }

    private fun paintBlock(s: Float, p: Prep, laid: Laid, iw: Float, ih: Float): Pair<JSONObject, JSONObject>? {
        val b = p.block; val plan = p.plan
        val font = laid.font; val paint = laid.paint; val fit = laid.fit; val art = laid.art; val allCaps = laid.allCaps; val em = laid.em; val stepRatio = laid.stepRatio

        // zone du patch : région effacée + toutes les lignes placées
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        for (ln in fit.lines) { l = min(l, ln.cx - ln.width / 2f); r = max(r, ln.cx + ln.width / 2f); t = min(t, ln.top); bt = max(bt, ln.top + fit.lineStep) }
        var rect = RectF(l - fit.size * 0.3f, t - fit.size * 0.3f, r + fit.size * 0.3f, bt + fit.size * 0.3f)
        if (plan != null) rect = rect.union(RectF(plan.x0 / s, plan.y0 / s, (plan.x0 + plan.w) / s, (plan.y0 + plan.h) / s))
        rect = RectF(max(0f, rect.left), max(0f, rect.top), min(iw, rect.right), min(ih, rect.bottom))
        // double résolution pour les patches de taille normale ; réduite pour les très grands blocs afin de limiter mémoire et poids de l'envoi
        val areaImg = (rect.right - rect.left) * (rect.bottom - rect.top)
        val ss = min(SUPERSAMPLE * s, max(s, kotlin.math.sqrt(1_600_000f / max(1f, areaImg))))
        val pw = max(1, ((rect.right - rect.left) * ss).toInt()); val ph = max(1, ((rect.bottom - rect.top) * ss).toInt())
        if (pw.toLong() * ph > 12_000_000L) return null
        val patch = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(patch)
            // 1) effacement du texte d'origine (couche à la résolution échantillonnée, agrandie à la double résolution)
            if (plan != null) {
                val layerBmp = Bitmap.createBitmap(plan.layer, plan.w, plan.h, Bitmap.Config.ARGB_8888)
                try {
                    val dst = android.graphics.RectF((plan.x0 / s - rect.left) * ss, (plan.y0 / s - rect.top) * ss, ((plan.x0 + plan.w) / s - rect.left) * ss, ((plan.y0 + plan.h) / s - rect.top) * ss)
                    canvas.drawBitmap(layerBmp, null, dst, Paint(if (plan.mode == Erasure.Mode.ART) Paint.FILTER_BITMAP_FLAG else 0))
                } finally { layerBmp.recycle() }
            } else {
                canvas.drawColor(0xE6FFFFFF.toInt()) // image non décodée : aplat clair derrière le texte (repli)
            }
            // 2) texte traduit
            // fond réellement obtenu après effacement (et non une estimation) : le texte doit toujours y être lisible
            val bgForText = plan?.let { if (it.fillMean != 0) it.fillMean else it.bg } ?: 0xFFFFFFFF.toInt()
            var fg = plan?.textColor ?: ColorMath.textColorFor(bgForText)
            if (plan?.mode != Erasure.Mode.ART && kotlin.math.abs(ColorMath.luminance(fg) - ColorMath.luminance(bgForText)) < 0.45f) fg = ColorMath.textColorFor(bgForText)
            val px = fit.size * ss
            paint.textSize = px
            val capPx = Rect().also { paint.getTextBounds("H", 0, 1, it) }.height().toFloat()
            val allCapsOut = TextNormalizer.isAllCaps(p.text)
            for (ln in fit.lines) {
                val cx = (ln.cx - rect.left) * ss
                val bandCenter = (ln.top + fit.lineStep / 2f - rect.top) * ss
                val base = bandCenter + (if (allCapsOut || allCaps) capPx / 2f else capPx * 0.5f)
                paint.textAlign = Paint.Align.CENTER
                if (art) { // texte sur dessin : contour de couleur opposée pour rester lisible
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = px * 0.15f; paint.strokeJoin = Paint.Join.ROUND
                    paint.color = if (ColorMath.luminance(fg) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    canvas.drawText(ln.text, cx, base, paint)
                }
                if (font.embolden > 0f) { paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = font.embolden * px; paint.strokeJoin = Paint.Join.ROUND }
                else { paint.style = Paint.Style.FILL; paint.strokeWidth = 0f }
                // lettrage à lueur (halo coloré sur fond uni) : la traduction reçoit le même halo
                val glow = plan?.glow ?: 0
                if (glow != 0) {
                    paint.color = fg
                    paint.setShadowLayer(px * 0.32f, 0f, 0f, glow)
                    canvas.drawText(ln.text, cx, base, paint)
                    canvas.drawText(ln.text, cx, base, paint)
                    paint.clearShadowLayer()
                }
                paint.color = fg
                canvas.drawText(ln.text, cx, base, paint)
            }
            val out = ByteArrayOutputStream()
            patch.compress(Bitmap.CompressFormat.PNG, 100, out)
            val patchJson = JSONObject().put("x", rect.left.toDouble()).put("y", rect.top.toDouble()).put("w", (rect.right - rect.left).toDouble()).put("h", (rect.bottom - rect.top).toDouble())
                .put("bid", b.id).put("src", b.source).put("tr", b.translated)
                .put("png", "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            val rep = JSONObject().put("x", l.toDouble()).put("y", t.toDouble()).put("w", (r - l).toDouble()).put("h", (bt - t).toDouble())
                .put("t", fit.lines.joinToString("\n") { it.text }).put("fs", fit.size.toDouble()).put("style", p.style.name)
                .put("fg", "#%06x".format(fg and 0xFFFFFF)).put("fillMean", "#%06x".format((plan?.fillMean ?: 0) and 0xFFFFFF)).put("planBg", "#%06x".format((plan?.bg ?: 0) and 0xFFFFFF))
                .put("mode", plan?.mode?.name ?: "NONE").put("outlined", plan?.outlined ?: false)
                .put("fits", fit.fits).put("lines", fit.lines.size).put("shape", plan?.shape != null)
                .put("family", font.family).put("skew", font.skew.toDouble()).put("embolden", font.embolden.toDouble()).put("scaleX", font.scaleX.toDouble()).put("wght", font.wght).put("wdth", font.wdth.toDouble()).put("em", em.toDouble()).put("stepRatio", stepRatio.toDouble())
            return patchJson to rep
        } finally {
            patch.recycle()
        }
    }

    /** Image réduite à environ 4 millions de pixels ; null si la mémoire manque. */
    private fun decodeSampled(bytes: ByteArray, w: Int, h: Int): Bitmap? = try {
        var sample = 1
        while ((w.toLong() / sample) * (h.toLong() / sample) > 4_000_000L) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }
}
