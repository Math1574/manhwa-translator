package app.manhwa.core

/**
 * Sites web : extraction du nom d'hôte d'une adresse et du « domaine enregistrable » (le site, sans les sous-domaines).
 * Le calcul du site est une approximation de la liste des suffixes publics, suffisante pour distinguer le site visité des services tiers.
 */
object Sites {
    private val multiLabel = hashSetOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk", "sch.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "co.kr", "or.kr", "ne.kr",
        "com.br", "net.br", "org.br", "com.cn", "net.cn", "org.cn", "com.tw", "org.tw", "com.hk", "com.sg", "com.my", "com.mx", "com.ar",
        "co.in", "net.in", "org.in", "co.id", "co.nz", "co.za", "com.tr", "com.ua", "com.vn", "com.ph", "co.th", "com.co", "com.pe",
        "com.ve", "com.eg", "com.sa", "com.pk", "com.ng", "co.il", "co.ke",
        // hébergeurs où chaque sous-domaine est un site différent
        "github.io", "gitlab.io", "pages.dev", "workers.dev", "vercel.app", "netlify.app", "herokuapp.com", "web.app", "firebaseapp.com",
        "blogspot.com", "wordpress.com", "cloudfront.net", "azurewebsites.net", "appspot.com", "fly.dev", "onrender.com", "r2.dev", "b-cdn.net",
    )

    /** Nom d'hôte (en minuscules, sans port) d'une adresse http(s), ou null. */
    fun hostOf(url: String?): String? {
        if (url == null) return null
        val s = hostStart(url)
        if (s < 0) return null
        val e = hostEnd(url, s)
        return if (e > s) url.substring(s, e).lowercase().trimEnd('.') else null
    }

    internal fun hostStart(u: String): Int {
        val i = u.indexOf("://")
        if (i < 1 || i > 12) return -1
        var s = i + 3
        var end = s
        while (end < u.length && u[end] != '/' && u[end] != '?' && u[end] != '#') end++
        val at = u.lastIndexOf('@', end - 1)
        if (at >= s) s = at + 1
        return s
    }

    internal fun hostEnd(u: String, s: Int): Int {
        var e = s
        if (e < u.length && u[e] == '[') {                      // adresse IPv6
            while (e < u.length && u[e] != ']') e++
            return minOf(e + 1, u.length)
        }
        while (e < u.length) { val c = u[e]; if (c == '/' || c == '?' || c == '#' || c == ':') break; e++ }
        return e
    }

    private fun isIp(h: String): Boolean = h.indexOf(':') >= 0 || (h.isNotEmpty() && h.all { it.isDigit() || it == '.' })

    /** Le site d'un nom d'hôte : « img.exemple.co.uk » → « exemple.co.uk ». Les adresses IP et les noms d'un seul libellé sont rendus tels quels. */
    fun registrable(host: String): String {
        val h = host.lowercase().trimEnd('.')
        if (isIp(h)) return h
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val last2 = parts[parts.size - 2] + "." + parts[parts.size - 1]
        val n = if (last2 in multiLabel || (parts[parts.size - 2].length <= 3 && parts.last().length == 2 && parts[parts.size - 2] in shortSecondLevel)) 3 else 2
        return parts.takeLast(n).joinToString(".")
    }

    private val shortSecondLevel = hashSetOf("co", "com", "org", "net", "gov", "ac", "edu", "ne", "or")

    fun sameSite(a: String?, b: String?): Boolean = a != null && b != null && registrable(a) == registrable(b)

    /** Vrai si l'hôte ou l'un de ses domaines parents (a.b.c → a.b.c, b.c, c) figure dans l'ensemble. */
    fun hostInSet(host: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        var d = host
        while (true) {
            if (d in set) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }
}
