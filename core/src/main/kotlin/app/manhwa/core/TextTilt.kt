package app.manhwa.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rotation du texte d'origine, mesurée sur son encre (pas sur les boîtes de la lecture : celles d'une ligne penchée sont gonflées et ne disent rien de l'angle).
 *
 * Méthode : profil de projection. Pour chaque angle candidat, l'encre est projetée sur la perpendiculaire à la ligne ; quand l'angle est le bon, les lignes de texte se projettent sur de
 * minces bandes serrées et la somme des carrés de l'histogramme est maximale. Un texte droit donne le maximum à 0° : aucune rotation n'est rendue. La mesure n'est retenue que lorsqu'elle est
 * nette (assez d'encre, lignes nettement plus longues qu'épaisses, gain net par rapport à 0°, pic unique), sinon le texte reste droit comme avant.
 */
object TextTilt {
    /** [degrees] > 0 : la ligne descend vers la droite (sens horaire à l'écran, y vers le bas). [gain] : netteté des lignes à cet angle rapportée à celle à 0° (1 = aucune différence). */
    class Result(val degrees: Float, val gain: Float, val length: Float, val thickness: Float)

    /** Angle maximal pris en compte : au-delà, ce n'est plus un texte penché (texte vertical, lettrage en diagonale) et la mesure n'est pas fiable. */
    const val MAX_DEGREES = 30f
    /** Angle minimal rendu : en dessous, la rotation ne se voit pas et le risque d'une mesure fausse l'emporte. */
    const val MIN_DEGREES = 2.5f
    /** Gain minimal de netteté par rapport à 0°. */
    const val MIN_GAIN = 1.12f
    /** Encre minimale (pixels) et longueur minimale d'une ligne, en épaisseurs de ligne. */
    const val MIN_INK = 300
    const val MIN_ASPECT = 2.4f

    /**
     * Angle du texte. [ink] : masque d'encre ([w] × [h]) ; [boxes] : zones des lignes de texte (x0, y0, x1, y1 ; x1 et y1 exclus) dans le même repère. Seule l'encre située dans ces zones compte.
     * Rend null quand le texte est droit, trop court ou la mesure incertaine.
     */
    fun estimate(ink: BooleanArray, w: Int, h: Int, boxes: List<IntArray>): Result? {
        if (boxes.isEmpty()) return null
        // points d'encre des zones (sans compter deux fois un pixel couvert par deux zones)
        val seen = BooleanArray(w * h)
        var xs = IntArray(1024); var ys = IntArray(1024); var n = 0
        for (b in boxes) {
            val x0 = max(0, b[0]); val y0 = max(0, b[1]); val x1 = min(w, b[2]); val y1 = min(h, b[3])
            for (y in y0 until y1) for (x in x0 until x1) {
                val i = y * w + x
                if (!ink[i] || seen[i]) continue
                seen[i] = true
                if (n == xs.size) { xs = xs.copyOf(n * 2); ys = ys.copyOf(n * 2) }
                xs[n] = x; ys[n] = y; n++
            }
        }
        if (n < MIN_INK) return null
        // limite le coût : au plus ~40 000 points
        val stride = max(1, n / 40_000)
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE; var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        for (i in 0 until n) { minY = min(minY, ys[i]); maxY = max(maxY, ys[i]); minX = min(minX, xs[i]); maxX = max(maxX, xs[i]) }
        val span = (maxX - minX + maxY - minY) + 8          // plage de projection : borne large pour toutes les rotations
        val hist = IntArray(2 * span + 2)
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f

        fun score(deg: Float): Double {
            java.util.Arrays.fill(hist, 0)
            val a = deg * PI.toFloat() / 180f; val c = cos(a); val s = sin(a)
            var i = 0
            while (i < n) {
                val v = (ys[i] - cy) * c - (xs[i] - cx) * s
                hist[(v + span).roundToInt().coerceIn(0, hist.size - 1)]++
                i += stride
            }
            var sum = 0.0
            for (c2 in hist) if (c2 != 0) sum += c2.toDouble() * c2
            return sum
        }

        val s0 = score(0f)
        // recherche grossière (1°) puis fine (0,1°) autour du meilleur
        var bestDeg = 0f; var best = s0
        var d = -MAX_DEGREES
        while (d <= MAX_DEGREES + 0.001f) { val sc = score(d); if (sc > best) { best = sc; bestDeg = d }; d += 1f }
        var fine = bestDeg - 1f
        val fineEnd = bestDeg + 1f
        var fineBest = best; var fineDeg = bestDeg
        while (fine <= fineEnd + 0.001f) { val sc = score(fine); if (sc > fineBest) { fineBest = sc; fineDeg = fine }; fine += 0.1f }
        bestDeg = fineDeg; best = fineBest
        if (abs(bestDeg) < MIN_DEGREES || abs(bestDeg) >= MAX_DEGREES - 0.5f) return null
        val gain = (best / max(1.0, s0)).toFloat()
        if (gain < MIN_GAIN) return null

        // pic unique : aucun angle éloigné de plus de 3° du meilleur ne fait presque aussi bien (profil plat = texte en blocs, pas de lignes)
        var d2 = -MAX_DEGREES
        while (d2 <= MAX_DEGREES + 0.001f) {
            if (abs(d2 - bestDeg) > 3f && score(d2) > 0.985 * best) return null
            d2 += 1f
        }

        // longueur et épaisseur des lignes dans le repère tourné : le texte doit être nettement plus long qu'épais (sinon un bloc compact, un mot court : l'angle n'est pas mesurable)
        val a = bestDeg * PI.toFloat() / 180f; val c = cos(a); val s = sin(a)
        var uMin = Float.MAX_VALUE; var uMax = -Float.MAX_VALUE; var vMin = Float.MAX_VALUE; var vMax = -Float.MAX_VALUE
        for (i in 0 until n) {
            val u = (xs[i] - cx) * c + (ys[i] - cy) * s; val v = (ys[i] - cy) * c - (xs[i] - cx) * s
            uMin = min(uMin, u); uMax = max(uMax, u); vMin = min(vMin, v); vMax = max(vMax, v)
        }
        val length = uMax - uMin; val total = vMax - vMin
        val lines = max(1, boxes.size)
        val thickness = total / lines
        if (thickness < 1f || length < MIN_ASPECT * thickness) return null
        return Result(bestDeg, gain, length, thickness)
    }
}
