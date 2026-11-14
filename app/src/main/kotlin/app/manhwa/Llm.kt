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
import app.manhwa.core.ReuseOutcome
import app.manhwa.core.TranslationEngine
import app.manhwa.core.VisionPrompt
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
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

    /**
     * Essai « traduction accélérée » : au lieu de relire les consignes de traduction à chaque page, le modèle les lit une fois dans une conversation préparée,
     * et chaque page part d'une copie de celle-ci (même texte, moins de calcul). Null : suit le réglage ; le banc d'essai et la comparaison le forcent.
     */
    @Volatile var reuseOverride: Boolean? = null
    /** Banc d'essai seulement : impose le mode du décodage spéculatif (ON, OFF ou ADAPTIVE) à la création du moteur. */
    @Volatile var specOverride: SpeculativeDecodingMode? = null
    /** Message pour les réglages quand l'essai a posé problème (plantage au lancement précédent, copie impossible sur ce téléphone). */
    @Volatile var reuseStatus: String = ""
        private set
    /** Vrai quand le lancement précédent s'est arrêté en pleine copie de conversation : l'essai doit être désactivé. */
    @Volatile var reuseCrashed: Boolean = false
        private set
    /** Dernière erreur complète du moteur lors d'une copie ratée (pour le banc d'essai) ; [reuseStatus] n'en garde que la première ligne. */
    @Volatile var reuseLastError: String = ""
        private set
    private var reuseFallbacks = 0           // copies ratées depuis le début de la comparaison / du banc d'essai
    private var reuseCalls = 0               // demandes passées par la copie avec le réglage actif
    private var reuseFails = 0
    private var reuseRun = 0                 // échecs d'affilée
    private var reuseOffSession = false      // coupée jusqu'au prochain lancement après trop d'échecs
    private var primed: Conversation? = null
    private var primedKey = ""
    private val reuseGuard = File(ctx.filesDir, "reuse_probe.state")

    init {
        // un plantage natif ne s'intercepte pas : « running » est écrit avant chaque demande en mode accéléré et effacé après ; s'il reste, l'essai est désactivé
        try {
            if (reuseGuard.exists()) {
                if (reuseGuard.readText().trim() == "running") { reuseCrashed = true; reuseStatus = "L'essai de traduction accélérée a fait planter l'application : il a été désactivé." }
                reuseGuard.delete()
            }
        } catch (_: Throwable) {}
    }

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

    /** Charge le moteur à l'avance (tâche de fond) : la première traduction ne paie plus les 10 à 30 s de chargement du modèle. */
    @Synchronized fun warmUp() {
        val s = settings()
        val spec = LlmModels.specFor(s.engine) ?: return
        if (!models.isReady(spec)) return
        main.removeCallbacks(releaseTask)
        try { ensureEngine(spec, s.useGpu, vision = visionReady && s.autoReread) } catch (_: Throwable) {}
        finally { main.postDelayed(releaseTask, IDLE_RELEASE_MS) }
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
        val system = LlmPrompt.system(source, target, promptVariant)
        val sampler = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.15)
        val maxOut = LlmPrompt.maxOutputTokens(texts)
        val prompt = LlmPrompt.user(texts, history.toList(), glossary, series, memory.namesToKeep())
        // mode accéléré (essai) : en cas d'échec, quelle qu'en soit la raison (erreur du moteur, réponse illisible), on retombe sur le chemin normal ci-dessous
        var reused: String? = null
        if (reuseOn()) {
            try {
                val r = askReusing(eng, system, sampler, maxOut, prompt)
                if (texts.isNotEmpty() && LlmPrompt.parse(r, texts.size).all { it == null }) throw IllegalStateException("réponse illisible de la copie")
                reuseNoted(true); reused = r
            } catch (t: Throwable) { reuseFailed(t) }
        }
        val reply: String = reused ?: eng.createConversation(ConversationConfig(systemInstruction = Contents.of(system), samplerConfig = sampler, maxOutputToken = maxOut)).use { conv ->
            withWatchdog(conv) { textOf(conv.sendMessage(prompt)) }
        }
        return LlmPrompt.parse(reply, texts.size).toMutableList()
    }

    /** Le réglage décide, sauf si la comparaison ou le banc d'essai l'impose ; trop d'échecs coupent le réglage jusqu'au prochain lancement. */
    private fun reuseOn(): Boolean = reuseOverride ?: (settings().fastLlm && !reuseOffSession)

    /** Comptage des demandes passées par la copie avec le réglage actif (la comparaison et le banc d'essai comptent à part) : trois échecs d'affilée, ou plus d'un tiers d'échecs sur huit demandes ou plus, coupent l'essai. */
    private fun reuseNoted(ok: Boolean) {
        if (reuseOverride != null) return
        reuseCalls++
        if (ok) { reuseRun = 0; return }
        reuseFails++; reuseRun++
        if (reuseRun >= 3 || (reuseCalls >= 8 && reuseFails * 3 > reuseCalls)) reuseOffSession = true
    }

    private fun dropPrimed() {
        try { primed?.close() } catch (_: Throwable) {}
        primed = null; primedKey = ""
    }

    private fun reuseFailed(t: Throwable) {
        dropPrimed(); reuseFallbacks++; reuseNoted(false)
        val full = t.message ?: t.javaClass.simpleName
        reuseLastError = full
        val why = full.lineSequence().firstOrNull { it.isNotBlank() }?.take(140) ?: t.javaClass.simpleName
        reuseStatus = when {
            reuseOverride != null -> "La copie des consignes a échoué : la traduction normale a pris le relais ($why)."
            reuseOffSession -> "La copie des consignes a échoué trop souvent ($reuseFails fois sur $reuseCalls demandes) : l'essai est coupé jusqu'au prochain lancement de l'application ($why)."
            else -> "La copie des consignes a échoué $reuseFails fois sur $reuseCalls demandes ; la traduction normale a pris le relais pour ces pages ($why)."
        }
    }

    /**
     * Demande en mode accéléré : une copie de la conversation préparée (créée à la première demande : le modèle y lit les consignes une fois, puis elle est gardée)
     * reçoit la page. La limite de sortie se donne par message, car la conversation préparée sert à toutes les pages.
     */
    @OptIn(ExperimentalApi::class)
    private fun askReusing(eng: Engine, system: String, sampler: SamplerConfig, maxOut: Int, prompt: String): String {
        try { reuseGuard.writeText("running") } catch (_: Throwable) {}
        try {
            val key = "$engineKey|${sampler.temperature}|${system.length}|${system.hashCode()}"
            val base: Conversation = primed?.takeIf { primedKey == key && it.isAlive } ?: run {
                dropPrimed()
                eng.createConversation(ConversationConfig(systemInstruction = Contents.of(system), samplerConfig = sampler, prefillPrefaceOnInit = true)).also { primed = it; primedKey = key }
            }
            return base.clone().use { c -> withWatchdog(c) { textOf(c.sendMessage(prompt, maxOutputToken = maxOut)) } }
        } finally { try { reuseGuard.delete() } catch (_: Throwable) {} }
    }

    class ReuseCompareResult(val outcome: ReuseOutcome?, val note: String)

    /**
     * Comparaison sur le téléphone, avec les mêmes répliques : une chauffe (non mesurée), deux mesures du mode normal, la préparation de la copie (non mesurée),
     * puis trois mesures du mode accéléré, échecs compris (un essai raté est refait en mode normal et ce temps est compté). Le dialogue du chapitre en cours n'est pas touché.
     */
    @Synchronized fun compareReuse(): ReuseCompareResult {
        val s = settings()
        val spec = LlmModels.specFor(s.engine)?.takeIf { models.isReady(it) }
            ?: return ReuseCompareResult(null, "Choisissez d'abord Gemma comme moteur de traduction (onglet Modèles).")
        main.removeCallbacks(releaseTask)
        val savedHistory = history.toList(); history.clear()
        val savedOverride = reuseOverride
        reuseFallbacks = 0; reuseStatus = ""
        try {
            val eng = ensureEngine(spec, s.useGpu, vision = visionReady && s.autoReread)
            val lines = benchLines
            class Run(val out: List<String?>, val ms: Long, val fell: Boolean)
            fun once(reuse: Boolean): Run {
                reuseOverride = reuse
                val before = reuseFallbacks
                val t0 = System.currentTimeMillis()
                val out = ask(eng, lines, "en", "fr", emptyList(), series = null)
                return Run(out, System.currentTimeMillis() - t0, reuseFallbacks > before)
            }
            once(false)                                   // chauffe (premiers calculs du moteur) : non mesurée
            val n1 = once(false); val n2 = once(false)    // deux mesures du mode normal
            val prime = once(true)                        // première fois : les consignes sont lues puis gardées (non mesurée)
            val fast = List(3) { once(true) }             // vitesse de croisière
            val all = listOf(n1, n2, prime) + fast
            if (all.any { r -> r.out.any { it == null } }) return ReuseCompareResult(null, "Le modèle a donné une réponse incomplète : refais l'essai.")
            val tries = 1 + fast.size
            val failed = (if (prime.fell) 1 else 0) + fast.count { it.fell }
            val same = lines.indices.count { i -> fast.all { it.out[i] == n1.out[i] } }
            val base = lines.indices.count { n1.out[it] == n2.out[it] }
            val normalMs = (n1.ms + n2.ms) / 2
            val fastMs = fast.map { it.ms }.average().toLong()
            return ReuseCompareResult(ReuseOutcome(normalMs, fastMs, same, base, lines.size, failed, tries), "")
        } catch (t: Throwable) {
            return ReuseCompareResult(null, "Comparaison impossible : ${t.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(160) ?: t.javaClass.simpleName}")
        } finally {
            reuseOverride = savedOverride
            if (!settings().fastLlm) dropPrimed()         // l'essai est désactivé : la copie préparée n'a plus à occuper la mémoire
            history.clear(); history.addAll(savedHistory)
            main.postDelayed(releaseTask, IDLE_RELEASE_MS)
        }
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
        val spec = SpeculativeDecodingConfig(specOverride ?: if (speculative) SpeculativeDecodingMode.ON else SpeculativeDecodingMode.OFF)
        // lecture d'image : encodeur de vision sur GPU (la voie CPU plante au deuxième tour d'image sur certains Snapdragon), une image à la fois
        val cfg = if (vision) EngineConfig(modelPath = path, backend = backend, visionBackend = Backend.GPU(), maxNumTokens = 4096, maxNumImages = 1, cacheDir = ctx.cacheDir.path, speculativeDecodingConfig = spec)
        else EngineConfig(modelPath = path, backend = backend, maxNumTokens = 4096, cacheDir = ctx.cacheDir.path, speculativeDecodingConfig = spec)
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

    /** Six répliques types pour les mesures de vitesse. */
    private val benchLines = listOf("I never thought I would see you again.", "Don't underestimate me!", "Master, the elders are waiting in the main hall.", "...Who are you? What do you want from me?", "HAHAHA! So the rumors were true after all!", "We have no choice but to fight.")

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
            val lines = benchLines
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
        dropPrimed()
        try { engine?.close() } catch (_: Throwable) {}
        engine = null; engineKey = ""; engineHasVision = false
    }

    companion object {
        const val HISTORY_SIZE = 10
        /** Délai d'inactivité avant de décharger le modèle (plusieurs Go) : assez long pour qu'une pause de lecture ne force pas un rechargement de 10 à 30 s. */
        const val IDLE_RELEASE_MS = 10 * 60 * 1000L
        /** Une génération normale dure moins de 20 s : au-delà d'une minute, elle est interrompue (la réponse partielle reste exploitable ligne par ligne). */
        const val GENERATION_TIMEOUT_MS = 60 * 1000L
        fun engineId(spec: ModelSpec) = "${spec.id}-ctx1"
    }
}
