package app.manhwa.core

data class GlossaryEntry(val source: String, val target: String)

data class Protected(val text: String, val tokens: Map<String, String>)

/**
 * Glossaire local : noms propres et termes récurrents + corrections exactes de blocs.
 * La traduction automatique ne connaît pas le glossaire : on remplace les termes par des jetons
 * avant traduction puis on les restitue. Si un jeton est perdu, l'appelant retraduit sans protection.
 */
class Glossary(entries: List<GlossaryEntry> = emptyList(), corrections: Map<String, String> = emptyMap()) {
    private val terms = LinkedHashMap<String, String>()
    private val fixes = LinkedHashMap<String, String>()

    init {
        entries.forEach { addTerm(it.source, it.target) }
        corrections.forEach { (k, v) -> addCorrection(k, v) }
    }

    fun addTerm(source: String, target: String) {
        if (source.isNotBlank() && target.isNotBlank()) terms[source.trim()] = target.trim()
    }

    fun removeTerm(source: String) { terms.remove(source.trim()) }
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
            if (out.contains(src)) {
                val token = "ZQ${i}X"
                i++
                out = out.replace(src, token)
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
    }

    private fun normalize(s: String) = s.filter { !it.isWhitespace() }

    companion object {
        fun parse(data: String): Glossary {
            val g = Glossary()
            for (line in data.lines()) {
                val p = line.split('\t')
                if (p.size != 3) continue
                when (p[0]) {
                    "T" -> g.addTerm(unesc(p[1]), unesc(p[2]))
                    "C" -> g.fixes[unesc(p[1])] = unesc(p[2])
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
