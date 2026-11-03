package app.manhwa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.AppSettings
import app.manhwa.core.BlockReader
import app.manhwa.core.LlmModels
import app.manhwa.core.TextBlock
import app.manhwa.core.VisionPrompt
import app.manhwa.core.VisionRead
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Relit un bloc de texte douteux avec Gemma (modèle de vision, déjà chargé pour la traduction) : le recadrage de la zone détectée est envoyé avec une
 * consigne de transcription seule. Le résultat n'est qu'une proposition : le pipeline la vérifie avant de l'adopter.
 */
class GemmaBlockReader(private val translator: GemmaTranslator, private val settings: () -> AppSettings) : BlockReader {
    override val available: Boolean
        get() { val s = settings(); return s.autoReread && LlmModels.specFor(s.engine) != null && translator.visionReady }

    override fun reread(imageBytes: ByteArray, block: TextBlock, engineText: String): VisionRead? {
        val png = cropPng(imageBytes, block) ?: return null
        val reply = translator.readImage(png, VisionPrompt.system(), VisionPrompt.user(engineText)) ?: return null
        return VisionPrompt.parse(reply)
    }

    /** Zone détectée (sinon lignes lues) agrandie de ~13 % pour garder le contour de la bulle et les lignes voisines dans le cadre ; réduite si elle est très grande. */
    private fun cropPng(imageBytes: ByteArray, block: TextBlock): ByteArray? {
        val u = block.lines.map { it.box }.reduce { a, b -> a.union(b) }
        val z = block.zone
        val base = if (z != null && z.area <= 6f * u.area) z.union(u) else u
        val margin = max(14f, 0.13f * max(base.width, base.height))
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size, false) ?: return null
        try {
            val r = Rect(max(0, (base.left - margin).toInt()), max(0, (base.top - margin).toInt()), min(decoder.width, (base.right + margin).toInt()), min(decoder.height, (base.bottom + margin).toInt()))
            if (r.width() < 16 || r.height() < 16) return null
            val longSide = max(r.width(), r.height())
            val sample = if (longSide > 2000) 2 else 1
            var bmp = decoder.decodeRegion(r, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            if (max(bmp.width, bmp.height) > 1000) {
                val k = 1000f / max(bmp.width, bmp.height)
                val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
                bmp.recycle(); bmp = scaled
            }
            return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out); bmp.recycle(); out.toByteArray() }
        } finally { decoder.recycle() }
    }
}

/** Test de la lecture d'image (réglages) avec détection de plantage : voir [GemmaTranslator.readImage]. */
class VisionTester(private val ctx: Context, private val translator: GemmaTranslator) {
    private val main = Handler(Looper.getMainLooper())
    var running by mutableStateOf(false)
        private set
    var summary by mutableStateOf(translator.visionStatus)
        private set

    fun run() {
        if (running) return
        running = true; summary = "Test en cours (une à deux minutes), ne traduisez pas pendant ce temps…"
        Thread {
            val png = VisionTestImage.render(ctx)
            val r = try { translator.testVision(png, listOf("RESTRICTION", "MOUTH")) } catch (t: Throwable) { null }
            val text = if (r == null) "Test impossible." else buildString {
                append(if (r.ok) "Lecture d'image validée." else "Échec : " + r.note)
                if (r.text.isNotEmpty()) append("\nLecture : « ").append(r.text.take(120)).append(" »")
                if (r.firstMs > 0) append(String.format(Locale.FRANCE, "\nTemps : %.1f s (première image), %.1f s (seconde)", r.firstMs / 1000.0, r.secondMs / 1000.0))
            }
            main.post { summary = text; running = false }
        }.start()
    }
}

/** Image du test : texte manuscrit incliné de 7° qui chevauche le contour d'une bulle, comme dans les cas qui trompent les lecteurs rapides. */
object VisionTestImage {
    fun render(ctx: Context): ByteArray {
        val bmp = Bitmap.createBitmap(640, 280, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f }
        c.drawArc(RectF(-120f, -260f, 420f, 190f), 0f, 180f, false, outline)
        val tf = try { Typeface.createFromAsset(ctx.assets, "fonts/PatrickHandSC-Regular.ttf") } catch (_: Throwable) { Typeface.DEFAULT_BOLD }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = tf; textSize = 40f; textAlign = Paint.Align.CENTER }
        c.save(); c.rotate(7f, 340f, 150f)
        c.drawText("WANT ME TO PUT", 340f, 100f, text); c.drawText("A RESTRICTION ON YOUR", 340f, 150f, text); c.drawText("MOUTH?", 340f, 200f, text)
        c.restore()
        return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out); bmp.recycle(); out.toByteArray() }
    }
}
