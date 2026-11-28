package app.manhwa.core

/** Réglages d'échantillonnage d'une génération. */
data class LlmSampling(val topK: Int = 20, val topP: Double = 0.9, val temperature: Double = 0.15)

/** Réponse brute d'une génération ; [setupMs] : temps passé à ouvrir la conversation (mesure seulement). */
class LlmReply(val text: String, val setupMs: Long = 0L)

/**
 * Moteur de génération : chaque appel ouvre une conversation neuve (consigne système + un seul message) et renvoie la réponse brute.
 * L'application le fait avec Gemma sur le téléphone (LiteRT-LM) ; le banc d'essai de traduction, avec le même modèle sur un ordinateur.
 */
fun interface LlmBackend {
    fun generate(system: String, user: String, sampling: LlmSampling, maxTokens: Int): LlmReply
}

/** Résultat des contrôles d'une ligne traduite (pour le journal). */
class LineReport(val src: String, val tr: String, val problems: List<String>, val retried: Boolean, val fixed: Boolean)

/**
 * Traduction d'une page par un modèle de langage : demande groupée de toute la page, lignes absentes ou mal placées refaites, relecture facultative, contrôle de chaque ligne
 * (refaite seule si elle échoue), retouches finales ; puis la page rejoint la mémoire du chapitre (dialogue récent, noms, tutoiement) et celle de l'œuvre.
 * Même code dans l'application et dans le banc d'essai : seul le [backend] change.
 *
 * [lexicon] : dictionnaire anglais (lignes mal placées, noms) ; null tant qu'il n'est pas chargé.
 */
class LlmTranslator(private val backend: LlmBackend, private val lexicon: () -> EnglishLexicon?) {
    /** Version du prompt et relecture : réglables par le banc d'essai de traduction. */
    @Volatile var promptVariant: Int = LlmPrompt.CURRENT
    @Volatile var refine: Boolean = false
    /** Titre de la série (déduit de l'adresse) : aide le modèle à comprendre le genre et les noms. */
    @Volatile var seriesTitle: String? = null
    /** Synopsis de l'œuvre (en français s'il a été traduit) : donné au modèle comme contexte (qui sont les personnages). */
    @Volatile var seriesPremise: String? = null
    @Volatile var lastReport: List<LineReport> = emptyList()
    /** Mémoire de l'œuvre (personnages, genre du narrateur, lettres mal lues, genre de l'œuvre) : rappelée au modèle et nourrie par chaque page (prompt 5 et suivants). */
    @Volatile var series: SeriesMemory? = null
    /** Taille de la fenêtre du moteur (consigne + message + réponse), en jetons : le message est raccourci (dialogue précédent d'abord) pour y tenir. */
    @Volatile var contextTokens: Int = 4096

    /**
     * Mémoire du chapitre (dialogue récent, noms propres) : son propre verrou, tenu un instant seulement. Elle ne partage jamais celui de la traduction, qui reste pris pendant toute une génération
     * (des dizaines de secondes) : l'appel qui l'oublie au changement de page vient du fil principal, qu'il ne doit pas figer.
     */
    private val ctxLock = Any()
    /** Compte les oublis : une traduction qui était en cours pendant que la page changeait n'ajoute rien à la mémoire du nouveau chapitre. */
    private var ctxEpoch = 0
    private val history = ArrayDeque<Pair<String, String>>()
    private val memory = ChapterMemory(isCommonWord = { w -> lexicon()?.isCommon(w) == true })

    private fun recentDialogue(): List<Pair<String, String>> = synchronized(ctxLock) { history.toList() }
    private fun keptNames(): List<String> = synchronized(ctxLock) { memory.namesToKeep() }

    /**
     * Noms à garder tels quels pour les lignes [texts] : ceux du chapitre, plus (prompt 5, vers le français) ceux de l'œuvre et de son synopsis que la page contient — un nom connu
     * depuis les chapitres précédents (« Ain », « Eugene ») n'est plus traduit ni « corrigé » au début d'un nouveau chapitre.
     */
    private fun namesFor(texts: List<String>, target: String): List<String> {
        val kept = keptNames()
        if (promptVariant < 5 || target != "fr") return kept
        val lex = try { lexicon() } catch (_: Throwable) { null }
        val fromSeries = try { series?.namesOnPage(texts, lex) ?: emptyList() } catch (_: Throwable) { emptyList() }
        val page = texts.joinToString(" ").lowercase()
        val fromPremise = SeriesMemory.premiseNames(seriesPremise, lex).filter { it.length >= 3 && Regex("(?<![\\p{L}])" + Regex.escape(it) + "(?![\\p{L}])").containsMatchIn(page) }
            .map { it.replaceFirstChar { c -> c.uppercase() } }
        val seen = HashSet<String>()
        return (kept + fromSeries + fromPremise).filter { seen.add(it.lowercase()) }.take(18)
    }
    /** Ce que le chapitre a établi (tutoiement, genre du narrateur) quand c'est net : rappelé au modèle, en français seulement. */
    private fun styleNotes(target: String): List<String> = if (target == "fr" && promptVariant >= 4) synchronized(ctxLock) { memory.styleHints() } else emptyList()

    /** Noms propres repérés dans le chapitre (minuscules) : le réparateur de texte lu ne doit pas les « corriger ». */
    fun chapterNames(): Set<String> = keptNames().map { it.lowercase() }.toSet()

    /** Nouveau chapitre ou nouvelle page web : on oublie le dialogue précédent. N'attend jamais la fin d'une traduction. */
    fun resetContext() { synchronized(ctxLock) { ctxEpoch++; history.clear(); memory.reset() } }

    // ---------- mesures (journal) ----------

    /**
     * Mesure seulement, sans effet sur la traduction : appels au modèle faits par ce fil pour sa dernière traduction, un par entrée « type:durée ms/c création de la conversation ms/i caractères envoyés/o caractères reçus »
     * (types : ask = le groupe, miss = une ligne refaite seule, fix = une ligne corrigée après contrôle, refine = relecture).
     */
    private val callLog = ThreadLocal<ArrayList<String>>()
    fun beginCallLog() { callLog.set(ArrayList()) }
    fun addCallNote(note: String) { try { callLog.get()?.add(note) } catch (_: Throwable) {} }
    fun lastCallDetail(): String? = try { callLog.get()?.joinToString(";") } catch (_: Throwable) { null }
    private fun note(kind: String, startNs: Long, setupMs: Long, inChars: Int, outChars: Int) =
        addCallNote(kind + ":" + ((System.nanoTime() - startNs) / 1_000_000L) + "/c" + setupMs + "/i" + inChars + "/o" + outChars)

    /** Dernière réponse brute du modèle (journal des réponses fautives). */
    private var lastReply = ""

    // ---------- traduction ----------

    /** Traduit les lignes d'une page ; [refinePass] : seconde passe de relecture (réglage). */
    fun translate(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>, refinePass: Boolean = false): List<String> {
        val epoch = synchronized(ctxLock) { ctxEpoch }
        var out = ask(texts, source, target, glossary)
        out = realign(texts, out, source, target, glossary)
        var final = out.mapIndexed { i, t -> t ?: throw IllegalStateException("Réponse du modèle incomplète (ligne ${i + 1})") }
        // défauts de génération (chiffre doublé, mot-outil doublé) : corrigés avant les contrôles, qui n'ont plus à refaire ces lignes
        if (promptVariant >= 5) final = final.mapIndexed { i, t -> TranslationPost.doubledWords(texts[i], TranslationPost.numbers(texts[i], t)).let { if (target == "fr") TranslationPost.grammar(texts[i], it) else it } }
        // page anglaise : une longue suite coréenne, japonaise ou chinoise sans lettre latine (crédits, titre d'origine) reste telle quelle ; le modèle y inventait une phrase
        if (promptVariant >= 5 && source == "en") final = final.mapIndexed { i, t -> if (foreignBlock(texts[i])) texts[i] else t }
        if ((refine || refinePass) && target == "fr") final = refineDraft(texts, final, glossary)
        final = checkAndFix(texts, final, target, source, glossary)
        if (target == "fr" && promptVariant >= 4) final = final.mapIndexed { i, t -> TranslationPost.rescueStutter(texts[i], TranslationPost.stutter(texts[i], t)) }   // « B-BUT » → « M-MAIS » ; « Y-YOU… » recopié → « T-TOI… »
        // la page a changé pendant la traduction (le contexte a été oublié) : ce dialogue n'appartient plus au chapitre en cours
        synchronized(ctxLock) {
            if (epoch == ctxEpoch) {
                texts.indices.forEach { remember(texts[it], final[it]); memory.observe(texts[it], final[it]) }
                if (target == "fr") try { series?.observePage(texts, final, memory.namesToKeep(), lexicon()) } catch (_: Throwable) {}
            }
        }
        return final
    }

    private fun foreignBlock(src: String): Boolean =
        src.none { it in 'A'..'Z' || it in 'a'..'z' } && src.count { Character.UnicodeScript.of(it.code).let { sc -> sc == Character.UnicodeScript.HANGUL || sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA || sc == Character.UnicodeScript.KATAKANA } } >= 8

    private fun remember(src: String, tgt: String) {
        history.addLast(src to tgt)
        while (history.size > HISTORY_MAX) history.removeFirst()
    }

    /** Dialogue précédent donné au modèle : 10 dernières répliques (prompt 4), 16 à partir du prompt 5 (la relecture des consignes coûte peu, le contexte aide les fragments). */
    private fun historyFor(): List<Pair<String, String>> = recentDialogue().takeLast(if (promptVariant >= 5) 16 else HISTORY_SIZE)

    /**
     * Lignes absentes de la réponse ou mal placées (le modèle a sauté une ligne et décalé les suivantes, ou mis la traduction d'une ligne sous le numéro d'une autre : voir
     * [TranslationAlign]) : elles sont refaites ensemble, avec les autres lignes du lot comme contexte ; ce qui reste absent ou suspect est refait seul. La réponse brute fautive va au journal.
     */
    private fun realign(texts: List<String>, first: MutableList<String?>, source: String, target: String, glossary: List<GlossaryEntry>): MutableList<String?> {
        val out = first
        val lex = try { lexicon() } catch (_: Throwable) { null }
        val names = chapterNames()
        fun bad(within: Set<Int>? = null): java.util.SortedSet<Int> {
            val s = (out.indices.filter { out[it] == null } + try { TranslationAlign.suspects(texts, out, lex, names) } catch (_: Throwable) { emptySet() }).toSortedSet()
            return if (within == null) s else s.filter { it in within }.toSortedSet()
        }
        val redo = bad()
        if (redo.isEmpty()) return out
        addCallNote("realign:${redo.size}/${texts.size}")
        addCallNote("raw:" + lastReply.take(600).replace('\n', '⏎').replace(';', ','))
        val context = texts.indices.filter { it !in redo && out[it] != null }.map { texts[it] to out[it]!! }
        val again = try { ask(redo.map { texts[it] }, source, target, glossary, kind = "realign", page = context) } catch (e: Throwable) { null }
        redo.forEachIndexed { k, i -> out[i] = again?.getOrNull(k) }
        for (i in bad(redo)) out[i] = try { ask(listOf(texts[i]), source, target, glossary, kind = "miss").firstOrNull() } catch (e: Throwable) { null }
        // contrôle final de toute la page : une ligne laissée telle quelle peut maintenant se révéler mal placée (même traduction qu'une ligne refaite) ; refaite seule (quatre au plus)
        val late = bad().filter { it !in redo }.take(4)
        if (late.isNotEmpty()) addCallNote("late:${late.size}")
        for (i in late) out[i] = (try { ask(listOf(texts[i]), source, target, glossary, kind = "miss").firstOrNull() } catch (e: Throwable) { null }) ?: out[i]
        return out
    }

    /** Seconde passe : un « éditeur » corrige les contresens et le style littéral du brouillon. En cas de réponse invalide, le brouillon est conservé. */
    private fun refineDraft(texts: List<String>, draft: List<String>, glossary: List<GlossaryEntry>): List<String> {
        val t0 = System.nanoTime(); var setup = 0L; var inLen = 0; var outLen = -1
        return try {
            val system = LlmPrompt.refineSystem()
            val u = LlmPrompt.refineUser(texts, draft, recentDialogue(), glossary, seriesTitle, seriesPremise, keptNames(), styleNotes("fr"))
            inLen = system.length + u.length
            val r = backend.generate(system, u, LlmSampling(20, 0.9, 0.15), LlmPrompt.maxOutputTokens(texts))
            setup = r.setupMs; outLen = r.text.length
            // une ligne relue vide, ou qui ne correspond plus à son texte (relecture décalée), garde le brouillon
            val edited = LlmPrompt.parse(r.text, texts.size, texts).mapIndexed { i, t -> t?.let { LlmPrompt.stripRefineLabel(it) }?.takeIf { it.isNotBlank() } ?: draft[i] }
            val shifted = try { TranslationAlign.suspects(texts, edited, lexicon(), chapterNames()) } catch (_: Throwable) { emptySet() }
            edited.mapIndexed { i, t -> if (i in shifted) draft[i] else t }
        } catch (e: Throwable) {
            draft
        } finally { note("refine", t0, setup, inLen, outLen) }
    }

    /**
     * Contrôle chaque ligne (mots anglais restants, contenu perdu, nom modifié, chiffres, répétition…) et refait seule toute ligne qui échoue,
     * une fois, avec le détail des problèmes. La nouvelle version n'est retenue que si elle a moins de problèmes.
     */
    private fun checkAndFix(texts: List<String>, draft: List<String>, target: String, source: String, glossary: List<GlossaryEntry>): List<String> {
        val names = namesFor(texts, target)
        // tutoiement et genre du narrateur établis par le chapitre ; chaque ligne de la page y est ajoutée une fois finie, pour que la suivante s'y accorde
        val style = synchronized(ctxLock) { memory.styleCopy() }
        val out = draft.toMutableList(); val reports = ArrayList<LineReport>()
        for (i in out.indices) {
            var probs = TranslationChecks.problems(texts[i], out[i], target, names, style)
            var retried = false; var fixed = false
            if (probs.isNotEmpty()) {
                retried = true
                val again = try { askFix(texts[i], out[i], probs.map { "${it.code}: ${it.detail}" }, source, target, glossary, names, if (target == "fr" && promptVariant >= 4) style.hints() else emptyList()) } catch (e: Throwable) { null }
                if (again != null) {
                    val p2 = TranslationChecks.problems(texts[i], again, target, names, style)
                    if (p2.size < probs.size) { out[i] = again; fixed = p2.isEmpty(); probs = p2 }
                }
            }
            reports.add(LineReport(texts[i], out[i], probs.map { it.code }, retried, fixed))
            if (target == "fr") style.observe(texts[i], out[i], names)
        }
        lastReport = reports
        return out
    }

    private fun askFix(text: String, previous: String, problems: List<String>, source: String, target: String, glossary: List<GlossaryEntry>, names: List<String>, styleHints: List<String> = emptyList()): String? {
        val t0 = System.nanoTime(); var setup = 0L; var outLen = -1
        val system = LlmPrompt.system(source, target, promptVariant)
        var inLen = system.length
        try {
            val hints = if (promptVariant >= 4 && target == "fr") IdiomHints.find(listOf(text)) else emptyList()
            val gloss = if (promptVariant >= 5 && target == "fr") glossary + genreTerms(listOf(text), glossary).map { GlossaryEntry(it.source, it.french) } else glossary
            val u = LlmPrompt.fixUser(text, previous, problems, historyFor(), gloss, names, hints, styleHints)
            inLen += u.length
            val r = backend.generate(system, u, LlmSampling(20, 0.9, 0.1), LlmPrompt.maxOutputTokens(listOf(text)))
            setup = r.setupMs; outLen = r.text.length
            return LlmPrompt.parse(r.text, 1, listOf(text)).firstOrNull()
        } finally { note("fix", t0, setup, inLen, outLen) }
    }

    private fun ask(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>, series: String? = seriesTitle, kind: String = "ask", page: List<Pair<String, String>> = emptyList()): MutableList<String?> {
        val t0 = System.nanoTime(); var setup = 0L; var outLen = -1
        val system = LlmPrompt.system(source, target, promptVariant)
        var inLen = system.length
        try {
            val hints = if (promptVariant >= 4 && target == "fr") IdiomHints.find(texts) else emptyList()
            val u = if (promptVariant >= 5 && target == "fr") {
                val mem = this.series
                val notes = try { mem?.promptNotes(texts, lexicon(), seriesPremise) ?: emptyList() } catch (_: Throwable) { emptyList() }
                val examples = try { mem?.termExamples(texts, lexicon()) ?: emptyList() } catch (_: Throwable) { emptyList() }
                val terms = genreTerms(texts, glossary)
                fit(system, texts) { hist, withNotes ->
                    LlmPrompt.user(texts, hist, glossary, series, namesFor(texts, target), hints, styleNotes(target), page, seriesPremise, if (withNotes) notes else emptyList(), terms, if (withNotes) examples else emptyList())
                }
            } else LlmPrompt.user(texts, historyFor(), glossary, series, keptNames(), hints, styleNotes(target), page, if (target == "fr") seriesPremise else null)
            inLen += u.length
            val r = backend.generate(system, u, LlmSampling(20, 0.9, 0.15), LlmPrompt.maxOutputTokens(texts))
            setup = r.setupMs; outLen = r.text.length
            val reply = r.text
            lastReply = reply
            // le modèle recopie parfois la ligne d'origine devant sa traduction (« SOURCE| TRADUCTION ») : la recopie est retirée, et comptée au journal (détail des appels : « echo:2/3 »)
            val raw = LlmPrompt.parse(reply, texts.size)
            val parsed = LlmPrompt.parse(reply, texts.size, texts)
            val echoes = raw.indices.count { raw[it] != parsed[it] }
            if (echoes > 0) addCallNote("echo:$echoes/${texts.size}")
            // ligne sautée (fragment incompris) et suivantes renumérotées : replacées, la ligne sautée sera traduite à part (voir TranslationAlign.repairSkip)
            val repaired = try { TranslationAlign.repairSkip(texts, parsed, lexicon(), chapterNames()) } catch (_: Throwable) { null }
            if (repaired != null) addCallNote("skip:" + (repaired.indexOfFirst { it == null } + 1) + "/" + texts.size)
            return (repaired ?: parsed).toMutableList()
        } finally { note(kind, t0, setup, inLen, outLen) }
    }

    /** Termes d'usage du genre de l'œuvre présents dans [texts] ; un terme que le glossaire de l'utilisateur fixe déjà n'est pas répété. */
    private fun genreTerms(texts: List<String>, glossary: List<GlossaryEntry>): List<GenrePacks.Hit> {
        val ids = try { series?.genres(seriesTitle, seriesPremise) ?: GenrePacks.detect(emptyMap(), seriesTitle, seriesPremise) } catch (_: Throwable) { emptySet() }
        val user = glossary.map { it.source.lowercase() }.toSet()
        return GenrePacks.find(ids, texts).filter { it.source !in user }
    }

    /**
     * Message qui tient dans la fenêtre du moteur avec la réponse attendue : tout le dialogue précédent si possible, sinon de moins en moins (les répliques les plus anciennes partent
     * d'abord), puis sans les notes de l'œuvre. Estimation prudente des jetons (voir [CHARS_PER_TOKEN]).
     */
    private fun fit(system: String, texts: List<String>, build: (List<Pair<String, String>>, Boolean) -> String): String {
        val budget = contextTokens - LlmPrompt.maxOutputTokens(texts) - 64
        val hist = historyFor()
        fun tokens(u: String) = ((system.length + u.length) / CHARS_PER_TOKEN).toInt()
        val sizes = ((hist.size downTo 0 step 2) + 0).distinct()
        for (withNotes in listOf(true, false)) for (n in sizes) {
            val u = build(hist.takeLast(n), withNotes)
            if (tokens(u) <= budget) { if (n < hist.size || !withNotes) addCallNote("ctx:${n}/${hist.size}" + (if (withNotes) "" else ",-notes")); return u }
        }
        addCallNote("ctx:over")
        return build(emptyList(), false)
    }

    companion object {
        const val HISTORY_SIZE = 10
        private const val HISTORY_MAX = 16
        /** Jetons estimés par caractère (anglais et français mêlés, consignes comprises) : estimation prudente. */
        private const val CHARS_PER_TOKEN = 3.0
    }
}
