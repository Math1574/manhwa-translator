package app.manhwa.core

import java.text.Normalizer

/** Façon de ranger la liste des œuvres de la bibliothèque. */
enum class SeriesSort(val id: String, val label: String) {
    RECENT("recent", "Lues récemment"),
    TITLE("title", "Titre (A → Z)"),
    READ("read", "Chapitres lus");

    companion object {
        /** Le tri enregistré sous cet identifiant ; « lues récemment » si on ne le connaît pas. */
        fun of(id: String?): SeriesSort = values().firstOrNull { it.id == id } ?: RECENT
    }
}

/** Ce que la liste des œuvres montre : tout, ce qui est en cours, ce qui est terminé, les favoris. */
enum class SeriesFilter(val label: String) { ALL("Toutes"), ONGOING("En cours"), FINISHED("Terminées"), FAVORITES("Favoris") }

/**
 * Recherche, filtres et tris de la bibliothèque, sans rien d'Android : l'écran n'a qu'à afficher ce qui est rendu ici.
 * Les comparaisons ignorent les majuscules, les accents et la ponctuation (« oeuvre » trouve « Œuvre », « eleve » trouve « Élève »).
 */
object LibraryView {
    /** Minuscules, sans accents ni ponctuation, mots séparés par une seule espace. */
    fun fold(s: String): String {
        val n = Normalizer.normalize(s.lowercase().replace("œ", "oe").replace("æ", "ae").replace("ß", "ss"), Normalizer.Form.NFD)
        val sb = StringBuilder(n.length)
        var gap = true
        for (ch in n) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            if (ch.isLetterOrDigit()) { sb.append(ch); gap = false } else if (!gap) { sb.append(' '); gap = true }
        }
        return sb.toString().trimEnd()
    }

    /** Les mots d'une recherche, pliés comme [fold]. */
    fun words(query: String): List<String> = fold(query).split(' ').filter { it.isNotEmpty() }

    /** Vrai si chaque mot de [query] se retrouve dans l'un des [fields] ; une recherche vide convient à tout. */
    fun matches(query: String, vararg fields: String): Boolean {
        val w = words(query)
        if (w.isEmpty()) return true
        val hay = fold(fields.joinToString(" "))
        return w.all { hay.contains(it) }
    }

    private fun keeps(e: SeriesEntry, filter: SeriesFilter) = when (filter) {
        SeriesFilter.ALL -> true
        SeriesFilter.ONGOING -> !e.finished
        SeriesFilter.FINISHED -> e.finished
        SeriesFilter.FAVORITES -> e.favorite
    }

    /** La liste à afficher : œuvres qui correspondent à la recherche et au filtre, rangées selon le tri. */
    fun series(all: List<SeriesEntry>, query: String, filter: SeriesFilter, sort: SeriesSort): List<SeriesEntry> {
        val kept = all.filter { keeps(it, filter) && matches(query, it.title, it.host) }
        return when (sort) {
            SeriesSort.RECENT -> kept.sortedByDescending { it.lastVisited }
            SeriesSort.TITLE -> kept.sortedWith(compareBy<SeriesEntry> { fold(it.title) }.thenByDescending { it.lastVisited })
            SeriesSort.READ -> kept.sortedWith(compareByDescending<SeriesEntry> { it.doneChapters }.thenByDescending { it.chapters }.thenByDescending { it.lastVisited })
        }
    }

    /** Combien d'œuvres chaque filtre montrerait, pour la recherche en cours (les pastilles de filtre l'affichent). */
    fun counts(all: List<SeriesEntry>, query: String): Map<SeriesFilter, Int> {
        val found = all.filter { matches(query, it.title, it.host) }
        return SeriesFilter.values().associateWith { f -> found.count { keeps(it, f) } }
    }

    /** Numéro de chapitre écrit comme on le lit : 12.0 → « 12 », 12.5 → « 12.5 ». */
    fun numberText(n: Double): String = if (n == Math.floor(n) && n < 1.0e9) n.toLong().toString() else n.toString()

    private val numberQuery = Regex("^[0-9]+(?:[.,][0-9]+)?$")

    /**
     * Chapitres d'une œuvre qui correspondent à la recherche. Un nombre cherche le chapitre qui commence par ce numéro (« 12 » trouve 12, 12.5 et 120, pas 112) ;
     * des mots cherchent dans le libellé.
     */
    fun chapters(all: List<ChapterEntry>, query: String): List<ChapterEntry> {
        val q = query.trim()
        if (q.isEmpty()) return all
        if (numberQuery.matches(q)) {
            val num = q.replace(',', '.')
            return all.filter { ch -> ch.number?.let { numberText(it).startsWith(num) } ?: fold(ch.label).contains(fold(q)) }
        }
        val w = words(q)
        if (w.isEmpty()) return all
        return all.filter { ch -> val label = fold(ch.label); w.all { label.contains(it) } }
    }
}

/**
 * Titres simples : l'œuvre et le chapitre, jamais le titre brut de la page (« Chapter 8 - Asura Scans | Read Free Online »).
 * L'œuvre se lit dans l'adresse (comme pour la bibliothèque) ; sans elle, on nettoie le titre de la page : le nom du site et les mentions d'usage en sont retirés.
 */
object PageTitles {
    /** [main] : le titre ; [detail] : le chapitre (ou « Page de l'œuvre »), s'il y a lieu. */
    data class Simple(val main: String, val detail: String?)

    private val separators = Regex("\\s+[-–—]\\s+|\\s*[|•·»«]\\s*")

    /** Mentions qui n'apprennent rien sur la page, comparées une fois pliées (voir [LibraryView.fold]). */
    private val noise = setOf(
        "read", "read online", "read free", "read online free", "read for free", "online", "free", "manga", "manhwa", "manhua", "webtoon", "manga online", "manhwa online",
        "scans", "scan", "home", "accueil", "lire", "lire en ligne", "gratuit", "en ligne", "lecture en ligne", "page d accueil",
    )

    /** Nom du site pour comparer (« asurascans » pour www.asurascans.com). */
    private fun siteLabel(host: String): String =
        LibraryView.fold(Sites.registrable(host.removePrefix("www.")).substringBefore('.')).replace(" ", "")

    /** Titre de page débarrassé du nom du site et des mentions d'usage ; vide s'il ne reste rien. Deux morceaux au plus, séparés par « — ». */
    fun clean(raw: String, host: String): String {
        val site = siteLabel(host)
        val parts = raw.trim().split(separators).map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val kept = parts.filter { p ->
            val f = LibraryView.fold(p)
            val compact = f.replace(" ", "")
            f.isNotEmpty() && f !in noise && compact != site && !(site.length >= 5 && compact.contains(site))
        }
        val s = kept.take(2).joinToString(" — ")
        return if (s.length > 80) s.take(79).trimEnd() + "…" else s
    }

    fun simple(url: String, rawTitle: String): Simple {
        val host = Sites.hostOf(url) ?: ""
        val series = SeriesHint.fromUrl(url)
        val chapter = BrowsingLibrary.chapterLabel(url, rawTitle)
        if (series != null) return Simple(series, chapter ?: "Page de l'œuvre")
        val cleaned = clean(rawTitle, host)
        val main = cleaned.ifEmpty { host.removePrefix("www.").ifEmpty { url } }
        // le chapitre n'est répété que s'il n'est pas déjà dans le titre nettoyé (« Solo Leveling Chapter 3 »)
        val number = chapter?.substringAfter(' ')
        val detail = if (chapter != null && number != null && Regex("(?<![0-9.])" + Regex.escape(number) + "(?![0-9])").containsMatchIn(cleaned)) null else chapter
        return Simple(main, detail)
    }

    /** Le site d'une adresse pour un sous-titre (« asurascans.com »). */
    fun site(url: String): String = Sites.hostOf(url)?.removePrefix("www.") ?: url.substringAfter("://", url).substringBefore('/')
}
