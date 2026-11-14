package app.manhwa.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Correspondance entre la zone d'affichage de l'éditeur et le recadrage montré (en pixels de la fenêtre traitée) : l'image recadrée est centrée dans la zone, à l'échelle [scale]
 * (pixels d'écran par pixel d'image) ; le décalage ([offX], [offY]) est celui de son coin haut gauche dans la zone.
 */
class ViewLayout(val scale: Float, val offX: Float, val offY: Float) {
    /** Position à l'écran (dans la zone) du point (x, y) de l'image. */
    fun screenX(x: Float, crop: RectF): Float = offX + (x - crop.left) * scale
    fun screenY(y: Float, crop: RectF): Float = offY + (y - crop.top) * scale

    /** Point de l'image sous la position (px, py) de la zone. */
    fun imageX(px: Float, crop: RectF): Float = crop.left + (px - offX) / scale
    fun imageY(py: Float, crop: RectF): Float = crop.top + (py - offY) / scale
}

/** Zoom et déplacement de la vue de l'éditeur : calculs seuls (le geste à deux doigts, lui, est lu par l'écran). */
object EditorView {
    /** Plus petit recadrage atteignable en zoomant (pixels de l'image). */
    const val MIN_W = 160f
    const val MIN_H = 120f

    fun layout(crop: RectF, areaW: Float, areaH: Float): ViewLayout {
        val cw = max(1f, crop.width); val ch = max(1f, crop.height)
        val s = min(max(1f, areaW) / cw, max(1f, areaH) / ch)
        return ViewLayout(s, (areaW - cw * s) / 2f, (areaH - ch * s) / 2f)
    }

    /**
     * Nouveau recadrage après un geste à deux doigts (ou à un doigt pour déplacer seulement) : le point de l'image qui était sous la position ([fromX], [fromY]) de la zone se retrouve
     * sous ([toX], [toY]), à l'échelle multipliée par [zoom] (plus de 1 : on se rapproche). La taille reste dans les limites, d'au moins [minW] × [minH] et au plus la fenêtre entière
     * (les proportions sont gardées), et le recadrage reste dans la fenêtre de [iw] × [ih] pixels : au bord, la vue s'arrête au lieu de se déformer.
     */
    fun transform(
        crop: RectF, areaW: Float, areaH: Float, fromX: Float, fromY: Float, toX: Float, toY: Float, zoom: Float, iw: Float, ih: Float,
        minW: Float = MIN_W, minH: Float = MIN_H,
    ): RectF {
        if (areaW <= 0f || areaH <= 0f || iw <= 0f || ih <= 0f || !fromX.isFinite() || !fromY.isFinite() || !toX.isFinite() || !toY.isFinite()) return crop
        val cw = max(1f, crop.width); val ch = max(1f, crop.height)
        val before = layout(crop, areaW, areaH)
        val ax = before.imageX(fromX, crop); val ay = before.imageY(fromY, crop)
        val zLow = max(cw / iw, ch / ih)                                                    // plus petit facteur : le recadrage ne dépasse pas la fenêtre
        val zHigh = max(zLow, max(1f, min(cw / min(minW, iw), ch / min(minH, ih))))         // plus grand facteur : la taille minimale (jamais moins de 1 : une vue déjà plus petite ne saute pas)
        val z = (if (zoom.isFinite() && zoom > 0f) zoom else 1f).coerceIn(zLow, zHigh)
        val nw = cw / z; val nh = ch / z
        val after = layout(RectF(0f, 0f, nw, nh), areaW, areaH)
        val left = (ax - (toX - after.offX) / after.scale).coerceIn(0f, max(0f, iw - nw))
        val top = (ay - (toY - after.offY) / after.scale).coerceIn(0f, max(0f, ih - nh))
        return RectF(left, top, left + nw, top + nh)
    }

    /** Région entière de l'image à décoder pour couvrir [crop] (arrondie vers l'extérieur, dans la fenêtre de [iw] × [ih] pixels, d'au moins 1 pixel). */
    fun decodeRegion(crop: RectF, iw: Float, ih: Float): RectF {
        val l = floor(crop.left).coerceIn(0f, max(0f, iw - 1f)); val t = floor(crop.top).coerceIn(0f, max(0f, ih - 1f))
        val r = ceil(crop.right).coerceIn(l + 1f, max(l + 1f, iw)); val b = ceil(crop.bottom).coerceIn(t + 1f, max(t + 1f, ih))
        return RectF(l, t, r, b)
    }
}

/**
 * Historique « défaire / refaire » : on enregistre l'état d'avant chaque modification ([record]) ; [undo] rend le dernier état enregistré (et garde l'état courant pour [redo]),
 * une nouvelle modification efface ce qui pouvait être refait. L'historique est borné : les plus anciens états sont oubliés.
 */
class History<T>(private val limit: Int = 100) {
    private val past = ArrayList<T>()
    private val future = ArrayList<T>()
    private var savedFuture: List<T> = emptyList()      // ce qui pouvait être refait avant le dernier enregistrement (pour [revert])
    val undoCount: Int get() = past.size
    val redoCount: Int get() = future.size
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun record(before: T) {
        past.add(before)
        if (past.size > limit) past.removeAt(0)
        savedFuture = ArrayList(future)
        future.clear()
    }

    /** Rend l'état d'avant la dernière modification ; [current] est gardé pour pouvoir refaire. Null s'il n'y a rien à défaire. */
    fun undo(current: T): T? {
        if (past.isEmpty()) return null
        savedFuture = emptyList()
        future.add(current)
        return past.removeAt(past.lastIndex)
    }

    /** Rend l'état d'après la modification défaite ; [current] est gardé pour pouvoir défaire de nouveau. Null s'il n'y a rien à refaire. */
    fun redo(current: T): T? {
        if (future.isEmpty()) return null
        savedFuture = emptyList()
        past.add(current)
        return future.removeAt(future.lastIndex)
    }

    /** Annule le dernier enregistrement (geste interrompu aussitôt) : rend l'état enregistré, et ce qui pouvait être refait l'est de nouveau. */
    fun revert(): T? {
        if (past.isEmpty()) return null
        val s = past.removeAt(past.lastIndex)
        future.clear(); future.addAll(savedFuture); savedFuture = emptyList()
        return s
    }

    fun clear() { past.clear(); future.clear(); savedFuture = emptyList() }
}
