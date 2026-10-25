package app.manhwa

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.manhwa.core.QualityMode
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import app.manhwa.core.TranslationEngineChoice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * Crochets de test automatique (builds debug uniquement) : un intent « am start --es mt_url … » charge une page,
 * lance la traduction, attend la fin et écrit un rapport JSON détaillé dans files/report.json.
 */
object TestHooks {
    private const val TAG = "MTTEST"
    @Volatile private var running = false

    fun maybeRun(ctx: Context, graph: AppGraph, intent: Intent) {
        val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val url = intent.getStringExtra("mt_url")
        if (!debuggable || url == null || running) return
        running = true
        val c = graph.controller
        val main = Handler(Looper.getMainLooper())
        fun s(name: String) = intent.getStringExtra(name)

        graph.settingsStore.update { st ->
            st.copy(
                source = SourceLang.values().firstOrNull { it.name == s("mt_source") } ?: st.source,
                target = TargetLang.values().firstOrNull { it.name == s("mt_target") } ?: st.target,
                engine = TranslationEngineChoice.values().firstOrNull { it.name == s("mt_engine") } ?: st.engine,
                useGpu = s("mt_gpu") == "true",
                diagnostic = false,
                enhancedOcr = s("mt_enhanced") != "false",
                autoTranslate = true,
                mode = QualityMode.FAST,
                textScale = s("mt_scale")?.toFloatOrNull() ?: st.textScale,
            )
        }
        val timeoutMs = (s("mt_timeout")?.toLongOrNull() ?: 900L) * 1000
        val models = s("mt_models")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val reportName = s("mt_report") ?: "report.json"

        thread(name = "mt-test") {
            val t0 = System.currentTimeMillis()
            val notes = JSONArray()
            var timedOut = false
            try {
                // 1) modèles de traduction ML Kit demandés
                for (lang in models) {
                    val t1 = System.currentTimeMillis()
                    main.post { graph.models.download(lang) }
                    while (System.currentTimeMillis() - t1 < 600_000) {
                        Thread.sleep(1500)
                        val st = graph.models.status[lang]
                        if (st == "ready") break
                        if (st != null && st.startsWith("error:")) { notes.put("modèle $lang : $st"); break }
                    }
                    notes.put("modèle $lang : ${graph.models.status[lang]} (${System.currentTimeMillis() - t1} ms)")
                }
                graph.llm.refresh()
                notes.put("gemma prêt : E2B=${graph.llm.ready["gemma4-e2b"]} E4B=${graph.llm.ready["gemma4-e4b"]}")

                // 2) attendre que le navigateur soit créé puis charger la page de test
                var waited = 0
                while (!c.isReady() && waited < 60) { Thread.sleep(500); waited++ }
                c.testing = true
                if (s("mt_fullscreen") == "true") main.post { c.enterFullscreen() }
                val tLoad = System.currentTimeMillis()
                main.post { c.load(url) }

                // 3) attendre la fin de la traduction (compteurs stables)
                var lastSig = ""; var stableSince = System.currentTimeMillis()
                while (System.currentTimeMillis() - tLoad < timeoutMs) {
                    Thread.sleep(2000)
                    val sig = "${c.total}/${c.done}/${c.failed}/${c.loading}"
                    val now = System.currentTimeMillis()
                    if (sig != lastSig) { lastSig = sig; stableSince = now }
                    val finished = !c.loading && c.total > 0 && c.done + c.failed >= c.total && now - stableSince > 8000
                    val nothing = !c.loading && c.total == 0 && now - stableSince > 60_000
                    if (finished || nothing) break
                    if (now - tLoad >= timeoutMs) timedOut = true
                }
                if (System.currentTimeMillis() - tLoad >= timeoutMs) timedOut = true
            } catch (e: Throwable) {
                notes.put("exception : $e")
            }
            val settings = graph.settingsStore.settings
            val report = JSONObject()
                .put("url", url).put("elapsedMs", System.currentTimeMillis() - t0).put("timeout", timedOut)
                .put("settings", JSONObject().put("source", settings.source.name).put("target", settings.target.name).put("engine", settings.engine.name).put("textScale", settings.textScale.toDouble()).put("enhancedOcr", settings.enhancedOcr && graph.paddleModels.available))
                .put("counters", JSONObject().put("total", c.total).put("done", c.done).put("failed", c.failed).put("noText", c.noText).put("lastError", c.lastError ?: ""))
                .put("notes", notes)
                .put("images", JSONArray(c.testLog.toList()))
            val tmp = File(ctx.filesDir, "$reportName.tmp")
            tmp.writeText(report.toString())
            tmp.renameTo(File(ctx.filesDir, reportName))
            Log.i(TAG, "REPORT_READY $reportName")
            c.testing = false
            running = false
        }
    }
}
