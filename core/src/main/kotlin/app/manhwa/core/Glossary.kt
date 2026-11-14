package app.manhwa.core

data class GlossaryEntry(val source: String, val target: String)

/** Terme propre à une œuvre (clé d'œuvre : site + titre). */
data class SeriesTerm(val series: String, val source: String, val target: String)

/** Traduction retenue pour un texte lu exactement ainsi (correction manuelle d'une bulle) : [source] est le texte tel qu'on l'a lu, sans les sauts de ligne, quand il est connu. */
data class TextCorrection(val source: String, val translation: String)

data class Protected(val text: String, val tokens: Map<String, String>)

/**
 * Glossaire local : noms propres et termes récurrents + corrections exactes de blocs.
 * La traduction automatique ne connaît pas le glossaire : on remplace les termes par des jetons
 * avant traduction puis on les restitue. Si un jeton est perdu, l'appelant retraduit sans protection.
 */
class Glossary(entries: List<GlossaryEntry> = emptyList(), corrections: Map<String, String> = emptyMap()) {
    private val terms = LinkedHashMap<String, String>()
    private val fixes = LinkedHashMap<String, String>()
    /** Texte lu, tel qu'on l'a vu, de chaque correction (la clé des corrections n'a plus d'espaces) : pour les montrer lisiblement. Absent des corrections d'avant ce suivi. */
    private val fixText = HashMap<String, String>()
    /** Termes propres à une œuvre : ils s'ajoutent aux termes communs et les remplacent quand le terme d'origine est le même. */
    private val seriesTerms = LinkedHashMap<String, LinkedHashMap<String, String>>()

    /**
     * Numéro de version du glossaire : il augmente d'un cran chaque fois qu'un terme est ajouté ou que sa traduction change. Une page traduite garde le numéro du glossaire avec lequel
     * elle l'a été : les termes plus récents ([termsNewerThan]) ne concernent que les textes qui les contiennent, qui peuvent alors être retraduits seuls.
     */
    var revision: Long = 0L
        private set
    /** Version à laquelle chaque terme a été ajouté ou modifié (0 : terme d'avant ce suivi, jamais considéré comme récent). Clé : le terme commun, ou « œuvre, séparateur, terme » pour un terme d'œuvre. */
    private val termRev = HashMap<String, Long>()

    init {
        entries.forEach { addTerm(it.source, it.target) }
        corrections.forEach { (k, v) -> addCorrection(k, v) }
    }

    /** Ajoute un terme ou change sa traduction. Un terme déjà connu tel quel ne change rien (ni version, ni retraduction). */
    fun addTerm(source: String, target: String) {
        if (source.isBlank() || target.isBlank()) return
        val s = source.trim(); val t = target.trim()
        if (terms[s] == t) return
        terms[s] = t; termRev[s] = ++revision
    }

    fun removeTerm(source: String) { terms.remove(source.trim()); termRev.remove(source.trim()) }

    fun addSeriesTerm(series: String, source: String, target: String) {
        if (series.isBlank() || source.isBlank() || target.isBlank()) return
        val s = source.trim(); val t = target.trim()
        val m = seriesTerms.getOrPut(series) { LinkedHashMap() }
        if (m[s] == t) return
        m[s] = t; termRev[series + SEP + s] = ++revision
    }

    fun removeSeriesTerm(series: String, source: String) {
        seriesTerms[series]?.remove(source.trim()); termRev.remove(series + SEP + source.trim())
        if (seriesTerms[series]?.isEmpty() == true) seriesTerms.remove(series)
    }

    fun seriesTerms(): List<SeriesTerm> = seriesTerms.flatMap { (k, m) -> m.map { SeriesTerm(k, it.key, it.value) } }

    /** Le terme est-il connu tel quel (même traduction) : pour une œuvre si [series] est donnée, sinon pour toutes ? */
    fun hasTerm(series: String?, source: String, target: String): Boolean {
        val s = source.trim(); val t = target.trim()
        return if (series != null) seriesTerms[series]?.get(s) == t else terms[s] == t
    }

    /** Termes (de ce glossaire, déjà fusionné pour une œuvre) ajoutés ou modifiés après la version [rev] : ceux qu'une page traduite avec cette version ne connaît pas encore. */
    fun termsNewerThan(rev: Long): List<GlossaryEntry> = terms.filter { (termRev[it.key] ?: 0L) > rev }.map { GlossaryEntry(it.key, it.value) }

    /** Copie indépendante (versions comprises) : sert à simuler un ajout de termes sans toucher au glossaire réel. */
    fun copy(): Glossary = parse(serialize())

    /** Glossaire à utiliser pour une œuvre : termes communs, puis ceux de l'œuvre ; les corrections de blocs restent communes. Sans œuvre : une copie du glossaire commun. */
    fun forSeries(series: String?): Glossary {
        val g = Glossary()
        terms.forEach { (k, v) -> g.loadTerm(k, v, termRev[k] ?: 0L) }
        if (series != null) seriesTerms[series]?.forEach { (k, v) -> g.loadTerm(k, v, termRev[series + SEP + k] ?: 0L) }
        fixes.forEach { (k, v) -> g.fixes[k] = v; fixText[k]?.let { t -> g.fixText[k] = t } }
        g.revision = revision
        return g
    }

    private fun loadTerm(source: String, target: String, rev: Long) {
        terms[source] = target
        if (rev > 0L) termRev[source] = rev else termRev.remove(source)
    }
    fun addCorrection(sourceBlock: String, translation: String) {
        if (sourceBlock.isBlank()) return
        val k = normalize(sourceBlock)
        fixes[k] = translation
        fixText[k] = sourceBlock.trim().replace(Regex("\\s+"), " ")
    }

    /** Les traductions retenues pour un texte exact, dans l'ordre où elles ont été faites. */
    fun corrections(): List<TextCorrection> = fixes.map { (k, v) -> TextCorrection(fixText[k] ?: k, v) }

    /** Oublie la traduction retenue pour ce texte (le texte est de nouveau traduit comme les autres). Vrai si elle existait. */
    fun removeCorrection(sourceBlock: String): Boolean {
        val k = normalize(sourceBlock)
        fixText.remove(k)
        return fixes.remove(k) != null
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

    /** Format : une ligne par terme (T), correction (C) ou terme d'œuvre (S), la version du terme en dernière colonne si elle est connue (pour une correction : le texte lu, tel qu'on l'a vu), et une ligne R pour la version du glossaire. Les anciens fichiers (sans versions) se lisent tels quels. */
    fun serialize(): String = buildString {
        if (revision > 0L) append("R\t").append(revision).append('\n')
        terms.forEach { (k, v) -> append("T\t").append(esc(k)).append('\t').append(esc(v)); termRev[k]?.let { append('\t').append(it) }; append('\n') }
        fixes.forEach { (k, v) -> append("C\t").append(esc(k)).append('\t').append(esc(v)); fixText[k]?.let { append('\t').append(esc(it)) }; append('\n') }
        seriesTerms.forEach { (s, m) -> m.forEach { (k, v) -> append("S\t").append(esc(s)).append('\t').append(esc(k)).append('\t').append(esc(v)); termRev[s + SEP + k]?.let { append('\t').append(it) }; append('\n') } }
    }

    private fun normalize(s: String) = s.filter { !it.isWhitespace() }

    companion object {
        private const val SEP = '\u0000'

        fun parse(data: String): Glossary {
            val g = Glossary()
            var fileRev = 0L
            for (line in data.lines()) {
                val p = line.split('\t')
                when {
                    p.size == 2 && p[0] == "R" -> fileRev = p[1].toLongOrNull() ?: 0L
                    (p.size == 3 || p.size == 4) && p[0] == "T" -> {
                        val s = unesc(p[1]).trim(); val t = unesc(p[2]).trim()
                        if (s.isNotBlank() && t.isNotBlank()) g.loadTerm(s, t, p.getOrNull(3)?.toLongOrNull() ?: 0L)
                    }
                    (p.size == 3 || p.size == 4) && p[0] == "C" -> { val k = unesc(p[1]); g.fixes[k] = unesc(p[2]); if (p.size == 4) g.fixText[k] = unesc(p[3]) }
                    (p.size == 4 || p.size == 5) && p[0] == "S" -> {
                        val series = unesc(p[1]); val s = unesc(p[2]).trim(); val t = unesc(p[3]).trim()
                        if (series.isNotBlank() && s.isNotBlank() && t.isNotBlank()) {
                            g.seriesTerms.getOrPut(series) { LinkedHashMap() }[s] = t
                            val r = p.getOrNull(4)?.toLongOrNull() ?: 0L
                            if (r > 0L) g.termRev[series + SEP + s] = r
                        }
                    }
                }
            }
            g.revision = maxOf(fileRev, g.termRev.values.maxOrNull() ?: 0L)
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
