package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/**
 * Ordre de traitement des images selon ce que le lecteur regarde : les images visibles d'abord (de haut en bas), puis celles sous l'écran (la plus proche en premier),
 * puis celles au-dessus (la plus proche en premier). Une image qui déborde de l'écran par le haut (on la lit) reste « visible » :
 * seul le bord haut comptait avant, si bien que la première image d'un chapitre, décalée de quelques pixels, passait en dernier.
 * Valeur basse = traitée d'abord.
 */
object ReadingOrder {
    fun priority(top: Double, height: Double, viewportY: Double, viewportH: Double): Int {
        val bottom = top + max(height, 1.0)
        val vb = viewportY + max(viewportH, 1.0)
        return when {
            top < vb && bottom > viewportY -> 50_000 + (top - viewportY).coerceIn(-50_000.0, 50_000.0).toInt()   // visible : de haut en bas
            top >= vb -> 200_000 + min(top - vb, 1_000_000.0).toInt()                                                 // sous l'écran : le plus proche d'abord
            else -> 2_000_000 + min(viewportY - bottom, 1_000_000.0).toInt()                                          // au-dessus de l'écran
        }
    }
}

/**
 * Jonctions entre images empilées (les sites de scans découpent un chapitre en images collées les unes aux autres, et le découpage tombe souvent au milieu d'un texte).
 * Pour lire un texte coupé en deux, on traduit une image avec le bas de l'image du dessus collé au-dessus d'elle (la « fenêtre ») : le texte coupé y est entier.
 * Chaque bloc n'est alors traduit que par une image : celle qui est sous la jonction si le bas du bloc la dépasse (règle identique pour les deux images voisines).
 */
object Seams {
    /** Hauteur maximale (px de l'image) de l'image du dessus jointe à celle-ci. */
    const val CTX_MAX = 480
    /** Au-delà, la fenêtre serait trop lourde à décoder : l'image est traitée seule, comme avant. */
    const val MAX_WINDOW_IMAGE_H = 6000
    /** Tolérance (px) sur la position du bas d'un bloc par rapport à la jonction. */
    const val MARGIN = 4f

    fun contextHeight(prevImageHeight: Int): Int = min(CTX_MAX, max(0, prevImageHeight))

    /**
     * Blocs à traduire pour la fenêtre [contexte de hauteur [ctxH] | image de hauteur [imageH]] :
     * ceux dont le bas est dans l'image ou à cheval sur la jonction du haut (le bas est sous la jonction), sauf, si [nextTakesOver], ceux coupés au bas de l'image
     * (ils continuent dans l'image suivante, dont la fenêtre les contient en entier).
     */
    fun owned(blocks: List<TranslatedBlock>, ctxH: Int, imageH: Int, nextTakesOver: Boolean): List<TranslatedBlock> =
        blocks.filter { b ->
            val bottom = b.box.bottom
            bottom >= ctxH - MARGIN && !(nextTakesOver && bottom >= ctxH + imageH - MARGIN)
        }

    /** L'image suivante prend-elle en charge les blocs coupés au bas de celle-ci ? Oui si elle est collée et assez petite pour avoir une fenêtre. */
    fun nextTakesOver(nextImageHeight: Int?): Boolean = nextImageHeight != null && nextImageHeight in 1..MAX_WINDOW_IMAGE_H

    /** Cette image a-t-elle une fenêtre (image du dessus collée, et elle-même assez petite) ? Retourne la hauteur de contexte, 0 sinon. */
    fun windowContext(imageH: Int, prevImageHeight: Int?): Int =
        if (prevImageHeight == null || imageH > MAX_WINDOW_IMAGE_H || imageH <= 0) 0 else contextHeight(prevImageHeight)
}
