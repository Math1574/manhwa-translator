package app.manhwa.core

/** Préparation du texte OCR avant traduction : filtrage du bruit, casse, découpage en phrases. */
object TextNormalizer {
    private val tokenRe = Regex("ZQ\\d+X")
    private val maskRe = Regex("\uE000(\\d+)\uE001")
    private val vowels = "aeiouyAEIOUYàâäéèêëîïôöùûüÿœæÀÂÉÈÊÎÔÙÛ"
    private val abbrev = Regex("(?i)\\b(mr|mrs|ms|dr|st|sr|jr|vs|no)\\.$")
    private val splitRe = Regex("(?<=[.!?…])\\s+|(?<=[。！？])")

    /**
     * Vrai pour les textes inutilisables : onomatopées sans voyelle (« HMPH », « SHH »), débris d'OCR, ponctuation seule.
     * Le texte CJK n'est jamais considéré comme du bruit.
     */
    fun isNoise(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (ScriptDetector.detect(t) != "en") return false
        val letters = t.count { it.isLetter() }
        val nonSpace = t.count { !it.isWhitespace() }
        if (letters < 2) return true
        if (letters.toFloat() / nonSpace < 0.5f) return true
        return t.none { it in vowels }
    }

    /** Texte en majuscules (cris, lettrage de bulle) : au moins 4 lettres dont plus de 80 % en capitales. */
    fun isAllCaps(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= 4 && letters.count { it.isUpperCase() }.toFloat() / letters.length >= 0.8f
    }

    /** Le lettrage d'origine est en majuscules : on garde cette apparence dans la traduction. */
    fun matchCase(source: String, translated: String): String = if (isAllCaps(source)) translated.uppercase() else translated

    /**
     * Le texte de bulle est souvent en majuscules, ce qui dégrade nettement la traduction automatique.
     * Si plus de 80 % des lettres sont en capitales, on passe en casse de phrase. Les jetons du glossaire sont conservés.
     */
    fun fixCase(text: String): String {
        val tokens = ArrayList<String>()
        val masked = tokenRe.replace(text) { tokens.add(it.value); "\uE000${tokens.size - 1}\uE001" }
        val letters = masked.filter { it.isLetter() }
        if (letters.length < 4) return text
        if (letters.count { it.isUpperCase() }.toFloat() / letters.length < 0.8f) return text
        val sb = StringBuilder()
        var cap = true
        for (ch in masked.lowercase()) {
            if (cap && ch.isLetter()) { sb.append(ch.uppercaseChar()); cap = false }
            else {
                sb.append(ch)
                if (ch == '.' || ch == '!' || ch == '?' || ch == '…') cap = true
                else if (ch.isLetterOrDigit()) cap = false
            }
        }
        val fixed = Regex("\\bi\\b").replace(sb.toString(), "I")
        return maskRe.replace(fixed) { tokens[it.groupValues[1].toInt()] }
    }

    /** Découpe en phrases (les phrases courtes se traduisent mieux et sans omission). Respecte « Mr. », « Dr. »… */
    fun splitSentences(text: String): List<String> {
        val raw = text.trim().split(splitRe).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var prefix = ""
        for (s in raw) {
            val onlyPunct = s.none { it.isLetterOrDigit() }
            when {
                onlyPunct && out.isNotEmpty() -> out[out.size - 1] = out.last() + " " + s
                onlyPunct -> prefix = if (prefix.isEmpty()) s else "$prefix $s"
                out.isNotEmpty() && abbrev.containsMatchIn(out.last()) -> out[out.size - 1] = out.last() + " " + s
                else -> { out.add(if (prefix.isEmpty()) s else "$prefix $s"); prefix = "" }
            }
        }
        if (out.isEmpty() && prefix.isNotEmpty()) out.add(prefix)
        return out
    }
}
