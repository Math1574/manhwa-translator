import app.manhwa.core.*
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.*
import kotlin.random.Random

object Lib {
    val dir = File("/tmp/fontlib/static")
    private val fonts = HashMap<String, Font>()
    private val caps = HashMap<String, Float>()
    fun font(id: String): Font = fonts.getOrPut(id) { Font.createFont(Font.TRUETYPE_FONT, File(dir, "$id.ttf")) }
    fun cap(id: String): Float = caps.getOrPut(id) {
        val gv = font(id).deriveFont(200f).createGlyphVector(FontRenderContext(null, true, true), "H"); (gv.visualBounds.height / 200f).toFloat()
    }
    val frc = FontRenderContext(null, true, true)
}

val SENTENCES = listOf("I WILL NEVER FORGIVE YOU FOR THIS", "WHAT ARE YOU TALKING ABOUT", "THE POWER OF THE HEAVENLY DEMON", "YOU THINK YOU CAN STOP ME", "WE MUST LEAVE THIS PLACE NOW",
    "THREE YEARS AGO I BEGAN TRAINING HERE", "THIS IS NOT EVEN NORMAL, IT IS PATHETIC", "BROTHERS WHO WERE ACCEPTED AT THE SAME TIME", "THE STRONG WILL ADVANCE", "THE WEAK WILL BE ELIMINATED",
    "I HAD A HOME AND A FAMILY", "HAPPINESS WAS JUST BEGINNING", "AFTER SURVIVING COUNTLESS BATTLES", "OVER THE YEARS", "BELIEVE IN DESTINY", "IT WILL LEAD TO DIFFERENT ENDINGS",
    "YOUR DIFFERENT CHOICES", "WHERE DID HE GO", "DON'T LOOK AT ME LIKE THAT", "THE SWORD OF A THOUSAND WINDS", "QUICKLY, GET BACK TO THE VILLAGE", "JUST WAIT AND SEE WHAT HAPPENS",
    "EVERYONE IS WATCHING US", "A SECRET ART HIDDEN FOR CENTURIES", "WHY WOULD THE ELDERS HIDE THIS", "MY BODY FEELS LIGHTER THAN BEFORE", "HAVE YOU EXPECTED SOMETHING ELSE", "SILENCE, THE MASTER IS COMING",
    "THE FIRST DISCIPLE OF THE SECT", "A FEW WORDS FOR THE FALLEN", "NOT EVEN A SINGLE SCRATCH", "LET'S SEE HOW LONG YOU LAST", "THE JOURNEY ENDS HERE", "NO ONE HAS EVER SURVIVED THAT",
    "FIVE HUNDRED YEARS OF WAITING", "PREPARE YOURSELF, JUNIOR BROTHER", "WHO TAUGHT YOU THAT TECHNIQUE", "EXACTLY AS I EXPECTED OF YOU", "THE QUICK BROWN FOX JUMPS", "PACK MY BOX WITH FIVE DOZEN JUGS",
    "SPHINX OF BLACK QUARTZ, JUDGE MY VOW", "HOW VEXINGLY QUICK DAFT ZEBRAS JUMP", "BRIGHT VIXENS JUMP; DOZY FOWL QUACK", "WALTZ, BAD NYMPH, FOR QUICK JIGS VEX", "THE FIVE BOXING WIZARDS JUMP QUICKLY",
    "A LONG TIME AGO IN A DISTANT LAND", "NOTHING CAN SAVE YOU NOW", "I SWEAR ON MY LIFE", "THE GATE WILL OPEN AT DAWN", "STAY BEHIND ME AND DO NOT MOVE", "HE HAS ALREADY REACHED THE MOUNTAIN",
    "MASTER, PLEASE FORGIVE THIS DISCIPLE", "A TRUE WARRIOR NEVER RETREATS", "THE ENEMY IS TOO STRONG FOR US", "LISTEN CAREFULLY TO MY WORDS", "TELL THEM TO GATHER AT THE HALL", "YOU WILL REGRET THIS")

fun renderLine(id: String, text: String, capPx: Float, outlineEm: Float = 0f, gap: Int = 0): BufferedImage {
    val size = capPx / Lib.cap(id)
    val f = Lib.font(id).deriveFont(size)
    val tl = TextLayout(text, f, Lib.frc)
    val b = tl.bounds
    val pad = (capPx * 0.5f + capPx * outlineEm * 2).toInt() + 4
    val w = ceil(b.width).toInt() + 2 * pad; val h = ceil(b.height).toInt() + 2 * pad
    val img = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON); g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.color = Color(if (gap > 0) gap else 255, if (gap > 0) gap else 255, if (gap > 0) gap else 255); g.fillRect(0, 0, w, h)
    val x = pad - b.x.toFloat(); val y = pad - b.y.toFloat()
    val shape = tl.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()))
    if (outlineEm > 0f) { g.color = Color.WHITE; g.stroke = BasicStroke(capPx * outlineEm * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(shape) }
    g.color = Color.BLACK; g.fill(shape)
    g.dispose(); return img
}

fun blur(img: BufferedImage, sigma: Float): BufferedImage {
    if (sigma < 0.15f) return img
    val r = ceil(sigma * 3).toInt(); val n = 2 * r + 1; val k = FloatArray(n) { val x = (it - r).toFloat(); exp(-x * x / (2 * sigma * sigma)) }
    val s = k.sum(); for (i in k.indices) k[i] /= s
    val h = ConvolveOp(Kernel(n, 1, k), ConvolveOp.EDGE_NO_OP, null); val v = ConvolveOp(Kernel(1, n, k), ConvolveOp.EDGE_NO_OP, null)
    return v.filter(h.filter(img, null), null)
}
fun jpeg(img: BufferedImage, q: Float): BufferedImage {
    val w = ImageIO.getImageWritersByFormatName("jpeg").next(); val p = w.defaultWriteParam; p.compressionMode = ImageWriteParam.MODE_EXPLICIT; p.compressionQuality = q
    val bo = ByteArrayOutputStream(); val ios = ImageIO.createImageOutputStream(bo); w.output = ios; w.write(null, IIOImage(img, null, null), p); ios.close(); w.dispose()
    return ImageIO.read(ByteArrayInputStream(bo.toByteArray()))
}
fun noisy(img: BufferedImage, sigma: Float, rnd: Random): BufferedImage {
    if (sigma < 0.5f) return img
    val o = BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_GRAY); val a = img.raster; val b = o.raster
    for (y in 0 until img.height) for (x in 0 until img.width) { val v = a.getSample(x, y, 0) + (rnd.nextDouble() * 2 - 1) * sigma * 1.7; b.setSample(x, y, 0, v.toInt().coerceIn(0, 255)) }
    return o
}
fun otsu(img: BufferedImage): Int {
    val hist = IntArray(256); val r = img.raster; for (y in 0 until img.height) for (x in 0 until img.width) hist[r.getSample(x, y, 0)]++
    val total = img.width * img.height; var sum = 0.0; for (i in 0..255) sum += i * hist[i]
    var sb = 0.0; var wb = 0; var best = 0.0; var th = 128
    for (t in 0..255) { wb += hist[t]; if (wb == 0) continue; val wf = total - wb; if (wf == 0) break; sb += t * hist[t]; val mb = sb / wb; val mf = (sum - sb) / wf; val v = wb.toDouble() * wf * (mb - mf) * (mb - mf); if (v > best) { best = v; th = t } }
    return th
}
fun toInk(img: BufferedImage, text: String, off: Int = 0, dilate: Boolean = false): LineInk? {
    val th = (otsu(img) + off).coerceIn(10, 245); val w = img.width; val h = img.height; val r = img.raster
    var m = BooleanArray(w * h) { r.getSample(it % w, it / w, 0) <= th }
    if (dilate) { val o = m.copyOf(); for (y in 1 until h - 1) for (x in 1 until w - 1) if (!m[y * w + x] && (m[y * w + x - 1] || m[y * w + x + 1] || m[(y - 1) * w + x] || m[(y + 1) * w + x])) o[y * w + x] = true; m = o }
    return FontMatcher.lineInk(m, w, h, 0, 0, w, h, text, pad = 0)
}

class Metric(val sigma: FloatArray, val w: FloatArray) {
    fun d(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) { val x = (a[i] - b[i]) / sigma[i]; s += w[i] * x * x }; return sqrt(s) }
}
