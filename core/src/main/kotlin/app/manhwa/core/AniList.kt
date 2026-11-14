package app.manhwa.core

/** Une œuvre trouvée par la recherche d'AniList. */
data class AniListWork(
    val id: Int,
    val title: String,
    /** Un autre titre de la même œuvre (romaji, anglais, d'origine), pour la reconnaître ; null s'il n'y en a pas d'autre. */
    val altTitle: String?,
    val year: Int?,
    /** « Manhwa », « Manhua », « Manga » ou « Roman ». */
    val kind: String,
    val adult: Boolean,
) {
    /** Ligne sous le titre dans la liste des résultats : « Manhwa · 2018 · 18+ ». */
    fun details(): String = listOfNotNull(kind, year?.toString(), if (adult) "18+" else null).joinToString(" · ")
}

/** Un personnage de la fiche d'une œuvre. [role] : MAIN (principal), SUPPORTING (secondaire) ou BACKGROUND (figurant). */
data class AniListCharacter(
    val role: String,
    val full: String,
    val first: String,
    val middle: String,
    val last: String,
    val native: String,
    val alternatives: List<String>,
)

/** Un nom proposé pour le glossaire ; [owner] : le personnage dont il vient. */
data class NameCandidate(val name: String, val owner: String, val role: String)

/**
 * Ce qu'on peut proposer pour un lot de personnages : les noms à cocher, ceux qu'on a écartés (mots anglais courants, noms trop courts : à ajouter à la main si besoin)
 * et le nombre de noms déjà au glossaire.
 */
data class NameProposals(val candidates: List<NameCandidate>, val skipped: List<String>, val already: Int)

/** Une page de la liste des personnages ; [hasNext] : il y en a d'autres après. */
data class CharactersPage(val characters: List<AniListCharacter>, val hasNext: Boolean)

/**
 * Suggestions de glossaire d'après AniList : la recherche de l'œuvre, la liste de ses personnages et les noms à proposer. Le cœur ne fait aucun accès réseau : il fabrique les requêtes
 * et lit les réponses (l'envoi se fait côté application, seulement quand l'utilisateur le demande) ; rien n'est ajouté au glossaire sans que l'utilisateur l'ait coché.
 */
object AniList {
    const val ENDPOINT = "https://graphql.anilist.co"
    /** AniList ne donne pas plus de 25 personnages par page. */
    const val PER_PAGE = 25
    /** Nombre d'œuvres proposées après une recherche. */
    const val WORKS = 6
    const val MAX_NAMES = 80

    /** Recherche d'une œuvre par son titre. [full] faux : version réduite (repli si AniList refuse un champ). */
    fun searchRequest(text: String, full: Boolean = true): String {
        val fields = if (full) "id title{romaji english native} format countryOfOrigin startDate{year} isAdult" else "id title{romaji english native}"
        return request("query(\$s:String,\$n:Int){Page(page:1,perPage:\$n){media(search:\$s,type:MANGA,sort:SEARCH_MATCH){$fields}}}", "\"s\":${quote(text.trim())},\"n\":$WORKS")
    }

    /** Une page des personnages d'une œuvre, principaux d'abord. [sorted] faux : sans tri (repli si AniList refuse le tri). */
    fun charactersRequest(mediaId: Int, page: Int, sorted: Boolean = true): String {
        val sort = if (sorted) ",sort:[ROLE,FAVOURITES_DESC]" else ""
        return request("query(\$id:Int,\$p:Int,\$n:Int){Media(id:\$id,type:MANGA){characters(page:\$p,perPage:\$n$sort){pageInfo{hasNextPage} edges{role node{name{first middle last full native alternative}}}}}}", "\"id\":$mediaId,\"p\":$page,\"n\":$PER_PAGE")
    }

    private fun request(query: String, variables: String) = "{\"query\":${quote(query)},\"variables\":{$variables}}"

    /** Texte JSON (entre guillemets) : guillemets, barres obliques inverses et caractères de contrôle échappés. */
    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    private fun Map<*, *>.text(key: String): String = (this[key] as? String)?.trim().orEmpty()

    /** Les œuvres d'une réponse de recherche (liste vide si la réponse n'en contient pas ou n'est pas lisible). */
    fun parseWorks(json: String): List<AniListWork> {
        val root = try { MiniJson.parse(json) as? Map<*, *> } catch (e: Exception) { null } ?: return emptyList()
        val media = ((root["data"] as? Map<*, *>)?.get("Page") as? Map<*, *>)?.get("media") as? List<*> ?: return emptyList()
        return media.mapNotNull { m ->
            val w = m as? Map<*, *> ?: return@mapNotNull null
            val id = (w["id"] as? Double)?.toInt() ?: return@mapNotNull null
            val t = w["title"] as? Map<*, *> ?: return@mapNotNull null
            val titles = listOf(t.text("english"), t.text("romaji"), t.text("native")).filter { it.isNotEmpty() }
            val title = titles.firstOrNull() ?: return@mapNotNull null
            val year = ((w["startDate"] as? Map<*, *>)?.get("year") as? Double)?.toInt()
            AniListWork(id, title, titles.firstOrNull { !it.equals(title, ignoreCase = true) }, year, kindOf(w.text("countryOfOrigin"), w.text("format")), w["isAdult"] == true)
        }
    }

    /** « Manhwa » pour la Corée, « Manhua » pour la Chine et Taïwan, « Manga » pour le Japon ; un roman reste un roman. */
    fun kindOf(country: String, format: String): String = when {
        format == "NOVEL" -> "Roman"
        country == "KR" -> "Manhwa"
        country == "CN" || country == "TW" -> "Manhua"
        else -> "Manga"
    }

    /** Les personnages d'une réponse (une page) ; ceux qui n'ont aucun nom lisible sont laissés de côté. */
    fun parseCharacters(json: String): CharactersPage {
        val root = try { MiniJson.parse(json) as? Map<*, *> } catch (e: Exception) { null } ?: return CharactersPage(emptyList(), false)
        val conn = ((root["data"] as? Map<*, *>)?.get("Media") as? Map<*, *>)?.get("characters") as? Map<*, *> ?: return CharactersPage(emptyList(), false)
        val hasNext = (conn["pageInfo"] as? Map<*, *>)?.get("hasNextPage") == true
        val edges = conn["edges"] as? List<*> ?: emptyList<Any?>()
        val list = edges.mapNotNull { e ->
            val edge = e as? Map<*, *> ?: return@mapNotNull null
            val name = (edge["node"] as? Map<*, *>)?.get("name") as? Map<*, *> ?: return@mapNotNull null
            val alt = (name["alternative"] as? List<*>)?.filterIsInstance<String>()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val c = AniListCharacter(edge.text("role").ifEmpty { "BACKGROUND" }, name.text("full"), name.text("first"), name.text("middle"), name.text("last"), name.text("native"), alt)
            if (c.full.isEmpty() && c.first.isEmpty() && c.last.isEmpty() && c.alternatives.isEmpty()) null else c
        }
        return CharactersPage(list, hasNext)
    }

    private fun roleRank(role: String) = when (role) { "MAIN" -> 0; "SUPPORTING" -> 1; else -> 2 }

    /** Les morceaux de nom d'un personnage, dans l'ordre : nom complet, prénom, milieu, nom de famille, puis les autres noms (les noms à spoiler ne sont jamais demandés). */
    private fun pieces(c: AniListCharacter): List<String> =
        (listOf(c.full, c.first, c.middle, c.last) + c.alternatives).flatMap { it.split(SPLIT) }.map { it.trim(*TRIM) }.filter { it.isNotEmpty() }

    private val SPLIT = Regex("[\\s,;/()\\[\\]{}\"“”‘]+")
    private val TRIM = charArrayOf('.', '\'', '’', '-', '!', '?', ':', '*', '~')

    /** Lettres latines (accents compris), tiret et apostrophe : un nom qu'un texte anglais pourrait contenir tel quel. */
    private fun latinName(w: String): Boolean = w.isNotEmpty() && w.all { (it.isLetter() && it.code < 0x250) || it == '-' || it == '\'' || it == '’' } && w.any { it.isLetter() }

    /**
     * Noms à proposer pour [chars] : chaque morceau de nom (« Jin-Woo », « Sung »), une seule fois, les personnages principaux d'abord. [known] : termes déjà au glossaire, en minuscules.
     * Sont écartés, parce que le remplacer par un jeton abîmerait des mots courants (« Eren » dans « weren't ») ou que ce n'est pas un nom : les mots anglais (« Light », « Sung »),
     * les fragments de mots courants et les noms de moins de 4 lettres. Sans [lex], seule la longueur est contrôlée.
     */
    fun proposals(chars: List<AniListCharacter>, known: Set<String>, lex: EnglishLexicon?, limit: Int = MAX_NAMES): NameProposals {
        val seen = HashSet<String>()
        val out = ArrayList<NameCandidate>()
        val skipped = LinkedHashSet<String>()
        var already = 0
        for (c in chars.sortedBy { roleRank(it.role) }) {
            val owner = c.full.ifEmpty { listOf(c.first, c.middle, c.last).filter { it.isNotEmpty() }.joinToString(" ") }.ifEmpty { c.alternatives.firstOrNull() ?: c.native }
            for (piece in pieces(c)) {
                if (!latinName(piece)) continue
                val k = piece.lowercase()
                if (!seen.add(k)) continue
                if (k in known) { already++; continue }
                val letters = piece.count { it.isLetter() }
                val safe = letters >= 4 && (lex == null || (!lex.known(piece) && !lex.insideCommonWord(piece)))
                if (!safe) { if (letters >= 3) skipped.add(piece); continue }
                if (out.size < limit) out.add(NameCandidate(piece, owner, c.role))
            }
        }
        return NameProposals(out, skipped.toList(), already)
    }
}
