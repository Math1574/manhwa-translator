package app.manhwa.core

/**
 * Retraduction ciblée d'une page déjà traduite quand des termes ont été ajoutés au glossaire depuis : seuls les textes qui contiennent un terme récent sont retraduits ;
 * les autres blocs de la page gardent leur traduction telle quelle (rien ne change là où le terme n'intervient pas).
 *
 * Les étapes sont celles de [PagePipeline.process] pour un bloc : protection des termes par des jetons (ML Kit) ou glossaire dans la consigne (modèle de langage), traduction,
 * restitution des termes, casse du texte d'origine.
 */
class TermRefresh(private val translator: TranslationEngine) {

    /**
     * Retraduit les blocs [indices] avec le glossaire [g] : nouvelle traduction par indice. Un bloc dont la traduction échoue n'y figure pas (l'appelant garde l'ancienne) ;
     * si le lot échoue, chaque bloc est retenté seul pour ne perdre que ceux qui sont réellement en erreur.
     */
    fun retranslate(result: PageResult, indices: List<Int>, g: Glossary, source: String, target: String): Map<Int, String> {
        if (indices.isEmpty()) return emptyMap()
        val clean = indices.map { i -> result.blocks[i].let { it.repaired.ifEmpty { it.source } } }
        val llm = translator.usesGlossaryPrompt()
        val prot = clean.map { c ->
            if (llm) Protected(TextNormalizer.fixCase(c), emptyMap())
            else g.protect(c).let { p -> p.copy(text = TextNormalizer.fixCase(p.text)) }
        }
        val terms = if (llm) g.terms().filter { t -> clean.any { it.contains(t.source, ignoreCase = true) } } else emptyList()
        fun run(texts: List<String>): List<String> =
            if (llm) translator.translateWithGlossary(texts, source, target, terms) else translator.translate(texts, source, target)

        val out = LinkedHashMap<Int, String>()
        val batch = try { run(prot.map { it.text }) } catch (e: Exception) { null }
        if (batch != null && batch.size == indices.size) {
            indices.forEachIndexed { k, i -> out[i] = TextNormalizer.matchCase(clean[k], finish(g, prot[k], batch[k], clean[k], source, target)) }
        } else {
            indices.forEachIndexed { k, i ->
                try {
                    val one = run(listOf(prot[k].text)).single()
                    out[i] = TextNormalizer.matchCase(clean[k], finish(g, prot[k], one, clean[k], source, target))
                } catch (e: Exception) { /* ce bloc garde sa traduction actuelle */ }
            }
        }
        return out
    }

    private fun finish(g: Glossary, p: Protected, out: String, original: String, source: String, target: String): String {
        if (p.tokens.isEmpty()) return out
        g.restore(out, p)?.let { return it }
        return try { translator.translate(listOf(original), source, target).single() } catch (e: Exception) { out }
    }

    companion object {
        /**
         * Indices (dans [PageResult.blocks]) des blocs que les termes de [g] plus récents que [sinceRev] concernent : le texte lu contient le terme et la traduction actuelle ne
         * contient pas encore sa traduction. Une correction exacte de ce texte garde la priorité (elle n'est pas retraduite), de même que les blocs que [skip] écarte
         * (traduction saisie à la main, texte ignoré…).
         */
        fun concerned(result: PageResult, g: Glossary, sinceRev: Long, skip: (TranslatedBlock) -> Boolean = { false }): List<Int> {
            val newer = g.termsNewerThan(sinceRev)
            if (newer.isEmpty()) return emptyList()
            return result.blocks.indices.filter { i ->
                val b = result.blocks[i]
                val text = b.repaired.ifEmpty { b.source }
                !skip(b) && g.correctionFor(b.source) == null && newer.any { t -> text.contains(t.source, ignoreCase = true) && !b.translated.contains(t.target) }
            }
        }

        /** La page avec les nouvelles traductions des blocs retraduits ([changes] : indice → traduction). */
        fun withTranslations(result: PageResult, changes: Map<Int, String>): PageResult =
            result.copy(blocks = result.blocks.mapIndexed { i, b -> changes[i]?.let { b.copy(translated = it) } ?: b })
    }
}
