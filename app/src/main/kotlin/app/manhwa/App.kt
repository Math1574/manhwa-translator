package app.manhwa

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.AppSettings
import app.manhwa.core.Glossary
import app.manhwa.core.GlossaryEntry
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
    val llm = LlmModelsManager(ctx)
    val gemma = GemmaTranslator(ctx, llm) { settingsStore.settings }
    init {
        // dès qu'un modèle Gemma est prêt, on le choisit s'il n'y avait que ML Kit : meilleure traduction sans geste de l'utilisateur
        llm.onDownloaded = { spec ->
            settingsStore.update { st ->
                if (st.engine == app.manhwa.core.TranslationEngineChoice.MLKIT) st.copy(engine = if (spec == app.manhwa.core.LlmModels.E4B) app.manhwa.core.TranslationEngineChoice.GEMMA_E4B else app.manhwa.core.TranslationEngineChoice.GEMMA_E2B) else st
            }
        }
    }
    val controller = TranslationController(ctx, this)
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

    fun current(): Glossary = snapshot

    fun add(source: String, target: String) { glossary.addTerm(source, target); commit() }
    fun remove(source: String) { glossary.removeTerm(source); commit() }

    private fun commit() {
        val data = glossary.serialize()
        file.writeText(data)
        snapshot = Glossary.parse(data)
        terms.clear(); terms.addAll(glossary.terms())
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
