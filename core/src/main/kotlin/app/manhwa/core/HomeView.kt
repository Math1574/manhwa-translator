package app.manhwa.core

/** Ce que « Reprendre » ouvre pour une œuvre : le chapitre là où la lecture s'était arrêtée, ou le chapitre suivant quand le dernier est lu jusqu'au bout. */
data class ResumePlan(
    val url: String,
    /** Avancement (en %) où se replacer dans la page ; 0 = depuis le début. */
    val resumeAt: Int,
    /** Vrai quand le dernier chapitre ouvert est lu : on propose le suivant. */
    val next: Boolean,
    /** Chapitre concerné (« Chapitre 12 ») quand l'adresse ou le titre le disent. */
    val chapter: String?,
)

/** Un site de l'accueil : sa clé (adresse sans « www. »), l'adresse de sa page d'accueil, son nom lisible, le nombre d'œuvres lues dessus et la dernière lecture. */
data class SiteTile(val key: String, val url: String, val name: String, val works: Int, val lastVisited: Long, val pinned: Boolean)

/**
 * Ce que l'accueil de l'onglet Lecture affiche, déduit de la bibliothèque : de quoi reprendre en un geste, les œuvres en cours et les sites lus.
 * Aucune recherche, aucun tri ni statut ici : c'est le rôle de la bibliothèque, l'accueil ne propose que des raccourcis.
 */
object HomeView {
    /** Avancement à partir duquel on se replace dans le chapitre (en dessous, on repart du début ; au-dessus, il est considéré comme lu). */
    const val RESUME_MIN = 4
    const val RESUME_MAX = 93
    const val MAX_SITES = 8
    const val MAX_ONGOING = 8
    private const val MAX_PINNED = 24
    private const val MAX_HIDDEN = 100

    fun resumeAt(percent: Int, done: Boolean): Int = if (!done && percent in RESUME_MIN..RESUME_MAX) percent else 0

    /** Œuvres en cours (la plus récemment lue d'abord) : celles que l'utilisateur n'a pas marquées « terminées ». */
    fun ongoing(series: List<SeriesEntry>): List<SeriesEntry> = series.filter { !it.finished }

    /** Page à ouvrir pour reprendre une œuvre : le dernier chapitre là où on s'était arrêté, ou le suivant quand il est lu jusqu'au bout et que son adresse se devine. */
    fun resumePlan(lib: BrowsingLibrary, e: SeriesEntry): ResumePlan {
        val done = lib.progressOf(e.lastUrl)?.done == true
        if (done) {
            val next = ChapterNav.next(e.lastUrl)
            if (next != null) return ResumePlan(next, 0, true, BrowsingLibrary.chapterLabel(next, ""))
        }
        return ResumePlan(e.lastUrl, resumeAt(e.percent, done), false, e.chapter)
    }

    /** Ligne d'avancement d'une œuvre : « Chapitre 3 · 45 % », ou « Suivant : Chapitre 4 » quand le dernier chapitre est lu. */
    fun caption(e: SeriesEntry, plan: ResumePlan): String =
        if (plan.next) "Suivant : " + (plan.chapter ?: "chapitre suivant")
        else listOfNotNull(plan.chapter ?: e.chapter, if (e.percent > 0) "${e.percent} %" else null).joinToString(" · ").ifEmpty { "Dernière page ouverte" }

    /** Une ou deux lettres pour la pastille d'une œuvre : initiales des deux premiers mots utiles (« The Beginning After… » → « BA »), deux premiers signes d'un titre d'un seul mot. */
    fun initials(title: String): String {
        val words = title.trim().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val skip = setOf("the", "a", "an", "le", "la", "les", "l", "un", "une", "de", "du", "des", "of", "and", "et")
        val sig = words.filter { it.lowercase() !in skip }.ifEmpty { words }
        if (sig.isEmpty()) return "?"
        fun head(s: String, n: Int): String { var end = 0; var k = 0; while (k < n && end < s.length) { end = s.offsetByCodePoints(end, 1); k++ }; return s.substring(0, end) }
        return if (sig.size == 1) head(sig[0], 2).replaceFirstChar { it.uppercase() } else (head(sig[0], 1) + head(sig[1], 1)).uppercase()
    }

    // ---------- sites ----------

    private fun authorityOf(url: String): String? {
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        return url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').takeIf { it.isNotEmpty() }
    }

    /** Clé d'un site : son adresse en minuscules sans « www. » ni identifiants, port compris (deux ports sont deux sites) ; null pour une adresse qui n'est pas http(s). */
    fun siteKey(url: String): String? = authorityOf(url)?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }

    /** Page d'accueil du site d'une adresse (« https://exemple.com/ ») ; le « www. » éventuel est conservé : certains sites ne répondent pas sans. */
    fun siteRoot(url: String): String? = authorityOf(url)?.let { url.substringBefore("://").lowercase() + "://" + it + "/" }

    /** Nom lisible d'un site : « asurascans.com » → « Asurascans » ; une adresse IP ou un nom d'un seul libellé reste tel quel (avec son port). */
    fun siteName(key: String): String {
        if (key.startsWith("[")) return key
        val host = key.substringBefore(':')
        val reg = Sites.registrable(host)
        val ip = host.isNotEmpty() && host.all { it.isDigit() || it == '.' }
        return if (ip) key else reg.substringBefore('.').replaceFirstChar { it.uppercase() }
    }

    /** Sites épinglés (adresses d'accueil séparées par des espaces) et sites retirés de l'accueil (clés séparées par des virgules), tels que les réglages les gardent. */
    fun pinned(s: AppSettings): List<String> = s.homeSites.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
    fun hidden(s: AppSettings): Set<String> = s.homeHidden.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** Épingle le site d'une adresse (en tête) ; s'il avait été retiré de l'accueil, il ne l'est plus. */
    fun withPinned(s: AppSettings, url: String): AppSettings {
        val key = siteKey(url) ?: return s
        val root = siteRoot(url) ?: return s
        val rest = pinned(s).filter { siteKey(it) != key }
        return s.copy(homeSites = (listOf(root) + rest).take(MAX_PINNED).joinToString(" "), homeHidden = (hidden(s) - key).joinToString(","))
    }

    /** Retire un site de l'accueil (il n'y revient pas tout seul) ; l'historique et la bibliothèque ne changent pas. */
    fun withHidden(s: AppSettings, key: String): AppSettings {
        val rest = pinned(s).filter { siteKey(it) != key }
        return s.copy(homeSites = rest.joinToString(" "), homeHidden = (hidden(s) + key).toList().takeLast(MAX_HIDDEN).joinToString(","))
    }

    /** Remet à l'accueil tous les sites retirés. */
    fun withoutHidden(s: AppSettings): AppSettings = s.copy(homeHidden = "")

    /**
     * Sites à afficher : ceux où l'on a lu une œuvre, ceux d'une page favorite et ceux que l'utilisateur a épinglés, hors sites retirés.
     * Les épinglés d'abord, puis les plus récemment lus. [max] sites au plus ; [series] évite de recalculer les œuvres quand l'appelant les a déjà.
     */
    fun sites(lib: BrowsingLibrary, pinned: Collection<String>, hidden: Set<String>, max: Int = MAX_SITES, series: List<SeriesEntry> = lib.series()): List<SiteTile> {
        class Acc(var url: String, var works: Int = 0, var last: Long = 0L, var pinned: Boolean = false)
        val by = LinkedHashMap<String, Acc>()
        for (e in series) {
            val key = siteKey(e.lastUrl) ?: continue
            val root = siteRoot(e.lastUrl) ?: continue
            val a = by.getOrPut(key) { Acc(root) }
            a.works++
            if (e.lastVisited > a.last) { a.last = e.lastVisited; a.url = root }
        }
        for (f in lib.favorites) {
            val key = siteKey(f.url) ?: continue
            val root = siteRoot(f.url) ?: continue
            val seen = lib.history.firstOrNull { it.url == f.url }?.visitedAt ?: f.addedAt
            val a = by.getOrPut(key) { Acc(root) }
            if (seen > a.last) a.last = seen
        }
        for (p in pinned) {
            val key = siteKey(p) ?: continue
            val root = siteRoot(p) ?: continue
            val a = by.getOrPut(key) { Acc(root) }
            a.pinned = true; a.url = root
        }
        return by.entries
            .filter { (k, a) -> a.pinned || k !in hidden }
            .map { (k, a) -> SiteTile(k, a.url, siteName(k), a.works, a.last, a.pinned) }
            .sortedWith(compareByDescending<SiteTile> { it.pinned }.thenByDescending { it.lastVisited }.thenBy { it.name })
            .take(max)
    }
}
