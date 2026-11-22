package app.manhwa

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Synopsis des œuvres en français : le texte que le site (ou l'annuaire) donne, le plus souvent en anglais, est traduit une fois par œuvre, sur le téléphone, avec le moteur de traduction choisi
 * pour la lecture (Gemma s'il est prêt, sinon ML Kit), puis gardé avec le synopsis. La traduction ne part que lorsque la fiche de l'œuvre est ouverte, et attend un moment où aucun chapitre ne se
 * traduit (le moteur de langage ne fait qu'une chose à la fois). En attendant, ou si elle n'est pas possible (modèle absent, réponse inexploitable), la fiche montre l'original. Une œuvre n'est
 * essayée qu'une fois par lancement de l'application : un échec ne se répète pas en boucle. Rien ne quitte le téléphone.
 */
class SynopsisFrench(
    private val store: CoverStore,
    private val enabled: () -> Boolean,
    /** Un chapitre se traduit (ou le téléphone chauffe) : on n'ajoute pas de travail au moteur. */
    private val busy: () -> Boolean,
    /** Texte → traduction française, null si ce n'est pas possible. Appelé hors du fil principal. */
    private val translate: (String) -> String?,
    private val log: (JSONObject) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    /** Œuvres dont la traduction est en cours ou attend un moment calme : la fiche l'indique. État observé par l'interface. */
    var pending by mutableStateOf(emptySet<String>())
        private set
    private val tried = ConcurrentHashMap.newKeySet<String>()
    /** Tests automatiques : remplace le moteur par une traduction fixe. */
    @Volatile var testTranslate: ((String) -> String?)? = null

    /** Demande la traduction du synopsis de l'œuvre si elle manque (réglage actif, synopsis trouvé en une autre langue, pas encore essayée). Fil principal. */
    fun request(key: String) {
        if (!enabled() || !store.synopsisNeedsFrench(key) || !tried.add(key)) return
        pending = pending + key
        Thread({
            try { work(key) } finally { main.post { pending = pending - key } }
        }, "synopsis-fr").also { it.isDaemon = true; it.priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    private fun work(key: String) {
        val t0 = SystemClock.uptimeMillis()
        // un chapitre en cours de traduction passe avant : on attend un moment calme (10 min au plus, puis on réessaiera à la prochaine ouverture de la fiche)
        var waited = 0L
        while (busy() && waited < MAX_WAIT_MS) { Thread.sleep(2_000L); waited += 2_000L }
        if (busy()) { tried.remove(key); return }
        val text = store.synopsisOf(key) ?: return
        if (!store.synopsisNeedsFrench(key)) return
        var why = ""
        val fr = try { (testTranslate ?: translate)(text) } catch (t: Throwable) { why = t.javaClass.simpleName + ": " + (t.message ?: "").take(120); null }
        if (fr != null) main.post { store.saveFrench(key, text, fr) }
        try {
            log(JSONObject().put("t", System.currentTimeMillis()).put("type", "synopsisfr").put("series", key.substringAfter('|')).put("ok", fr != null).put("chars", text.length)
                .put("ms", SystemClock.uptimeMillis() - t0).put("why", if (fr != null) "" else why.ifEmpty { "moteur indisponible ou réponse inexploitable" }))
        } catch (_: Throwable) {}
    }

    private companion object {
        const val MAX_WAIT_MS = 10 * 60_000L
    }
}
