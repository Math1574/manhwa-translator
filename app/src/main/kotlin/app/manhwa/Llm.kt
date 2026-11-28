package app.manhwa

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateMapOf
import app.manhwa.core.AppSettings
import app.manhwa.core.ConfirmationRequired
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.GpuGuard
import app.manhwa.core.InsufficientSpace
import app.manhwa.core.LineReport
import app.manhwa.core.LlmBackend
import app.manhwa.core.LlmReply
import app.manhwa.core.LlmTranslator
import app.manhwa.core.LlmModels
import app.manhwa.core.LlmPrompt
import app.manhwa.core.ModelSpec
import app.manhwa.core.ModelStatus
import app.manhwa.core.ModelStore
import app.manhwa.core.Synopsis
import app.manhwa.core.SynopsisPrompt
import app.manhwa.core.TranslationEngine
import app.manhwa.core.VisionPrompt
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingMode
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

    /**
     * Génération par le moteur chargé : une conversation neuve par appel, sous la garde du GPU et du délai maximal (voir [withWatchdog]).
     * Appelée seulement depuis [translateWithGlossary], qui tient le verrou du moteur.
     */
    private val backend = LlmBackend { system, user, sampling, maxTokens ->
        val eng = engine ?: throw IllegalStateException("moteur non chargé")
        val t0 = SystemClock.uptimeMillis()
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = sampling.topK, topP = sampling.topP, temperature = sampling.temperature),
            maxOutputToken = maxTokens,
        )
        var setup = 0L
        val text = eng.createConversation(config).use { conv ->
            setup = SystemClock.uptimeMillis() - t0
            withWatchdog(conv) { textOf(conv.sendMessage(user)) }
        }
        LlmReply(text, setup)
    }

    /** Orchestration de la traduction (demande groupée, lignes refaites, contrôles, mémoire du chapitre et de l'œuvre) : dans le cœur, partagée avec le banc d'essai. */
    val core = LlmTranslator(backend) { Lexicon.peek() ?: try { Lexicon.get(ctx) } catch (_: Throwable) { null } }

    /** Version du prompt et relecture : réglables par le banc d'essai de traduction. */
    var promptVariant: Int
        get() = core.promptVariant
        set(v) { core.promptVariant = v }
    var refine: Boolean
        get() = core.refine
        set(v) { core.refine = v }
    /** Durée au-delà de laquelle une génération est interrompue. Seul le banc d'essai l'allonge : sur le processeur de l'émulateur de test le grand modèle met près d'une minute par page. */
    @Volatile var generationTimeoutMs: Long = GENERATION_TIMEOUT_MS
    /** Titre de la série (déduit de l'adresse) : aide le modèle à comprendre le genre et les noms. */
    var seriesTitle: String?
        get() = core.seriesTitle
        set(v) { core.seriesTitle = v }
    /** Synopsis de l'œuvre (en français s'il a été traduit) : donné au modèle comme contexte (qui sont les personnages). */
    var seriesPremise: String?
        get() = core.seriesPremise
        set(v) { core.seriesPremise = v }
    val lastReport: List<LineReport> get() = core.lastReport

    /** Noms propres repérés dans le chapitre (minuscules) : le réparateur de texte lu ne doit pas les « corriger ». */
    fun chapterNames(): Set<String> = core.chapterNames()
    private val main = Handler(Looper.getMainLooper())
    /** Dernier usage du moteur : le déchargement après inactivité ne part que si rien n'a servi depuis. */
    @Volatile private var lastUse = SystemClock.uptimeMillis()
    /** Le déchargement ferme le moteur (plusieurs Go) et attend la fin d'une traduction éventuelle : il se fait hors du fil principal. */
    private val releaseTask = Runnable { thread(name = "mt-gemma-release", isDaemon = true) { releaseIfIdle() } }

    @Synchronized private fun releaseIfIdle() { if (SystemClock.uptimeMillis() - lastUse >= IDLE_RELEASE_MS - 5_000L) release() }
    /** Fin d'un usage du moteur : le déchargement est reprogrammé après le délai d'inactivité. */
    private fun scheduleRelease() { lastUse = SystemClock.uptimeMillis(); main.postDelayed(releaseTask, IDLE_RELEASE_MS) }

    override fun usesGlossaryPrompt(): Boolean = true

    override fun lastCallDetail(): String? = core.lastCallDetail()

    override fun translate(texts: List<String>, source: String, target: String): List<String> =
        translateWithGlossary(texts, source, target, emptyList())

    @Synchronized
    override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> {
        val s = settings()
        val spec = LlmModels.specFor(s.engine) ?: throw IllegalStateException("Aucun modèle Gemma sélectionné")
        if (!models.isReady(spec)) throw IllegalStateException("Modèle Gemma absent : téléchargez-le dans l'onglet Modèles")
        main.removeCallbacks(releaseTask)
        core.beginCallLog()
        try {
            val tLoad = SystemClock.uptimeMillis()
            ensureEngine(spec, s.useGpu, vision = visionReady && s.autoReread)
            val loadMs = SystemClock.uptimeMillis() - tLoad
            if (loadMs > 300L) core.addCallNote("load:$loadMs")
            return core.translate(texts, source, target, glossary, s.refinePass)
        } finally {
            scheduleRelease() // libère la mémoire (plusieurs Go) quand on ne traduit plus
        }
    }

    /** Charge le moteur à l'avance (tâche de fond) : la première traduction ne paie plus les 10 à 30 s de chargement du modèle. */
    @Synchronized fun warmUp() {
        val s = settings()
        val spec = LlmModels.specFor(s.engine) ?: return
        if (!models.isReady(spec)) return
        main.removeCallbacks(releaseTask)
        try { ensureEngine(spec, s.useGpu, vision = visionReady && s.autoReread) } catch (_: Throwable) {}
        finally { scheduleRelease() }
    }

    /** Interrompt une génération qui dure trop longtemps (sécurité) : la réponse partielle reste exploitable ligne par ligne. */
    private fun <T> withWatchdog(conv: com.google.ai.edge.litertlm.Conversation, block: () -> T): T {
        val timer = java.util.Timer("mt-llm-watchdog", true)
        timer.schedule(object : java.util.TimerTask() { override fun run() { try { conv.cancelProcess() } catch (_: Throwable) {} } }, generationTimeoutMs)
        val guarded = engineIsGpu && gpuUnproven() // première génération sur le GPU avec cette version : sous garde (voir plus haut)
        try {
            if (guarded) gpuMark(GpuGuard.running(BuildConfig.BUILD_NUMBER))
            val r = block()
            if (guarded) gpuMark(GpuGuard.proven(BuildConfig.BUILD_NUMBER))
            return r
        } catch (t: Throwable) {
            if (guarded) gpuMark(null) // une exception n'est pas un plantage : rien n'est prouvé, la garde reprendra
            throw t
        } finally { timer.cancel() }
    }

    private fun textOf(m: Message): String =
        m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.ifEmpty { m.toString() }

    /** Description du moteur actif (« GPU + décodage spéculatif adaptatif », « CPU »…), pour les réglages et le journal. */
    @Volatile var backendLabel: String = ""
        private set

    /**
     * Crée et initialise un moteur. Le décodage spéculatif (prédiction de plusieurs jetons) accélère la génération sur CPU comme sur GPU ;
     * il suppose un modèle récent (fichier publié après mai 2026) : en cas d'échec on réessaie sans.
     * Depuis le moteur 0.19 il est « adaptatif » : le moteur mesure en continu, sur ce téléphone et sur ce texte, si prédire plusieurs jetons va plus vite que la génération normale,
     * et ne l'utilise que tant que c'est le cas. La qualité ne change pas : chaque jeton prédit n'est gardé que s'il est identique à celui que le modèle complet vient de choisir lui-même.
     */
    @OptIn(ExperimentalApi::class)
    private fun createEngine(path: String, backend: Backend, speculative: Boolean, vision: Boolean = false): Engine {
        val spec = SpeculativeDecodingConfig(mode = if (speculative) SpeculativeDecodingMode.ADAPTIVE else SpeculativeDecodingMode.OFF)
        // lecture d'image : encodeur de vision sur GPU (la voie CPU plante au deuxième tour d'image sur certains Snapdragon), une image à la fois
        val cfg = if (vision) EngineConfig(modelPath = path, backend = backend, visionBackend = Backend.GPU(), maxNumTokens = 4096, maxNumImages = 1, cacheDir = ctx.cacheDir.path, speculativeDecodingConfig = spec)
        else EngineConfig(modelPath = path, backend = backend, maxNumTokens = 4096, cacheDir = ctx.cacheDir.path, speculativeDecodingConfig = spec)
        return Engine(cfg).also { it.initialize() }
    }

    /** Vrai si le moteur chargé sait lire une image. */
    @Volatile var engineHasVision: Boolean = false
        private set

    // ---------- garde du GPU : premier usage de chaque version ----------

    /**
     * Un plantage natif ne s'intercepte pas : il tue l'application, et le réglage GPU conservé la ferait replanter à chaque traduction (voir [GpuGuard]).
     * Tant que le GPU n'a réussi aucune génération avec cette version, une marque est écrite avant chaque appel natif sur le GPU (création du moteur, génération) et effacée juste après ;
     * au lancement suivant, une marque restée ET un plantage natif confirmé par le système coupent l'accélération GPU au lieu de la laisser replanter.
     */
    private val gpuGuardFile = File(ctx.filesDir, "gpu_guard.state")
    /** Non nul si le lancement précédent est mort d'un plantage natif pendant le premier usage du GPU de cette version : description donnée par le système (pour le journal). */
    @Volatile var gpuTripped: String? = null
        private set
    /** Vrai si le moteur chargé tourne sur le GPU. */
    @Volatile private var engineIsGpu = false

    private fun gpuUnproven(): Boolean = try { !GpuGuard.isProven(gpuGuardFile.readText(), BuildConfig.BUILD_NUMBER) } catch (_: Throwable) { true }
    private fun gpuMark(mark: String?) { try { if (mark == null) gpuGuardFile.delete() else gpuGuardFile.writeText(mark) } catch (_: Throwable) {} }

    init {
        try {
            if (gpuGuardFile.exists()) {
                val mark = gpuGuardFile.readText()
                if (GpuGuard.interrupted(mark, BuildConfig.BUILD_NUMBER) && android.os.Build.VERSION.SDK_INT >= 30) {
                    // le système garde la cause de la mort du lancement précédent : seul un plantage natif coupe le GPU (arrêt par l'utilisateur ou manque de mémoire : non)
                    val last = ctx.getSystemService(android.app.ActivityManager::class.java)?.getHistoricalProcessExitReasons(ctx.packageName, 0, 1)?.firstOrNull()
                    val nativeCrash = last != null && last.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
                    if (GpuGuard.tripped(mark, BuildConfig.BUILD_NUMBER, nativeCrash)) gpuTripped = last?.description ?: "plantage natif"
                }
                // une marque « running » (de ce build ou d'un autre) ne survit pas au lancement ; la preuve « ok » de ce build reste
                if (!GpuGuard.isProven(mark, BuildConfig.BUILD_NUMBER)) gpuGuardFile.delete()
            }
        } catch (_: Throwable) {}
    }

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
                val guarded = useGpu && gpuUnproven()
                try {
                    if (guarded) gpuMark(GpuGuard.running(BuildConfig.BUILD_NUMBER))
                    val e = createEngine(path, if (useGpu) Backend.GPU() else Backend.CPU(), spec2, withVision)
                    if (guarded) gpuMark(null) // le processus a survécu à la création ; la preuve vient de la première génération réussie
                    engine = e; engineKey = spec.id + (if (gpu) ":gpu" else ":cpu") + (if (withVision) ":v" else "")
                    engineIsGpu = useGpu
                    engineHasVision = withVision
                    backendLabel = (if (useGpu) "GPU" else "CPU") + (if (spec2) " + décodage spéculatif adaptatif" else "") + (if (withVision) " + lecture d'image" else "")
                    if (vision && !withVision) markVision(false, "le moteur n'a pas pu être créé avec la lecture d'image : ${last?.message ?: last?.javaClass?.simpleName}")
                    return e
                } catch (t: Throwable) { if (guarded) gpuMark(null); last = t }
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

    /**
     * Traduit en français le synopsis d'une œuvre : une conversation neuve avec sa propre consigne ([SynopsisPrompt]), sans rien lire ni écrire de la mémoire du chapitre ni du dialogue récent.
     * Même moteur que la lecture, donc même verrou : si un chapitre se traduit en même temps, l'un attend l'autre. Null si le modèle n'est pas prêt ou si la réponse n'est pas une traduction entière
     * ([Synopsis.cleanFrench]). Le moteur est déchargé à la fin (une fois par œuvre, la fiche étant ouverte) : la traduction d'un chapitre le recharge à neuf.
     */
    @Synchronized fun translateSynopsis(text: String): String? {
        val s = settings()
        val spec = LlmModels.specFor(s.engine) ?: return null
        if (!models.isReady(spec)) return null
        main.removeCallbacks(releaseTask)
        try {
            val eng = ensureEngine(spec, s.useGpu, vision = visionReady && s.autoReread)
            val config = ConversationConfig(
                systemInstruction = Contents.of(SynopsisPrompt.system()),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                maxOutputToken = SynopsisPrompt.maxOutputTokens(text),
            )
            val reply = eng.createConversation(config).use { conv ->
                withWatchdog(conv) { textOf(conv.sendMessage(SynopsisPrompt.user(text))) }
            }
            return Synopsis.cleanFrench(reply, text)
        } catch (t: Throwable) {
            return null
        } finally {
            // le moteur est déchargé tout de suite : une traduction de chapitre ne repart jamais d'un moteur qui vient de traduire autre chose (la page suivante le recharge à neuf)
            try { release() } catch (_: Throwable) {}
        }
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
            scheduleRelease()
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
            scheduleRelease()
        }
    }

    /** [shortMs] et [outChars] : mesure seulement (durée d'un second appel très court, qui sépare la relecture des consignes du temps de génération ; taille de la réponse du premier). */
    class BenchResult(val label: String, val ok: Boolean, val initMs: Long, val translateMs: Long, val error: String?, val shortMs: Long = -1L, val outChars: Int = 0)

    /** Mesure de vitesse : charge le modèle avec le backend demandé, traduit six répliques types et rapporte les temps. Un essai réussi sur le GPU le prouve pour cette version (garde du GPU). */
    @Synchronized fun benchmark(spec: ModelSpec, gpu: Boolean): BenchResult {
        val r = benchmarkRaw(spec, gpu)
        if (gpu && r.ok && r.label.startsWith("GPU")) gpuMark(GpuGuard.proven(BuildConfig.BUILD_NUMBER))
        return r
    }

    private fun benchmarkRaw(spec: ModelSpec, gpu: Boolean): BenchResult {
        release()
        val label = if (gpu) "GPU" else "CPU"
        val path = models.fileOf(spec).absolutePath
        val t0 = System.currentTimeMillis()
        val eng = try { createEngine(path, if (gpu) Backend.GPU() else Backend.CPU(), true) } catch (t: Throwable) {
            return try { createEngine(path, if (gpu) Backend.GPU() else Backend.CPU(), false).let { e2 -> benchWith(e2, label, t0) } } catch (t2: Throwable) { BenchResult(label, false, 0, 0, t2.message ?: t2.javaClass.simpleName) }
        }
        return benchWith(eng, "$label + décodage spéculatif adaptatif", t0)
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
            // mesure seulement : un second appel très court (même consigne, une réplique) ; sa durée est surtout la relecture des consignes, la différence avec le premier est la génération
            var shortMs = -1L
            try {
                val config2 = ConversationConfig(
                    systemInstruction = Contents.of(LlmPrompt.system("en", "fr", promptVariant)),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1),
                    maxOutputToken = LlmPrompt.maxOutputTokens(listOf("Run!")),
                )
                val t2 = System.currentTimeMillis()
                eng.createConversation(config2).use { conv -> textOf(conv.sendMessage(LlmPrompt.user(listOf("Run!"), emptyList(), emptyList()))) }
                shortMs = System.currentTimeMillis() - t2
            } catch (_: Throwable) {}
            BenchResult(label, parsed.count { !it.isNullOrBlank() } == lines.size, initMs, ms, null, shortMs, reply.length)
        } catch (t: Throwable) {
            BenchResult(label, false, initMs, 0, t.message ?: t.javaClass.simpleName)
        } finally { try { eng.close() } catch (_: Throwable) {} }
    }

    /** Tests automatiques : occupe le moteur comme une traduction en cours (le verrou de la traduction reste pris pendant [ms]). */
    fun testBusy(ms: Long) { thread(name = "mt-test-busy", isDaemon = true) { synchronized(this) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} } } }

    /** Nouveau chapitre ou nouvelle page web : on oublie le dialogue précédent. N'attend jamais la fin d'une traduction (appelé depuis le fil principal). */
    fun resetContext() = core.resetContext()

    @Synchronized fun release() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null; engineKey = ""; engineHasVision = false; engineIsGpu = false
    }

    companion object {
        /** Délai d'inactivité avant de décharger le modèle (plusieurs Go) : assez long pour qu'une pause de lecture ne force pas un rechargement de 10 à 30 s. */
        const val IDLE_RELEASE_MS = 10 * 60 * 1000L
        /** Une génération normale dure moins de 20 s : au-delà d'une minute, elle est interrompue (la réponse partielle reste exploitable ligne par ligne). */
        const val GENERATION_TIMEOUT_MS = 60 * 1000L
        /** La version du prompt (4 et suivantes) fait partie de l'identifiant : un prompt nouveau retraduit les pages déjà en cache. */
        fun engineId(spec: ModelSpec) = "${spec.id}-ctx1" + (if (LlmPrompt.CURRENT >= 4) "-p${LlmPrompt.CURRENT}" else "")
    }
}
