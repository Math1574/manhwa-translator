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
