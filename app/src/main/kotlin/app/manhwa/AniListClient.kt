package app.manhwa

import app.manhwa.core.AniList
import app.manhwa.core.AniListCharacter
import app.manhwa.core.AniListWork
import app.manhwa.core.CharactersPage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Accès à AniList (anilist.co), seulement quand l'utilisateur appuie sur « Chercher » ou choisit une œuvre dans la page de suggestions du glossaire. Seul le texte cherché
 * (puis le numéro de l'œuvre choisie) est envoyé ; aucune clé, aucun compte, rien d'autre. Appeler hors du fil principal.
 */
object AniListClient {
    /** Adresse du service ; les contrôles automatiques la remplacent par un faux serveur local. */
    @Volatile var endpoint: String = AniList.ENDPOINT

    /** Échec expliqué à l'utilisateur en une phrase. */
    class Failure(val reason: String) : IOException(reason)

    private const val MAX_BYTES = 2 * 1024 * 1024

    private fun post(body: String): Pair<Int, String> {
        val c = URL(endpoint).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "ManhwaTranslator")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val out = ByteArrayOutputStream()
            if (stream != null) stream.use { s ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw Failure("La réponse d'AniList est trop volumineuse.")
                }
            }
            return code to out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }

    /** Envoie [first] ; si AniList le refuse (erreur 400 : champ ou tri inconnu), retente avec [fallback]. Renvoie le texte de la réponse. */
    private fun call(first: String, fallback: String?): String {
        var (code, text) = post(first)
        if (code == 400 && fallback != null) { val r = post(fallback); code = r.first; text = r.second }
        return when {
            code in 200..299 -> text
            code == 429 -> throw Failure("AniList demande d'attendre un peu avant une nouvelle recherche. Réessaie dans une minute.")
            code >= 500 -> throw Failure("AniList est indisponible pour le moment (erreur $code). Réessaie plus tard.")
            else -> throw Failure("AniList a refusé la demande (erreur $code).")
        }
    }

    /** Les œuvres qui correspondent au titre cherché (six au plus). */
    fun search(text: String): List<AniListWork> = AniList.parseWorks(call(AniList.searchRequest(text, true), AniList.searchRequest(text, false)))

    /** Les personnages d'une œuvre : deux pages au plus (50 personnages, les principaux d'abord) ; [CharactersPage.hasNext] : il y en a d'autres. */
    fun characters(mediaId: Int): CharactersPage {
        val all = ArrayList<AniListCharacter>()
        var page = 1
        var more = false
        while (page <= 2) {
            val r = AniList.parseCharacters(call(AniList.charactersRequest(mediaId, page, true), AniList.charactersRequest(mediaId, page, false)))
            all.addAll(r.characters)
            more = r.hasNext
            if (!more) break
            page++
        }
        return CharactersPage(all, more)
    }

    /** Message à montrer pour une exception levée par [search] ou [characters]. */
    fun explain(e: Throwable): String = when (e) {
        is Failure -> e.reason
        is IOException -> "Impossible de joindre AniList : vérifie la connexion, puis réessaie."
        else -> "La réponse d'AniList n'a pas pu être lue."
    }
}
