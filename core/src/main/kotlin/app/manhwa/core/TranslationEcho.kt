package app.manhwa.core

/**
 * Le modèle de langage recopie parfois la ligne d'origine devant sa traduction : « SOURCE| TRADUCTION » (la ligne de la demande, reprise puis prolongée), ou la source sur une ligne et
 * la traduction sur la suivante. Le texte dessiné dans la bulle contenait alors les deux (« YOU'RE BEING A LITTLE EXCESSIVE.| TU ES UN PEU TROP SÉVÈRE. »).
 *
 * [strip] retire cette recopie quelle que soit sa mise en forme (séparateur « | », « => », saut de ligne, ou rien du tout) ; une traduction qui n'en contient pas n'est jamais modifiée.
 * La comparaison ignore la casse, les espaces et la ponctuation : la page en majuscules est envoyée au modèle en casse de phrase, et le texte gardé est en majuscules.
 */
object TranslationEcho {
    /** Séparateurs qu'un modèle met entre la source recopiée et la traduction. */
    private val separators = listOf("|", "=>", "->", "→", "⇒", "\n")
    /** Ce qui reste de la source (ponctuation finale, séparateur) au début de ce qui suit une recopie sans séparateur net. */
    private const val LEFTOVER = ".!?,;:…-–—|/\\=>"

    private fun norm(s: String): String { val sb = StringBuilder(s.length); for (c in s) if (c.isLetterOrDigit()) sb.append(c.lowercaseChar()); return sb.toString() }

    private fun letters(s: String): Int = s.count { it.isLetter() }

    /** Distance d'édition entre deux textes, abandonnée dès qu'elle dépasse [max] (rend alors max + 1). */
    private fun distance(a: String, b: String, max: Int): Int {
        if (Math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** [left] est-il la recopie d'une des sources (textes normalisés) ? Identique, ou presque pour un texte assez long (une lettre sur six, la lecture a pu être corrigée au passage). */
    private fun isEchoOf(left: String, sources: List<String>): Boolean {
        val a = norm(left)
        if (a.length < 2) return false
        for (s in sources) {
            if (a == s) return true
            val n = maxOf(a.length, s.length)
            if (n >= 8 && distance(a, s, n / 6) <= n / 6) return true
        }
        return false
    }

    /** Une recopie retirée du début de [t], ou null s'il n'y en a pas. */
    private fun once(t: String, sources: List<String>): String? {
        // 1) un séparateur après la source recopiée : tout ce qui précède l'un des séparateurs (le premier qui convient) est la recopie
        var tried = 0
        var i = 0
        while (i < t.length && tried < 10) {
            val sep = separators.firstOrNull { t.startsWith(it, i) }
            if (sep == null) { i++; continue }
            tried++
            val left = t.substring(0, i)
            val right = t.substring(i + sep.length).trim()
            if (letters(right) > 0 && isEchoOf(left, sources)) return right
            i += sep.length
        }
        // 2) rien qui sépare : la traduction commence par la source entière (six lettres ou plus), suivie d'autre chose
        val nt = norm(t)
        for (s in sources) {
            if (s.length < 6 || nt.length < s.length + 2 || !nt.startsWith(s)) continue
            var seen = 0; var cut = 0
            while (cut < t.length && seen < s.length) { if (t[cut].isLetterOrDigit()) seen++; cut++ }
            if (cut < t.length && t[cut].isLetterOrDigit()) continue      // la source s'arrête au milieu d'un mot : ce n'est pas elle (« NO » dans « NON »)
            val rest = t.substring(cut).trimStart { it.isWhitespace() || it in LEFTOVER }
            if (letters(rest) >= 2 && norm(rest) != s) return rest
        }
        return null
    }

    /**
     * La traduction [tr] sans l'original [src] recopié devant elle. [alt] : une autre forme de la source (texte relu avant la traduction, ou le texte lu par l'OCR) : le modèle a recopié ce qu'il a reçu.
     * Sans recopie, [tr] est rendue telle quelle ; un « | » seul au début ou à la fin est un résidu de mise en forme, il est retiré.
     */
    fun strip(src: String, tr: String, alt: String = ""): String {
        var t = tr.trim()
        if (t.isEmpty()) return tr
        val sources = listOf(src, alt).map { norm(it) }.filter { it.length >= 2 }.distinct()
        var changed = false
        if (sources.isNotEmpty()) {
            repeat(3) {
                val next = once(t, sources) ?: return@repeat
                t = next; changed = true
            }
        }
        val bare = t.trim('|', ' ', '\t')
        if (bare != t && letters(bare) > 0) { t = bare; changed = true }
        return if (changed) t else tr
    }

    /** La page dont les traductions n'ont plus d'original recopié ; la même instance si rien n'a changé (pages gardées en mémoire avant le filtre). */
    fun cleanPage(page: PageResult): PageResult {
        var changed = false
        val blocks = page.blocks.map { b ->
            val s = strip(b.source, b.translated, b.repaired)
            if (s == b.translated) b else { changed = true; b.copy(translated = s) }
        }
        return if (changed) page.copy(blocks = blocks) else page
    }
}
