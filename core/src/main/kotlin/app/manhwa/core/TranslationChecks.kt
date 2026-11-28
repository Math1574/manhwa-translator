package app.manhwa.core

/** Contrôles automatiques d'une traduction ligne par ligne : mots non traduits, contenu perdu, noms modifiés, chiffres perdus, répétitions. */
object TranslationChecks {
    /**
     * Nombres de l'original. Pas un chiffre collé à des lettres (« INJURIE3 »), ni accroché par un trait d'union (« 0-OUCHY » : le O lu 0), ni collé derrière des points en fin
     * de bulle (« ME..2 », « WHAT...2! » : le « ? » lu 2) — erreurs de lecture que la traduction corrige à raison.
     */
    private val srcNumber = Regex("(?<![A-Za-z0-9]|[A-Za-z]-)\\d{1,3}(?:[,.\u00A0\u202F ]\\d{3})+(?![A-Za-z0-9]|-[A-Za-z])|(?<![A-Za-z0-9.?!]|[A-Za-z]-)\\d+(?![A-Za-z0-9]|-[A-Za-z])")
    private val trNumber = Regex("(?<!\\d)\\d{1,3}(?:[,.\u00A0\u202F ]\\d{3})+(?!\\d)|\\d+")

    /** Une syllabe répétée de part et d'autre d'un trait d'union (« EST-EST », « A-A », « B-BUT » côté anglais). */
    /** Bégaiement de l'original, trait d'union suivi ou non d'une espace (« B-BUT », « WH- WHAT ») : la traduction peut alors bégayer aussi. */
    /** Bégaiement de l'original, quelle que soit sa ponctuation : « Y-YOU », « Y, YOU », « Y.. YOU », « Y... YOU ». */
    private val srcStutter = Regex("(?i)(?<![\\p{L}])(\\p{L}{1,4})(?:-|,|\\.{1,3}|…)\\s?\\1")
    private val stutteredSyllable = Regex("(?i)(?<![\\p{L}])(\\p{L}{1,4})-\\1(?![\\p{L}])|(?i)(?<![\\p{L}])(\\p{L}{1,4})-\\2\\p{L}")

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

    /** Onomatopée ou cri : interjection connue, ou trois lettres différentes au plus (« AAAACK », « KEKEKE », « PFFT », « KUAA »). */
    private fun soundLike(w: String): Boolean = JunkFilter.isInterjectionToken(w) || w.filter { it.isLetter() }.toSet().size <= 3

    /** Mot anglais courant qui n'existe pas en français (voir [englishOnly]). */
    internal fun isEnglishOnly(word: String): Boolean = word.lowercase().replace('’', '\'') in englishOnly

    fun problems(src: String, tr: String, target: String, names: Collection<String> = emptyList(), style: StyleMemory? = null): List<Problem> {
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
            // « Y-YOU… » recopié tel quel : un bégaiement est un seul « mot » (trait d'union) ; son second morceau est ce qui dit s'il est resté anglais
            val englishStutter = toks.any { stutterEnglish(it, nameSet) }
            if (englishStutter && eng.isEmpty()) out.add(Problem("non-traduit", "bégaiement resté en anglais : " + toks.first { stutterEnglish(it, nameSet) }))
            // recopié tel quel : un problème seulement s'il reste un vrai mot anglais (« KUAA AAK ! », « KEKEKE », « PFFT ! HAHAHA », « JIN MOOWON » se gardent)
            if (sLetters >= 3 && normalize(src) == normalize(t) && ((toks.size >= 2 && toks.any { w -> val l = w.lowercase(); l in englishOnly || (w.length > 3 && l !in nameSet && !soundLike(l)) }) || englishStutter))
                out.add(Problem("non-traduit", "identique à l'original"))
        }
        // rendu mot à mot d'une expression toute faite (« tuer deux oiseaux », « faire du sens », « écume » pour « scum ») : la ligne est refaite avec l'équivalent
        if (target == "fr") for (h in IdiomHints.calques(src, t).take(2)) out.add(Problem("calque", "« ${h.matched} » est traduit mot à mot ; dire : ${h.french}"))
        // tu / vous et genre du narrateur : plusieurs personnes tutoyées, formes mêlées, interlocuteur ou chapitre qui change de registre
        if (target == "fr") out.addAll(FrenchAgreement.problems(src, t, names, style))
        // chiffres : ceux de l'original doivent se retrouver
        // nombres isolés (« INJURIE3 » est une erreur de lecture), milliers groupés compris (« 1,000 » = « 1 000 » = « 1000 »)
        val digits = srcNumber.findAll(src).map { it.value.filter(Char::isDigit) }.toList()
        // chaque nombre doit s'y retrouver entier : « 26 » n'est pas dans « 266 » (chiffre doublé à la génération) ; ou écrit en lettres (« trois », « deux cent cinquante »)
        val tLow = t.lowercase()
        val trNums = trNumber.findAll(t).map { it.value.filter(Char::isDigit) }.toSet()
        fun present(d: String) = d in trNums || (d.length <= 5 && FrenchNumbers.pattern(d.toInt())?.containsMatchIn(tLow) == true)
        if (digits.any { !present(it) }) out.add(Problem("chiffres", "chiffre(s) perdu(s) : " + digits.filter { !present(it) }.joinToString(", ")))
        // noms à conserver
        val lower = src.lowercase(); val tl = t.lowercase()
        for (n in names) if (n.length >= 3 && mentions(lower, n.lowercase()) && !mentions(tl, n.lowercase())) out.add(Problem("nom", "nom modifié : $n"))
        // répétition anormale d'un mot (« de de de »)
        val words = toks(t)
        for (i in 2 until words.size) if (words[i] == words[i - 1] && words[i] == words[i - 2] && words[i].length > 1) { out.add(Problem("répétition", "mot répété : ${words[i]}")); break }
        // verbosité excessive
        if (sLetters >= 10 && t.count { it.isLetter() } > 3.2f * sLetters) out.add(Problem("long", "beaucoup plus long que l'original"))
        // syllabe doublée par un trait d'union alors que l'original ne bégaie pas (« QU'EST-EST-CE QUE C'EST ? Y A-A-T-IL UN PROBLÈME ? » pour « WHAT IS IT? IS SOMETHING WRONG? », journal du 22/11)
        if (!srcStutter.containsMatchIn(src)) stutteredSyllable.find(t)?.let { out.add(Problem("répétition", "syllabe doublée : ${it.value}")) }
        // reste de format
        if (Regex("^\\s*\\d+\\s*\\|").containsMatchIn(t) || t.contains("**") || t.startsWith("```")) out.add(Problem("format", "résidu de mise en forme"))
        // l'original recopié devant la traduction (« SOURCE| TRADUCTION »), ou un « | » resté dans le texte alors que l'original n'en a pas
        if (TranslationEcho.strip(src, t) != t) out.add(Problem("recopie", "l'original est recopié devant la traduction"))
        else if (t.contains('|') && !src.contains('|')) out.add(Problem("format", "« | » dans la traduction"))
        return out
    }

    /** Bégaiement (une ou deux lettres, trait d'union, mot) dont le mot est un mot anglais courant : « Y-YOU », « B-BUT », « W-WHAT ». « T-TOI », « a-t-il », « peut-être » n'y répondent pas. */
    private val stutterRe = Regex("^\\p{L}{1,2}-\\p{L}[\\p{L}'’]*$")
    private fun stutterEnglish(token: String, names: Set<String>): Boolean {
        if (!stutterRe.matches(token)) return false
        val word = token.substringAfter('-').lowercase().replace('’', '\'')
        return word in englishOnly && word !in names
    }

    /** Le nom figure-t-il dans le texte ? Un nom court (jusqu'à 4 lettres) y est comme mot entier (« por » n'est pas dans « important »), un nom plus long peut y être décliné (« Kandeya's »). */
    private fun mentions(text: String, name: String): Boolean =
        if (name.length > 4) text.contains(name) else Regex("(?<![\\p{L}])" + Regex.escape(name) + "(?![\\p{L}])").containsMatchIn(text)

    private fun toks(s: String) = tokenRe.findAll(s).map { it.value.lowercase() }.toList()
    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}

/**
 * Mémoire d'un chapitre : noms propres et termes à garder tels quels. Un mot de l'original qui se retrouve identique dans la traduction
 * (nom de personnage, de lieu) et qui revient au moins deux fois est ajouté à la liste fournie au modèle pour les pages suivantes.
 * Un nom de trois lettres (« Gaf », « Zod ») doit revenir trois fois, comme mot entier, et n'être ni un mot courant ni une interjection : tant de mots anglais
 * de trois lettres sont identiques en français qu'il faut plus de preuves. [isCommonWord] : le dictionnaire des mots courants (l'application le fournit).
 * [style] : tutoiement et genre du narrateur déjà établis (voir [StyleMemory]).
 */
class ChapterMemory(private val maxNames: Int = 14, private val isCommonWord: (String) -> Boolean = { false }) {
    private val counts = LinkedHashMap<String, Int>()
    private val shortCounts = LinkedHashMap<String, Int>()
    private val display = HashMap<String, String>()
    private val tok = Regex("[\\p{L}][\\p{L}'’-]{3,}")
    private val shortTok = Regex("(?<![\\p{L}])\\p{L}{3}(?![\\p{L}])")
    private val stutterPrefix = Regex("^(?:\\p{L}-)+")
    private val possessive = Regex("['’][sS]$")
    private val common = setOf(
        "that", "this", "with", "have", "from", "they", "there", "what", "when", "will", "would", "could", "your", "just", "about", "then", "than",
        "dans", "pour", "avec", "mais", "plus", "tout", "tous", "cette", "elle", "nous", "vous", "sont", "être", "fait", "comme", "sans", "sous",
    )
    /** Mots de trois lettres très courants en anglais et en français : jamais des noms. */
    private val shortCommon = (
        "the and you are for not but all any can had her was one our out day get has him his how man new now old see two way who boy did its let put say she too use yes yet why war try top sun sit sir run red off odd nor net may map low lot lie led law key job ice hot hit god gas fun fly far eye end eat dog die cut cry cup car bed bad bag big bit box buy cat den dry due ear egg era fan fat fee few fit fix fox gun guy hat hip ill ink jar jaw joy kid lap lay leg lip mad mix mud nap nod oil pan pat pay pen pet pig pit pop raw row sad sea set shy sin sky son sum tag tax tea ten tie tip toe ton tug van wet win won zip age ago aid aim air arm art ask ate awe bar bat bay bet bow bus cap cow dad fed fur gap gym hug hum jam jet lad mom nut oak own pad pie pub rid rub rug sip sob tan tin toy vow wax web wig yep nah yay wow aha ooh ahh umm gah tsk mmm hee heh boo eww bam pow zap " +
            "les des une que qui pas par sur est ses tes mes nos vos ils nul oui non mal bon ici peu mot bas cas eau fin fou ami âme roi feu vie ans dit fut vit vue rue mer sac vin jeu lui moi toi ton aux ces cet"
        ).split(' ').toSet()
    private val shortMinSightings = 3

    val style = StyleMemory()
    fun styleCopy(): StyleMemory = style.copy()
    fun styleHints(): List<String> = style.hints()

    /** [learnStyle] : faux pour une ligne refaite ou corrigée (prompt 6) : ses noms comptent, mais le tutoiement et les accords qu'elle porte ne font pas la règle du chapitre. */
    fun observe(src: String, tr: String, learnStyle: Boolean = true) {
        for (m in tok.findAll(src)) {
            if (stutterPrefix.containsMatchIn(m.value)) continue          // « G-GAF » : le nom est compté seul (mots de trois lettres), pas avec son bégaiement
            val w = m.value.replace(possessive, ""); val l = w.lowercase()   // « KANDEYA'S » compte pour « KANDEYA »
            if (l.length < 4 || l in common || l in TranslationChecksWords.stop) continue
            if (keptAsName(tr, l)) { counts[l] = (counts[l] ?: 0) + 1; display.putIfAbsent(l, w) }
        }
        for (w in shortTok.findAll(src).map { it.value }.distinct()) {
            val l = w.lowercase()
            if (isShortCommon(l) || !keptAsName(tr, l)) continue
            shortCounts[l] = (shortCounts[l] ?: 0) + 1; display.putIfAbsent(l, w)
        }
        if (learnStyle) style.observe(src, tr, namesToKeep())
    }

    /**
     * La traduction garde-t-elle [l] comme un nom ? Le mot y figure en entier (« Kandeya's » compte), avec une majuscule en cours de phrase (« Et toi, Gaf ? »), ou en début de phrase
     * si ce n'est pas un mot courant. Un mot qui s'écrit pareil dans les deux langues (« village », « long », « change ») reste en minuscule : ce n'est pas un nom. Une traduction
     * toute en majuscules ne permet pas de juger : le mot compte s'il y figure.
     */
    private fun keptAsName(tr: String, l: String): Boolean {
        val lower = tr.lowercase()
        val allCaps = tr.none { it.isLowerCase() }
        val commonWord = isCommonWord(l)
        for (m in Regex("(?<![\\p{L}])" + Regex.escape(l) + "(?![\\p{L}])").findAll(lower)) {
            if (allCaps) return true
            val i = m.range.first
            if (!tr[i].isUpperCase()) continue
            val before = tr.substring(0, i).trimEnd()
            val sentenceStart = before.isEmpty() || before.last() in ".!?…:«\"“(-–—"
            if (!sentenceStart || !commonWord) return true
        }
        return false
    }

    private fun isShortCommon(l: String): Boolean =
        l in shortCommon || l in common || l in TranslationChecksWords.stop || TranslationChecks.isEnglishOnly(l) || JunkFilter.isInterjectionToken(l) || isCommonWord(l)

    /** Noms à conserver (vus au moins deux fois ; trois pour un nom de trois lettres), les plus fréquents d'abord. Les mots écrits en majuscules sont remis en casse de titre. */
    fun namesToKeep(): List<String> =
        (counts.filter { it.value >= 2 } + shortCounts.filter { it.value >= shortMinSightings }).entries.sortedByDescending { it.value }.take(maxNames).map { e ->
            val d = display[e.key] ?: e.key
            if (d.all { !it.isLetter() || it.isUpperCase() }) d.lowercase().replaceFirstChar { it.uppercase() } else d
        }

    fun reset() { counts.clear(); shortCounts.clear(); display.clear(); style.reset() }
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

/** Nombres écrits en lettres, à la française (« 10 » → « dix », « 34 » → « trente-quatre », « 100 » → « cent ») : un nombre écrit ainsi n'est pas un chiffre perdu. */
object FrenchNumbers {
    /** Le nombre [n] écrit en lettres dans un texte en minuscules : traits d'union ou espaces (orthographe de 1990 ou non), « une » pour « un », « vingts » / « cents » sans s. */
    fun pattern(n: Int): Regex? {
        val w = words(n) ?: return null
        val parts = w.split(Regex("[ -]+")).map { p -> when (p) { "un" -> "une?"; "vingts" -> "vingts?"; "cents" -> "cents?"; else -> Regex.escape(p) } }
        return Regex("(?<![\\p{L}-])" + parts.joinToString("[\\s-]+") + "(?![\\p{L}-])")
    }

    private val units = listOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf")
    private val tens = mapOf(2 to "vingt", 3 to "trente", 4 to "quarante", 5 to "cinquante", 6 to "soixante")

    fun words(n: Int): String? = when {
        n < 0 -> null
        n < 20 -> units[n]
        n < 70 -> { val t = tens[n / 10]!!; val u = n % 10; when (u) { 0 -> t; 1 -> "$t et un"; else -> "$t-${units[u]}" } }
        n < 80 -> if (n == 71) "soixante et onze" else "soixante-" + units[n - 60]
        n < 100 -> if (n == 80) "quatre-vingts" else "quatre-vingt-" + units[n - 80]
        n == 100 -> "cent"
        n < 1000 -> { val c = n / 100; val r = n % 100; (if (c == 1) "cent" else units[c] + "-cent" + (if (r == 0) "s" else "")) + (if (r == 0) "" else "-" + words(r)) }
        n == 1000 -> "mille"
        n % 1000 == 0 && n < 20000 -> words(n / 1000) + " mille"
        else -> null
    }
}
