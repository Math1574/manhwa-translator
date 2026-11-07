package app.manhwa

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import app.manhwa.core.Detection
import app.manhwa.core.RectF
import app.manhwa.core.RegionClass
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Détecteur de bulles et de texte de bande dessinée (RT-DETR « comic-text-and-bubble-detector », Apache-2.0, version quantifiée de 11 Mo) :
 * classes 0 bulle, 1 texte dans une bulle, 2 texte hors bulle. Entrée 640×640 étirée (pas de letterbox), sortie en pixels de la tuile.
 * Les pages hautes (webtoons) sont découpées en tuiles de rapport hauteur/largeur au plus 1,5, avec un recouvrement de 15 %.
 */
class ComicDetector(private val ctx: Context, private val env: OrtEnvironment) {
    private var session: OrtSession? = null

    val available: Boolean by lazy {
        try { ctx.assets.open(ASSET).close(); true } catch (e: IOException) { false }
    }

    @Synchronized private fun load(): OrtSession = session ?: run {
        val bytes = ctx.assets.open(ASSET).use { it.readBytes() }
        env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }).also { session = it }
    }

    /** Charge le modèle à l'avance (la première page ne paie plus ce chargement). */
    fun warmUp() { if (available) load() }

    /** Détections sur toute l'image (pixels de l'image d'origine), doublons des recouvrements de tuiles supprimés. */
    fun detect(imageBytes: ByteArray, imageW: Int, imageH: Int): List<Detection> {
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size, false) ?: return emptyList()
        try {
            val tileH = max(640, (imageW * 1.5f).toInt())
            val sample = if (imageW > 1600) 2 else 1
            val all = ArrayList<Detection>()
            var y = 0
            while (true) {
                val end = if (imageH - y <= tileH * 1.05f) imageH else min(imageH, y + tileH)
                val tile = decoder.decodeRegion(Rect(0, y, imageW, end), BitmapFactory.Options().apply { inSampleSize = sample }) ?: break
                try { all += detectTile(tile, sample, y) } finally { tile.recycle() }
                if (end >= imageH) break
                y = end - (tileH * 0.15f).toInt()
            }
            return nms(all, 0.6f)
        } finally { decoder.recycle() }
    }

    @Synchronized private fun detectTile(tile: Bitmap, sample: Int, yOffset: Int): List<Detection> {
        val s = load()
        val scaled = Bitmap.createScaledBitmap(tile, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE); scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== tile) scaled.recycle()
        val plane = SIZE * SIZE
        val data = FloatArray(3 * plane)
        for (i in px.indices) {
            val c = px[i]
            data[i] = ((c shr 16) and 0xFF) / 255f; data[plane + i] = ((c shr 8) and 0xFF) / 255f; data[2 * plane + i] = (c and 0xFF) / 255f
        }
        val tw = tile.width.toLong(); val th = tile.height.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { img ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(tw, th)), longArrayOf(1, 2)).use { size ->
                s.run(mapOf("images" to img, "orig_target_sizes" to size)).use { r ->
                    @Suppress("UNCHECKED_CAST") val labels = (r.get("labels").get().value as Array<LongArray>)[0]
                    @Suppress("UNCHECKED_CAST") val boxes = (r.get("boxes").get().value as Array<Array<FloatArray>>)[0]
                    @Suppress("UNCHECKED_CAST") val scores = (r.get("scores").get().value as Array<FloatArray>)[0]
                    val out = ArrayList<Detection>()
                    for (i in labels.indices) {
                        if (scores[i] < MIN_SCORE) continue
                        val cls = RegionClass.values().getOrNull(labels[i].toInt()) ?: continue
                        val b = boxes[i]
                        val box = RectF(b[0] * sample, b[1] * sample + yOffset, b[2] * sample, b[3] * sample + yOffset)
                        if (box.width < 6f || box.height < 6f) continue
                        out.add(Detection(cls, box, scores[i]))
                    }
                    return out
                }
            }
        }
    }

    private fun nms(ds: List<Detection>, thr: Float): List<Detection> {
        val keep = ArrayList<Detection>()
        for (d in ds.sortedByDescending { it.score }) {
            val dup = keep.any { k ->
                if (k.cls != d.cls) return@any false
                val i = d.box.intersection(k.box)?.area ?: 0f
                i / max(1f, d.box.area + k.box.area - i) > thr
            }
            if (!dup) keep.add(d)
        }
        return keep
    }

    @Synchronized fun close() { try { session?.close() } catch (_: Throwable) {}; session = null }

    private companion object {
        const val ASSET = "detector/detector-v4-s_int8.onnx"
        const val SIZE = 640
        const val MIN_SCORE = 0.3f
    }
}
