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

    // ---------- défauts de génération : chiffres et mots doublés ----------

    /** Un nombre, séparateurs de milliers compris (« 3,149 », « 3 149 », « 1.200 ») : ses chiffres seuls servent à comparer. */
    private val numberRe = Regex("(?<![\\d])\\d+(?:[ ,.\\u00A0\\u202F]\\d{3}(?!\\d))*")

    /**
     * Nombres abîmés à la génération (journal du 27/11 : « 26 YEARS » → « 266 ANS », « $50 » → « 500 DOLLARS », « 06:50 » → « 06:500 », « 24/7 » → « 2/7 ») : un nombre de la traduction
     * absent de l'original, qui ne diffère que d'un chiffre (en trop ou en moins) d'un nombre de l'original absent de la traduction, est remis tel qu'à l'original.
     * Un nombre écrit en lettres (« dix », « cent ») n'est pas concerné ; un nombre que l'original n'explique pas d'une seule façon est laissé.
     */
    fun numbers(src: String, tr: String): String {
        val srcNums = numberRe.findAll(src).map { it.value.filter(Char::isDigit) }.toList()
        if (srcNums.isEmpty()) return tr
        if (clockRe.containsMatchIn(src)) return tr   // « 1 PM » → « 13 h » : l'heure change légitimement de chiffres
        val trMatches = numberRe.findAll(tr).toList()
        if (trMatches.isEmpty()) return tr
        val trNums = trMatches.map { it.value.filter(Char::isDigit) }
        val missing = srcNums.filter { it !in trNums }.distinct()
        if (missing.isEmpty()) return tr
        val sb = StringBuilder(tr)
        val used = HashSet<String>()
        for (m in trMatches.reversed()) {
            val t = m.value.filter(Char::isDigit)
            if (t in srcNums) continue
            val cands = missing.filter { it !in used && oneDigitApart(t, it) }
            if (cands.size != 1) continue
            val s = cands[0]; used.add(s)
            val grouped = m.value.any { !it.isDigit() } && s.length > 3
            sb.replace(m.range.first, m.range.last + 1, if (grouped) groupFrench(s) else s)
        }
        return sb.toString()
    }

    private val clockRe = Regex("(?i)\\d\\s*(?:a\\.?m\\b|p\\.?m\\b)|o['’]clock")

    private fun oneDigitApart(a: String, b: String): Boolean {
        if (a.length == b.length + 1) return a.indices.any { a.removeRange(it, it + 1) == b }
        if (b.length == a.length + 1) return b.indices.any { b.removeRange(it, it + 1) == a }
        return false
    }

    private fun groupFrench(d: String): String = d.reversed().chunked(3).joinToString(" ").reversed()

    /** Mots-outils qui ne se répètent jamais d'affilée en français écrit (« à à », « que que », « de de ») ; « vous vous », « nous nous » sont réfléchis, donc permis (trois de suite ne l'est pas). */
    private val doubledWord = Regex("(?iu)(?<![\\p{L}'’])(à|a|de|du|des|que|qui|le|la|les|un|une|et|en|ne|pour|par|sur|dans|avec|je|tu|il|elle|on|ils|elles|ce|se|me|te|mon|ma|mes|ton|ta|tes|son|sa|ses|au|aux|y)\\s+\\1(?![\\p{L}'’])")
    private val tripledReflexive = Regex("(?iu)(?<![\\p{L}'’])((vous|nous)\\s+\\2)\\s+\\2(?![\\p{L}'’])")
    private val srcRepeat = Regex("(?iu)(?<![\\p{L}'’])(\\p{L}+)[\\s,.…-]+\\1(?![\\p{L}'’])")

    /** Mot-outil doublé par la génération (« QUELLE QUE QUE RAISON », « À À QUEL POINT ») : le doublon est retiré, sauf si l'original répète lui-même un mot (bégaiement, insistance). */
    fun doubledWords(src: String, tr: String): String {
        if (srcRepeat.containsMatchIn(src)) return tr
        var out = doubledWord.replace(tr) { m -> m.value.substring(0, m.groupValues[1].length) }
        out = tripledReflexive.replace(out) { m -> m.groupValues[1] }
        return out
    }
}

