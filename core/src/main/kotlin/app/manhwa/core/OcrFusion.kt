package app.manhwa.core

import kotlin.math.min

/**
 * Croisement de deux lectures d'une même ligne de texte (les deux lecteurs anglais PP-OCRv3 et PP-OCRv5, ou deux moteurs, ou une relecture).
 *
 * Avant : on gardait la lecture « la plus sûre d'elle » en entier, même quand l'autre avait raison sur un mot
 * (« CAMEBECAUSE » collé, « ATTRIBLITES » pour « ATTRIBUTES », « NO CHUICE » pour « NO CHOICE »).
 * Maintenant : la lecture de base reste la référence, et un MORCEAU n'est remplacé par celui de l'autre lecture que si ce dernier
 * est mieux écrit d'après le dictionnaire (moins de mots inconnus, de jetons parasites, de ponctuation collée).
 * Une égalité laisse la lecture de base : sans preuve par le dictionnaire, rien ne change.
 *
 * Les deux lectures sont alignées lettre à lettre (espaces ignorés) ; les morceaux sont les tronçons entre deux débuts de mot
 * que les deux lectures ont en commun. Un morceau n'est jamais remplacé par un texte beaucoup plus court (un mot qui disparaît
 * n'est pas un mot corrigé) ni beaucoup plus long.
 */
object OcrFusion {
    /** [text] : lecture retenue ; [replaced] : nombre de morceaux repris à l'autre lecture. */
    class Result(val text: String, val replaced: Int)

    private class Tok(val text: String, val start: Int)

    private val wordRe = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
    private val gluedPunct = Regex("[,;:!?][A-Za-z]")
    private const val MAX_LEN = 400
    private const val RARE = 10_000      // EnglishLexicon.rank : valide mais ni courant ni du genre

    private fun isKnown(word: String, lex: EnglishLexicon, names: Set<String>): Boolean {
        val w = word.lowercase()
        if (lex.known(w) || w in names) return true
        // « KAZAN'S » : possessif d'un nom du chapitre
        return (w.endsWith("'s") || w.endsWith("’s")) && w.dropLast(2) in names
    }

    /**
     * Défauts lexicaux d'un texte : mots inconnus du dictionnaire (hors noms du chapitre), jetons parasites (chiffres mêlés aux lettres comme « 2O »,
     * lettre seule autre que « A » et « I »), ponctuation collée à la lettre suivante (« NORMAL,ITIS », demi-point). Plus c'est bas, mieux c'est.
     */
    fun problems(text: String, lex: EnglishLexicon, names: Set<String> = emptySet(), rare: Double = 0.5): Double {
        var n = 0.0
        for (raw in text.split(' ', '\t')) {
            val t = raw.trim('.', ',', '!', '?', '…', '"', '\'', '’', '“', '”', '(', ')', ':', ';')
            if (t.isEmpty()) continue
            val letters = t.count { it.isLetter() }; val digits = t.count { it.isDigit() }
            if (letters >= 1 && digits >= 1 && t.length <= 8 && !OcrRepair.isLegitMixed(t)) { n += 1.0; continue }
            if (t.length == 1 && letters == 1 && t[0].uppercaseChar() != 'A' && t[0].uppercaseChar() != 'I') n += 1.0
        }
        for (m in wordRe.findAll(text)) {
            val w = m.value
            if (w.length < 2) continue
            // un mot inconnu vaut 1 ; un très long mot inconnu est presque sûrement plusieurs mots collés : il vaut davantage (jamais préféré à une lecture espacée)
            if (!isKnown(w, lex, names)) n += 1.0 + maxOf(0, w.length - 9) / 6.0
            // mot valide mais absent des listes de mots courants (« EATH » pour « DEATH ») : à égalité de tout le reste, la lecture qui n'en a pas est préférée
            else if (w.length >= 3 && '\'' !in w && '’' !in w && w.lowercase() !in names && lex.rank(w) == RARE) n += rare
        }
        n += 0.5 * gluedPunct.findAll(text).count()
        return n
    }

    /** Vrai si la lecture mérite une seconde chance : au moins un défaut lexical et assez de lettres pour que ce soit du texte (pas une icône, pas une adresse). */
    fun worthRetry(text: String, lex: EnglishLexicon, names: Set<String> = emptySet()): Boolean =
        text.count { it.isLetter() } >= 3 && '/' !in text && defects(text, lex, names) >= 1.0

    /** Comme [problems] mais sans le demi-point des mots rares : sert à décider d'une relecture (un mot rare mais valide n'en vaut pas la peine). */
    private fun defects(text: String, lex: EnglishLexicon, names: Set<String>): Double = problems(text, lex, names, rare = 0.0)

    private fun tokens(text: String): List<Tok> {
        val out = ArrayList<Tok>(); var pos = 0
        for (t in text.split(' ', '\t')) { if (t.isEmpty()) continue; out.add(Tok(t, pos)); pos += t.length }
        return out
    }

    /**
     * Points de coupure possibles : couples (i, j) « i lettres de [a] lues, j lettres de [b] lues » qui se trouvent sur AU MOINS UN alignement
     * de distance d'édition minimale (plusieurs alignements se valent souvent : « SHOULD E » / « SHOUD BE ») et où un mot commence dans les deux lectures
     * (ou bien la fin du texte). Suite strictement croissante en i et en j, de (0, 0) à (|a|, |b|).
     */
    private fun cutPoints(a: String, b: String, startsA: Set<Int>, startsB: Set<Int>): List<IntArray> {
        val n = a.length; val m = b.length
        val f = Array(n + 1) { IntArray(m + 1) }          // distance entre a[0 until i] et b[0 until j]
        for (i in 0..n) f[i][0] = i
        for (j in 0..m) f[0][j] = j
        for (i in 1..n) for (j in 1..m) f[i][j] = min(f[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1, min(f[i - 1][j] + 1, f[i][j - 1] + 1))
        val g = Array(n + 1) { IntArray(m + 1) }          // distance entre a[i until n] et b[j until m]
        for (i in 0..n) g[i][m] = n - i
        for (j in 0..m) g[n][j] = m - j
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) g[i][j] = min(g[i + 1][j + 1] + if (a[i] == b[j]) 0 else 1, min(g[i + 1][j] + 1, g[i][j + 1] + 1))
        val best = f[n][m]
        val cuts = ArrayList<IntArray>(); cuts.add(intArrayOf(0, 0))
        for (i in 1..n) for (j in 1..m) {
            if (f[i][j] + g[i][j] != best) continue
            if ((i != n && i !in startsA) || (j != m && j !in startsB)) continue
            val last = cuts.last()
            if (i > last[0] && j > last[1]) cuts.add(intArrayOf(i, j))
        }
        if (cuts.last()[0] != n || cuts.last()[1] != m) cuts.add(intArrayOf(n, m))
        return cuts
    }

    /** Longueur d'un morceau sans les espaces. */
    private fun compactLen(s: String) = s.count { it != ' ' && it != '\t' }

    /**
     * Lecture de [base] corrigée, morceau par morceau, par [alt] là où le dictionnaire donne raison à [alt].
     * Renvoie [base] telle quelle (même texte) quand rien ne change.
     */
    fun fuse(base: String, alt: String, lex: EnglishLexicon, names: Set<String> = emptySet()): Result {
        val tb = tokens(base); val ta = tokens(alt)
        if (tb.isEmpty() || ta.isEmpty()) return Result(base, 0)
        val cb = buildString { tb.forEach { append(it.text) } }.map { it.lowercaseChar() }.joinToString("")
        val ca = buildString { ta.forEach { append(it.text) } }.map { it.lowercaseChar() }.joinToString("")
        if (cb.length > MAX_LEN || ca.length > MAX_LEN) return Result(base, 0)
        val startsB = tb.map { it.start }.toHashSet(); val startsA = ta.map { it.start }.toHashSet()
        val cuts = cutPoints(cb, ca, startsB, startsA)
        fun chunk(toks: List<Tok>, lo: Int, hi: Int) = toks.filter { it.start in lo until hi }.joinToString(" ") { it.text }
        val parts = ArrayList<String>(); var replaced = 0
        for (k in 0 until cuts.size - 1) {
            val xb = chunk(tb, cuts[k][0], cuts[k + 1][0])
            val xa = chunk(ta, cuts[k][1], cuts[k + 1][1])
            var pick = xb
            if (xb.isNotEmpty() && xa.isNotEmpty() && !xb.equals(xa, ignoreCase = true)) {
                val lb = compactLen(xb); val la = compactLen(xa)
                // jamais un texte nettement plus court (un mot qui disparaît n'est pas corrigé) ni nettement plus long (« E » → « BE » reste permis)
                if (la * 4 >= lb * 3 && la * 2 <= lb * 3 + 4 && problems(xa, lex, names) < problems(xb, lex, names)) { pick = xa; replaced++ }
            }
            if (pick.isNotEmpty()) parts.add(pick)
        }
        return if (replaced == 0) Result(base, 0) else Result(parts.joinToString(" "), replaced)
    }

    // ---------- bord de ligne : ce que la boîte du détecteur a coupé ----------

    /** Ce qu'une relecture sur recadrage élargi ajoute à la lecture de base : [prefix] à poser devant le texte (lettre manquante, « I »), [suffix] = nouvelle ponctuation finale (remplace l'ancienne). */
    class Edges(val prefix: String, val suffix: String?)

    private const val PUNCT = ".,!?…"
    private fun trailingPunct(s: String) = s.takeLastWhile { it in PUNCT }
    /** Ponctuation finale plausible : une virgule seule, ou un à quatre signes parmi « . ! ? … » (jamais « : », « } », une lettre). */
    private fun okRun(run: String) = run == "," || (run.length in 1..4 && run.all { it in ".!?…" })

    /**
     * La boîte du détecteur coupe parfois une lettre étroite au bord de la ligne (un « I » seul à gauche : « I SAID, » lu « SAID. ») ou la virgule finale
     * (« CHURCH FINDS OUT, » lu « CHURCH FINDS OUT »). Une relecture sur recadrage élargi ([padded]) les retrouve, mais elle invente aussi des lettres sur le décor
     * (« THAT » → « STHAT, »). Seules deux additions sont donc prises, et seulement si le CORPS de la ligne est resté identique :
     *  - ponctuation finale : une virgule, ou des « . ! ? … » en plus ;
     *  - début : la ou les lettres qui complètent un mot inconnu en mot connu (« ALENT. » → « TALENT. »), ou un « I » seul devant un mot qui suit d'ordinaire « I » (« SAID », « WILL »…).
     * Renvoie null quand il n'y a rien à ajouter.
     */
    fun edgeAdditions(base: String, padded: String, lex: EnglishLexicon, names: Set<String> = emptySet()): Edges? {
        val b = base.trim(); val p = padded.trim()
        if (b.isEmpty() || p.isEmpty() || b.length > MAX_LEN || p.length > MAX_LEN + 8) return null
        val rb = trailingPunct(b); val rp = trailingPunct(p)
        val bBody = b.dropLast(rb.length).trimEnd(); val pBody = p.dropLast(rp.length).trimEnd()
        if (bBody.count { it.isLetter() } < 2) return null
        val at = pBody.lowercase().indexOf(bBody.lowercase())
        if (at < 0 || at + bBody.length != pBody.length) return null
        val suffix = when {
            rp.length > rb.length && rp.startsWith(rb) && okRun(rp) && (rb.isEmpty() || okRun(rb)) -> rp
            rb == "." && rp == "," -> rp          // le point est la confusion habituelle d'une virgule vue de trop près : la lecture élargie voit la queue entière
            else -> null
        }
        val pre = pBody.substring(0, at)
        var prefix = ""
        if (pre.isNotEmpty()) {
            val w0 = Regex("^[A-Za-z]+(?:['’][A-Za-z]+)?").find(bBody)?.value
            val letters = pre.trim()
            if (w0 != null && !pre.any { it.isWhitespace() } && letters.length in 1..2 && letters.all { it.isLetter() }
                && !isKnown(w0, lex, names) && isKnown(letters + w0, lex, names)) prefix = letters
            else if (w0 != null && letters.length == 1 && letters[0] in "Il1|" && pre.length <= 2 && w0.uppercase() in OcrFragments.iFollowers) prefix = "I "
        }
        return if (prefix.isEmpty() && suffix == null) null else Edges(prefix, suffix)
    }

    /** Réunit les additions proposées par deux lectures (l'autre lecteur, la relecture élargie) : le premier début trouvé ; pour la fin, la virgule si l'une des deux la voit (le point est sa confusion habituelle), sinon la plus longue. */
    fun mergeEdges(a: Edges?, b: Edges?): Edges? {
        if (a == null) return b
        if (b == null) return a
        val sa = a.suffix; val sb = b.suffix
        val suffix = when {
            sa == null -> sb
            sb == null -> sa
            sa == "," || sb == "," -> ","
            else -> if (sb.length > sa.length) sb else sa
        }
        return Edges(a.prefix.ifEmpty { b.prefix }, suffix)
    }

    /** Appliquée au texte final [t] (déjà croisé, relu) : le début n'est ajouté que si [t] ne l'a pas déjà, la fin que si la ponctuation finale de [t] est celle de la lecture de base. */
    fun applyEdges(t: String, base: String, e: Edges): String {
        var out = t.trim()
        if (e.prefix.isNotEmpty() && !out.startsWith(e.prefix.trim(), ignoreCase = true)) out = e.prefix + out
        if (e.suffix != null) {
            val rb = trailingPunct(base.trim()); val rt = trailingPunct(out)
            if (rt == rb) out = out.dropLast(rt.length) + e.suffix
        }
        return out
    }

    /** Vaut-il la peine de relire la ligne sur recadrage élargi ? Seulement si une lettre ou une virgule peut manquer : pas de ponctuation forte à la fin, premier mot inconnu ou qui suit « I ». */
    fun worthEdgeRead(text: String, lex: EnglishLexicon, names: Set<String> = emptySet()): Boolean {
        val t = text.trim()
        if (t.count { it.isLetter() } < 3 || '/' in t) return false
        val run = trailingPunct(t)
        if (run.isEmpty() || run == "." || run == "..") return true
        val w0 = Regex("^[^A-Za-z]*([A-Za-z]+(?:['’][A-Za-z]+)?)").find(t)?.groupValues?.get(1) ?: return false
        return w0.uppercase() in OcrFragments.iFollowers || !isKnown(w0, lex, names)
    }

    /** Fusion en chaîne : [base] corrigée successivement par chacune des [alts] (lectures du même texte). */
    fun fuseAll(base: String, alts: List<String>, lex: EnglishLexicon, names: Set<String> = emptySet()): Result {
        var cur = base; var total = 0
        for (a in alts) { val r = fuse(cur, a, lex, names); cur = r.text; total += r.replaced }
        return Result(cur, total)
    }
}
