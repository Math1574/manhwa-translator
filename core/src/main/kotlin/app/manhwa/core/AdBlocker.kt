package app.manhwa.core

/**
 * Blocage des publicités par nom de domaine : une requête est bloquée si son domaine, ou l'un de ses domaines parents, figure dans la liste.
 * Jamais de blocage des requêtes du site visité lui-même (même domaine de second niveau).
 */
class AdBlocker(domains: Iterable<String>) {
    private val set = HashSet<String>().also { s -> for (d in domains) { val t = d.trim().lowercase(); if (t.isNotEmpty()) s.add(t) } }
    val size: Int get() = set.size

    fun isBlocked(host: String?, pageHost: String? = null): Boolean {
        if (host.isNullOrEmpty() || set.isEmpty()) return false
        val h = host.lowercase().trimEnd('.')
        if (pageHost != null && sameSite(h, pageHost.lowercase())) return false
        var d = h
        while (true) {
            if (d in set) return true
            val i = d.indexOf('.')
            if (i < 0 || d.indexOf('.', i + 1) < 0) return false   // on s'arrête avant le suffixe seul (« com »)
            d = d.substring(i + 1)
        }
    }

    /** Même site si les deux hôtes partagent les deux derniers libellés (ou trois pour les suffixes de pays du type co.uk). */
    fun sameSite(a: String, b: String): Boolean = registrable(a) == registrable(b)

    private fun registrable(h: String): String {
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val twoLevel = setOf("co", "com", "org", "net", "gov", "ac")
        val n = if (parts[parts.size - 2] in twoLevel && parts.last().length == 2) 3 else 2
        return parts.takeLast(n).joinToString(".")
    }

    /** Une navigation de page entière déclenchée sans geste de l'utilisateur vers un autre site est une redirection publicitaire. */
    fun isSuspiciousRedirect(targetHost: String?, pageHost: String?, hasGesture: Boolean, isRedirect: Boolean): Boolean {
        if (targetHost.isNullOrEmpty() || pageHost.isNullOrEmpty()) return false
        if (sameSite(targetHost.lowercase(), pageHost.lowercase())) return false
        return !hasGesture || (isRedirect && isBlocked(targetHost))
    }
}
