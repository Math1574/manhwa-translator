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
        val digits = Regex("\\d+").findAll(src).map { it.value }.toList()
        if (digits.any { it !in t }) out.add(Problem("chiffres", "chiffre(s) perdu(s) : " + digits.filter { it !in t }.joinToString(", ")))
        // noms à conserver
        val lower = src.lowercase(); val tl = t.lowercase()
        for (n in names) if (n.length >= 3 && lower.contains(n.lowercase()) && !tl.contains(n.lowercase())) out.add(Problem("nom", "nom modifié : $n"))
        // répétition anormale d'un mot (« de de de »)
        val words = toks(t)
        for (i in 2 until words.size) if (words[i] == words[i - 1] && words[i] == words[i - 2] && words[i].length > 1) { out.add(Problem("répétition", "mot répété : ${words[i]}")); break }
        // verbosité excessive
        if (sLetters >= 10 && t.count { it.isLetter() } > 3.2f * sLetters) out.add(Problem("long", "beaucoup plus long que l'original"))
        // reste de format
        if (Regex("^\\s*\\d+\\s*\\|").containsMatchIn(t) || t.contains("**") || t.startsWith("```")) out.add(Problem("format", "résidu de mise en forme"))
        return out
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
