package app.manhwa

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.Log
import app.manhwa.core.Ctc
import app.manhwa.core.DbPost
import app.manhwa.core.OcrEngine
import app.manhwa.core.OcrLine
import app.manhwa.core.VerticalColumns
import app.manhwa.core.OcrMerge
import app.manhwa.core.OcrResult
import app.manhwa.core.RectF
import app.manhwa.core.ScriptDetector
import app.manhwa.core.SourceLang
import java.io.IOException
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/** Modèles PaddleOCR (détection + lecture par écriture) chargés à la demande depuis les assets de l'application. */
/** Dernières statistiques de lecture (pour le rapport des tests) : colonnes verticales vues, lues par manga-ocr, première erreur. */
object OcrDebug {
    @Volatile var note: String = ""
}

class PaddleModels(private val ctx: Context) {
    class Rec(val session: OrtSession, val charset: List<String>, val imgH: Int)

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var det: OrtSession? = null
    private val recs = HashMap<String, Rec>()

    /** Faux si les modèles n'ont pas été inclus dans l'APK (compilation locale sans téléchargement des modèles). */
    val available: Boolean by lazy {
        try { ctx.assets.open("ocr/ch_PP-OCRv4_det_infer.onnx").close(); true } catch (e: IOException) { false }
    }

    val environment: OrtEnvironment get() = env

    /** Détecteur de bulles et de texte hors bulle (zones servant d'ancres au regroupement des lignes). */
    val detector: ComicDetector by lazy { ComicDetector(ctx, env) }

    /** Lecture du japonais vertical (manga-ocr) : appelée pour les colonnes que la lecture horizontale ne sait pas traiter. */
    val manga: MangaOcrReader by lazy { MangaOcrReader(ctx, env) }

    private fun session(asset: String): OrtSession {
        val bytes = ctx.assets.open(asset).use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
        return env.createSession(bytes, opts)
    }

    private fun lines(asset: String): List<String> = ctx.assets.open(asset).bufferedReader(Charsets.UTF_8).readLines()

    @Synchronized fun detector(): OrtSession = det ?: session("ocr/ch_PP-OCRv4_det_infer.onnx").also { det = it }

    @Synchronized fun recognizer(lang: String): Rec = recs.getOrPut(lang) {
        when (lang) {
            // coréen : PP-OCRv5 (erreur de caractères mesurée 3 % contre 27 % pour l'ancien modèle sur ma page de test), ancien modèle en repli
            "ko" -> try { Rec(session("ocr/korean_PP-OCRv5_rec_infer.onnx"), Ctc.charset(lines("ocr/ko_v5_dict.txt"), true), 48) }
                    catch (e: Throwable) { Rec(session("ocr/korean_mobile_v2.0_rec_infer.onnx"), Ctc.charset(lines("ocr/korean_dict.txt"), false), 32) }
            // japonais : lecteur unifié PP-OCRv5 (chinois, japonais, anglais) : 0 % d'erreur de caractères contre 3 % pour l'ancien modèle sur mes dix lignes de test ;
            // l'ancien modèle reste en repli si le nouveau n'est pas dans l'APK
            "ja" -> try { Rec(session("ocr/ppocrv5_unified_rec_infer.onnx"), Ctc.charset(lines("ocr/ppocrv5_unified_dict.txt"), true), 48) }
                    catch (e: Throwable) { Rec(session("ocr/japan_rec_crnn.onnx"), Ctc.charset(lines("ocr/japan_dict.txt"), false), 32) }
            "zh" -> Rec(session("ocr/ch_PP-OCRv4_rec_infer.onnx"), Ctc.charset(lines("ocr/ppocr_keys_v1.txt"), true), 48)
            else -> Rec(session("ocr/en_PP-OCRv3_rec_infer.onnx"), Ctc.charset(lines("ocr/en_dict.txt"), true), 48)
        }
    }

    private var altEn: Rec? = null
    private var altEnChecked = false

    /**
     * Second reconnaisseur anglais (PP-OCRv5, Apache-2.0) en concurrent du v3 : pour chaque ligne on garde la lecture la plus sûre.
     * Sur mes lignes de référence il fait baisser l'erreur de caractères de 0,8 % à 0,5 % sans régression. Null si le modèle n'est pas dans l'APK
     * ou pour les autres écritures.
     */
    @Synchronized fun altRecognizer(lang: String): Rec? {
        if (lang == "ko" || lang == "ja" || lang == "zh") return null
        if (!altEnChecked) {
            altEnChecked = true
            altEn = try { Rec(session("ocr/en_PP-OCRv5_rec_infer.onnx"), Ctc.charset(lines("ocr/en_v5_dict.txt"), true), 48) } catch (e: Throwable) { null }
        }
        return altEn
    }

    @Synchronized fun close() {
        try { manga.close() } catch (_: Throwable) {}
        try { detector.close() } catch (_: Throwable) {}
        try { altEn?.session?.close() } catch (_: Throwable) {}
        altEn = null; altEnChecked = false
        try { det?.close() } catch (_: Throwable) {}
        recs.values.forEach { try { it.session.close() } catch (_: Throwable) {} }
        det = null; recs.clear()
    }
}

/**
 * Lecture de texte PaddleOCR : détection des lignes (robuste aux lettrages stylisés et au texte hors bulles),
 * puis lecture de chaque ligne. Les longues images sont découpées en tuiles.
 */
class PaddleOcr(private val models: PaddleModels) {

    /** Zones détectées (bulles, texte dans une bulle, texte hors bulle) ; liste vide si le modèle n'est pas dans l'APK. */
    fun detectRegions(imageBytes: ByteArray, imageW: Int, imageH: Int): List<app.manhwa.core.Detection> =
        if (models.detector.available) models.detector.detect(imageBytes, imageW, imageH) else emptyList()

    fun recognize(imageBytes: ByteArray, lang: String, imageW: Int, imageH: Int): List<OcrLine> {
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size, false) ?: return emptyList()
        val out = ArrayList<OcrLine>()
        try {
            val sample = if (imageW > 1600) 2 else 1
            var y = 0
            while (true) {
                val end = min(imageH, y + TILE_H)
                val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val tile = decoder.decodeRegion(Rect(0, y, imageW, end), opts) ?: throw IOException("tuile illisible")
                try {
                    // zone de la tuile qui lui appartient : on écarte le recouvrement avec ses voisines pour ne pas détecter deux fois une même ligne
                    val keepTop = if (y == 0) -1f else y + TILE_OVERLAP / 2f
                    val keepBottom = if (end >= imageH) Float.MAX_VALUE else end - TILE_OVERLAP / 2f
                    out.addAll(recognizeTile(tile, lang, sample, y).filter { val cy = (it.box.top + it.box.bottom) / 2f; cy >= keepTop && cy < keepBottom })
                } finally { tile.recycle() }
                if (end >= imageH) break
                y = end - TILE_OVERLAP
            }
        } finally {
            decoder.recycle()
        }
        return out
    }

    private fun recognizeTile(tile: Bitmap, lang: String, sample: Int, yOffset: Int): List<OcrLine> {
        val env = models.environment
        val tw = tile.width; val th = tile.height
        val (dw, dh) = DbPost.inputSize(tw, th)
        // 1) détection : carte de probabilité de texte
        val scaled = Bitmap.createScaledBitmap(tile, dw, dh, true)
        val prob = try { detect(env, scaled, dw, dh) } finally { if (scaled !== tile) scaled.recycle() }
        val rawBoxes = DbPost.boxes(prob, dw, dh)
        // japonais : les fragments d'une colonne verticale (point final, signes) sont rattachés à elle
        val merged = if (lang == "ja") VerticalColumns.merge(rawBoxes) else null
        val boxes = if (merged != null) (merged.columns + merged.rest).sortedWith(compareBy({ it.y0 }, { it.x0 })) else rawBoxes
        if (boxes.isEmpty()) { OcrDebug.note = (OcrDebug.note + " [y=$yOffset : aucune boîte]").takeLast(500); return emptyList() }

        // 2) lecture de chaque ligne détectée
        val rec = models.recognizer(lang)
        val alt = models.altRecognizer(lang)
        val lines = ArrayList<OcrLine>()
        var tall = 0; var mangaRead = 0; var mangaErr = ""; var mangaAvail = "-"
        for (b in boxes) {
            val x0 = (b.x0 / dw * tw).toInt().coerceIn(0, tw - 1); val y0 = (b.y0 / dh * th).toInt().coerceIn(0, th - 1)
            val x1 = (b.x1 / dw * tw).toInt().coerceIn(x0 + 1, tw); val y1 = (b.y1 / dh * th).toInt().coerceIn(y0 + 1, th)
            val cw = x1 - x0; val ch = y1 - y0
            if (cw < 8 || ch < 8) continue
            if (ch > 2.2f * cw) {
                // colonne verticale : la lecture horizontale ne convient pas. Japonais : manga-ocr ; autres écritures : laissée à ML Kit
                val mr = if (lang == "ja") models.manga else null
                tall++
                if (mr != null) mangaAvail = mr.available.toString()
                if (mr != null && mr.available) {
                    val bx0 = max(0, x0 - 4); val by0 = max(0, y0 - 4); val bx1 = min(tw, x1 + 4); val by1 = min(th, y1 + 4)
                    val col = Bitmap.createBitmap(tile, bx0, by0, bx1 - bx0, by1 - by0)
                    val res = try { mr.read(col) } catch (e: Throwable) { Log.w("MT", "manga-ocr : ${e.message}"); if (mangaErr.isEmpty()) mangaErr = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(160); null } finally { col.recycle() }
                    if (res != null && res.first.isNotEmpty() && res.second >= 0.5f) { mangaRead++
                        lines.add(OcrLine(res.first, RectF(x0.toFloat() * sample, y0.toFloat() * sample + yOffset, x1.toFloat() * sample, y1.toFloat() * sample + yOffset), res.second, vertical = true)) }
                    else if (res != null) mangaErr = mangaErr.ifEmpty { "rejeté: '${res.first.take(12)}' conf=${"%.2f".format(res.second)}" }
                }
                continue
            }
            val crop = Bitmap.createBitmap(tile, x0, y0, cw, ch)
            val (text, conf) = try {
                val a = readLine(env, rec, crop)
                // deux lecteurs anglais : on garde le plus confiant (égalité : le v5, plus récent)
                if (alt != null) { val b = try { readLine(env, alt, crop) } catch (e: Throwable) { null }; if (b != null && b.second >= a.second) b else a } else a
            } finally { crop.recycle() }
            val clean = text.trim()
            if (clean.isEmpty() || conf < MIN_CONF) continue
            lines.add(
                OcrLine(
                    clean,
                    RectF(x0.toFloat() * sample, y0.toFloat() * sample + yOffset, x1.toFloat() * sample, y1.toFloat() * sample + yOffset),
                    conf,
                    thickness = if (b.thickness > 0f) b.thickness / dh * th * sample else 0f,
                ),
            )
        }
        OcrDebug.note = (OcrDebug.note + " [y=$yOffset langue=$lang boîtes=${boxes.size} colonnes=$tall manga(dispo=$mangaAvail, lues=$mangaRead) $mangaErr]").takeLast(500)
        return lines
    }

    private fun detect(env: OrtEnvironment, bmp: Bitmap, w: Int, h: Int): FloatArray {
        val data = FloatArray(3 * w * h)
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        // ordre des canaux B, G, R et normalisation ImageNet, comme PaddleOCR / OpenCV
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f); val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val plane = w * h
        for (i in 0 until plane) {
            val c = px[i]
            data[i] = (((c and 0xFF) / 255f) - mean[0]) / std[0]
            data[plane + i] = ((((c shr 8) and 0xFF) / 255f) - mean[1]) / std[1]
            data[2 * plane + i] = ((((c shr 16) and 0xFF) / 255f) - mean[2]) / std[2]
        }
        val session = models.detector()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, h.toLong(), w.toLong())).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { res ->
                @Suppress("UNCHECKED_CAST")
                val out = res.get(0).value as Array<Array<Array<FloatArray>>>
                val prob = FloatArray(w * h)
                val map = out[0][0]
                for (y in 0 until h) System.arraycopy(map[y], 0, prob, y * w, w)
                return prob
            }
        }
    }

    private fun readLine(env: OrtEnvironment, rec: PaddleModels.Rec, crop: Bitmap): Pair<String, Float> {
        val (rh, rw) = Ctc.inputSize(crop.width, crop.height, rec.imgH)
        val scaled = Bitmap.createScaledBitmap(crop, rw, rh, true)
        try {
            val px = IntArray(rw * rh); scaled.getPixels(px, 0, rw, 0, 0, rw, rh)
            val plane = rw * rh
            val data = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val c = px[i]
                data[i] = (((c and 0xFF) / 255f) - 0.5f) / 0.5f
                data[plane + i] = ((((c shr 8) and 0xFF) / 255f) - 0.5f) / 0.5f
                data[2 * plane + i] = ((((c shr 16) and 0xFF) / 255f) - 0.5f) / 0.5f
            }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, rh.toLong(), rw.toLong())).use { input ->
                rec.session.run(mapOf(rec.session.inputNames.first() to input)).use { res ->
                    @Suppress("UNCHECKED_CAST")
                    val out = res.get(0).value as Array<Array<FloatArray>>
                    val steps = out[0].size; val classes = out[0][0].size
                    val flat = FloatArray(steps * classes)
                    for (t in 0 until steps) System.arraycopy(out[0][t], 0, flat, t * classes, classes)
                    return Ctc.decode(flat, steps, classes, rec.charset)
                }
            }
        } finally {
            scaled.recycle()
        }
    }

    private companion object {
        const val TILE_H = 1280
        const val TILE_OVERLAP = 96
        const val MIN_CONF = 0.5f
    }
}

/** ML Kit + PaddleOCR : tout texte repéré par l'un ou l'autre est conservé, la lecture la plus sûre l'emporte. */
class CombinedOcr(
    private val source: SourceLang,
    private val mlkit: MlKitOcr,
    private val paddle: PaddleOcr?,
    private val lexicon: app.manhwa.core.EnglishLexicon? = null,
) : OcrEngine {

    override fun recognize(imageBytes: ByteArray): OcrResult {
        val ml: OcrResult? = try { mlkit.recognize(imageBytes) } catch (e: Throwable) {
            if (paddle == null) throw e
            Log.w("MT", "ML Kit a échoué : $e"); null
        }
        if (paddle == null) return ml!!
        OcrDebug.note = ""
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        val w = ml?.imageWidth ?: bounds.outWidth; val h = ml?.imageHeight ?: bounds.outHeight
        val script = scriptFor(ml?.lines ?: emptyList())
        val pp = try { paddle.recognize(imageBytes, script, w, h) } catch (e: Throwable) {
            Log.w("MT", "PaddleOCR a échoué : $e"); emptyList()
        }
        val dets = try { paddle.detectRegions(imageBytes, w, h) } catch (e: Throwable) {
            Log.w("MT", "détecteur de bulles : $e"); OcrDebug.note += " [détecteur : ${(e.javaClass.simpleName + ": " + (e.message ?: "")).take(120)}]"; emptyList()
        }
        OcrDebug.note = (OcrDebug.note + " [zones : bulles=${dets.count { it.cls == app.manhwa.core.RegionClass.BUBBLE }} texte_bulle=${dets.count { it.cls == app.manhwa.core.RegionClass.TEXT_BUBBLE }} texte_libre=${dets.count { it.cls == app.manhwa.core.RegionClass.TEXT_FREE }}]").takeLast(600)
        return OcrResult(w, h, OcrMerge.merge(ml?.lines ?: emptyList(), pp, script, lexicon), dets)
    }

    private fun scriptFor(mlLines: List<OcrLine>): String = when (source) {
        SourceLang.JA -> "ja"
        SourceLang.KO -> "ko"
        SourceLang.ZH_HANS, SourceLang.ZH_HANT -> "zh"
        SourceLang.EN -> "en"
        SourceLang.AUTO -> mlLines.filter { it.confidence >= 0.5f }.joinToString("") { it.text }.let { if (it.isBlank()) "en" else ScriptDetector.dominant(it) }
    }
}
