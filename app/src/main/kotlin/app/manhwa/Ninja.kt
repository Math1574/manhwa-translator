package app.manhwa

import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature

/**
 * Mode Ninja (navigation privée) : tant qu'il est actif, rien de ce qui se lit ou se traduit n'est enregistré — ni historique, ni progression de lecture, ni bibliothèque,
 * ni journal, ni cache des traductions, ni corrections, ni icônes de sites. Les enregistrements de l'application regardent [on] avant d'écrire.
 * Le navigateur lui-même tourne dans un profil à part (cookies, cache, stockage des sites, historique), détruit à la sortie.
 */
object Ninja {
    @Volatile @JvmField var on = false

    private const val PREFIX = "ninja-"

    /** Le navigateur du téléphone sait-il isoler un profil (WebView récente) ? Sans cela, le mode Ninja ne peut pas être étanche : il n'est pas proposé. */
    fun supported(): Boolean = try { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) } catch (_: Throwable) { false }

    fun newProfileName(): String = PREFIX + System.currentTimeMillis()

    /** Supprime un profil Ninja laissé par une session interrompue (application fermée sans quitter le mode). Fil principal. */
    fun purgeLeftovers() {
        if (!supported()) return
        try {
            val store = ProfileStore.getInstance()
            for (n in store.allProfileNames) if (n.startsWith(PREFIX)) try { store.deleteProfile(n) } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }
}
