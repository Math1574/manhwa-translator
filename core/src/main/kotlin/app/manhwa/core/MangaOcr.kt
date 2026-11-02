package app.manhwa.core

import kotlin.math.exp

/**
 * Décodage de manga-ocr (encodeur ViT + décodeur BERT, vocabulaire de 6 144 caractères) : lecture du japonais vertical, des furigana et du texte
 * stylisé. Partie pure : le modèle est fourni par [step], qui renvoie les scores du jeton suivant pour la séquence déjà produite.
 */
object MangaOcrDecoder {
    const val START = 2
    const val EOS = 3
    private const val SPECIAL_BELOW = 5   // [PAD] [UNK] [CLS] [SEP] [MASK]

    class Result(val ids: List<Int>, val meanProb: Float)

    /** Décodage glouton avec interdiction de répéter un même trigramme (comme la configuration de génération du modèle). */
    fun greedy(step: (List<Int>) -> FloatArray, maxLen: Int = 48, noRepeatNgram: Int = 3): Result {
        val ids = ArrayList<Int>().apply { add(START) }
        var probSum = 0.0; var n = 0
        while (ids.size <= maxLen) {
            val logits = step(ids)
            var mx = Float.NEGATIVE_INFINITY
            for (v in logits) if (v > mx) mx = v
            var z = 0.0
            for (v in logits) z += exp((v - mx).toDouble())
            // meilleurs candidats par ordre décroissant (on n'en regarde que quelques-uns)
            val order = logits.indices.sortedByDescending { logits[it] }.take(6)
            var chosen = order[0]
            for (c in order) {
                if (!repeatsNgram(ids, c, noRepeatNgram)) { chosen = c; break }
            }
            if (chosen == EOS) break
            probSum += exp((logits[chosen] - mx).toDouble()) / z; n++
            ids.add(chosen)
        }
        return Result(ids.drop(1), if (n == 0) 0f else (probSum / n).toFloat())
    }

    private fun repeatsNgram(ids: List<Int>, next: Int, n: Int): Boolean {
        if (n < 2 || ids.size < n - 1) return false
        val tail = ids.takeLast(n - 1) + next
        for (i in 0..ids.size - n) {
            var same = true
            for (k in 0 until n) if (ids[i + k] != tail[k]) { same = false; break }
            if (same) return true
        }
        return false
    }

    /** Texte : jetons spéciaux ignorés, préfixes « ## » retirés, espaces supprimés ; points de suspension normalisés. */
    fun text(ids: List<Int>, vocab: List<String>): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id < SPECIAL_BELOW || id >= vocab.size) continue
            val t = vocab[id]
            if (t.startsWith("<unused") || (t.startsWith("[") && t.endsWith("]") && t.length > 2)) continue
            sb.append(if (t.startsWith("##")) t.substring(2) else t)
        }
        return sb.toString().filter { !it.isWhitespace() }.replace(Regex("[.・]{3,}"), "…")
    }
}
