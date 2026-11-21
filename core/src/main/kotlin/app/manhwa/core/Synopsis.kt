package app.manhwa.core

/**
 * Un texte relevé dans la page d'une œuvre, avec ce qui dit d'où il vient :
 * « head » = le texte qui suit un titre « Synopsis » / « Résumé » / « Description », « dom » = un bloc de description de la page, « ld » = les données structurées (JSON-LD),
 * « dom2 » = un bloc dont la classe parle de résumé ou de description (moins sûr), « meta » = les balises de partage (og:description…), « api » = l'annuaire public.
 */
data class SynopsisText(val source: String, val text: String)

/**
 * Synopsis d'une œuvre pour la bibliothèque : nettoyage du texte (balises, étiquettes, lignes de métadonnées, spoilers), choix du meilleur texte relevé dans la page de l'œuvre,
 * refus de ce qui n'est pas un synopsis (phrase générique du site, page d'attente, simple titre).
 * Rien n'est inventé : le texte affiché est celui que le site (ou l'annuaire) donne, à peine mis en forme.
 */
object Synopsis {
    /** En dessous, ce n'est pas un synopsis (un titre, un genre, « Lire gratuitement »). */
    const val MIN_CHARS = 40
    /** Au-dessus, le texte est coupé à la fin d'une phrase. */
    const val MAX_CHARS = 1400

    private val tag = Regex("<[^>]{0,300}>")
    private val lineBreakTag = Regex("(?i)<\\s*br\\s*/?\\s*>|</\\s*(?:p|div|li|h[1-6]|tr)\\s*>")
    private val spoiler = Regex("~!.*?!~", RegexOption.DOT_MATCHES_ALL)
    private val spaces = Regex("[ \\t\\u00a0\\u2007\\u202f]+")
    private val label = Regex("(?i)^(?:synopsis|summary|description|résumé|resume|plot|story|storyline|overview|sinopsis|sinopse|intrigue|あらすじ|ストーリー|줄거리|시놉시스|简介|内容简介|剧情简介)\\s*[:：\\-–—]\\s*(.*)$")
    private val bareLabel = Regex("(?i)^(?:synopsis|summary|description|résumé|resume|plot|story|storyline|overview|sinopsis|sinopse|intrigue|あらすじ|ストーリー|줄거리|시놉시스|简介|内容简介|剧情简介)(?:\\s+(?:of|de|du|d')\\s+.{1,80})?\\s*[:：]?$")
    private val meta = Regex("(?i)^\\(?\\s*(?:alternatives?(?: (?:title|name)s?)?|alt\\.? titles?|other names?|autres? (?:titres?|noms?)|genres?|authors?|author\\(s\\)|artists?|auteurs?|status|statut|type|released?|year|source|serialization|written by|art by|rating|views?|followers?|bookmarked)\\s*[:：]")
    private val more = Regex("(?i)^(?:show more|show less|read more|read less|see more|voir plus|voir moins|lire la suite|afficher plus|…|\\.\\.\\.|more|less)\\.?$")

    private val challenge = Regex("(?i)(enable javascript|checking your browser|just a moment|ddos protection|access denied|cloudflare|captcha|are you (?:a )?(?:human|robot)|verify you are human|un instant|vérification en cours)")
    /** Phrases d'un site, pas d'une œuvre : « Read X online for free at … », droits réservés, cookies. */
    private val generic = listOf(
        Regex("(?i)^\\s*(?:read|lisez|lire|watch)\\b.{0,140}\\b(?:online|for free|free online|en ligne|gratuit(?:ement)?)\\b"),
        Regex("(?i)\\b(?:read|lisez|lire)\\b.{0,100}\\b(?:chapters?|chapitres?|manga|manhwa|manhua|webtoon|scans?)\\b.{0,80}\\b(?:online|for free|free online|en ligne|gratuit(?:ement)?)\\b.{0,80}$"),
        Regex("(?i)all rights reserved|tous droits réservés|terms of (?:use|service)|privacy policy|politique de confidentialité|(?:we|this (?:web)?site) uses? cookies|accept (?:all )?cookies"),
        Regex("(?i)^\\s*(?:latest|newest|popular|trending)\\b.{0,60}\\b(?:chapters?|series|manga|manhwa|updates?)\\b"),
    )

    private fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val end = s.indexOf(';', i + 1)
                if (end in (i + 2)..(i + 10)) {
                    val name = s.substring(i + 1, end)
                    val rep: String? = when {
                        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        name.startsWith("#") -> name.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> when (name) {
                            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "; "hellip" -> "…"
                            "rsquo", "lsquo" -> "'"; "rdquo", "ldquo" -> "\""; "ndash" -> "–"; "mdash" -> "—"; "laquo" -> "«"; "raquo" -> "»"
                            else -> null
                        }
                    }
                    if (rep != null) { sb.append(rep); i = end + 1; continue }
                }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }

    /**
     * Le texte tel qu'on l'affiche : balises retirées (les retours à la ligne gardés), entités décodées, spoilers de l'annuaire (« ~!…!~ ») supprimés, espaces compressés,
     * étiquette « Synopsis : » du début, lignes de métadonnées (titres alternatifs, genres, auteur, « Source : … ») et « Voir plus » retirées, coupé à [MAX_CHARS] à la fin d'une phrase.
     */
    fun clean(raw: String): String {
        var s = raw.replace("\r\n", "\n").replace('\r', '\n')
        s = lineBreakTag.replace(s, "\n")
        s = tag.replace(s, "")
        s = decodeEntities(s)
        s = spoiler.replace(s, "")
        s = s.replace("\u200b", "").replace("\u00ad", "")
        val out = ArrayList<String>()
        for ((i, line0) in s.split('\n').withIndex()) {
            var line = spaces.replace(line0, " ").trim()
            if (out.isEmpty() && i < 3) {
                label.matchEntire(line)?.let { line = it.groupValues[1].trim() }
                if (bareLabel.matches(line)) continue
            }
            if (line.isEmpty()) { if (out.isNotEmpty() && out.last().isNotEmpty()) out.add(""); continue }
            if (meta.containsMatchIn(line) || more.matches(line)) continue
            out.add(line)
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        return cut(out.joinToString("\n"))
    }

    /** Coupe à [MAX_CHARS] : à la fin d'une phrase si l'une tombe dans la dernière partie, sinon à un mot, avec « … ». */
    private fun cut(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val window = text.substring(0, MAX_CHARS)
        val sentence = maxOf(window.lastIndexOf(". "), window.lastIndexOf("! "), window.lastIndexOf("? "), window.lastIndexOf(".\n"), window.lastIndexOf("。"))
        if (sentence >= MAX_CHARS * 6 / 10) return window.substring(0, sentence + 1).trimEnd()
        val word = window.lastIndexOf(' ')
        return (if (word >= MAX_CHARS * 6 / 10) window.substring(0, word) else window).trimEnd(' ', ',', ';', ':', '-') + "…"
    }

    /** Une phrase qui parle du site et non de l'œuvre, une page d'attente, ou un texte trop court pour être un résumé. */
    fun usable(cleaned: String, title: String): Boolean {
        // (japonais, chinois, coréen : pas d'espaces entre les mots, un résumé plus court tient en moins de lettres)
        val cjk = cleaned.count { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF || it.code in 0xAC00..0xD7AF }
        if (cjk * 2 >= cleaned.length) { if (cleaned.length < 20) return false }
        else if (cleaned.length < MIN_CHARS || cleaned.count { it == ' ' } < 5) return false
        if (challenge.containsMatchIn(cleaned)) return false
        if (generic.any { it.containsMatchIn(cleaned) }) return false
        val c = CoverPick.compact(cleaned); val t = CoverPick.compact(title)
        if (t.length >= 3 && c == t) return false                                                            // le titre seul
        // « Titre – Lire en ligne », « Titre | Site » : le titre suivi d'un séparateur, en peu de mots
        if (t.length >= 3 && cleaned.startsWith(title.trim(), ignoreCase = true) && cleaned.length < 160) {
            val rest = cleaned.substring(title.trim().length).trimStart()
            if (rest.isEmpty() || rest[0] in "-–—|:»·•") return false
        }
        return true
    }

    /** Note d'un texte : la source d'abord (le texte sous le titre « Synopsis » vaut mieux que la balise de partage, souvent coupée ou fabriquée par le site), puis sa longueur. */
    private fun score(source: String, text: String): Int {
        var s = when (source) { "head" -> 60; "dom" -> 58; "ld" -> 52; "api" -> 50; "dom2" -> 44; "meta" -> 40; else -> 30 }
        s += minOf(text.length, 600) / 30
        if (text.length in 150..MAX_CHARS) s += 8
        if (text.length < 90) s -= 15
        return s
    }

    /** Un texte relevé, nettoyé, ou null s'il n'est pas utilisable. */
    fun accept(raw: String, title: String): String? = clean(raw).takeIf { usable(it, title) }

    /** Le meilleur synopsis parmi ce que la page annonce, ou null. Un bloc dont seule la classe parle de description (« dom2 ») doit être assez long : un commentaire a la même classe. */
    fun pick(texts: List<SynopsisText>, title: String): String? =
        texts.mapNotNull { t -> accept(t.text, title)?.takeIf { t.source != "dom2" || it.length >= 90 }?.let { it to score(t.source, it) } }.maxByOrNull { it.second }?.first

    /** Empreinte d'un texte (ses 160 premières lettres et chiffres) : deux œuvres qui reçoivent le même texte, c'est le texte du site. */
    fun fingerprint(text: String): String = CoverPick.compact(text).take(160)

    // ---------- version française ----------

    /** Petits mots propres au français, et à l'anglais (sans ceux que les deux langues partagent : « a », « on », « en », « son », « plus », « an »…). */
    private val frenchWords = setOf(
        "le", "la", "les", "un", "une", "des", "du", "de", "et", "est", "sont", "qui", "que", "dans", "pour", "avec", "sur", "pas", "il", "elle", "ils", "elles", "au", "aux", "ce", "cette", "ces", "ses",
        "mais", "où", "comme", "nous", "vous", "leur", "leurs", "été", "être", "ont", "sans", "sous", "entre", "après", "avant", "lui", "se", "ne", "était", "étaient", "ainsi", "depuis", "lorsque",
        "quand", "alors", "afin", "chez", "vers", "dont", "aussi", "très", "bien", "tout", "tous", "toute", "toutes", "même", "autre", "autres", "mon", "ma", "mes", "votre", "notre", "l", "qu",
    )
    private val englishWords = setOf(
        "the", "of", "and", "to", "in", "is", "are", "was", "were", "that", "this", "with", "for", "his", "her", "he", "she", "they", "their", "its", "it", "at", "by", "from", "be", "been", "has", "have",
        "had", "but", "not", "who", "which", "when", "after", "into", "one", "him", "them", "will", "can", "what", "where", "while", "before", "only", "also", "every", "other", "all", "now", "once",
        "there", "then", "than", "about", "over", "my", "your", "our", "you", "we", "i", "if", "so", "do", "does", "did", "would", "could", "should", "me",
    )
    private val wordRe = Regex("[\\p{L}]+")

    private fun wordCounts(text: String): Pair<Int, Int> {
        var fr = 0; var en = 0
        for (m in wordRe.findAll(text.lowercase())) {
            val w = m.value
            if (w in frenchWords) fr++
            if (w in englishWords) en++
        }
        return fr to en
    }

    /** Le texte est déjà en français (nettement plus de petits mots français qu'anglais) : inutile de le traduire. */
    fun isFrench(text: String): Boolean {
        val (fr, en) = wordCounts(text)
        return fr >= 2 && fr > en * 2
    }

    /** Écriture dominante du texte à traduire : vrai pour du japonais, du coréen ou du chinois (on compte les caractères, pas les mots). */
    private fun isCjk(text: String): Boolean = text.count { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF || it.code in 0xAC00..0xD7AF } * 2 >= text.count { !it.isWhitespace() }

    /**
     * Ce que le modèle a répondu, mis au propre : phrase d'introduction (« Voici la traduction : »), titre de section, guillemets autour du tout et gras retirés, sauts de ligne compactés.
     * Null si ce n'est pas une traduction exploitable : réponse vide, identique à l'original, pas en français (le modèle a recopié l'anglais), de longueur invraisemblable, ou coupée
     * en pleine phrase alors que l'original finit par une ponctuation (génération interrompue : mieux vaut l'original entier qu'une traduction tronquée).
     */
    fun cleanFrench(raw: String, source: String): String? {
        var lines = raw.replace("\r\n", "\n").replace('\r', '\n').trim().split('\n').map { it.trimEnd() }.toMutableList()
        val intro = Regex("(?i)^\\W*(?:voici|here is|here's|traduction|translation|résumé traduit|synopsis)\\b.{0,60}[:：]\\s*$")
        val heading = Regex("(?i)^\\W*(?:synopsis|résumé|traduction|translation)\\W*$")
        while (lines.isNotEmpty() && (lines.first().isBlank() || intro.matches(lines.first().trim()) || heading.matches(lines.first().trim()))) lines.removeAt(0)
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        var t = lines.joinToString("\n").replace("**", "").replace(Regex("\\n{3,}"), "\n\n").trim()
        if (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '«' && t.last() == '»') || (t.first() == '“' && t.last() == '”'))) t = t.substring(1, t.length - 1).trim()
        if (t.count { it.isLetter() } < 15) return null
        if (CoverPick.compact(t) == CoverPick.compact(source)) return null
        val (fr, en) = wordCounts(t)
        if (fr <= en) return null
        if (!isCjk(source)) {
            val ratio = t.length.toDouble() / source.length.coerceAtLeast(1)
            if (ratio < 0.5 || ratio > 3.0) return null
        }
        val srcEnds = source.trimEnd().lastOrNull()?.let { it in ".!?…。！？»”\")" } == true
        val outEnds = t.lastOrNull()?.let { !it.isLetterOrDigit() && it != ',' && it != ';' && it != ':' && it != '-' } == true
        if (srcEnds && !outEnds) return null
        return t
    }
}

/**
 * Consigne donnée au modèle pour traduire un synopsis : une conversation à part (aucune mémoire de chapitre), le texte tel que le site le donne, la réponse attendue en français seulement.
 * Les noms propres restent tels quels : le titre, les personnages et les lieux ne se traduisent pas.
 */
object SynopsisPrompt {
    fun system(): String = """
        You translate the synopsis (the back-cover blurb) of a comic series — manhwa, manga, manhua or webtoon — into natural, fluent French.
        - Translate everything faithfully: do not add, remove, summarize, explain or comment.
        - Keep proper names exactly as written (characters, places, sects, clans, organizations, the series title). Do not translate them.
        - Write natural French, in the register of a book blurb. Keep the verb tenses of the original.
        - Keep the paragraph breaks of the original.
        - Reply with the French translation only: no introduction, no quotation marks, no notes.
    """.trimIndent()

    fun user(text: String): String = "Translate this synopsis into French:\n\n" + text.trim()

    /** Plafond de la réponse : le français est plus long que l'anglais, bien plus que le japonais ou le coréen n'ont de caractères. */
    fun maxOutputTokens(text: String): Int {
        val cjk = text.count { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF || it.code in 0xAC00..0xD7AF }
        val base = if (cjk * 2 >= text.count { !it.isWhitespace() }) text.length * 2.4 else text.length / 2.6 * 1.3
        return (base + 64).toInt().coerceIn(160, 1100)
    }
}

/** Ce que l'on sait du synopsis d'une œuvre : trouvé (texte, d'où il vient) ou introuvable (nombre d'essais, dernier essai). */
data class SynopsisRecord(val key: String, val text: String, val source: String, val ok: Boolean, val tries: Int, val lastTry: Long, val fr: String = "")

/**
 * Registre des synopsis, données immuables (comme [CoverBook]) : une œuvre sans synopsis est réessayée quelques fois, pas à l'infini. Un même texte reçu par deux œuvres de titres
 * différents n'est pas un synopsis mais le texte du site : il n'est gardé pour aucune, ni pour les suivantes.
 */
class SynopsisBook(val records: List<SynopsisRecord> = emptyList(), val banned: Set<String> = emptySet()) {
    private val byKey = records.associateBy { it.key }

    data class Saved(val book: SynopsisBook, val accepted: Boolean)

    fun textOf(key: String): String? = byKey[key]?.takeIf { it.ok }?.text
    fun sourceOf(key: String): String? = byKey[key]?.takeIf { it.ok }?.source
    fun has(key: String): Boolean = byKey[key]?.ok == true

    /** La traduction française du synopsis, si elle est faite. */
    fun frenchOf(key: String): String? = byKey[key]?.takeIf { it.ok && it.fr.isNotEmpty() }?.fr

    /** Le synopsis est trouvé, pas encore traduit, et n'est pas déjà en français. */
    fun needsFrench(key: String): Boolean = byKey[key]?.let { it.ok && it.fr.isEmpty() && !Synopsis.isFrench(it.text) } == true

    /** Range la traduction faite pour [text] ; sans effet si le synopsis de l'œuvre a changé entre-temps (la traduction ne serait plus celle du texte affiché). */
    fun withFrench(key: String, text: String, fr: String): SynopsisBook {
        val r = byKey[key] ?: return this
        if (!r.ok || r.text != text || fr.isBlank()) return this
        return SynopsisBook(records.map { if (it.key == key) it.copy(fr = fr) else it }, banned)
    }

    /** À chercher : jamais essayé, ou essayé sans succès depuis assez longtemps et pas trop souvent (mêmes délais que les illustrations). */
    fun wanted(key: String, now: Long): Boolean {
        val r = byKey[key] ?: return true
        return !r.ok && r.tries < CoverBook.MAX_TRIES && now - r.lastTry >= CoverBook.retryAfter(r.tries)
    }

    /** Demande de l'utilisateur : une œuvre sans synopsis est de nouveau à chercher tout de suite ; un synopsis trouvé reste. */
    fun again(key: String): SynopsisBook {
        val r = byKey[key] ?: return this
        return if (r.ok) this else SynopsisBook(records.filter { it.key != key }, banned)
    }

    private fun titleOf(key: String) = key.substringAfter('|')

    /** Texte trouvé pour [key] : gardé, sauf si c'est celui du site (le même texte pour une autre œuvre). */
    fun saved(key: String, text: String, source: String, now: Long): Saved {
        val fp = Synopsis.fingerprint(text)
        if (fp in banned) return Saved(failed(key, now), false)
        val same = records.filter { it.ok && it.key != key && Synopsis.fingerprint(it.text) == fp && !CoverPick.sameTitle(titleOf(it.key), titleOf(key)) }.map { it.key }
        if (same.isEmpty()) return Saved(SynopsisBook(records.filter { it.key != key } + SynopsisRecord(key, text, source, true, 0, now), banned), true)
        val gone = same.toSet() + key
        val marks = gone.map { SynopsisRecord(it, "", "", false, 1, now) }
        return Saved(SynopsisBook(records.filter { it.key !in gone } + marks, banned + fp), false)
    }

    fun failed(key: String, now: Long): SynopsisBook {
        val old = byKey[key]
        return SynopsisBook(records.filter { it.key != key } + SynopsisRecord(key, "", "", false, (old?.tries ?: 0) + 1, now), banned)
    }

    fun forget(key: String) = SynopsisBook(records.filter { it.key != key }, banned)

    fun serialize(): String = buildString {
        for (r in records) append("S\t${esc(r.key)}\t${esc(r.text)}\t${esc(r.source)}\t${if (r.ok) 1 else 0}\t${r.tries}\t${r.lastTry}").append(if (r.fr.isEmpty()) "" else "\t" + esc(r.fr)).append('\n')
        for (b in banned) append("B\t${esc(b)}\n")
    }

    companion object {
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
        fun parse(data: String?): SynopsisBook {
            if (data.isNullOrBlank()) return SynopsisBook()
            val out = LinkedHashMap<String, SynopsisRecord>()
            val ban = LinkedHashSet<String>()
            for (line in data.lines()) {
                val p = line.split('\t')
                try {
                    when {
                        p.size >= 7 && p[0] == "S" -> { val r = SynopsisRecord(unesc(p[1]), unesc(p[2]), unesc(p[3]), p[4] == "1", p[5].toInt(), p[6].toLong(), p.getOrNull(7)?.let { unesc(it) } ?: ""); out[r.key] = r }
                        p.size >= 2 && p[0] == "B" && p[1].isNotEmpty() -> ban.add(unesc(p[1]))
                    }
                } catch (_: Exception) {}
            }
            return SynopsisBook(out.values.toList(), ban)
        }
    }
}
