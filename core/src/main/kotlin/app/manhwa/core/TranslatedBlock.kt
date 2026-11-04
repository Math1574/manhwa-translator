package app.manhwa.core

fun interface TextMeasurer {
    fun width(text: String, sizePx: Float): Float
}

/** [lineHeight] : hauteur d'une ligne de texte d'origine (largeur si vertical), sert à estimer la taille du texte. 0 = inconnue. */
data class TranslatedBlock(
    val id: Int,
    val box: RectF,
    val source: String,
    val translated: String,
    val vertical: Boolean = false,
    val lineHeight: Float = 0f,
    /** Boîtes des lignes d'origine (pour effacer précisément le texte). */
    val lineBoxes: List<RectF> = emptyList(),
    /** Texte OCR de chaque ligne d'origine (même ordre que [lineBoxes]) : sert à choisir une police ressemblant au lettrage. */
    val lineTexts: List<String> = emptyList(),
    /** Texte après réparation des erreurs de lecture (mots collés, chiffres parasites) : ce que le traducteur a reçu. Vide si identique. */
    val repaired: String = "",
    /** Nature du bloc selon le détecteur (« IN_BUBBLE », « FREE », ou vide) et boîte de la bulle qui le contient. */
    val kind: String = "",
    val bubble: RectF? = null,
    /** Zone de texte détectée (ancre du bloc) : toute la zone est effacée, même ce que la lecture n'a pas vu. */
    val zone: RectF? = null,
)
