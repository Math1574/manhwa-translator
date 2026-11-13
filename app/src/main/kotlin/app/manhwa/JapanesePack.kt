package app.manhwa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.ConfirmationRequired
import app.manhwa.core.InsufficientSpace
import app.manhwa.core.ModelSpec
import app.manhwa.core.ModelStatus
import app.manhwa.core.ModelStore
import app.manhwa.core.PackFile
import app.manhwa.core.PackManifest
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Module optionnel « lecture du japonais vertical » (manga-ocr, une centaine de Mo) : il n'est plus embarqué dans l'application, ce qui l'allège beaucoup.
 * La liste des fichiers (taille, somme de contrôle, adresse) est écrite dans l'APK par la compilation en ligne ; le téléchargement se fait à la demande, avec reprise et vérification.
 * Sans le module, le japonais est lu par PaddleOCR et ML Kit (comme avant pour tout ce qui n'est pas en colonnes verticales).
 */
class JapanesePack(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "models/ja")
    private val files: List<PackFile> = try { ctx.assets.open("packs/ja.txt").bufferedReader(Charsets.UTF_8).use { PackManifest.parse(it.readText()) } } catch (_: Throwable) { emptyList() }
    private val specs = files.map { ModelSpec("ja-" + it.name, it.url, it.size, it.sha256, it.name) }
    private val store by lazy { ModelStore(dir, freeSpace = { ctx.filesDir.usableSpace }) }

    /** Taille totale du téléchargement, en octets. */
    val totalBytes: Long = files.sumOf { it.size }
    /** Vrai si cette version sait où télécharger le module. */
    val offered: Boolean get() = specs.isNotEmpty()
    /** Vrai si les modèles sont déjà dans l'APK (version de test sur émulateur) : rien à télécharger. */
    val bundled: Boolean by lazy {
        try { listOf("ocr/manga_ocr_encoder_u8.onnx", "ocr/manga_ocr_decoder_u8.onnx", "ocr/manga_ocr_vocab.txt").forEach { ctx.assets.open(it).close() }; true } catch (e: IOException) { false }
    }

    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var error by mutableStateOf<String?>(null); private set
    @Volatile private var readyFlag = false
    private val cancelFlag = AtomicBoolean(false)

    init { refresh() }

    fun refresh() {
        val r = specs.isNotEmpty() && specs.all { store.status(it) == ModelStatus.Ready }
        readyFlag = r; ready = r
    }

    /** Fichier du module s'il est complet et vérifié, sinon null. Peut être appelé depuis n'importe quel fil. */
    fun fileIfReady(name: String): File? = if (readyFlag) File(dir, name).takeIf { it.exists() } else null

    /** À n'appeler qu'après accord de l'utilisateur. */
    fun download() {
        if (busy || specs.isEmpty()) return
        busy = true; error = null; progress = 0f; cancelFlag.set(false)
        thread(name = "ja-pack-download") {
            try {
                var done = 0L
                var lastPermille = -1
                for (spec in specs) {
                    val base = done
                    store.download(spec, confirmed = true, onProgress = { d, _ ->
                        val permille = ((base + d) * 1000 / totalBytes).toInt()
                        if (permille != lastPermille) { lastPermille = permille; progress = (base + d).toFloat() / totalBytes }
                    }, isCancelled = { cancelFlag.get() })
                    done += spec.sizeBytes
                }
            } catch (e: InsufficientSpace) {
                error = "Espace insuffisant : ${e.needed / 1_000_000} Mo nécessaires, ${e.free / 1_000_000} Mo libres"
            } catch (e: ConfirmationRequired) {
                error = "Confirmation requise"
            } catch (e: Exception) {
                error = if (cancelFlag.get()) "Téléchargement interrompu : il reprendra là où il s'est arrêté." else (e.message ?: "Échec du téléchargement")
            } finally {
                busy = false; progress = 0f; refresh()
            }
        }
    }

    fun cancel() { cancelFlag.set(true) }

    /** Supprime les fichiers du module (le lecteur doit avoir libéré ses sessions avant). */
    fun delete() {
        readyFlag = false; ready = false
        specs.forEach { store.delete(it) }
        error = null
        refresh()
    }
}
