package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/**
 * Ordre de traitement des images selon ce que le lecteur regarde : les images visibles d'abord (de haut en bas), puis celles sous l'écran (la plus proche en premier),
 * puis celles au-dessus (la plus proche en premier). Une image qui déborde de l'écran par le haut (on la lit) reste « visible » :
 * seul le bord haut comptait avant, si bien que la première image d'un chapitre, décalée de quelques pixels, passait en dernier.
 * Valeur basse = traitée d'abord.
 *
 * [index] (rang de l'image dans la page, de haut en bas) départage les images d'une même catégorie : la position relevée quand l'image a fini de charger n'est pas fiable
 * (les images du dessus, pas encore chargées, n'occupent pas encore leur place : plusieurs images se retrouvaient à la même hauteur, et c'est la plus rapide à charger qui passait la première).
 * Le rang, lui, ne change jamais. Sans rang (négatif), la position sert, comme avant.
 */
object ReadingOrder {
    private const val MAX_INDEX = 99_999

    fun priority(top: Double, height: Double, viewportY: Double, viewportH: Double, index: Int = -1): Int {
        val bottom = top + max(height, 1.0)
        val vb = viewportY + max(viewportH, 1.0)
        val rank = index.coerceAtMost(MAX_INDEX)
        return when {
            top < vb && bottom > viewportY -> 50_000 + (if (rank >= 0) rank else (top - viewportY).coerceIn(-50_000.0, 50_000.0).toInt())   // visible : de haut en bas
            top >= vb -> 200_000 + (if (rank >= 0) rank else min(top - vb, 1_000_000.0).toInt())                                                // sous l'écran : le plus proche d'abord
            else -> 2_000_000 + (if (rank >= 0) MAX_INDEX - rank else min(viewportY - bottom, 1_000_000.0).toInt())                              // au-dessus de l'écran : le plus proche d'abord
        }
    }

    /**
     * Vrai si déplacer un bloc de [before] à [after] le fait passer de l'autre côté de [other] (centre au-dessus puis en dessous, ou l'inverse) alors qu'ils se recouvrent
     * horizontalement : les phrases seraient lues à l'envers. Sert à refuser un replacement qui contournerait un voisin par l'autre côté.
     */
    fun crossesNeighbour(before: RectF, after: RectF, other: RectF): Boolean {
        if (min(after.right, other.right) <= max(after.left, other.left)) return false
        return (before.centerY < other.centerY) != (after.centerY < other.centerY)
    }
}
