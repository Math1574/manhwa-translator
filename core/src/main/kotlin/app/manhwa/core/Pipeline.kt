package app.manhwa.core

fun interface OcrEngine { fun recognize(imageBytes: ByteArray): OcrResult }

fun interface TranslationEngine {
    /** Doit renvoyer autant de textes que d'entrées, dans le même ordre. */
    fun translate(texts: List<String>, source: String, target: String): List<String>
}

sealed class PageOutcome {
    data class Done(val result: PageResult, val fromCache: Boolean) : PageOutcome()
    /** Aucune zone de texte détectée : ce n'est pas une erreur, rien à afficher. */
    data class NoText(val imageWidth: Int, val imageHeight: Int) : PageOutcome()
    data class Failed(val stage: String, val message: String) : PageOutcome()
}

class PagePipeline(
    private val cache: ResultCache,
    private val ocr: OcrEngine,
    private val translator: TranslationEngine,
    private val engineId: String,
    private val glossary: () -> Glossary = { Glossary() },
    private val grouper: BlockGrouper = BlockGrouper(),
) {
    fun process(imageBytes: ByteArray, source: String, target: String): PageOutcome {
        val key = CacheKey(sha256Hex(imageBytes), source, target, engineId)
        cache.get(key)?.let { return PageOutcome.Done(it, fromCache = true) }

        val ocrResult = try { ocr.recognize(imageBytes) } catch (e: Exception) {
            return PageOutcome.Failed("ocr", e.message ?: e.javaClass.simpleName)
        }
        val blocks = grouper.group(ocrResult.lines).filter { !TextNormalizer.isNoise(it.text) }
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
            val protectedTexts = pending.map { val p = g.protect(blocks[it].text); p.copy(text = TextNormalizer.fixCase(p.text)) }
            val batch = try { translator.translate(protectedTexts.map { it.text }, source, target) } catch (e: Exception) { null }
            if (batch != null && batch.size == pending.size) {
                pending.forEachIndexed { k, i -> translated[i] = finish(g, protectedTexts[k], batch[k], blocks[i].text, source, target) }
            } else {
                // le lot a échoué : on retente bloc par bloc pour ne perdre que les blocs réellement en erreur
                pending.forEachIndexed { k, i ->
                    try {
                        val out = translator.translate(listOf(protectedTexts[k].text), source, target).single()
                        translated[i] = finish(g, protectedTexts[k], out, blocks[i].text, source, target)
                    } catch (e: Exception) { errors++; lastError = e.message ?: e.javaClass.simpleName }
                }
            }
        }
        if (translated.all { it == null }) return PageOutcome.Failed("translation", lastError.ifEmpty { "aucune traduction produite" })

        val out = blocks.mapIndexedNotNull { i, b ->
            translated[i]?.let { TranslatedBlock(b.id, b.box, b.text, it, b.vertical, medianLineHeight(b)) }
        }
        val result = PageResult(ocrResult.imageWidth, ocrResult.imageHeight, out)
        if (errors == 0) cache.put(key, result) // une page partielle n'est pas mise en cache
        return PageOutcome.Done(result, fromCache = false)
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
