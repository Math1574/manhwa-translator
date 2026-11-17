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

    /** Une réplique qui n'est qu'un bégaiement (« Y-YOU… », « B-BUT! ») : ponctuation, une lettre, trait d'union, mot. */
    private val loneStutter = Regex("^([^\\p{L}]*)(\\p{L})-\\s?(\\p{L}+)([^\\p{L}]*)$")
    /** Mots que l'on bégaie le plus et leur équivalent français (la lettre répétée est la première du mot français). */
    private val stutterWords = mapOf(
        "you" to "toi", "but" to "mais", "what" to "quoi", "wait" to "attends", "why" to "pourquoi", "who" to "qui", "how" to "comment",
        "no" to "non", "stop" to "arrête", "me" to "moi", "he" to "lui", "she" to "elle", "we" to "nous", "they" to "eux",
        "help" to "à l'aide", "sorry" to "désolé", "okay" to "d'accord", "please" to "s'il te plaît", "yes" to "oui", "hey" to "hé", "hold" to "attends", "run" to "cours",
    )

    /**
     * Filet de sécurité : le modèle recopie parfois « Y-YOU… » tel quel (un seul « mot » pour lui, qu'il juge intraduisible). Quand la traduction est identique
     * à l'original et que celui-ci n'est qu'un bégaiement d'un mot courant, la réplique est écrite directement : « T-TOI… ». Tout autre cas est laissé tel quel.
     */
    fun rescueStutter(src: String, tr: String): String {
        val m = loneStutter.matchEntire(src.trim()) ?: return tr
        val key = m.groupValues[3]
        val fr = stutterWords[key.lowercase()] ?: return tr
        if (!m.groupValues[2].equals(key.take(1), ignoreCase = true)) return tr
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        if (norm(src) != norm(tr)) return tr
        val upper = key.length >= 2 && key.all { it.isUpperCase() }
        val word = if (upper) fr.uppercase() else if (key[0].isUpperCase()) fr.replaceFirstChar { it.uppercase() } else fr
        val letter = word.first().toString()
        val tail = m.groupValues[4]
        return m.groupValues[1] + letter + "-" + word + (if (tail.isNotEmpty() && tail[0] in "!?:;") " $tail" else tail)       // espace avant « ! », « ? » à la française
    }

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
