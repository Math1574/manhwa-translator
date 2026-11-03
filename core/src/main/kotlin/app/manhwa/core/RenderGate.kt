package app.manhwa.core

/**
 * Quand ne PAS toucher à l'image. Effacer puis redessiner un texte est destructif : si le « texte » n'en est pas un (motif de broderie, architecture, onomatopée
 * coréenne lue comme du latin), on abîme le dessin. Deux cas où l'image reste intacte :
 *  - la traduction est identique au texte lu (nom propre, interjection, texte mal lu) : il n'y a rien à remplacer ;
 *  - texte hors bulle, sans zone confirmée par le détecteur, sur un dessin : trop risqué.
 */
object RenderGate {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Traduction identique au texte lu, à la casse, aux espaces et à la ponctuation près. */
    fun unchanged(source: String, translated: String): Boolean {
        val a = norm(source); val b = norm(translated)
        return a.isNotEmpty() && a == b
    }

    /**
     * Texte libre (« FREE » : le détecteur a fonctionné et n'a vu ni bulle ni zone) dont le fond est un dessin (mode « ART ») : on le laisse tel quel, sauf s'il s'agit
     * manifestement d'une vraie phrase (au moins 12 lettres et 3 mots : narration sur le dessin). Sans détecteur (kind « UNKNOWN »), aucune règle ne s'applique.
     */
    fun tooUncertain(kind: String, hasZone: Boolean, hasContainer: Boolean, artBackground: Boolean, source: String = ""): Boolean {
        if (kind != "FREE" || hasZone || hasContainer || !artBackground) return false
        val letters = source.count { it.isLetter() }; val words = source.split(Regex("\\s+")).count { w -> w.count { it.isLetter() } >= 2 }
        return !(letters >= 12 && words >= 3)
    }
}
