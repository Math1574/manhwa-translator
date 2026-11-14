package app.manhwa.core

import java.security.MessageDigest

/** Une image de la page de l'œuvre, telle que le navigateur la décrit (adresse, texte alternatif, classes, taille). */
data class CoverImg(val src: String, val alt: String = "", val cls: String = "", val w: Int = 0, val h: Int = 0)

/**
 * Ce que la page d'une œuvre annonce comme illustration, lu par le navigateur dans la page chargée (adresses déjà complètes).
 * [shared] : balises de partage (og:image, twitter:image, image_src), dans l'ordre de confiance ; [structured] : images des données structurées ; [images] : images de la page.
 */
data class CoverCandidates(val shared: List<String> = emptyList(), val structured: List<String> = emptyList(), val images: List<CoverImg> = emptyList())

/** Choix de l'illustration d'une œuvre : où la chercher, quelle image retenir, à quelle condition on la garde. */
object CoverPick {
    /** Images qui ne sont pas l'illustration d'une œuvre : logo du site, icône, image par défaut, publicité. */
    private val notCover = Regex("(?i)(logo|favicon|sprite|placeholder|no[-_]?image|noimage|blank\\.|avatar|emoji|/icons?/|[-_/]icons?[-_./?]|default[-_]?(og|image|cover|thumb)|[-_/]ads?[-_/]|banner[-_]?ad)")
    private val badFormat = Regex("(?i)\\.(svg|ico|gif)(\\?|#|$)")
    private val coverClass = Regex("(?i)(cover|poster|summary_image|comic[-_]?image)")
    private val coverUrl = Regex("(?i)(cover|poster)")

    /** Une adresse d'image qui peut être une illustration (http ou https, ni logo, ni icône, ni format vectoriel ou animé). */
    fun acceptable(url: String): Boolean =
        url.length in 12..1500 && (url.startsWith("https://") || url.startsWith("http://")) && !badFormat.containsMatchIn(url) && !notCover.containsMatchIn(url)

    /**
     * L'image à retenir pour l'œuvre [title] : d'abord ce que la page annonce pour le partage (c'est fait pour représenter la page), puis les données structurées,
     * enfin, faute de mieux, une image de la page dont le texte alternatif porte le titre de l'œuvre (les vignettes des autres œuvres n'en font pas partie).
     * Null si rien ne convient : mieux vaut une pastille qu'une image qui ne correspond pas.
     */
    fun choose(c: CoverCandidates, title: String): String? {
        c.shared.firstOrNull { acceptable(it) }?.let { return it }
        c.structured.firstOrNull { acceptable(it) }?.let { return it }
        val words = LibraryView.words(title).filter { it.length >= 3 }
        if (words.isEmpty()) return null
        var best: String? = null
        var bestScore = 0
        for (im in c.images) {
            if (!acceptable(im.src)) continue
            val alt = LibraryView.fold(im.alt)
            if (words.count { alt.contains(it) } * 2 < words.size) continue
            var s = 3
            if (coverClass.containsMatchIn(im.cls)) s += 2
            if (coverUrl.containsMatchIn(im.src)) s += 1
            if (im.w >= 200 && im.h >= 200) s += 2 else if ((im.w in 1..99) || (im.h in 1..99)) s -= 2
            if (s > bestScore) { best = im.src; bestScore = s }
        }
        return if (bestScore >= 3) best else null
    }

    /**
     * Pages où chercher l'illustration d'une œuvre, d'après les adresses lues : d'abord les pages de l'œuvre elles-mêmes (celles qui ne sont pas des chapitres),
     * puis celles qu'on obtient en retirant « /chapter/12 » des adresses de chapitres. Au plus [max], sans doublon.
     */
    fun pagesFor(urls: List<String>, max: Int = 3): List<String> {
        val direct = urls.filter { !BrowsingLibrary.looksLikeChapter(it) && BrowsingLibrary.seriesKey(it) != null }.map { it.substringBefore('#') }
        val derived = urls.filter { BrowsingLibrary.looksLikeChapter(it) }.mapNotNull { BrowsingLibrary.seriesUrlOf(it) }
        return (direct + derived).distinctBy { it.trimEnd('/') }.take(max)
    }

    /** Une fois téléchargée : assez grande et pas démesurément large ou haute (un logo, un bandeau de publicité, un pixel). */
    fun plausible(width: Int, height: Int): Boolean = width >= 80 && height >= 80 && width.toFloat() / height in 0.2f..4.0f

    /** Nom de fichier stable d'une œuvre (la clé contient le site et des caractères qu'un nom de fichier n'aime pas). */
    fun fileKey(seriesKey: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(seriesKey.toByteArray(Charsets.UTF_8))
        return d.take(8).joinToString("") { "%02x".format(it) }
    }
}

/** Ce que l'on sait de l'illustration d'une œuvre : trouvée (avec l'adresse d'où elle vient) ou introuvable (nombre d'essais, dernier essai). */
data class CoverRecord(val key: String, val url: String, val ok: Boolean, val tries: Int, val lastTry: Long)

/**
 * Registre des illustrations, données immuables. Une œuvre sans illustration est réessayée quelques fois (le site était peut-être hors ligne), pas à l'infini.
 * Une même image annoncée pour deux œuvres différentes n'est pas une illustration mais l'image du site (logo, image par défaut) : elle n'est gardée pour aucune,
 * ni pour les suivantes (adresse « bannie »).
 */
class CoverBook(val records: List<CoverRecord> = emptyList(), val banned: Set<String> = emptySet()) {
    private val byKey = records.associateBy { it.key }

    /** Résultat d'un enregistrement : le nouveau registre, si l'image est gardée pour cette œuvre, et les autres œuvres dont l'image doit être effacée. */
    data class Saved(val book: CoverBook, val accepted: Boolean, val dropped: List<String>)

    fun has(key: String): Boolean = byKey[key]?.ok == true
    fun urlOf(key: String): String? = byKey[key]?.takeIf { it.ok }?.url

    /** À chercher : jamais essayée, ou essayée sans succès il y a longtemps et pas trop souvent. */
    fun wanted(key: String, now: Long): Boolean {
        val r = byKey[key] ?: return true
        return !r.ok && r.tries < MAX_TRIES && now - r.lastTry >= RETRY_MS
    }

    /** Une œuvre se reconnaît à son titre (la clé est « site|titre ») : la même œuvre sur deux adresses du même site garde sa même image. */
    private fun titleOf(key: String) = key.substringAfter('|')

    /** Image trouvée pour [key] : gardée, sauf si c'est celle du site (voir plus haut). */
    fun saved(key: String, url: String, now: Long): Saved {
        if (url in banned) return Saved(failed(key, now), false, emptyList())
        val same = records.filter { it.ok && it.url == url && it.key != key && titleOf(it.key) != titleOf(key) }.map { it.key }
        if (same.isEmpty()) return Saved(CoverBook(records.filter { it.key != key } + CoverRecord(key, url, true, 0, now), banned), true, emptyList())
        val gone = same.toSet() + key
        val marks = gone.map { CoverRecord(it, "", false, MAX_TRIES, now) }
        return Saved(CoverBook(records.filter { it.key !in gone } + marks, banned + url), false, same)
    }

    /** Essai sans résultat (page introuvable, aucune image qui convienne, image inutilisable). */
    fun failed(key: String, now: Long): CoverBook {
        val old = byKey[key]
        return CoverBook(records.filter { it.key != key } + CoverRecord(key, "", false, (old?.tries ?: 0) + 1, now), banned)
    }

    /** L'œuvre est oubliée : son enregistrement disparaît (elle sera cherchée de nouveau si on la relit). */
    fun forget(key: String) = CoverBook(records.filter { it.key != key }, banned)

    fun serialize(): String = buildString {
        for (r in records) append("C\t${esc(r.key)}\t${esc(r.url)}\t${if (r.ok) 1 else 0}\t${r.tries}\t${r.lastTry}\n")
        for (b in banned) append("B\t${esc(b)}\n")
    }

    companion object {
        const val MAX_TRIES = 5
        const val RETRY_MS = 12L * 3600_000L

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
        fun parse(data: String?): CoverBook {
            if (data.isNullOrBlank()) return CoverBook()
            val out = LinkedHashMap<String, CoverRecord>()
            val ban = LinkedHashSet<String>()
            for (line in data.lines()) {
                val p = line.split('\t')
                try {
                    when {
                        p.size >= 6 && p[0] == "C" -> { val r = CoverRecord(unesc(p[1]), unesc(p[2]), p[3] == "1", p[4].toInt(), p[5].toLong()); out[r.key] = r }
                        p.size >= 2 && p[0] == "B" && p[1].isNotEmpty() -> ban.add(unesc(p[1]))
                    }
                } catch (_: Exception) {}
            }
            return CoverBook(out.values.toList(), ban)
        }
    }
}
