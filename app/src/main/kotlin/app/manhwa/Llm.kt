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
import app.manhwa.core.IdiomHints
import app.manhwa.core.InsufficientSpace
import app.manhwa.core.ChapterMemory
import app.manhwa.core.LlmModels
import app.manhwa.core.TranslationAlign
import app.manhwa.core.TranslationChecks
import app.manhwa.core.TranslationPost
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
    /** Durée au-delà de laquelle une génération est interrompue. Seul le banc d'essai l'allonge : sur le processeur de l'émulateur de test le grand modèle met près d'une minute par page. */
    @Volatile var generationTimeoutMs: Long = GENERATION_TIMEOUT_MS
    /** Titre de la série (déduit de l'adresse) : aide le modèle à comprendre le genre et les noms. */
    @Volatile var seriesTitle: String? = null
    /** Synopsis de l'œuvre (en français s'il a été traduit) : donné au modèle comme contexte (qui sont les personnages). */
    @Volatile var seriesPremise: String? = null
    /** Noms et termes du chapitre en cours (mémoire) et rapport des contrôles de la dernière page. */
    private val memory = ChapterMemory(isCommonWord = { w -> Lexicon.peek()?.isCommon(w) == true })
    @Volatile var lastReport: List<LineReport> = emptyList()

    /**
     * Mémoire du chapitre (dialogue récent, noms propres) : son propre verrou, tenu un instant seulement. Elle ne partage jamais celui de la traduction, qui reste pris pendant toute une génération
     * (des dizaines de secondes) : l'appel qui l'oublie au changement de page vient du fil principal, qu'il ne doit pas figer (l'application « ne répondait plus » quand on changeait de chapitre pendant une traduction).
     */
    private val ctxLock = Any()
    /** Compte les oublis : une traduction qui était en cours pendant que la page changeait n'ajoute rien à la mémoire du nouveau chapitre. */
    private var ctxEpoch = 0
    private val history = ArrayDeque<Pair<String, String>>()
    private fun recentDialogue(): List<Pair<String, String>> = synchronized(ctxLock) { history.toList() }
    private fun keptNames(): List<String> = synchronized(ctxLock) { memory.namesToKeep() }
    /** Ce que le chapitre a établi (tutoiement, genre du narrateur) quand c'est net : rappelé au modèle, en français seulement. */
    private fun styleNotes(target: String): List<String> = if (target == "fr" && promptVariant >= 4) synchronized(ctxLock) { memory.styleHints() } else emptyList()

    /** Noms propres repérés dans le chapitre (minuscules) : le réparateur de texte lu ne doit pas les « corriger ». */
    fun chapterNames(): Set<String> = keptNames().map { it.lowercase() }.toSet()
    private val main = Handler(Looper.getMainLooper())
    /** Dernier usage du moteur : le déchargement après inactivité ne part que si rien n'a servi depuis. */
    @Volatile private var lastUse = SystemClock.uptimeMillis()
    /** Le déchargement ferme le moteur (plusieurs Go) et attend la fin d'une traduction éventuelle : il se fait hors du fil principal. */
    private val releaseTask = Runnable { thread(name = "mt-gemma-release", isDaemon = true) { releaseIfIdle() } }

    @Synchronized private fun releaseIfIdle() { if (SystemClock.uptimeMillis() - lastUse >= IDLE_RELEASE_MS - 5_000L) release() }
    /** Fin d'un usage du moteur : le déchargement est reprogrammé après le délai d'inactivité. */
    private fun scheduleRelease() { lastUse = SystemClock.uptimeMillis(); main.postDelayed(releaseTask, IDLE_RELEASE_MS) }

    override fun usesGlossaryPrompt(): Boolean = true

    /**
     * Mesure seulement, sans effet sur la traduction : appels au modèle faits par ce fil pour sa dernière traduction, un par entrée « type:durée ms/c création de la conversation ms/i caractères envoyés/o caractères reçus »
     * (types : ask = le groupe, miss = une ligne refaite seule, fix = une ligne corrigée après contrôle, refine = relecture ; load = chargement du moteur s'il a dépassé 0,3 s).
     */
    private val callLog = ThreadLocal<ArrayList<String>>()
    private fun note(kind: String, startMs: Long, createMs: Long, inChars: Int, outChars: Int) {
        try { callLog.get()?.add(kind + ":" + (SystemClock.uptimeMillis() - startMs) + "/c" + createMs + "/i" + inChars + "/o" + outChars) } catch (_: Throwable) {}
    }
    override fun lastCallDetail(): String? = try { callLog.get()?.joinToString(";") } catch (_: Throwable) { null }

    override fun translate(texts: List<String>, source: String, target: String): List<String> =
        translateWithGlossary(texts, source, target, emptyList())

    @Synchronized
    override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> {
        val s = settings()
        val spec = LlmModels.specFor(s.engine) ?: throw IllegalStateException("Aucun modèle Gemma sélectionné")
        if (!models.isReady(spec)) throw IllegalStateException("Modèle Gemma absent : téléchargez-le dans l'onglet Modèles")
        main.removeCallbacks(releaseTask)
        callLog.set(ArrayList())
        val epoch = synchronized(ctxLock) { ctxEpoch }
        try {
            val tLoad = SystemClock.uptimeMillis()
            val eng = ensureEngine(spec, s.useGpu, vision = visionReady && s.autoReread)
            val loadMs = SystemClock.uptimeMillis() - tLoad
            if (loadMs > 300L) try { callLog.get()?.add("load:$loadMs") } catch (_: Throwable) {}
            var out = ask(eng, texts, source, target, glossary)
            out = realign(eng, texts, out, source, target, glossary)
            var final = out.mapIndexed { i, t -> t ?: throw IllegalStateException("Réponse du modèle incomplète (ligne ${i + 1})") }
            if ((refine || s.refinePass) && target == "fr") final = refineDraft(eng, texts, final, glossary)
            final = checkAndFix(eng, texts, final, target, source, glossary)
            if (target == "fr" && promptVariant >= 4) final = final.mapIndexed { i, t -> TranslationPost.rescueStutter(texts[i], TranslationPost.stutter(texts[i], t)) }   // « B-BUT » → « M-MAIS » (lettre répétée fausse ou perdue) ; « Y-YOU… » recopié → « T-TOI… »
            // la page a changé pendant la traduction (le contexte a été oublié) : ce dialogue n'appartient plus au chapitre en cours
            synchronized(ctxLock) { if (epoch == ctxEpoch) texts.indices.forEach { remember(texts[it], final[it]); memory.observe(texts[it], final[it]) } }
            return final
        } finally {
            scheduleRelease() // libère la mémoire (plusieurs Go) quand on ne traduit plus
        }
    }

    /**
     * Lignes absentes de la réponse ou mal placées (le modèle a sauté une ligne et décalé les suivantes, ou mis la traduction d'une ligne sous le numéro d'une autre : voir
     * [TranslationAlign]) : elles sont refaites ensemble, avec les autres lignes du lot comme contexte ; ce qui reste absent ou suspect est refait seul. La réponse brute fautive va au journal.
     */
    private fun realign(eng: Engine, texts: List<String>, first: MutableList<String?>, source: String, target: String, glossary: List<GlossaryEntry>): MutableList<String?> {
        val out = first
        val lex = try { Lexicon.get(ctx) } catch (_: Throwable) { null }
        val names = chapterNames()
        fun bad(within: Set<Int>? = null): java.util.SortedSet<Int> {
            val s = (out.indices.filter { out[it] == null } + try { TranslationAlign.suspects(texts, out, lex, names) } catch (_: Throwable) { emptySet() }).toSortedSet()
            return if (within == null) s else s.filter { it in within }.toSortedSet()
        }
        val redo = bad()
        if (redo.isEmpty()) return out
        try {
            callLog.get()?.add("realign:${redo.size}/${texts.size}")
            callLog.get()?.add("raw:" + lastReply.take(600).replace('\n', '⏎').replace(';', ','))
        } catch (_: Throwable) {}
        val context = texts.indices.filter { it !in redo && out[it] != null }.map { texts[it] to out[it]!! }
        val again = try { ask(eng, redo.map { texts[it] }, source, target, glossary, kind = "realign", page = context) } catch (e: Throwable) { null }
        redo.forEachIndexed { k, i -> out[i] = again?.getOrNull(k) }
        for (i in bad(redo)) out[i] = try { ask(eng, listOf(texts[i]), source, target, glossary, kind = "miss").firstOrNull() } catch (e: Throwable) { null }
        return out
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

    /** Seconde passe : un « éditeur » corrige les contresens et le style littéral du brouillon. En cas de réponse invalide, le brouillon est conservé. */
    private fun refineDraft(eng: Engine, texts: List<String>, draft: List<String>, glossary: List<GlossaryEntry>): List<String> {
        val t0 = SystemClock.uptimeMillis(); var createMs = 0L; var inLen = 0; var outLen = -1
        return try {
            val system = LlmPrompt.refineSystem()
            inLen = system.length
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.15),
                maxOutputToken = LlmPrompt.maxOutputTokens(texts),
            )
            val reply = eng.createConversation(config).use { conv ->
                createMs = SystemClock.uptimeMillis() - t0
                withWatchdog(conv) { val u = LlmPrompt.refineUser(texts, draft, recentDialogue(), glossary, seriesTitle, seriesPremise, keptNames(), styleNotes("fr")); inLen += u.length; textOf(conv.sendMessage(u)) }
            }
            outLen = reply.length
            // une ligne relue vide, ou qui ne correspond plus à son texte (relecture décalée), garde le brouillon
            val edited = LlmPrompt.parse(reply, texts.size, texts).mapIndexed { i, t -> t?.let { LlmPrompt.stripRefineLabel(it) }?.takeIf { it.isNotBlank() } ?: draft[i] }
            val lex = try { Lexicon.get(ctx) } catch (_: Throwable) { null }
            val shifted = try { TranslationAlign.suspects(texts, edited, lex, chapterNames()) } catch (_: Throwable) { emptySet() }
            edited.mapIndexed { i, t -> if (i in shifted) draft[i] else t }
        } catch (e: Throwable) {
            draft
        } finally { note("refine", t0, createMs, inLen, outLen) }
    }

    /**
     * Contrôle chaque ligne (mots anglais restants, contenu perdu, nom modifié, chiffres, répétition…) et refait seule toute ligne qui échoue,
     * une fois, avec le détail des problèmes. La nouvelle version n'est retenue que si elle a moins de problèmes.
     */
    private fun checkAndFix(eng: Engine, texts: List<String>, draft: List<String>, target: String, source: String, glossary: List<GlossaryEntry>): List<String> {
        val names = keptNames()
        // tutoiement et genre du narrateur établis par le chapitre ; chaque ligne de la page y est ajoutée une fois finie, pour que la suivante s'y accorde
        val style = synchronized(ctxLock) { memory.styleCopy() }
        val out = draft.toMutableList(); val reports = ArrayList<LineReport>()
        for (i in out.indices) {
            var probs = TranslationChecks.problems(texts[i], out[i], target, names, style)
            var retried = false; var fixed = false
            if (probs.isNotEmpty()) {
                retried = true
                val again = try { askFix(eng, texts[i], out[i], probs.map { "${it.code}: ${it.detail}" }, source, target, glossary, names, if (target == "fr" && promptVariant >= 4) style.hints() else emptyList()) } catch (e: Throwable) { null }
                if (again != null) {
                    val p2 = TranslationChecks.problems(texts[i], again, target, names, style)
                    if (p2.size < probs.size) { out[i] = again; fixed = p2.isEmpty(); probs = p2 }
                }
            }
            reports.add(LineReport(texts[i], out[i], probs.map { it.code }, retried, fixed))
            if (target == "fr") style.observe(texts[i], out[i], names)
        }
        lastReport = reports
        return out
    }

    private fun askFix(eng: Engine, text: String, previous: String, problems: List<String>, source: String, target: String, glossary: List<GlossaryEntry>, names: List<String>, styleHints: List<String> = emptyList()): String? {
        val t0 = SystemClock.uptimeMillis(); var createMs = 0L; var outLen = -1
        val system = LlmPrompt.system(source, target, promptVariant)
        var inLen = system.length
        try {
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1),
                maxOutputToken = LlmPrompt.maxOutputTokens(listOf(text)),
            )
            val reply = eng.createConversation(config).use { conv ->
                createMs = SystemClock.uptimeMillis() - t0
                val hints = if (promptVariant >= 4 && target == "fr") IdiomHints.find(listOf(text)) else emptyList()
                withWatchdog(conv) { val u = LlmPrompt.fixUser(text, previous, problems, recentDialogue(), glossary, names, hints, styleHints); inLen += u.length; textOf(conv.sendMessage(u)) }
            }
            outLen = reply.length
            return LlmPrompt.parse(reply, 1, listOf(text)).firstOrNull()
        } finally { note("fix", t0, createMs, inLen, outLen) }
    }

    /** Dernière réponse brute du modèle (journal des réponses fautives). */
    private var lastReply = ""

    private fun ask(eng: Engine, texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>, series: String? = seriesTitle, kind: String = "ask", page: List<Pair<String, String>> = emptyList()): MutableList<String?> {
        val t0 = SystemClock.uptimeMillis(); var createMs = 0L; var outLen = -1
        val system = LlmPrompt.system(source, target, promptVariant)
        var inLen = system.length
        try {
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.15),
                maxOutputToken = LlmPrompt.maxOutputTokens(texts),
            )
            val reply = eng.createConversation(config).use { conv ->
                createMs = SystemClock.uptimeMillis() - t0
                val hints = if (promptVariant >= 4 && target == "fr") IdiomHints.find(texts) else emptyList()
                withWatchdog(conv) { val u = LlmPrompt.user(texts, recentDialogue(), glossary, series, keptNames(), hints, styleNotes(target), page, if (target == "fr") seriesPremise else null); inLen += u.length; textOf(conv.sendMessage(u)) }
            }
            outLen = reply.length
            lastReply = reply
            // le modèle recopie parfois la ligne d'origine devant sa traduction (« SOURCE| TRADUCTION ») : la recopie est retirée, et comptée au journal (détail des appels : « echo:2/3 »)
            val raw = LlmPrompt.parse(reply, texts.size)
            val lines = LlmPrompt.parse(reply, texts.size, texts)
            val echoes = raw.indices.count { raw[it] != lines[it] }
            if (echoes > 0) try { callLog.get()?.add("echo:$echoes/${texts.size}") } catch (_: Throwable) {}
            return lines.toMutableList()
        } finally { note(kind, t0, createMs, inLen, outLen) }
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

    private fun remember(src: String, tgt: String) {
        history.addLast(src to tgt)
        while (history.size > HISTORY_SIZE) history.removeFirst()
    }

    /** Tests automatiques : occupe le moteur comme une traduction en cours (le verrou de la traduction reste pris pendant [ms]). */
    fun testBusy(ms: Long) { thread(name = "mt-test-busy", isDaemon = true) { synchronized(this) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} } } }

    /** Nouveau chapitre ou nouvelle page web : on oublie le dialogue précédent. N'attend jamais la fin d'une traduction (appelé depuis le fil principal). */
    fun resetContext() { synchronized(ctxLock) { ctxEpoch++; history.clear(); memory.reset() } }

    @Synchronized fun release() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null; engineKey = ""; engineHasVision = false; engineIsGpu = false
    }

    companion object {
        const val HISTORY_SIZE = 10
        /** Délai d'inactivité avant de décharger le modèle (plusieurs Go) : assez long pour qu'une pause de lecture ne force pas un rechargement de 10 à 30 s. */
        const val IDLE_RELEASE_MS = 10 * 60 * 1000L
        /** Une génération normale dure moins de 20 s : au-delà d'une minute, elle est interrompue (la réponse partielle reste exploitable ligne par ligne). */
        const val GENERATION_TIMEOUT_MS = 60 * 1000L
        /** La version du prompt (4 et suivantes) fait partie de l'identifiant : un prompt nouveau retraduit les pages déjà en cache. */
        fun engineId(spec: ModelSpec) = "${spec.id}-ctx1" + (if (LlmPrompt.CURRENT >= 4) "-p${LlmPrompt.CURRENT}" else "")
    }
}
