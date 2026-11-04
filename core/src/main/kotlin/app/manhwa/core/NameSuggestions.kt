package app.manhwa.core

/**
 * Noms propres probables d'une œuvre, repérés pendant la lecture : un mot que le lexique anglais ne connaît pas et qui revient dans plusieurs pages (un bruitage ou une erreur
 * de lecture ne revient pas à l'identique page après page). L'utilisateur choisit de les garder tels quels dans le glossaire de l'œuvre, ou de les ignorer.
 */
class NameSuggestions(private val minPages: Int = 2, private val minTotal: Int = 3) {
    private class Seen { var total = 0; val pages = HashSet<String>() }
    private val seen = HashMap<String, HashMap<String, Seen>>()   // œuvre → mot (en minuscules) → vu
    private val shown = HashMap<String, String>()                 // mot (en minuscules) → écriture la plus lisible (« Jin-Woo » plutôt que « JIN-WOO »)
    private val ignored = HashSet<String>()
    private val word = Regex("[A-Za-z][A-Za-z'’-]{2,}")

    /** À appeler pour chaque page lue avec les textes de ses blocs. [pageId] évite de compter dix fois le même mot sur une seule page. */
    fun observe(series: String?, pageId: String, texts: List<String>, lex: EnglishLexicon) {
        if (series == null) return
        val map = seen.getOrPut(series) { HashMap() }
        for (t in texts) for (m in word.findAll(t)) {
            val w = m.value.trim('\'', '’', '-')
            if (w.length < 3 || lex.known(w) || w.any { it.isDigit() }) continue
            if (Regex("(.)\\1\\1").containsMatchIn(w.lowercase())) continue          // « KRAAAK » : un bruitage, pas un nom
            val k = w.lowercase()
            val e = map.getOrPut(k) { Seen() }; e.total++; e.pages.add(pageId)
            val best = shown[k]; if (best == null || (best.all { it.isUpperCase() || !it.isLetter() } && !w.all { it.isUpperCase() || !it.isLetter() })) shown[k] = w
        }
    }

    /** Mots à proposer pour une œuvre, les plus fréquents d'abord ; [known] : termes déjà au glossaire (en minuscules), jamais reproposés. */
    fun suggestions(series: String?, known: Set<String>, limit: Int = 8): List<String> {
        val map = seen[series ?: return emptyList()] ?: return emptyList()
        return map.filter { (k, v) -> k !in known && k !in ignored && v.total >= minTotal && v.pages.size >= minPages }
            .entries.sortedByDescending { it.value.total }.take(limit).map { shown[it.key] ?: it.key }
    }

    fun ignore(word: String) { ignored.add(word.lowercase()) }
}
