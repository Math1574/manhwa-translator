package app.manhwa.core

/**
 * Phrase coupée en deux bulles, avec d'autres répliques entre les deux (journal du 23/11) : « SOMEDAY, WILL I ALSO.. » … « FATHER! » … « I'LL BE BACK WITH THE HERBS. » …
 * « BE A PHYSICIAN ON HIS LEVEL? ». Traduites chacune pour elle-même, les deux moitiés perdaient leur lien (« Être un médecin à son niveau ? », mot à mot) : le message au modèle
 * les signale ensemble, pour que la seconde continue la première en français.
 *
 * Une paire n'est retenue que sur des indices nets : la première ligne reste en suspens (points de suspension, virgule, tiret, ou un mot qui ne termine pas une phrase), la seconde
 * commence comme une suite (minuscule, points de suspension, conjonction, ou verbe à l'infinitif après un auxiliaire resté en suspens dans la première).
 */
object SplitSentences {
    /** Une phrase coupée : lignes [first] et [second] (à partir de 1, dans le lot envoyé). */
    data class Pair(val first: Int, val second: Int)

    private val modals = setOf("will", "would", "can", "could", "should", "must", "might", "may", "shall", "to", "gonna", "wanna", "gotta", "let", "i'll", "you'll", "we'll", "he'll", "she'll", "they'll", "won't", "can't", "don't", "didn't", "cannot")
    /** Verbes courants à l'infinitif qui suivent un auxiliaire (« will I also… / BE a physician »). */
    private val bareVerbs = setOf(
        "be", "become", "have", "get", "go", "do", "make", "see", "find", "live", "die", "stay", "win", "lose", "beat", "defeat", "kill", "protect", "save", "surpass", "reach",
        "keep", "take", "give", "come", "return", "leave", "fight", "learn", "know", "understand", "forgive", "forget", "remember", "believe", "trust", "help", "stop", "end",
        "change", "meet", "marry", "love", "hate", "follow", "wait", "stand", "rise", "fall", "grow", "eat", "sleep", "walk", "run", "fly", "kneel", "bow", "obey", "serve",
        "join", "use", "show", "tell", "say", "ask", "pay", "buy", "sell", "own", "rule", "lead", "become", "turn", "remain", "survive", "escape", "finish", "start", "begin",
    )
    /** Mots qui ne terminent pas une phrase anglaise. */
    private val dangling = setOf("also", "and", "but", "or", "the", "a", "an", "to", "of", "with", "for", "in", "on", "at", "from", "that", "which", "who", "if", "because",
        "than", "as", "like", "my", "your", "his", "her", "our", "their", "will", "would", "can", "could", "should", "must", "even", "just", "still", "too", "be", "is", "are", "was")
    /** Débuts de suite (conjonctions, prépositions) : seulement après une première ligne qui finit en suspens. */
    private val linkers = setOf("and", "but", "or", "than", "that", "to", "of", "with", "for", "because", "so", "then", "until", "unless", "without", "like", "as", "which", "who")

    private val wordRe = Regex("[A-Za-z']+")

    private fun words(t: String): List<String> = wordRe.findAll(t).map { it.value.lowercase().trim('\'') }.filter { it.isNotEmpty() }.toList()

    /** Ligne restée en suspens : rend vrai si la phrase n'y est manifestement pas finie. */
    private fun unfinished(t: String): Boolean {
        val s = t.trim()
        if (s.isEmpty()) return false
        if (s.endsWith("..") || s.endsWith("…") || s.endsWith(",") || s.endsWith("-") || s.endsWith("—") || s.endsWith("–")) return true
        if (s.last().isLetter()) return words(s).lastOrNull() in dangling
        return false
    }

    /** Début de la seconde moitié d'une phrase. */
    private fun continues(t: String, first: String): Boolean {
        val s = t.trimStart()
        if (s.isEmpty()) return false
        if (s.startsWith("..") || s.startsWith("…")) return true
        val ws = words(s)
        val w0 = ws.firstOrNull() ?: return false
        // minuscule en tête d'une ligne d'un texte en casse normale (pas un « i » isolé)
        val c0 = s.firstOrNull { it.isLetter() } ?: return false
        if (c0.isLowerCase() && first.any { it.isLowerCase() } && w0 != "i") return true
        val fw = words(first)
        if (w0 in bareVerbs && fw.any { it in modals } && (first.trim().endsWith("..") || first.trim().endsWith("…") || fw.lastOrNull() in dangling)) return true
        if (w0 in linkers && (first.trim().endsWith(",") || first.trim().endsWith("..") || first.trim().endsWith("…") || first.trim().endsWith("-"))) return true
        return false
    }

    /**
     * Paires (première ligne, suite) parmi [texts], la suite à au moins [minGap] et au plus [maxGap] lignes de distance. Une ligne ne sert qu'une fois ; une ligne vide ou d'un seul
     * caractère n'en fait pas partie.
     */
    fun find(texts: List<String>, maxGap: Int = 4, minGap: Int = 1): List<Pair> {
        val out = ArrayList<Pair>()
        val used = HashSet<Int>()
        for (j in texts.indices) {
            if (j in used) continue
            var found = -1
            // la ligne en suspens la plus proche : une suite juste après sa première moitié n'est pas signalée (le modèle les voit ensemble), mais elle ne peut plus se rattacher plus haut
            for (i in j - 1 downTo maxOf(0, j - maxGap)) {
                if (i in used) continue
                if (texts[i].count { it.isLetter() } < 2) continue
                if (unfinished(texts[i]) && continues(texts[j], texts[i])) { found = i; break }
            }
            if (found >= 0) { if (j - found >= minGap) out.add(Pair(found + 1, j + 1)); used.add(found); used.add(j) }
        }
        return out
    }
}
