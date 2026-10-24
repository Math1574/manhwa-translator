package app.manhwa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import app.manhwa.core.AppSettings
import app.manhwa.core.Erasure
import app.manhwa.core.PixelSource
import app.manhwa.core.ColorMath
import app.manhwa.core.DebugInfo
import app.manhwa.core.OverlayLayout
import app.manhwa.core.PageResult
import app.manhwa.core.PlacedBlock
import app.manhwa.core.RectF
import app.manhwa.core.TextErase
import app.manhwa.core.TextErase.FontStyle
import app.manhwa.core.TextMeasurer
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
class Typefaces(ctx: Context) {
    private val speech: Typeface = load(ctx, "fonts/ComicNeue-Bold.ttf", Typeface.DEFAULT_BOLD)
    private val shout: Typeface = load(ctx, "fonts/Bangers-Regular.ttf", Typeface.DEFAULT_BOLD)
    private val narration: Typeface = load(ctx, "fonts/PatrickHand-Regular.ttf", Typeface.DEFAULT_BOLD)
    private val sans: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

    fun of(style: FontStyle): Typeface = when (style) {
        FontStyle.SPEECH -> speech
        FontStyle.SHOUT -> shout
        FontStyle.NARRATION -> narration
        FontStyle.SANS -> sans
    }

    private fun load(ctx: Context, path: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(ctx.assets, path) } catch (e: Throwable) { fallback }
}

/** Adaptateur Bitmap → accès aux pixels pour le moteur d'effacement du cœur. */
private class BitmapSource(private val bmp: Bitmap) : PixelSource {
    override val width: Int get() = bmp.width
    override val height: Int get() = bmp.height
    override fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray = IntArray(w * h).also { bmp.getPixels(it, 0, w, x, y, w, h) }
}

object PageRenderer {

    private class Prep(
        val block: TranslatedBlock,
        val plan: Erasure.Plan?,       // null si l'image n'a pas pu être décodée
        val style: FontStyle,
        val area: RectF,               // zone disponible pour la traduction, en pixels de l'image d'origine
    )

    fun render(fonts: Typefaces, imageBytes: ByteArray, result: PageResult, settings: AppSettings, debug: DebugInfo?): JSONObject {
        val bmp = decodeSampled(imageBytes, result.imageWidth, result.imageHeight)
        val s = if (bmp == null) 1f else bmp.width.toFloat() / result.imageWidth
        val iw = result.imageWidth.toFloat(); val ih = result.imageHeight.toFloat()
        val blocks = result.blocks.filter { it.translated.isNotBlank() }
        val lineHs = blocks.map { lineHeightOf(it) }.sorted()
        val medianLine = if (lineHs.isEmpty()) 0f else lineHs[lineHs.size / 2]

        val preps = ArrayList<Prep>()
        try {
            val src = bmp?.let { BitmapSource(it) }
            for (b in blocks) preps.add(prepare(b, src, s, iw, ih, medianLine))
            if (preps.isEmpty()) return JSONObject().put("w", result.imageWidth).put("h", result.imageHeight).put("patches", JSONArray()).put("blocks", JSONArray())

            // mise en page : tailles de texte et positions, avec la mesure de chaque police
            val measurers = HashMap<Int, TextMeasurer>()
            for (p in preps) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.of(p.style) }
                measurers[p.block.id] = TextMeasurer { text, size -> paint.textSize = size; paint.measureText(text) * 1.04f }
            }
            val fitted = OverlayLayout.place(
                preps.map { it.block.copy(box = it.area) }, result.imageWidth, result.imageHeight, settings.textScale,
                measurers.values.first(), measurerFor = { measurers.getValue(it.id) },
            ).associateBy { it.id }

            val patches = JSONArray(); val report = JSONArray()
            for (p in preps) {
                val pl = fitted[p.block.id] ?: continue
                patches.put(drawPatch(fonts, s, p, pl, iw, ih))
                report.put(
                    JSONObject().put("x", pl.box.left.toDouble()).put("y", pl.box.top.toDouble()).put("w", pl.box.width.toDouble()).put("h", pl.box.height.toDouble())
                        .put("t", pl.lines.joinToString("\n")).put("fs", pl.fontSizePx.toDouble()).put("style", p.style.name)
                        .put("mode", p.plan?.mode?.name ?: "NONE").put("outlined", p.plan?.outlined ?: false),
                )
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

    private fun prepare(b: TranslatedBlock, src: PixelSource?, s: Float, iw: Float, ih: Float, medianLine: Float): Prep {
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val plan = if (src != null) try { Erasure.plan(src, s, lineBoxes, lineH, iw, ih) } catch (e: Throwable) { null } else null
        val flat = plan?.mode == Erasure.Mode.FLAT
        val rectangular = flat && plan?.bubbleArea == null && union.width > 2.2f * union.height && lineBoxes.size <= 2
        val style = TextErase.chooseFont(b.source, lineH, medianLine, flat, plan?.outlined ?: false, rectangular)
        // zone disponible : intérieur de la bulle si on a pu la détecter, sinon la zone d'origine légèrement agrandie
        var area = RectF(max(0f, union.left - union.width * 0.06f), max(0f, union.top - union.height * 0.08f), min(iw, union.right + union.width * 0.06f), min(ih, union.bottom + union.height * 0.08f))
        plan?.bubbleArea?.let { area = area.union(it) }
        return Prep(b, plan, style, area)
    }

    private fun drawPatch(fonts: Typefaces, s: Float, p: Prep, pl: PlacedBlock, iw: Float, ih: Float): JSONObject {
        val plan = p.plan
        // zone du patch : région effacée + emplacement de la traduction, en pixels de l'image d'origine
        var rect = pl.box.inflate(4f, 4f)
        if (plan != null) rect = rect.union(RectF(plan.x0 / s, plan.y0 / s, (plan.x0 + plan.w) / s, (plan.y0 + plan.h) / s))
        rect = RectF(max(0f, rect.left), max(0f, rect.top), min(iw, rect.right), min(ih, rect.bottom))
        val px0 = (rect.left * s).toInt(); val py0 = (rect.top * s).toInt()
        val pw = max(1, ((rect.right * s).toInt() - px0)); val ph = max(1, ((rect.bottom * s).toInt() - py0))
        val patch = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        try {
            // 1) effacement du texte d'origine
            if (plan != null) {
                val ox = plan.x0 - px0; val oy = plan.y0 - py0
                val cx0 = max(0, ox); val cy0 = max(0, oy)
                val cw = min(plan.w - (cx0 - ox), pw - cx0); val chh = min(plan.h - (cy0 - oy), ph - cy0)
                if (cw > 0 && chh > 0) patch.setPixels(plan.layer, (cy0 - oy) * plan.w + (cx0 - ox), plan.w, cx0, cy0, cw, chh)
            } else {
                Canvas(patch).drawColor(0xE6FFFFFF.toInt()) // image non décodée : aplat clair derrière le texte (repli)
            }
            // 2) texte traduit
            val bgForText = plan?.bg ?: 0xFFFFFFFF.toInt()
            var fg = plan?.textColor ?: ColorMath.textColorFor(bgForText)
            if (kotlin.math.abs(ColorMath.luminance(fg) - ColorMath.luminance(bgForText)) < 0.35f) fg = ColorMath.textColorFor(bgForText)
            val flat = plan?.mode != Erasure.Mode.ART
            val size = pl.fontSizePx * s
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.of(p.style); textSize = size; textAlign = Paint.Align.CENTER }
            val lineStep = size * 1.15f
            val total = lineStep * pl.lines.size
            val cxText = ((pl.box.left + pl.box.right) / 2f) * s - px0
            val top0 = ((pl.box.top + pl.box.bottom) / 2f) * s - py0 - total / 2f
            val fm = paint.fontMetrics
            val canvas = Canvas(patch)
            pl.lines.forEachIndexed { i, line ->
                val base = top0 + i * lineStep + lineStep / 2f - (fm.ascent + fm.descent) / 2f
                if (!flat) { // texte sur dessin : contour de couleur opposée pour rester lisible
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.16f; paint.strokeJoin = Paint.Join.ROUND
                    paint.color = if (ColorMath.luminance(fg) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    canvas.drawText(line, cxText, base, paint)
                }
                paint.style = Paint.Style.FILL; paint.color = fg
                canvas.drawText(line, cxText, base, paint)
            }
            val out = ByteArrayOutputStream()
            patch.compress(Bitmap.CompressFormat.PNG, 100, out)
            return JSONObject().put("x", rect.left.toDouble()).put("y", rect.top.toDouble()).put("w", (rect.right - rect.left).toDouble()).put("h", (rect.bottom - rect.top).toDouble())
                .put("png", "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
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
