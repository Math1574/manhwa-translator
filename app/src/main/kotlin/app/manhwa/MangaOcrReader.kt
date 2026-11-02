package app.manhwa

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import app.manhwa.core.MangaOcrDecoder
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Lecture du japonais vertical (et du texte manga stylisé) par manga-ocr, version quantifiée en entiers 8 bits (encodeur 87 Mo, décodeur 30 Mo).
 * Les modèles sont copiés une fois dans le cache de l'application puis chargés depuis le fichier (pas de copie de 87 Mo en mémoire).
 */
class MangaOcrReader(private val ctx: Context, private val env: OrtEnvironment) {
    private var enc: OrtSession? = null
    private var dec: OrtSession? = null
    private var vocab: List<String> = emptyList()

    val available: Boolean by lazy {
        try { listOf("ocr/manga_ocr_encoder_int8.onnx", "ocr/manga_ocr_decoder_int8.onnx", "ocr/manga_ocr_vocab.txt").forEach { ctx.assets.open(it).close() }; true } catch (e: IOException) { false }
    }

    private fun fileFromAsset(asset: String): File {
        val stamp = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime } catch (e: Throwable) { 0L }
        val dir = File(ctx.cacheDir, "models").apply { mkdirs() }
        val name = asset.substringAfterLast('/')
        val target = File(dir, "$name.$stamp")
        if (!target.exists() || target.length() == 0L) {
            dir.listFiles()?.filter { it.name.startsWith("$name.") }?.forEach { it.delete() }
            val tmp = File(dir, "$name.part")
            ctx.assets.open(asset).use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
            tmp.renameTo(target)
        }
        return target
    }

    @Synchronized private fun load() {
        if (enc != null) return
        val opts = { OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) } }
        enc = env.createSession(fileFromAsset("ocr/manga_ocr_encoder_int8.onnx").absolutePath, opts())
        dec = env.createSession(fileFromAsset("ocr/manga_ocr_decoder_int8.onnx").absolutePath, opts())
        vocab = ctx.assets.open("ocr/manga_ocr_vocab.txt").bufferedReader(Charsets.UTF_8).readLines()
    }

    /** Texte lu et confiance moyenne (probabilité moyenne des jetons choisis) ; null en cas d'échec. */
    @Synchronized fun read(crop: Bitmap): Pair<String, Float>? {
        load()
        val scaled = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE); scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== crop) scaled.recycle()
        val plane = SIZE * SIZE
        val data = FloatArray(3 * plane)
        for (i in px.indices) {
            val c = px[i]
            val g = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
            val v = (g - 0.5f) / 0.5f           // niveaux de gris recopiés sur trois canaux, normalisation moyenne 0,5 / écart-type 0,5
            data[i] = v; data[plane + i] = v; data[2 * plane + i] = v
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { input ->
            enc!!.run(mapOf("pixel_values" to input)).use { eres ->
                val hidden = eres.get(0) as OnnxTensor
                val r = MangaOcrDecoder.greedy({ ids -> step(ids, hidden) })
                return MangaOcrDecoder.text(r.ids, vocab) to r.meanProb
            }
        }
    }

    private fun step(ids: List<Int>, hidden: OnnxTensor): FloatArray {
        val arr = LongArray(ids.size) { ids[it].toLong() }
        OnnxTensor.createTensor(env, LongBuffer.wrap(arr), longArrayOf(1, arr.size.toLong())).use { idt ->
            dec!!.run(mapOf("input_ids" to idt, "encoder_hidden_states" to hidden)).use { r ->
                val t = r.get(0) as OnnxTensor
                val vs = t.info.shape[2].toInt()
                val fb = t.floatBuffer
                val off = (ids.size - 1) * vs
                return FloatArray(vs) { fb.get(off + it) }
            }
        }
    }

    @Synchronized fun close() {
        try { enc?.close() } catch (_: Throwable) {}
        try { dec?.close() } catch (_: Throwable) {}
        enc = null; dec = null
    }

    private companion object { const val SIZE = 224 }
}
