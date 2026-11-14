package app.manhwa.core

import java.text.Normalizer
import java.util.Locale

/**
 * Un réglage (ou une page) que la recherche des réglages peut trouver.
 * [path] : chemin affiché sous le titre (« Lecture › Affichage ») ; [words] : mots supplémentaires qui mènent au même réglage (synonymes, noms des valeurs).
 */
data class SearchEntry(val id: String, val title: String, val path: String, val words: String = "")

/**
 * Recherche dans les réglages : insensible aux accents, aux majuscules, à la ponctuation et aux petits mots (« le », « du »…).
 * Chaque mot tapé doit commencer un mot du titre, du chemin ou des mots supplémentaires ; les titres passent avant le reste.
 */
object SettingsSearch {
    private val stop = setOf("le", "la", "les", "l", "un", "une", "des", "de", "du", "d", "et", "ou", "a", "au", "aux", "en", "sur", "pour", "mon", "ma", "mes")

    /** Minuscules, sans accents, ligatures développées, tout ce qui n'est ni lettre ni chiffre devient une espace. */
    fun normalize(s: String): String {
        val folded = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace("œ", "oe").replace("æ", "ae")
        return folded.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }

    private fun words(s: String): List<String> = normalize(s).split(' ').filter { it.isNotEmpty() }

    /** Mots de la recherche, sans les petits mots (sauf s'il n'y a rien d'autre). */
    fun tokens(query: String): List<String> {
        val all = words(query)
        val kept = all.filter { it !in stop }
        return kept.ifEmpty { all }
    }

    private fun score(t: String, title: List<String>, path: List<String>, extra: List<String>): Int {
        var best = 0
        for (w in title) best = maxOf(best, if (w == t) 7 else if (w.startsWith(t)) 6 else if (t.length >= 3 && w.contains(t)) 2 else 0)
        for (w in path) best = maxOf(best, if (w.startsWith(t)) 3 else if (t.length >= 3 && w.contains(t)) 1 else 0)
        for (w in extra) best = maxOf(best, if (w.startsWith(t)) 3 else if (t.length >= 3 && w.contains(t)) 1 else 0)
        return best
    }

    /** Réglages qui correspondent à tous les mots de [query], les meilleurs d'abord ; à égalité, dans l'ordre de la liste. Une recherche vide ne donne rien. */
    fun find(query: String, entries: List<SearchEntry>, limit: Int = 20): List<SearchEntry> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Int, Int>>()
        entries.forEachIndexed { i, e ->
            val title = words(e.title); val path = words(e.path); val extra = words(e.words)
            var total = 0
            for (t in tokens) {
                val s = score(t, title, path, extra)
                if (s == 0) { total = -1; break }
                total += s
            }
            if (total > 0) scored.add(i to total)
        }
        return scored.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }).take(limit).map { entries[it.first] }
    }
}
