package app.manhwa

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import org.json.JSONObject

/**
 * Mesure de fluidité : pendant que la page défile, compte les images de l'écran affichées trop tard, séparément selon que la traduction travaille ou non, et note ce qui travaillait
 * quand une image était vraiment trop lente (plus de [SLOW_MS] ms) et où elle a perdu son temps (attente avant le dessin, dessin de l'application, page Web, GPU).
 * Un résumé part au journal (ligne « jank ») tous les [REPORT_EVERY_MS] ms de mesure et quand l'application quitte l'écran ; rien d'autre n'est enregistré, aucun contenu de page.
 * Sert à savoir, sur le téléphone lui-même, d'où viennent les saccades quand elles reviennent.
 *
 * [translating] : une traduction est en cours ; [activity] : étapes qui travaillent à cet instant (bits [FETCH], [OCR], [LLM], [DRAW], [APPLY]) ; [backend] : description du moteur de traduction ;
 * [report] : écriture au journal.
 */
class JankMeter(
    private val translating: () -> Boolean,
    private val activity: () -> Int,
    private val backend: () -> String,
    private val report: (JSONObject) -> Unit,
) {
    /** Compteurs d'une catégorie d'images (défilement sans traduction, défilement pendant la traduction). Fil de mesure seulement. */
    private class Bucket {
        var frames = 0; var late = 0; var slow = 0; var worstMs = 0L
        var delayMs = 0L; var uiMs = 0L; var syncMs = 0L; var renderMs = 0L; var gpuMs = 0L
        fun json(): JSONObject {
            val o = JSONObject().put("n", frames).put("late", late).put("slow", slow).put("worst", worstMs)
            if (slow > 0) o.put("avgSlow", JSONObject().put("wait", delayMs / slow).put("ui", uiMs / slow).put("web", syncMs / slow).put("render", renderMs / slow).put("gpu", gpuMs / slow))
            return o
        }
    }

    private val still = Bucket()
    private val busy = Bucket()
    private val slowWith = IntArray(WITH.size)
    private var startedAt = 0L
    private var refreshHz = 0

    // défilement : début et fin signalés par la page (fil du pont JavaScript) ; le temps et le travail du processeur passés à défiler sont additionnés pour en tirer une charge moyenne
    @Volatile private var scrolling = false
    private var scrollFrom = 0L
    private var cpuFrom = 0L
    private var scrollWall = 0L
    private var scrollCpu = 0L

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var attached: Window? = null
    private val listener = Window.OnFrameMetricsAvailableListener { _, fm, _ -> try { onFrame(fm) } catch (_: Throwable) {} }

    /** À appeler quand la page commence ou cesse de défiler. */
    fun onScroll(on: Boolean) {
        val now = SystemClock.elapsedRealtime(); val cpu = android.os.Process.getElapsedCpuTime()
        synchronized(this) {
            if (on && !scrolling) { scrolling = true; scrollFrom = now; cpuFrom = cpu }
            else if (!on && scrolling) { scrolling = false; scrollWall += now - scrollFrom; scrollCpu += cpu - cpuFrom }
        }
    }

    fun attach(window: Window) {
        if (attached === window) return
        detach()
        try {
            val t = HandlerThread("jank").also { it.start() }
            thread = t; handler = Handler(t.looper)
            window.addOnFrameMetricsAvailableListener(listener, handler)
            attached = window
        } catch (_: Throwable) { thread?.quitSafely(); thread = null; handler = null }
    }

    /** L'application quitte l'écran : le résumé en cours part au journal. */
    fun detach() {
        val w = attached ?: return
        attached = null
        try { w.removeOnFrameMetricsAvailableListener(listener) } catch (_: Throwable) {}
        val t = thread; val h = handler
        thread = null; handler = null
        h?.post { try { flush() } catch (_: Throwable) {} }
        t?.quitSafely()
    }

    private fun onFrame(fm: FrameMetrics) {
        if (!scrolling) return
        if (fm.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        val totalNs = fm.getMetric(FrameMetrics.TOTAL_DURATION)
        // temps imparti : sur Android 12 et plus, l'échéance de l'image (instant, ou durée selon la version) ; sinon 60 images par seconde
        var limit = 16_700_000L
        if (Build.VERSION.SDK_INT >= 31) {
            val dl = fm.getMetric(FrameMetrics.DEADLINE); val iv = fm.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            val budget = if (dl > iv) dl - iv else dl
            if (budget in 3_000_000L..50_000_000L) limit = budget
        }
        val b = if (translating()) busy else still
        if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
        b.frames++
        if (totalNs > limit) b.late++
        val ms = totalNs / 1_000_000
        if (ms > b.worstMs) b.worstMs = ms
        if (ms > SLOW_MS) {
            b.slow++
            b.delayMs += fm.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION) / 1_000_000
            b.uiMs += (fm.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) + fm.getMetric(FrameMetrics.ANIMATION_DURATION) + fm.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) + fm.getMetric(FrameMetrics.DRAW_DURATION)) / 1_000_000
            b.syncMs += fm.getMetric(FrameMetrics.SYNC_DURATION) / 1_000_000
            b.renderMs += (fm.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) + fm.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)) / 1_000_000
            if (Build.VERSION.SDK_INT >= 31) b.gpuMs += fm.getMetric(FrameMetrics.GPU_DURATION) / 1_000_000
            if (b === busy) { val a = try { activity() } catch (_: Throwable) { 0 }; for (i in WITH.indices) if (a and (1 shl i) != 0) slowWith[i]++ }
        }
        if (SystemClock.elapsedRealtime() - startedAt >= REPORT_EVERY_MS) flush()
    }

    /** Écrit le résumé s'il y a assez de mesures (une seconde de défilement au moins), puis repart de zéro. */
    private fun flush() {
        val n = still.frames + busy.frames
        if (n >= MIN_FRAMES) {
            // fréquence de l'écran (l'écran n'est rattaché à une fenêtre qu'après le démarrage : on la lit ici, au premier résumé)
            if (refreshHz == 0) refreshHz = try { Math.round(attached?.decorView?.display?.refreshRate ?: 0f) } catch (_: Throwable) { 0 }
            val (wall, cpu) = synchronized(this) { scrollWall to scrollCpu }
            val o = JSONObject().put("t", System.currentTimeMillis()).put("type", "jank").put("build", BuildConfig.BUILD_NUMBER).put("hz", refreshHz)
                .put("scrollS", wall / 1000).put("still", still.json()).put("busy", busy.json())
            if (wall > 0) o.put("cores", Math.round(cpu * 10.0 / wall) / 10.0)
            val w = JSONObject(); for (i in WITH.indices) if (slowWith[i] > 0) w.put(WITH[i], slowWith[i])
            if (w.length() > 0) o.put("with", w)
            val be = try { backend() } catch (_: Throwable) { "" }
            if (be.isNotEmpty()) o.put("llm", be)
            report(o)
        }
        still.frames = 0; still.late = 0; still.slow = 0; still.worstMs = 0; still.delayMs = 0; still.uiMs = 0; still.syncMs = 0; still.renderMs = 0; still.gpuMs = 0
        busy.frames = 0; busy.late = 0; busy.slow = 0; busy.worstMs = 0; busy.delayMs = 0; busy.uiMs = 0; busy.syncMs = 0; busy.renderMs = 0; busy.gpuMs = 0
        java.util.Arrays.fill(slowWith, 0)
        startedAt = 0L
        synchronized(this) { scrollWall = 0; scrollCpu = 0; if (scrolling) { scrollFrom = SystemClock.elapsedRealtime(); cpuFrom = android.os.Process.getElapsedCpuTime() } }
    }

    companion object {
        /** Étapes qui peuvent travailler pendant une image lente : téléchargement ou lecture des images, lecture du texte, modèle de traduction, dessin de la page, pose du calque dans la page. */
        const val FETCH = 1
        const val OCR = 2
        const val LLM = 4
        const val DRAW = 8
        const val APPLY = 16
        private val WITH = arrayOf("fetch", "ocr", "llm", "draw", "apply")
        /** Une image est « lente » au-delà de ce temps, quelle que soit la fréquence de l'écran. */
        const val SLOW_MS = 32L
        const val REPORT_EVERY_MS = 30_000L
        const val MIN_FRAMES = 60
    }
}
