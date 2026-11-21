package app.manhwa.core

import kotlin.math.abs

/**
 * Mise en forme du texte traduit choisie à la main pour un bloc (éditeur, onglet « Position »). Les valeurs par défaut sont celles de la mise en page automatique : un bloc sans réglage est mis
 * en page exactement comme avant. Les retours à la ligne, eux, sont ceux que l'on tape dans la traduction (voir [ShapeLayout.fit]).
 */
data class TextFormat(
    /** Alignement des lignes entre elles : centré (par défaut), à gauche ou à droite. */
    val align: Int = ALIGN_CENTER,
    /** Nombre de lignes imposé (0 : automatique). Le texte rétrécit si la place manque, jamais l'inverse. */
    val lines: Int = 0,
    /** Facteur d'interligne (1 : celui de la mise en page automatique). */
    val spacing: Float = 1f,
    /** Casse du texte : telle que traduite, MAJUSCULES ou minuscules. */
    val caseMode: Int = CASE_AUTO,
    /** Rotation du texte en degrés (positif : la ligne descend vers la droite), 0 = droit. */
    val angle: Float = 0f,
) {
    /** Vrai si aucun réglage n'est choisi (mise en page automatique). */
    val isDefault: Boolean get() = align == ALIGN_CENTER && lines == 0 && abs(spacing - 1f) < 0.005f && caseMode == CASE_AUTO && abs(angle) < 0.05f

    /** Les valeurs ramenées dans leurs bornes (une ligne enregistrée à la main ou d'une autre version ne doit jamais produire une mise en page absurde). */
    fun sane(): TextFormat = TextFormat(align.coerceIn(ALIGN_CENTER, ALIGN_RIGHT), lines.coerceIn(0, MAX_LINES), spacing.coerceIn(MIN_SPACING, MAX_SPACING), caseMode.coerceIn(CASE_AUTO, CASE_LOWER), angle.coerceIn(-MAX_ANGLE, MAX_ANGLE))

    /** Texte [text] dans la casse choisie. */
    fun applyCase(text: String): String = when (caseMode) { CASE_UPPER -> text.uppercase(); CASE_LOWER -> text.lowercase(); else -> text }

    fun encode(): String = if (isDefault) "-" else "a=$align;l=$lines;s=$spacing;c=$caseMode;r=$angle"

    companion object {
        const val ALIGN_CENTER = 0
        const val ALIGN_LEFT = 1
        const val ALIGN_RIGHT = 2
        const val CASE_AUTO = 0
        const val CASE_UPPER = 1
        const val CASE_LOWER = 2
        const val MAX_LINES = 8
        const val MIN_SPACING = 0.8f
        const val MAX_SPACING = 1.8f
        const val MAX_ANGLE = 45f

        fun decode(s: String?): TextFormat {
            if (s == null || s == "-" || s.isBlank()) return TextFormat()
            var f = TextFormat()
            for (part in s.split(';')) {
                val i = part.indexOf('=')
                if (i <= 0) continue
                val k = part.substring(0, i); val v = part.substring(i + 1)
                f = when (k) {
                    "a" -> v.toIntOrNull()?.let { f.copy(align = it) } ?: f
                    "l" -> v.toIntOrNull()?.let { f.copy(lines = it) } ?: f
                    "s" -> v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { f.copy(spacing = it) } ?: f
                    "c" -> v.toIntOrNull()?.let { f.copy(caseMode = it) } ?: f
                    "r" -> v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { f.copy(angle = it) } ?: f
                    else -> f
                }
            }
            return f.sane()
        }
    }
}

/**
 * Apparence choisie à la main pour le texte traduit : couleur, contour et lueur. Chaque champ à null reste automatique (couleurs mesurées sur le lettrage d'origine, voir [TextPaint]).
 * Les couleurs sont en RVB opaque ; les épaisseurs sont relatives : contour = fraction de la hauteur de capitale, lueur = fraction du corps de la police (0 : aucun contour, aucune lueur).
 */
data class TextLook(
    val fill: Int? = null,
    val outline: Int? = null, val outlineW: Float? = null,
    val glow: Int? = null, val glowW: Float? = null,
) {
    val isDefault: Boolean get() = fill == null && outline == null && outlineW == null && glow == null && glowW == null

    fun sane(): TextLook = TextLook(fill?.and(0xFFFFFF), outline?.and(0xFFFFFF), outlineW?.coerceIn(0f, MAX_OUTLINE), glow?.and(0xFFFFFF), glowW?.coerceIn(0f, MAX_GLOW))

    fun encode(): String {
        if (isDefault) return "-"
        val l = ArrayList<String>()
        fill?.let { l.add("f=%06x".format(it and 0xFFFFFF)) }
        outline?.let { l.add("o=%06x".format(it and 0xFFFFFF)) }
        outlineW?.let { l.add("ow=$it") }
        glow?.let { l.add("g=%06x".format(it and 0xFFFFFF)) }
        glowW?.let { l.add("gw=$it") }
        return l.joinToString(";")
    }

    companion object {
        const val MAX_OUTLINE = 0.4f
        const val MAX_GLOW = 1.0f

        /** Couleur hexadécimale (avec ou sans canal alpha) en RVB, null si illisible. */
        private fun hex(v: String): Int? = v.toLongOrNull(16)?.takeIf { it in 0..0xFFFFFFFFL }?.let { (it and 0xFFFFFF).toInt() }

        fun decode(s: String?): TextLook {
            if (s == null || s == "-" || s.isBlank()) return TextLook()
            var l = TextLook()
            for (part in s.split(';')) {
                val i = part.indexOf('=')
                if (i <= 0) continue
                val k = part.substring(0, i); val v = part.substring(i + 1)
                l = when (k) {
                    "f" -> hex(v)?.let { l.copy(fill = it) } ?: l
                    "o" -> hex(v)?.let { l.copy(outline = it) } ?: l
                    "ow" -> v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { l.copy(outlineW = it) } ?: l
                    "g" -> hex(v)?.let { l.copy(glow = it) } ?: l
                    "gw" -> v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { l.copy(glowW = it) } ?: l
                    else -> l
                }
            }
            return l.sane()
        }
    }
}
