package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

/**
 * Relecture des textes douteux. Les lecteurs rapides (ML Kit, Paddle) se trompent sur le lettrage manuscrit, incliné ou traversé par un contour,
 * et leur erreur donne une traduction fluide mais fausse. Ce fichier décide quels blocs relire, puis vérifie qu'une relecture (modèle vision-langage)
 * est une vraie correction et non une invention : un modèle qui « complète » ce qu'il lit est le risque principal.
 */

/** Pourquoi un bloc est suspect (lecture incomplète ou absurde) : un score de 1 ou plus déclenche la relecture. */
class Suspicion(val score: Float, val reasons: List<String>, val coverageGap: Boolean) {
    val suspect: Boolean get() = score >= 1.0f
}

object BlockSuspicion {
    private val repeated = Regex("(.)\\1{3,}")

    /** @param plausibility part des mots de la lecture qui sont de vrais mots (0..1) */
    fun assess(block: TextBlock, text: String, plausibility: (String) -> Float): Suspicion {
        if (text.count { it.isLetter() } < 3) return Suspicion(0f, emptyList(), false)
        val reasons = ArrayList<String>(); var score = 0f; var gap = false
        // la zone de texte détectée est plus grande que ce que la lecture a couvert : une ligne manque probablement
        val z = block.zone
        if (z != null && z.height > 0f && !block.vertical && block.lines.isNotEmpty()) {
            val ivs = block.lines.map { max(z.top, it.box.top) to min(z.bottom, it.box.bottom) }.filter { it.second > it.first }.sortedBy { it.first }
            var covered = 0f; var s0 = Float.NaN; var e0 = Float.NaN
            for ((a, b) in ivs) { if (s0.isNaN()) { s0 = a; e0 = b } else if (a <= e0) e0 = max(e0, b) else { covered += e0 - s0; s0 = a; e0 = b } }
            if (!s0.isNaN()) covered += e0 - s0
            val vc = covered / z.height
            val union = block.lines.map { it.box }.reduce { a, b -> a.union(b) }
            if (vc < 0.68f) { gap = true; score += 1.2f; reasons.add("zone détectée mal couverte (${(vc * 100).toInt()} %)") }
            else if (z.width > 1.35f * union.width) { gap = true; score += 0.7f; reasons.add("zone plus large que le texte lu") }
        }
        val p = plausibility(text)
        if (p < 0.55f) { score += 1.5f; reasons.add("mots peu plausibles (${(p * 100).toInt()} %)") }
        else if (p < 0.8f) { score += 1.0f; reasons.add("mots douteux (${(p * 100).toInt()} %)") }
        if (repeated.containsMatchIn(text.uppercase().filter { it.isLetter() })) { score += 1.0f; reasons.add("lettres répétées") }
        val minConf = block.lines.minOfOrNull { it.confidence } ?: 1f
        if (minConf < 0.6f) { score += 1.2f; reasons.add("confiance faible") } else if (minConf < 0.8f) { score += 0.7f; reasons.add("confiance moyenne") }
        if (block.kind == BlockKind.FREE) { score += 0.3f }
        if (block.lines.any { it.thickness > 0f && it.box.height > 1.6f * it.thickness }) { score += 0.3f; reasons.add("ligne inclinée") }
        return Suspicion(score, reasons, gap)
    }
}

/** Lecture proposée par un modèle vision-langage : texte, nombre de lignes annoncé, et présence de marqueurs d'illisible. */
class VisionRead(val text: String, val lines: Int, val uncertain: Boolean)

class Verdict(val accepted: Boolean, val text: String, val reason: String)

object ReadingVerifier {
    private val unreadable = Regex("\\[\\s*(?:LIGNE\\s*)?\\?\\s*\\]", RegexOption.IGNORE_CASE)
    private val wordRe = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")

    private fun words(s: String): List<String> = wordRe.findAll(s).map { it.value.uppercase() }.filter { it.length >= 2 }.toList()
    private fun letters(s: String) = s.count { it.isLetter() }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private fun similarity(a: String, b: String): Float = 1f - lev(a, b).toFloat() / max(a.length, b.length)

    /** Part des lettres de [a] retrouvées dans le même ordre dans [b] (plus longue sous-suite commune) : mesure l'accord malgré des mots abîmés ou collés. */
    private fun lcsRatio(a: String, b: String): Float {
        val x = a.uppercase().filter { it.isLetter() }; val y = b.uppercase().filter { it.isLetter() }
        if (x.isEmpty() || y.isEmpty()) return 0f
        var prev = IntArray(y.length + 1)
        for (i in 1..x.length) {
            val cur = IntArray(y.length + 1)
            for (j in 1..y.length) cur[j] = if (x[i - 1] == y[j - 1]) prev[j - 1] + 1 else max(prev[j], cur[j - 1])
            prev = cur
        }
        return prev[y.length].toFloat() / x.length
    }

    /**
     * Accepte la relecture seulement si elle ressemble à une correction : mots plausibles, appuyés par la lecture des moteurs (sauf pour la partie
     * qu'ils n'avaient pas lue quand une zone était mal couverte), longueur raisonnable, sans marqueur d'illisible en nombre.
     */
    fun verify(engineText: String, read: VisionRead, suspicion: Suspicion, plausibility: (String) -> Float): Verdict {
        val marks = unreadable.findAll(read.text).count()
        val cleaned = read.text.replace(unreadable, " ").replace(Regex("\\s+"), " ").trim()
        val gw = words(cleaned); val ew = words(engineText)
        if (gw.isEmpty()) return Verdict(false, engineText, "lecture vide")
        if (marks >= 2 || (marks >= 1 && gw.size <= 3)) return Verdict(false, engineText, "texte illisible")
        val pg = plausibility(cleaned); val pe = plausibility(engineText)
        if (pg < 0.75f || pg + 0.02f < pe) return Verdict(false, engineText, "lecture moins plausible")
        val supported = gw.count { w -> ew.any { e -> similarity(w, e) >= 0.55f } }
        val ratio = supported.toFloat() / gw.size
        val el = letters(engineText); val gl = letters(cleaned)
        if (pe < 0.55f) {
            // lecture des moteurs absurde (mots abîmés ou collés) : il n'y a presque rien à préserver. On exige que la relecture soit plausible et reste
            // du même ordre de grandeur, et qu'elle recoupe la lecture abîmée (mots communs ou lettres communes dans l'ordre) : aucune invention sans rapport.
            val agree = ratio >= 0.2f || lcsRatio(engineText, cleaned) >= 0.6f || el < 4
            if (!agree) return Verdict(false, engineText, "lecture sans appui des moteurs")
            if (gl > 3.0f * el + 20f || gl < 0.5f * el) return Verdict(false, engineText, "longueur incohérente")
        } else {
            // lecture des moteurs plausible et complète : seules de petites retouches sont acceptées
            val strict = pe >= 0.9f && !suspicion.coverageGap
            val minRatio = if (strict) 0.85f else if (suspicion.coverageGap) 0.2f else 0.5f
            if (ratio < minRatio) return Verdict(false, engineText, "lecture sans appui des moteurs")
            val maxLetters = if (suspicion.coverageGap) 2.8f * el + 20f else 1.4f * el + 8f
            if (gl > maxLetters) return Verdict(false, engineText, "lecture trop longue")
        }
        if (words(cleaned) == ew) return Verdict(false, engineText, "identique")
        return Verdict(true, cleaned, "relecture acceptée")
    }
}

/** Consigne et lecture de la réponse du modèle vision-langage (transcription seule, jamais de traduction). */
object VisionPrompt {
    fun system(): String = "You transcribe comic lettering. You are a reader, not an editor: you copy what is written and never improve it."

    fun user(engineText: String): String = buildString {
        append("Transcribe the English text in the image EXACTLY as it is written, line by line.\n")
        append("Rules:\n")
        append("- Copy only the visible letters. Never add, fix, complete or translate anything.\n")
        append("- Ignore speech-bubble outlines, panel borders, drawings and motion lines.\n")
        append("- Write a word you cannot read as [?].\n")
        append("- Keep the original uppercase and lowercase.\n")
        append("Automatic readings (they may be WRONG or INCOMPLETE; use them only where the image confirms them): ")
        append(engineText.replace('\n', ' ')).append('\n')
        append("Answer in exactly this format:\nLINES: <number of text lines you see>\nTEXT:\n<the transcription, one text line per line>")
    }

    private val linesRe = Regex("LINES:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val textRe = Regex("TEXT:\\s*(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun parse(reply: String): VisionRead? {
        val body = textRe.find(reply)?.groupValues?.get(1)?.trim() ?: reply.trim().takeIf { it.isNotEmpty() && it.length <= 300 && !it.contains("LINES:", true) } ?: return null
        val text = body.lines().map { it.trim().trim('"', '`') }.filter { it.isNotEmpty() }.joinToString(" ")
        if (text.isEmpty()) return null
        val n = linesRe.find(reply)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return VisionRead(text, n, unreadable.containsMatchIn(text))
    }
    private val unreadable = Regex("\\[\\s*(?:LIGNE\\s*)?\\?\\s*\\]", RegexOption.IGNORE_CASE)
}

/** Relit un bloc à partir de l'image (modèle vision-langage local) ; null si indisponible ou en échec. */
interface BlockReader {
    val available: Boolean
    fun reread(imageBytes: ByteArray, block: TextBlock, engineText: String): VisionRead?
}
