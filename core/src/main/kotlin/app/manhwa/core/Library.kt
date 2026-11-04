package app.manhwa.core

/** Page visitée (historique de lecture). */
data class VisitedPage(val url: String, val title: String, val visitedAt: Long, val visits: Int = 1)

/** Page ou série mise en favori. */
data class Favorite(val url: String, val title: String, val addedAt: Long)

/** Une œuvre reconnue dans l'historique, avec la dernière page lue : sert au bouton « Reprendre la lecture ». */
data class SeriesEntry(
    val key: String, val title: String, val lastUrl: String, val lastTitle: String, val lastVisited: Long, val pages: Int, val chapter: String?,
    val favorite: Boolean = false,
    /** Chapitres distincts ouverts, et parmi eux ceux lus jusqu'au bout. */
    val chapters: Int = 0, val doneChapters: Int = 0,
    /** Avancement (0..100) de la dernière page ouverte. */
    val percent: Int = 0,
    val host: String = "",
)

/** Avancement de lecture d'une page (un chapitre) : part de la page parcourue, et lue jusqu'au bout ou non. */
data class ReadProgress(val url: String, val percent: Int, val done: Boolean, val updatedAt: Long)

/** Œuvre mise en favori (clé d'œuvre : site + titre). */
data class SeriesFavorite(val key: String, val title: String, val addedAt: Long)

/** Un chapitre d'une œuvre, tel que l'historique le connaît. */
data class ChapterEntry(val url: String, val label: String, val number: Double?, val lastVisited: Long, val visits: Int, val percent: Int, val done: Boolean)

/**
 * Bibliothèque de navigation : historique (récent en tête, dédoublonné par adresse) et favoris. Données immuables : chaque opération renvoie
 * une nouvelle bibliothèque. Stockée sur le téléphone seulement.
 */
data class BrowsingLibrary(
    val history: List<VisitedPage> = emptyList(), val favorites: List<Favorite> = emptyList(),
    val progress: List<ReadProgress> = emptyList(), val seriesFavorites: List<SeriesFavorite> = emptyList(),
) {

    fun visit(url: String, title: String, now: Long, maxHistory: Int = MAX_HISTORY): BrowsingLibrary {
        val u = normalize(url) ?: return this
        val existing = history.firstOrNull { it.url == u }
        val entry = VisitedPage(u, title.trim().ifEmpty { existing?.title ?: "" }, now, (existing?.visits ?: 0) + 1)
        val rest = history.filter { it.url != u }
        return copy(history = (listOf(entry) + rest).take(maxHistory))
    }

    fun isFavorite(url: String): Boolean = normalize(url)?.let { u -> favorites.any { it.url == u } } ?: false

    fun toggleFavorite(url: String, title: String, now: Long): BrowsingLibrary {
        val u = normalize(url) ?: return this
        return if (favorites.any { it.url == u }) copy(favorites = favorites.filter { it.url != u })
        else copy(favorites = listOf(Favorite(u, title.trim().ifEmpty { history.firstOrNull { it.url == u }?.title ?: u }, now)) + favorites)
    }

    fun removeHistory(url: String) = copy(history = history.filter { it.url != url })
    fun removeFavorite(url: String) = copy(favorites = favorites.filter { it.url != url })
    fun clearHistory() = copy(history = emptyList())

    // ---------- avancement de lecture ----------

    fun progressOf(url: String): ReadProgress? = normalize(url)?.let { u -> progress.firstOrNull { it.url == u } }

    /** Avancement d'une page. Une page parcourue à 94 % ou plus est « lue » ; une page lue le reste jusqu'à ce qu'on la marque « non lue ». */
    fun setProgress(url: String, percent: Int, now: Long): BrowsingLibrary {
        val u = normalize(url) ?: return this
        val old = progress.firstOrNull { it.url == u }
        val p = percent.coerceIn(0, 100)
        if (old != null && old.percent == p && (old.done || p < DONE_AT)) return this
        val done = (old?.done ?: false) || p >= DONE_AT
        return copy(progress = (listOf(ReadProgress(u, p, done, now)) + progress.filter { it.url != u }).take(MAX_PROGRESS))
    }

    /** Marque une page comme lue (100 %) ou non lue (0 %). */
    fun markDone(url: String, done: Boolean, now: Long): BrowsingLibrary {
        val u = normalize(url) ?: return this
        return copy(progress = (listOf(ReadProgress(u, if (done) 100 else 0, done, now)) + progress.filter { it.url != u }).take(MAX_PROGRESS))
    }

    // ---------- œuvres ----------

    fun isSeriesFavorite(key: String) = seriesFavorites.any { it.key == key }

    fun toggleSeriesFavorite(key: String, title: String, now: Long): BrowsingLibrary =
        if (isSeriesFavorite(key)) copy(seriesFavorites = seriesFavorites.filter { it.key != key })
        else copy(seriesFavorites = listOf(SeriesFavorite(key, title, now)) + seriesFavorites)

    /** Oublie une œuvre : ses pages d'historique, leur avancement et son favori. */
    fun removeSeries(key: String): BrowsingLibrary {
        val urls = history.filter { seriesKey(it.url) == key }.map { it.url }.toSet()
        return copy(history = history.filter { it.url !in urls }, progress = progress.filter { it.url !in urls }, seriesFavorites = seriesFavorites.filter { it.key != key })
    }

    /** Chapitres d'une œuvre vus dans l'historique : le plus récent d'abord par numéro ; une page sans numéro de chapitre vient après. */
    fun chapters(key: String): List<ChapterEntry> {
        val pages = history.filter { seriesKey(it.url) == key }
        val byChapter = LinkedHashMap<String, MutableList<VisitedPage>>()
        for (h in pages) byChapter.getOrPut(chapterLabel(h.url, h.title) ?: ("p|" + h.url)) { ArrayList() }.add(h)
        val out = byChapter.map { (label, hs) ->
            val last = hs.maxByOrNull { it.visitedAt }!!
            val prog = hs.mapNotNull { progressOf(it.url) }.maxByOrNull { it.updatedAt }
            val number = Regex("([0-9]+(?:[.][0-9]+)?)").find(label.removePrefix("p|"))?.value?.toDoubleOrNull()?.takeIf { !label.startsWith("p|") }
            ChapterEntry(last.url, if (label.startsWith("p|")) last.title.ifEmpty { "Page" } else label, number, last.visitedAt, hs.sumOf { it.visits }, prog?.percent ?: 0, prog?.done ?: false)
        }
        return out.sortedWith(compareByDescending<ChapterEntry> { it.number != null }.thenByDescending { it.number ?: 0.0 }.thenByDescending { it.lastVisited })
    }

    /** Œuvres lues, la plus récente d'abord. Seules les pages dont l'adresse permet de deviner le titre de l'œuvre comptent. */
    fun series(): List<SeriesEntry> =
        history.mapNotNull { h -> seriesKey(h.url)?.let { key -> key to h } }
            .groupBy({ it.first }, { it.second })
            .map { (key, pages) ->
                val last = pages.maxByOrNull { it.visitedAt }!!
                val chs = chapters(key)
                SeriesEntry(
                    key, SeriesHint.fromUrl(last.url) ?: key, last.url, last.title, last.visitedAt, pages.size, chapterLabel(last.url, last.title),
                    favorite = isSeriesFavorite(key), chapters = chs.size, doneChapters = chs.count { it.done }, percent = progressOf(last.url)?.percent ?: 0,
                    host = last.url.substringAfter("://", last.url).substringBefore('/').lowercase().removePrefix("www."),
                )
            }
            .sortedByDescending { it.lastVisited }

    fun serialize(): String = buildString {
        for (h in history) append("H\t${esc(h.url)}\t${esc(h.title)}\t${h.visitedAt}\t${h.visits}\n")
        for (f in favorites) append("F\t${esc(f.url)}\t${esc(f.title)}\t${f.addedAt}\n")
        for (p in progress) append("P\t${esc(p.url)}\t${p.percent}\t${if (p.done) 1 else 0}\t${p.updatedAt}\n")
        for (f in seriesFavorites) append("S\t${esc(f.key)}\t${esc(f.title)}\t${f.addedAt}\n")
    }

    companion object {
        const val MAX_HISTORY = 3000
        const val MAX_PROGRESS = 3000
        /** Part de la page parcourue à partir de laquelle un chapitre est considéré comme lu. */
        const val DONE_AT = 94

        private val chapterCut = Regex("(?i)^(.*?)/(?:chapter|chapitre|chap|ch|episode|épisode|ep)[-_/.]*0*[0-9]+.*$")

        /** Adresse de la page de l'œuvre déduite de celle d'un chapitre (on coupe avant « /chapter/8 », « /chapter-12 »…) ; null si on ne peut pas la deviner. */
        fun seriesUrlOf(url: String): String? {
            val u = url.substringBefore('#').substringBefore('?')
            val m = chapterCut.find(u) ?: return null
            val base = m.groupValues[1]
            return base.takeIf { it.substringAfter("://", "").contains('/') }
        }

        /** Adresse normalisée : seules les pages http(s) comptent ; le fragment (#…) est ignoré. */
        fun normalize(url: String): String? {
            val u = url.trim().substringBefore('#')
            return if (u.startsWith("http://") || u.startsWith("https://")) u else null
        }

        fun seriesKey(url: String): String? {
            val title = SeriesHint.fromUrl(url) ?: return null
            val host = url.substringAfter("://", url).substringBefore('/').lowercase().removePrefix("www.")
            return "$host|${title.lowercase()}"
        }

        private val chapterPath = Regex("(?i)(?<![a-z])(?:chapter|chapitre|chap|ch|episode|épisode|ep)[-_ /.]*0*[0-9]+")

        /** L'adresse est celle d'une page de chapitre (…/chapter/8, …/chapitre-12, …/ep3) : sert à ne traduire que les pages où l'on lit, pas les accueils ni les listes d'œuvres. */
        fun looksLikeChapter(url: String): Boolean {
            val path = url.substringBefore('#').substringBefore('?').substringAfter("://", url).substringAfter('/', "")
            return chapterPath.containsMatchIn(path)
        }

        private val chapterRe = Regex("(?i)(?:chapter|chapitre|chap|ch|episode|ep|épisode)[-_ /.]*0*([0-9]+(?:[.][0-9]+)?)")
        fun chapterLabel(url: String, title: String): String? {
            val m = chapterRe.find(url.substringAfter("://", url).substringAfter('/', "")) ?: chapterRe.find(title) ?: return null
            return "Chapitre " + m.groupValues[1]
        }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")
        private fun unesc(s: String): String {
            val sb = StringBuilder(); var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) { sb.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2 } else { sb.append(c); i++ }
            }
            return sb.toString()
        }

        /** Tolérant : une ligne illisible est ignorée. */
        fun parse(data: String?): BrowsingLibrary {
            if (data.isNullOrBlank()) return BrowsingLibrary()
            val hist = ArrayList<VisitedPage>(); val favs = ArrayList<Favorite>(); val prog = ArrayList<ReadProgress>(); val sfavs = ArrayList<SeriesFavorite>()
            for (line in data.lines()) {
                val p = line.split('\t')
                try {
                    when {
                        p.size >= 5 && p[0] == "H" -> hist.add(VisitedPage(unesc(p[1]), unesc(p[2]), p[3].toLong(), p[4].toInt()))
                        p.size >= 4 && p[0] == "F" -> favs.add(Favorite(unesc(p[1]), unesc(p[2]), p[3].toLong()))
                        p.size >= 5 && p[0] == "P" -> prog.add(ReadProgress(unesc(p[1]), p[2].toInt().coerceIn(0, 100), p[3] == "1", p[4].toLong()))
                        p.size >= 4 && p[0] == "S" -> sfavs.add(SeriesFavorite(unesc(p[1]), unesc(p[2]), p[3].toLong()))
                    }
                } catch (_: Exception) {}
            }
            return BrowsingLibrary(hist, favs, prog, sfavs)
        }
    }
}

object RelativeTime {
    /** « à l'instant », « il y a 5 min », « il y a 3 h », « il y a 2 j », sinon « il y a 3 sem. ». */
    fun fr(now: Long, then: Long): String {
        val s = ((now - then) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "à l'instant"
            s < 3600 -> "il y a ${s / 60} min"
            s < 86400 -> "il y a ${s / 3600} h"
            s < 14 * 86400 -> "il y a ${s / 86400} j"
            else -> "il y a ${s / (7 * 86400)} sem."
        }
    }
}
