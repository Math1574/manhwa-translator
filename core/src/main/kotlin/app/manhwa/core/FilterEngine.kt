package app.manhwa.core

/** Types de ressources (masque de bits) pour les options de filtres ($script, $image, $popup…). */
object ResType {
    const val SCRIPT = 1
    const val IMAGE = 2
    const val STYLESHEET = 4
    const val SUBDOCUMENT = 8
    const val XHR = 16
    const val MEDIA = 32
    const val FONT = 64
    const val WEBSOCKET = 128
    const val PING = 256
    const val OBJECT = 512
    const val OTHER = 1024
    const val DOCUMENT = 2048
    const val POPUP = 4096
    const val REQUESTS = SCRIPT or IMAGE or STYLESHEET or SUBDOCUMENT or XHR or MEDIA or FONT or WEBSOCKET or PING or OBJECT or OTHER
    /** Nature incertaine (en-tête Accept générique) : tout ce qui n'est ni une image, ni une feuille de style, ni une page. */
    const val AMBIGUOUS = SCRIPT or XHR or MEDIA or FONT or WEBSOCKET or PING or OBJECT or OTHER
    /** Navigation de page entière ou fenêtre ouverte par la page. */
    const val NAVIGATION = DOCUMENT or POPUP
}

/** Règle réseau au format Adblock Plus / uBlock Origin. [pattern] est en minuscules, sans les ancres ; [text] est la règle d'origine (pour le journal). */
class NetRule(
    @JvmField val pattern: String, @JvmField val flags: Int, @JvmField val types: Int, @JvmField val party: Int,
    @JvmField val include: Array<String>?, @JvmField val exclude: Array<String>?, @JvmField val toInclude: Array<String>?, @JvmField val denyAllow: Array<String>?,
    @JvmField val text: String,
) {
    private var compiled: Regex? = null
    internal fun regex(): Regex = compiled ?: Regex(pattern, RegexOption.IGNORE_CASE).also { compiled = it }
    val important: Boolean get() = flags and FilterEngine.F_IMPORTANT != 0
    val domainAnchored: Boolean get() = flags and FilterEngine.F_DOMAIN != 0
}

/** Décision du moteur : [blocked] et la règle responsable (ou l'exception qui a autorisé). */
class AdVerdict(val blocked: Boolean, val rule: String?) {
    companion object { val ALLOW = AdVerdict(false, null) }
}

/** Ensemble de nombres de 64 bits à adressage ouvert : les noms de domaine des listes y sont stockés sous forme d'empreintes (≈ 8 octets chacun au lieu de ≈ 80). */
class LongSet(expected: Int) {
    private var table = LongArray(Integer.highestOneBit(maxOf(16, expected * 2 - 1)) shl 1)
    var size = 0; private set
    fun add(v0: Long) {
        val v = if (v0 == 0L) 1L else v0
        if ((size + 1) * 10 > table.size * 7) grow()
        val mask = table.size - 1
        var i = (v xor (v ushr 32)).toInt() and mask
        while (true) {
            val t = table[i]
            if (t == v) return
            if (t == 0L) { table[i] = v; size++; return }
            i = (i + 1) and mask
        }
    }
    operator fun contains(v0: Long): Boolean {
        val v = if (v0 == 0L) 1L else v0
        val mask = table.size - 1
        var i = (v xor (v ushr 32)).toInt() and mask
        while (true) {
            val t = table[i]
            if (t == v) return true
            if (t == 0L) return false
            i = (i + 1) and mask
        }
    }
    private fun grow() { val old = table; table = LongArray(old.size * 2); size = 0; for (x in old) if (x != 0L) add(x) }
}

/** Sélecteur CSS de masquage propre à un site ; [exclude] : sous-domaines où il ne s'applique pas. */
class Cos(@JvmField val selector: String, @JvmField val exclude: Array<String>?)

/**
 * Moteur de filtrage au format Adblock Plus / uBlock Origin, en Kotlin pur.
 *
 *  - règles réseau (`||domaine^`, motifs avec `*` et `^`, ancres `|`, expressions régulières, options `$third-party`, `$script`, `$image`, `$domain=`, `$important`, `$popup`…) et exceptions `@@` ;
 *  - masquage d'éléments propre à un site (`site.com##.pub`, exceptions `#@#`) ;
 *  - les domaines publicitaires « purs » (le gros des listes) sont stockés sous forme d'empreintes ; les autres règles sont indexées par mot-clé de leur motif,
 *    de sorte qu'une requête n'est comparée qu'à quelques règles au lieu de plusieurs dizaines de milliers.
 *
 * Le moteur est immuable une fois construit : il peut être interrogé depuis plusieurs fils à la fois.
 */
class FilterEngine private constructor(
    private val block: Side, private val allow: Side,
    private val cosInc: HashMap<String, Array<Cos>>, private val cosExc: HashMap<String, Array<String>>,
    val networkRules: Int, val cosmeticRules: Int,
) {
    internal class Side(
        val dom: LongSet, val domRules: HashMap<String, Array<NetRule>>,
        val idx: HashMap<Int, Array<NetRule>>, val loose: Array<NetRule>, val regex: Array<NetRule>,
    )
    private class Hit(val text: String, val important: Boolean)

    // ---------- interrogation ----------

    /** Faut-il bloquer cette requête ? [type] : un ou plusieurs [ResType]. Les images du site lui-même ne sont jamais bloquées ; les images tierces seulement par des règles de domaine. */
    fun check(url: String, pageUrl: String?, type: Int): AdVerdict {
        val u = url.lowercase()
        val hs = Sites.hostStart(u); if (hs < 0) return AdVerdict.ALLOW
        val he = Sites.hostEnd(u, hs); if (he <= hs) return AdVerdict.ALLOW
        val host = u.substring(hs, he).trimEnd('.')
        val pageHost = Sites.hostOf(pageUrl)
        val third = pageHost != null && !Sites.sameSite(host, pageHost)
        val isImage = type == ResType.IMAGE
        if (isImage && !third) return AdVerdict.ALLOW
        val hit = search(block, u, hs, he, host, pageHost, third, type, nav = false, domainOnly = isImage) ?: return AdVerdict.ALLOW
        if (!hit.important) {
            val ex = search(allow, u, hs, he, host, pageHost, third, type, nav = false, domainOnly = false)
            if (ex != null) return AdVerdict(false, ex.text)
        }
        return AdVerdict(true, hit.text)
    }

    /**
     * Une navigation de page entière (clic, redirection, fenêtre ouverte) vers cette adresse est-elle publicitaire ? Les domaines des listes de publicité et de pop-ups
     * comptent ici même sans option `$popup` : personne ne va volontairement sur une régie publicitaire.
     */
    fun isDocumentBlocked(url: String, pageUrl: String?): AdVerdict {
        val u = url.lowercase()
        val hs = Sites.hostStart(u); if (hs < 0) return AdVerdict.ALLOW
        val he = Sites.hostEnd(u, hs); if (he <= hs) return AdVerdict.ALLOW
        val host = u.substring(hs, he).trimEnd('.')
        val pageHost = Sites.hostOf(pageUrl)
        val third = pageHost != null && !Sites.sameSite(host, pageHost)
        val hit = search(block, u, hs, he, host, pageHost, third, ResType.NAVIGATION, nav = true, domainOnly = false) ?: return AdVerdict.ALLOW
        if (!hit.important) {
            val ex = search(allow, u, hs, he, host, pageHost, third, ResType.NAVIGATION, nav = true, domainOnly = false)
            if (ex != null) return AdVerdict(false, ex.text)
        }
        return AdVerdict(true, hit.text)
    }

    private fun search(side: Side, u: String, hs: Int, he: Int, host: String, pageHost: String?, third: Boolean, type: Int, nav: Boolean, domainOnly: Boolean): Hit? {
        // 1. domaines : le nom d'hôte et ses domaines parents (a.b.c, b.c)
        var s = hs
        while (s < he) {
            val suffixOk = s == hs || u[s - 1] == '.'
            if (suffixOk && he - s > 0 && u.indexOf('.', s).let { it in 0 until he }) {   // jamais le suffixe seul (« com »)
                if (type and ResType.REQUESTS != 0 || nav) {
                    if (side.dom.size > 0 && hash64(u, s, he) in side.dom) return Hit("||" + u.substring(s, he) + "^", false)
                }
                val rs = side.domRules[u.substring(s, he)]
                if (rs != null) for (r in rs) {
                    if (nav && !(r.types and ResType.NAVIGATION != 0 || r.types == ResType.REQUESTS)) continue
                    if (!nav && !optionsOk(r, type, third, pageHost, host)) continue
                    if (nav && !partyAndDomainsOk(r, third, pageHost, host)) continue
                    return Hit(r.text, r.important)
                }
            }
            s++
        }
        if (side.idx.isEmpty() && side.loose.isEmpty() && side.regex.isEmpty()) return null
        // 2. motifs indexés par mot-clé
        var i = 0
        val n = u.length
        while (i < n) {
            if (isTok(u[i])) {
                var j = i; var h = FNV_OFFSET
                while (j < n && isTok(u[j])) { h = (h xor u[j].code) * FNV_PRIME; j++ }
                if (j - i >= 2) {
                    val b = side.idx[h]
                    if (b != null) for (r in b) { val x = tryRule(r, u, hs, he, host, pageHost, third, type, nav, domainOnly); if (x != null) return x }
                }
                i = j
            } else i++
        }
        for (r in side.loose) { val x = tryRule(r, u, hs, he, host, pageHost, third, type, nav, domainOnly); if (x != null) return x }
        if (type != ResType.IMAGE) for (r in side.regex) { val x = tryRule(r, u, hs, he, host, pageHost, third, type, nav, domainOnly); if (x != null) return x }
        return null
    }

    private fun tryRule(r: NetRule, u: String, hs: Int, he: Int, host: String, pageHost: String?, third: Boolean, type: Int, nav: Boolean, domainOnly: Boolean): Hit? {
        if (domainOnly && !r.domainAnchored) return null                 // images : les règles génériques (« /pub/ », « -ad- ») ne suffisent pas
        if (!optionsOk(r, type, third, pageHost, host)) return null
        if (!patternMatches(r, u, hs, he)) return null
        return Hit(r.text, r.important)
    }

    private fun partyAndDomainsOk(r: NetRule, third: Boolean, pageHost: String?, host: String): Boolean {
        if (r.party == P_THIRD && !third) return false
        if (r.party == P_FIRST && third) return false
        val inc = r.include; if (inc != null && (pageHost == null || !anyHost(pageHost, inc))) return false
        val exc = r.exclude; if (exc != null && pageHost != null && anyHost(pageHost, exc)) return false
        val ti = r.toInclude; if (ti != null && !anyHost(host, ti)) return false
        val da = r.denyAllow; if (da != null && anyHost(host, da)) return false
        return true
    }

    private fun optionsOk(r: NetRule, type: Int, third: Boolean, pageHost: String?, host: String): Boolean =
        (r.types and type) != 0 && partyAndDomainsOk(r, third, pageHost, host)

    private fun anyHost(host: String, set: Array<String>): Boolean {
        var d = host
        while (true) {
            for (x in set) if (x == d) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }

    private fun patternMatches(r: NetRule, u: String, hs: Int, he: Int): Boolean {
        val f = r.flags
        if (f and F_REGEX != 0) return try { r.regex().containsMatchIn(u) } catch (e: Throwable) { false }
        val p = r.pattern
        if (p.isEmpty()) return true
        val endAnchor = f and F_END != 0
        if (f and F_DOMAIN != 0) {
            var i = hs
            while (i < he) {
                if ((i == hs || u[i - 1] == '.') && glob(p, 0, u, i, endAnchor)) return true
                i++
            }
            return false
        }
        if (f and F_START != 0) return glob(p, 0, u, 0, endAnchor)
        val first = p[0]
        val anyStart = first == '*' || first == '^'
        var i = 0
        while (i <= u.length) {
            if (anyStart || (i < u.length && u[i] == first)) { if (glob(p, 0, u, i, endAnchor)) return true }
            i++
        }
        return false
    }

    /** Masquage d'éléments propre au site [host] (et à ses domaines parents), sous forme de feuille de style prête à injecter ; vide s'il n'y a rien. */
    fun cosmeticCss(host: String): String {
        if (cosInc.isEmpty()) return ""
        val h = host.lowercase().trimEnd('.')
        val sels = LinkedHashSet<String>()
        var d = h
        while (true) {
            cosInc[d]?.let { arr -> for (c in arr) { val ex = c.exclude; if (ex == null || !anyHost(h, ex)) sels.add(c.selector) } }
            val i = d.indexOf('.')
            if (i < 0) break
            d = d.substring(i + 1)
        }
        if (sels.isEmpty()) return ""
        d = h
        while (true) {
            cosExc[d]?.let { for (x in it) sels.remove(x) }
            val i = d.indexOf('.')
            if (i < 0) break
            d = d.substring(i + 1)
        }
        if (sels.isEmpty()) return ""
        val sb = StringBuilder()
        sels.chunked(40).forEach { chunk -> sb.append(":is(").append(chunk.joinToString(",")).append("){display:none!important}") }
        return sb.toString()
    }

    // ---------- construction ----------

    companion object {
        const val F_DOMAIN = 1; const val F_START = 2; const val F_END = 4; const val F_REGEX = 8; const val F_IMPORTANT = 32
        const val P_ANY = 0; const val P_THIRD = 1; const val P_FIRST = 2
        private const val FNV_OFFSET = -2128831035; private const val FNV_PRIME = 16777619

        internal fun isTok(c: Char) = (c in 'a'..'z') || (c in '0'..'9')
        internal fun isSep(c: Char) = !(c in 'a'..'z' || c in '0'..'9' || c in 'A'..'Z' || c == '_' || c == '-' || c == '.' || c == '%')

        internal fun hash64(s: String, from: Int, to: Int): Long {
            var h = -3750763034362895579L
            for (i in from until to) { h = (h xor s[i].code.toLong()) * 1099511628211L }
            return h
        }

        /** Construit un moteur à partir des lignes d'une ou plusieurs listes (les commentaires et les règles non gérées sont ignorés). */
        fun build(lines: Iterable<String>): FilterEngine = build { lines.asSequence() }

        /** Variante qui relit la source deux fois (une première passe repère les règles « badfilter ») sans garder toutes les lignes en mémoire. */
        fun build(source: () -> Sequence<String>): FilterEngine {
            val bad = HashSet<String>()
            for (raw in source()) if (raw.contains("badfilter")) { val k = badKey(raw.trim()); if (k != null) bad.add(k) }
            val b = Builder(bad)
            for (raw in source()) b.add(raw)
            return b.finish()
        }

        private fun badKey(l: String): String? {
            if (!l.contains("badfilter")) return null
            return l.replace(",badfilter", "").replace("badfilter,", "").replace("\$badfilter", "")
        }

        /** Mise en correspondance d'un motif (`*` = n'importe quoi, `^` = séparateur ou fin d'adresse) à partir de la position [ui]. */
        internal fun glob(p: String, pi0: Int, u: String, ui0: Int, endAnchor: Boolean): Boolean {
            var i = pi0; var j = ui0
            while (i < p.length) {
                val c = p[i]
                when (c) {
                    '*' -> {
                        while (i < p.length && p[i] == '*') i++
                        if (i == p.length) return true
                        var k = j
                        while (k <= u.length) { if (glob(p, i, u, k, endAnchor)) return true; k++ }
                        return false
                    }
                    '^' -> {
                        if (j == u.length) { i++ }
                        else if (isSep(u[j])) { i++; j++ }
                        else return false
                    }
                    else -> { if (j >= u.length || u[j] != c) return false; i++; j++ }
                }
            }
            return if (endAnchor) j == u.length else true
        }
    }

    private class Builder(val bad: Set<String>) {
        val domBlock = LongSet(1 shl 17); val domAllow = LongSet(1 shl 10)
        val domBlockRules = HashMap<String, ArrayList<NetRule>>(); val domAllowRules = HashMap<String, ArrayList<NetRule>>()
        val blocks = ArrayList<NetRule>(); val allows = ArrayList<NetRule>()
        val cosInc = HashMap<String, ArrayList<Cos>>(); val cosExc = HashMap<String, ArrayList<String>>()
        var net = 0; var cos = 0

        fun add(raw: String) {
            val line = raw.trim()
            if (line.isEmpty()) return
            val c0 = line[0]
            if (c0 == '!' || c0 == '[') return
            if (line.contains("\$\$")) return                                   // filtres HTML : non gérés
            val h = line.indexOf('#')
            if (h >= 0 && (line.startsWith("##", h) || line.startsWith("#@#", h) || line.startsWith("#?#", h) || line.startsWith("#@?#", h) ||
                    line.startsWith("#\$#", h) || line.startsWith("#@\$#", h) || line.startsWith("#%#", h) || line.startsWith("#@%#", h))) { cosmetic(line, h); return }
            network(line)
        }

        private val procedural = arrayOf(":has-text(", ":-abp-", ":xpath(", ":matches-css", ":matches-path", ":min-text-length", ":upward(", ":nth-ancestor(", ":others(",
            ":remove(", ":style(", ":watch-attr(", ":contains(", ":matches-media(", ":matches-attr(", ":matches-property(", ":is-visible", ":shadow(", ":-abp", "+js(")

        private fun cosmetic(line: String, h: Int) {
            val exception = line.startsWith("#@#", h)
            if (!exception && !line.startsWith("##", h)) return
            if (exception && !line.startsWith("#@#", h)) return
            val sel = line.substring(h + if (exception) 3 else 2).trim()
            if (sel.isEmpty() || sel.indexOf('{') >= 0 || sel.indexOf('}') >= 0) return
            for (p in procedural) if (sel.contains(p)) return
            val domainsPart = line.substring(0, h)
            if (domainsPart.isEmpty()) return                                  // règles génériques : non utilisées (trop lourdes, risque de masquer du contenu)
            val inc = ArrayList<String>(); val exc = ArrayList<String>()
            for (d0 in domainsPart.split(',')) {
                val d = d0.trim().lowercase()
                if (d.isEmpty() || d.contains('*') || d.contains('/')) continue
                if (d.startsWith("~")) exc.add(d.substring(1)) else inc.add(d)
            }
            if (inc.isEmpty()) return
            cos++
            for (d in inc) {
                if (exception) cosExc.getOrPut(d) { ArrayList(2) }.add(sel)
                else cosInc.getOrPut(d) { ArrayList(4) }.add(Cos(sel, if (exc.isEmpty()) null else exc.toTypedArray()))
            }
        }

        private fun looksLikeOptions(s: String): Boolean {
            if (s.isEmpty()) return false
            val c = s[0]
            if (!(c.isLetterOrDigit() || c == '~')) return false          // « 3p » et « 1p » (tiers, site lui-même) commencent par un chiffre
            var nameStart = true
            for (ch in s) {
                if (nameStart) { if (!(ch.isLetterOrDigit() || ch == '~' || ch == '-' || ch == '_')) return false; nameStart = false }
                else if (ch == ',') nameStart = true
                else if (ch == '=') { /* la valeur peut contenir presque tout */ return validAfterEq(s) }
                else if (!(ch.isLetterOrDigit() || ch == '-' || ch == '_')) return false
            }
            return true
        }
        private fun validAfterEq(s: String): Boolean {
            // chaque option avant un « = » doit avoir un nom valide ; on vérifie les noms de tout le texte
            for (tok in splitOptions(s)) {
                val eq = tok.indexOf('=')
                val name = (if (eq >= 0) tok.substring(0, eq) else tok).removePrefix("~")
                if (name.isEmpty() || !name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return false
            }
            return true
        }
        /** Découpe les options sur les virgules, sans couper les valeurs d'expressions régulières entre barres obliques. */
        private fun splitOptions(s: String): List<String> {
            val out = ArrayList<String>(); var start = 0; var i = 0; var inRegex = false
            while (i < s.length) {
                val c = s[i]
                if (c == '=' && i + 1 < s.length && s[i + 1] == '/') inRegex = true
                if (c == ',' && !inRegex) { out.add(s.substring(start, i)); start = i + 1 }
                if (c == ',' && inRegex && i > 0 && s[i - 1] == '/') { inRegex = false; out.add(s.substring(start, i)); start = i + 1 }
                i++
            }
            out.add(s.substring(start)); return out
        }

        private fun hostList(v: String?): Array<String>? {
            if (v.isNullOrEmpty()) return null
            val r = ArrayList<String>()
            for (d in v.split('|')) { val t = d.trim().lowercase(); if (t.isEmpty()) continue; if (t.contains('*') || t.startsWith("/")) return null; r.add(t) }
            return if (r.isEmpty()) null else r.toTypedArray()
        }

        private fun network(line: String) {
            if (bad.contains(line)) return
            var l = line
            val exception = l.startsWith("@@")
            if (exception) l = l.substring(2)
            var opt: String? = null
            val d = l.lastIndexOf('$')
            if (d >= 0 && looksLikeOptions(l.substring(d + 1))) { opt = l.substring(d + 1); l = l.substring(0, d) }
            var types = 0; var neg = 0; var party = P_ANY; var flags = 0
            var inc: ArrayList<String>? = null; var exc: ArrayList<String>? = null; var toInc: Array<String>? = null; var denyAllow: Array<String>? = null
            if (opt != null) {
                for (tok in splitOptions(opt)) {
                    val eq = tok.indexOf('=')
                    var name = if (eq >= 0) tok.substring(0, eq) else tok
                    val value = if (eq >= 0) tok.substring(eq + 1) else null
                    val negated = name.startsWith("~"); if (negated) name = name.substring(1)
                    val t = when (name) {
                        "script" -> ResType.SCRIPT; "image" -> ResType.IMAGE; "stylesheet", "css" -> ResType.STYLESHEET
                        "subdocument", "frame" -> ResType.SUBDOCUMENT; "xmlhttprequest", "xhr" -> ResType.XHR; "media" -> ResType.MEDIA
                        "font" -> ResType.FONT; "object" -> ResType.OBJECT; "ping" -> ResType.PING; "websocket" -> ResType.WEBSOCKET; "other" -> ResType.OTHER
                        "document", "doc" -> ResType.DOCUMENT; "popup", "popunder" -> ResType.POPUP
                        "all" -> ResType.REQUESTS or ResType.NAVIGATION
                        else -> 0
                    }
                    if (t != 0) { if (negated) neg = neg or t else types = types or t; continue }
                    when (name) {
                        "third-party", "3p", "strict3p" -> party = if (negated) P_FIRST else P_THIRD
                        "first-party", "1p", "strict1p" -> party = if (negated) P_THIRD else P_FIRST
                        "important" -> flags = flags or F_IMPORTANT
                        "match-case" -> {}
                        "badfilter" -> return                                    // la règle « badfilter » elle-même n'est pas une règle de blocage
                        "redirect" -> if (exception) return
                        "empty", "mp4" -> {}
                        "domain", "from" -> {
                            if (value == null) return
                            for (x in value.split('|')) {
                                val dd = x.trim().lowercase(); if (dd.isEmpty()) continue
                                // un joker (« site.* ») ou une expression régulière ne sont pas représentables : on abandonne la règle plutôt que de l'élargir à tous les sites
                                if (dd.contains('*') || dd.startsWith("/") || dd.startsWith("~/")) return
                                if (dd.startsWith("~")) (exc ?: ArrayList<String>().also { exc = it }).add(dd.substring(1)) else (inc ?: ArrayList<String>().also { inc = it }).add(dd)
                            }
                            if (inc == null && exc == null) return
                        }
                        "denyallow" -> denyAllow = hostList(value) ?: return
                        "to" -> {
                            if (value == null) return
                            val pos = ArrayList<String>(); val negs = ArrayList<String>()
                            for (x in value.split('|')) { val dd = x.trim().lowercase(); if (dd.isEmpty()) continue; if (dd.contains('*')) return; if (dd.startsWith("~")) negs.add(dd.substring(1)) else pos.add(dd) }
                            if (pos.isNotEmpty()) toInc = pos.toTypedArray()
                            if (negs.isNotEmpty()) denyAllow = negs.toTypedArray()
                        }
                        else -> return                                          // option non gérée (csp, removeparam, replace, cookie, header…) : règle ignorée
                    }
                }
            }
            if (types == 0) types = ResType.REQUESTS and neg.inv()
            var p = l
            val regex = p.length > 2 && p.startsWith("/") && p.endsWith("/")
            if (!regex) {
                if (p.startsWith("||")) { flags = flags or F_DOMAIN; p = p.substring(2) }
                else if (p.startsWith("|")) { flags = flags or F_START; p = p.substring(1) }
                if (p.endsWith("|")) { flags = flags or F_END; p = p.substring(0, p.length - 1) }
                p = p.lowercase()
                if (flags and (F_DOMAIN or F_START) == 0) p = p.trimStart('*')
                if (flags and F_END == 0) p = p.trimEnd('*')
            } else {
                p = p.substring(1, p.length - 1); flags = flags or F_REGEX
                if (p.length > 250) return
            }
            if (p.isEmpty() && opt == null) return
            if (p == "*" || (p.isEmpty() && flags and (F_DOMAIN or F_START) != 0)) return
            if (p.isEmpty() && inc == null && !exception) return               // « tous les scripts tiers » partout : bien trop large
            net++
            // domaine « pur » : ||hote^ sans chemin
            if (flags and F_DOMAIN != 0 && flags and (F_END or F_REGEX) == 0 && p.length > 3 && p.endsWith("^") && isHostname(p, p.length - 1)) {
                val host = p.substring(0, p.length - 1)
                val plain = types == ResType.REQUESTS && party == P_ANY && inc == null && exc == null && toInc == null && denyAllow == null && flags and F_IMPORTANT == 0
                if (plain) { (if (exception) domAllow else domBlock).add(hash64(host, 0, host.length)); return }
                val r = NetRule(host, flags or F_DOMAIN, types, party, inc?.toTypedArray(), exc?.toTypedArray(), toInc, denyAllow, line)
                (if (exception) domAllowRules else domBlockRules).getOrPut(host) { ArrayList(1) }.add(r)
                return
            }
            val r = NetRule(p, flags, types, party, inc?.toTypedArray(), exc?.toTypedArray(), toInc, denyAllow, line)
            (if (exception) allows else blocks).add(r)
        }

        private fun isHostname(s: String, end: Int): Boolean {
            var dots = 0
            for (i in 0 until end) { val c = s[i]; if (c == '.') dots++ else if (!(c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_')) return false }
            return dots >= 1 && s[0] != '.' && s[end - 1] != '.'
        }

        private fun reliableTokens(r: NetRule, out: ArrayList<Int>) {
            val p = r.pattern; val n = p.length; var i = 0
            while (i < n) {
                if (isTok(p[i])) {
                    var j = i; var h = FNV_OFFSET
                    while (j < n && isTok(p[j])) { h = (h xor p[j].code) * FNV_PRIME; j++ }
                    val leftOk = if (i == 0) (r.flags and (F_DOMAIN or F_START)) != 0 else p[i - 1] != '*'
                    val rightOk = if (j == n) (r.flags and F_END) != 0 else p[j] != '*'
                    if (j - i >= 2 && leftOk && rightOk) out.add(h)
                    i = j
                } else i++
            }
        }

        private fun index(rules: List<NetRule>, domRules: HashMap<String, ArrayList<NetRule>>, dom: LongSet): Side {
            val freq = HashMap<Int, Int>(rules.size)
            val tmp = ArrayList<Int>()
            for (r in rules) if (r.flags and F_REGEX == 0) { tmp.clear(); reliableTokens(r, tmp); for (t in tmp) freq[t] = (freq[t] ?: 0) + 1 }
            val buckets = HashMap<Int, ArrayList<NetRule>>(); val loose = ArrayList<NetRule>(); val regex = ArrayList<NetRule>()
            for (r in rules) {
                if (r.flags and F_REGEX != 0) { regex.add(r); continue }
                tmp.clear(); reliableTokens(r, tmp)
                if (tmp.isEmpty()) { loose.add(r); continue }
                var best = tmp[0]; var bf = freq[best] ?: 1
                for (t in tmp) { val f = freq[t] ?: 1; if (f < bf) { best = t; bf = f } }
                buckets.getOrPut(best) { ArrayList(2) }.add(r)
            }
            val idx = HashMap<Int, Array<NetRule>>(buckets.size * 2)
            for ((k, v) in buckets) idx[k] = v.toTypedArray()
            val dr = HashMap<String, Array<NetRule>>(domRules.size * 2)
            for ((k, v) in domRules) dr[k] = v.toTypedArray()
            return Side(dom, dr, idx, loose.toTypedArray(), regex.toTypedArray())
        }

        fun finish(): FilterEngine {
            val blockSide = index(blocks, domBlockRules, domBlock)
            val allowSide = index(allows, domAllowRules, domAllow)
            val ci = HashMap<String, Array<Cos>>(cosInc.size * 2); for ((k, v) in cosInc) ci[k] = v.toTypedArray()
            val ce = HashMap<String, Array<String>>(cosExc.size * 2); for ((k, v) in cosExc) ce[k] = v.toTypedArray()
            return FilterEngine(blockSide, allowSide, ci, ce, net, cos)
        }
    }
}
