package app.manhwa.core

/** Contrôles automatiques d'une traduction ligne par ligne : mots non traduits, contenu perdu, noms modifiés, chiffres perdus, répétitions. */
object TranslationChecks {
    data class Problem(val code: String, val detail: String)

    /** Mots anglais courants qui n'existent pas en français : leur présence dans une traduction signale du texte non traduit. */
    private val englishOnly = setOf(
        "the", "and", "you", "your", "yours", "is", "are", "was", "were", "that", "this", "these", "those", "with", "have", "has", "had", "not",
        "what", "who", "why", "how", "when", "where", "from", "they", "them", "their", "there", "here", "just", "will", "would", "could", "should",
        "can't", "don't", "didn't", "doesn't", "won't", "isn't", "aren't", "i'm", "it's", "he's", "she's", "i'll", "i've", "i'd", "we're", "you're",
        "know", "think", "want", "going", "about", "because", "anyway", "nothing", "everything", "someone", "something", "really", "never", "always",
        "my", "me", "him", "her", "his", "she", "he", "we", "us", "of", "to", "for", "in", "on", "at", "be", "been", "being", "do", "does", "did",
        "or", "but", "if", "then", "so", "all", "any", "no", "yes", "okay", "master", "sword", "heavenly", "demon", "disciple", "sect", "elder",
        "young", "brother", "sister", "alliance", "martial", "storage", "watch", "over", "look", "please", "thank", "sorry",
    ) - setOf("on", "me", "do", "a", "so", "no", "or", "in", "all", "si", "est", "us", "he", "she", "her", "him", "pour", "see")

    // « on », « me », « do », « si », « est », « no », « all », « or », « in », « so » existent aussi en français ou comme sigles : exclus ci-dessus pour limiter les faux positifs.

    private val tokenRe = Regex("[\\p{L}][\\p{L}'’-]*")

    fun problems(src: String, tr: String, target: String, names: Collection<String> = emptyList()): List<Problem> {
        val out = ArrayList<Problem>()
        val t = tr.trim()
        if (t.isEmpty()) return listOf(Problem("vide", "traduction vide"))
        val sLetters = src.count { it.isLetter() }
        if (LlmPrompt.suspicious(src, tr)) out.add(Problem("contenu", "contenu supprimé ou terme injecté"))
        if (target == "fr") {
            val toks = tokenRe.findAll(t).map { it.value }.toList()
            val nameSet = names.map { it.lowercase() }.toSet()
            val eng = toks.filter { w -> val l = w.lowercase().replace('’', '\''); l in englishOnly && l !in nameSet }
            if (eng.size >= 2 && eng.size.toFloat() / toks.size.coerceAtLeast(1) >= 0.2f) out.add(Problem("non-traduit", "mots anglais : " + eng.distinct().take(6).joinToString(", ")))
            if (sLetters >= 3 && normalize(src) == normalize(t) && toks.size >= 2 && toks.any { it.lowercase() in englishOnly || it.length > 3 }) out.add(Problem("non-traduit", "identique à l'original"))
        }
        // chiffres : ceux de l'original doivent se retrouver
        val digits = Regex("(?<![A-Za-z0-9])\\d+(?![A-Za-z0-9])").findAll(src).map { it.value }.toList()   // nombres isolés ; « INJURIE3 » est une erreur de lecture
        if (digits.any { it !in t }) out.add(Problem("chiffres", "chiffre(s) perdu(s) : " + digits.filter { it !in t }.joinToString(", ")))
        // noms à conserver
        val lower = src.lowercase(); val tl = t.lowercase()
        for (n in names) if (n.length >= 3 && lower.contains(n.lowercase()) && !tl.contains(n.lowercase())) out.add(Problem("nom", "nom modifié : $n"))
        // répétition anormale d'un mot (« de de de »)
        val words = toks(t)
        for (i in 2 until words.size) if (words[i] == words[i - 1] && words[i] == words[i - 2] && words[i].length > 1) { out.add(Problem("répétition", "mot répété : ${words[i]}")); break }
        // personne, temps et négation : les erreurs typiques d'un petit modèle (« our » → « votre », « his » → « mon », « I have » → « j'aurai »)
        if (target == "fr") { personProblem(src, t)?.let { out.add(it) }; tenseProblem(src, t)?.let { out.add(it) }; negationProblem(src, t)?.let { out.add(it) } }
        // verbosité excessive
        if (sLetters >= 10 && t.count { it.isLetter() } > 3.2f * sLetters) out.add(Problem("long", "beaucoup plus long que l'original"))
        // reste de format
        if (Regex("^\\s*\\d+\\s*\\|").containsMatchIn(t) || t.contains("**") || t.startsWith("```")) out.add(Problem("format", "résidu de mise en forme"))
        return out
    }

    // ---------- personne, temps, négation ----------

    private val enTok = Regex("[a-z]+(?:'[a-z]+)?")
    private fun enWords(s: String) = enTok.findAll(s.lowercase().replace('’', '\'')).map { it.value }.toList()

    /** Personnes présentes dans l'original (1 = je, 2 = nous, 3 = tu/vous, 4 = il/elle/ils) d'après les pronoms et possessifs anglais. */
    private val enPerson: Map<String, Int> = buildMap {
        for (w in listOf("i", "i'm", "i've", "i'll", "i'd", "me", "my", "mine", "myself")) put(w, 1)
        for (w in listOf("we", "we're", "we've", "we'll", "we'd", "us", "our", "ours", "ourselves", "let's")) put(w, 2)
        for (w in listOf("you", "you're", "you've", "you'll", "you'd", "your", "yours", "yourself", "yourselves", "y'all")) put(w, 3)
        for (w in listOf("he", "he's", "he'll", "he'd", "him", "his", "himself", "she", "she's", "she'll", "she'd", "her", "hers", "herself", "it", "it's", "its", "itself", "they", "they're", "they've", "they'll", "they'd", "them", "their", "theirs", "themselves")) put(w, 4)
    }
    /** Possessifs anglais → personne, avec les formes françaises attendues. */
    private val enPossessive: Map<String, Pair<Int, String>> = mapOf(
        "my" to (1 to "mon/ma/mes"), "our" to (2 to "notre/nos"), "your" to (3 to "ton/ta/tes or votre/vos"), "his" to (4 to "son/sa/ses"), "her" to (4 to "son/sa/ses"), "its" to (4 to "son/sa/ses"), "their" to (4 to "son/sa/ses or leur/leurs"),
    )
    /** Déterminants possessifs français → personne. « ton » et « son » sont aussi des noms (le ton, le son) : ignorés après un article. */
    private val frPossessive: Map<String, Int> = mapOf("mon" to 1, "ma" to 1, "mes" to 1, "notre" to 2, "nos" to 2, "ton" to 3, "ta" to 3, "tes" to 3, "votre" to 3, "vos" to 3, "son" to 4, "sa" to 4, "ses" to 4)
    private val nounPreceders = setOf("le", "un", "ce", "du", "au", "ce", "d'un", "sur", "ce", "mon", "ton", "son")
    private val personName = mapOf(1 to "I/me/my", 2 to "we/us/our", 3 to "you/your", 4 to "he/she/they")

    /** Possessif français d'une autre personne que celles de l'original : « our » rendu par « votre », « his » par « mon ». */
    private fun personProblem(src: String, tr: String): Problem? {
        val sw = enWords(src)
        val present = sw.mapNotNull { enPerson[it] }.toSet()
        val poss = sw.mapNotNull { w -> enPossessive[w]?.let { w to it } }
        if (poss.isEmpty()) return null                       // sans possessif dans l'original, le français peut en employer un (« mon vieux », « mon pote »)
        val frToks = tr.lowercase().replace('’', '\'').split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
        for ((i, w) in frToks.withIndex()) {
            val person = frPossessive[w] ?: continue
            if ((w == "son" || w == "ton") && i > 0 && frToks[i - 1] in nounPreceders) continue
            if (person in present) continue
            val (word, info) = poss.first()
            return Problem("personne", "wrong person: the source has '$word' (${personName[info.first]}) but the translation uses '$w'; translate '$word' with ${info.second}")
        }
        return null
    }

    private val frSubj = "(?:j['’]|je\\s|tu\\s|il\\s|elle\\s|on\\s|nous\\s|vous\\s|ils\\s|elles\\s)(?:(?:ne|n['’])\\s*)?"
    private val frStems = "(?:aur|ser|ir|fer|pourr|voudr|devr|saur|verr|viendr|tiendr|faudr|mourr|enverr|deviendr)"
    private val frFuture = Regex("(?i)(?<![\\p{L}'’])$frSubj${frStems}(?:ai|as|a|ons|ez|ont)(?![\\p{L}])")
    private val frConditional = Regex("(?i)(?<![\\p{L}'’])$frSubj${frStems}(?:ais|ait|ions|iez|aient)(?![\\p{L}])")
    private val futureTriggers = setOf(
        "will", "won't", "shall", "gonna", "going", "would", "wouldn't", "could", "couldn't", "should", "shouldn't", "might", "may", "tomorrow", "tonight", "soon", "later", "next", "someday",
        "after", "until", "before", "once", "when", "if", "then", "promise", "swear", "wish", "perhaps", "maybe",
    )
    private val conditionalTriggers = setOf("would", "wouldn't", "could", "couldn't", "should", "shouldn't", "might", "may", "if", "wish", "unless", "maybe", "perhaps", "like")

    /** Futur ou conditionnel dans la traduction alors que l'original est au présent ou au passé (« I have… » → « j'aurai… »). */
    private fun tenseProblem(src: String, tr: String): Problem? {
        val sw = enWords(src)
        if (sw.isEmpty()) return null
        val hasWill = sw.any { it in futureTriggers || it.endsWith("'ll") || it.endsWith("'d") } || src.lowercase().replace('’', '\'').contains("one day") || src.lowercase().contains("from now on")
        frFuture.find(tr)?.let { m -> if (!hasWill) return Problem("temps", "wrong tense: the source has no future marker (will, 'll, going to...) but the translation uses the future ('${m.value.trim()}'); keep the tense of the source (present stays present, e.g. \"I have\" = \"j'ai\")") }
        val hasCond = sw.any { it in conditionalTriggers || it.endsWith("'d") }
        frConditional.find(tr)?.let { m -> if (!hasCond) return Problem("temps", "wrong tense: the source has no conditional marker (would, could, should, 'd, if...) but the translation uses the conditional ('${m.value.trim()}'); keep the tense of the source") }
        return null
    }

    private val enNegation = setOf("not", "no", "never", "nothing", "nobody", "none", "neither", "nor", "without", "cannot", "nowhere", "hardly")
    private val frNegation = setOf("ne", "n'", "pas", "jamais", "rien", "personne", "aucun", "aucune", "nul", "nulle", "ni", "sans", "non", "plus", "nulle part", "guère")
    private val noNegation = listOf("never mind", "no way", "no matter", "no wonder", "no one", "nobody", "no kidding")

    /** Négation de l'original perdue dans la traduction : le sens est inversé. */
    private fun negationProblem(src: String, tr: String): Problem? {
        val low = src.lowercase().replace('’', '\'')
        if (noNegation.any { low.contains(it) }) return null
        val sw = enWords(src)
        val negated = sw.any { it in enNegation || it.endsWith("n't") }
        if (!negated) return null
        val ft = tr.lowercase().replace('’', '\'').split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
        if (ft.any { it in frNegation || it.startsWith("n'") || it.startsWith("jamais") }) return null
        if (ft.size < 2) return null
        return Problem("négation", "lost negation: the source is negative ('${sw.first { it in enNegation || it.endsWith("n't") }}') but the translation has no negation (ne…pas, jamais, rien, sans, non); keep the meaning")
    }

    private fun toks(s: String) = tokenRe.findAll(s).map { it.value.lowercase() }.toList()
    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}

/**
 * Mémoire d'un chapitre : noms propres et termes à garder tels quels. Un mot de l'original qui se retrouve identique dans la traduction
 * (nom de personnage, de lieu) et qui revient au moins deux fois est ajouté à la liste fournie au modèle pour les pages suivantes.
 */
class ChapterMemory(private val maxNames: Int = 14) {
    private val counts = LinkedHashMap<String, Int>()
    private val display = HashMap<String, String>()
    private val tok = Regex("[\\p{L}][\\p{L}'’-]{3,}")
    private val common = setOf(
        "that", "this", "with", "have", "from", "they", "there", "what", "when", "will", "would", "could", "your", "just", "about", "then", "than",
        "dans", "pour", "avec", "mais", "plus", "tout", "tous", "cette", "elle", "nous", "vous", "sont", "être", "fait", "comme", "sans", "sous",
    )

    fun observe(src: String, tr: String) {
        val trLower = tr.lowercase()
        for (m in tok.findAll(src)) {
            val w = m.value; val l = w.lowercase()
            if (l in common || l in TranslationChecksWords.stop) continue
            if (trLower.contains(l)) { counts[l] = (counts[l] ?: 0) + 1; display.putIfAbsent(l, w) }
        }
    }

    /** Noms à conserver (vus au moins deux fois), les plus fréquents d'abord. Les mots écrits en majuscules sont remis en casse de titre. */
    fun namesToKeep(): List<String> =
        counts.filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(maxNames).map { e ->
            val d = display[e.key] ?: e.key
            if (d.all { !it.isLetter() || it.isUpperCase() }) d.lowercase().replaceFirstChar { it.uppercase() } else d
        }

    fun reset() { counts.clear(); display.clear() }
}

internal object TranslationChecksWords {
    /** Mots anglais/français très courants (jamais des noms propres), pour ne pas les compter dans la mémoire des noms. */
    val stop = setOf(
        "this", "that", "these", "those", "with", "from", "have", "has", "been", "were", "will", "would", "could", "should", "their", "there", "where",
        "what", "when", "which", "while", "about", "after", "before", "because", "again", "only", "even", "still", "never", "always", "every",
        "something", "nothing", "anything", "everything", "someone", "anyone", "please", "thank", "okay", "yeah", "right", "just", "like", "know",
        "think", "want", "need", "come", "back", "here", "then", "than", "them", "they", "your", "mine", "more", "most", "much", "many", "some",
    )
}
