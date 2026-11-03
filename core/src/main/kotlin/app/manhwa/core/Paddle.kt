package app.manhwa.core

import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ScoredBox(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float, val score: Float, val thickness: Float = 0f,
    /** Inclinaison de la ligne en degrés (0 = horizontale, positif = descend vers la droite) et rectangle orienté : centre, longueur, épaisseur (0 s'il n'y en a pas). */
    val angle: Float = 0f, val ocx: Float = 0f, val ocy: Float = 0f, val olen: Float = 0f, val othick: Float = 0f,
) {
    /** Vrai si la ligne est inclinée : on la lit sur un recadrage redressé plutôt que sur sa boîte alignée sur les axes (qui déborde sur les lignes voisines). */
    val oriented: Boolean get() = olen > 0f

    /** Coins du rectangle orienté (haut-gauche, haut-droit, bas-droit, bas-gauche), en coordonnées de la carte ; x0, y1… ci-dessus en sont l'enveloppe. */
    fun orientedCorners(): FloatArray? {
        if (!oriented) return null
        val a = Math.toRadians(angle.toDouble()); val ux = Math.cos(a).toFloat(); val uy = Math.sin(a).toFloat()
        val vx = -uy; val vy = ux
        val hl = olen / 2f; val ht = othick / 2f
        return floatArrayOf(
            ocx - hl * ux - ht * vx, ocy - hl * uy - ht * vy,
            ocx + hl * ux - ht * vx, ocy + hl * uy - ht * vy,
            ocx + hl * ux + ht * vx, ocy + hl * uy + ht * vy,
            ocx - hl * ux + ht * vx, ocy - hl * uy + ht * vy,
        )
    }

    val width: Float get() = x1 - x0
    val height: Float get() = y1 - y0
}

/** Post-traitement de la détection de texte « DB » (PaddleOCR) : carte de probabilité → boîtes de lignes. */
object DbPost {
    /**
     * @param prob carte de probabilité (h lignes de w valeurs)
     * Composantes connexes de la carte binarisée, boîtes alignées sur les axes, puis « unclip » : la carte DB est
     * volontairement rétrécie, on agrandit donc chaque boîte d'une marge proportionnelle à sa surface / son périmètre.
     */
    fun boxes(prob: FloatArray, w: Int, h: Int, thresh: Float = 0.3f, boxThresh: Float = 0.5f, unclip: Float = 1.6f, minSide: Float = 3f): List<ScoredBox> {
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val out = ArrayList<ScoredBox>()
        for (start in 0 until w * h) {
            if (seen[start] || prob[start] <= thresh) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var minX = w; var minY = h; var maxX = 0; var maxY = 0; var sum = 0.0
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                sum += prob[i]
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (!seen[j] && prob[j] > thresh) { seen[j] = true; queue[tail++] = j }
                }
            }
            val bw = (maxX - minX + 1).toFloat(); val bh = (maxY - minY + 1).toFloat()
            val score = (sum / tail).toFloat()
            if (min(bw, bh) < minSide || score < boxThresh || tail < 6) continue
            val d = bw * bh * unclip / (2f * (bw + bh))
            val o = orientedRect(queue, tail, w, unclip)
            if (o != null && abs(o.angle) in 2f..35f && o.len >= 1.8f * o.thick) {
                // ligne inclinée : enveloppe du rectangle orienté (bien plus serrée que la boîte de la composante agrandie) et recadrage redressé à la lecture
                val sb = ScoredBox(0f, 0f, 0f, 0f, score, o.thick, o.angle, o.cx, o.cy, o.len, o.thick)
                val c = sb.orientedCorners()!!
                val ex0 = max(0f, minOf(c[0], c[2], c[4], c[6])); val ey0 = max(0f, minOf(c[1], c[3], c[5], c[7]))
                val ex1 = min(w.toFloat(), maxOf(c[0], c[2], c[4], c[6])); val ey1 = min(h.toFloat(), maxOf(c[1], c[3], c[5], c[7]))
                if (ex1 - ex0 >= 3f && ey1 - ey0 >= 3f) { out.add(sb.copy(x0 = ex0, y0 = ey0, x1 = ex1, y1 = ey1)); continue }
            }
            out.add(ScoredBox(max(0f, minX - d), max(0f, minY - d), min(w.toFloat(), maxX + 1 + d), min(h.toFloat(), maxY + 1 + d), score, orientedThickness(queue, tail, w, unclip)))
        }
        return out.sortedWith(compareBy({ it.y0 }, { it.x0 }))
    }

    private class Oriented(val cx: Float, val cy: Float, val angle: Float, val len: Float, val thick: Float)

    /** Rectangle orienté d'une composante (axes principaux de ses pixels) avec l'agrandissement « unclip » : longueur, épaisseur, centre et angle en degrés (axe de lecture, -90..90). */
    private fun orientedRect(pixels: IntArray, n: Int, w: Int, unclip: Float): Oriented? {
        if (n < 12) return null
        var mx = 0.0; var my = 0.0
        for (k in 0 until n) { mx += pixels[k] % w; my += pixels[k] / w }
        mx /= n; my /= n
        var cxx = 0.0; var cyy = 0.0; var cxy = 0.0
        for (k in 0 until n) { val dx = pixels[k] % w - mx; val dy = pixels[k] / w - my; cxx += dx * dx; cyy += dy * dy; cxy += dx * dy }
        val theta = 0.5 * Math.atan2(2 * cxy, cxx - cyy)
        val ux = Math.cos(theta); val uy = Math.sin(theta)
        var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE; var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
        for (k in 0 until n) {
            val dx = pixels[k] % w - mx; val dy = pixels[k] / w - my
            val u = dx * ux + dy * uy; val v = -dx * uy + dy * ux
            if (u < minU) minU = u; if (u > maxU) maxU = u; if (v < minV) minV = v; if (v > maxV) maxV = v
        }
        val len = (maxU - minU + 1).toFloat(); val thick = (maxV - minV + 1).toFloat()
        // l'axe de lecture est l'axe long ; angle ramené entre -90 et 90 degrés
        var ang = Math.toDegrees(theta)
        if (thick > len) return null                         // ce n'est pas une ligne : pas de rectangle orienté
        while (ang > 90) ang -= 180; while (ang <= -90) ang += 180
        val d = len * thick * unclip / (2f * (len + thick))
        val cu = (minU + maxU) / 2; val cv = (minV + maxV) / 2
        val ccx = (mx + cu * ux - cv * uy).toFloat(); val ccy = (my + cu * uy + cv * ux).toFloat()
        return Oriented(ccx, ccy, ang.toFloat(), len + 2f * d, thick + 2f * d)
    }

    /**
     * Épaisseur de la ligne mesurée perpendiculairement à son axe principal (analyse en composantes principales des pixels de la composante), avec le
     * même « unclip » que les boîtes : une ligne inclinée a une boîte englobante bien plus haute que le texte, son épaisseur reste celle des lettres.
     */
    private fun orientedThickness(pixels: IntArray, n: Int, w: Int, unclip: Float): Float {
        if (n < 12) return 0f
        var mx = 0.0; var my = 0.0
        for (k in 0 until n) { mx += pixels[k] % w; my += pixels[k] / w }
        mx /= n; my /= n
        var cxx = 0.0; var cyy = 0.0; var cxy = 0.0
        for (k in 0 until n) { val dx = pixels[k] % w - mx; val dy = pixels[k] / w - my; cxx += dx * dx; cyy += dy * dy; cxy += dx * dy }
        val theta = 0.5 * Math.atan2(2 * cxy, cxx - cyy)          // axe principal
        val ux = Math.cos(theta); val uy = Math.sin(theta)
        var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE; var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
        for (k in 0 until n) {
            val dx = pixels[k] % w - mx; val dy = pixels[k] / w - my
            val u = dx * ux + dy * uy; val v = -dx * uy + dy * ux
            if (u < minU) minU = u; if (u > maxU) maxU = u; if (v < minV) minV = v; if (v > maxV) maxV = v
        }
        val len = (maxU - minU + 1).toFloat(); val thick = (maxV - minV + 1).toFloat()
        val (a, b) = if (len >= thick) Pair(len, thick) else Pair(thick, len)       // épaisseur = le petit côté
        return b + 2f * (a * b * unclip / (2f * (a + b)))
    }

    /** Dimensions d'entrée du détecteur : multiples de 32, au moins 32. */
    fun inputSize(w: Int, h: Int): Pair<Int, Int> = Pair(max(32, ceil(w / 32.0).toInt() * 32), max(32, ceil(h / 32.0).toInt() * 32))
}

/** Décodage CTC glouton du modèle de reconnaissance. */
object Ctc {
    /** Jeu de caractères : l'indice 0 est le « blanc » CTC, puis le dictionnaire, puis éventuellement l'espace. */
    fun charset(dictLines: List<String>, useSpace: Boolean): List<String> =
        dictLines.map { it.trimEnd('\n', '\r') }.filter { it.isNotEmpty() || false } + (if (useSpace) listOf(" ") else emptyList())

    /** @param probs [steps][classes] aplati ; renvoie le texte et la confiance moyenne des pas émis. */
    fun decode(probs: FloatArray, steps: Int, classes: Int, charset: List<String>): Pair<String, Float> {
        val sb = StringBuilder(); var prev = -1; var sum = 0.0; var n = 0
        for (t in 0 until steps) {
            var best = 0; var bv = probs[t * classes]
            for (c in 1 until classes) { val v = probs[t * classes + c]; if (v > bv) { bv = v; best = c } }
            if (best != 0 && best != prev) {
                val k = best - 1
                if (k < charset.size) { sb.append(charset[k]); sum += bv; n++ }
            }
            prev = best
        }
        return Pair(sb.toString(), if (n == 0) 0f else (sum / n).toFloat())
    }

    /** Taille d'entrée de la reconnaissance : hauteur fixe, largeur proportionnelle (bornée). */
    fun inputSize(w: Int, h: Int, imgH: Int, maxW: Int = 1600): Pair<Int, Int> {
        val ww = ceil(imgH * w.toDouble() / max(1, h)).toInt().coerceIn(8, maxW)
        return Pair(imgH, ww)
    }
}

/** Fusion des lectures de deux moteurs d'OCR (ML Kit et PaddleOCR) : on garde tout texte vu par l'un ou l'autre, la lecture la plus sûre l'emporte. */
object OcrMerge {
    /** Part des mots d'une lecture (après réparation) qui sont de vrais mots anglais : départage deux lectures d'un même texte (un lecteur peut être très sûr de lui et se tromper). */
    fun plausibility(text: String, lex: EnglishLexicon, names: Set<String> = emptySet()): Float {
        val words = Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(OcrRepair.repair(text, lex, names)).map { it.value }.filter { it.length >= 2 || it.equals("I", true) || it.equals("A", true) }.toList()
        if (words.isEmpty()) return 0.5f
        return words.count { lex.known(it.lowercase()) || it.lowercase() in names }.toFloat() / words.size
    }

    fun merge(mlkit: List<OcrLine>, paddle: List<OcrLine>, script: String, lex: EnglishLexicon? = null): List<OcrLine> {
        val all = ArrayList<Pair<OcrLine, Boolean>>() // (ligne, vient de Paddle)
        mlkit.forEach { all.add(it to false) }
        paddle.forEach { all.add(it to true) }
        val parent = IntArray(all.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (i in all.indices) for (j in i + 1 until all.size) {
            if (all[i].second == all[j].second) continue // on ne relie que des lignes de moteurs différents
            if (all[i].first.box.overlapRatio(all[j].first.box) > 0.4f) parent[find(i)] = find(j)
        }
        val cjk = script != "en"
        val result = ArrayList<OcrLine>()
        for (group in all.indices.groupBy { find(it) }.values) {
            val ml = group.filter { !all[it].second }.map { all[it].first }
            val pp = group.filter { all[it].second }.map { all[it].first }
            when {
                pp.isEmpty() -> result.addAll(ml)
                ml.isEmpty() -> result.addAll(pp)
                else -> {
                    fun score(ls: List<OcrLine>): Double {
                        val len = ls.sumOf { it.text.length }.coerceAtLeast(1)
                        // alphabet latin : la plausibilité des mots pèse sur la confiance du lecteur (un texte mal lu mais lu avec assurance perd)
                        return ls.sumOf { l -> l.confidence.toDouble() * l.text.length * (if (lex != null && !cjk) 0.4 + 0.6 * plausibility(l.text, lex) else 1.0) } / len
                    }
                    // l'alphabet latin est mieux lu (espaces, ponctuation) par Paddle, le CJK par ML Kit
                    val sm = score(ml) + (if (cjk) 0.1 else 0.0)
                    val sp = score(pp) + (if (cjk) 0.0 else 0.1)
                    result.addAll(if (sp > sm) pp else ml)
                }
            }
        }
        return result.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }
}
