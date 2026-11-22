package app.manhwa.core

import java.text.Normalizer

/**
 * Contrôle d'alignement d'une réponse du modèle : chaque traduction doit correspondre à SA ligne.
 *
 * Le modèle numérote ses réponses, mais il lui arrive (journal du 22/11, mode GPU) de sauter une ligne qu'il ne comprend pas (charabia lu par l'OCR) et de décaler les suivantes d'un cran,
 * ou de placer la traduction d'une ligne plus loin sous le numéro d'une autre ; la ligne oubliée, retraduite à part, donnait alors deux traductions du même texte, et la bulle d'à côté
 * affichait un texte qui n'était pas le sien (la note « Yuzu : … » dans la bulle noire, la bulle suivante dans la note).
 *
 * Indices retenus, chacun sûr à lui seul :
 * 1. deux traductions qui disent la même chose (mots pleins en commun) pour deux textes d'origine différents : l'une au moins est mal placée ;
 * 2. un nom propre d'une autre ligne du lot (mot inconnu du dictionnaire anglais, ou nom du chapitre) dans une traduction dont le texte d'origine ne le contient pas ;
 * 3. une traduction démesurée par rapport à son texte (plus de trois fois plus longue ou moins d'un quart, sur un texte assez long).
 * Une ligne suspecte n'est jamais affichée telle quelle : elle est retraduite (voir l'appelant).
 */
object TranslationAlign {
    /** Mots vides du français : ne disent rien du sens d'une réplique. */
    private val frStop = setOf(
        "les", "des", "une", "est", "pas", "que", "qui", "quoi", "pour", "dans", "avec", "mais", "sur", "par", "plus", "tout", "tous", "toute", "toutes", "cette", "ces", "ceux",
        "son", "sa", "ses", "mon", "mes", "ton", "tes", "notre", "nos", "votre", "vos", "leur", "leurs", "elle", "elles", "ils", "nous", "vous", "moi", "toi", "lui", "eux", "fait",
        "faire", "etre", "avoir", "suis", "es", "sont", "etait", "ete", "ai", "as", "avait", "avais", "aussi", "comme", "bien", "tres", "encore", "deja", "alors", "donc", "car", "ici",
        "est-ce", "c'est", "n'est", "qu'il", "qu'elle", "quand", "comment", "pourquoi", "rien", "sans", "sous", "chez", "vers", "entre", "meme", "autre", "autres", "peu", "trop",
        "non", "oui", "ainsi", "puis", "ca", "cela", "ceci", "celui", "celle", "dont", "ont", "sera", "serait", "peut", "faut", "veux", "vais", "vas", "va", "allez", "aller",
    )
    /** Mots vides de l'anglais. */
    private val enStop = setOf(
        "the", "and", "you", "your", "are", "for", "that", "this", "with", "was", "were", "not", "but", "have", "has", "had", "his", "her", "him", "she", "they", "them", "their",
        "what", "who", "how", "why", "when", "where", "there", "here", "will", "would", "can", "could", "should", "just", "all", "any", "out", "from", "about", "into", "its",
        "it's", "i'm", "don't", "didn't", "can't", "won't", "that's", "there's", "what's", "let's", "our", "ours", "yours", "one", "too", "very", "now", "then", "than", "been",
        "being", "did", "does", "doing", "get", "got", "yes", "no", "yeah", "well", "even", "still", "only", "also", "some", "such", "more", "most", "much", "many", "like",
    )

    private val nonWord = Regex("[^\\p{L}\\p{Nd}']+")

    /** Minuscules, sans accents. */
    internal fun fold(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(d.length)
        for (c in d) if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) sb.append(if (c == '’') '\'' else c)
        return sb.toString()
    }

    private fun words(s: String): List<String> = fold(s).split(nonWord).map { it.trim('\'') }.filter { it.isNotEmpty() }

    /** Mots pleins d'une traduction française (trois lettres ou plus, élisions retirées : « j'avais » → « avais »). */
    internal fun frContent(t: String): Set<String> =
        words(t).map { it.substringAfter('\'') }.filter { it.length >= 3 && it !in frStop }.toSet()

    private fun enContent(s: String): Set<String> = words(s).filter { it.length >= 3 && it !in enStop }.toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Float = if (a.isEmpty() && b.isEmpty()) 0f else a.intersect(b).size.toFloat() / a.union(b).size

    /** Noms propres d'un texte d'origine : mots inconnus du dictionnaire anglais (trois lettres ou plus, lettres seules), ou noms établis par le chapitre ; et les nombres. */
    internal fun anchors(src: String, lex: EnglishLexicon?, names: Set<String>): Set<String> {
        val out = HashSet<String>()
        for (w in words(src)) {
            val bare = w.substringBefore('\'')
            if (bare.all { it.isDigit() } && bare.isNotEmpty()) { out.add(bare); continue }
            if (bare.length < 3 || !bare.all { it.isLetter() } || bare in enStop) continue
            if (bare in names || (lex != null && !lex.known(bare))) out.add(bare)
        }
        return out
    }

    /** Racines communes anglais/français (cinq premières lettres des mots de six lettres ou plus : « absorbs » / « absorbe », « natural » / « naturel ») : indice faible, pour départager. */
    private fun stems(ws: Collection<String>): Set<String> = ws.filter { it.length >= 6 }.map { it.take(5) }.toSet()

    /** Deux graphies d'un même nom (une ou deux lettres de différence, selon la longueur) : une variante de lecture, pas un autre nom. */
    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        val max = if (minOf(a.length, b.length) >= 6) 2 else 1
        if (kotlin.math.abs(a.length - b.length) > max || minOf(a.length, b.length) < 3) return false
        var prev = IntArray(b.length + 1) { it }
        for (x in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = x
            for (y in 1..b.length) cur[y] = minOf(prev[y] + 1, cur[y - 1] + 1, prev[y - 1] + if (a[x - 1] == b[y - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length] <= max
    }

    private fun fit(src: String, tr: String, lex: EnglishLexicon?, names: Set<String>): Int {
        val trWords = words(tr).map { it.substringAfter('\'') }.toSet()
        val a = anchors(src, lex, names).count { it in trWords } * 3
        val s = stems(enContent(src)).intersect(stems(trWords)).size
        return a + s
    }

    /**
     * Lignes dont la traduction ne correspond vraisemblablement pas à leur texte d'origine. [translations] : null pour une ligne absente de la réponse (ignorée ici).
     * [names] : noms du chapitre, en minuscules.
     */
    fun suspects(sources: List<String>, translations: List<String?>, lex: EnglishLexicon?, names: Set<String> = emptySet()): Set<Int> {
        val n = minOf(sources.size, translations.size)
        val out = HashSet<Int>()
        val fr = (0 until n).map { translations[it]?.let { t -> frContent(t) } }
        val en = (0 until n).map { enContent(sources[it]) }
        // 1. deux traductions qui disent la même chose pour deux textes différents
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = fr[i] ?: continue; val b = fr[j] ?: continue
            if (minOf(a.size, b.size) < 4) continue
            if (jaccard(a, b) < 0.4f) continue
            if (jaccard(en[i], en[j]) > 0.3f) continue   // même texte (ou presque) : même traduction, normal
            val ti = translations[i]!!; val tj = translations[j]!!
            val iOwn = fit(sources[i], ti, lex, names); val iOther = fit(sources[j], ti, lex, names)
            val jOwn = fit(sources[j], tj, lex, names); val jOther = fit(sources[i], tj, lex, names)
            val iWrong = iOther > iOwn; val jWrong = jOther > jOwn
            if (iWrong) out.add(i)
            if (jWrong) out.add(j)
            if (!iWrong && !jWrong) { out.add(i); out.add(j) }   // impossible de départager : les deux sont refaites
        }
        // 2. un nom propre d'une autre ligne du lot, absent de son propre texte (une variante de lecture du même nom, « KANDEA » pour « KANDEYA », n'en est pas une preuve) ;
        //    quand ce nom vient de la ligne suivante, la réponse a sauté une ligne : les suivantes sont décalées d'un cran jusqu'à une ligne qui va clairement avec son texte
        val own = (0 until n).map { words(sources[it]).map { w -> w.substringBefore('\'') }.toSet() }
        val anch = (0 until n).map { anchors(sources[it], lex, names).filter { a -> !a.all { c -> c.isDigit() } }.toSet() }
        val shifted = HashSet<Int>()
        for (i in 0 until n) {
            val t = translations[i] ?: continue
            val tw = words(t).map { it.substringAfter('\'') }.toSet()
            for (j in 0 until n) {
                if (j == i) continue
                if (anch[j].any { it in tw && it !in own[i] && own[i].none { o -> close(o, it) } }) { out.add(i); if (j == i + 1) shifted.add(i); break }
            }
        }
        for (i in shifted) {
            for (k in i + 1 until n) {
                val t = translations[k] ?: continue
                val ownFit = fit(sources[k], t, lex, names)
                val nextFit = if (k + 1 < n) fit(sources[k + 1], t, lex, names) else -1
                if (ownFit > 0 && ownFit > nextFit) break
                out.add(k)
            }
        }
        // 3. traduction démesurée
        for (i in 0 until n) {
            val t = translations[i] ?: continue
            val sl = sources[i].count { it.isLetter() }; val tl = t.count { it.isLetter() }
            if (sl >= 25 && (tl > sl * 3 || tl * 4 < sl)) out.add(i)
        }
        return out
    }
}
