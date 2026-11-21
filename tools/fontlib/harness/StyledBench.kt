import app.manhwa.core.*
import java.awt.*
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.*
import kotlin.random.Random

/** Équivalent Java2D de AndroidGlyphRenderer (Render.kt) : le texte est mis à l'échelle pour remplir exactement w × h. */
class JavaGlyphRenderer : GlyphRenderer {
    override fun render(text: String, family: String, embolden: Float, skew: Float, w: Int, h: Int): GlyphRender? {
        val font = try { Lib.font(family) } catch (e: Throwable) { return null }.deriveFont(120f)
        val shape = TextLayout(text, font, Lib.frc).getOutline(null)
        val b = shape.bounds2D
        if (b.width < 2.0 || b.height < 2.0 || w < 4 || h < 4) return null
        val img = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK; g.fillRect(0, 0, w, h)
        g.scale(w / b.width, h / b.height); g.translate(-b.x, -b.y)
        g.color = Color.WHITE; g.fill(shape); g.dispose()
        val r = img.raster
        return GlyphRender(BooleanArray(w * h) { r.getSample(it % w, it / w, 0) > 127 }, ((w / b.width) / (h / b.height)).toFloat())
    }
}

fun main() {
    val k = (System.getProperty("k") ?: "6").toInt(); val seed = (System.getProperty("seed") ?: "5").toInt()
    val which = System.getProperty("which") ?: "regular"            // regular : l'original est une police ordinaire ; styled : une police stylisée
    val leave = System.getProperty("leave") ?: "family"             // none | self | family : candidates privées de l'original ou de toute sa famille
    val out = File(System.getProperty("out") ?: "/tmp/styledbench.csv")
    val renderer = JavaGlyphRenderer()
    val specs = if (which == "styled") FontLibrary.styled else FontLibrary.specs
    val sb = StringBuilder("id,cls,regime,letters,refIou,refScore,flat,best,bestIou,bestGap,bestScore,second,secondIou,passes,regBest,all\n")
    val t0 = System.currentTimeMillis(); var total = 0
    for ((si, s) in specs.withIndex()) {
        val rnd = Random(seed * 1000 + si)
        var got = 0; var tries = 0
        while (got < k && tries < k * 3) {
            tries++
            val sm = makeSample(s.id, rnd) ?: continue
            val W = sm.img.width; val H = sm.img.height
            val plan = try { Erasure.plan(ImgSrcP(sm.img), 1f, sm.rects, sm.pitch, W.toFloat(), H.toFloat(), exclude = emptyList(), container = sm.container, free = sm.container == null, extra = emptyList()) } catch (e: Throwable) { null } ?: continue
            val inks = sm.rects.indices.mapNotNull { StyleMatcher.lineInk(plan, 1f, sm.rects[it], sm.texts[it]) }
            if (inks.isEmpty()) continue
            val regime = if (plan.mode == Erasure.Mode.ART) StyleMatcher.Regime.ART else StyleMatcher.Regime.FLAT
            val blk = StyleMatcher.describe(inks, regime) ?: continue
            fun gone(id: String) = when (leave) { "none" -> false; "self" -> id == s.id; else -> FontLibrary.spec(id)?.file == s.file }
            val regAllowed: (String) -> Boolean = if (which == "regular") { id -> !gone(id) } else { _ -> true }
            val dec = StyleMatcher.decide(listOf(blk), renderer, null, null, regAllowed)[0] ?: continue
            val cands = if (which == "styled") FontLibrary.styled.filter { !gone(it.id) } else FontLibrary.styled
            val v = StyledFonts.evaluate(listOf(blk), dec, renderer, cands) ?: continue
            val b1 = v.cands[0]; val b2 = v.cands.getOrNull(1)
            sb.append("${s.id},${s.cls},${regime.name},${v.letters},%.3f,%.3f,${v.flat},${b1.id},%.3f,%.3f,%.3f,${b2?.id ?: ""},%.3f,${StyledFonts.passes(v)},${dec.id},${v.cands.joinToString("|") { "%s=%.3f/%.3f".format(java.util.Locale.ROOT, it.id, it.iou, it.gap) }}\n".format(
                java.util.Locale.ROOT, v.refIou, v.refScore, b1.iou, b1.gap, b1.score, b2?.iou ?: 0f))
            got++; total++
        }
    }
    out.writeText(sb.toString()); println("$which/$leave : $total échantillons en ${(System.currentTimeMillis() - t0) / 1000} s → $out")
}
