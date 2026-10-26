package app.manhwa.core

/** Construction du prompt de traduction pour un modèle de langage local et lecture de sa réponse. */
object LlmPrompt {

    fun languageName(code: String): String = when (code.substringBefore('-')) {
        "ja" -> "Japanese"; "ko" -> "Korean"; "zh" -> "Chinese"; "en" -> "English"
        "fr" -> "French"; "es" -> "Spanish"; "de" -> "German"
        else -> "the source language (detect it)"
    }

    /** Version courante du prompt (le banc d'essai de traduction compare plusieurs versions). */
    const val CURRENT = 2

    fun system(source: String, target: String, variant: Int = CURRENT): String =
        if (variant == 1 || target != "fr") systemV1(source, target) else systemV2(source)

    private fun systemV1(source: String, target: String): String {
        val src = if (source == "auto") "the source language (Korean, Japanese, Chinese or English; detect it)" else languageName(source)
        val tgt = languageName(target)
        return """
You are an expert translator of comics (manga, manhwa, manhua, webtoons). You translate the dialogue of one page, given in reading order, from $src into $tgt.
Rules:
- Translate the MEANING naturally, the way a native $tgt speaker would say it in a comic. Never translate word for word. The result must sound like real speech.
- The lines come from OCR: they may contain recognition mistakes, be in capital letters, or be cut in the middle of a sentence. Use the surrounding lines and the previous dialogue to understand and fix them.
- Keep the tone, register and personality of each speaker. Keep names and terms consistent. Apply the glossary exactly.
- For a sound effect, give a short natural equivalent in $tgt, or keep it as is.
- Write normal sentence case with proper accents and punctuation, even if the source was in capital letters.
- Never add notes, explanations or quotation marks.
- Answer with exactly one line per input line, in the format `N| translation`, using the same numbers, and nothing else.
""".trim()
    }

    private fun systemV2(source: String): String {
        val src = if (source == "auto") "Korean, Japanese, Chinese or English (detect it)" else languageName(source)
        return """
You are a professional French translator of manhwa, manga, manhua and webtoons, working for a high-quality scanlation team. You translate the dialogue of one page (given in reading order) from $src into French, as published professionally.

Rules:
1. Translate the MEANING, never word for word. Rewrite each line as natural, idiomatic spoken French that a native would really say in that situation. Never copy the syntax of the source language. Natural examples:
   "I have no choice but to kill him." -> "Je n'ai pas d'autre choix que de le tuer."
   "Don't even think about it." -> "N'y pense même pas."
   "What do you want from me?" -> "Qu'est-ce que tu me veux ?"
   "I heard that many people with severed meridians were geniuses." -> "Il paraît que beaucoup de gens aux méridiens sectionnés étaient des génies."
   "And the last touch..." -> "Et la dernière touche..."
2. The lines come from OCR: they may have recognition mistakes (a wrong letter, a missing word), be in capital letters, or be cut in the middle of a sentence. Use the surrounding lines, the previous dialogue and common sense to understand what was really said, and translate that.
3. Register: choose "tu" or "vous" from the relationship between the speakers (family, friends and equals say "tu"; subordinates, strangers, elders and rivals in formal martial-arts settings often say "vous"), and stay consistent with the previous dialogue.
4. Fantasy and martial-arts vocabulary: use the usual French terms and keep them consistent: Heavenly Demon -> Démon Céleste, meridians -> méridiens, inner energy / qi -> qi, sect -> secte, cultivation -> cultivation, martial arts -> arts martiaux, murim -> murim, sword -> épée, disciple -> disciple, master -> maître, junior / senior -> cadet / aîné (or keep the Korean title if the previous dialogue does). Keep character names exactly as in the glossary or the previous dialogue; never translate a name.
5. Sound effects and interjections: give the natural French equivalent ("Ugh" -> "Argh", "Tsk" -> "Tss", "Haha" -> "Haha"), or keep the sound effect unchanged if there is no equivalent.
6. Keep the speaker's tone (anger, irony, fear, politeness). Keep the length close to the original so the text fits the bubble; do not add or remove information.
7. French typography: normal sentence case with correct accents, a space before ? ! : ; and « » for quotations. Do not write in capital letters even if the source is in capitals.
8. Never add notes, explanations or quotation marks around lines.
9. Answer with exactly one line per input line, in the format `N| translation`, using the same numbers, and nothing else.
""".trim()
    }

    /** Seconde passe optionnelle : relecture du brouillon (variante « r »). */
    fun refineSystem(): String = """
You are a senior French editor of professionally published manhwa. You receive source lines (from OCR, possibly with small mistakes) and a draft French translation. Improve the draft: fix any mistranslation or misunderstanding, remove literal phrasing and calques, make every line natural idiomatic spoken French with the right register and tone, keep names and terms consistent, keep the length close to the original. French typography: sentence case, a space before ? ! : ;. Answer with exactly one line per input line, in the format `N| final translation`, using the same numbers, and nothing else.
""".trim()

    fun refineUser(texts: List<String>, draft: List<String>, history: List<Pair<String, String>>, glossary: List<GlossaryEntry>): String = buildString {
        if (history.isNotEmpty()) {
            appendLine("Previous dialogue (context):")
            history.forEach { (src, tgt) -> appendLine("- ${oneLine(src)} => ${oneLine(tgt)}") }
            appendLine()
        }
        if (glossary.isNotEmpty()) { appendLine("Glossary (use exactly):"); glossary.forEach { appendLine("${it.source} = ${it.target}") }; appendLine() }
        appendLine("Lines:")
        texts.forEachIndexed { i, t -> appendLine("${i + 1}| source: ${oneLine(t)}") ; appendLine("${i + 1}| draft: ${oneLine(draft.getOrElse(i) { "" })}") }
    }.trim()

    fun user(texts: List<String>, history: List<Pair<String, String>>, glossary: List<GlossaryEntry>): String = buildString {
        if (history.isNotEmpty()) {
            appendLine("Previous dialogue of this chapter (context only, do not translate again):")
            history.forEach { (src, tgt) -> appendLine("- ${oneLine(src)} => ${oneLine(tgt)}") }
            appendLine()
        }
        if (glossary.isNotEmpty()) {
            appendLine("Glossary (use exactly):")
            glossary.forEach { appendLine("${it.source} = ${it.target}") }
            appendLine()
        }
        appendLine("Lines to translate:")
        texts.forEachIndexed { i, t -> appendLine("${i + 1}| ${oneLine(t)}") }
    }.trim()

    private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private val thinkRe = Regex("(?s)<think>.*?</think>")
    private val lineRe = Regex("^\\s*[\\[(]?(\\d+)[\\])]?\\s*[|:.)\\-–]\\s?(.*)$")

    /** Renvoie une traduction par ligne d'entrée (null si absente). Tolère le Markdown, les guillemets et les lignes multiples. */
    fun parse(reply: String, count: Int): List<String?> {
        val cleaned = thinkRe.replace(reply, "").replace("**", "").replace("`", "")
        val found = HashMap<Int, StringBuilder>()
        var current: StringBuilder? = null
        val plain = ArrayList<String>()
        for (raw in cleaned.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val m = lineRe.matchEntire(line)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                if (n in 1..count) {
                    current = StringBuilder(clean(m.groupValues[2])).also { found[n] = it }
                } else current = null // numéro hors plage : ligne ignorée, sans la rattacher à la précédente
                continue
            }
            plain.add(line.trim())
            current?.append(' ')?.append(clean(line))
        }
        if (found.isEmpty() && plain.size == count) return plain.map { clean(it) }
        return (1..count).map { i -> found[i]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
    }

    private fun clean(s: String): String = s.trim().removeSurrounding("\"").removeSurrounding("«", "»").removeSurrounding("“", "”").removePrefix("«").removeSuffix("»").trim()
}

enum class TranslationEngineChoice { MLKIT, GEMMA_E2B, GEMMA_E4B }

object LlmModels {
    private const val BASE = "https://huggingface.co/litert-community"
    val E2B = ModelSpec(
        "gemma4-e2b", "$BASE/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        2_588_147_712L, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", "gemma-4-E2B-it.litertlm",
    )
    val E4B = ModelSpec(
        "gemma4-e4b", "$BASE/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        3_659_530_240L, "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", "gemma-4-E4B-it.litertlm",
    )

    fun specFor(c: TranslationEngineChoice): ModelSpec? = when (c) {
        TranslationEngineChoice.GEMMA_E2B -> E2B
        TranslationEngineChoice.GEMMA_E4B -> E4B
        TranslationEngineChoice.MLKIT -> null
    }
}
