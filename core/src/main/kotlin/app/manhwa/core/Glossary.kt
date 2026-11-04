package app.manhwa.core

data class GlossaryEntry(val source: String, val target: String)

/** Terme propre à une œuvre (clé d'œuvre : site + titre). */
data class SeriesTerm(val series: String, val source: String, val target: String)

data class Protected(val text: String, val tokens: Map<String, String>)

/**
 * Glossaire local : noms propres et termes récurrents + corrections exactes de blocs.
 * La traduction automatique ne connaît pas le glossaire : on remplace les termes par des jetons
 * avant traduction puis on les restitue. Si un jeton est perdu, l'appelant retraduit sans protection.
 */
class Glossary(entries: List<GlossaryEntry> = emptyList(), corrections: Map<String, String> = emptyMap()) {
    private val terms = LinkedHashMap<String, String>()
    private val fixes = LinkedHashMap<String, String>()
    /** Termes propres à une œuvre : ils s'ajoutent aux termes communs et les remplacent quand le terme d'origine est le même. */
    private val seriesTerms = LinkedHashMap<String, LinkedHashMap<String, String>>()

    init {
        entries.forEach { addTerm(it.source, it.target) }
        corrections.forEach { (k, v) -> addCorrection(k, v) }
    }

    fun addTerm(source: String, target: String) {
        if (source.isNotBlank() && target.isNotBlank()) terms[source.trim()] = target.trim()
    }

    fun removeTerm(source: String) { terms.remove(source.trim()) }

    fun addSeriesTerm(series: String, source: String, target: String) {
        if (series.isNotBlank() && source.isNotBlank() && target.isNotBlank()) seriesTerms.getOrPut(series) { LinkedHashMap() }[source.trim()] = target.trim()
    }

    fun removeSeriesTerm(series: String, source: String) {
        seriesTerms[series]?.remove(source.trim()); if (seriesTerms[series]?.isEmpty() == true) seriesTerms.remove(series)
    }

    fun seriesTerms(): List<SeriesTerm> = seriesTerms.flatMap { (k, m) -> m.map { SeriesTerm(k, it.key, it.value) } }

    /** Glossaire à utiliser pour une œuvre : termes communs, puis ceux de l'œuvre ; les corrections de blocs restent communes. Sans œuvre : une copie du glossaire commun. */
    fun forSeries(series: String?): Glossary {
        val g = Glossary()
        terms.forEach { (k, v) -> g.addTerm(k, v) }
        if (series != null) seriesTerms[series]?.forEach { (k, v) -> g.addTerm(k, v) }
        fixes.forEach { (k, v) -> g.fixes[k] = v }
        return g
    }
    fun addCorrection(sourceBlock: String, translation: String) {
        if (sourceBlock.isNotBlank()) fixes[normalize(sourceBlock)] = translation
    }

    fun terms(): List<GlossaryEntry> = terms.map { GlossaryEntry(it.key, it.value) }
    fun correctionFor(sourceBlock: String): String? = fixes[normalize(sourceBlock)]

    fun protect(text: String): Protected {
        var out = text
        val tokens = LinkedHashMap<String, String>()
        var i = 0
        for ((src, tgt) in terms.entries.sortedByDescending { it.key.length }) {
            if (out.contains(src, ignoreCase = true)) {
                val token = "ZQ${i}X"
                i++
                out = out.replace(src, token, ignoreCase = true)
                tokens[token] = tgt
            }
        }
        return Protected(out, tokens)
    }

    /** Renvoie null si au moins un jeton a disparu de la traduction. */
    fun restore(translated: String, p: Protected): String? {
        var out = translated
        for ((token, tgt) in p.tokens) {
            if (!out.contains(token)) return null
            out = out.replace(token, tgt)
        }
        return out
    }

    fun serialize(): String = buildString {
        terms.forEach { (k, v) -> append("T\t").append(esc(k)).append('\t').append(esc(v)).append('\n') }
        fixes.forEach { (k, v) -> append("C\t").append(esc(k)).append('\t').append(esc(v)).append('\n') }
        seriesTerms.forEach { (s, m) -> m.forEach { (k, v) -> append("S\t").append(esc(s)).append('\t').append(esc(k)).append('\t').append(esc(v)).append('\n') } }
    }

    private fun normalize(s: String) = s.filter { !it.isWhitespace() }

    companion object {
        fun parse(data: String): Glossary {
            val g = Glossary()
            for (line in data.lines()) {
                val p = line.split('\t')
                when {
                    p.size == 3 && p[0] == "T" -> g.addTerm(unesc(p[1]), unesc(p[2]))
                    p.size == 3 && p[0] == "C" -> g.fixes[unesc(p[1])] = unesc(p[2])
                    p.size == 4 && p[0] == "S" -> g.addSeriesTerm(unesc(p[1]), unesc(p[2]), unesc(p[3]))
                }
            }
            return g
        }
    }
}

internal fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
internal fun unesc(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (s[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); 'r' -> sb.append('\r'); else -> sb.append(s[i + 1]) }
            i += 2
        } else { sb.append(c); i++ }
    }
    return sb.toString()
}
