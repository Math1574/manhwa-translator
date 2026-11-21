import app.manhwa.core.*
import java.awt.*
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.*
import kotlin.random.Random

fun main() {
    val k = (System.getProperty("k") ?: "20").toInt(); val seed = (System.getProperty("seed") ?: "5").toInt(); val out = File(System.getProperty("out") ?: "/tmp/demo/pipe.csv")
    val from = (System.getProperty("from") ?: "0").toInt(); val to = (System.getProperty("to") ?: "${FontLibrary.specs.size}").toInt()
    val sb = StringBuilder(); var t0 = System.currentTimeMillis(); var done = 0; var tot = 0
    for ((si, s) in FontLibrary.specs.withIndex()) {
        if (si < from || si >= to) continue
        val rnd = Random(seed * 1000 + si)
        var got = 0; var tries = 0
        while (got < k && tries < k * 3) {
            tries++
            val sm = makeSample(s.id, rnd) ?: continue
            val W = sm.img.width; val H = sm.img.height
            val plan = try { Erasure.plan(ImgSrcP(sm.img), 1f, sm.rects, sm.pitch, W.toFloat(), H.toFloat(), exclude = emptyList(), container = sm.container, free = sm.container == null, extra = emptyList()) } catch (e: Throwable) { null } ?: continue
            val inks = sm.rects.indices.mapNotNull { StyleMatcher.lineInk(plan, 1f, sm.rects[it], sm.texts[it]) }
            val v = StyleFeatures.mean(inks.mapNotNull { StyleFeatures.of(it) }) ?: continue
            sb.append(s.id).append(',').append(s.cls).append(',').append(sm.pitch.toInt()).append(',').append(v.v.joinToString(",") { "%.5f".format(it) }).append(",").append(plan.mode.name).append(",").append(sm.bgType).append('\n'); got++
        }
        done++; tot += got
    }
    out.writeText(sb.toString()); println("échantillons : $tot pour $done polices en ${(System.currentTimeMillis() - t0) / 1000} s → $out")
}
