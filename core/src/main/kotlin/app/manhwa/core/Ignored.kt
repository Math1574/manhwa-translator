package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Ce que l'utilisateur ne veut plus voir traduit : des textes (dans une œuvre ou partout) et des images entières (bandeaux de début ou de fin de chapitre).
 * Rien n'est ignoré sans un geste de sa part, et tout se retire depuis la liste des réglages.
 */
data class IgnoredText(
    /** [EVERYWHERE] ou la clé de l'œuvre (site + titre). */
    val scope: String,
    /** Texte lu sur l'image, tel qu'affiché à l'utilisateur. */
    val text: String,
) {
    companion object { const val EVERYWHERE = "*" }
}

/**
 * Image à ne jamais traduire. Reconnue par ses octets (somme de contrôle) ou, si elle a été réencodée par le site, par son empreinte visuelle : même proportions, empreinte presque identique.
 */
data class IgnoredImage(
    val id: String,
    val sha: String,
    val width: Int,
    val height: Int,
    /** Empreinte visuelle en hexadécimal (voir [Fingerprint]) ; vide si l'image n'a pas assez de contenu pour être reconnue autrement que par ses octets. */
    val bits: String = "",
    val label: String = "",
    val addedAt: Long = 0L,
)

/** Textes et images ignorés. Immuable : chaque modification donne une nouvelle liste. */
class IgnoreRules(val texts: List<IgnoredText> = emptyList(), val images: List<IgnoredImage> = emptyList()) {
    val isEmpty: Boolean get() = texts.isEmpty() && images.isEmpty()

    /** Vrai si ce texte lu (bloc détecté) est à laisser tel quel dans cette œuvre : règle valable partout, ou propre à l'œuvre. */
    fun textIgnored(series: String?, source: String): Boolean {
        if (texts.isEmpty()) return false
        val n = IgnoredMatch.normalize(source)
        if (n.isEmpty()) return false
        for (r in texts) {
            if (r.scope != IgnoredText.EVERYWHERE && r.scope != series) continue
            if (IgnoredMatch.same(n, IgnoredMatch.normalize(r.text))) return true
        }
        return false
    }

    /** Règle d'image qui reconnaît cette image (mêmes octets, ou même empreinte), sinon null. [fp] : empreinte calculée sur l'image (null si on n'a pas pu la calculer). */
    fun imageMatch(sha: String, fp: Fingerprint?): IgnoredImage? {
        for (r in images) {
            if (r.sha == sha) return r
            if (fp != null && r.bits.isNotEmpty() && Fingerprint.similar(fp, r)) return r
        }
        return null
    }

    fun withText(scope: String, text: String): IgnoreRules {
        val t = text.trim()
        if (IgnoredMatch.normalize(t).isEmpty()) return this
        if (texts.any { it.scope == scope && IgnoredMatch.normalize(it.text) == IgnoredMatch.normalize(t) }) return this
        return IgnoreRules(texts + IgnoredText(scope, t), images)
    }

    fun withoutText(scope: String, text: String): IgnoreRules = IgnoreRules(texts.filterNot { it.scope == scope && it.text == text }, images)

    fun withImage(img: IgnoredImage): IgnoreRules = IgnoreRules(texts, images.filterNot { it.id == img.id || it.sha == img.sha } + img)

    fun withoutImage(id: String): IgnoreRules = IgnoreRules(texts, images.filterNot { it.id == id })

    fun serialize(): String = buildString {
        for (t in texts) append("T\t").append(esc(t.scope)).append('\t').append(esc(t.text)).append('\n')
        for (i in images) append("I\t").append(esc(i.id)).append('\t').append(i.sha).append('\t').append(i.width).append('\t').append(i.height).append('\t').append(i.bits)
            .append('\t').append(esc(i.label)).append('\t').append(i.addedAt).append('\n')
    }

    companion object {
        /** Relit un fichier enregistré ; une ligne illisible est sautée plutôt que de perdre tout le reste. */
        fun parse(s: String): IgnoreRules {
            val texts = ArrayList<IgnoredText>(); val images = ArrayList<IgnoredImage>()
            for (line in s.split('\n')) {
                if (line.isEmpty()) continue
                val f = line.split('\t')
                try {
                    when (f[0]) {
                        "T" -> if (f.size >= 3) texts.add(IgnoredText(unesc(f[1]), unesc(f[2])))
                        "I" -> if (f.size >= 8) images.add(IgnoredImage(unesc(f[1]), f[2], f[3].toInt(), f[4].toInt(), f[5], unesc(f[6]), f[7].toLong()))
                    }
                } catch (_: Exception) { /* ligne abîmée : sautée */ }
            }
            return IgnoreRules(texts, images)
        }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
        private fun unesc(s: String): String {
            val sb = StringBuilder(); var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); 'r' -> sb.append('\r'); else -> sb.append(s[i + 1]) }
                    i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}

/** Comparaison de textes lus : insensible à la casse, aux accents, à la ponctuation et aux espaces ; une ou deux lettres mal lues sont tolérées sur un texte assez long. */
object IgnoredMatch {
    /** Lettres et chiffres seuls, en minuscules, sans accents. */
    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)) if (ch.isLetterOrDigit()) sb.append(ch.lowercaseChar())
        return sb.toString()
    }

    /** Deux textes déjà normalisés : égaux, ou (dès 10 caractères) semblables à 85 % près. Un texte court doit être identique : « no » n'est pas « now ». */
    fun same(a: String, b: String): Boolean {
        if (a == b) return true
        val m = max(a.length, b.length)
        if (m < 10 || abs(a.length - b.length) > m * 15 / 100) return false
        return 1f - lev(a, b).toFloat() / m >= 0.85f
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }; var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}

/**
 * Empreinte visuelle d'une image : 16 × 16 comparaisons de luminosité entre voisins horizontaux d'une réduction à 17 × 16 points (256 bits). Elle ne change presque pas quand le site réencode
 * ou redimensionne l'image, et change beaucoup d'une image à l'autre.
 */
class Fingerprint(val width: Int, val height: Int, val bits: LongArray) {
    /** Nombre de bits à 1 : une page presque unie (blanche) en a très peu et ne peut pas être reconnue par son empreinte. */
    val ones: Int get() = bits.sumOf { java.lang.Long.bitCount(it) }

    fun distance(o: Fingerprint): Int { var d = 0; for (i in bits.indices) d += java.lang.Long.bitCount(bits[i] xor o.bits[i]); return d }

    /** Utilisable pour reconnaître une image réencodée : assez de contenu. */
    val usable: Boolean get() = ones >= MIN_ONES

    fun hex(): String = bits.joinToString("") { "%016x".format(it) }

    companion object {
        const val COLS = 17
        const val ROWS = 16
        const val MIN_ONES = 24
        /** Écart de bits toléré (sur 256) entre deux empreintes de la même image réencodée. */
        const val MAX_DISTANCE = 12
        /** Écart relatif des proportions toléré. */
        const val ASPECT_TOLERANCE = 0.015f

        /** [gray] : luminosité de la réduction à [COLS] × [ROWS] points, ligne par ligne ; [width] et [height] : taille de l'image d'origine. */
        fun fromGrid(width: Int, height: Int, gray: IntArray): Fingerprint {
            require(gray.size == COLS * ROWS) { "grille de ${COLS * ROWS} points attendue" }
            val bits = LongArray(4)
            var k = 0
            for (r in 0 until ROWS) for (c in 0 until COLS - 1) {
                if (gray[r * COLS + c] > gray[r * COLS + c + 1]) bits[k / 64] = bits[k / 64] or (1L shl (k % 64))
                k++
            }
            return Fingerprint(width, height, bits)
        }

        fun fromHex(width: Int, height: Int, hex: String): Fingerprint? {
            if (hex.length != 64) return null
            return try { Fingerprint(width, height, LongArray(4) { java.lang.Long.parseUnsignedLong(hex.substring(it * 16, it * 16 + 16), 16) }) } catch (_: NumberFormatException) { null }
        }

        /** Même image que la règle [r] (réencodée ou redimensionnée) : proportions proches, assez de contenu, empreintes presque identiques. */
        fun similar(fp: Fingerprint, r: IgnoredImage): Boolean {
            if (!fp.usable || r.width <= 0 || r.height <= 0 || fp.height <= 0) return false
            val a = fp.width.toFloat() / fp.height; val b = r.width.toFloat() / r.height
            if (abs(a - b) > ASPECT_TOLERANCE * b) return false
            val rf = fromHex(r.width, r.height, r.bits) ?: return false
            return rf.usable && fp.distance(rf) <= MAX_DISTANCE
        }
    }
}
