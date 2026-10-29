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
import app.manhwa.core.Erasure
import app.manhwa.core.PixelSource
import app.manhwa.core.ColorMath
import app.manhwa.core.DebugInfo
import app.manhwa.core.PageResult
import app.manhwa.core.RectF
import app.manhwa.core.RowShape
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
    private val shoutFace: Typeface = load("fonts/Bangers-Regular.ttf", Typeface.DEFAULT_BOLD)

    fun shout(): Typeface = shoutFace

    /** Noto Sans variable : épaisseur (wght 100-900) et largeur (wdth 62,5-100) réglées pour imiter le lettrage d'origine. */
    fun noto(wght: Int, wdth: Float): Typeface {
        val w = ((wght + 25) / 50) * 50
        val d = (kotlin.math.round(wdth / 2.5f) * 2.5f).coerceIn(62.5f, 100f)
        return synchronized(cache) {
            cache.getOrPut("$w/$d") {
                try {
                    Typeface.Builder(ctx.assets, "fonts/NotoSans-Variable.ttf")
                        .setFontVariationSettings("'wght' $w, 'wdth' $d").build() ?: Typeface.DEFAULT_BOLD
                } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
            }
        }
    }

    private fun load(path: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(ctx.assets, path) } catch (e: Throwable) { fallback }
}

/** Adaptateur Bitmap → accès aux pixels pour le moteur d'effacement du cœur. */
private class BitmapSource(private val bmp: Bitmap) : PixelSource {
    override val width: Int get() = bmp.width
    override val height: Int get() = bmp.height
    override fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray = IntArray(w * h).also { bmp.getPixels(it, 0, w, x, y, w, h) }
}

/** Police retenue pour un bloc : famille, réglages variables et espacement des lettres. */
private class FontChoice(val typeface: Typeface, val letterSpacing: Float, val wght: Int, val wdth: Float)

object PageRenderer {
    /** Double résolution pour un texte net quand la page est agrandie à l'écran. */
    private const val SUPERSAMPLE = 2f
    private const val CAP_RATIO = 0.714f   // hauteur des capitales / corps, Noto Sans

    private class Prep(
        val block: TranslatedBlock,
        val plan: Erasure.Plan?,       // null si l'image n'a pas pu être décodée
        val style: FontStyle,
        val text: String,              // traduction avec typographie
    )

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
            for ((bi, b) in blocks.withIndex()) {
                // lignes des autres blocs : ce bloc ne doit pas les effacer
                val others = blocks.withIndex().filter { it.index != bi }.flatMap { it.value.lineBoxes.ifEmpty { listOf(it.value.box) } }
                val prep = prepare(b, src, s, iw, ih, medianLine, settings.target.code, others)
                val rendered = try { drawBlock(fonts, s, prep, iw, ih, settings.textScale) } catch (e: Throwable) { null } ?: continue
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

    private fun prepare(b: TranslatedBlock, src: PixelSource?, s: Float, iw: Float, ih: Float, medianLine: Float, target: String, others: List<RectF>): Prep {
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val plan = if (src != null) try { Erasure.plan(src, s, lineBoxes, lineH, iw, ih, others) } catch (e: Throwable) { null } else null
        val flat = plan?.mode == Erasure.Mode.FLAT
        val rectangular = flat && plan?.shape == null && union.width > 2.2f * union.height && lineBoxes.size <= 2
        val style = TextErase.chooseFont(b.source, lineH, medianLine, flat, plan?.outlined ?: false, rectangular)
        val text = if (target == "fr") Typography.french(b.translated) else b.translated
        return Prep(b, plan, style, text)
    }

    /** Choisit et règle la police pour approcher le lettrage d'origine : épaisseur du trait, largeur des lettres, espacement. */
    private fun matchFont(fonts: Typefaces, p: Prep, s: Float, em: Float, lineH: Float): FontChoice {
        if (p.style == FontStyle.SHOUT) return FontChoice(fonts.shout(), 0f, 400, 100f)
        val src = p.block.source
        val cjk = ScriptDetector.detect(src) != "en"
        val outlined = p.plan?.outlined ?: false
        var wght = if (outlined) 800 else 600
        if (!cjk && p.plan != null && p.plan.mode == Erasure.Mode.FLAT && p.plan.strokePx > 0f) {
            val capH = lineH * (if (TextNormalizer.isAllCaps(src)) 1f else 0.78f)
            val r = p.plan.strokePx / (capH * s).coerceAtLeast(1f)
            wght = (400 + (r - 0.125f) / (0.20f - 0.125f) * 300f).toInt().coerceIn(300, 900)
        }
        if (cjk) return FontChoice(fonts.noto(wght, 100f), 0f, wght, 100f)
        // largeur : on compare la largeur réelle du texte d'origine à celle de la même chaîne en Noto Sans (normal et condensé)
        val origWidth = p.block.lineBoxes.ifEmpty { listOf(p.block.box) }.sumOf { it.width.toDouble() }.toFloat()
        val oneLine = src.replace(Regex("\\s+"), " ").trim()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = em }
        paint.typeface = fonts.noto(wght, 100f); val w100 = paint.measureText(oneLine)
        paint.typeface = fonts.noto(wght, 62.5f); val w62 = paint.measureText(oneLine)
        var wdth = 100f; var spacing = 0f
        if (w100 > 0f && w100 > w62 + 1f) {
            if (origWidth <= w100) wdth = (62.5f + 37.5f * (origWidth - w62) / (w100 - w62)).coerceIn(62.5f, 100f)
            else spacing = ((origWidth - w100) / (oneLine.length.coerceAtLeast(1) * em)).coerceIn(0f, 0.12f)
        }
        return FontChoice(fonts.noto(wght, wdth), spacing, wght, wdth)
    }

    private fun drawBlock(fonts: Typefaces, s: Float, p: Prep, iw: Float, ih: Float, userScale: Float): Pair<JSONObject, JSONObject>? {
        val b = p.block; val plan = p.plan
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val allCaps = TextNormalizer.isAllCaps(b.source)

        // corps cible : même hauteur de capitales (ou même emprise) que le texte d'origine
        val em = (if (allCaps) lineH / CAP_RATIO else lineH / 0.95f) * userScale.coerceIn(0.5f, 2f)
        val font = matchFont(fonts, p, s, em, lineH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply { typeface = font.typeface; letterSpacing = font.letterSpacing }
        val measurer = TextMeasurer { text, size -> paint.textSize = size; paint.measureText(text) * 1.02f }

        // interligne d'origine : pas entre deux lignes / corps
        val tops = lineBoxes.map { it.top }.sorted()
        val pitch = if (tops.size >= 2) tops.zipWithNext { a, c -> c - a }.sorted().let { it[it.size / 2] } else 0f
        val stepRatio = if (pitch > 0f) (pitch / (if (allCaps) lineH / CAP_RATIO else lineH / 0.95f)).coerceIn(1.0f, 1.5f) else 1.18f

        // forme disponible : intérieur de la bulle si détecté ; sinon l'emprise d'origine légèrement élargie
        val grow = if (plan?.mode == Erasure.Mode.ART) 0.12f else 0.06f
        val rectArea = RectF(max(0f, union.left - union.width * grow), max(0f, union.top - union.height * grow), min(iw, union.right + union.width * grow), min(ih, union.bottom + union.height * grow))
        val shape = plan?.shape ?: RowShape.rect(rectArea.left, rectArea.top, rectArea.right, rectArea.bottom)
        val margin = max(3f, em * 0.12f)
        val fit = ShapeLayout.fit(shape, (union.top + union.bottom) / 2f, p.text, measurer, em, max(5f, em * 0.4f), stepRatio, margin)
        if (fit.lines.isEmpty()) return null

        // zone du patch : région effacée + toutes les lignes placées
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        for (ln in fit.lines) { l = min(l, ln.cx - ln.width / 2f); r = max(r, ln.cx + ln.width / 2f); t = min(t, ln.top); bt = max(bt, ln.top + fit.lineStep) }
        var rect = RectF(l - fit.size * 0.3f, t - fit.size * 0.3f, r + fit.size * 0.3f, bt + fit.size * 0.3f)
        if (plan != null) rect = rect.union(RectF(plan.x0 / s, plan.y0 / s, (plan.x0 + plan.w) / s, (plan.y0 + plan.h) / s))
        rect = RectF(max(0f, rect.left), max(0f, rect.top), min(iw, rect.right), min(ih, rect.bottom))
        val ss = SUPERSAMPLE * s
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
            val bgForText = plan?.bg ?: 0xFFFFFFFF.toInt()
            var fg = plan?.textColor ?: ColorMath.textColorFor(bgForText)
            if (plan?.mode != Erasure.Mode.ART && kotlin.math.abs(ColorMath.luminance(fg) - ColorMath.luminance(bgForText)) < 0.35f) fg = ColorMath.textColorFor(bgForText)
            val art = plan?.mode == Erasure.Mode.ART
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
                paint.style = Paint.Style.FILL; paint.strokeWidth = 0f; paint.color = fg
                canvas.drawText(ln.text, cx, base, paint)
            }
            val out = ByteArrayOutputStream()
            patch.compress(Bitmap.CompressFormat.PNG, 100, out)
            val patchJson = JSONObject().put("x", rect.left.toDouble()).put("y", rect.top.toDouble()).put("w", (rect.right - rect.left).toDouble()).put("h", (rect.bottom - rect.top).toDouble())
                .put("png", "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            val rep = JSONObject().put("x", l.toDouble()).put("y", t.toDouble()).put("w", (r - l).toDouble()).put("h", (bt - t).toDouble())
                .put("t", fit.lines.joinToString("\n") { it.text }).put("fs", fit.size.toDouble()).put("style", p.style.name)
                .put("mode", plan?.mode?.name ?: "NONE").put("outlined", plan?.outlined ?: false)
                .put("fits", fit.fits).put("lines", fit.lines.size).put("shape", plan?.shape != null)
                .put("wght", font.wght).put("wdth", font.wdth.toDouble()).put("em", em.toDouble()).put("stepRatio", stepRatio.toDouble())
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
