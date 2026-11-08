package app.manhwa.core

import kotlin.math.max

/**
 * Jonctions entre images empilées (les sites de scans découpent un chapitre en images collées les unes aux autres, et le découpage tombe souvent au milieu d'un texte).
 * Pour lire un texte coupé en deux, on traite une image avec le bas de l'image du dessus collé au-dessus d'elle (la « fenêtre ») : le texte coupé y est entier.
 * Chaque texte n'est alors traduit que par une image : celle qui est sous la jonction si le bas du texte (ou de sa bulle) la dépasse (règle identique pour les deux images voisines).
 */
object Seams {
    /** Hauteur maximale (px de l'image) de l'image du dessus jointe à celle-ci. */
    const val CTX_MAX = 480
    /** Au-delà, la fenêtre serait trop lourde à décoder : l'image est traitée seule, comme avant. */
    const val MAX_WINDOW_IMAGE_H = 6000
    /** Tolérance (px) sur la position du bas d'un bloc par rapport à la jonction. */
    const val MARGIN = 4f

    fun contextHeight(prevImageHeight: Int): Int = minOf(CTX_MAX, max(0, prevImageHeight))

    /**
     * Ce texte revient-il à l'image traitée, dans la fenêtre [contexte de hauteur [ctxH] | image de hauteur [imageH]] ?
     * Non s'il est entièrement dans le contexte (c'est l'image du dessus qui le traduit), sauf si sa bulle ou sa zone détectée passe sous la jonction ;
     * non s'il est coupé au bas de l'image, quand [nextTakesOver] (l'image suivante, dont la fenêtre le contient en entier, le traduit).
     * Le bas du texte, de sa bulle ou de sa zone détectée compte : une bulle coupée par le bord, même si ses lignes ne touchent pas le bord, continue dans l'image suivante.
     */
    fun owns(box: RectF, container: RectF?, zone: RectF?, ctxH: Int, imageH: Int, nextTakesOver: Boolean): Boolean {
        val bottom = max(box.bottom, max(container?.bottom ?: 0f, zone?.bottom ?: 0f))
        if (ctxH > 0 && bottom < ctxH - MARGIN) return false
        if (nextTakesOver && bottom >= ctxH + imageH - MARGIN) return false
        return true
    }

    /** L'image suivante prend-elle en charge les blocs coupés au bas de celle-ci ? Oui si elle est collée et assez petite pour avoir une fenêtre. */
    fun nextTakesOver(nextImageHeight: Int?): Boolean = nextImageHeight != null && nextImageHeight in 1..MAX_WINDOW_IMAGE_H

    /** Cette image a-t-elle une fenêtre (image du dessus collée, et elle-même assez petite) ? Retourne la hauteur de contexte, 0 sinon. */
    fun windowContext(imageH: Int, prevImageHeight: Int?): Int =
        if (prevImageHeight == null || imageH > MAX_WINDOW_IMAGE_H || imageH <= 0) 0 else contextHeight(prevImageHeight)
}
