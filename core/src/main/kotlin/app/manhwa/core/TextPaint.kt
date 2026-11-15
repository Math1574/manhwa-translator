package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Peinture du texte traduit : couleur de remplissage et contour, déduits du lettrage d'origine.
 *
 * Avant : une seule couleur « extrême » (la moyenne des 10 % de pixels les plus sombres ou les plus clairs) était reprise, et toute couleur à moins de 0,45 de luminance du fond devenait
 * noire ou blanche. Un texte violet ou rouge dégradé sortait presque noir ; un texte vert, orange ou cyan sur fond clair sortait noir.
 *
 * Maintenant :
 *  - la couleur d'un lettrage coloré est celle du corps des traits (bande de luminance de 10 à 40 % côté texte), pas celle de ses pixels les plus extrêmes ;
 *  - une couleur trop proche du fond est rapprochée du contraste voulu en gardant sa teinte (un orange devient un orange plus foncé, pas du noir) ; seules les couleurs grises passent au noir ou au blanc ;
 *  - sur dessin, le contour reprend la couleur et l'épaisseur mesurées du halo d'origine quand ce halo est coloré (texte clair cerné de bleu, de rouge, etc.) ; un halo gris reste un contour noir ou blanc pur.
 *
 * Deux idées essayées sur les pages d'essai ont été retirées parce qu'elles se trompaient : l'« inversion de polarité » (un lettrage mesuré presque de la couleur du fond devenait le halo clair :
 * les deux seuls cas touchés étaient des lettrages NOIRS à contour blanc sur fond sombre, vérifiés à fort grossissement, qui sortaient gris) et le dégradé par ligne (faux positif sur une ligne
 * courte de texte noir, et aucun vrai dégradé détecté).
 */
object TextPaint {
    /**
     * Peinture retenue. [fill] : remplissage (RVB opaque). [outline] : contour (RVB opaque), 0 = noir ou blanc selon le remplissage. [outlineRel] : épaisseur totale du contour rapportée à la
     * hauteur de capitale (0 = valeur par défaut). [info] : résumé pour le journal.
     */
    class Spec(val fill: Int, val outline: Int, val outlineRel: Float, val info: String)

    /** Contraste minimal de luminance avec le fond (hors dessin). */
    const val MIN_CONTRAST = 0.45f
    /** Pixels minimaux d'une ligne pour en mesurer la couleur. */
    const val MIN_PIXELS = 60

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b
    private fun lum(c: Int) = ColorMath.luminance(c)
    private fun dist(a: Int, c: Int): Float { val dr = r(a) - r(c); val dg = g(a) - g(c); val db = b(a) - b(c); return sqrt((dr * dr + dg * dg + db * db).toFloat()) }

    /** Couleur grise : l'écart entre ses canaux est faible (noir, blanc, gris : la teinte ne compte pas). */
    fun isNeutral(c: Int): Boolean = max(r(c), max(g(c), b(c))) - min(r(c), min(g(c), b(c))) <= 36

    /**
     * Couleur du corps des traits parmi [n] pixels (RVB) : moyenne de la bande de luminance de 10 à 40 % (texte sombre) ou de 60 à 90 % (texte clair). Ni les pixels les plus extrêmes
     * (reflets, bruit de compression) ni ceux du bord des lettres (mélangés au fond) n'y comptent. Peu de pixels : moyenne de tous.
     */
    internal fun typical(colors: IntArray, n: Int, dark: Boolean): Int {
        if (n <= 0) return 0
        val order = (0 until n).sortedBy { lum(colors[it]) }
        val lo: Int; val hi: Int
        if (n < 12) { lo = 0; hi = n } else if (dark) { lo = (n * 0.10f).toInt(); hi = max(lo + 1, (n * 0.40f).toInt()) } else { lo = (n * 0.60f).toInt(); hi = max(lo + 1, (n * 0.90f).toInt()) }
        var sr = 0L; var sg = 0L; var sb = 0L; var c = 0
        for (k in lo until min(hi, n)) { val p = colors[order[k]]; sr += r(p); sg += g(p); sb += b(p); c++ }
        return if (c == 0) 0 else rgb((sr / c).toInt(), (sg / c).toInt(), (sb / c).toInt())
    }

    /**
     * Couleur du corps des traits de chaque ligne d'origine : [mask] (encre ou cœur des lettres, w × h) dans la boîte de chaque ligne ([boxes] : x0, y0, x1, y1 ; x1 et y1 exclus).
     * null quand la ligne a trop peu d'encre.
     */
    fun measure(px: IntArray, w: Int, h: Int, mask: BooleanArray, boxes: List<IntArray>, darkText: Boolean): List<Int?> = boxes.map { bx ->
        val x0 = max(0, bx[0]); val y0 = max(0, bx[1]); val x1 = min(w, bx[2]); val y1 = min(h, bx[3])
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) if (mask[y * w + x]) n++
        if (n < MIN_PIXELS) null
        else {
            val cols = IntArray(n); var k = 0
            for (y in y0 until y1) for (x in x0 until x1) if (mask[y * w + x]) { cols[k++] = px[y * w + x] and 0xFFFFFF }
            typical(cols, n, darkText)
        }
    }

    // --- teinte conservée : la clarté se déplace, la teinte et la saturation restent
    private class Hsl(val h: Float, val s: Float, val l: Float)

    private fun toHsl(c: Int): Hsl {
        val rf = r(c) / 255f; val gf = g(c) / 255f; val bf = b(c) / 255f
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf)); val l = (mx + mn) / 2f; val d = mx - mn
        if (d < 1e-6f) return Hsl(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) { rf -> ((gf - bf) / d) % 6f; gf -> (bf - rf) / d + 2f; else -> (rf - gf) / d + 4f } * 60f
        return Hsl(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
    }

    private fun fromHsl(hs: Hsl): Int {
        val c = (1f - abs(2f * hs.l - 1f)) * hs.s; val hp = hs.h / 60f; val x = c * (1f - abs(hp % 2f - 1f)); val m = hs.l - c / 2f
        val (r1, g1, b1) = when { hp < 1f -> Triple(c, x, 0f); hp < 2f -> Triple(x, c, 0f); hp < 3f -> Triple(0f, c, x); hp < 4f -> Triple(0f, x, c); hp < 5f -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return rgb(ch(r1), ch(g1), ch(b1))
    }

    /**
     * [fg] ramenée à au moins [minDelta] de luminance d'écart avec [bg] en gardant sa teinte et sa saturation (la clarté s'éloigne du fond). Une couleur grise, ou qui ne peut pas
     * atteindre l'écart, devient noire ou blanche comme avant. Sans effet quand l'écart est déjà suffisant.
     */
    fun withContrast(fg: Int, bg: Int, minDelta: Float): Int {
        val fgc = fg and 0xFFFFFF; val bgc = bg and 0xFFFFFF
        val lb = lum(bgc)
        if (abs(lum(fgc) - lb) >= minDelta) return fgc
        val plain = ColorMath.textColorFor(bgc) and 0xFFFFFF
        if (isNeutral(fgc)) return plain
        val hs = toHsl(fgc)
        val darker = lb > 0.5f
        var l = hs.l
        while (true) {
            l += if (darker) -0.02f else 0.02f
            if (l < 0f || l > 1f) return plain
            val c = fromHsl(Hsl(hs.h, hs.s, l))
            if (abs(lum(c) - lb) >= minDelta) return c
        }
    }

    /**
     * Peinture du bloc. [mode] : mode d'effacement (ART = texte sur dessin). [bg] : fond estimé, [fillMean] : couleur moyenne réellement obtenue après effacement (0 si inconnue). [textColor] :
     * couleur extrême mesurée. [haloColor], [haloPx] : halo mesuré (0 : aucun), [letterHeightPx] : hauteur des lettres d'origine, dans la même unité que [haloPx]. [lines] : couleur du corps des
     * traits de chaque ligne d'origine (null : ligne non mesurable).
     */
    fun decide(mode: Erasure.Mode?, bg: Int, fillMean: Int, textColor: Int, outlined: Boolean, haloColor: Int, haloPx: Float, letterHeightPx: Float, lines: List<Int?>): Spec {
        val art = mode == Erasure.Mode.ART
        val bgText = (if (fillMean != 0) fillMean else bg) and 0xFFFFFF
        var fill = textColor and 0xFFFFFF
        var outline = 0; var outlineRel = 0f
        val notes = ArrayList<String>()
        val halo = haloColor and 0xFFFFFF
        val haloOk = halo != 0 && haloPx >= 1.2f
        // couleur du corps pour un lettrage coloré (les gris restent tels que mesurés : noir et blanc purs)
        val reps = lines.filterNotNull()
        if (reps.isNotEmpty()) {
            var sr = 0L; var sg = 0L; var sb = 0L
            for (l in reps) { sr += r(l); sg += g(l); sb += b(l) }
            val rep = rgb((sr / reps.size).toInt(), (sg / reps.size).toInt(), (sb / reps.size).toInt())
            // la mesure du corps ne doit pas changer de polarité (même côté du fond) ni s'écarter de la couleur extrême de plus d'un demi-contraste
            val sameSide = (lum(rep) - lum(bgText)) * (lum(fill) - lum(bgText)) >= 0f
            if (!isNeutral(rep) || !isNeutral(fill)) if (sameSide && abs(lum(rep) - lum(fill)) <= 0.5f && dist(rep, fill) > 12f) { fill = rep; notes.add("corps des traits") }
        }
        // contraste avec le fond (hors dessin : le contour porte la lisibilité sur un dessin)
        if (!art) {
            val adj = withContrast(fill, bgText, MIN_CONTRAST)
            if (adj != fill) { notes.add("contraste"); fill = adj }
        }
        // contour coloré du lettrage d'origine. Un halo gris reste un contour noir ou blanc pur à l'épaisseur habituelle : la moyenne de l'anneau mélange le bord net du contour au fond et donne
        // un gris sale, plus épais et moins lisible que l'original (vérifié sur texte noir cerné de blanc, sur fond sombre comme sur fond clair).
        if (art && outlined && haloOk && !isNeutral(halo) && abs(lum(halo) - lum(fill)) >= 0.3f) {
            outline = halo
            if (letterHeightPx > 4f) outlineRel = (2f * haloPx / letterHeightPx).coerceIn(0.12f, 0.22f)
            notes.add("contour coloré")
        }
        return Spec(fill, outline, outlineRel, notes.joinToString(", "))
    }
}
