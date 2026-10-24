package app.manhwa.core

/** Construction du prompt de traduction pour un modèle de langage local et lecture de sa réponse. */
object LlmPrompt {

    fun languageName(code: String): String = when (code.substringBefore('-')) {
        "ja" -> "Japanese"; "ko" -> "Korean"; "zh" -> "Chinese"; "en" -> "English"
        "fr" -> "French"; "es" -> "Spanish"; "de" -> "German"
        else -> "the source language (detect it)"
    }

    fun system(source: String, target: String): String {
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
