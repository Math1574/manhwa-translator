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

    /** Texte libre (hors bulle) sans zone du détecteur, dont le fond est un dessin (mode « ART ») : on le laisse tel quel. */
    fun tooUncertain(kind: String, hasZone: Boolean, hasContainer: Boolean, artBackground: Boolean): Boolean =
        artBackground && !hasZone && !hasContainer && kind != "IN_BUBBLE"
}
