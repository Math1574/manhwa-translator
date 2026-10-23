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
) {
    fun process(imageBytes: ByteArray, source: String, target: String): PageOutcome {
        val key = CacheKey(sha256Hex(imageBytes), source, target, engineId)
        if (useCache) cache.get(key)?.let { return PageOutcome.Done(it, fromCache = true) }

        val ocrResult = try { ocr.recognize(imageBytes) } catch (e: Exception) {
            return PageOutcome.Failed("ocr", e.message ?: e.javaClass.simpleName)
        }
        val grouped = grouper.group(ocrResult.lines)
        val blocks = grouped.filter { !TextNormalizer.isNoise(it.text) }
        reportDebug(ocrResult, grouped, blocks, emptySet())
        if (blocks.isEmpty()) return PageOutcome.NoText(ocrResult.imageWidth, ocrResult.imageHeight)

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
                if (llm) Protected(blocks[it].text, emptyMap())
                else g.protect(blocks[it].text).let { p -> p.copy(text = TextNormalizer.fixCase(p.text)) }
            }
            val terms = if (llm) g.terms().filter { t -> pending.any { blocks[it].text.contains(t.source) } } else emptyList()
            fun run(texts: List<String>): List<String> =
                if (llm) translator.translateWithGlossary(texts, source, target, terms) else translator.translate(texts, source, target)

            val batch = try { run(protectedTexts.map { it.text }) } catch (e: Exception) { lastError = e.message ?: e.javaClass.simpleName; null }
            if (batch != null && batch.size == pending.size) {
                pending.forEachIndexed { k, i -> translated[i] = finish(g, protectedTexts[k], batch[k], blocks[i].text, source, target) }
            } else {
                // le lot a échoué : on retente bloc par bloc pour ne perdre que les blocs réellement en erreur
                pending.forEachIndexed { k, i ->
                    try {
                        val out = run(listOf(protectedTexts[k].text)).single()
                        translated[i] = finish(g, protectedTexts[k], out, blocks[i].text, source, target)
                    } catch (e: Exception) { errors++; lastError = e.message ?: e.javaClass.simpleName }
                }
            }
        }
        if (translated.all { it == null }) return PageOutcome.Failed("translation", lastError.ifEmpty { "aucune traduction produite" })

        val failedIds = blocks.indices.filter { translated[it] == null }.map { blocks[it].id }.toSet()
        if (failedIds.isNotEmpty()) reportDebug(ocrResult, grouped, blocks, failedIds)

        val out = blocks.mapIndexedNotNull { i, b ->
            translated[i]?.let { TranslatedBlock(b.id, b.box, b.text, it, b.vertical, medianLineHeight(b)) }
        }
        val result = PageResult(ocrResult.imageWidth, ocrResult.imageHeight, out)
        if (errors == 0) cache.put(key, result) // une page partielle n'est pas mise en cache
        return PageOutcome.Done(result, fromCache = false)
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
        val hs = b.lines.map { if (b.vertical) it.box.width else it.box.height }.sorted()
        return if (hs.isEmpty()) 0f else hs[hs.size / 2]
    }

    private fun finish(g: Glossary, p: Protected, out: String, original: String, source: String, target: String): String {
        if (p.tokens.isEmpty()) return out
        g.restore(out, p)?.let { return it }
        return try { translator.translate(listOf(original), source, target).single() } catch (e: Exception) { out }
    }
}
