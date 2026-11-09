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

class ImgSrcP(val img: BufferedImage) : PixelSource {
    override val width get() = img.width
    override val height get() = img.height
    override fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray = IntArray(w * h) { img.getRGB((x + it % w).coerceIn(0, img.width - 1), (y + it / w).coerceIn(0, img.height - 1)) or (0xFF shl 24) }
}

fun rgb(r: Int, g: Int, b: Int) = Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

fun background(w: Int, h: Int, type: Int, rnd: Random): BufferedImage {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); val g = img.createGraphics()
    when (type) {
        0 -> { val v = 244 + rnd.nextInt(12); g.color = rgb(v, v, v); g.fillRect(0, 0, w, h) }
        1 -> { val v = 190 + rnd.nextInt(55); val tint = rnd.nextInt(14); g.color = rgb(v + tint, v, v - tint); g.fillRect(0, 0, w, h) }
        2 -> { val v = 150 + rnd.nextInt(70); val period = 2.6 + rnd.nextDouble() * 2.4; val amp = 12 + rnd.nextInt(24)
            for (y in 0 until h) for (x in 0 until w) { val d = (sin(x / period * PI) * sin(y / period * PI)) * amp + (sqrt(-2.0 * ln(1.0 - rnd.nextDouble())) * cos(2 * PI * rnd.nextDouble())) * 5; val c = (v + d).toInt().coerceIn(0, 255); img.setRGB(x, y, (c shl 16) or (c shl 8) or c) } }
        3 -> { val c1 = rgb(40 + rnd.nextInt(180), 40 + rnd.nextInt(180), 40 + rnd.nextInt(180)); val c2 = rgb(40 + rnd.nextInt(180), 40 + rnd.nextInt(180), 40 + rnd.nextInt(180))
            g.paint = GradientPaint(0f, 0f, c1, w.toFloat(), h.toFloat(), c2); g.fillRect(0, 0, w, h)
            repeat(6 + rnd.nextInt(8)) { g.color = Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256), 90 + rnd.nextInt(100)); val bw = 10 + rnd.nextInt(max(11, w / 2)); val bh = 10 + rnd.nextInt(max(11, h / 2)); if (rnd.nextBoolean()) g.fill(Ellipse2D.Float(rnd.nextInt(w).toFloat() - bw / 2, rnd.nextInt(h).toFloat() - bh / 2, bw.toFloat(), bh.toFloat())) else g.fill(Rectangle2D.Float(rnd.nextInt(w).toFloat() - bw / 2, rnd.nextInt(h).toFloat() - bh / 2, bw.toFloat(), bh.toFloat())) } }
        else -> { val v = 15 + rnd.nextInt(70); g.color = rgb(v, v + rnd.nextInt(12), v + rnd.nextInt(30)); g.fillRect(0, 0, w, h) }
    }
    g.dispose(); return img
}

class Sample(val img: BufferedImage, val rects: List<RectF>, val texts: List<String>, val pitch: Float, val container: RectF?, val bgType: Int, val light: Boolean, val outline: Float)

fun makeSample(id: String, rnd: Random): Sample? {
    val n = 2 + rnd.nextInt(3); val cap = 13f + rnd.nextFloat() * 28f
    val mixed = rnd.nextFloat() < 0.4f
    val lines = ArrayList<String>()
    while (lines.size < n) {
        val t = SENTENCES[rnd.nextInt(SENTENCES.size)].let { if (it.length > 26) it.substring(0, it.lastIndexOf(' ', 26).takeIf { i -> i > 8 } ?: 26) else it }
        lines.add(if (mixed) t.lowercase().replaceFirstChar { c -> c.uppercase() } else t)
    }
    val size = cap / Lib.cap(id); val f = Lib.font(id).deriveFont(size)
    val tls = lines.map { TextLayout(it, f, Lib.frc) }; val bs = tls.map { it.bounds }
    val bgType = listOf(0, 0, 1, 2, 2, 3, 3, 4)[rnd.nextInt(8)]
    val light = bgType == 4 || (bgType == 3 && rnd.nextFloat() < 0.35f)
    val outline = if (bgType == 0) 0f else if (rnd.nextFloat() < 0.55f) 0.05f + rnd.nextFloat() * 0.11f else 0f
    val pitch = cap * (1.35f + rnd.nextFloat() * 0.25f)
    val margin = cap * 1.3f + outline * cap * 2
    val W = ceil(bs.maxOf { it.width } + 2 * margin).toInt(); val H = ceil(pitch * n + 2 * margin).toInt()
    val img = background(W, H, bgType, rnd); val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    val fill = if (light) rgb(236 + rnd.nextInt(20), 236 + rnd.nextInt(20), 236 + rnd.nextInt(20)) else rgb(rnd.nextInt(40), rnd.nextInt(40), rnd.nextInt(45))
    val rim = if (light) Color.BLACK else Color.WHITE
    val rects = ArrayList<RectF>()
    for (i in 0 until n) {
        val b = bs[i]; val yc = margin + pitch * (i + 0.5f)
        val tx = W / 2.0 - (b.x + b.width / 2); val ty = yc - (b.y + b.height / 2)
        val shape = tls[i].getOutline(AffineTransform.getTranslateInstance(tx, ty))
        if (outline > 0f) { g.color = rim; g.stroke = BasicStroke(cap * outline * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(shape) }
        g.color = fill; g.fill(shape)
        val jit = { 1f + rnd.nextFloat() * 3f }
        rects.add(RectF((W / 2.0 - b.width / 2).toFloat() - jit(), (yc - b.height / 2).toFloat() - jit(), (W / 2.0 + b.width / 2).toFloat() + jit(), (yc + b.height / 2).toFloat() + jit()))
    }
    g.dispose()
    var out: BufferedImage = img
    val blurS = rnd.nextFloat() * 0.8f
    if (blurS > 0.15f) { // flou léger sur l'image couleur
        val rr = ceil(blurS * 3).toInt(); val nn = 2 * rr + 1; val k = FloatArray(nn) { val x = (it - rr).toFloat(); exp(-x * x / (2 * blurS * blurS)) }; val sm = k.sum(); for (i in k.indices) k[i] /= sm
        out = java.awt.image.ConvolveOp(java.awt.image.Kernel(1, nn, k), java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(java.awt.image.ConvolveOp(java.awt.image.Kernel(nn, 1, k), java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(out, null), null)
    }
    // JPEG (qualité 55–92) sur l'image couleur
    val wr = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next(); val pr = wr.defaultWriteParam; pr.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT; pr.compressionQuality = 0.55f + rnd.nextFloat() * 0.37f
    val bo = java.io.ByteArrayOutputStream(); val ios = javax.imageio.ImageIO.createImageOutputStream(bo); wr.output = ios; wr.write(null, javax.imageio.IIOImage(out, null, null), pr); ios.close(); wr.dispose()
    out = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bo.toByteArray()))
    val cont = if (bgType in 0..2) RectF(3f, 3f, W - 3f, H - 3f) else null
    return Sample(out, rects, lines, pitch, cont, bgType, light, outline)
}

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
