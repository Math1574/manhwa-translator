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
    data class Done(val result: PageResult, val fromCache: Boolean) : PageOutcome()
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
    private val junk: (text: String, relativeHeight: Float) -> Boolean = { _, _ -> false },
    /** Répare les erreurs de lecture avant traduction (mots collés, chiffres ou minuscules parasites). */
    private val repair: (String) -> String = { it },
    /** Relit les blocs douteux avec un modèle vision-langage (null = désactivé). */
    private val reader: BlockReader? = null,
    /** Part des mots d'une lecture qui sont de vrais mots (0..1) : sert à repérer les blocs douteux et à vérifier les relectures. */
    private val plausibility: (String) -> Float = { 1f },
    /** Journal de diagnostic (relectures proposées, acceptées ou refusées). */
    private val log: (String) -> Unit = {},
    private val maxReread: Int = 4,
) {
    fun process(imageBytes: ByteArray, source: String, target: String): PageOutcome {
        val key = CacheKey(sha256Hex(imageBytes), source, target, engineId)
        if (useCache) cache.get(key)?.let { return PageOutcome.Done(it, fromCache = true) }

        val ocrResult = try { ocr.recognize(imageBytes) } catch (e: Exception) {
            return PageOutcome.Failed("ocr", e.message ?: e.javaClass.simpleName)
        }
        val grouped = grouper.group(ocrResult.lines, ocrResult.detections)
        // hauteur de ligne relative à la médiane de la page : un gros lettrage (onomatopée, titre) n'est jamais un débris
        val heights = grouped.map { medianLineHeight(it) }.filter { it > 0f }.sorted()
        val medianH = if (heights.isEmpty()) 0f else heights[heights.size / 2]
        val blocks = grouped.filter { !TextNormalizer.isNoise(it.text) && !junk(it.text, if (medianH > 0f) medianLineHeight(it) / medianH else 1f) }
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
            translated[i]?.let { TranslatedBlock(b.id, b.box, b.text, it, b.vertical, medianLineHeight(b), b.lines.map { l -> l.box }, b.lines.map { l -> l.text }, if (clean[i] != b.text) clean[i] else "", b.kind.name.takeIf { b.kind != BlockKind.UNKNOWN } ?: "", b.container, b.zone) }
        }
        val result = PageResult(ocrResult.imageWidth, ocrResult.imageHeight, out)
        if (errors == 0) cache.put(key, result) // une page partielle n'est pas mise en cache
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
