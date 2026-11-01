package app.manhwa

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.LlmModels
import java.io.File
import java.util.Locale

/**
 * Test de vitesse CPU / GPU pour Gemma, avec détection de plantage : un plantage natif du GPU ne peut pas être intercepté, donc on écrit
 * « running » dans un fichier avant le test et on l'efface ensuite. Si l'application est relancée avec « running », le GPU est désactivé.
 */
class GpuTester(ctx: Context, private val graph: AppGraph) {
    private val sentinel = File(ctx.filesDir, "gpu_probe.state")
    private val main = Handler(Looper.getMainLooper())
    var running by mutableStateOf(false)
        private set
    var summary by mutableStateOf("")
        private set

    init {
        try {
            if (sentinel.exists()) {
                val t = sentinel.readText().trim()
                when {
                    t == "running" -> { sentinel.writeText("crashed"); graph.settingsStore.update { it.copy(useGpu = false) }; summary = "Le GPU a fait planter l'application lors du dernier test : il est désactivé." }
                    t == "crashed" -> summary = "Le GPU a fait planter l'application lors d'un test précédent : il reste désactivé."
                    t.startsWith("ok|") -> summary = t.removePrefix("ok|")
                }
            }
        } catch (_: Throwable) {}
    }

    private fun fmt(r: GemmaTranslator.BenchResult) =
        if (r.ok) String.format(Locale.FRANCE, "%s : %.1f s pour 6 répliques (chargement %.1f s)", r.label, r.translateMs / 1000.0, r.initMs / 1000.0)
        else "${r.label} : échec (${r.error ?: "réponse incomplète"})"

    fun run() {
        if (running) return
        val st = graph.settingsStore.settings
        val spec = LlmModels.specFor(st.engine)?.takeIf { graph.llm.isReady(it) } ?: listOf(LlmModels.E4B, LlmModels.E2B).firstOrNull { graph.llm.isReady(it) }
        if (spec == null) { summary = "Téléchargez d'abord un modèle Gemma dans l'onglet Modèles."; return }
        running = true; summary = "Test en cours (1 à 3 minutes), ne traduisez pas pendant ce temps…"
        Thread {
            var text: String
            try {
                sentinel.writeText("running")
                val gpu = graph.gemma.benchmark(spec, true)
                sentinel.writeText(if (gpu.ok) "gpu-ok" else "gpu-failed")
                val cpu = graph.gemma.benchmark(spec, false)
                val faster = gpu.ok && cpu.ok && gpu.translateMs * 1.25 < cpu.translateMs
                graph.settingsStore.update { it.copy(useGpu = faster) }
                text = fmt(cpu) + "\n" + fmt(gpu) + "\n" + if (faster) "Le GPU est nettement plus rapide : activé." else "Le GPU n'apporte pas de gain net ou a échoué : désactivé."
                sentinel.writeText("ok|$text")
            } catch (t: Throwable) {
                text = "Test impossible : ${t.message ?: t.javaClass.simpleName}"
                try { sentinel.writeText("ok|$text") } catch (_: Throwable) {}
            }
            main.post { summary = text; running = false }
        }.start()
    }
}
