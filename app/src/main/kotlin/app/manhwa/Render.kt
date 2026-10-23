package app.manhwa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import app.manhwa.core.AppSettings
import app.manhwa.core.BubbleMask
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

object PageRenderer {

    private class Prep(
        val block: TranslatedBlock,
        val regionImg: RectF,          // zone d'effacement, en pixels de l'image d'origine
        val pixels: IntArray?,         // pixels de la zone (résolution échantillonnée), null si l'image n'a pas pu être décodée
        val rw: Int, val rh: Int,      // taille de la zone en pixels échantillonnés
        val analysis: TextErase.Analysis?,
        val style: FontStyle,
        val area: RectF,               // zone disponible pour la traduction, en pixels de l'image d'origine
        val lineH: Float,
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
            for (b in blocks) preps.add(prepare(b, bmp, s, iw, ih, medianLine))
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
                patches.put(drawPatch(fonts, bmp, s, p, pl, iw, ih))
                report.put(
                    JSONObject().put("x", pl.box.left.toDouble()).put("y", pl.box.top.toDouble()).put("w", pl.box.width.toDouble()).put("h", pl.box.height.toDouble())
                        .put("t", pl.lines.joinToString("\n")).put("fs", pl.fontSizePx.toDouble()).put("style", p.style.name)
                        .put("flat", (p.analysis?.uniformity ?: 0f) >= 0.85f).put("outlined", p.analysis?.outlined ?: false),
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

    private fun prepare(b: TranslatedBlock, bmp: Bitmap?, s: Float, iw: Float, ih: Float, medianLine: Float): Prep {
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val pad = max(10f, lineH * 0.7f)
        val reg = RectF(max(0f, union.left - pad), max(0f, union.top - pad), min(iw, union.right + pad), min(ih, union.bottom + pad))

        var pixels: IntArray? = null; var an: TextErase.Analysis? = null; var rw = 0; var rh = 0
        if (bmp != null) {
            val x0 = (reg.left * s).toInt().coerceIn(0, bmp.width - 1); val y0 = (reg.top * s).toInt().coerceIn(0, bmp.height - 1)
            val x1 = (reg.right * s).toInt().coerceIn(x0 + 1, bmp.width); val y1 = (reg.bottom * s).toInt().coerceIn(y0 + 1, bmp.height)
            rw = x1 - x0; rh = y1 - y0
            if (rw >= 8 && rh >= 8) {
                val px = IntArray(rw * rh); bmp.getPixels(px, 0, rw, x0, y0, rw, rh)
                fun rects() = lineBoxes.map { l -> intArrayOf(((l.left * s).toInt() - x0), ((l.top * s).toInt() - y0), ((l.right * s).toInt() - x0), ((l.bottom * s).toInt() - y0)) }
                var a = TextErase.analyze(px, rw, rh, TextErase.rectMask(rw, rh, rects(), max(3, (lineH * 0.12f * s).toInt())), grow = max(2, (lineH * 0.06f * s).toInt()))
                if (a.outlined) a = TextErase.analyze(px, rw, rh, TextErase.rectMask(rw, rh, rects(), max(6, (lineH * 0.3f * s).toInt())), grow = max(3, (lineH * 0.1f * s).toInt()))
                pixels = px; an = a
            }
        }
        val flat = (an?.uniformity ?: 0f) >= 0.85f
        val rectangular = flat && lineBoxes.size >= 1 && union.width > 2.2f * union.height && lineBoxes.size <= 2
        val style = TextErase.chooseFont(b.source, lineH, medianLine, flat, an?.outlined ?: false, rectangular)

        // zone disponible : intérieur de la bulle si on peut la détecter, sinon la zone d'origine légèrement agrandie
        var area = RectF(max(0f, union.left - union.width * 0.06f), max(0f, union.top - union.height * 0.08f), min(iw, union.right + union.width * 0.06f), min(ih, union.bottom + union.height * 0.08f))
        if (bmp != null && flat && an != null) bubbleArea(bmp, s, union, an.bg, iw, ih)?.let { inner -> area = area.union(inner) }
        return Prep(b, reg, pixels, rw, rh, an, style, area, lineH)
    }

    /** Rectangle inscrit dans la bulle (forme obtenue par remplissage de la zone de même couleur), en pixels de l'image. */
    private fun bubbleArea(bmp: Bitmap, s: Float, union: RectF, bg: Int, iw: Float, ih: Float): RectF? {
        val gx = union.width * 0.7f; val gy = union.height * 0.7f
        val x0 = (max(0f, union.left - gx) * s).toInt().coerceIn(0, bmp.width - 1); val y0 = (max(0f, union.top - gy) * s).toInt().coerceIn(0, bmp.height - 1)
        val x1 = (min(iw, union.right + gx) * s).toInt().coerceIn(x0 + 1, bmp.width); val y1 = (min(ih, union.bottom + gy) * s).toInt().coerceIn(y0 + 1, bmp.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 16 || w.toLong() * h > 1_200_000L) return null
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, x0, y0, w, h)
        val text = intArrayOf((union.left * s).toInt() - x0, (union.top * s).toInt() - y0, (union.right * s).toInt() - x0, (union.bottom * s).toInt() - y0)
        val mask = BubbleMask.compute(px, w, h, text, bg) ?: return null
        val bnd = BubbleMask.bounds(mask, w, h) ?: return null
        // rectangle inscrit d'une ellipse : ~71 % de la boîte englobante
        val bw = (bnd[2] - bnd[0]).toFloat(); val bh = (bnd[3] - bnd[1]).toFloat()
        val l = (x0 + bnd[0] + bw * 0.146f) / s; val t = (y0 + bnd[1] + bh * 0.146f) / s
        return RectF(l, t, l + bw * 0.708f / s, t + bh * 0.708f / s)
    }

    private fun drawPatch(fonts: Typefaces, bmp: Bitmap?, s: Float, p: Prep, pl: PlacedBlock, iw: Float, ih: Float): JSONObject {
        val rect = p.regionImg.union(pl.box.inflate(4f, 4f)).let { RectF(max(0f, it.left), max(0f, it.top), min(iw, it.right), min(ih, it.bottom)) }
        val pw = max(1, ((rect.right - rect.left) * s).toInt()); val ph = max(1, ((rect.bottom - rect.top) * s).toInt())
        val patch = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val an = p.analysis
        try {
            // 1) effacement du texte d'origine
            if (an != null && p.pixels != null) {
                val fill = TextErase.inpaint(p.pixels, p.rw, p.rh, an.mask, if (an.uniformity >= 0.85f) an.bg else null)
                val layer = IntArray(p.rw * p.rh)
                for (i in layer.indices) if (an.mask[i]) layer[i] = fill[i]
                val ox = ((p.regionImg.left - rect.left) * s).toInt().coerceIn(0, max(0, pw - 1)); val oy = ((p.regionImg.top - rect.top) * s).toInt().coerceIn(0, max(0, ph - 1))
                val cw = min(p.rw, pw - ox); val chh = min(p.rh, ph - oy)
                if (cw > 0 && chh > 0) patch.setPixels(layer, 0, p.rw, ox, oy, cw, chh)
            } else {
                // image non décodée : aplat clair derrière le texte (repli)
                Canvas(patch).drawColor(0xE6FFFFFF.toInt())
            }
            // 2) texte traduit
            val bgForText = an?.bg ?: 0xFFFFFFFF.toInt()
            var fg = an?.textColor ?: ColorMath.textColorFor(bgForText)
            if (kotlin.math.abs(ColorMath.luminance(fg) - ColorMath.luminance(bgForText)) < 0.35f) fg = ColorMath.textColorFor(bgForText)
            val flat = (an?.uniformity ?: 0f) >= 0.85f
            val size = pl.fontSizePx * s
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.of(p.style); textSize = size; textAlign = Paint.Align.CENTER }
            val lineStep = size * 1.15f
            val total = lineStep * pl.lines.size
            val cx = ((pl.box.left + pl.box.right) / 2f - rect.left) * s
            val top0 = ((pl.box.top + pl.box.bottom) / 2f - rect.top) * s - total / 2f
            val fm = paint.fontMetrics
            val canvas = Canvas(patch)
            pl.lines.forEachIndexed { i, line ->
                val base = top0 + i * lineStep + lineStep / 2f - (fm.ascent + fm.descent) / 2f
                if (!flat) { // texte sur dessin : contour de couleur opposée pour rester lisible
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.16f; paint.strokeJoin = Paint.Join.ROUND
                    paint.color = ColorMath.textColorFor(fg).let { if (ColorMath.luminance(fg) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
                    canvas.drawText(line, cx, base, paint)
                }
                paint.style = Paint.Style.FILL; paint.color = fg
                canvas.drawText(line, cx, base, paint)
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
