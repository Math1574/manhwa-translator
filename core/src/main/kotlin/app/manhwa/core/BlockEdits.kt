package app.manhwa.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Point en pixels de l'image. */
data class Pt(val x: Float, val y: Float)

/** Trait de pinceau libre : points et rayon en pixels de l'image ; [erase] : la gomme retire ce qui a été peint. */
class BrushStroke(val points: List<Pt>, val radius: Float, val erase: Boolean = false)

/**
 * Zone de texte choisie à la main, en pixels de l'image : un rectangle ([rect]) ou, si le pinceau a servi, la forme peinte ([strokes], gomme comprise).
 * Quand des traits existent, seuls les pixels peints appartiennent à la zone ; [rect] est alors leur boîte englobante.
 */
class UserRegion(val rect: RectF, val strokes: List<BrushStroke> = emptyList()) {
    /** Vrai si la zone est une forme peinte (et non un simple rectangle). */
    val painted: Boolean get() = strokes.any { !it.erase && it.points.isNotEmpty() }

    /** Boîte englobante de la zone : le rectangle, ou le tracé peint (la gomme n'est pas prise en compte, la zone peut être plus petite). */
    val bounds: RectF
        get() {
            if (!painted) return rect
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (s in strokes) if (!s.erase) for (p in s.points) { l = min(l, p.x - s.radius); t = min(t, p.y - s.radius); r = max(r, p.x + s.radius); b = max(b, p.y + s.radius) }
            return RectF(l, t, r, b)
        }

    /** Vrai si le point (pixels de l'image) est dans la zone. */
    fun contains(x: Float, y: Float): Boolean {
        if (!painted) return rect.contains(x, y)
        var inside = false
        for (s in strokes) if (touches(s, x, y)) inside = !s.erase
        return inside
    }

    private fun touches(s: BrushStroke, x: Float, y: Float): Boolean {
        val r2 = s.radius * s.radius
        if (s.points.size == 1) return sq(x - s.points[0].x) + sq(y - s.points[0].y) <= r2
        for (i in 0 until s.points.size - 1) if (distToSegmentSq(x, y, s.points[i], s.points[i + 1]) <= r2) return true
        return false
    }

    private fun sq(v: Float) = v * v

    private fun distToSegmentSq(px: Float, py: Float, a: Pt, b: Pt): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0f) 0f else (((px - a.x) * dx + (py - a.y) * dy) / len2).coerceIn(0f, 1f)
        return sq(px - (a.x + t * dx)) + sq(py - (a.y + t * dy))
    }

    /**
     * Masque de la zone sur la région analysée par l'effacement : [w] × [h] pixels échantillonnés à l'échelle [s] (un pixel échantillonné vaut 1/s pixel de l'image), dont le coin
     * est en ([x0], [y0]). Un rectangle déborde de 2 pixels de l'image pour ne pas rogner le contour des lettres.
     */
    fun mask(x0: Int, y0: Int, w: Int, h: Int, s: Float): BooleanArray {
        val m = BooleanArray(w * h)
        if (!painted) {
            val l = (floor((rect.left - 2f) * s).toInt() - x0).coerceIn(0, w); val r = (ceil((rect.right + 2f) * s).toInt() - x0).coerceIn(0, w)
            val t = (floor((rect.top - 2f) * s).toInt() - y0).coerceIn(0, h); val b = (ceil((rect.bottom + 2f) * s).toInt() - y0).coerceIn(0, h)
            for (y in t until b) for (x in l until r) m[y * w + x] = true
            return m
        }
        for (st in strokes) {
            if (st.points.isEmpty()) continue
            val rr = max(0.5f, st.radius * s)
            fun stamp(px: Float, py: Float) {
                val cx = px * s - x0; val cy = py * s - y0
                val ya = floor(cy - rr).toInt().coerceAtLeast(0); val yb = ceil(cy + rr).toInt().coerceAtMost(h - 1)
                val xa = floor(cx - rr).toInt().coerceAtLeast(0); val xb = ceil(cx + rr).toInt().coerceAtMost(w - 1)
                for (y in ya..yb) for (x in xa..xb) { val ddx = x + 0.5f - cx; val ddy = y + 0.5f - cy; if (ddx * ddx + ddy * ddy <= rr * rr) m[y * w + x] = !st.erase }
            }
            stamp(st.points[0].x, st.points[0].y)
            for (i in 0 until st.points.size - 1) {
                val a = st.points[i]; val b = st.points[i + 1]
                val len = hypot(b.x - a.x, b.y - a.y) * s
                val steps = max(1, ceil(len / max(1f, rr * 0.5f)).toInt())
                for (k in 1..steps) stamp(a.x + (b.x - a.x) * k / steps, a.y + (b.y - a.y) * k / steps)
            }
        }
        return m
    }

    fun shifted(dy: Float): UserRegion =
        if (dy == 0f) this else UserRegion(rect.offset(0f, dy), strokes.map { s -> BrushStroke(s.points.map { Pt(it.x, it.y + dy) }, s.radius, s.erase) })
}

enum class EditKind {
    /** Modifie un bloc détecté par la lecture automatique (traduction, position, taille, police, ou « ne pas traduire »). */
    ADJUST,
    /** Bloc défini à la main : sa zone, son texte et sa traduction viennent de l'utilisateur ; il remplace les blocs détectés qu'il recouvre. */
    MANUAL,
}

/**
 * Correction manuelle d'un bloc, conservée par image. Les coordonnées sont celles de l'image elle-même (pas de la fenêtre de jonction avec l'image du dessus).
 */
data class BlockEdit(
    val id: String,
    val kind: EditKind,
    /** ADJUST : boîte et texte lu du bloc détecté au moment de la correction (pour le retrouver) ; MANUAL : bloc détecté que celui-ci remplace, s'il y en avait un. */
    val match: RectF? = null,
    val matchSource: String = "",
    /** Zone choisie à la main (MANUAL). */
    val region: UserRegion? = null,
    /** Texte d'origine corrigé et traduction saisie ; null : ceux de la lecture et de la traduction automatiques. */
    val source: String? = null,
    val translation: String? = null,
    /** Ne pas traduire : l'original reste tel quel. */
    val hidden: Boolean = false,
    /** Décalage du texte traduit (pixels de l'image), facteur de taille et police choisie (null : automatique). */
    val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f, val fontId: String? = null,
    /** MANUAL : géométrie des lignes lues dans la zone, nature du bloc (« IN_BUBBLE », « FREE ») et bulle qui le contient. */
    val vertical: Boolean = false, val lineHeight: Float = 0f,
    val lineBoxes: List<RectF> = emptyList(), val lineTexts: List<String> = emptyList(),
    val blockKind: String = "", val bubble: RectF? = null,
    /** Effacement précis : seules les lettres (et un léger contour) sont remplacées, pas toute la zone du texte (évite le grand rectangle clair derrière la traduction). */
    val precise: Boolean = false,
) {
    /** Vrai si cette correction ne change rien (à ne pas conserver). */
    val isNeutral: Boolean
        get() = kind == EditKind.ADJUST && translation == null && source == null && !hidden && dx == 0f && dy == 0f && scale == 1f && fontId == null && !precise
}

/** Ce que le rendu doit appliquer à un bloc en plus de sa mise en page automatique (coordonnées de la fenêtre traitée). */
class RenderEdit(
    val dx: Float, val dy: Float, val scale: Float, val fontId: String?,
    /** Zone choisie à la main : l'effacement ne touche rien en dehors. */
    val region: UserRegion?, val manual: Boolean, val editId: String,
    /** Ne pas traduire : l'image n'est pas touchée, mais le bloc reste (patch invisible) pour pouvoir être rouvert dans l'éditeur. */
    val hidden: Boolean = false,
    /** Effacement précis des lettres seules (voir [BlockEdit.precise]). */
    val precise: Boolean = false,
) {
    /** Position fixée par l'utilisateur : la mise en page automatique ne la déplace plus. */
    val pinned: Boolean get() = manual || dx != 0f || dy != 0f
}

/** Enregistrement texte des corrections (sans bibliothèque JSON : le cœur est du Kotlin pur). */
object EditsCodec {
    private const val SEP = '\u001F'

    private fun rect(r: RectF?) = if (r == null) "-" else "${r.left},${r.top},${r.right},${r.bottom}"
    private fun parseRect(s: String): RectF? {
        if (s == "-" || s.isEmpty()) return null
        val p = s.split(',').mapNotNull { it.toFloatOrNull() }
        return if (p.size == 4) RectF(p[0], p[1], p[2], p[3]) else null
    }
    private fun nstr(s: String?) = if (s == null) "N" else "S" + esc(s)
    private fun parseNstr(s: String): String? = if (s.startsWith("S")) unesc(s.substring(1)) else null

    private fun strokes(r: UserRegion?): String = if (r == null) "-" else (rect(r.rect) + "|" + r.strokes.joinToString("/") { s ->
        "${s.radius}:${if (s.erase) 1 else 0}:" + s.points.joinToString(";") { "${it.x},${it.y}" }
    })

    private fun parseRegion(s: String): UserRegion? {
        if (s == "-" || s.isEmpty()) return null
        val i = s.indexOf('|')
        val rc = parseRect(if (i < 0) s else s.substring(0, i)) ?: return null
        val strokes = if (i < 0 || i == s.length - 1) emptyList() else s.substring(i + 1).split('/').mapNotNull { part ->
            val f = part.split(':')
            if (f.size < 3) return@mapNotNull null
            val radius = f[0].toFloatOrNull() ?: return@mapNotNull null
            val pts = f[2].split(';').mapNotNull { q -> val c = q.split(','); if (c.size == 2) { val x = c[0].toFloatOrNull(); val y = c[1].toFloatOrNull(); if (x != null && y != null) Pt(x, y) else null } else null }
            if (pts.isEmpty()) null else BrushStroke(pts, radius, f[1] == "1")
        }
        return UserRegion(rc, strokes)
    }

    fun encode(e: BlockEdit): String = listOf(
        "E", e.id, e.kind.name, rect(e.match), esc(e.matchSource), strokes(e.region), nstr(e.source), nstr(e.translation), if (e.hidden) "1" else "0",
        "${e.dx}", "${e.dy}", "${e.scale}", nstr(e.fontId), if (e.vertical) "1" else "0", "${e.lineHeight}",
        e.lineBoxes.joinToString(";") { rect(it) }, e.lineTexts.joinToString(SEP.toString()) { esc(it) }, esc(e.blockKind), rect(e.bubble), if (e.precise) "1" else "0",
    ).joinToString("\t")

    fun decode(line: String): BlockEdit? {
        val f = line.split('\t')
        if (f.size < 19 || f[0] != "E") return null
        val kind = try { EditKind.valueOf(f[2]) } catch (_: Exception) { return null }
        return try {
            BlockEdit(
                id = f[1], kind = kind, match = parseRect(f[3]), matchSource = unesc(f[4]), region = parseRegion(f[5]), source = parseNstr(f[6]), translation = parseNstr(f[7]),
                hidden = f[8] == "1", dx = f[9].toFloat(), dy = f[10].toFloat(), scale = f[11].toFloat(), fontId = parseNstr(f[12]), vertical = f[13] == "1", lineHeight = f[14].toFloat(),
                lineBoxes = if (f[15].isEmpty()) emptyList() else f[15].split(';').mapNotNull { parseRect(it) },
                lineTexts = if (f[16].isEmpty()) emptyList() else f[16].split(SEP).map { unesc(it) },
                blockKind = unesc(f[17]), bubble = parseRect(f[18]), precise = f.getOrNull(19) == "1",
            )
        } catch (_: Exception) { null }
    }

    /** Fichier complet : une ligne « I » par image (clé, dernier usage) suivie de ses corrections « E ». */
    fun serialize(images: Map<String, Pair<Long, List<BlockEdit>>>): String = buildString {
        for ((key, v) in images) { append("I\t").append(key).append('\t').append(v.first).append('\n'); v.second.forEach { append(encode(it)).append('\n') } }
    }

    fun parse(data: String): LinkedHashMap<String, Pair<Long, MutableList<BlockEdit>>> {
        val out = LinkedHashMap<String, Pair<Long, MutableList<BlockEdit>>>()
        var cur: MutableList<BlockEdit>? = null
        for (line in data.lines()) {
            if (line.startsWith("I\t")) {
                val p = line.split('\t')
                if (p.size >= 3) { val l = ArrayList<BlockEdit>(); out[p[1]] = (p[2].toLongOrNull() ?: 0L) to l; cur = l } else cur = null
            } else if (line.startsWith("E\t")) decode(line)?.let { cur?.add(it) }
        }
        return out
    }
}
