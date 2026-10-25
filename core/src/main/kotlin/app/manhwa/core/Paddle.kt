package app.manhwa.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class ScoredBox(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val score: Float) {
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
            out.add(ScoredBox(max(0f, minX - d), max(0f, minY - d), min(w.toFloat(), maxX + 1 + d), min(h.toFloat(), maxY + 1 + d), score))
        }
        return out.sortedWith(compareBy({ it.y0 }, { it.x0 }))
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
    fun merge(mlkit: List<OcrLine>, paddle: List<OcrLine>, script: String): List<OcrLine> {
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
                        return ls.sumOf { it.confidence.toDouble() * it.text.length } / len
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
