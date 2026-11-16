package app.manhwa.core

/**
 * Garde du premier usage du GPU de chaque version de l'application (voir GemmaTranslator) : les décisions, sans rien d'Android, pour les tester sans téléphone.
 *
 * Un plantage natif ne s'intercepte pas : il tue l'application, et le réglage GPU conservé la ferait replanter à chaque traduction.
 * Tant que le GPU n'a réussi aucune génération avec CETTE version (numéro du build), la marque « running|numéro » est écrite avant chaque appel natif sur le GPU
 * et effacée juste après ; « ok|numéro » remplace la marque après une génération réussie (plus de garde pour cette version).
 */
object GpuGuard {
    fun running(build: Int) = "running|$build"
    fun proven(build: Int) = "ok|$build"

    /** Vrai si la marque lue prouve que le GPU a déjà réussi une génération avec cette version : plus besoin de garde. */
    fun isProven(mark: String?, build: Int): Boolean = mark?.trim() == proven(build)

    /** Vrai si la marque lue est « running » pour CETTE version : le lancement précédent s'est arrêté pendant un appel GPU (cause à confirmer auprès du système). */
    fun interrupted(mark: String?, build: Int): Boolean = mark?.trim() == running(build)

    /**
     * Vrai s'il faut couper le GPU au lancement : la marque « running » est restée pour CETTE version (le lancement précédent est mort pendant un appel GPU)
     * ET le système confirme un plantage natif. Sans cette confirmation (arrêt par l'utilisateur, manque de mémoire, ancien Android), rien n'est coupé :
     * un arrêt n'est pas un plantage, et couper le GPU à tort ferait perdre sa vitesse.
     */
    fun tripped(mark: String?, build: Int, nativeCrashConfirmed: Boolean): Boolean = nativeCrashConfirmed && interrupted(mark, build)
}
