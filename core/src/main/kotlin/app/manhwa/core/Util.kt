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
