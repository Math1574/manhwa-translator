package app.manhwa.core

/**
 * Chapitre suivant ou précédent déduit de l'adresse : le dernier numéro qui suit « chapter », « chapitre », « ch », « episode »… est incrémenté
 * (le remplissage par des zéros est conservé). Les chapitres décimaux (« chapter-12-5 ») et les adresses sans numéro de chapitre donnent null.
 */
object ChapterNav {
    private val re = Regex("(?i)((?:chapter|chapitre|chap|ch|episode|épisode|ep)[-_/.]*)(0*)(\\d+)(?![-.]?\\d)")

    fun next(url: String): String? = step(url, +1)
    fun previous(url: String): String? = step(url, -1)

    private fun step(url: String, delta: Int): String? {
        val noFragment = url.substringBefore('#')
        val q = noFragment.indexOf('?')
        val path = if (q >= 0) noFragment.substring(0, q) else noFragment
        val query = if (q >= 0) noFragment.substring(q) else ""
        val schemeEnd = path.indexOf("://"); if (schemeEnd < 0) return null
        val hostEnd = path.indexOf('/', schemeEnd + 3); if (hostEnd < 0) return null
        val rest = path.substring(hostEnd)
        val m = re.findAll(rest).lastOrNull() ?: return null
        val number = m.groupValues[3].toLongOrNull() ?: return null
        val target = number + delta
        if (target < 1) return null
        val width = m.groupValues[2].length + m.groupValues[3].length
        val digits = target.toString().padStart(width, '0')
        val numStart = m.groups[3]!!.range.first - m.groupValues[2].length
        val numEnd = m.groups[3]!!.range.last + 1
        return path.substring(0, hostEnd) + rest.substring(0, numStart) + digits + rest.substring(numEnd) + query
    }
}
