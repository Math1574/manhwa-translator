package app.manhwa.core

import java.net.URLEncoder

/** Devine l'écriture d'un texte pour choisir le modèle de traduction (ja, ko, zh, en). */
object ScriptDetector {
    fun detect(text: String): String {
        var kana = 0; var hangul = 0; var han = 0
        for (c in text) {
            val x = c.code
            when {
                x in 0x3040..0x30FF -> kana++
                x in 0xAC00..0xD7AF || x in 0x1100..0x11FF || x in 0x3130..0x318F -> hangul++
                x in 0x4E00..0x9FFF || x in 0x3400..0x4DBF -> han++
            }
        }
        return when {
            kana > 0 -> "ja"
            hangul > 0 -> "ko"
            han > 0 -> "zh"
            else -> "en"
        }
    }

    fun hasCjk(text: String) = text.any { it.isCjk() }
}

object ColorMath {
    /** Médiane par canal d'une liste de couleurs ARGB ; blanc si la liste est vide. */
    fun median(colors: IntArray): Int {
        if (colors.isEmpty()) return 0xFFFFFFFF.toInt()
        fun ch(shift: Int): Int = colors.map { (it shr shift) and 0xFF }.sorted()[colors.size / 2]
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun luminance(rgb: Int): Float {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    fun textColorFor(bg: Int): Int = if (luminance(bg) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    fun css(rgb: Int): String = "#%06x".format(rgb and 0xFFFFFF)
}

object UrlInput {
    private val blockedSchemes = Regex("^(javascript|file|content|intent|data|about|blob):", RegexOption.IGNORE_CASE)

    /** Adresse saisie → URL http(s) ; texte libre → recherche. Les schémas dangereux deviennent une recherche. */
    fun normalize(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return t
        val looksLikeHost = !t.any { it.isWhitespace() } && (t.contains('.') || t.startsWith("localhost")) && !blockedSchemes.containsMatchIn(t)
        return if (looksLikeHost) "https://$t" else "https://duckduckgo.com/?q=" + URLEncoder.encode(t, "UTF-8")
    }
}

/** Déduit le titre de la série d'une adresse de chapitre (ex. /comics/the-heavenly-demon-wants-a-quiet-life-11fe7da3/chapter/7). */
object SeriesHint {
    private val keywords = setOf("comics", "comic", "series", "serie", "manga", "manhwa", "manhua", "webtoon", "webtoons", "title", "titles", "read", "novel", "work")
    private val chapterLike = Regex("(?i)^(chapter|chap|ch|episode|ep|vol|volume|page|viewer)([-_]?\\d+.*)?$|^\\d+$|(^|-)(season|episode|chapter)(-|\\d|$)|-ep-\\d+")
    private val trailingId = Regex("(?i)[-_](?:[0-9a-f]{6,}|\\d{3,})$")

    fun fromUrl(url: String): String? {
        val path = url.substringAfter("://", url).substringAfter('/', "").substringBefore('?').substringBefore('#')
        val segs = path.split('/').filter { it.isNotBlank() }
        if (segs.isEmpty()) return null
        var cand: String? = null
        for (i in segs.indices) if (segs[i].lowercase() in keywords && i + 1 < segs.size && !chapterLike.containsMatchIn(segs[i + 1])) { cand = segs[i + 1]; break }
        if (cand == null) cand = segs.filter { it.contains('-') && !chapterLike.containsMatchIn(it) && it.count { c -> c.isLetter() } >= 6 }.maxByOrNull { it.length }
        if (cand == null) return null
        val words = cand.substringBefore('.').replace(trailingId, "").split('-', '_').filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        val title = words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        return title.takeIf { it.length >= 3 }
    }
}
