package app.manhwa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import app.manhwa.core.OcrEngine
import app.manhwa.core.OcrLine
import app.manhwa.core.OcrResult
import app.manhwa.core.RectF
import app.manhwa.core.ScriptDetector
import app.manhwa.core.SourceLang
import app.manhwa.core.TextNormalizer
import app.manhwa.core.TranslationEngine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.concurrent.TimeUnit

object Recognizers {
    val latin: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val japanese: TextRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    val korean: TextRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
    val chinese: TextRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
}

/**
 * OCR ML Kit, entièrement local. Les longues images (webtoons) sont découpées en tuiles
 * qui se recouvrent légèrement ; le regroupement final supprime les doublons de ligne.
 */
class MlKitOcr(private val source: SourceLang) : OcrEngine {

    override fun recognize(imageBytes: ByteArray): OcrResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IOException("Image illisible ou format non pris en charge")
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size, false)
            ?: throw IOException("Impossible d'ouvrir l'image")
        try {
            val lines = when (source) {
                SourceLang.JA -> run(decoder, w, h, Recognizers.japanese)
                SourceLang.KO -> run(decoder, w, h, Recognizers.korean)
                SourceLang.ZH_HANS, SourceLang.ZH_HANT -> run(decoder, w, h, Recognizers.chinese)
                SourceLang.EN -> run(decoder, w, h, Recognizers.latin)
                SourceLang.AUTO -> auto(decoder, w, h)
            }
            return OcrResult(w, h, lines)
        } finally {
            decoder.recycle()
        }
    }

    /** Premier passage japonais (lit aussi le latin), puis second passage si l'écriture détectée est autre. */
    private fun auto(d: BitmapRegionDecoder, w: Int, h: Int): List<OcrLine> {
        val first = run(d, w, h, Recognizers.japanese)
        val text = first.joinToString("") { it.text }
        if (text.isBlank()) return first
        return when (ScriptDetector.detect(text)) {
            "ko" -> run(d, w, h, Recognizers.korean)
            "zh" -> run(d, w, h, Recognizers.chinese)
            "en" -> if (ScriptDetector.hasCjk(text)) first else run(d, w, h, Recognizers.latin)
            else -> first
        }
    }

    private fun run(d: BitmapRegionDecoder, w: Int, h: Int, rec: TextRecognizer): List<OcrLine> {
        val sample = if (w > 2400) 2 else 1
        val out = ArrayList<OcrLine>()
        var y = 0
        while (true) {
            val end = minOf(h, y + TILE_H)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = d.decodeRegion(Rect(0, y, w, end), opts) ?: throw IOException("Découpe de l'image impossible")
            try {
                val text = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0)), 60, TimeUnit.SECONDS)
                for (block in text.textBlocks) for (line in block.lines) {
                    val b = line.boundingBox ?: continue
                    out.add(
                        OcrLine(
                            line.text,
                            RectF(b.left.toFloat() * sample, b.top.toFloat() * sample + y, b.right.toFloat() * sample, b.bottom.toFloat() * sample + y),
                            line.confidence,
                        ),
                    )
                }
            } finally {
                bmp.recycle()
            }
            if (end >= h) break
            y = end - TILE_OVERLAP
        }
        return out
    }

    private companion object {
        const val TILE_H = 2800
        const val TILE_OVERLAP = 150
    }
}

/** Traduction ML Kit locale. Les modèles doivent déjà être téléchargés : aucun téléchargement silencieux. */
class MlKitTranslation(private val models: TranslationModels) : TranslationEngine {

    override fun translate(texts: List<String>, source: String, target: String): List<String> {
        val out = arrayOfNulls<String>(texts.size)
        val groups = texts.indices.groupBy { i -> if (source == "auto") ScriptDetector.detect(texts[i]) else source.substringBefore('-') }
        for ((lang, idxs) in groups) {
            if (lang == target) { idxs.forEach { out[it] = texts[it] }; continue }
            for (l in listOf(lang, target)) {
                if (!models.isDownloaded(l)) throw IllegalStateException("Modèle de traduction « $l » absent : téléchargez-le dans l'onglet Modèles")
            }
            val translator = Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(lang).setTargetLanguage(target).build(),
            )
            try {
                // Les modèles requis sont déjà présents : cet appel ne fait que charger le moteur.
                Tasks.await(translator.downloadModelIfNeeded(), 120, TimeUnit.SECONDS)
                for (i in idxs) {
                    // phrase par phrase : meilleure qualité et aucune phrase omise
                    val parts = TextNormalizer.splitSentences(texts[i]).ifEmpty { listOf(texts[i]) }
                    out[i] = parts.joinToString(" ") { Tasks.await(translator.translate(it), 60, TimeUnit.SECONDS) }
                }
            } finally {
                translator.close()
            }
        }
        return out.map { it ?: "" }
    }

    companion object {
        const val ID = "mlkit-ocr16.0.1+mlkit-translate17.0.3+norm2"
        @Suppress("unused") val SUPPORTED = listOf(TranslateLanguage.JAPANESE, TranslateLanguage.KOREAN, TranslateLanguage.CHINESE, TranslateLanguage.ENGLISH)
    }
}
