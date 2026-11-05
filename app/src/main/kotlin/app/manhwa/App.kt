package app.manhwa

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.AppSettings
import app.manhwa.core.BrowsingLibrary
import app.manhwa.core.Glossary
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.NameSuggestions
import app.manhwa.core.SeriesTerm
import app.manhwa.core.ResultCache
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.io.File

class App : Application() {
    lateinit var graph: AppGraph
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

class AppGraph(ctx: Context) {
    val settingsStore = SettingsStore(ctx)
    val cache = ResultCache(File(ctx.filesDir, "cache"), maxEntries = 64, maxDiskBytes = 100L * 1024 * 1024)
    val glossary = GlossaryStore(File(ctx.filesDir, "glossary.tsv"))
    val models = TranslationModels(ctx)
    val library = LibraryStore(File(ctx.filesDir, "library.tsv"))
    val journal = TranslationJournal(File(ctx.filesDir, "journal.jsonl"))
    init {
        library.onChanged = { ResumeShortcut.update(ctx, it) }
        ResumeShortcut.update(ctx, library.library)
    }
    val llm = LlmModelsManager(ctx)
    val paddleModels = PaddleModels(ctx)
    val paddle = PaddleOcr(paddleModels)
    val gemma = GemmaTranslator(ctx, llm) { settingsStore.settings }
    init {
        // dès qu'un modèle Gemma est prêt, on le choisit s'il n'y avait que ML Kit : meilleure traduction sans geste de l'utilisateur
        llm.onDownloaded = { spec ->
            settingsStore.update { st ->
                if (st.engine == app.manhwa.core.TranslationEngineChoice.MLKIT) st.copy(engine = if (spec == app.manhwa.core.LlmModels.E4B) app.manhwa.core.TranslationEngineChoice.GEMMA_E4B else app.manhwa.core.TranslationEngineChoice.GEMMA_E2B) else st
            }
        }
    }
    val gpuTest = GpuTester(ctx, this)
    val blockReader = GemmaBlockReader(gemma) { settingsStore.settings }
    val visionTest = VisionTester(ctx, gemma)
    /** Noms propres probables repérés pendant la lecture (propositions du glossaire) ; remis à zéro quand l'application redémarre. */
    val names = NameSuggestions()
    val controller = TranslationController(ctx, this)
}

/** Historique de lecture, avancement par chapitre et favoris, conservés sur le téléphone (fichier texte). */
class LibraryStore(private val file: File) {
    var library by mutableStateOf(BrowsingLibrary.parse(try { if (file.exists()) file.readText() else null } catch (e: Throwable) { null }))
        private set
    private var dirty = false
    private var lastWrite = 0L
    /** Appelé après chaque enregistrement (le raccourci « Reprendre la lecture » de l'icône s'y met à jour). */
    var onChanged: ((BrowsingLibrary) -> Unit)? = null

    private fun write() {
        try { file.writeText(library.serialize()); dirty = false; lastWrite = System.currentTimeMillis() } catch (_: Throwable) {}
        try { onChanged?.invoke(library) } catch (_: Throwable) {}
    }

    private fun commit(f: (BrowsingLibrary) -> BrowsingLibrary) { library = f(library); write() }

    fun visit(url: String, title: String) = commit { it.visit(url, title, System.currentTimeMillis()) }
    fun toggleFavorite(url: String, title: String) = commit { it.toggleFavorite(url, title, System.currentTimeMillis()) }
    fun removeHistory(url: String) = commit { it.removeHistory(url) }
    fun removeFavorite(url: String) = commit { it.removeFavorite(url) }
    fun clearHistory() = commit { it.clearHistory() }
    fun markDone(url: String, done: Boolean) = commit { it.markDone(url, done, System.currentTimeMillis()) }
    fun toggleSeriesFavorite(key: String, title: String) = commit { it.toggleSeriesFavorite(key, title, System.currentTimeMillis()) }
    fun removeSeries(key: String) = commit { it.removeSeries(key) }

    /** Avancement de la page ouverte : l'état est mis à jour tout de suite, le fichier au plus toutes les 4 s ([flush] à la fermeture). */
    fun progress(url: String, percent: Int) {
        val next = library.setProgress(url, percent, System.currentTimeMillis())
        if (next === library) return
        library = next; dirty = true
        if (System.currentTimeMillis() - lastWrite > 4000) write()
    }

    fun flush() { if (dirty) write() }

    /** Cœur du navigateur : l'œuvre quand l'adresse en désigne une, sinon la page elle-même. */
    fun isHeart(url: String): Boolean {
        val key = BrowsingLibrary.seriesKey(url)
        return if (key != null) library.isSeriesFavorite(key) else library.isFavorite(url)
    }

    fun toggleHeart(url: String, title: String) {
        val key = BrowsingLibrary.seriesKey(url)
        if (key != null) toggleSeriesFavorite(key, app.manhwa.core.SeriesHint.fromUrl(url) ?: title) else toggleFavorite(url, title)
    }
}

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("manhwa", Context.MODE_PRIVATE)
    var settings by mutableStateOf(AppSettings.parse(prefs.getString("settings", null)))
        private set

    fun update(f: (AppSettings) -> AppSettings) {
        settings = f(settings).sanitized()
        prefs.edit().putString("settings", settings.serialize()).apply()
    }
}

/** Glossaire persistant. [current] renvoie une copie immuable, sûre à lire depuis les threads de traitement. */
class GlossaryStore(private val file: File) {
    private var glossary = if (file.exists()) Glossary.parse(file.readText()) else Glossary()
    @Volatile private var snapshot: Glossary = Glossary.parse(glossary.serialize())
    val terms = mutableStateListOf<GlossaryEntry>().apply { addAll(glossary.terms()) }
    /** Termes propres à une œuvre (clé d'œuvre : site + titre). */
    val seriesTerms = mutableStateListOf<SeriesTerm>().apply { addAll(glossary.seriesTerms()) }

    /** Glossaire à utiliser pour une œuvre : termes communs puis termes de l'œuvre ([series] null : termes communs seuls). */
    fun current(series: String? = null): Glossary = snapshot.forSeries(series)

    fun add(source: String, target: String) { glossary.addTerm(source, target); commit() }
    fun remove(source: String) { glossary.removeTerm(source); commit() }
    fun addForSeries(series: String, source: String, target: String) { glossary.addSeriesTerm(series, source, target); commit() }
    fun removeForSeries(series: String, source: String) { glossary.removeSeriesTerm(series, source); commit() }

    /** Correction manuelle d'une bulle : cette traduction sera reprise telle quelle chaque fois que ce texte apparaît. */
    fun addCorrection(sourceBlock: String, translation: String) { glossary.addCorrection(sourceBlock, translation); commit() }

    private fun commit() {
        val data = glossary.serialize()
        file.writeText(data)
        snapshot = Glossary.parse(data)
        terms.clear(); terms.addAll(glossary.terms())
        seriesTerms.clear(); seriesTerms.addAll(glossary.seriesTerms())
    }
}

/** Modèles de traduction ML Kit : téléchargés uniquement sur action explicite de l'utilisateur. */
class TranslationModels(private val ctx: Context) {
    /** L'anglais est intégré à ML Kit ; les autres langues se téléchargent une par une. */
    val languages = listOf("ja" to "Japonais", "ko" to "Coréen", "zh" to "Chinois", "fr" to "Français", "es" to "Espagnol", "de" to "Allemand")
    val status = mutableStateMapOf<String, String>() // ready | absent | downloading | error:…
    private val manager = RemoteModelManager.getInstance()

    fun refresh() {
        manager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { have ->
                val codes = have.map { it.language }.toSet()
                languages.forEach { (code, _) -> if (status[code] != "downloading") status[code] = if (code in codes) "ready" else "absent" }
            }
            .addOnFailureListener { e -> languages.forEach { (code, _) -> status[code] = "error:" + (e.message ?: "inconnu") } }
    }

    fun download(lang: String) {
        if (ctx.filesDir.usableSpace < 150L * 1024 * 1024) {
            status[lang] = "error:Espace de stockage insuffisant (150 Mo libres minimum)"
            return
        }
        status[lang] = "downloading"
        manager.download(TranslateRemoteModel.Builder(lang).build(), DownloadConditions.Builder().build())
            .addOnSuccessListener { status[lang] = "ready" }
            .addOnFailureListener { e -> status[lang] = "error:" + (e.message ?: "échec du téléchargement") }
    }

    fun delete(lang: String) {
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(lang).build())
            .addOnSuccessListener { status[lang] = "absent" }
            .addOnFailureListener { e -> status[lang] = "error:" + (e.message ?: "échec de la suppression") }
    }

    /** Synchrone, à appeler hors du thread principal. */
    fun isDownloaded(lang: String): Boolean =
        lang == "en" || Tasks.await(manager.isModelDownloaded(TranslateRemoteModel.Builder(lang).build()))
}
