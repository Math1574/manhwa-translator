package app.manhwa

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import app.manhwa.core.AppSettings
import app.manhwa.core.ConfirmationRequired
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.InsufficientSpace
import app.manhwa.core.ChapterMemory
import app.manhwa.core.LlmModels
import app.manhwa.core.TranslationChecks
import app.manhwa.core.LlmPrompt
import app.manhwa.core.ModelSpec
import app.manhwa.core.ModelStatus
import app.manhwa.core.ModelStore
import app.manhwa.core.TranslationEngine
import app.manhwa.core.VisionPrompt
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
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
/** Résultat des contrôles d'une ligne traduite (pour le journal). */
class LineReport(val src: String, val tr: String, val problems: List<String>, val retried: Boolean, val fixed: Boolean)

class GemmaTranslator(
    private val ctx: Context,
    private val models: LlmModelsManager,
    private val settings: () -> AppSettings,
) : TranslationEngine {

    private var engine: Engine? = null
    private var engineKey = ""
    /** Version du prompt et relecture : réglables par le banc d'essai de traduction. */
    @Volatile var promptVariant: Int = LlmPrompt.CURRENT
    @Volatile var refine: Boolean = false
    /** Titre de la série (déduit de l'adresse) : aide le modèle à comprendre le genre et les noms. */
    @Volatile var seriesTitle: String? = null
    /** Noms et termes du chapitre en cours (mémoire) et rapport des contrôles de la dernière page. */
    private val memory = ChapterMemory()
    @Volatile var lastReport: List<LineReport> = emptyList()

    /** Noms propres repérés dans le chapitre (minuscules) : le réparateur de texte lu ne doit pas les « corriger ». */
    fun chapterNames(): Set<String> = memory.namesToKeep().map { it.lowercase() }.toSet()
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
            val eng = ensureEngine(spec, s.useGpu, vision = visionReady && s.autoReread)
            var out = ask(eng, texts, source, target, glossary)
            // lignes manquantes dans la réponse : une seconde tentative, ligne par ligne
            for (i in out.indices) if (out[i] == null) out[i] = ask(eng, listOf(texts[i]), source, target, glossary).firstOrNull()
            var final = out.mapIndexed { i, t -> t ?: throw IllegalStateException("Réponse du modèle incomplète (ligne ${i + 1})") }
            if (refine || s.refinePass) final = refineDraft(eng, texts, final, glossary)
            final = checkAndFix(eng, texts, final, target, source, glossary)
            texts.indices.forEach { remember(texts[it], final[it]); memory.observe(texts[it], final[it]) }
            return final
        } finally {
            main.postDelayed(releaseTask, IDLE_RELEASE_MS) // libère la mémoire (plusieurs Go) quand on ne traduit plus
        }
    }

    /** Seconde passe : un « éditeur » corrige les contresens et le style littéral du brouillon. En cas de réponse invalide, le brouillon est conservé. */
    private fun refineDraft(eng: Engine, texts: List<String>, draft: List<String>, glossary: List<GlossaryEntry>): List<String> {
        return try {
            val config = ConversationConfig(
                systemInstruction = Contents.of(LlmPrompt.refineSystem()),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.15),
                maxOutputToken = LlmPrompt.maxOutputTokens(texts),
            )
            val reply = eng.createConversation(config).use { conv -> withWatchdog(conv) { textOf(conv.sendMessage(LlmPrompt.refineUser(texts, draft, history.toList(), glossary, seriesTitle))) } }
            LlmPrompt.parse(reply, texts.size).mapIndexed { i, t -> t ?: draft[i] }
        } catch (e: Throwable) {
            draft
        }
    }

    /**
     * Contrôle chaque ligne (mots anglais restants, contenu perdu, nom modifié, chiffres, répétition…) et refait seule toute ligne qui échoue,
     * une fois, avec le détail des problèmes. La nouvelle version n'est retenue que si elle a moins de problèmes.
     */
    private fun checkAndFix(eng: Engine, texts: List<String>, draft: List<String>, target: String, source: String, glossary: List<GlossaryEntry>): List<String> {
        val names = memory.namesToKeep()
        val out = draft.toMutableList(); val reports = ArrayList<LineReport>()
        for (i in out.indices) {
            var probs = TranslationChecks.problems(texts[i], out[i], target, names)
            var retried = false; var fixed = false
            if (probs.isNotEmpty()) {
                retried = true
                val again = try { askFix(eng, texts[i], out[i], probs.map { "${it.code}: ${it.detail}" }, source, target, glossary, names) } catch (e: Throwable) { null }
                if (again != null) {
                    val p2 = TranslationChecks.problems(texts[i], again, target, names)
                    if (p2.size < probs.size) { out[i] = again; fixed = p2.isEmpty(); probs = p2 }
                }
            }
            reports.add(LineReport(texts[i], out[i], probs.map { it.code }, retried, fixed))
        }
        lastReport = reports
        return out
    }

    private fun askFix(eng: Engine, text: String, previous: String, problems: List<String>, source: String, target: String, glossary: List<GlossaryEntry>, names: List<String>): String? {
        val config = ConversationConfig(
            systemInstruction = Contents.of(LlmPrompt.system(source, target, promptVariant)),
            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1),
            maxOutputToken = LlmPrompt.maxOutputTokens(listOf(text)),
        )
        val reply = eng.createConversation(config).use { conv ->
            withWatchdog(conv) { textOf(conv.sendMessage(LlmPrompt.fixUser(text, previous, problems, history.toList(), glossary, names))) }
        }
        return LlmPrompt.parse(reply, 1).firstOrNull()
    }

    private fun ask(eng: Engine, texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>, series: String? = seriesTitle): MutableList<String?> {
        val config = ConversationConfig(
            systemInstruction = Contents.of(LlmPrompt.system(source, target, promptVariant)),
            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.15),
            maxOutputToken = LlmPrompt.maxOutputTokens(texts),
        )
        val reply = eng.createConversation(config).use { conv ->
            withWatchdog(conv) { textOf(conv.sendMessage(LlmPrompt.user(texts, history.toList(), glossary, series, memory.namesToKeep()))) }
        }
        return LlmPrompt.parse(reply, texts.size).toMutableList()
    }

    /** Interrompt une génération qui dure trop longtemps (sécurité) : la réponse partielle reste exploitable ligne par ligne. */
    private fun <T> withWatchdog(conv: com.google.ai.edge.litertlm.Conversation, block: () -> T): T {
        val timer = java.util.Timer("mt-llm-watchdog", true)
        timer.schedule(object : java.util.TimerTask() { override fun run() { try { conv.cancelProcess() } catch (_: Throwable) {} } }, GENERATION_TIMEOUT_MS)
        try { return block() } finally { timer.cancel() }
    }

    private fun textOf(m: Message): String =
        m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.ifEmpty { m.toString() }

    /** Description du moteur actif (« GPU + décodage spéculatif », « CPU »…), pour les réglages et le journal. */
    @Volatile var backendLabel: String = ""
        private set

    /**
     * Crée et initialise un moteur. Le décodage spéculatif (prédiction de plusieurs jetons) accélère la génération sur CPU comme sur GPU ;
     * il suppose un modèle récent (fichier publié après mai 2026) : en cas d'échec on réessaie sans.
     */
    @OptIn(ExperimentalApi::class)
    private fun createEngine(path: String, backend: Backend, speculative: Boolean, vision: Boolean = false): Engine {
        ExperimentalFlags.enableSpeculativeDecoding = speculative
        // lecture d'image : encodeur de vision sur GPU (la voie CPU plante au deuxième tour d'image sur certains Snapdragon), une image à la fois
        val cfg = if (vision) EngineConfig(modelPath = path, backend = backend, visionBackend = Backend.GPU(), maxNumTokens = 4096, maxNumImages = 1, cacheDir = ctx.cacheDir.path)
        else EngineConfig(modelPath = path, backend = backend, maxNumTokens = 4096, cacheDir = ctx.cacheDir.path)
        return Engine(cfg).also { it.initialize() }
    }

    /** Vrai si le moteur chargé sait lire une image. */
    @Volatile var engineHasVision: Boolean = false
        private set

    private fun ensureEngine(spec: ModelSpec, gpu: Boolean, vision: Boolean = false): Engine {
        val key = spec.id + (if (gpu) ":gpu" else ":cpu") + (if (vision) ":v" else "")
        engine?.let { if (engineKey == key) return it }
        release()
        val path = models.fileOf(spec).absolutePath
        // ordre d'essai : GPU + spéculatif, GPU, CPU + spéculatif, CPU (repli automatique si une option n'est pas disponible)
        val attempts = (if (gpu) listOf(true to true, true to false) else emptyList()) + listOf(false to true, false to false)
        var last: Throwable? = null
        for (withVision in if (vision) listOf(true, false) else listOf(false)) {
            for ((useGpu, spec2) in attempts) {
                try {
                    val e = createEngine(path, if (useGpu) Backend.GPU() else Backend.CPU(), spec2, withVision)
                    engine = e; engineKey = spec.id + (if (gpu) ":gpu" else ":cpu") + (if (withVision) ":v" else "")
                    engineHasVision = withVision
                    backendLabel = (if (useGpu) "GPU" else "CPU") + (if (spec2) " + décodage spéculatif" else "") + (if (withVision) " + lecture d'image" else "")
                    if (vision && !withVision) markVision(false, "le moteur n'a pas pu être créé avec la lecture d'image : ${last?.message ?: last?.javaClass?.simpleName}")
                    return e
                } catch (t: Throwable) { last = t }
            }
        }
        throw last ?: IllegalStateException("moteur indisponible")
    }

    // ---------- lecture d'image (relecture des textes douteux) ----------

    private val visionFile = File(ctx.filesDir, "vision_probe.state")
    /** Vrai si le test de lecture d'image a réussi : la relecture automatique est alors possible. */
    @Volatile var visionReady: Boolean = false
        private set
    @Volatile var visionStatus: String = ""
        private set

    init {
        // un plantage natif ne s'intercepte pas : « running » écrit avant chaque lecture d'image et effacé après ; s'il reste, la lecture d'image est désactivée
        try {
            if (visionFile.exists()) {
                val t = visionFile.readText().trim()
                when {
                    t == "running" -> { markVision(false, "l'application a planté pendant une lecture d'image : fonction désactivée") }
                    t == "ok" -> { visionReady = true; visionStatus = "Validée : les textes douteux sont relus automatiquement." }
                    t.startsWith("failed|") -> { visionStatus = "Indisponible : " + t.removePrefix("failed|") }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun markVision(ok: Boolean, note: String) {
        visionReady = ok
        visionStatus = if (ok) "Validée : les textes douteux sont relus automatiquement." else "Indisponible : $note"
        try { visionFile.writeText(if (ok) "ok" else "failed|$note") } catch (_: Throwable) {}
    }

    /** Lit une image avec une consigne (texte transcrit) ; null en cas d'échec. Une conversation neuve par image (pas de contexte partagé). */
    @Synchronized fun readImage(png: ByteArray, system: String, user: String, maxTokens: Int = 200): String? {
        val s = settings()
        val spec = LlmModels.specFor(s.engine) ?: return null
        if (!models.isReady(spec) || !visionReady) return null
        main.removeCallbacks(releaseTask)
        try {
            visionFile.writeText("running")
            val eng = ensureEngine(spec, s.useGpu, vision = true)
            if (!engineHasVision) return null
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.05),
                maxOutputToken = maxTokens,
            )
            val reply = eng.createConversation(config).use { conv ->
                withWatchdog(conv) { textOf(conv.sendMessage(Contents.of(listOf(Content.ImageBytes(png), Content.Text(user))))) }
            }
            visionFile.writeText("ok")
            return reply
        } catch (t: Throwable) {
            markVision(false, t.message ?: t.javaClass.simpleName)
            return null
        } finally {
            main.postDelayed(releaseTask, IDLE_RELEASE_MS)
        }
    }

    class VisionTestResult(val ok: Boolean, val text: String, val firstMs: Long, val secondMs: Long, val note: String)

    /** Test de la lecture d'image : un recadrage type (texte manuscrit incliné traversé par un contour) doit être lu correctement. */
    @Synchronized fun testVision(png: ByteArray, expected: List<String>): VisionTestResult {
        val s = settings()
        val spec = LlmModels.specFor(s.engine)?.takeIf { models.isReady(it) } ?: listOf(LlmModels.E4B, LlmModels.E2B).firstOrNull { models.isReady(it) }
            ?: return VisionTestResult(false, "", 0, 0, "Téléchargez d'abord un modèle Gemma dans l'onglet Modèles.")
        main.removeCallbacks(releaseTask)
        try {
            visionFile.writeText("running")
            release()
            val eng = ensureEngine(spec, s.useGpu, vision = true)
            if (!engineHasVision) { markVision(false, "le moteur n'accepte pas les images"); return VisionTestResult(false, "", 0, 0, visionStatus) }
            fun once(): Pair<String, Long> {
                val t0 = System.currentTimeMillis()
                val config = ConversationConfig(systemInstruction = Contents.of(VisionPrompt.system()), samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.05), maxOutputToken = 200)
                val reply = eng.createConversation(config).use { conv ->
                    withWatchdog(conv) { textOf(conv.sendMessage(Contents.of(listOf(Content.ImageBytes(png), Content.Text(VisionPrompt.user("(none)")))))) }
                }
                return reply to (System.currentTimeMillis() - t0)
            }
            val (r1, t1) = once()
            val (_, t2) = once()      // deuxième image d'affilée : c'est le cas qui plantait sur la voie CPU
            val text = VisionPrompt.parse(r1)?.text ?: r1
            val ok = expected.all { text.uppercase().contains(it) }
            if (ok) { visionFile.writeText("ok"); visionReady = true; visionStatus = "Validée : les textes douteux sont relus automatiquement." }
            else markVision(false, "lecture incorrecte pendant le test (« ${text.take(60)} »)")
            return VisionTestResult(ok, text, t1, t2, visionStatus)
        } catch (t: Throwable) {
            markVision(false, t.message ?: t.javaClass.simpleName)
            return VisionTestResult(false, "", 0, 0, visionStatus)
        } finally {
            main.postDelayed(releaseTask, IDLE_RELEASE_MS)
        }
    }

    class BenchResult(val label: String, val ok: Boolean, val initMs: Long, val translateMs: Long, val error: String?)

    /** Mesure de vitesse : charge le modèle avec le backend demandé, traduit six répliques types et rapporte les temps. */
    @Synchronized fun benchmark(spec: ModelSpec, gpu: Boolean): BenchResult {
        release()
        val label = if (gpu) "GPU" else "CPU"
        val path = models.fileOf(spec).absolutePath
        val t0 = System.currentTimeMillis()
        val eng = try { createEngine(path, if (gpu) Backend.GPU() else Backend.CPU(), true) } catch (t: Throwable) {
            return try { createEngine(path, if (gpu) Backend.GPU() else Backend.CPU(), false).let { e2 -> benchWith(e2, label, t0) } } catch (t2: Throwable) { BenchResult(label, false, 0, 0, t2.message ?: t2.javaClass.simpleName) }
        }
        return benchWith(eng, "$label + décodage spéculatif", t0)
    }

    private fun benchWith(eng: Engine, label: String, t0: Long): BenchResult {
        val initMs = System.currentTimeMillis() - t0
        return try {
            val lines = listOf("I never thought I would see you again.", "Don't underestimate me!", "Master, the elders are waiting in the main hall.", "...Who are you? What do you want from me?", "HAHAHA! So the rumors were true after all!", "We have no choice but to fight.")
            val config = ConversationConfig(
                systemInstruction = Contents.of(LlmPrompt.system("en", "fr", promptVariant)),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1),
                maxOutputToken = LlmPrompt.maxOutputTokens(lines),
            )
            val t1 = System.currentTimeMillis()
            val reply = eng.createConversation(config).use { conv -> textOf(conv.sendMessage(LlmPrompt.user(lines, emptyList(), emptyList()))) }
            val ms = System.currentTimeMillis() - t1
            val parsed = LlmPrompt.parse(reply, lines.size)
            BenchResult(label, parsed.count { !it.isNullOrBlank() } == lines.size, initMs, ms, null)
        } catch (t: Throwable) {
            BenchResult(label, false, initMs, 0, t.message ?: t.javaClass.simpleName)
        } finally { try { eng.close() } catch (_: Throwable) {} }
    }

    private fun remember(src: String, tgt: String) {
        history.addLast(src to tgt)
        while (history.size > HISTORY_SIZE) history.removeFirst()
    }

    /** Nouveau chapitre ou nouvelle page web : on oublie le dialogue précédent. */
    @Synchronized fun resetContext() { history.clear(); memory.reset() }

    @Synchronized fun release() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null; engineKey = ""; engineHasVision = false
    }

    companion object {
        const val HISTORY_SIZE = 10
        const val IDLE_RELEASE_MS = 3 * 60 * 1000L
        const val GENERATION_TIMEOUT_MS = 5 * 60 * 1000L
        fun engineId(spec: ModelSpec) = "${spec.id}-ctx1"
    }
}
