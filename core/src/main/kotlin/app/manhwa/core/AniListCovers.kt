package app.manhwa.core

/**
 * Illustration d'une œuvre d'après un annuaire public d'œuvres (AniList), quand la page de l'œuvre sur le site lu n'en donne pas (site protégé, page vide ou construite trop tard).
 * Seul le titre de l'œuvre est envoyé. L'image n'est retenue que si l'un des titres de l'annuaire est exactement celui de l'œuvre (au signe près) : mieux vaut une pastille qu'une image qui ne correspond pas.
 */
object AniListCovers {
    const val ENDPOINT = "https://graphql.anilist.co"

    private const val QUERY = "query(\$s:String){Page(page:1,perPage:10){media(search:\$s,type:MANGA,sort:SEARCH_MATCH){format countryOfOrigin popularity title{romaji english native}synonyms coverImage{extraLarge large}}}}"

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\""); c == '\\' -> sb.append("\\\\"); c < ' ' -> sb.append(String.format("\\u%04x", c.code)); else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    /** Corps de la demande (JSON) : la recherche du titre. */
    fun requestBody(title: String): String = "{\"query\":${quote(QUERY)},\"variables\":{\"s\":${quote(title)}}}"

    /** Une œuvre de la réponse : ses titres (tous), sa popularité et l'adresse de sa couverture. */
    private data class Hit(val names: List<String>, val popularity: Double, val cover: String)

    @Suppress("UNCHECKED_CAST")
    private fun hits(json: String, allowHttp: Boolean): List<Hit> {
        val root = try { MiniJson.parse(json) as? Map<String, Any?> } catch (_: Throwable) { null } ?: return emptyList()
        val media = (((root["data"] as? Map<String, Any?>)?.get("Page") as? Map<String, Any?>)?.get("media")) as? List<*> ?: return emptyList()
        val out = ArrayList<Hit>()
        for (m in media) {
            val o = m as? Map<String, Any?> ?: continue
            if (o["format"] == "NOVEL") continue                     // un roman n'est pas l'œuvre dessinée
            val t = o["title"] as? Map<String, Any?>
            val names = ArrayList<String>()
            for (k in listOf("english", "romaji", "native")) (t?.get(k) as? String)?.let { names.add(it) }
            (o["synonyms"] as? List<*>)?.forEach { (it as? String)?.let { n -> names.add(n) } }
            val ci = o["coverImage"] as? Map<String, Any?>
            val cover = (ci?.get("extraLarge") as? String) ?: (ci?.get("large") as? String) ?: continue
            if (!(cover.startsWith("https://") || (allowHttp && cover.startsWith("http://"))) || cover.endsWith("/default.jpg")) continue
            out.add(Hit(names, (o["popularity"] as? Number)?.toDouble() ?: 0.0, cover))
        }
        return out
    }

    /**
     * L'adresse de la couverture de l'œuvre [title] dans la réponse [json], ou null si aucun titre de l'annuaire n'est exactement celui-là. Plusieurs : la plus populaire.
     * Les adresses en https seulement ; [allowHttp] : aussi en http (faux annuaire des tests automatiques).
     */
    fun cover(json: String, title: String, allowHttp: Boolean = false): String? =
        hits(json, allowHttp).filter { h -> h.names.any { CoverPick.sameTitle(it, title) } }.maxByOrNull { it.popularity }?.cover
}
