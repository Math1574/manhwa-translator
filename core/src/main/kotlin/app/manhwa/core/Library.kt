package app.manhwa.core

/** Page visitée (historique de lecture). */
data class VisitedPage(val url: String, val title: String, val visitedAt: Long, val visits: Int = 1)

/** Page ou série mise en favori. */
data class Favorite(val url: String, val title: String, val addedAt: Long)

/** Une œuvre reconnue dans l'historique, avec la dernière page lue : sert au bouton « Reprendre la lecture ». */
data class SeriesEntry(val key: String, val title: String, val lastUrl: String, val lastTitle: String, val lastVisited: Long, val pages: Int, val chapter: String?)

/**
 * Bibliothèque de navigation : historique (récent en tête, dédoublonné par adresse) et favoris. Données immuables : chaque opération renvoie
 * une nouvelle bibliothèque. Stockée sur le téléphone seulement.
 */
data class BrowsingLibrary(val history: List<VisitedPage> = emptyList(), val favorites: List<Favorite> = emptyList()) {

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

    /** Œuvres lues, la plus récente d'abord. Seules les pages dont l'adresse permet de deviner le titre de l'œuvre comptent. */
    fun series(): List<SeriesEntry> =
        history.mapNotNull { h -> seriesKey(h.url)?.let { key -> key to h } }
            .groupBy({ it.first }, { it.second })
            .map { (key, pages) ->
                val last = pages.maxByOrNull { it.visitedAt }!!
                SeriesEntry(key, SeriesHint.fromUrl(last.url) ?: key, last.url, last.title, last.visitedAt, pages.size, chapterLabel(last.url, last.title))
            }
            .sortedByDescending { it.lastVisited }

    fun serialize(): String = buildString {
        for (h in history) append("H\t${esc(h.url)}\t${esc(h.title)}\t${h.visitedAt}\t${h.visits}\n")
        for (f in favorites) append("F\t${esc(f.url)}\t${esc(f.title)}\t${f.addedAt}\n")
    }

    companion object {
        const val MAX_HISTORY = 300

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
            val hist = ArrayList<VisitedPage>(); val favs = ArrayList<Favorite>()
            for (line in data.lines()) {
                val p = line.split('\t')
                try {
                    when {
                        p.size >= 5 && p[0] == "H" -> hist.add(VisitedPage(unesc(p[1]), unesc(p[2]), p[3].toLong(), p[4].toInt()))
                        p.size >= 4 && p[0] == "F" -> favs.add(Favorite(unesc(p[1]), unesc(p[2]), p[3].toLong()))
                    }
                } catch (_: Exception) {}
            }
            return BrowsingLibrary(hist, favs)
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
