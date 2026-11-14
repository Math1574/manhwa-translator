package app.manhwa.core

/**
 * Trace d'un blocage de l'application (ce que le système relève quand elle « ne répond plus ») : on n'en garde que la pile des fils qui disent ce que l'application attendait
 * (le fil principal, et celui de la traduction), pour le journal.
 */
object CrashTrace {
    /**
     * Piles des fils dont le nom est dans [threads] (premier mot de la ligne d'en-tête, entre guillemets), les lignes « | » de détail du système omises, au plus [maxLines] lignes par fil et
     * [maxChars] caractères en tout. Texte vide si la trace est illisible ou ne contient aucun de ces fils.
     */
    fun excerpt(trace: String, threads: List<String>, maxLines: Int = 28, maxChars: Int = 4000): String {
        val out = StringBuilder()
        val lines = trace.lineSequence().iterator()
        var keep = 0
        while (lines.hasNext()) {
            val l = lines.next()
            if (l.startsWith("\"")) {
                val name = l.substring(1).substringBefore('"')
                keep = if (name in threads) maxLines else 0
                if (keep > 0) { out.append(l.take(160)).append('\n'); keep-- }
            } else if (keep > 0) {
                if (l.isBlank()) { keep = 0; continue }
                if (l.trimStart().startsWith("|")) continue
                out.append(l.take(200)).append('\n'); keep--
            }
            if (out.length >= maxChars) break
        }
        return out.toString().take(maxChars).trimEnd()
    }
}
