package app.manhwa.core

import kotlin.math.ln

/**
 * Lexique anglais : mots valides (liste complète) et rang de fréquence des mots courants (plus petit = plus fréquent).
 * Sert à réparer les erreurs de lecture (mots collés, chiffres ou minuscules parasites, faute d'une lettre) et à repérer les débris.
 */
class EnglishLexicon(allWords: Iterable<String>, frequent: List<String>) {
    private val words = HashSet<String>()
    private val rank = HashMap<String, Int>()

    init {
        for (w in allWords) { val t = w.trim().lowercase(); if (t.isNotEmpty()) words.add(t) }
        frequent.forEachIndexed { i, w -> val t = w.trim().lowercase(); if (t.isNotEmpty()) { words.add(t); rank.putIfAbsent(t, i) } }
        for (w in listOf("i", "a", "oh", "ah", "ok", "okay", "yeah", "hmm", "huh", "hey", "ha", "haha", "mr", "mrs", "ms", "dr")) words.add(w)
    }

    private val contractionBase = setOf("don", "can", "won", "isn", "aren", "didn", "doesn", "couldn", "wouldn", "shouldn", "hadn", "hasn", "haven", "wasn", "weren", "let", "i", "it", "he", "she", "that", "there", "what", "who", "we", "you", "they", "here", "how", "where", "when", "why", "o", "ma", "y")
    private val contractionEnd = setOf("s", "t", "ll", "re", "ve", "d", "m", "clock", "am")

    fun known(word: String): Boolean {
        val w = word.lowercase().trim('\'', '’')
        if (w.isEmpty()) return true
        if (w in words) return true
        val ap = w.indexOfFirst { it == '\'' || it == '’' }
        if (ap > 0) { val a = w.substring(0, ap); val b = w.substring(ap + 1); return (a in contractionBase || a in words) && (b in contractionEnd) }
        return false
    }

    /** Rang de fréquence (0 = très courant) ; 10 000 pour un mot valide mais rare ; Int.MAX_VALUE pour un mot inconnu. */
    fun rank(word: String): Int { val w = word.lowercase(); return rank[w] ?: if (known(w)) 10_000 else Int.MAX_VALUE }
    fun isCommon(word: String) = rank.containsKey(word.lowercase())
}

object OcrRepair {
    private val tokenRe = Regex("[A-Za-z0-9'’]+")
    private val digitMap = mapOf('0' to "OD", '1' to "IL", '3' to "ES", '4' to "A", '5' to "S", '6' to "G", '8' to "B", '2' to "Z")
    private val shortWords = setOf("go", "to", "of", "in", "is", "it", "on", "or", "no", "so", "my", "me", "we", "he", "be", "by", "do", "up", "us", "an", "at", "as", "if", "oh")
    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** Répare un texte lu par l'OCR. [protect] : mots à ne jamais modifier (noms du chapitre). Texte non anglais : renvoyé tel quel. */
    fun repair(text: String, lex: EnglishLexicon, protect: Set<String> = emptySet()): String {
        if (ScriptDetector.dominant(text) != "en") return text
        var s = text.replace(Regex("([.!?])(?=[A-Za-z])"), "$1 ")         // « EVERYONE!IP » → « EVERYONE! IP »
        // « I AND I ALSO HAVE… » : un « I » parasite (bord de bulle, pointe) lu devant « AND I » ; la traduction donnait « MOI ET MOI AVONS ». Seulement en majuscules, en tête de texte.
        s = s.replace(Regex("^I (AND|BUT|SO|THEN|OR|YET) (I\\b)"), "$1 $2")
        s = s.replace(Regex("(?<![A-Za-z0-9])[Oo](?=[.,]\\d)"), "0")      // « SET TO O.1% » → « SET TO 0.1% » : un O collé à une décimale est un zéro
        val letters = s.filter { it.isLetter() }
        val caps = letters.length >= 4 && letters.count { it.isUpperCase() }.toFloat() / letters.length >= 0.5f
        val tokens = tokenRe.findAll(s).toList()
        val alphaTokens = tokens.count { t -> t.value.any { it.isLetter() } }
        // une chiffre isolé n'est corrigé que si la ligne contient d'autres signes d'erreur de lecture (mots inconnus)
        // la lecture n'est pas jugée abîmée à cause d'un débris collé en fin de phrase (retiré ensuite par dropJunkTail) : « AS 36?! Taay sr4: In ) » n'a pas un nombre abîmé
        val bodyTokens = tokenRe.findAll(dropJunkTail(s, lex)).toList()
        val noisy = bodyTokens.any { t -> val c = t.value.trim('\'', '’'); c.any { it.isLetter() } && c.none { it.isDigit() } && c.length >= 3 && !lex.known(c) && c.lowercase() !in protect }
        val sb = StringBuilder(); var last = 0
        for (m in tokens) {
            sb.append(s, last, m.range.first); last = m.range.last + 1
            sb.append(fixToken(m.value, caps, alphaTokens, noisy, lex, protect))
        }
        sb.append(s, last, s.length)
        return sb.toString()
    }

    private val tailRe = Regex("^(.*[.!?…][\"'”’)]*)\\s+([^.!?…]{1,24})$", RegexOption.DOT_MATCHES_ALL)

    /**
     * Débris collé en fin de phrase : un texte voisin (caractères d'une fenêtre de jeu, d'un décor) lu avec la dernière ligne (« …SI ÉLEVÉ ? Taay sr4: In ) »).
     * Retiré seulement s'il suit une fin de phrase, reste court, contient un chiffre ou un signe parasite et ressemble à un débris.
     * À appliquer au texte final : [repair] sert aussi à comparer des lectures, où retirer un débris ferait paraître plausible une lecture absurde.
     */
    fun dropJunkTail(text: String, lex: EnglishLexicon): String {
        val tail = junkTail(text, lex)
        if (tail.isEmpty()) return text
        return tailRe.find(text.trim())!!.groupValues[1]
    }

    /** Débris collé en fin de phrase (voir [dropJunkTail]) ; chaîne vide s'il n'y en a pas. */
    fun junkTail(text: String, lex: EnglishLexicon): String {
        if (ScriptDetector.dominant(text) != "en") return ""
        val m = tailRe.find(text.trim()) ?: return ""
        val tail = m.groupValues[2].trim()
        if (tail.any { !it.isLetter() && !it.isWhitespace() && it != '\'' && it != '’' && it != '-' } && JunkFilter.isDebris(tail, lex)) return tail
        return ""
    }

    private fun recase(orig: String, cand: String, caps: Boolean): String = when {
        caps || orig.length > 1 && orig.all { !it.isLetter() || it.isUpperCase() } -> cand.uppercase()
        orig.firstOrNull()?.isUpperCase() == true -> cand.lowercase().replaceFirstChar { it.uppercase() }
        else -> cand.lowercase()
    }

    private fun fixToken(tok: String, caps: Boolean, alphaTokens: Int, noisy: Boolean, lex: EnglishLexicon, protect: Set<String>): String {
        val core = tok.trim('\'', '’')
        if (core.isEmpty()) return tok
        if (core.all { it.isDigit() }) {
            // nombre seul : gardé (36, 20, 12, 25… sont de vrais nombres), sauf « 50 » → « SO » ou « 4 » → « A » dans une phrase dont la lecture est manifestement abîmée ;
            // chaque chiffre doit alors devenir une lettre (jamais supprimé : « 36 » ne devient pas « E »)
            if (alphaTokens < 3 || core.length > 2 || !noisy) return tok
            // deux chiffres ne deviennent des lettres que si elles forment un petit mot courant (« 50 » → « SO ») : « 36 » ne devient jamais « EG »
            return digitFix(core, lex, keepAll = true)?.takeIf { lex.isCommon(it) && (core.length < 2 || it.lowercase() in shortWords) }?.let { recase(tok, it, caps) } ?: tok
        }
        // nombre abîmé : « 1OO » → « 100 », « 2O0X » → « 200X » (O et I lus à la place de 0 et 1 dans un nombre)
        numberFix(core)?.let { return tok.replace(core, it) }
        val lower = core.lowercase()
        if (lower in protect) return tok
        val hasDigit = core.any { it.isDigit() }
        if (!hasDigit && lex.known(lower)) return if (caps) tok.uppercase() else tok   // mot valide : on harmonise seulement la casse
        // 1) chiffres pris pour des lettres (INJURIE3 → INJURIES, 50 → SO, wA4NT → WANT)
        if (hasDigit) digitFix(core, lex)?.let { return recase(tok, it, caps).let { r -> keepApostrophes(tok, r) } }
        if (core.length < 3) return tok
        // 2) mots collés (GOCHECK → GO CHECK)
        if (core.length >= 6) segment(lower, lex)?.let { return recase(tok, it, caps) }
        // 3) faute d'une lettre sur un mot courant (CHIEFZ → CHIEF)
        if (core.length >= 4) editOne(lower, lex)?.let { return recase(tok, it, caps) }
        return if (caps) tok.uppercase() else tok
    }

    private fun keepApostrophes(orig: String, fixed: String): String = fixed

    /** Nombre dont des chiffres ont été lus comme des lettres : au moins un vrai chiffre, le reste n'étant que des O, I, l ou | (suffixe x ou % permis). */
    private val lookalikeNumber = Regex("[0-9OoIl|]{2,}[xX%]?")
    private fun numberFix(core: String): String? {
        if (!lookalikeNumber.matches(core) || core.none { it.isDigit() }) return null
        val body = core.trimEnd('x', 'X', '%')
        if (body.isEmpty() || body.none { it.isDigit() }) return null
        val mapped = body.map { when (it) { 'O', 'o' -> '0'; 'I', 'l', '|' -> '1'; else -> it } }.joinToString("")
        return mapped + core.substring(body.length)
    }

    /**
     * [keepAll] : chaque chiffre doit devenir une lettre (nombre seul). Sinon un chiffre parasite dans un mot peut aussi être supprimé (« INJURIE3 » → « INJURIES »).
     * Un résultat d'une seule lettre n'est accepté que s'il s'agit d'un mot anglais (A, I) : « E », « D » ou « S » n'en sont pas.
     */
    private fun digitFix(core: String, lex: EnglishLexicon, keepAll: Boolean = false): String? {
        val up = core.uppercase()
        // variantes : chaque chiffre → l'une de ses lettres possibles, ou supprimé (sauf pour un nombre seul)
        var cands = listOf("")
        for (c in up) {
            val opts: List<String> = if (c.isDigit()) (digitMap[c]?.map { it.toString() } ?: emptyList()) + (if (keepAll) emptyList() else listOf("")) else listOf(c.toString())
            cands = cands.flatMap { p -> opts.map { p + it } }.take(400)
        }
        val ok = cands.filter { it.isNotEmpty() && lex.known(it) && (it.length >= 3 || lex.isCommon(it)) && (it.length >= 2 || it == "A" || it == "I") }
        return ok.minByOrNull { lex.rank(it) }
    }

    /** Découpe un mot inconnu en 2 ou 3 mots connus (coût = somme des logarithmes de rang) ; null si impossible ou douteux. */
    private fun segment(w: String, lex: EnglishLexicon): String? {
        val n = w.length
        val best = DoubleArray(n + 1) { Double.MAX_VALUE }; val prev = IntArray(n + 1) { -1 }; val cnt = IntArray(n + 1)
        best[0] = 0.0
        for (i in 1..n) for (j in 0 until i) {
            val piece = w.substring(j, i)
            if (best[j] == Double.MAX_VALUE || cnt[j] >= 3) continue
            if (piece.length < 3 && piece != "a" && piece != "i" && piece !in shortWords) continue
            if (!lex.known(piece) || !(lex.isCommon(piece) || piece.length >= 5)) continue   // pas de morceaux rares : on évite de couper des noms propres
            val cost = best[j] + ln(2.0 + lex.rank(piece).coerceAtMost(30000)) + 3.0
            if (cost < best[i]) { best[i] = cost; prev[i] = j; cnt[i] = cnt[j] + 1 }
        }
        if (prev[n] < 0 || cnt[n] < 2) return null
        val parts = ArrayList<String>(); var i = n
        while (i > 0) { parts.add(0, w.substring(prev[i], i)); i = prev[i] }
        if (parts.any { it.length < 3 && it != "a" && it != "i" && it !in shortWords }) return null
        return parts.joinToString(" ")
    }

    private fun editOne(w: String, lex: EnglishLexicon): String? {
        if (w.length < 6) return null   // sur les mots courts, plusieurs corrections plausibles : on ne devine pas
        val cands = LinkedHashSet<String>()
        fun consider(c: String) { if (c.length >= 3 && c != w && lex.known(c)) cands.add(c) }
        for (i in w.indices) consider(w.removeRange(i, i + 1))                                       // suppression
        for (i in 0 until w.length - 1) consider(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)) // transposition
        val abc = ALPHA.lowercase()
        for (i in w.indices) for (c in abc) { consider(w.substring(0, i) + c + w.substring(i + 1)); consider(w.substring(0, i) + c + w.substring(i)) } // substitution, insertion
        for (c in abc) consider(w + c)
        if (cands.isEmpty()) return null
        if (cands.size == 1) return cands.first().takeIf { lex.isCommon(it) || w.length >= 8 }   // un nom propre inconnu ne devient pas un mot rare
        val ranked = cands.sortedBy { lex.rank(it) }
        val r0 = lex.rank(ranked[0]); val r1 = lex.rank(ranked[1])
        return if (r0 < 10_000 && r1.toLong() >= 4L * r0 + 50) ranked[0] else null   // ambigu : on laisse tel quel
    }
}

/** Filtre des lignes lues qui ne sont pas du dialogue : filigranes, logos, débris de lecture sur le dessin. */
object JunkFilter {
    private val watermark = Regex("(?i)(asura|\\.com\\b|\\.net\\b|\\.org\\b|\\.io\\b|https?:|www\\.|discord|scanlat|scans\\b|read (it )?(at|on)\\b|join (us|our))")
    private val interjection = Regex("(?i)^(?:[a-z]*([a-z])\\1{2,}[a-z]*|a+r*g+h+|u+g+h+|g+a+g+h*|h+m+p*h*|h+a+h+a*|k+y+a+|t+s+k+|a+h+|o+h+|u+h+|e+h+|h+u+h+|w+h+a+t+|n+o+|y+e+s+)$")

    fun isWatermark(text: String): Boolean = watermark.containsMatchIn(text)

    /** Débris : aucun mot anglais reconnu, texte court et qui ne ressemble pas à une interjection. Texte non anglais : jamais un débris. */
    fun isDebris(text: String, lex: EnglishLexicon): Boolean {
        if (ScriptDetector.dominant(text) != "en") return false
        val toks = Regex("[A-Za-z0-9'’]+").findAll(text).map { it.value }.toList()
        if (toks.isEmpty()) return false
        val letters = text.count { it.isLetter() }
        if (letters > 16) return false
        if (toks.any { it.any { c -> c.isLetter() } && it.length >= 3 && lex.known(it) && it.none { c -> c.isDigit() } }) return false
        if (toks.any { it.any { c -> c.isDigit() } && it.any { c -> c.isLetter() } } && letters <= 10) return true
        if (toks.any { interjection.matches(it.trim('\'', '’')) }) return false
        if (toks.all { t -> t.length <= 2 && lex.known(t) }) return false      // « OK », « NO », « I »
        return toks.none { lex.known(it) && it.length >= 3 }
    }
}
