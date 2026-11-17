package app.manhwa.core

/** Retouches sûres d'une traduction, faites après coup sur chaque ligne (le modèle se trompe de façon régulière sur ces points). */
object TranslationPost {
    /** Bégaiement de l'original : « B-BUT », « W-wait », « Th-the », « WH- WHAT » (une ou deux lettres, répétées). */
    private val sourceStutter = Regex("(?i)(?<![\\p{L}'’-])(\\p{L}{1,2})-\\s?\\1\\p{L}*")
    private val sourceStartStutter = Regex("(?i)^[^\\p{L}]*(\\p{L}{1,2})-\\s?\\1")
    /** Bégaiement déjà présent dans la traduction (« M-MAIS », « DÉ-DÉMON »). */
    private val frenchStutter = Regex("(?i)(?<![\\p{L}'’-])(\\p{L}{1,2})-\\1\\p{L}*")
    /** Une lettre suivie d'un trait d'union et d'un mot d'au moins deux lettres : « E-ATTENDEZ ». « a-t-il », « peut-être » n'y répondent pas. */
    private val singleLetterStutter = Regex("(?<![\\p{L}'’-])(\\p{L})-(\\p{L}[\\p{L}'’]+)(?![\\p{L}-])")
    private val firstWord = Regex("^([^\\p{L}]*)(\\p{L}[\\p{L}'’]*)")
    /** « S-Rank », « X-Ray » : une lettre, un trait d'union et un mot qui n'a rien d'un bégaiement. */
    private val codeWords = Regex("(?i)^(rank|class|grade|level|type|ray|tier|series|shirt)$")

    /**
     * Bégaiement : l'original « B-BUT » doit donner « M-MAIS », pas « P-MAIS » (le modèle recopie ou invente la lettre) ni « MAIS » (le bégaiement est perdu).
     * La lettre répétée est toujours la première du mot français qui suit. Sans bégaiement dans l'original, rien n'est changé.
     */
    fun stutter(src: String, tr: String): String {
        val n = sourceStutter.findAll(src).count()
        if (n == 0) return tr
        var seen = 0
        // 1) bégaiement français dont la lettre n'est pas celle du mot (au plus autant que dans l'original)
        var out = singleLetterStutter.replace(tr) { m ->
            val letter = m.groupValues[1]; val word = m.groupValues[2]
            if (seen >= n || codeWords.matches(word)) return@replace m.value
            seen++
            val first = word[0]
            if (letter[0].equals(first, ignoreCase = true)) m.value
            else (if (letter[0].isUpperCase()) first.uppercaseChar() else first.lowercaseChar()).toString() + "-" + word
        }
        // 2) bégaiement perdu en tête de ligne : ajouté devant le premier mot
        if (seen == 0 && sourceStartStutter.containsMatchIn(src) && !frenchStutter.containsMatchIn(out)) {
            val m = firstWord.find(out)
            if (m != null && m.groupValues[2].length >= 2) {
                val w = m.groupValues[2]
                out = m.groupValues[1] + w[0] + "-" + w + out.substring(m.range.last + 1)
            }
        }
        return out
    }
}
