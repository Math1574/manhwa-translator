package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/** Zone disponible pour un texte hors bulle. */
object LayoutArea {
    /**
     * L'emprise d'origine du texte agrandie de [grow] (proportion de sa largeur et de sa hauteur) dans chaque direction, sans sortir de l'image ni
     * empiéter sur les obstacles (textes voisins, bulles voisines). Un obstacle déjà recouvert par plus de 15 % de l'emprise d'origine est ignoré :
     * le texte d'origine le chevauchait déjà (texte contre le bord d'une bulle).
     */
    fun expand(base: RectF, grow: Float, obstacles: List<RectF>, imageW: Float, imageH: Float): RectF {
        var l = max(0f, base.left - base.width * grow); var t = max(0f, base.top - base.height * grow)
        var r = min(imageW, base.right + base.width * grow); var b = min(imageH, base.bottom + base.height * grow)
        for (o in obstacles) {
            val overBase = base.intersection(o)?.area ?: 0f
            if (overBase > 0.15f * base.area) continue
            if (!(o.left < r && o.right > l && o.top < b && o.bottom > t)) continue
            // quatre coupes possibles : on garde celle qui conserve le plus de surface (la base reste toujours dedans)
            data class Cut(val area: Float, val l: Float, val t: Float, val r: Float, val b: Float)
            val cuts = ArrayList<Cut>()
            if (o.right <= base.left + 0.5f * base.width) cuts.add(Cut((r - max(l, o.right)) * (b - t), max(l, o.right), t, r, b))
            if (o.left >= base.right - 0.5f * base.width) cuts.add(Cut((min(r, o.left) - l) * (b - t), l, t, min(r, o.left), b))
            if (o.bottom <= base.top + 0.5f * base.height) cuts.add(Cut((r - l) * (b - max(t, o.bottom)), l, max(t, o.bottom), r, b))
            if (o.top >= base.bottom - 0.5f * base.height) cuts.add(Cut((r - l) * (min(b, o.top) - t), l, t, r, min(b, o.top)))
            val best = cuts.filter { it.l <= base.left + 1f && it.t <= base.top + 1f && it.r >= base.right - 1f && it.b >= base.bottom - 1f }.maxByOrNull { it.area } ?: continue
            l = best.l; t = best.t; r = best.r; b = best.b
        }
        return RectF(l, t, r, b)
    }
}
