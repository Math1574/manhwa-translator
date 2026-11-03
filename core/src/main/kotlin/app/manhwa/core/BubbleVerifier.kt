package app.manhwa.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vérifie qu'une « bulle » annoncée par le détecteur en est une : le détecteur étiquette parfois du texte libre sur une page blanche ou noire
 * comme une bulle. Une vraie bulle a un contour (trait sombre ou changement net de fond) à distance raisonnable du texte, sur la plus grande part de
 * sa périphérie ; un texte libre n'en a pas. Seul un contour confirmé donne droit au traitement « bulle » (forme, remplissage borné à l'intérieur).
 */
object BubbleVerifier {
    /** Part minimale du pourtour (sur 40 directions) où un contour est trouvé. */
    private const val MIN_SHARE = 0.5f

    fun verify(src: PixelSource, s: Float, bubble: RectF, text: RectF, imageW: Float, imageH: Float): Boolean {
        // une bulle est nettement plus grande que son texte
        if (bubble.area < 1.35f * text.area) return false
        val cx = text.centerX; val cy = text.centerY
        if (!bubble.contains(cx, cy)) return false
        // luminance sur la région de la bulle (une seule lecture)
        val x0 = (max(0f, bubble.left - 12f) * s).toInt().coerceIn(0, src.width - 1); val y0 = (max(0f, bubble.top - 12f) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, bubble.right + 12f) * s).toInt().coerceIn(x0 + 1, src.width); val y1 = (min(imageH, bubble.bottom + 12f) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 16 || h < 16) return false
        val px = src.pixels(x0, y0, w, h)
        val lum = IntArray(w * h) { i -> val c = px[i]; (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8 }
        fun at(x: Float, y: Float): Int {
            val ix = ((x * s).toInt() - x0).coerceIn(0, w - 1); val iy = ((y * s).toInt() - y0).coerceIn(0, h - 1); return lum[iy * w + ix]
        }
        val n = 40
        var edges = 0
        val rx = bubble.width / 2f; val ry = bubble.height / 2f
        val bx = bubble.centerX; val by = bubble.centerY
        for (k in 0 until n) {
            val a = 2.0 * Math.PI * k / n
            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            // distance du centre du texte au bord de la boîte, dans cette direction
            var tMax = Float.MAX_VALUE
            if (dx > 1e-4f) tMax = min(tMax, (bubble.right - cx) / dx) else if (dx < -1e-4f) tMax = min(tMax, (bubble.left - cx) / dx)
            if (dy > 1e-4f) tMax = min(tMax, (bubble.bottom - cy) / dy) else if (dy < -1e-4f) tMax = min(tMax, (bubble.top - cy) / dy)
            if (tMax <= 4f || tMax == Float.MAX_VALUE) continue
            // sortie du rayon hors du rectangle du texte : on ne cherche un contour qu'au-delà du texte (les lettres ne sont pas un contour)
            var tText = Float.MAX_VALUE
            if (dx > 1e-4f) tText = min(tText, (text.right - cx) / dx) else if (dx < -1e-4f) tText = min(tText, (text.left - cx) / dx)
            if (dy > 1e-4f) tText = min(tText, (text.bottom - cy) / dy) else if (dy < -1e-4f) tText = min(tText, (text.top - cy) / dy)
            if (tText == Float.MAX_VALUE) continue
            val t0 = tText + 3f; val t1 = tMax + 8f
            if (t1 - t0 < 6f) continue            // pas de place entre le texte et le bord annoncé : pas de contour visible
            val step = max(1.2f, (t1 - t0) / 40f)
            var prev = at(cx + dx * t0, cy + dy * t0)
            var minL = prev; var maxL = prev; var best = 0
            var t = t0 + step
            while (t <= t1) {
                val l = at(cx + dx * t, cy + dy * t)
                best = max(best, abs(l - prev)); minL = min(minL, l); maxL = max(maxL, l)
                prev = l; t += step
            }
            // contour : saut net de luminance entre deux pas rapprochés (≥ 55), ou trait sombre entre deux zones claires
            if (best >= 55 || (minL < 90 && maxL - minL >= 90)) edges++
        }
        // la queue de la bulle et les parties cachées par un autre élément ne comptent pas : seuil à la moitié du pourtour
        return edges >= (n * MIN_SHARE).toInt()
    }

    /**
     * Vrai si la bulle est un cartouche rectangulaire (son contour ou son fond suit les bords de la boîte détectée), faux pour une bulle ronde, y compris
     * quand son contour est ouvert. Aux quatre coins de la boîte, une ellipse n'a rien : ni trait, ni changement de fond. Un cartouche, si.
     */
    fun isRectangular(src: PixelSource, s: Float, bubble: RectF, imageW: Float, imageH: Float): Boolean {
        val x0 = (max(0f, bubble.left - 14f) * s).toInt().coerceIn(0, src.width - 1); val y0 = (max(0f, bubble.top - 14f) * s).toInt().coerceIn(0, src.height - 1)
        val x1 = (min(imageW, bubble.right + 14f) * s).toInt().coerceIn(x0 + 1, src.width); val y1 = (min(imageH, bubble.bottom + 14f) * s).toInt().coerceIn(y0 + 1, src.height)
        val w = x1 - x0; val h = y1 - y0
        if (w < 24 || h < 24) return false
        val px = src.pixels(x0, y0, w, h)
        fun lum(x: Float, y: Float): Int {
            val ix = ((x * s).toInt() - x0).coerceIn(0, w - 1); val iy = ((y * s).toInt() - y0).coerceIn(0, h - 1)
            val c = px[iy * w + ix]; return (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }
        val cx = bubble.centerX; val cy = bubble.centerY; val rx = bubble.width / 2f; val ry = bubble.height / 2f
        var hits = 0; var tested = 0
        // pour chaque coin de la boîte : le coin lui-même et deux points du bord tout près du coin (là où une ellipse inscrite est loin du bord)
        for (sx in floatArrayOf(-1f, 1f)) for (sy in floatArrayOf(-1f, 1f)) for (fr in arrayOf(floatArrayOf(1f, 1f), floatArrayOf(0.86f, 1f), floatArrayOf(1f, 0.86f))) {
            val tx = sx * rx * fr[0]; val ty = sy * ry * fr[1]
            val dist = sqrt(tx * tx + ty * ty); val dx = tx / dist; val dy = ty / dist
            val tEll = 1f / sqrt((dx / rx) * (dx / rx) + (dy / ry) * (dy / ry))
            if (dist - tEll < 8f) continue                       // trop près de l'ellipse pour trancher
            tested++
            val inside = lum(cx + dx * dist * 0.93f, cy + dy * dist * 0.93f); val outside = lum(cx + dx * dist * 1.07f, cy + dy * dist * 1.07f)
            var minL = 255; var t = dist * 0.9f
            while (t <= dist * 1.06f) { minL = min(minL, lum(cx + dx * t, cy + dy * t)); t += max(1f, dist * 0.01f) }
            // trait sombre sur le bord de la boîte, ou changement de fond net de part et d'autre
            if ((minL < 90 && max(inside, outside) - minL >= 70) || abs(inside - outside) >= 45) hits++
        }
        return tested >= 6 && hits >= (tested * 0.75f).toInt().coerceAtLeast(5)
    }
}
