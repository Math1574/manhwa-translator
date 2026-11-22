package app.manhwa.core

/** Construction du prompt de traduction pour un modèle de langage local et lecture de sa réponse. */
object LlmPrompt {

    fun languageName(code: String): String = when (code.substringBefore('-')) {
        "ja" -> "Japanese"; "ko" -> "Korean"; "zh" -> "Chinese"; "en" -> "English"
        "fr" -> "French"; "es" -> "Spanish"; "de" -> "German"
        else -> "the source language (detect it)"
    }

    /** Version courante du prompt (le banc d'essai de traduction compare plusieurs versions). */
    const val CURRENT = 4

    fun system(source: String, target: String, variant: Int = CURRENT): String =
        if (variant == 1 || target != "fr") systemV1(source, target) else if (variant >= 4) systemV4(source) else systemV2(source, extended = variant >= 3)

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

    private fun systemV2(source: String, extended: Boolean = false): String {
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
""".trim() + (if (extended) """

Additional rules:
10. Never leave English words in the French text, except proper names and set martial-arts terms. Translate every title and rank: elder -> ancien (or doyen), young master -> jeune maître, sect leader -> chef de secte, patriarch -> patriarche, grandmaster -> grand maître, senior / junior (martial) -> aîné / cadet, brother / sister (martial) -> frère / sœur d'armes, Alliance -> Alliance, general -> général, majesty -> majesté.
11. Pick the meaning that fits the setting, not the commonest everyday one: storage / storehouse -> entrepôt or réserve; a drunk -> un ivrogne; nerve / guts -> culot; "to handle" -> s'occuper de; "to teach someone a lesson" -> donner une leçon à quelqu'un; "I wouldn't bet my life on it" -> "je n'en mettrais pas ma main au feu"; "play dumb" -> faire l'innocent; "through hell and back" -> revenir de l'enfer.
12. Prefer the shortest natural wording; use contractions and everyday vocabulary in casual speech, and a more formal register only for formal characters. Avoid stiff phrases such as "Voulez-vous que nous..." when "On lui..." sounds more natural between fighters.
12bis. Translate a sentence cut across two bubbles as one coherent sentence, but keep one output line per input line.
""".trimEnd() else "")
    }

    /**
     * Version 4 : contre la traduction mot à mot. La règle 1 donne des contre-exemples (anglicisme → français naturel), la règle 2 renvoie aux expressions que le message liste
     * (voir [IdiomHints]), la règle 3 apprend à reconnaître une faute de lecture au lieu de la traduire, la règle 4 règle les bégaiements ; titres et rangs d'arts martiaux sont réunis.
     */
    private fun systemV4(source: String): String {
        val src = if (source == "auto") "Korean, Japanese, Chinese or English (detect it)" else languageName(source)
        return """
You are a professional French translator of manhwa, manga, manhua and webtoons, working for a high-quality scanlation team. You translate the dialogue of one page (given in reading order) from $src into French, as it would appear in a professionally published edition.

Rules:
1. Translate the MEANING, never word for word. First understand what the character really means, then say it the way a native French speaker would in that situation: rebuild the sentence with French constructions (change the word order, use "on", pronouns and contractions, pick the right French verb instead of copying "verb + particle"). A line that sounds like English written with French words is wrong. Literal (wrong) -> natural (right):
   "Not a chance!" -> "Pas question !" (not "Pas une chance !")
   "I'll have my revenge." -> "Je me vengerai."
   "That made me realize my mistake." -> "Ça m'a fait comprendre mon erreur."
   "I won't let you get away with this!" -> "Je ne te laisserai pas t'en tirer comme ça !"
   "He didn't lay a finger on me." -> "Il ne m'a même pas touché."
   "Cover your ears, everyone!" -> "Bouchez-vous les oreilles, tout le monde !"
2. Idioms, proverbs, phrasal verbs (get away, take down, turn against...), insults and exclamations always take their natural French equivalent, never a word-for-word rendering. When the message lists "Expressions" with their French meaning, use them.
3. The lines come from OCR: they may contain recognition mistakes (a wrong letter, a missing word, two words glued together), be in capital letters, or be cut in the middle of a sentence. Use the surrounding lines, the previous dialogue and common sense to work out what was really said, and translate that. A strange short word or abbreviation that makes no sense (for example "YOY" instead of "YOU") is a recognition mistake, not an acronym: never expand it.
4. A stutter ("B-But", "W-wait", "Th-the") keeps its stutter in French, repeating the first letter of the French word that follows: "M-Mais", "A-Attends", "L-Le".
5. Register: choose "tu" or "vous" from the relationship between the speakers (family, friends and equals say "tu"; subordinates, strangers, elders and rivals in formal martial-arts settings often say "vous"), and stay consistent with the previous dialogue: the same two characters keep the same form from one line to the next. "you" said to several people (everyone, you all, you guys, yourselves) is always "vous" (with "votre", "vos", imperatives in -ez); to one person it is "tu" or "vous", never both in the same line.
6. Fantasy and martial-arts vocabulary: use the usual French terms and keep them consistent: Heavenly Demon -> Démon Céleste, meridians -> méridiens, inner energy / qi -> qi, sect -> secte, cultivation -> cultivation, martial arts -> arts martiaux, murim -> murim, sword -> épée, disciple -> disciple, master -> maître. Translate every title and rank, never leave it in English: elder -> ancien (or doyen), young master -> jeune maître, eldest young master -> jeune maître aîné, sect leader -> chef de secte, patriarch -> patriarche, grandmaster -> grand maître, senior / junior (martial brother or sister) -> aîné / cadet or grand frère / petit frère d'armes, Alliance -> Alliance, general -> général, majesty -> majesté. Keep character names exactly as in the glossary or the previous dialogue; never translate a name. Never leave other English words in the French text.
7. Pick the meaning that fits the setting, not the commonest everyday one: storage / storehouse -> entrepôt or réserve; a drunk -> un ivrogne; nerve / guts -> culot; "to handle" -> s'occuper de; "to teach someone a lesson" -> donner une leçon à quelqu'un; "play dumb" -> faire l'innocent.
8. Sound effects and interjections: give the natural French equivalent ("Ugh" -> "Argh", "Tsk" -> "Tss", "Haha" -> "Haha"), or keep the sound effect unchanged if there is no equivalent.
9. Keep the speaker's tone (anger, irony, fear, politeness). Prefer the shortest natural wording and keep the length close to the original so the text fits the bubble; use contractions and everyday vocabulary in casual speech. Do not add or remove information.
10. A sentence cut across two bubbles is translated as one coherent sentence, but keep one output line per input line.
11. French typography: normal sentence case with correct accents, a space before ? ! : ; and « » for quotations. Do not write in capital letters even if the source is in capitals.
12. Never add notes, explanations or quotation marks around lines.
13. Answer with exactly one line per input line, in the format `N| translation`, using the same numbers, and nothing else.
""".trim()
    }

    /** Seconde passe optionnelle : relecture du brouillon (variante « r »). */
    /** Demande de correction d'une seule ligne dont la première traduction a échoué aux contrôles automatiques. */
    fun fixUser(text: String, previous: String, problems: List<String>, history: List<Pair<String, String>>, glossary: List<GlossaryEntry>, names: List<String>, hints: List<IdiomHints.Hit> = emptyList(), style: List<String> = emptyList()): String = buildString {
        if (names.isNotEmpty()) { appendLine("Names and terms to keep exactly as written: ${names.joinToString(", ")}"); appendLine() }
        if (style.isNotEmpty()) { appendLine("Established in this chapter (keep it consistent):"); style.forEach { appendLine("- $it") }; appendLine() }
        if (hints.isNotEmpty()) { appendLine("Expressions in this line to render by meaning, never word for word (conjugate the French to fit the sentence and keep its negation, tense and person):"); hints.forEach { appendLine("- \"${it.matched}\": ${it.french}") }; appendLine() }
        if (history.isNotEmpty()) { appendLine("Previous dialogue (context only):"); history.takeLast(6).forEach { (a, b) -> appendLine("- ${oneLine(a)} => ${oneLine(b)}") }; appendLine() }
        if (glossary.isNotEmpty()) { appendLine("Glossary (use exactly):"); glossary.forEach { appendLine("${it.source} = ${it.target}") }; appendLine() }
        appendLine("Your previous translation of the line below was rejected by an automatic check.")
        appendLine("Problems: ${problems.joinToString("; ")}")
        appendLine("Previous attempt: ${oneLine(previous)}")
        appendLine()
        appendLine("Translate this line again, completely and faithfully, in natural French (every word translated except names and set terms):")
        appendLine("1| ${oneLine(text)}")
    }.trim()

    fun refineSystem(): String = """
You are a senior French editor of professionally published manhwa. You receive source lines (from OCR, possibly with small mistakes) and a draft French translation. Improve the draft: fix any mistranslation or misunderstanding, remove literal phrasing and calques, make every line natural idiomatic spoken French with the right register and tone, keep names and terms consistent, keep the length close to the original. French typography: sentence case, a space before ? ! : ;. Answer with exactly one line per input line, in the format `N| final translation`, using the same numbers, and nothing else.
""".trim()

    fun refineUser(texts: List<String>, draft: List<String>, history: List<Pair<String, String>>, glossary: List<GlossaryEntry>, series: String? = null): String = buildString {
        if (!series.isNullOrBlank()) { appendLine("Series: $series"); appendLine() }
        if (history.isNotEmpty()) {
            appendLine("Previous dialogue (context):")
            history.forEach { (src, tgt) -> appendLine("- ${oneLine(src)} => ${oneLine(tgt)}") }
            appendLine()
        }
        if (glossary.isNotEmpty()) { appendLine("Glossary (use exactly):"); glossary.forEach { appendLine("${it.source} = ${it.target}") }; appendLine() }
        appendLine("Lines:")
        texts.forEachIndexed { i, t -> appendLine("${i + 1}| source: ${oneLine(t)}") ; appendLine("${i + 1}| draft: ${oneLine(draft.getOrElse(i) { "" })}") }
    }.trim()

    /**
     * [page] : les autres lignes du lot déjà traduites, données pour le contexte quand seules quelques lignes sont refaites (lignes absentes de la réponse ou mal placées, voir [TranslationAlign]).
     */
    fun user(texts: List<String>, history: List<Pair<String, String>>, glossary: List<GlossaryEntry>, series: String? = null, names: List<String> = emptyList(), hints: List<IdiomHints.Hit> = emptyList(), style: List<String> = emptyList(), page: List<Pair<String, String>> = emptyList(), premise: String? = null): String = buildString {
        if (names.isNotEmpty()) { appendLine("Names and terms of this chapter to keep exactly as written (never translate them): ${names.joinToString(", ")}"); appendLine() }
        if (style.isNotEmpty()) { appendLine("Established in this chapter (keep it consistent):"); style.forEach { appendLine("- $it") }; appendLine() }
        if (!series.isNullOrBlank()) { appendLine("Series: $series (background only: never put the series title or its words in a translation unless the source line itself contains them)"); appendLine() }
        premiseOf(premise)?.let { appendLine("Story premise (background only, to know who the characters are, their gender and relationships; never add its content to a translation): $it"); appendLine() }
        if (history.isNotEmpty()) {
            appendLine("Previous dialogue of this chapter (context only, do not translate again):")
            history.forEach { (src, tgt) -> appendLine("- ${oneLine(src)} => ${oneLine(tgt)}") }
            appendLine()
        }
        if (page.isNotEmpty()) {
            appendLine("Other lines of these pages, already translated (context only, do not translate them again):")
            page.forEach { (src, tgt) -> appendLine("- ${oneLine(src)} => ${oneLine(tgt)}") }
            appendLine()
        }
        if (glossary.isNotEmpty()) {
            appendLine("Glossary (use exactly):")
            glossary.forEach { appendLine("${it.source} = ${it.target}") }
            appendLine()
        }
        if (hints.isNotEmpty()) {
            appendLine("Expressions in the lines below to render by meaning, never word for word (conjugate the French to fit the sentence and keep its negation, tense and person):")
            hints.forEach { appendLine("- line ${it.line}, \"${it.matched}\": ${it.french}") }
            appendLine()
        }
        appendLine("Lines to translate:")
        texts.forEachIndexed { i, t -> appendLine("${i + 1}| ${oneLine(t)}") }
    }.trim()

    /**
     * Le synopsis de l'œuvre, raccourci pour le prompt : il dit qui sont les personnages (le héros réincarné en demi-démon est un homme : « je suis à moitié humain », pas « humaine »).
     * Coupé à la fin d'une phrase avant [PREMISE_MAX] caractères ; null s'il est vide.
     */
    fun premiseOf(text: String?): String? {
        val t = text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (t.length < 20) return null
        if (t.length <= PREMISE_MAX) return t
        val cut = t.take(PREMISE_MAX)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '…'))
        return if (end >= PREMISE_MAX / 2) cut.take(end + 1) else cut.substringBeforeLast(' ') + "…"
    }
    const val PREMISE_MAX = 450

    /** Nombre maximal de jetons de sortie pour une page : ~ (caractères / 3) × 1,6 de marge + 64 ; borné pour éviter toute génération sans fin. */
    fun maxOutputTokens(texts: List<String>): Int = (((texts.sumOf { it.length } * 1.3 / 3.0) + 12 * texts.size) * 1.6 + 64).toInt().coerceIn(96, 1500)

    /**
     * Traduction suspecte : contenu supprimé (beaucoup plus courte que l'original) ou terme de glossaire/série injecté alors que la
     * ligne d'origine n'en parle pas (ex. « Démon Céleste » pour « Anyway, I heard that... »).
     */
    fun suspicious(src: String, tr: String): Boolean {
        val srcLetters = src.count { it.isLetter() }
        if (srcLetters >= 12 && tr.count { it.isLetter() } < 0.35f * srcLetters) return true
        val s = src.lowercase(); val t = tr.lowercase()
        val injected = listOf("démon céleste" to listOf("demon", "heavenly"), "méridien" to listOf("meridian"), "murim" to listOf("murim"))
        for ((fr, en) in injected) if (t.contains(fr) && en.none { s.contains(it) } && !s.contains("meridian")) return true
        return false
    }

    private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private val thinkRe = Regex("(?s)<think>.*?</think>")
    private val lineRe = Regex("^\\s*[\\[(]?(\\d+)[\\])]?\\s*[|:.)\\-–]\\s?(.*)$")

    /**
     * Renvoie une traduction par ligne d'entrée (null si absente). Tolère le Markdown, les guillemets et les lignes multiples.
     * [sources] : les lignes envoyées au modèle, dans l'ordre ; quand elles sont données, la ligne d'origine que le modèle a recopiée devant sa traduction en est retirée (voir [TranslationEcho]).
     */
    fun parse(reply: String, count: Int, sources: List<String>? = null): List<String?> {
        val parsed = parseLines(reply, count)
        if (sources == null) return parsed
        return parsed.mapIndexed { i, t ->
            if (t == null) null else sources.getOrNull(i)?.let { s -> TranslationEcho.strip(s, t).takeIf { it.isNotBlank() } } ?: t
        }
    }

    private fun parseLines(reply: String, count: Int): List<String?> {
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
