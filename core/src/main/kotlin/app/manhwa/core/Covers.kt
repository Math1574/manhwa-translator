package app.manhwa.core

import java.security.MessageDigest

/** Une image de la page de l'œuvre, telle que le navigateur la décrit (adresse, texte alternatif, classes, taille). */
data class CoverImg(val src: String, val alt: String = "", val cls: String = "", val w: Int = 0, val h: Int = 0)

/**
 * Ce que la page d'une œuvre annonce comme illustration, lu par le navigateur dans la page chargée (adresses déjà complètes).
 * [shared] : balises de partage (og:image, twitter:image, image_src), dans l'ordre de confiance ; [structured] : images des données structurées ; [images] : images de la page ;
 * [texts] : textes qui pourraient être le synopsis de l'œuvre (lus dans la même page, au même moment).
 */
data class CoverCandidates(val shared: List<String> = emptyList(), val structured: List<String> = emptyList(), val images: List<CoverImg> = emptyList(), val pageTitle: String = "", val texts: List<SynopsisText> = emptyList())

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

    /** Adresses qui disent « image du site » (partage générique, bandeau, en-tête) plutôt qu'« illustration de l'œuvre ». */
    private val generic = Regex("(?i)(?<![a-z])(og|opengraph|open[-_]?graph|social|share|sharing|banner|hero|header|site[-_]?image|default|fallback|splash)(?![a-z])")

    /** Mots qui peuvent suivre le titre dans une adresse d'image sans en faire une autre œuvre (format, taille, qualité). */
    private val tail = setOf(
        "jpg", "jpeg", "png", "webp", "avif", "cover", "covers", "poster", "thumb", "thumbnail", "large", "small", "medium", "optimized", "full", "original", "orig",
        "img", "image", "images", "mobile", "desktop", "min", "compressed", "opt", "new", "hd",
    )

    /** Le titre réduit à ses lettres et chiffres (sans accents ni majuscules) : « Solo Leveling: Ragnarok » et « solo-leveling-ragnarok » se valent. */
    fun compact(s: String): String = LibraryView.fold(s).replace(" ", "")

    /** Deux titres désignent la même œuvre : mêmes lettres et chiffres, à l'article initial près (« The Beginning After the End » / « Beginning After The End »). */
    fun sameTitle(a: String, b: String): Boolean {
        val x = compact(a); val y = compact(b)
        if (x.length < 3 || y.length < 3) return false
        return x == y || x.removePrefix("the") == y.removePrefix("the")
    }

    /**
     * L'adresse d'une image porte le titre de l'œuvre en entier, suivi seulement d'un code, d'une taille ou d'une extension : « …/covers/logging-out-is-too-hard.5c1851-400.webp » est celle de
     * « Logging Out Is Too Hard » ; « solo-leveling-ragnarok.jpg » n'est pas celle de « Solo Leveling » (d'autres mots suivent : c'est une autre œuvre).
     */
    fun urlNamesTitle(url: String, title: String): Boolean {
        val tw = LibraryView.words(title)
        if (tw.isEmpty() || compact(title).length < 4) return false
        val raw = url.substringAfter("://", url).substringBefore('?').substringBefore('#')
        val path = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Throwable) { raw }
        val tokens = LibraryView.words(path)
        val whole = compact(title)
        if (tokens.any { it == whole }) return true
        for (i in 0..tokens.size - tw.size) {
            if (tw.indices.all { tokens[i + it] == tw[it] }) {
                val next = tokens.getOrNull(i + tw.size)
                if (next == null || !(next.length >= 3 && next.all { it.isLetter() } && next !in tail)) return true
            }
        }
        return false
    }

    /** Une image candidate et sa note ; [evidenced] : son texte alternatif ou son adresse porte le titre de l'œuvre. */
    data class Ranked(val url: String, val score: Int, val evidenced: Boolean)

    /** En dessous de cette note, une image n'est pas proposée : mieux vaut une pastille qu'une image qui ne correspond pas. */
    const val MIN_SCORE = 30

    /**
     * Les images à essayer pour l'œuvre [title], la meilleure d'abord. Ce que la page annonce pour le partage (balises og:image…) est fait pour représenter la page, donc d'abord ; puis les données
     * structurées ; enfin les images de la page, qui ne comptent que si leur texte alternatif ou leur adresse porte le titre de l'œuvre (les vignettes des autres œuvres n'en font pas partie).
     * Une adresse qui parle de l'image du site (« og-image », « banner »…) perd des points ; une image qui porte le titre en gagne. Rien sous [MIN_SCORE].
     */
    fun rank(c: CoverCandidates, title: String): List<Ranked> {
        val tWords = LibraryView.words(title)
        val key = compact(title)
        val best = LinkedHashMap<String, Ranked>()
        fun add(url: String, base: Int, alt: String = "", cls: String = "", w: Int = 0, h: Int = 0, needsEvidence: Boolean = false) {
            if (!acceptable(url)) return
            var s = base
            var evidence = false
            if (key.length >= 3 && alt.isNotBlank()) {
                val aw = LibraryView.words(alt)
                if (compact(alt) == key) { s += 35; evidence = true }
                else if (tWords.isNotEmpty() && tWords.all { it in aw } && aw.size <= tWords.size + 3) { s += 25; evidence = true }
            }
            if (urlNamesTitle(url, title)) { s += 30; evidence = true }
            if (coverClass.containsMatchIn(cls)) s += 8
            if (coverUrl.containsMatchIn(url)) s += 8
            if (generic.containsMatchIn(url.substringAfter("://", url))) s -= 30
            if (w > 0 && h > 0) { if (h * 100 >= w * 115) s += 8 else if (w * 100 >= h * 130) s -= 15 }
            if ((w in 1..99) || (h in 1..99)) s -= 20 else if (w >= 200 && h >= 200) s += 4
            if (needsEvidence && !evidence) return          // une image de la page ne compte que si elle porte le titre de l'œuvre (une classe « cover » ne suffit pas : les autres œuvres en ont aussi)
            val old = best[url]
            if (old == null || s > old.score) best[url] = Ranked(url, s, evidence || old?.evidenced == true)
        }
        c.shared.forEachIndexed { i, u -> add(u, if (i == 0) 50 else 44) }
        c.structured.forEach { add(it, 40) }
        c.images.forEach { add(it.src, 10, it.alt, it.cls, it.w, it.h, needsEvidence = true) }
        return best.values.filter { it.score >= MIN_SCORE }.sortedByDescending { it.score }
    }

    /** La meilleure image pour l'œuvre [title], ou null si rien ne convient. */
    fun choose(c: CoverCandidates, title: String): String? = rank(c, title).firstOrNull()?.url

    /** Page d'attente d'un site protégé (« Just a moment… », « Un instant… ») : elle n'annonce rien de fiable et ne se résout pas sans une personne. */
    fun looksLikeChallenge(pageTitle: String): Boolean =
        Regex("(?i)(just a moment|un instant|attention required|checking your browser|verifying you are human|vérification|cloudflare|ddos|access denied|accès refusé)").containsMatchIn(pageTitle)

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

    /** Une illustration d'œuvre est en hauteur ou carrée : une image large n'est gardée que si son adresse ou son texte alternatif porte le titre (un bandeau de site est large). */
    fun fits(width: Int, height: Int, evidenced: Boolean): Boolean = plausible(width, height) && (evidenced || width * 100 <= height * 130)

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

    /** À chercher : jamais essayée, ou essayée sans succès depuis assez longtemps et pas trop souvent (d'abord vite : le site ou le réseau n'était peut-être pas prêt). */
    fun wanted(key: String, now: Long): Boolean {
        val r = byKey[key] ?: return true
        return !r.ok && r.tries < MAX_TRIES && now - r.lastTry >= retryAfter(r.tries)
    }

    /** Une œuvre qu'on avait renoncé à chercher, ou dont l'essai est trop récent, est de nouveau cherchée tout de suite (demande de l'utilisateur) ; son illustration trouvée, elle, reste. */
    fun again(key: String): CoverBook {
        val r = byKey[key] ?: return this
        return if (r.ok) this else CoverBook(records.filter { it.key != key }, banned)
    }

    /** Une œuvre se reconnaît à son titre (la clé est « site|titre ») : la même œuvre sur deux adresses du même site garde sa même image. */
    private fun titleOf(key: String) = key.substringAfter('|')

    /** Image trouvée pour [key] : gardée, sauf si c'est celle du site (voir plus haut). */
    fun saved(key: String, url: String, now: Long): Saved {
        if (url in banned) return Saved(failed(key, now), false, emptyList())
        val same = records.filter { it.ok && it.url == url && it.key != key && titleOf(it.key) != titleOf(key) && !CoverPick.sameTitle(titleOf(it.key), titleOf(key)) }.map { it.key }
        if (same.isEmpty()) return Saved(CoverBook(records.filter { it.key != key } + CoverRecord(key, url, true, 0, now), banned), true, emptyList())
        val gone = same.toSet() + key
        // (ces œuvres seront cherchées de nouveau un peu plus tard : l'adresse bannie est alors écartée et une autre image peut convenir)
        val marks = gone.map { CoverRecord(it, "", false, 1, now) }
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
        const val MAX_TRIES = 8

        /** Attente avant le prochain essai, selon le nombre d'essais ratés : 10 min, 30 min, 2 h, 6 h, puis un jour. */
        fun retryAfter(tries: Int): Long = when {
            tries <= 1 -> 10L * 60_000L
            tries == 2 -> 30L * 60_000L
            tries == 3 -> 2L * 3600_000L
            tries == 4 -> 6L * 3600_000L
            else -> 24L * 3600_000L
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
