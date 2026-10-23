package app.manhwa

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import app.manhwa.core.AppSettings
import app.manhwa.core.ConfirmationRequired
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.InsufficientSpace
import app.manhwa.core.LlmModels
import app.manhwa.core.LlmPrompt
import app.manhwa.core.ModelSpec
import app.manhwa.core.ModelStatus
import app.manhwa.core.ModelStore
import app.manhwa.core.TranslationEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Téléchargement vérifié (somme de contrôle, reprise, espace disque) des modèles Gemma ; état observable par l'interface. */
class LlmModelsManager(ctx: Context) {
    private val store = ModelStore(File(ctx.filesDir, "models"), freeSpace = { ctx.filesDir.usableSpace })
    val specs: List<ModelSpec> = listOf(LlmModels.E2B, LlmModels.E4B)

    val ready = mutableStateMapOf<String, Boolean>()
    val progress = mutableStateMapOf<String, Float>()      // 0..1 pendant un téléchargement
    val downloaded = mutableStateMapOf<String, Long>()     // octets reçus
    val busy = mutableStateMapOf<String, Boolean>()
    val error = mutableStateMapOf<String, String>()
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()
    @Volatile var onDownloaded: ((ModelSpec) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init { refresh() }

    fun refresh() { specs.forEach { ready[it.id] = store.status(it) == ModelStatus.Ready } }
    fun isReady(spec: ModelSpec): Boolean = store.status(spec) == ModelStatus.Ready
    fun fileOf(spec: ModelSpec): File = store.fileOf(spec)
    fun usedBytes(): Long = store.usedBytes()

    /** À n'appeler qu'après confirmation explicite de l'utilisateur (plusieurs Go). */
    fun download(spec: ModelSpec) {
        if (busy[spec.id] == true) return
        busy[spec.id] = true; error.remove(spec.id); progress[spec.id] = 0f
        val flag = AtomicBoolean(false).also { cancelFlags[spec.id] = it }
        thread(name = "model-download-${spec.id}") {
            var lastPct = -1
            try {
                store.download(spec, confirmed = true, onProgress = { done, total ->
                    val pct = (done * 1000 / total).toInt() // pas de 0,1 % pour ne pas saturer l'interface
                    if (pct != lastPct) { lastPct = pct; progress[spec.id] = done.toFloat() / total; downloaded[spec.id] = done }
                }, isCancelled = { flag.get() })
                if (store.status(spec) == ModelStatus.Ready) mainHandler.post { onDownloaded?.invoke(spec) }
            } catch (e: InsufficientSpace) {
                error[spec.id] = "Espace insuffisant : ${e.needed / 1_000_000} Mo nécessaires, ${e.free / 1_000_000} Mo libres"
            } catch (e: ConfirmationRequired) {
                error[spec.id] = "Confirmation requise"
            } catch (e: Exception) {
                error[spec.id] = e.message ?: "Échec du téléchargement"
            } finally {
                busy[spec.id] = false; progress.remove(spec.id); refresh()
            }
        }
    }

    fun cancel(spec: ModelSpec) { cancelFlags[spec.id]?.set(true) }

    fun delete(spec: ModelSpec) { store.delete(spec); error.remove(spec.id); refresh() }
}

/**
 * Traduction par modèle de langage local (Gemma 4 via LiteRT-LM) : toute la page est traduite en une requête,
 * avec le dialogue précédent du chapitre et le glossaire comme contexte. Aucune donnée ne quitte le téléphone.
 */
class GemmaTranslator(
    private val ctx: Context,
    private val models: LlmModelsManager,
    private val settings: () -> AppSettings,
) : TranslationEngine {

    private var engine: Engine? = null
    private var engineKey = ""
    private val history = ArrayDeque<Pair<String, String>>()
    private val main = Handler(Looper.getMainLooper())
    private val releaseTask = Runnable { release() }

    override fun usesGlossaryPrompt(): Boolean = true

    override fun translate(texts: List<String>, source: String, target: String): List<String> =
        translateWithGlossary(texts, source, target, emptyList())

    @Synchronized
    override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> {
        val s = settings()
        val spec = LlmModels.specFor(s.engine) ?: throw IllegalStateException("Aucun modèle Gemma sélectionné")
        if (!models.isReady(spec)) throw IllegalStateException("Modèle Gemma absent : téléchargez-le dans l'onglet Modèles")
        main.removeCallbacks(releaseTask)
        try {
            val eng = ensureEngine(spec, s.useGpu)
            var out = ask(eng, texts, source, target, glossary)
            // lignes manquantes dans la réponse : une seconde tentative, ligne par ligne
            for (i in out.indices) if (out[i] == null) out[i] = ask(eng, listOf(texts[i]), source, target, glossary).firstOrNull()
            val final = out.mapIndexed { i, t -> t ?: throw IllegalStateException("Réponse du modèle incomplète (ligne ${i + 1})") }
            texts.indices.forEach { remember(texts[it], final[it]) }
            return final
        } finally {
            main.postDelayed(releaseTask, IDLE_RELEASE_MS) // libère la mémoire (plusieurs Go) quand on ne traduit plus
        }
    }

    private fun ask(eng: Engine, texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): MutableList<String?> {
        val config = ConversationConfig(
            systemInstruction = Contents.of(LlmPrompt.system(source, target)),
            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.3),
        )
        val reply = eng.createConversation(config).use { conv ->
            textOf(conv.sendMessage(LlmPrompt.user(texts, history.toList(), glossary)))
        }
        return LlmPrompt.parse(reply, texts.size).toMutableList()
    }

    private fun textOf(m: Message): String =
        m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.ifEmpty { m.toString() }

    private fun ensureEngine(spec: ModelSpec, gpu: Boolean): Engine {
        val key = spec.id + if (gpu) ":gpu" else ":cpu"
        engine?.let { if (engineKey == key) return it }
        release()
        val path = models.fileOf(spec).absolutePath
        fun create(backend: Backend): Engine = Engine(
            EngineConfig(modelPath = path, backend = backend, maxNumTokens = 4096, cacheDir = ctx.cacheDir.path),
        ).also { it.initialize() }
        val e = if (gpu) {
            try { create(Backend.GPU()) } catch (t: Throwable) { create(Backend.CPU()) } // repli CPU si le GPU est indisponible
        } else create(Backend.CPU())
        engine = e; engineKey = key
        return e
    }

    private fun remember(src: String, tgt: String) {
        history.addLast(src to tgt)
        while (history.size > HISTORY_SIZE) history.removeFirst()
    }

    /** Nouveau chapitre ou nouvelle page web : on oublie le dialogue précédent. */
    @Synchronized fun resetContext() { history.clear() }

    @Synchronized fun release() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null; engineKey = ""
    }

    companion object {
        const val HISTORY_SIZE = 10
        const val IDLE_RELEASE_MS = 3 * 60 * 1000L
        fun engineId(spec: ModelSpec) = "${spec.id}-ctx1"
    }
}
