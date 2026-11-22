package app.manhwa

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.manhwa.core.AiInpainter
import app.manhwa.core.ConfirmationRequired
import app.manhwa.core.InsufficientSpace
import app.manhwa.core.ModelSpec
import app.manhwa.core.ModelStatus
import app.manhwa.core.ModelStore
import app.manhwa.core.PackFile
import app.manhwa.core.PackManifest
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Module optionnel « effacement par IA » : LaMa entraîné sur 300 000 images de mangas et d'animés (AnimeMangaInpainting, licence MIT ; version ONNX à taille libre, Apache-2.0), environ 200 Mo.
 * Il reconstruit le dessin sous les lettres effacées quand un texte est posé sur une image (voir AiFill dans le noyau). Les bulles blanches n'en ont pas besoin.
 * La liste des fichiers (taille, somme de contrôle, adresse) est écrite dans l'APK par la compilation en ligne ; le téléchargement se fait à la demande, avec reprise et vérification.
 */
class InpaintPack(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "models/inpaint")
    private val files: List<PackFile> = try { ctx.assets.open("packs/inpaint.txt").bufferedReader(Charsets.UTF_8).use { PackManifest.parse(it.readText()) } } catch (_: Throwable) { emptyList() }
    private val specs = files.map { ModelSpec("inpaint-" + it.name, it.url, it.size, it.sha256, it.name) }
    private val store by lazy { ModelStore(dir, freeSpace = { ctx.filesDir.usableSpace }) }

    /** Taille totale du téléchargement, en octets. */
    val totalBytes: Long = files.sumOf { it.size }
    /** Vrai si cette version sait où télécharger le module. */
    val offered: Boolean get() = specs.isNotEmpty()

    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var error by mutableStateOf<String?>(null); private set
    @Volatile private var readyFlag = false
    private val cancelFlag = AtomicBoolean(false)
    /** Appelé (sur le fil du téléchargement) quand le module devient prêt ou est supprimé. */
    @Volatile var onChanged: (() -> Unit)? = null

    init { refresh() }

    fun refresh() {
        val r = specs.isNotEmpty() && specs.all { store.status(it) == ModelStatus.Ready }
        val changed = r != readyFlag
        readyFlag = r; ready = r
        if (changed) onChanged?.invoke()
    }

    /** Fichier du modèle s'il est complet et vérifié, sinon null. Peut être appelé depuis n'importe quel fil. */
    fun modelIfReady(): File? = if (readyFlag) files.firstOrNull { it.name.endsWith(".onnx") }?.let { File(dir, it.name) }?.takeIf { it.exists() } else null

    /** À n'appeler qu'après accord de l'utilisateur. */
    fun download() {
        if (busy || specs.isEmpty()) return
        busy = true; error = null; progress = 0f; cancelFlag.set(false)
        thread(name = "inpaint-pack-download") {
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

    /** Supprime les fichiers du module (le modèle doit avoir été fermé avant, voir [LamaInpainter.close]). */
    fun delete() {
        readyFlag = false; ready = false
        specs.forEach { store.delete(it) }
        error = null
        refresh()
        onChanged?.invoke()
    }
}

/**
 * Exécution du modèle de retouche avec ONNX Runtime, sur le processeur. La session (200 Mo de poids) s'ouvre au premier texte posé sur un dessin, puis reste ouverte ;
 * elle se ferme quand le module est supprimé ou le réglage coupé. Un seul calcul à la fois (chacun occupe déjà quatre cœurs).
 * Si le modèle ne s'ouvre pas (fichier abîmé, mémoire), l'effacement classique reprend sans nouvel essai jusqu'au prochain changement du module.
 */
class LamaInpainter(private val model: () -> File?, private val enabled: () -> Boolean) : AiInpainter {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null
    private var imageName = "image"
    private var maskName = "mask"
    @Volatile private var broken = false
    /** Dernière erreur d'ouverture ou de calcul (affichée dans la carte du module). */
    @Volatile var lastError: String? = null; private set
    /** Compteurs pour le journal : textes reconstruits, temps de calcul cumulé (ouverture comprise), échecs. */
    val runs = AtomicInteger(0)
    val busyMs = AtomicLong(0)
    val fails = AtomicInteger(0)

    override fun ready(): Boolean = !broken && enabled() && model() != null

    @Synchronized override fun run(image: FloatArray, mask: FloatArray, w: Int, h: Int): FloatArray? {
        if (broken || !enabled()) return null
        val t0 = SystemClock.uptimeMillis()
        try {
            val s = session ?: open()
            if (s == null) { fails.incrementAndGet(); return null }
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(image), longArrayOf(1, 3, h.toLong(), w.toLong())).use { a ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(mask), longArrayOf(1, 1, h.toLong(), w.toLong())).use { b ->
                    s.run(mapOf(imageName to a, maskName to b)).use { r ->
                        val fb = (r.get(0) as OnnxTensor).floatBuffer
                        FloatArray(fb.remaining()).also { fb.get(it) }
                    }
                }
            }.also { runs.incrementAndGet() }
        } catch (e: Throwable) {
            lastError = "calcul : ${e.message ?: e.javaClass.simpleName}"
            fails.incrementAndGet()
            return null
        } finally {
            busyMs.addAndGet(SystemClock.uptimeMillis() - t0)
        }
    }

    private fun open(): OrtSession? {
        val f = model() ?: return null
        return try {
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                // tailles d'entrée variables : ni réserve de mémoire qui grandit, ni plan de mémoire figé (la mémoire est rendue après chaque calcul)
                setCPUArenaAllocator(false)
                setMemoryPatternOptimization(false)
            }
            env.createSession(f.absolutePath, opts).also { s ->
                val names = s.inputNames.toList()
                maskName = names.firstOrNull { it.contains("mask", ignoreCase = true) } ?: names.getOrElse(1) { "mask" }
                imageName = names.firstOrNull { it != maskName } ?: "image"
                session = s
            }
        } catch (e: Throwable) {
            broken = true
            lastError = "ouverture : ${e.message ?: e.javaClass.simpleName}"
            null
        }
    }

    /** Ferme la session (module supprimé, réglage coupé) ; un nouvel essai d'ouverture redevient possible. */
    @Synchronized fun close() {
        try { session?.close() } catch (_: Throwable) {}
        session = null; broken = false
    }
}
