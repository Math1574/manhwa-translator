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

    /** Fusion en chaîne : [base] corrigée successivement par chacune des [alts] (lectures du même texte). */
    fun fuseAll(base: String, alts: List<String>, lex: EnglishLexicon, names: Set<String> = emptySet()): Result {
        var cur = base; var total = 0
        for (a in alts) { val r = fuse(cur, a, lex, names); cur = r.text; total += r.replaced }
        return Result(cur, total)
    }
}
