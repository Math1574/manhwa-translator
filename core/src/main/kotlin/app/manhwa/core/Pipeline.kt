package app.manhwa.core

fun interface OcrEngine { fun recognize(imageBytes: ByteArray): OcrResult }

fun interface TranslationEngine {
    /** Doit renvoyer autant de textes que d'entrées, dans le même ordre. */
    fun translate(texts: List<String>, source: String, target: String): List<String>

    /** Vrai si le moteur exploite lui-même le glossaire et le contexte (modèle de langage) : pas de jetons de protection. */
    fun usesGlossaryPrompt(): Boolean = false

    fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> =
        translate(texts, source, target)
}

sealed class PageOutcome {
    /** [refreshed] : nombre de textes retraduits parce que des termes du glossaire ajoutés depuis les concernaient (page reprise du cache). */
    data class Done(val result: PageResult, val fromCache: Boolean, val refreshed: Int = 0) : PageOutcome()
    /** Aucune zone de texte détectée : ce n'est pas une erreur, rien à afficher. */
    data class NoText(val imageWidth: Int, val imageHeight: Int) : PageOutcome()
    data class Failed(val stage: String, val message: String) : PageOutcome()
}

/** Une ligne détectée par l'OCR avec son sort : « gardée » ou le motif de rejet (mode diagnostic). */
data class DebugLine(val box: RectF, val text: String, val confidence: Float, val status: String)
data class DebugInfo(val imageWidth: Int, val imageHeight: Int, val lines: List<DebugLine>)

class PagePipeline(
    private val cache: ResultCache,
    private val ocr: OcrEngine,
    private val translator: TranslationEngine,
    private val engineId: String,
    private val glossary: () -> Glossary = { Glossary() },
    private val grouper: BlockGrouper = BlockGrouper(),
    private val useCache: Boolean = true,
    private val debugSink: ((DebugInfo) -> Unit)? = null,
    /** Vrai pour une ligne à ignorer (filigrane, logo, débris de lecture sur le dessin). */
    /** Texte à écarter ? (texte, hauteur relative à la médiane de la page, bloc ancré par le détecteur : zone ou bulle). */
    private val junk: (text: String, relativeHeight: Float, anchored: Boolean) -> Boolean = { _, _, _ -> false },
    /** Répare les erreurs de lecture avant traduction (mots collés, chiffres ou minuscules parasites). */
    private val repair: (String) -> String = { it },
    /** Débris collé en fin de texte (voir OcrRepair.junkTail) : sa boîte ne fait pas partie du bloc, sans quoi la boîte du bloc déborde de la bulle sur le texte voisin. */
    private val junkTail: (String) -> String = { "" },
    /** Onomatopée à laisser telle quelle ? (texte, nature du bloc, hauteur relative à la médiane de la page). */
    private val isSfx: (text: String, kind: BlockKind, relativeHeight: Float) -> Boolean = { _, _, _ -> false },
    /** Relit les blocs douteux avec un modèle vision-langage (null = désactivé). */
    private val reader: BlockReader? = null,
    /** Part des mots d'une lecture qui sont de vrais mots (0..1) : sert à repérer les blocs douteux et à vérifier les relectures. */
    private val plausibility: (String) -> Float = { 1f },
    /** Journal de diagnostic (relectures proposées, acceptées ou refusées). */
    private val log: (String) -> Unit = {},
    private val maxReread: Int = 4,
    /** Ce bloc revient-il à cette image ? (jonctions d'images empilées : un texte coupé est traduit par une seule des deux images voisines). */
    private val owns: (TextBlock) -> Boolean = { true },
) {
    /**
     * Retire d'un bloc les lignes (ou la fin de la dernière ligne) qui ne sont que le [tail] de débris collé en fin de texte : « …SI ÉLEVÉ ?! Taay sr4: In ) » est la dernière ligne d'une bulle
     * suivie de caractères de la fenêtre holographique voisine, lus sur la même ligne. Leur boîte étirait celle du bloc jusque sur le dessin, et la traduction débordait de la bulle.
     */
    private fun withoutJunkTail(b: TextBlock, tail: String): TextBlock {
        val t = tail.trim()
        if (t.isEmpty() || b.vertical || b.lines.isEmpty()) return b
        var remaining = t.length
        val lines = b.lines.toMutableList()
        while (remaining > 0 && lines.isNotEmpty()) {
            val l = lines.last(); val lt = l.text.trim()
            if (lt.isEmpty() || lt.length <= remaining + 1) { remaining -= lt.length + 1; lines.removeAt(lines.size - 1) }
            else {
                val keep = lt.length - remaining
                val box = RectF(l.box.left, l.box.top, l.box.left + l.box.width * keep / lt.length, l.box.bottom)
                lines[lines.size - 1] = l.copy(text = lt.substring(0, keep).trimEnd(), box = box); remaining = 0
            }
        }
        if (lines.isEmpty() || lines.size == b.lines.size && lines.last() == b.lines.last()) return b
        return b.copy(lines = lines, box = lines.map { it.box }.reduce { x, y -> x.union(y) }, text = lines.joinToString(" ") { it.text })
    }

    /** Vrai si presque tout le texte de la page est en majuscules (au moins trois blocs de 8 lettres ou plus, 70 % sans aucune minuscule). */
    private fun isAllCapsPage(blocks: List<TextBlock>): Boolean {
        val big = blocks.filter { b -> b.text.count { it.isLetter() } >= 8 }
        if (big.size < 3) return false
        return big.count { b -> b.text.none { it.isLowerCase() } } >= 0.7 * big.size
    }

    /** Fragment de une à trois lettres, en minuscules seulement (jamais « I », « OK » ou un mot en majuscules). */
    private fun isLowercaseFragment(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length in 1..3 && letters.all { it.isLowerCase() } && ScriptDetector.dominant(text) == "en"
    }

    private val refresher = TermRefresh(translator)

    /**
     * Page reprise du cache : si des termes ont été ajoutés au glossaire depuis sa traduction, seuls les textes qui les contiennent sont retraduits (les autres blocs ne changent pas),
     * puis la page est remise à jour dans le cache avec la version actuelle du glossaire. Une retraduction qui échoue laisse la page telle quelle : elle sera retentée à l'affichage suivant.
     */
    private fun refreshFromCache(key: CacheKey, cached: PageResult, source: String, target: String): PageOutcome.Done {
        val g = glossary()
        val since = cache.revisionOf(key)
        val idx = TermRefresh.concerned(cached, g, since)
        if (idx.isEmpty()) {
            if (since < g.revision) cache.put(key, cached, g.revision)      // à jour pour ce glossaire : la prochaine vérification part de là
            return PageOutcome.Done(cached, fromCache = true)
        }
        val changes = try { refresher.retranslate(cached, idx, g, source, target) } catch (e: Exception) { emptyMap() }
        if (changes.isEmpty()) { log("glossaire : ${idx.size} texte(s) à retraduire, aucun n'a pu l'être (page gardée telle quelle)"); return PageOutcome.Done(cached, fromCache = true) }
        val updated = TermRefresh.withTranslations(cached, changes)
        cache.put(key, updated, if (changes.size == idx.size) g.revision else since)
        log("glossaire : ${changes.size} texte(s) retraduit(s) sur ${cached.blocks.size}")
        return PageOutcome.Done(updated, fromCache = true, refreshed = changes.size)
    }

    fun process(imageBytes: ByteArray, source: String, target: String): PageOutcome {
        val key = CacheKey(sha256Hex(imageBytes), source, target, engineId)
        if (useCache) cache.get(key)?.let { return refreshFromCache(key, it, source, target) }

        val ocrResult = try { ocr.recognize(imageBytes) } catch (e: Exception) {
            return PageOutcome.Failed("ocr", e.message ?: e.javaClass.simpleName)
        }
        val grouped0 = grouper.group(ocrResult.lines, ocrResult.detections)
        // hauteur de ligne relative à la médiane de la page : un gros lettrage (onomatopée, titre) n'est jamais un débris
        val heights = grouped0.map { medianLineHeight(it) }.filter { it > 0f }.sorted()
        val medianH = if (heights.isEmpty()) 0f else heights[heights.size / 2]
        fun relH(b: TextBlock) = if (medianH > 0f) medianLineHeight(b) / medianH else 1f
        // une onomatopée collée à une phrase voisine (même bloc) : sa ligne est retirée du bloc, qui garde le reste ; l'image n'y touche pas
        val grouped = grouped0.mapNotNull { b -> withoutSfxLines(b, medianH) }
        // page en majuscules : un petit fragment en minuscules (« in » lu dans une marque du dessin) n'est pas du texte de la page ; traduit avec ses voisins, il en absorbait le début de phrase
        val capsPage = isAllCapsPage(grouped)
        val blocks = grouped.filter { owns(it) && !TextNormalizer.isNoise(it.text) && !(capsPage && isLowercaseFragment(it.text)) && !junk(it.text, relH(it), it.kind != BlockKind.FREE || it.zone != null) && !isSfx(it.text, it.kind, relH(it)) }
        for (g in grouped) if (g !in blocks && !owns(g)) log("écarté « ${g.text.take(40)} » (traduit par l'image voisine, de l'autre côté de la jonction)")
        for (g in grouped) if (g !in blocks && owns(g)) log("écarté « ${g.text.take(40)} » (${if (TextNormalizer.isNoise(g.text)) "bruit" else if (capsPage && isLowercaseFragment(g.text)) "fragment en minuscules sur une page en majuscules" else if (isSfx(g.text, g.kind, relH(g))) "onomatopée laissée telle quelle" else "débris, filigrane ou répété"}${if (g.kind != BlockKind.IN_BUBBLE && g.zone == null) ", hors zone" else ""})")
        val clean = blocks.map { repair(it.text) }.toMutableList()
        reportDebug(ocrResult, grouped, blocks, emptySet())
        if (blocks.isEmpty()) return PageOutcome.NoText(ocrResult.imageWidth, ocrResult.imageHeight)
        reread(imageBytes, source, blocks, clean)

        val g = glossary()
        val translated = arrayOfNulls<String>(blocks.size)
        val pending = ArrayList<Int>()
        blocks.forEachIndexed { i, b ->
            val fix = g.correctionFor(b.text)
            if (fix != null) translated[i] = fix else pending.add(i)
        }

        var errors = 0
        var lastError = ""
        if (pending.isNotEmpty()) {
            val llm = translator.usesGlossaryPrompt()
            val protectedTexts = pending.map {
                if (llm) Protected(TextNormalizer.fixCase(clean[it]), emptyMap())
                else g.protect(clean[it]).let { p -> p.copy(text = TextNormalizer.fixCase(p.text)) }
            }
            val terms = if (llm) g.terms().filter { t -> pending.any { clean[it].contains(t.source, ignoreCase = true) } } else emptyList()
            fun run(texts: List<String>): List<String> =
                if (llm) translator.translateWithGlossary(texts, source, target, terms) else translator.translate(texts, source, target)

            val batch = try { run(protectedTexts.map { it.text }) } catch (e: Exception) { lastError = e.message ?: e.javaClass.simpleName; null }
            if (batch != null && batch.size == pending.size) {
                pending.forEachIndexed { k, i -> translated[i] = TextNormalizer.matchCase(clean[i], finish(g, protectedTexts[k], batch[k], clean[i], source, target)) }
            } else {
                // le lot a échoué : on retente bloc par bloc pour ne perdre que les blocs réellement en erreur
                pending.forEachIndexed { k, i ->
                    try {
                        val out = run(listOf(protectedTexts[k].text)).single()
                        translated[i] = TextNormalizer.matchCase(clean[i], finish(g, protectedTexts[k], out, clean[i], source, target))
                    } catch (e: Exception) { errors++; lastError = e.message ?: e.javaClass.simpleName }
                }
            }
        }
        if (translated.all { it == null }) return PageOutcome.Failed("translation", lastError.ifEmpty { "aucune traduction produite" })

        val failedIds = blocks.indices.filter { translated[it] == null }.map { blocks[it].id }.toSet()
        if (failedIds.isNotEmpty()) reportDebug(ocrResult, grouped, blocks, failedIds)

        val out = blocks.mapIndexedNotNull { i, b ->
            val bt = withoutJunkTail(b, junkTail(b.text))
            translated[i]?.let { TranslatedBlock(b.id, bt.box, b.text, it, b.vertical, medianLineHeight(b), bt.lines.map { l -> l.box }, bt.lines.map { l -> l.text }, if (clean[i] != b.text) clean[i] else "", b.kind.name.takeIf { b.kind != BlockKind.UNKNOWN } ?: "", b.container, b.zone) }
        }
        val result = PageResult(ocrResult.imageWidth, ocrResult.imageHeight, out)
        if (errors == 0) cache.put(key, result, g.revision) // une page partielle n'est pas mise en cache ; elle garde la version du glossaire avec laquelle elle a été traduite
        return PageOutcome.Done(result, fromCache = false)
    }

    /**
     * Relecture des blocs douteux (lecture incomplète ou absurde) : au plus [maxReread] par page, les plus suspects d'abord. Une relecture n'est retenue
     * que si elle passe la vérification (mots plausibles, appui des lectures des moteurs) : sinon on garde la lecture d'origine.
     */
    private fun reread(imageBytes: ByteArray, source: String, blocks: List<TextBlock>, clean: MutableList<String>) {
        val r = reader ?: return
        if (source != "en" || !r.available) return
        val suspects = blocks.indices.map { it to BlockSuspicion.assess(blocks[it], clean[it], plausibility) }.filter { it.second.suspect }.sortedByDescending { it.second.score }.take(maxReread)
        if (suspects.isEmpty()) return
        log("relecture : ${suspects.size} bloc(s) douteux sur ${blocks.size}")
        for ((i, sus) in suspects) {
            val read = try { r.reread(imageBytes, blocks[i], clean[i]) } catch (e: Throwable) { log("relecture en échec : ${e.message ?: e.javaClass.simpleName}"); null }
            if (read == null) { log("bloc ${blocks[i].id} : pas de relecture"); continue }
            val v = ReadingVerifier.verify(clean[i], read, sus, plausibility)
            log("bloc ${blocks[i].id} (${sus.reasons.joinToString(", ")}) : « ${clean[i].take(40)} » → « ${read.text.take(70)} » : ${v.reason}")
            if (v.accepted) clean[i] = v.text
        }
    }

    private fun reportDebug(ocr: OcrResult, grouped: List<TextBlock>, kept: List<TextBlock>, failedIds: Set<Int>) {
        val sink = debugSink ?: return
        val keptLines = HashMap<OcrLine, String>() // égalité par valeur : suffisant pour un diagnostic
        grouped.forEach { b ->
            val status = when {
                kept.none { it.id == b.id } -> "bruit"
                b.id in failedIds -> "échec traduction"
                else -> "gardée"
            }
            b.lines.forEach { keptLines[it] = status }
        }
        sink(DebugInfo(ocr.imageWidth, ocr.imageHeight, ocr.lines.map { l ->
            DebugLine(l.box, l.text, l.confidence, grouper.dropReason(l)?.let { "rejetée : $it" } ?: keptLines[l] ?: "doublon")
        }))
    }

    /** Retire d'un bloc de plusieurs lignes celles qui sont des onomatopées seules ; le bloc garde le reste (sans zone : la zone recouvre aussi le bruitage). */
    private fun withoutSfxLines(b: TextBlock, medianH: Float): TextBlock? {
        if (b.lines.size < 2 || b.kind == BlockKind.IN_BUBBLE) return b
        val keep = b.lines.filter { l -> !isSfx(l.text, b.kind, if (medianH > 0f) (if (b.vertical) l.box.width else l.lineHeight) / medianH else 1f) }
        if (keep.size == b.lines.size) return b
        if (keep.isEmpty()) return null
        val box = keep.map { it.box }.reduce { x, y -> x.union(y) }
        return b.copy(lines = keep, box = box, text = keep.joinToString(" ") { it.text }, zone = null)
    }

    private fun medianLineHeight(b: TextBlock): Float {
        val hs = b.lines.map { if (b.vertical) it.box.width else it.lineHeight }.sorted()
        return if (hs.isEmpty()) 0f else hs[hs.size / 2]
    }

    private fun finish(g: Glossary, p: Protected, out: String, original: String, source: String, target: String): String {
        if (p.tokens.isEmpty()) return out
        g.restore(out, p)?.let { return it }
        return try { translator.translate(listOf(original), source, target).single() } catch (e: Exception) { out }
    }
}
