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
import app.manhwa.core.BarrierShape
import app.manhwa.core.BlockLayout
import app.manhwa.core.BlockPrep
import app.manhwa.core.BubbleVerifier
import app.manhwa.core.LayoutArea
import app.manhwa.core.Erasure
import app.manhwa.core.PixelSource
import app.manhwa.core.PlacedLine
import app.manhwa.core.ColorMath
import app.manhwa.core.DebugInfo
import app.manhwa.core.FontMatcher
import app.manhwa.core.FontLibrary
import app.manhwa.core.RenderEdit
import app.manhwa.core.StyleMatcher
import app.manhwa.core.EditApplier
import app.manhwa.core.GlyphRender
import app.manhwa.core.GlyphRenderer
import app.manhwa.core.PageResult
import app.manhwa.core.ReadingOrder
import app.manhwa.core.RectF
import app.manhwa.core.RenderGate
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
        FontLibrary.spec(id)?.let { return library(it) }
        if (id.startsWith("noto-")) { val p = id.split('-'); return noto(p[1].toInt(), p[2].toFloat()) }
        return familyStatic(id)
    }

    /** Police de la bibliothèque : fichier statique, ou fichier variable avec les valeurs de ses axes (graisse, largeur, inclinaison…). */
    private fun library(sp: FontLibrary.Spec): Typeface = synchronized(cache) {
        cache.getOrPut("lib/${sp.id}") {
            try {
                if (sp.axes.isEmpty()) Typeface.createFromAsset(ctx.assets, "fonts/${sp.file}")
                else Typeface.Builder(ctx.assets, "fonts/${sp.file}").setFontVariationSettings(sp.axes).build() ?: Typeface.DEFAULT_BOLD
            } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
        }
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

/**
 * Aperçu de l'éditeur de correction : couche d'effacement (fond reconstruit sous le texte d'origine) et couche de texte seul, toutes deux à [ss] pixels par pixel de la fenêtre et posées en
 * [rect], mise en page automatique avant décalage ([baseRect]) et centre vers lequel ramener le texte ([target]). L'appelant recycle les bitmaps.
 */
class LayerSink {
    var erase: Bitmap? = null
    var text: Bitmap? = null
    var rect: RectF? = null
    var ss: Float = 1f
    var baseRect: RectF? = null
    var target: Pair<Float, Float>? = null
    var report: JSONObject? = null
    fun recycle() { erase?.recycle(); text?.recycle(); erase = null; text = null }
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

private typealias Prep = BlockPrep

object PageRenderer {
    /** Double résolution pour un texte net quand la page est agrandie à l'écran. */
    private const val SUPERSAMPLE = 2f

    fun render(fonts: Typefaces, imageBytes: ByteArray, result: PageResult, settings: AppSettings, debug: DebugInfo?, fontMemory: StyleMatcher.Memory? = null, series: String? = null,
               edits: Map<Int, RenderEdit> = emptyMap(), only: Int? = null, sink: LayerSink? = null): JSONObject {
        val bmp = decodeSampled(imageBytes, result.imageWidth, result.imageHeight)
        val s = if (bmp == null) 1f else bmp.width.toFloat() / result.imageWidth
        val iw = result.imageWidth.toFloat(); val ih = result.imageHeight.toFloat()
        val blocks = result.blocks.filter { it.translated.isNotBlank() || edits[it.id]?.hidden == true }
        val lineHs = blocks.map { BlockLayout.lineHeightOf(it) }.sorted()
        val medianLine = if (lineHs.isEmpty()) 0f else lineHs[lineHs.size / 2]
        val patches = JSONArray(); val report = JSONArray()
        try {
            val src = bmp?.let { BitmapSource(it) }
            // bulles annoncées par le détecteur et confirmées par un contour visible (le texte libre sur page blanche ou noire n'en a pas)
            val verified = HashMap<Int, RectF?>()
            for (b in blocks) {
                if (only != null && b.id != only) continue            // aperçu de l'éditeur : les bulles voisines gardent la boîte annoncée par le détecteur (plus rapide)
                val bb = b.bubble ?: continue
                val u = b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { x, y -> x.union(y) }
                verified[b.id] = if (src == null) bb else try { if (BubbleVerifier.verify(src, s, bb, u, iw, ih)) bb else null } catch (e: Throwable) { null }
            }
            // cartouche rectangulaire ou bulle ronde : décide si la forme est bornée par l'ellipse de la bulle et comment sont protégées les bulles voisines
            val rectCache = HashMap<RectF, Boolean>()
            val isRect: (RectF) -> Boolean = { bb -> rectCache.getOrPut(bb) { if (src == null) false else try { BubbleVerifier.isRectangular(src, s, bb, iw, ih) } catch (e: Throwable) { false } } }
            // 1) préparation (effacement) et mise en page de chaque bloc
            val preps = ArrayList<Prep>(); val laids = ArrayList<Laid?>()
            var skipped = 0
            for ((bi, b) in blocks.withIndex()) {
                if (only != null && b.id != only) continue            // aperçu de l'éditeur : seul le bloc corrigé est mis en page
                val edit = edits[b.id]
                // « ne pas traduire » : l'image n'est pas touchée ; un patch invisible garde le bloc touchable (appui long) pour pouvoir le rétablir
                if (edit?.hidden == true) { if (sink == null) patches.put(inertPatch(b)); skipped++; continue }
                // traduction identique au texte lu (nom propre, interjection, décor lu comme du texte) : l'image n'est pas touchée (sauf dans l'aperçu de l'éditeur, qui montre toujours le texte)
                if (sink == null && RenderGate.unchanged(b.source, b.translated)) { skipped++; continue }
                // lignes des autres blocs : ce bloc ne doit pas les effacer
                val others = blocks.withIndex().filter { it.index != bi }.flatMap { it.value.lineBoxes.ifEmpty { listOf(it.value.box) } }
                val prep = BlockLayout.prepare(b, src, s, iw, ih, medianLine, settings.target.code, others, blocks, verified, isRect, edit)
                val manual = edit?.manual == true            // zone dessinée par l'utilisateur : elle fait foi, aucune des précautions suivantes ne s'applique
                // texte hors bulle, sans zone du détecteur, sur un dessin : trop risqué (faux texte dans le décor), l'image n'est pas touchée
                if (!manual && RenderGate.tooUncertain(b.kind, b.zone != null, b.bubble != null, prep.plan?.mode == Erasure.Mode.ART, b.source)) { skipped++; continue }
                // filet de sécurité, quelle que soit la méthode d'effacement : une zone effacée très supérieure au texte lu est du dessin qu'on abîmerait, on n'y touche pas
                val pl = prep.plan
                if (!manual && pl != null && pl.mode != Erasure.Mode.SMOOTH) {
                    val textArea = b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { x, y -> x.union(y) }.let { u -> max(1f, u.area * s * s) }
                    var n = 0; for (m in pl.mask) if (m) n++
                    if (n > 10f * textArea) { skipped++; continue }
                }
                preps.add(prep)
            }
            // police : celle de la bibliothèque qui copie le mieux le lettrage d'origine de chaque bloc, de façon cohérente d'un bloc à l'autre (voir StyleMatcher), puis mise en page
            val picks: List<FontPick?> = try { chooseFonts(fonts, preps, s, fontMemory, series) } catch (e: Throwable) { emptyList() }
            // taille : les blocs dont le texte d'origine a la même hauteur de lettre (à 18 % près) reçoivent la même hauteur de référence : la mesure de chacun est bruitée, la taille du texte
            // d'origine ne l'est pas ; deux bulles de même taille d'origine ne doivent pas recevoir des tailles de traduction différentes à cause d'une mesure
            val sizes: FloatArray = try { BlockLayout.harmonizedHeights(preps, s) } catch (e: Throwable) { FloatArray(preps.size) }
            for (i in preps.indices) {
                val pk = picks.getOrNull(i)
                val ed = edits[preps[i].block.id]
                preps[i] = if (ed?.fontId != null) preps[i].withFont(ed.fontId, 0f, "police choisie", sizes[i]) else preps[i].withFont(pk?.id, pk?.slant ?: 0f, pk?.info ?: "", sizes[i])
                var laid = try { layoutBlock(fonts, s, preps[i], iw, ih, settings.textScale, src) } catch (e: Throwable) { null }
                // taille choisie à la main : appliquée à la mise en page automatique, autour de son centre (les bornes de la mise en page automatique l'annulaient presque)
                if (laid != null && ed != null && kotlin.math.abs(ed.scale - 1f) > 0.005f) laid = laid.scaled(ed.scale.coerceIn(0.3f, 3f))
                // position choisie : le texte est décalé (la mise en page automatique reste la référence du « centrer » et de l'aperçu)
                if (sink != null && laid != null) { sink.baseRect = laid.placed(0f); sink.target = EditApplier.centerTarget(preps[i].bubble ?: preps[i].container, preps[i].block.zone, preps[i].block.lineBoxes.ifEmpty { listOf(preps[i].block.box) }.reduce { a, c -> a.union(c) }) }
                if (laid != null && ed != null && (ed.dx != 0f || ed.dy != 0f)) laid = laid.shifted(ed.dx, ed.dy)
                laids.add(laid)
            }
            // 2) collisions : un texte hors bulle (forme libre) dont le texte placé recouvre celui d'un autre bloc est replacé (voir BlockLayout.resolveCollisions)
            val results = ArrayList<BlockLayout.Result?>(laids.map { it?.r })
            BlockLayout.resolveCollisions(preps, results, { pr -> edits[pr.block.id]?.pinned == true }) { pr, k ->
                try { layoutBlock(fonts, s, pr, iw, ih, settings.textScale * k, src)?.r } catch (e: Throwable) { null }
            }
            for (i in laids.indices) { val old = laids[i] ?: continue; val nr = results[i] ?: continue; if (nr !== old.r) laids[i] = Laid(old.font, old.paint, nr) }
            // 3) dessin
            for (i in preps.indices) {
                val laid = laids[i] ?: continue
                // textes traduits des autres blocs : dessinés par-dessus l'effacement de ce bloc quand ils recouvrent son patch (voir paintBlock)
                val neighbours: List<Pair<Prep, Laid>> = if (sink != null) emptyList() else preps.indices.filter { it != i }.mapNotNull { j -> laids[j]?.let { preps[j] to it } }
                val rendered = try { paintBlock(s, preps[i], laid, iw, ih, neighbours, sink) } catch (e: Throwable) { null } ?: continue
                patches.put(rendered.first); report.put(rendered.second)
            }
            val json = JSONObject().put("w", result.imageWidth).put("h", result.imageHeight).put("patches", patches).put("blocks", report).put("skipped", skipped)
            if (debug != null && settings.diagnostic) json.put("dbg", OverlayBuilder.debugJson(debug))
            return json
        } finally {
            bmp?.recycle()
        }
    }

    /** Pixel transparent encodé en PNG : le patch d'un bloc que l'on ne traduit pas (invisible, mais repérable par l'appui long). */
    private val transparentPng: String by lazy {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out); bmp.recycle()
        "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun inertPatch(b: TranslatedBlock): JSONObject {
        val u = (b.zone ?: b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { x, y -> x.union(y) })
        return JSONObject().put("x", u.left.toDouble()).put("y", u.top.toDouble()).put("w", u.width.toDouble()).put("h", u.height.toDouble())
            .put("bid", b.id).put("src", b.source).put("tr", b.translated).put("png", transparentPng).put("inert", true)      // « inert » : rien n'est dessiné ici, la gomme n'a rien à supprimer
    }

    /** Hauteur des capitales / corps de police, mesurée sur la police réelle. */
    private fun capRatio(tf: Typeface): Float {
        val p = Paint().apply { typeface = tf; textSize = 100f }
        val r = Rect(); p.getTextBounds("H", 0, 1, r)
        return (r.height() / 100f).coerceIn(0.4f, 0.95f)
    }

    /** Police retenue pour un bloc par l'appariement de style : identifiant dans la bibliothèque, inclinaison mesurée, résumé pour le journal. */
    private class FontPick(val id: String, val slant: Float, val info: String)

    /** Police de la bibliothèque utilisée quand rien ne peut être mesuré. */
    private const val DEFAULT_FONT = StyleMatcher.DEFAULT

    /**
     * Police de chaque bloc : celle de la bibliothèque (143 candidates libres) dont le style ressemble le plus au lettrage d'origine, mesuré sur son encre (graisse, montants, barres,
     * contraste, orientation des contours), départagée sur le texte réel (forme, largeur), jamais plus grasse que l'original (texte lisible). Les blocs de style semblable reçoivent la même
     * police, ceux d'un autre style (autre personnage, texte hors bulle, lettrage particulier) peuvent en recevoir une autre ; la mémoire de la série garde la police d'un style déjà vu.
     */
    private fun chooseFonts(fonts: Typefaces, preps: List<Prep>, s: Float, memory: StyleMatcher.Memory?, series: String?): List<FontPick?> {
        val blocks = preps.map { p ->
            if (ScriptDetector.dominant(p.block.source) != "en") null            // source CJK : pas de lettrage latin à imiter
            else {
                val lines = BlockLayout.lineInks(p, s)
                if (lines.isEmpty()) null
                else StyleMatcher.describe(lines, if (p.plan?.mode == Erasure.Mode.ART) StyleMatcher.Regime.ART else StyleMatcher.Regime.FLAT)
            }
        }
        val dec = StyleMatcher.decide(blocks, AndroidGlyphRenderer(fonts), memory, series)
        return dec.mapIndexed { i, d ->
            if (d == null || ScriptDetector.dominant(preps[i].block.source) != "en") null
            else {
                val b = blocks[i]; val top = d.ranking.firstOrNull()
                val info = if (b == null) "non mesuré (voisin) · famille ${d.family}" else "famille %s · poids %.3f · dist %.1f · forme %.2f · pén %.2f%s · alt %s".format(
                    d.family, kotlin.math.exp(b.raw[0]), top?.dist ?: 0f, top?.shape ?: 0f, top?.penalty ?: 0f, if (d.fromMemory) " · série" else "",
                    d.ranking.drop(1).take(2).joinToString(",") { it.id })
                FontPick(d.id, b?.slant ?: 0f, info)
            }
        }
    }

    /** Police d'un bloc : sources CJK en Noto calibrée, tout le reste dans la police de la bibliothèque choisie pour lui (sans épaississement ni étirement), droite ou inclinée comme l'original. */
    private fun matchFont(fonts: Typefaces, p: Prep): FontChoice {
        val cjk = ScriptDetector.dominant(p.block.source) != "en"
        if (cjk) {
            val wght = if (p.plan?.outlined ?: false) 800 else 650
            return FontChoice(fonts.noto(wght, 100f), 0f, wght, 100f)
        }
        val fam = p.font ?: if (p.style == FontStyle.SHOUT) "bangers" else DEFAULT_FONT
        val native = FontLibrary.spec(fam)?.italic == true
        val skew = if (!native && p.fontSlant <= -0.10f) p.fontSlant.coerceIn(-0.25f, 0f) else 0f
        return FontChoice(fonts.family(fam), 0f, 700, 100f, skew = skew, family = fam)
    }

    /** Résultat de la mise en page d'un bloc : police, pinceau de mesure, lignes placées (voir BlockLayout). */
    private class Laid(val font: FontChoice, val paint: Paint, val r: BlockLayout.Result) {
        val fit: ShapeFit get() = r.fit
        val art: Boolean get() = r.art
        val allCaps: Boolean get() = r.allCaps
        val em: Float get() = r.em
        val stepRatio: Float get() = r.stepRatio
        val lay: String get() = r.lay
        fun scaled(k: Float): Laid = Laid(font, paint, r.scaled(k))
        fun shifted(dx: Float, dy: Float): Laid = Laid(font, paint, r.shifted(dx, dy))
        fun lineRects(margin: Float): List<RectF> = r.lineRects(margin)
        fun placed(margin: Float): RectF = r.placed(margin)
    }

    private fun layoutBlock(fonts: Typefaces, s: Float, p: Prep, iw: Float, ih: Float, userScale: Float, src: PixelSource? = null): Laid? {
        // police assortie au lettrage d'origine
        val font = matchFont(fonts, p)
        val cap = capRatio(font.typeface)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = font.typeface; letterSpacing = font.letterSpacing; textScaleX = font.scaleX; textSkewX = font.skew
        }
        val measurer = TextMeasurer { text, size -> paint.textSize = size; paint.measureText(text) * 1.02f }
        val r = BlockLayout.layout(p, s, iw, ih, userScale, src, measurer, cap) ?: return null
        return Laid(font, paint, r)
    }

    /** Couleur du texte traduit d'un bloc : celle du texte d'origine, ramenée à une couleur lisible sur le fond réellement obtenu après effacement. */
    private fun textColorOf(p: Prep): Int {
        val plan = p.plan
        // fond réellement obtenu après effacement (et non une estimation) : le texte doit toujours y être lisible
        val bgForText = plan?.let { if (it.fillMean != 0) it.fillMean else it.bg } ?: 0xFFFFFFFF.toInt()
        var fg = plan?.textColor ?: ColorMath.textColorFor(bgForText)
        if (plan?.mode != Erasure.Mode.ART && kotlin.math.abs(ColorMath.luminance(fg) - ColorMath.luminance(bgForText)) < 0.45f) fg = ColorMath.textColorFor(bgForText)
        return fg
    }

    /** Dessine les lignes traduites d'un bloc sur [tc] (patch de rectangle [rect] à [ss] pixels par pixel de l'image) ; rend la couleur utilisée. */
    private fun drawLines(tc: Canvas, p: Prep, laid: Laid, rect: RectF, ss: Float): Int {
        val plan = p.plan; val font = laid.font; val paint = laid.paint; val fit = laid.fit; val art = laid.art; val allCaps = laid.allCaps
        val fg = textColorOf(p)
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
                paint.style = Paint.Style.STROKE; paint.strokeWidth = px * 0.11f; paint.strokeJoin = Paint.Join.ROUND
                paint.color = if (ColorMath.luminance(fg) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                tc.drawText(ln.text, cx, base, paint)
            }
            if (font.embolden > 0f) { paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = font.embolden * px; paint.strokeJoin = Paint.Join.ROUND }
            else { paint.style = Paint.Style.FILL; paint.strokeWidth = 0f }
            // lettrage à lueur (halo coloré sur fond uni) : la traduction reçoit le même halo
            val glow = plan?.glow ?: 0
            if (glow != 0) {
                paint.color = fg
                paint.setShadowLayer(px * 0.32f, 0f, 0f, glow)
                tc.drawText(ln.text, cx, base, paint)
                tc.drawText(ln.text, cx, base, paint)
                paint.clearShadowLayer()
            }
            paint.color = fg
            tc.drawText(ln.text, cx, base, paint)
        }
        return fg
    }

    private fun paintBlock(s: Float, p: Prep, laid: Laid, iw: Float, ih: Float, neighbours: List<Pair<Prep, Laid>> = emptyList(), sink: LayerSink? = null): Pair<JSONObject, JSONObject>? {
        val b = p.block; val plan = p.plan
        val font = laid.font; val fit = laid.fit; val em = laid.em; val stepRatio = laid.stepRatio; val lay = laid.lay

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
        // aperçu de l'éditeur : le texte est dessiné seul, sur une couche transparente à part, pour pouvoir le déplacer sans refaire l'effacement
        val textBmp = if (sink != null) Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888) else null
        var keep = false
        try {
            val canvas = Canvas(patch)
            val tc = if (textBmp != null) Canvas(textBmp) else canvas
            // 1) effacement du texte d'origine (couche à la résolution échantillonnée, agrandie à la double résolution)
            if (plan != null) {
                // l'effacement est complet, même sous le texte traduit d'un autre bloc : ce texte est redessiné par-dessus à l'étape suivante (l'ancienne découpe de la couche d'effacement laissait
                // visibles des morceaux du texte d'origine entre les lettres du voisin)
                val layerPx = plan.layer
                val layerBmp = Bitmap.createBitmap(layerPx, plan.w, plan.h, Bitmap.Config.ARGB_8888)
                try {
                    val dst = android.graphics.RectF((plan.x0 / s - rect.left) * ss, (plan.y0 / s - rect.top) * ss, ((plan.x0 + plan.w) / s - rect.left) * ss, ((plan.y0 + plan.h) / s - rect.top) * ss)
                    canvas.drawBitmap(layerBmp, null, dst, Paint(if (plan.mode == Erasure.Mode.ART) Paint.FILTER_BITMAP_FLAG else 0))
                } finally { layerBmp.recycle() }
            } else {
                canvas.drawColor(0xE6FFFFFF.toInt()) // image non décodée : aplat clair derrière le texte (repli)
            }
            // 2) texte traduit : d'abord celui des blocs voisins qui recouvrent ce patch (leur propre patch les contient aussi : quel que soit l'ordre d'empilement, le résultat est le même),
            //    puis celui de ce bloc
            for ((np, nl) in neighbours) {
                val pr = nl.placed(0f)
                if (pr.right < rect.left || pr.left > rect.right || pr.bottom < rect.top || pr.top > rect.bottom) continue
                drawLines(tc, np, nl, rect, ss)
            }
            val fg = drawLines(tc, p, laid, rect, ss)
            val out = ByteArrayOutputStream()
            if (sink == null) patch.compress(Bitmap.CompressFormat.PNG, 100, out)
            val patchJson = JSONObject().put("x", rect.left.toDouble()).put("y", rect.top.toDouble()).put("w", (rect.right - rect.left).toDouble()).put("h", (rect.bottom - rect.top).toDouble())
                .put("bid", b.id).put("src", b.source).put("tr", b.translated)
                .put("png", "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            val rep = JSONObject().put("bid", b.id).put("x", l.toDouble()).put("y", t.toDouble()).put("w", (r - l).toDouble()).put("h", (bt - t).toDouble())
                .put("t", fit.lines.joinToString("\n") { it.text }).put("fs", fit.size.toDouble()).put("style", p.style.name)
                .put("fg", "#%06x".format(fg and 0xFFFFFF)).put("fillMean", "#%06x".format((plan?.fillMean ?: 0) and 0xFFFFFF)).put("planBg", "#%06x".format((plan?.bg ?: 0) and 0xFFFFFF))
                .put("mode", plan?.mode?.name ?: "NONE").put("outlined", plan?.outlined ?: false)
                .put("fits", fit.fits).put("lines", fit.lines.size).put("shape", plan?.shape != null)
                .put("kind", b.kind).put("bubbleOk", p.bubble != null).put("rectBubble", p.bubbleRect).put("lay", lay)
                .put("erase", plan?.let { pl -> var n = 0; for (m in pl.mask) if (m) n++; Math.round(n / (s * s)) } ?: 0)
                .put("orig", JSONArray(b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { a, c -> a.union(c) }.let { u -> listOf(u.left, u.top, u.right, u.bottom).map { v -> Math.round(v) } }))
                .put("cont", b.bubble?.let { c -> JSONArray(listOf(c.left, c.top, c.right, c.bottom).map { v -> Math.round(v) }) } ?: JSONObject.NULL)
                .put("zoneBox", b.zone?.let { c -> JSONArray(listOf(c.left, c.top, c.right, c.bottom).map { v -> Math.round(v) }) } ?: JSONObject.NULL)
                .put("family", font.family).put("fontInfo", p.fontInfo).put("skew", font.skew.toDouble()).put("embolden", font.embolden.toDouble()).put("scaleX", font.scaleX.toDouble()).put("wght", font.wght).put("wdth", font.wdth.toDouble()).put("em", em.toDouble()).put("stepRatio", stepRatio.toDouble())
            if (sink != null) { sink.erase = patch; sink.text = textBmp; sink.rect = rect; sink.ss = ss; sink.report = rep; keep = true; return JSONObject() to rep }
            return patchJson to rep
        } finally {
            if (!keep) { patch.recycle(); textBmp?.recycle() }
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
