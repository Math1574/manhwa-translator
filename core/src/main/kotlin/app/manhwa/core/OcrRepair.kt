package app.manhwa.core

import kotlin.math.ln

/**
 * Lexique anglais : mots valides (liste complète) et rang de fréquence des mots courants (plus petit = plus fréquent).
 * Sert à réparer les erreurs de lecture (mots collés, chiffres ou minuscules parasites, faute d'une lettre) et à repérer les débris.
 * [genre] : vocabulaire propre aux manhwas (arts martiaux, fantasy, cour royale, onomatopées) ; ces mots sont valides et comptent comme courants (rang moyen).
 */
class EnglishLexicon(allWords: Iterable<String>, frequent: List<String>, genre: Iterable<String> = emptyList()) {
    private val words = HashSet<String>()
    private val rank = HashMap<String, Int>()
    private val commonByLength: Map<Int, List<String>> by lazy {
        val m = HashMap<Int, ArrayList<String>>()
        for ((w, r) in rank) if (r < 10_000 && w.all { it in 'a'..'z' }) m.getOrPut(w.length) { ArrayList() }.add(w)
        m
    }

    init {
        for (w in allWords) { val t = w.trim().lowercase(); if (t.isNotEmpty()) words.add(t) }
        frequent.forEachIndexed { i, w -> val t = w.trim().lowercase(); if (t.isNotEmpty()) { words.add(t); rank.putIfAbsent(t, i) } }
        for (w in listOf("i", "a", "oh", "ah", "ok", "okay", "yeah", "hmm", "huh", "hey", "ha", "haha", "mr", "mrs", "ms", "dr")) words.add(w)
        var g = 0
        for (w in genre) { val t = w.trim().lowercase(); if (t.isEmpty() || t.startsWith("#")) continue; words.add(t); rank.putIfAbsent(t, GENRE_RANK + g / 8); g++ }
    }

    private val contractionBase = setOf("don", "can", "won", "isn", "aren", "didn", "doesn", "couldn", "wouldn", "shouldn", "hadn", "hasn", "haven", "wasn", "weren", "mustn", "needn", "ain", "mightn", "let", "i", "it", "he", "she", "that", "there", "what", "who", "we", "you", "they", "here", "how", "where", "when", "why", "o", "ma", "y")
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
    /** Mot courant : dans la liste des mots fréquents ou du genre, ou contraction valide (« couldn't »). */
    fun isCommon(word: String): Boolean { val w = word.lowercase(); return rank.containsKey(w) || (w.indexOf('\'') > 0 && known(w)) }
    /** Mots courants (lettres seules) dont la longueur est comprise entre [min] et [max] : sert à chercher le mot voulu quand deux lettres sont fausses. */
    fun commonWords(min: Int, max: Int): List<String> = (min..max).flatMap { commonByLength[it] ?: emptyList() }

    private companion object { const val GENRE_RANK = 6500 }
}

object OcrRepair {
    private val tokenRe = Regex("[A-Za-z0-9'’]+")
    private val digitMap = mapOf('0' to "OD", '1' to "IL", '3' to "ES", '4' to "A", '5' to "S", '6' to "G", '8' to "B", '2' to "Z")
    private val shortWords = setOf("go", "to", "of", "in", "is", "it", "on", "or", "no", "so", "my", "me", "we", "he", "be", "by", "do", "up", "us", "an", "at", "as", "if", "oh")
    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val possessiveRe = Regex("(.+)['’][sS]")
    private val legitMixedRe = Regex("(?i)^(\\d+(st|nd|rd|th)|[23]d|\\d+[kmgx]|\\d+p)$")

    /** Jeton fait de chiffres et de lettres qui est écrit ainsi pour de bon : ordinal (« 2ND », « 10TH »), « 3D », « 50K », « 5X », « 1080P ». Jamais une faute de lecture. */
    internal fun isLegitMixed(token: String): Boolean = legitMixedRe.matches(token)

    /** Répare un texte lu par l'OCR. [protect] : mots à ne jamais modifier (noms du chapitre). Texte non anglais : renvoyé tel quel. */
    fun repair(text: String, lex: EnglishLexicon, protect: Set<String> = emptySet()): String {
        if (ScriptDetector.dominant(text) != "en") return text
        // signes et lettres mal lus (points de suspension lus « 910 » ou « 2L », « I » lu « | » ou « 1 », apostrophes perdues) : avant tout le reste
        var s = OcrFragments.clean(text, lex, protect)
        s = s.replace(Regex("([.!?,])(?=[A-Za-z])"), "$1 ")         // « EVERYONE!IP » → « EVERYONE! IP » ; « WELL,IT'S » → « WELL, IT'S »
        // « I AND I ALSO HAVE… » : un « I » parasite (bord de bulle, pointe) lu devant « AND I » ; la traduction donnait « MOI ET MOI AVONS ». Seulement en majuscules, en tête de texte.
        s = s.replace(Regex("^I (AND|BUT|SO|THEN|OR|YET) (I\\b)"), "$1 $2")
        // « P WILL SWALLOW UP… » : le « I » d'un lettrage stylisé lu « P » devant un auxiliaire ou un verbe d'état (la traduction donnait « P VA ENGLOUTIR »). Un « P » isolé n'est pas un mot
        // anglais ; seulement en majuscules, en tête de texte ou de phrase, et jamais si « P » est un nom du chapitre.
        if ("p" !in protect) s = s.replace(Regex("(^|[.!?…] )P (?=(WILL|AM|HAVE|HAD|CAN|CAN'T|COULD|WOULD|SHALL|SHOULD|MUST|DON'T|DIDN'T|WON'T|WAS|WANT|NEED|KNOW|THINK|SWEAR|PROMISE)\\b)"), "$1I ")
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
        if (isLegitMixed(core)) return tok   // « 2ND » ne devient pas « ND », « 3D » ne devient pas « ED »
        // nombre abîmé : « 1OO » → « 100 », « 2O0X » → « 200X » (O et I lus à la place de 0 et 1 dans un nombre)
        numberFix(core)?.let { return tok.replace(core, it) }
        val lower = core.lowercase()
        if (lower in protect) return tok
        val hasDigit = core.any { it.isDigit() }
        if (!hasDigit && lex.known(lower)) return if (caps) tok.uppercase() else tok   // mot valide : on harmonise seulement la casse
        // possessif : « CHAERIN'S » → seul « CHAERIN » est examiné (« CHAGRIN'S » était un mot valide et remplaçait le nom)
        possessiveRe.matchEntire(core)?.let { m ->
            val base = m.groupValues[1]
            if (base.length >= 3 && base.none { it == '\'' || it == '’' }) {
                val fixed = fixToken(base, caps, alphaTokens, noisy, lex, protect)
                val i = tok.indexOf(core)
                return tok.substring(0, i) + (if (fixed == base && caps) base.uppercase() else fixed) + core.substring(base.length) + tok.substring(i + core.length)
            }
        }
        // cris et rires étirés (« AAAAARGH », « HEHEHE », « PWAHAHA ») : jamais coupés ni « corrigés »
        if (!hasDigit && JunkFilter.isInterjectionToken(core)) return if (caps) tok.uppercase() else tok
        // 1) chiffres pris pour des lettres (INJURIE3 → INJURIES, 50 → SO, wA4NT → WANT)
        if (hasDigit) digitFix(core, lex)?.let { return recase(tok, it, caps).let { r -> keepApostrophes(tok, r) } }
        if (core.length < 3) return tok
        // 2) faute d'une lettre sur un mot courant (CHIEFZ → CHIEF, CASTIE → CASTLE) ou mots collés (GOCHECK → GO CHECK)
        if (core.length >= 5) {
            val edit = if (core.length >= 6) editOne(lower, lex) else null
            val split = segment(lower, lex, strict = core.length == 5)
            val pick = when {
                edit != null && split != null ->
                    // « ASWORD » : retirer le « A » donnerait « SWORD » et perdrait l'article ; « A SWORD » est la bonne lecture
                    if (lower.startsWith("a") && edit == lower.substring(1)) split else if (lex.rank(edit) <= 8_000) edit else split
                else -> edit ?: split
            }
            if (pick != null) return recase(tok, pick, caps)
            // deux lettres fausses dans un long mot courant (« DALIGHTER » → « DAUGHTER ») : seulement s'il n'y a qu'un mot possible
            if (core.length >= 8) editTwo(lower, lex)?.let { return recase(tok, it, caps) }
        } else if (core.length >= 6) {
            editOne(lower, lex)?.let { return recase(tok, it, caps) }
        }
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

    /** Un mot inconnu peut-il être coupé en mots connus ? (sert à repérer « M » + « THEDEMON » après un « I » : « I MTHEDEMON » → « I'M THE DEMON ») */
    internal fun splittable(w: String, lex: EnglishLexicon): Boolean = w.length >= 5 && segment(w.lowercase(), lex) != null

    /**
     * Découpe un mot inconnu en 2 ou 3 mots connus (coût = somme des logarithmes de rang) ; null si impossible ou douteux.
     * Un très long mot (12 lettres et plus) peut avoir jusqu'à 5 morceaux. [strict] (mot de 5 lettres) : seulement deux morceaux très courants (« YOUDO » → « YOU DO »).
     */
    private fun segment(w: String, lex: EnglishLexicon, strict: Boolean = false): String? {
        val n = w.length
        val maxParts = if (strict) 2 else if (n >= 12) 5 else 3
        val best = DoubleArray(n + 1) { Double.MAX_VALUE }; val prev = IntArray(n + 1) { -1 }; val cnt = IntArray(n + 1)
        best[0] = 0.0
        for (i in 1..n) for (j in 0 until i) {
            val piece = w.substring(j, i)
            if (best[j] == Double.MAX_VALUE || cnt[j] >= maxParts) continue
            if (piece.length < 3 && piece != "a" && piece != "i" && piece !in shortWords) continue
            if (!lex.known(piece) || !(lex.isCommon(piece) || piece.length >= 5)) continue   // pas de morceaux rares : on évite de couper des noms propres
            if (piece.indexOf('\'') in 0..1) continue                                         // « n't » n'est pas un mot : « MUSTN'T » ne devient pas « MUST N'T »
            if (strict && lex.rank(piece) >= 1_500 && piece.length >= 3) continue
            var cost = best[j] + ln(2.0 + lex.rank(piece).coerceAtMost(30000)) + 3.0
            if (!lex.isCommon(piece)) cost += 3.0                                                   // morceau rare (« filet » dans « ifilet ») : seulement faute de mieux
            if (j == 0 && piece == "i") cost += 2.0                                                  // « I » collé devant un mot (« IFILET » = « IF I LET », pas « I FILET »)
            if (j == 0 && (piece == "a" || piece == "an" || piece == "the") && n - i >= 4) cost -= 2.5   // article collé devant un mot (« ASWORD » → « A SWORD »)
            if (cost < best[i]) { best[i] = cost; prev[i] = j; cnt[i] = cnt[j] + 1 }
        }
        if (prev[n] < 0 || cnt[n] < 2) return null
        val parts = ArrayList<String>(); var i = n
        while (i > 0) { parts.add(0, w.substring(prev[i], i)); i = prev[i] }
        if (parts.any { it.length < 3 && it != "a" && it != "i" && it !in shortWords }) return null
        if (parts.size > 3 && parts.any { !lex.isCommon(it) }) return null          // 4 ou 5 morceaux : tous doivent être des mots courants
        return parts.joinToString(" ")
    }

    /** Un mot courant à une lettre près (suppression, transposition, substitution, insertion) : accepté s'il est le seul, ou nettement plus fréquent que les autres. Un mot rare n'est jamais proposé. */
    private fun editOne(w: String, lex: EnglishLexicon): String? {
        if (w.length < 6) return null   // sur les mots courts, plusieurs corrections plausibles : on ne devine pas
        val cands = LinkedHashSet<String>()
        fun consider(c: String) { if (c.length >= 3 && c != w && lex.known(c) && lex.isCommon(c)) cands.add(c) }
        for (i in w.indices) consider(w.removeRange(i, i + 1))                                       // suppression
        for (i in 0 until w.length - 1) consider(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)) // transposition
        val abc = ALPHA.lowercase()
        for (i in w.indices) for (c in abc) { consider(w.substring(0, i) + c + w.substring(i + 1)); consider(w.substring(0, i) + c + w.substring(i)) } // substitution, insertion
        for (c in abc) consider(w + c)
        if (cands.isEmpty()) return null
        if (cands.size == 1) return cands.first()
        val ranked = cands.sortedBy { lex.rank(it) }
        val r0 = lex.rank(ranked[0]); val r1 = lex.rank(ranked[1])
        return if (r1.toLong() >= 4L * r0 + 50) ranked[0] else null   // ambigu : on laisse tel quel
    }

    /** Deux lettres fausses dans un long mot (8 lettres ou plus) : le seul mot courant à deux modifications près ; null s'il y en a plusieurs ou aucun. */
    private fun editTwo(w: String, lex: EnglishLexicon): String? {
        var found: String? = null
        for (c in lex.commonWords(w.length - 1, w.length + 1)) {
            if (c[0] != w[0] && c[c.length - 1] != w[w.length - 1]) continue      // ni début ni fin en commun : trop éloigné
            if (w.contains(c) || c.contains(w)) continue                          // un mot contenu dans l'autre : des lettres collées ou perdues, pas une faute de lecture (« TRENERGY » ne devient pas « ENERGY »)
            if (distanceAtMost2(w, c)) { if (found != null) return null; found = c }
        }
        return found
    }

    /** Distance d'édition (suppression, insertion, substitution, transposition) d'au plus 2 ? */
    internal fun distanceAtMost2(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 2) return false
        val n = a.length; val m = b.length
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
            d[i][j] = v
        }
        return d[n][m] <= 2
    }
}

/**
 * Parasites et signes mal lus qui ne sont pas des fautes de lettres : points de suspension lus comme des chiffres (« 910 », « 2L »), « I » lu « | » ou « 1 »,
 * apostrophes perdues (« COULDN T », « YOURE », « I M »), « ! » lu à la place d'un « I » (« BECAUSE! COULDN'T »). Chaque règle est étroite : elle ne s'applique que dans le contexte où l'erreur a été constatée.
 */
internal object OcrFragments {
    fun clean(text: String, lex: EnglishLexicon, protect: Set<String>): String {
        var s = text
        s = junkOpening(s)
        s = ntBangI.replace(s) { m -> m.groupValues[1] + "'" + m.groupValues[2] }
        s = trailingDots.replace(s, "...")
        s = strayInitial.replace(s, "")
        s = loneI(s)
        s = restoreApostrophes(s, lex)
        s = bangAsI.replace(s) { m -> m.groupValues[1] + " I" }
        s = leadingDots.replace(s, "...")
        return s
    }

    // ---------- points de suspension lus comme autre chose ----------

    /** Mots qui ouvrent une phrase : un nombre isolé devant l'un d'eux est un « ... » mal lu (« 910 HE WAS… »). */
    private val subjectStarts = setOf("HE", "SHE", "IT", "WE", "THEY", "YOU", "I", "THIS", "THAT", "THERE", "HERE", "WHAT", "WHO", "WHY", "HOW", "WHEN", "IF", "BUT", "SO", "THEN", "WELL", "HIS", "HER", "THEIR", "OUR", "MY", "YOUR", "THE", "NOW", "YES", "NOT")
    private val firstTwo = Regex("^(\\s*)(\\S+)(\\s+)(\\S+)")

    /** Un premier « mot » de une à trois lettres et chiffres mêlés (« 2L »), ou un nombre de un à trois chiffres devant « HE WAS », « IT IS »… : c'est le « ... » du début de la bulle. */
    private fun junkOpening(s: String): String {
        val m = firstTwo.find(s) ?: return s
        val first = m.groupValues[2]; val next = m.groupValues[4].trim('.', ',', '!', '?', '…', '"', '\'', '’', '“', '”', '(', ')')
        val letters = first.count { it.isLetter() }; val digits = first.count { it.isDigit() }
        val nextIsWord = next.length >= 2 && next.all { it.isLetter() || it == '\'' || it == '’' }
        val mixed = letters >= 1 && digits >= 1 && first.length <= 3 && first.all { it.isLetterOrDigit() } && !OcrRepair.isLegitMixed(first)
        val bare = letters == 0 && digits in 1..3 && first.all { it.isDigit() } && next.uppercase() in subjectStarts
        if (!nextIsWord || !(mixed || bare)) return s
        val r = m.groups[2]!!.range
        return s.substring(0, r.first) + "..." + s.substring(r.last + 1)
    }

    /** « RELICS. I.. » : la fin de la bulle (« RELICS... ») lue avec un point et un « I » en trop. Seulement avec deux points à la fin : « KNOW. I... » (trois points) est une vraie réplique. */
    private val trailingDots = Regex("(?<=[A-Za-z]{2})\\.\\s+[Il1|]\\.\\.\\s*$")
    /** « POWER. R. ALLOWING » : une consonne isolée suivie d'un point entre deux phrases est un débris (une virgule lue avec une lettre). Jamais pour des initiales (« J. K. ROWLING »). */
    private val strayInitial = Regex("(?<=[A-Za-z]{2}[.!?…])\\s+[B-HJ-NP-Zb-hj-np-z]\\.(?!\\s*[A-Za-z]\\.)(?=\\s+\\S)")
    /** « .ARE THEY… » / « ..AS I THOUGHT » : un ou deux points en tête sont un « ... » mal lu. */
    private val leadingDots = Regex("^\\s*\\.{1,2}(?=\\s*[A-Za-z])")

    // ---------- « I » lu autrement ----------

    /** Ce qui suit le pronom « I » : verbes, auxiliaires et morceaux de contraction. « 1 WILL », « 1 M » (« I'M »), « 1 LOOK » : le « 1 » est un « I ». */
    private val iFollowers = setOf(
        "M", "LL", "VE", "D", "AM", "WILL", "WAS", "CAN", "COULD", "WOULD", "SHOULD", "MUST", "HAVE", "HAD", "DON'T", "DIDN'T", "WON'T", "CAN'T", "COULDN'T", "WOULDN'T", "HAVEN'T", "SHALL", "MAY", "MIGHT", "DO", "DID",
        "GOT", "WANT", "NEED", "KNOW", "THINK", "LOOK", "SEE", "FEEL", "SAID", "TOLD", "MEAN", "SWEAR", "PROMISE", "GUESS", "SUPPOSE", "HOPE", "WISH", "LIKE", "LOVE", "HATE", "HEARD", "SAW", "NEVER", "ALSO", "ALWAYS",
        "JUST", "ALREADY", "ONLY", "STILL", "REALLY", "TRULY", "IMAGINE", "BELIEVE", "UNDERSTAND", "REMEMBER", "FORGOT", "MISS", "KNEW", "THOUGHT", "DOUBT", "BET", "AGREE", "ADMIT", "WONDER", "FEAR", "FOUND", "FELT",
        "TRUST", "MEANT", "PLAN", "DECIDED", "CHOSE", "REFUSE", "ACCEPT", "DESERVE", "KILLED", "SAVED", "LOST", "MADE", "TOOK", "GAVE", "CAME", "WENT", "BECAME", "AIN'T",
    )
    private val numericLeaders = setOf("CHAPTER", "EPISODE", "EP", "PART", "NO", "NUMBER", "VOL", "VOLUME", "LEVEL", "LV", "STAGE", "FLOOR", "PAGE", "ROUND", "RANK", "GRADE", "YEAR", "DAY", "WEEK", "MONTH", "AGE", "SEASON", "VERSION")
    private val pipeAsI = Regex("(?<!\\S)\\|(?=\\s+[A-Za-z])")
    private val loneOne = Regex("(?:(?<=[A-Za-z][,;:]?\\s)|(?<=[.!?…]\\s)|^)[1l|](?=\\s+[A-Za-z'’]+)")

    private fun loneI(s: String): String {
        val a = pipeAsI.replace(s, "I")
        return loneOne.replace(a) { m ->
            val before = a.substring(0, m.range.first).trimEnd(' ', ',', ';', ':')
            val prev = before.substringAfterLast(' ').trim('.', '!', '?', '"').uppercase()
            val next = a.substring(m.range.last + 1).trimStart().takeWhile { it.isLetter() || it == '\'' || it == '’' }.uppercase()
            if (next in iFollowers && prev !in numericLeaders) "I" else m.value
        }
    }

    // ---------- apostrophes perdues ----------

    /** « CANIT BE HELPED » : l'apostrophe de « CAN'T » lue « I » (ou « l », « 1 », « | »). Aucun mot anglais ne s'écrit ainsi. */
    private val ntBangI = Regex("(?i)\\b(can|don|won|isn|aren|didn|doesn|couldn|wouldn|shouldn|hadn|hasn|haven|wasn|weren|mustn)[il1|](t)\\b")

    private val ntSpace = Regex("(?i)\\b(don|can|won|isn|aren|didn|doesn|couldn|wouldn|shouldn|hadn|hasn|haven|wasn|weren|mustn|needn) (t)\\b")
    private val pronSpace = Regex("(?i)\\b(i|you|he|she|we|they|it|that|there|here|who|what|where|how|let) (s|m|d|ll|re|ve)\\b")
    private val pairs = mapOf(
        "i" to setOf("m", "ll", "ve", "d"), "you" to setOf("re", "ll", "ve", "d"), "he" to setOf("s", "ll", "d"), "she" to setOf("s", "ll", "d"), "we" to setOf("re", "ll", "ve", "d"),
        "they" to setOf("re", "ll", "ve", "d"), "it" to setOf("s", "ll"), "that" to setOf("s", "ll"), "there" to setOf("s", "ll"), "here" to setOf("s"), "who" to setOf("s", "ll", "d"),
        "what" to setOf("s", "ll"), "where" to setOf("s"), "how" to setOf("s", "ll"), "let" to setOf("s"),
    )
    private val possessiveSpace = Regex("\\b([A-Za-z]{3,}) ([Ss])\\b(?=\\s+[A-Za-z])")
    /** « I M THEDEMON » / « I LLREAD » : la lettre de la contraction est collée au mot suivant. */
    private val iGlue = Regex("(?<![A-Za-z'’])([Ii]) (LL|Ll|ll|VE|Ve|ve|M|m|D|d)([A-Za-z]{3,})(?![A-Za-z'’])")
    /** Mots lus sans leur apostrophe, qui ne sont pas des mots anglais (ou presque jamais) : « YOURE », « DONT », « THATS »… */
    private val glued = mapOf(
        "youre" to "you're", "theyre" to "they're", "ive" to "i've", "youve" to "you've", "youll" to "you'll", "theyve" to "they've", "theyll" to "they'll", "weve" to "we've",
        "thats" to "that's", "whats" to "what's", "theres" to "there's", "heres" to "here's", "whos" to "who's", "cant" to "can't", "dont" to "don't", "wont" to "won't", "isnt" to "isn't",
        "arent" to "aren't", "didnt" to "didn't", "doesnt" to "doesn't", "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "hadnt" to "hadn't", "hasnt" to "hasn't",
        "havent" to "haven't", "wasnt" to "wasn't", "werent" to "weren't", "hes" to "he's", "shes" to "she's", "mustnt" to "mustn't", "neednt" to "needn't", "wouldve" to "would've",
        "couldve" to "could've", "shouldve" to "should've", "itll" to "it'll", "thatll" to "that'll",
    )
    private val gluedWord = Regex("(?<![A-Za-z'’])([A-Za-z]{2,10})(?![A-Za-z'’])")
    /** « IM », « ILL », « LETS » existent aussi comme noms ou mots : corrigés seulement devant ce qui les suit d'ordinaire. */
    private val imFollowers = setOf("A", "AN", "THE", "NOT", "NO", "IN", "ON", "AT", "SO", "TOO", "VERY", "JUST", "ALWAYS", "NEVER", "ALSO", "STILL", "ONLY", "REALLY", "TRULY", "SURE", "SORRY", "GLAD", "HAPPY", "AFRAID", "SCARED",
        "FINE", "OK", "OKAY", "HERE", "THERE", "NOW", "HOME", "BACK", "GOING", "GETTING", "TRYING", "LOOKING", "WILLING", "READY", "ABOUT", "DONE", "ALIVE", "FREE", "ALONE", "LEAVING", "COMING", "WAITING", "SERIOUS", "TALKING", "THINKING", "NOT")
    private val illFollowers = setOf("BE", "HAVE", "GO", "DO", "TELL", "SEE", "TAKE", "SHOW", "MAKE", "GET", "KILL", "ENTER", "CHANGE", "STAY", "LET", "GIVE", "KEEP", "HELP", "FIND", "TRY", "SURVIVE", "NEVER", "ALWAYS", "NOT", "JUST", "ALSO",
        "STILL", "ONLY", "DEFINITELY", "CERTAINLY", "SURELY", "PROBABLY", "BRING", "SEND", "HANDLE", "PROTECT", "FOLLOW", "LEAVE", "CALL", "ASK", "WAIT", "READ", "WRITE", "REMEMBER", "FORGET", "DIE", "FIGHT", "STOP", "START", "TRUST", "PAY",
        "PROMISE", "SWEAR", "MEET", "LOOK", "WATCH", "SAVE", "LEARN", "TEACH", "CHECK", "THINK", "SEE", "TALK", "WALK", "RUN")
    private val illBlockers = setOf("IS", "WAS", "ARE", "WERE", "BE", "BEEN", "FEEL", "FELT", "FEELS", "LOOK", "LOOKS", "LOOKED", "GET", "GETS", "GOT", "FALL", "FELL", "TOO", "VERY", "SO", "MAKE", "MADE", "MAKES", "TAKEN")
    private val letsFollowers = setOf("GO", "DO", "SEE", "TRY", "START", "HURRY", "GET", "TAKE", "MOVE", "CALL", "STOP", "MAKE", "TALK", "BEGIN", "FIGHT", "EAT", "LEAVE", "FIND", "PLAY", "WAIT", "END", "CONTINUE", "FINISH", "SETTLE", "DISCUSS",
        "KEEP", "JUST", "NOT", "BE", "ALL", "HAVE", "WATCH", "BREAK", "STAY", "CHECK", "CHANGE", "HEAD", "RUN", "SIT", "STAND", "PUT", "BRING", "SEND", "ASK", "KILL", "SAVE", "FORGET", "LEARN", "MEET", "BUY", "DRINK", "SWEAR")
    private val lostI = Regex("(?<![A-Za-z0-9])[TtlL1|](?=['’](?:ve|ll|m|d)(?![A-Za-z]))", RegexOption.IGNORE_CASE)
    private val lostIm = Regex("(?<![A-Za-z0-9'’])(?:rM|RM|lM|LM|1M|TM|tM)(?![A-Za-z0-9])(?=\\s+[A-Za-z]{2,})")

    private fun matchCase(model: String, w: String): String = when {
        model.length > 1 && model.all { it.isUpperCase() } -> w.uppercase()
        model.first().isUpperCase() -> w.replaceFirstChar { it.uppercase() }
        else -> w
    }

    private fun neighbours(src: String, r: IntRange): Pair<String, String> {
        val prev = src.substring(0, r.first).trimEnd().substringAfterLast(' ').trim(',', '.', '!', '?', '"', '…').uppercase()
        val next = src.substring(r.last + 1).trimStart().takeWhile { it.isLetter() || it == '\'' || it == '’' }.uppercase()
        return prev to next
    }

    private fun restoreApostrophes(s0: String, lex: EnglishLexicon): String {
        var s = ntSpace.replace(s0) { m -> m.groupValues[1] + "'" + m.groupValues[2] }
        s = pronSpace.replace(s) { m ->
            if (pairs[m.groupValues[1].lowercase()]?.contains(m.groupValues[2].lowercase()) == true) m.groupValues[1] + "'" + m.groupValues[2] else m.value
        }
        s = iGlue.replace(s) { m ->
            val suffix = m.groupValues[2]; val rest = m.groupValues[3]
            val full = suffix + rest
            val restKnownOrSplit = lex.known(rest) || OcrRepair.splittable(rest, lex)
            if (!lex.known(full) && restKnownOrSplit) m.groupValues[1] + "'" + suffix + " " + rest else m.value
        }
        val t = lostIm.replace(s) { m ->
            val before = s.substring(0, m.range.first).trimEnd()
            if (before.isEmpty() || before.last() in ".!?…") "I'M" else m.value
        }
        s = lostI.replace(t, "I")
        val src = s
        s = gluedWord.replace(src) { m ->
            val w = m.groupValues[1]; val low = w.lowercase()
            val fixed = glued[low]
            when {
                fixed != null -> matchCase(w, fixed)
                low == "im" && neighbours(src, m.range).second in imFollowers && neighbours(src, m.range).first !in setOf("JONG", "SEO", "KIM", "PARK", "LEE", "YOON") -> matchCase(w, "i'm")
                low == "ill" && neighbours(src, m.range).second in illFollowers && neighbours(src, m.range).first !in illBlockers -> matchCase(w, "i'll")
                low == "lets" && neighbours(src, m.range).second in letsFollowers -> matchCase(w, "let's")
                else -> m.value
            }
        }
        // possessif dont l'apostrophe est lue comme une espace (« PEOPLE S SUFFERING » → « PEOPLE'S SUFFERING ») : le mot qui précède est un nom connu qui ne finit pas par « s »
        s = possessiveSpace.replace(s) { m ->
            val w = m.groupValues[1]
            if (!w.endsWith("s", ignoreCase = true) && lex.known(w) && w.uppercase() !in setOf("THE", "AND", "BUT", "FOR", "YOU", "NOT", "ALL", "ANY", "ARE")) w + "'" + m.groupValues[2] else m.value
        }
        return s
    }

    // ---------- « ! » ou « | » lu à la place d'un « I » ----------

    /** « BECAUSE! COULDN'T STAND… » : un « ! » collé à une conjonction, devant un auxiliaire ou un verbe, est un « I » (le point d'exclamation d'une vraie phrase n'est pas suivi d'une suite de phrase). */
    private val bangAsI = Regex("(?i)\\b(because|if|when|that|while|until|since|though|whether)\\s?[!|](?=\\s*(?:am|was|will|would|could|couldn't|can|can't|don't|didn't|have|haven't|had|hadn't|should|shouldn't|might|must|won't|wouldn't|want|need|think|know|guess|hope|wish|love|hate|feel|saw|said|told|mean|swear|promise|never|always|also|still|only|really|already)\\b)")
}

/** Filtre des lignes lues qui ne sont pas du dialogue : filigranes, logos, débris de lecture sur le dessin. */
object JunkFilter {
    private val watermark = Regex("(?i)(asura|\\.com\\b|\\.net\\b|\\.org\\b|\\.io\\b|\\.co\\.[a-z]{2}\\b|\\.kr\\b|https?:|www\\.|discord|scanlat|scans\\b|read (it )?(at|on)\\b|join (us|our)|(^|\\s)@\\s?[\\w.]{3,})")
    private val interjection = Regex("(?i)^(?:[a-z]*([a-z])\\1{2,}[a-z]*|a+r*g+h+|u+g+h+|g+a+g+h*|h+m+p*h*|h+a+h+a*|k+y+a+|t+s+k+|a+h+|o+h+|u+h+|e+h+|h+u+h+|w+h+a+t+|n+o+|y+e+s+)$")
    /** Rires et cris faits d'une syllabe répétée (« HEHEHE », « KUKUKU », « PWAHAHA ») : jamais coupés en mots ni corrigés. */
    private val stretched = Regex("(?i)^(?:(?:h[aeiou]){2,}h?|(?:k[aeiou]){2,}k?|(?:m[uo]){2,}|p+w+a+(?:h+a+)+h*|(?:b[aeiou]){3,})$")

    fun isWatermark(text: String): Boolean = watermark.containsMatchIn(text)

    /** Cri, rire ou bruit étiré lu comme un seul mot (« AAAAARGH », « HEHEHE ») : un mot de ce genre n'est ni coupé ni corrigé par la réparation du texte. */
    fun isInterjectionToken(token: String): Boolean { val t = token.trim('\'', '’'); return interjection.matches(t) || stretched.matches(t) }

    /**
     * Un texte qui n'est qu'un mot inconnu de deux à quatre lettres (« IE », « TE », « SA », « OE », « FIE ») : un reste de décor ou de bruitage lu comme du texte. Le laisser de côté ne
     * perd rien (le dessin d'origine reste) et évite une fausse bulle. Jamais pour un cri, un rire ou un nom du chapitre.
     */
    fun isTinyDebris(text: String, lex: EnglishLexicon, names: Set<String> = emptySet()): Boolean {
        if (ScriptDetector.dominant(text) != "en") return false
        val core = text.trim().trim('.', '…', '!', '?', ',', '"', '\'', '’', '“', '”', '-', ' ')
        if (core.length !in 2..4 || !core.all { it.isLetter() }) return false
        val low = core.lowercase()
        if (low in names || isInterjectionToken(core)) return false
        // la liste complète des mots valides contient « ie », « sa », « te », « ee »… : pour un si court texte, seuls comptent les petits mots du dialogue et les mots vraiment courants
        return if (core.length == 2) low !in dialogueTwo else !(lex.isCommon(low) && lex.rank(low) < 7_000)
    }
    private val dialogueTwo = setOf("ah", "aw", "an", "am", "as", "at", "be", "by", "do", "eh", "go", "ha", "he", "hi", "hm", "ho", "if", "in", "is", "it", "me", "mm", "my", "no", "of", "oh", "ok", "on", "or", "ow", "so", "to", "ty", "uh", "um", "up", "us", "we", "yo")

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
