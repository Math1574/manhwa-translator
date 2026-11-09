package app.manhwa.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Descripteur de style d'un lettrage : quelques mesures sans dimension, calculées de la même façon sur l'encre d'origine et sur le dessin d'une police candidate,
 * pour pouvoir les comparer. La ressemblance de forme lettre à lettre (IoU) est trop sensible au bruit, aux décalages et au halo : elle choisissait une police différente
 * d'un bloc à l'autre. Ces mesures-ci (graisse, épaisseur des montants et des barres, contraste, orientation des contours…) varient peu d'un texte à l'autre d'une même police
 * et séparent bien les styles.
 *
 * Composantes (indices de [Vec.v]) :
 *  0 graisse (ln de l'épaisseur moyenne du trait / hauteur de lettre)
 *  1 épaisseur des montants verticaux (ln, rapportée à la hauteur)
 *  2 épaisseur des barres horizontales (ln)
 *  3 contraste montants / barres (1 − 2)
 *  4..11 orientation des contours (8 classes de 22,5°, racine de la part d'énergie : 0 = contours verticaux, 4 = contours horizontaux)
 *  12 rondeur : part des contours qui ne sont ni verticaux ni horizontaux
 *  13 remplissage de la boîte
 *  14 densité de trait : longueur de trait par hauteur de lettre et par lettre (complexité des formes)
 */
object StyleFeatures {
    const val DIM = 15
    /** Hauteur normalisée du masque avant mesure : toutes les mesures se font à la même échelle, quelle que soit la taille du texte. */
    const val NORM_H = 48

    class Vec(val v: FloatArray, val weight: Double) {
        operator fun get(i: Int) = v[i]
    }

    /** Masque mis à l'échelle pour que la hauteur typique d'une lettre (refH) vaille [NORM_H] pixels (moyenne par case puis seuil à 50 %) ; retourne masque, largeur, hauteur. */
    private fun normalized(mask: BooleanArray, w: Int, h: Int, refH: Int): Triple<BooleanArray, Int, Int> {
        val s = NORM_H.toFloat() / max(1, refH)
        if (kotlin.math.abs(s - 1f) < 0.01f) return Triple(mask, w, h)
        val nw = max(8, (w * s).toInt()); val nh = max(8, (h * s).toInt())
        val out = BooleanArray(nw * nh)
        for (y in 0 until nh) {
            val y0 = (y / s).toInt().coerceIn(0, h - 1); val y1 = (((y + 1) / s).toInt()).coerceIn(y0 + 1, h)
            for (x in 0 until nw) {
                val x0 = (x / s).toInt().coerceIn(0, w - 1); val x1 = (((x + 1) / s).toInt()).coerceIn(x0 + 1, w)
                var c = 0; var t = 0
                for (yy in y0 until y1) for (xx in x0 until x1) { t++; if (mask[yy * w + xx]) c++ }
                out[y * nw + x] = c * 2 >= t
            }
        }
        return Triple(out, nw, nh)
    }

    /** Quantile [q] (0..1) des longueurs de segments : histogramme de longueurs → longueur. */
    private fun quantile(hist: IntArray, q: Float): Float {
        var total = 0; for (c in hist) total += c
        if (total == 0) return 0f
        val target = total * q; var acc = 0
        for (l in hist.indices) { acc += hist[l]; if (acc >= target) return l.toFloat() }
        return (hist.size - 1).toFloat()
    }

    /** Descripteur d'une ligne d'encre (masque recadré au plus juste, hauteur typique de lettre charH) ; null si l'encre est trop pauvre. */
    fun of(l: LineInk): Vec? = of(l.mask, l.w, l.h, l.charH, glyphCount(l.text))

    /** [glyphs] = nombre de lettres (sans les espaces) du texte, pour la densité de trait ; [refH] = hauteur typique d'une lettre. */
    fun of(mask: BooleanArray, w: Int, h: Int, refH: Int, glyphs: Int): Vec? {
        var n0 = 0; for (b in mask) if (b) n0++
        if (n0 < 60 || w < 8 || h < 8 || refH < 8) return null
        val (m, mw, mh) = normalized(mask, w, h, refH)
        var area = 0; var perim = 0
        for (y in 0 until mh) for (x in 0 until mw) {
            if (!m[y * mw + x]) continue
            area++
            if (x == 0 || !m[y * mw + x - 1]) perim++
            if (x == mw - 1 || !m[y * mw + x + 1]) perim++
            if (y == 0 || !m[(y - 1) * mw + x]) perim++
            if (y == mh - 1 || !m[(y + 1) * mw + x]) perim++
        }
        if (area < 40 || perim == 0) return null
        val weight = 2f * area / perim / NORM_H
        // segments horizontaux (épaisseur des montants) et verticaux (épaisseur des barres)
        val hh = IntArray(mw + 2); val hv = IntArray(mh + 2)
        for (y in 0 until mh) { var run = 0; for (x in 0..mw) { if (x < mw && m[y * mw + x]) run++ else { if (run > 0) hh[run]++; run = 0 } } }
        for (x in 0 until mw) { var run = 0; for (y in 0..mh) { if (y < mh && m[y * mw + x]) run++ else { if (run > 0) hv[run]++; run = 0 } } }
        val stem = max(1f, quantile(hh, 0.30f)) / NORM_H
        val bar = max(1f, quantile(hv, 0.30f)) / NORM_H
        // orientation des contours : gradient du masque légèrement flouté (deux passes de moyenne 3 × 3)
        var img = FloatArray(mw * mh) { if (m[it]) 1f else 0f }
        repeat(2) {
            val o = FloatArray(mw * mh)
            for (y in 0 until mh) for (x in 0 until mw) {
                var s = 0f; var c = 0
                for (dy in -1..1) for (dx in -1..1) { val xx = x + dx; val yy = y + dy; if (xx in 0 until mw && yy in 0 until mh) { s += img[yy * mw + xx]; c++ } }
                o[y * mw + x] = s / c
            }
            img = o
        }
        val bins = DoubleArray(8); var energy = 0.0
        for (y in 1 until mh - 1) for (x in 1 until mw - 1) {
            val gx = img[y * mw + x + 1] - img[y * mw + x - 1]; val gy = img[(y + 1) * mw + x] - img[(y - 1) * mw + x]
            val mag = hypot(gx, gy).toDouble(); if (mag < 0.05) continue
            var a = atan2(gy.toDouble(), gx.toDouble()); if (a < 0) a += PI; if (a >= PI) a -= PI
            // classes centrées sur 0° (gradient horizontal = contour vertical) : décalage d'une demi-classe
            val b = (((a + PI / 16) / (PI / 8)).toInt()) % 8
            bins[b] += mag; energy += mag
        }
        if (energy <= 0.0) return null
        val v = FloatArray(DIM)
        v[0] = ln(weight.coerceAtLeast(0.01f)); v[1] = ln(stem.coerceAtLeast(0.01f)); v[2] = ln(bar.coerceAtLeast(0.01f)); v[3] = v[1] - v[2]
        for (i in 0 until 8) v[4 + i] = sqrt(bins[i] / energy).toFloat()
        v[12] = (1.0 - (bins[0] + bins[4]) / energy).toFloat()
        v[13] = area.toFloat() / (mw * mh)
        v[14] = (perim.toFloat() / 2f / NORM_H / max(1, glyphs))
        return Vec(v, sqrt(area.toDouble()))
    }

    /** Moyenne pondérée de plusieurs descripteurs (lignes d'un bloc, échantillons d'une police). */
    fun mean(vs: List<Vec>): Vec? {
        if (vs.isEmpty()) return null
        val out = DoubleArray(DIM); var wsum = 0.0
        for (x in vs) { for (i in 0 until DIM) out[i] += x.v[i] * x.weight; wsum += x.weight }
        if (wsum <= 0.0) return null
        return Vec(FloatArray(DIM) { (out[it] / wsum).toFloat() }, wsum)
    }

    /** Nombre de lettres (hors espaces et ponctuation) d'un texte. */
    fun glyphCount(text: String): Int = text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
}
