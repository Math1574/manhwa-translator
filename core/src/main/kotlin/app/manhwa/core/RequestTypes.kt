package app.manhwa.core

/**
 * Nature d'une requête de la WebView. Android ne la fournit pas : on la déduit de l'en-tête Accept, de l'extension du fichier et de quelques autres indices.
 * Quand on ne peut pas savoir (script, requête XHR, police… ont tous un Accept générique), on renvoie [ResType.AMBIGUOUS] : le moteur applique alors
 * les règles qui visent l'un de ces types, plutôt que de laisser passer.
 */
object RequestTypes {
    private val imageExt = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp", "apng", "jfif")
    private val fontExt = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val mediaExt = setOf("mp4", "webm", "m3u8", "mp3", "ogg", "ogv", "m4a", "mpd", "ts", "aac", "wav", "flac")

    fun infer(isMainFrame: Boolean, accept: String?, url: String, method: String?, requestedWith: String?, fetchDest: String?): Int {
        if (isMainFrame) return ResType.DOCUMENT
        when (fetchDest?.lowercase()) {
            "image" -> return ResType.IMAGE
            "script", "worker", "sharedworker", "serviceworker" -> return ResType.SCRIPT
            "style" -> return ResType.STYLESHEET
            "iframe", "frame", "fencedframe" -> return ResType.SUBDOCUMENT
            "font" -> return ResType.FONT
            "audio", "video", "track" -> return ResType.MEDIA
            "object", "embed" -> return ResType.OBJECT
        }
        val a = accept?.lowercase() ?: ""
        if (a.startsWith("image/") || a.contains("image/avif") || a.contains("image/webp") || a.contains("image/apng")) return ResType.IMAGE
        if (a.contains("text/css")) return ResType.STYLESHEET
        if (a.startsWith("text/html") || a.contains("application/xhtml+xml")) return ResType.SUBDOCUMENT
        val ext = extensionOf(url)
        if (ext == "js" || ext == "mjs") return ResType.SCRIPT
        if (ext == "css") return ResType.STYLESHEET
        if (ext in imageExt) return ResType.IMAGE
        if (ext in fontExt) return ResType.FONT
        if (ext in mediaExt) return ResType.MEDIA
        if (requestedWith.equals("XMLHttpRequest", ignoreCase = true)) return ResType.XHR
        if (method != null && !method.equals("GET", ignoreCase = true)) return ResType.XHR
        return ResType.AMBIGUOUS
    }

    private fun extensionOf(url: String): String {
        var end = url.indexOf('?'); if (end < 0) end = url.length
        val hash = url.indexOf('#'); if (hash in 0 until end) end = hash
        val slash = url.lastIndexOf('/', end - 1)
        val dot = url.lastIndexOf('.', end - 1)
        return if (dot > slash && dot < end - 1 && end - dot <= 6) url.substring(dot + 1, end).lowercase() else ""
    }
}
