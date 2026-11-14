package app.manhwa.core

/**
 * Texte lu sur une page mais laissé de côté (ni traduit ni effacé) : onomatopée, débris, filigrane, bruit, ligne à la lecture peu sûre.
 * La géométrie est celle de [TranslatedBlock] (coordonnées de la fenêtre traitée) : un appui sur ce texte en fait un bloc ajouté à la main, avec la même lecture.
 */
data class DiscardedBlock(
    /** Rang dans la liste de la page (sert à désigner le texte depuis l'affichage). */
    val id: Int,
    val box: RectF,
    val source: String,
    /** Motif de l'écart, en clair. */
    val reason: String,
    val vertical: Boolean,
    val lineHeight: Float,
    val lineBoxes: List<RectF>,
    val lineTexts: List<String>,
    /** « IN_BUBBLE », « FREE » ou vide (voir [TranslatedBlock.kind]), bulle qui contient le texte et zone détectée, s'il y en a. */
    val kind: String = "",
    val bubble: RectF? = null,
    val zone: RectF? = null,
)
