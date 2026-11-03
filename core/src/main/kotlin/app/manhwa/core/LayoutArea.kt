package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/** Zone disponible pour un texte hors bulle. */
object LayoutArea {
    /**
     * Rectangles candidats pour un texte hors bulle posé contre une bulle : au-dessous, à droite, à gauche et au-dessus de la bulle (boîte), dans la zone
     * [area] agrandie. Un bloc compact dans un de ces rectangles se lit mieux que des lignes réparties de part et d'autre de l'ellipse.
     */
    fun candidatesAround(area: RectF, bubble: RectF, margin: Float, extraBelow: Float, imageW: Float, imageH: Float): List<RectF> {
        val out = ArrayList<RectF>()
        val below = RectF(area.left, bubble.bottom + margin, area.right, min(imageH, max(area.bottom, bubble.bottom + margin + extraBelow)))
        val right = RectF(bubble.right + margin, area.top, area.right, area.bottom)
        val left = RectF(area.left, area.top, bubble.left - margin, area.bottom)
        val above = RectF(area.left, area.top, area.right, bubble.top - margin)
        for (r in listOf(below, right, left, above)) if (r.right - r.left >= 40f && r.bottom - r.top >= 20f) out.add(RectF(max(0f, r.left), max(0f, r.top), min(imageW, r.right), min(imageH, r.bottom)))
        return out
    }

    /**
     * L'emprise d'origine du texte agrandie de [grow] (proportion de sa largeur et de sa hauteur) dans chaque direction, sans sortir de l'image ni
     * empiéter sur les obstacles (textes voisins, bulles voisines). Un obstacle déjà recouvert par plus de 15 % de l'emprise d'origine est ignoré :
     * le texte d'origine le chevauchait déjà (texte contre le bord d'une bulle).
     */
    fun expand(base: RectF, grow: Float, obstacles: List<RectF>, imageW: Float, imageH: Float, hard: List<RectF> = emptyList()): RectF {
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
        // obstacles « durs » (texte déjà placé d'un autre bloc) : toujours évités, même s'ils recouvrent l'emprise d'origine (la traduction voisine est plus longue
        // que l'original et s'y est étendue). On garde la coupe qui laisse le plus de surface, à condition qu'il reste de quoi poser du texte.
        for (o in hard) {
            if (!(o.left < r && o.right > l && o.top < b && o.bottom > t)) continue
            data class Cut2(val area: Float, val l: Float, val t: Float, val r: Float, val b: Float)
            val cuts = ArrayList<Cut2>()
            if (o.right < r) cuts.add(Cut2((r - max(l, o.right)) * (b - t), max(l, o.right), t, r, b))
            if (o.left > l) cuts.add(Cut2((min(r, o.left) - l) * (b - t), l, t, min(r, o.left), b))
            if (o.bottom < b) cuts.add(Cut2((r - l) * (b - max(t, o.bottom)), l, max(t, o.bottom), r, b))
            if (o.top > t) cuts.add(Cut2((r - l) * (min(b, o.top) - t), l, t, r, min(b, o.top)))
            val best = cuts.filter { it.r - it.l >= 0.5f * base.width && it.b - it.t >= 0.6f * base.height }.maxByOrNull { it.area } ?: continue
            l = best.l; t = best.t; r = best.r; b = best.b
        }
        return RectF(l, t, r, b)
    }
}
