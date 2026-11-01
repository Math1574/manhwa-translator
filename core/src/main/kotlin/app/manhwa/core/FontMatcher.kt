package app.manhwa.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** Encre d'une ligne de texte d'origine, recadrée au plus juste (w × h), et son texte OCR. */
class LineInk(val text: String, val mask: BooleanArray, val w: Int, val h: Int)

/** Résultat du dessin d'un texte dans une boîte w × h : masque binaire et déformation horizontale imposée (1 = proportions naturelles). */
class GlyphRender(val mask: BooleanArray, val stretch: Float)

/** Dessine [text] avec la police [family] (+ épaississement et inclinaison), mis à l'échelle pour remplir exactement w × h. */
fun interface GlyphRenderer {
    fun render(text: String, family: String, embolden: Float, skew: Float, w: Int, h: Int): GlyphRender?
}

/**
 * Choix de la police qui ressemble le plus au lettrage d'origine : on dessine le texte d'origine avec chaque police candidate,
 * ajusté à la même boîte, et on garde celle dont la forme recouvre le mieux l'encre d'origine (IoU), avec une pénalité si la police
 * doit être très étirée. Deuxième étape : réglage fin de l'épaisseur et de l'inclinaison pour la meilleure famille.
 */
object FontMatcher {
    data class Match(val family: String, val embolden: Float, val skew: Float, val stretch: Float, val score: Float)

    /** Recadre l'encre d'une ligne (masque de la région w × h, boîte de la ligne x0..x1, y0..y1) ; null si trop pauvre. */
    fun lineInk(ink: BooleanArray, w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int, text: String, pad: Int = 3): LineInk? {
        val ax0 = (x0 - pad).coerceIn(0, w); val ay0 = (y0 - pad).coerceIn(0, h); val ax1 = (x1 + pad).coerceIn(0, w); val ay1 = (y1 + pad).coerceIn(0, h)
        var minX = ax1; var minY = ay1; var maxX = ax0 - 1; var maxY = ay0 - 1; var n = 0
        for (y in ay0 until ay1) for (x in ax0 until ax1) if (ink[y * w + x]) { n++; if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
        val cw = maxX - minX + 1; val ch = maxY - minY + 1
        if (n < 20 || cw < 8 || ch < 6) return null
        val m = BooleanArray(cw * ch) { ink[(minY + it / cw) * w + minX + it % cw] }
        return LineInk(text, m, cw, ch)
    }

    /** Épaisseur moyenne du trait rapportée à la hauteur du masque : 2 × aire / périmètre / hauteur. */
    private fun strokeRatio(m: BooleanArray, w: Int, h: Int): Float {
        var area = 0; var perim = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (!m[y * w + x]) continue
            area++
            if (x == 0 || !m[y * w + x - 1]) perim++
            if (x == w - 1 || !m[y * w + x + 1]) perim++
            if (y == 0 || !m[(y - 1) * w + x]) perim++
            if (y == h - 1 || !m[(y + 1) * w + x]) perim++
        }
        return if (perim == 0) 0f else 2f * area / perim / h
    }

    private fun scoreOf(lines: List<LineInk>, r: GlyphRenderer, family: String, emb: Float, skew: Float): Pair<Float, Float>? {
        var sum = 0f; var stretchSum = 0f; var n = 0
        for (l in lines) {
            val g = r.render(l.text, family, emb, skew, l.w, l.h) ?: continue
            var inter = 0; var uni = 0
            for (i in l.mask.indices) { val a = l.mask[i]; val b = g.mask[i]; if (a && b) inter++; if (a || b) uni++ }
            val iou = if (uni == 0) 0f else inter.toFloat() / uni
            val so = strokeRatio(l.mask, l.w, l.h); val sc = strokeRatio(g.mask, l.w, l.h)
            val weightPenalty = if (so > 0f && sc > 0f) 0.6f * abs(ln(sc / so)) else 0f   // épaisseur du trait : même graisse que l'original
            sum += iou - 0.30f * abs(ln(max(g.stretch, 1e-3f))) - weightPenalty; stretchSum += g.stretch; n++
        }
        return if (n == 0) null else Pair(sum / n, stretchSum / n)
    }

    /**
     * @param lines lignes d'origine avec leur encre (au plus les 3 plus longues sont utilisées)
     * @param families familles candidates
     * @param italicCapable familles pouvant recevoir une inclinaison
     */
    fun best(lines: List<LineInk>, families: List<String>, renderer: GlyphRenderer): Match? {
        val use = lines.sortedByDescending { it.text.length }.take(3)
        if (use.isEmpty()) return null
        var top: Triple<String, Float, Float>? = null // famille, score, étirement
        val ranked = families.mapNotNull { f -> scoreOf(use, renderer, f, 0f, 0f)?.let { Triple(f, it.first, it.second) } }.sortedByDescending { it.second }
        if (ranked.isEmpty()) return null
        top = ranked.first()
        // réglage fin (épaisseur, inclinaison) pour les deux meilleures familles
        var best: Match? = null
        for (cand in ranked.take(2)) {
            for (emb in floatArrayOf(0f, 0.02f, 0.04f)) for (skew in floatArrayOf(0f, -0.2f)) {
                val r = scoreOf(use, renderer, cand.first, emb, skew) ?: continue
                val adj = r.first - 0.04f * abs(skew) / 0.2f - 0.15f * emb   // a priori : texte droit, trait normal, sauf gain net
                if (best == null || adj > best.score) best = Match(cand.first, emb, skew, r.second, adj)
            }
        }
        return best ?: Match(top.first, 0f, 0f, top.third, top.second)
    }
}
