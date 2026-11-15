package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Découpage d'une page haute en tuiles pour le détecteur, et recollage des régions que ce découpage a coupées en deux.
 *
 * Le détecteur ne voit qu'une tuile à la fois (hauteur au plus 1,5 fois la largeur, recouvrement de 15 %). Une bulle ou une zone de texte à cheval sur la limite de deux tuiles en sortait donc
 * en deux morceaux tronqués : le premier finit sur le bas de sa tuile, le second commence sur le haut de la suivante. Une « bulle » tronquée donnait un texte trop petit (la traduction rétrécissait
 * pour tenir dans une ellipse trop courte) ; deux zones de texte tronquées donnaient deux blocs, traduits et placés séparément, qui se superposaient. Les morceaux sont ici recollés.
 */
object DetectionTiles {
    /** Une tuile : lignes [start, end) de l'image. */
    data class Tile(val start: Int, val end: Int)

    /** Hauteur d'une tuile (hors dernière) pour une image de largeur [imageW]. */
    fun tileHeight(imageW: Int): Int = max(640, (imageW * 1.5f).toInt())

    /** Réduction à la lecture de chaque tuile (les très grandes images sont lues à moitié résolution). */
    fun sample(imageW: Int): Int = if (imageW > 1600) 2 else 1

    /** Les tuiles de haut en bas : chacune recouvre de 15 % de sa hauteur la précédente, et la dernière est étirée jusqu'à 5 % de plus plutôt que de laisser une bande étroite. */
    fun tiles(imageW: Int, imageH: Int): List<Tile> {
        val tileH = tileHeight(imageW)
        val out = ArrayList<Tile>()
        var y = 0
        while (true) {
            val end = if (imageH - y <= tileH * 1.05f) imageH else min(imageH, y + tileH)
            out.add(Tile(y, end))
            if (end >= imageH) break
            y = end - (tileH * 0.15f).toInt()
        }
        return out
    }

    /** Écart toléré entre le bord d'une région et la limite d'une tuile (le détecteur s'arrête à un ou deux pixels de la limite, plus quand la tuile est lue à demi résolution). */
    private fun tolerance(imageW: Int): Float = 2f + 2f * sample(imageW)

    /** Recouvrement horizontal de deux boîtes (intersection sur union de leurs étendues en x) : 1 = même colonne, 0 = côte à côte. */
    private fun columnMatch(a: RectF, b: RectF): Float {
        val inter = min(a.right, b.right) - max(a.left, b.left)
        val union = max(a.right, b.right) - min(a.left, b.left)
        return if (inter <= 0f || union <= 0f) 0f else inter / union
    }

    /**
     * Recolle les régions coupées par une limite de tuile et écarte les morceaux redondants, sur les détections brutes de toutes les tuiles (avant la suppression des doublons).
     *  - deux régions de même classe, l'une qui finit sur le bas d'une tuile et l'autre qui commence sur le haut de la suivante, dans la même colonne : une seule région, leur réunion ;
     *  - une région coupée par une tuile, mais entièrement contenue dans une région de même classe qui, elle, va plus loin (l'autre tuile l'a vue en entier) : écartée.
     */
    fun stitch(dets: List<Detection>, imageW: Int, imageH: Int): List<Detection> {
        val tiles = tiles(imageW, imageH)
        if (tiles.size < 2 || dets.size < 2) return dets
        val tol = tolerance(imageW)
        var cur: List<Detection> = dets
        for (k in 0 until tiles.size - 1) {
            val end = tiles[k].end.toFloat(); val start = tiles[k + 1].start.toFloat()
            val pairs = ArrayList<Triple<Int, Int, Float>>()
            for (i in cur.indices) {
                val u = cur[i]
                if (abs(u.box.bottom - end) > tol) continue
                for (j in cur.indices) {
                    if (i == j) continue
                    val l = cur[j]
                    if (l.cls != u.cls || abs(l.box.top - start) > tol) continue
                    val m = columnMatch(u.box, l.box)
                    if (m >= MIN_COLUMN_MATCH) pairs.add(Triple(i, j, m))
                }
            }
            if (pairs.isEmpty()) continue
            val used = BooleanArray(cur.size)
            val merged = ArrayList<Detection>()
            for ((i, j, _) in pairs.sortedByDescending { it.third }) {
                if (used[i] || used[j]) continue
                used[i] = true; used[j] = true
                merged.add(Detection(cur[i].cls, cur[i].box.union(cur[j].box), max(cur[i].score, cur[j].score)))
            }
            cur = cur.filterIndexed { i, _ -> !used[i] } + merged
        }
        // morceaux redondants : coupés par une tuile, mais contenus dans une région de même classe plus complète
        val cutTop = BooleanArray(cur.size) { i -> tiles.drop(1).any { abs(cur[i].box.top - it.start) <= tol } }
        val cutBottom = BooleanArray(cur.size) { i -> tiles.dropLast(1).any { abs(cur[i].box.bottom - it.end) <= tol } }
        val drop = BooleanArray(cur.size)
        for (i in cur.indices) {
            if (!cutTop[i] && !cutBottom[i]) continue
            val d = cur[i]
            for (j in cur.indices) {
                if (i == j || drop[j] || cur[j].cls != d.cls) continue
                val o = cur[j].box
                val inter = d.box.intersection(o)?.area ?: continue
                if (inter < 0.85f * d.box.area) continue
                if ((cutTop[i] && o.top < d.box.top - tol) || (cutBottom[i] && o.bottom > d.box.bottom + tol)) { drop[i] = true; break }
            }
        }
        return cur.filterIndexed { i, _ -> !drop[i] }
    }

    private const val MIN_COLUMN_MATCH = 0.5f
}
