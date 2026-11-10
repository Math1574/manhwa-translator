package app.manhwa.core

import kotlin.math.max
import kotlin.math.min

data class GrouperConfig(
    /** Confiance minimale pour l'alphabet latin (les débris d'OCR y donnent du charabia). */
    val minConfidence: Float = 0.4f,
    /** Confiance minimale pour le CJK : ML Kit y donne naturellement des scores plus bas (onomatopées, lettrages). */
    val minConfidenceCjk: Float = 0.22f,
    /** Écart max entre deux lignes d'un même bloc, en multiples de la hauteur (ou largeur si vertical). */
    val gapFactor: Float = 0.9f,
    val minOverlap: Float = 0.3f,
    val minSidePx: Float = 6f,
)

/**
 * Regroupe les lignes OCR en blocs (bulles). Méthode purement géométrique :
 * elle peut fusionner à tort deux bulles très proches ou séparer une bulle aux lignes très espacées.
 */
class BlockGrouper(private val cfg: GrouperConfig = GrouperConfig()) {

    fun group(lines: List<OcrLine>): List<TextBlock> {
        val kept = dedupe(lines.filter { isUsable(it) })
        if (kept.isEmpty()) return emptyList()

        val parent = IntArray(kept.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        fun join(a: Int, b: Int) { parent[find(a)] = find(b) }

        for (i in kept.indices) for (j in i + 1 until kept.size) {
            if (shouldJoin(kept[i], kept[j])) join(i, j)
        }

        val groups = kept.indices.groupBy { find(it) }.values
        val blocks = groups.map { idxs ->
            val ls = idxs.map { kept[it] }
            val vertical = ls.count { isVertical(it) } * 2 > ls.size
            val sorted = if (vertical) {
                ls.sortedWith(compareByDescending<OcrLine> { it.box.centerX }.thenBy { it.box.top })
            } else {
                readingOrder(ls)
            }
            val box = sorted.map { it.box }.reduce { a, b -> a.union(b) }
            Triple(sorted, box, vertical)
        }.sortedWith(compareBy({ it.second.top }, { it.second.left }))

        return blocks.mapIndexed { i, (ls, box, vertical) ->
            TextBlock(i, ls, box, joinText(ls.map { it.text }), vertical)
        }
    }

    /**
     * Regroupement par zones détectées : chaque ligne va à la zone de texte (texte dans une bulle ou hors bulle) qui la contient, au lieu d'être
     * rapprochée de ses voisines. Deux bulles qui se touchent, un cartouche de narration collé à une bulle ou un texte libre proche d'un dialogue
     * ne sont plus fusionnés. Les lignes sans zone retombent sur le regroupement géométrique. Sans détection : regroupement géométrique.
     */
    fun group(lines: List<OcrLine>, detections: List<Detection>): List<TextBlock> {
        val anchors = mergeAnchors(detections.filter { it.cls != RegionClass.BUBBLE })
        if (anchors.isEmpty()) return group(lines)
        val bubbles = detections.filter { it.cls == RegionClass.BUBBLE }
        // une ligne à l'intérieur d'une zone détectée est confirmée : on tolère une confiance de lecture plus basse (onomatopées, lettrages)
        fun anchorOf(l: OcrLine): Int {
            var best = -1; var bestRatio = 0.5f
            for ((i, a) in anchors.withIndex()) {
                val inter = l.box.intersection(a.box)?.area ?: 0f
                val ratio = if (l.box.area <= 0f) 0f else inter / l.box.area
                if (ratio > bestRatio + 1e-4f || (best >= 0 && ratio >= bestRatio - 1e-4f && ratio >= 0.5f && a.box.area < anchors[best].box.area)) { best = i; bestRatio = max(bestRatio, ratio) }
            }
            return best
        }
        // une ligne qui prolonge directement un bloc confirmé par le détecteur (même colonne, interligne normal, taille voisine, au moins trois lettres) est acceptée avec le seuil assoupli, même
        // hors de la zone : le détecteur coupe parfois la zone une ligne trop tôt (« THING TO DO IS… », dernière ligne d'une bulle, restait en anglais sous la traduction des quatre premières)
        val confirmed = lines.filter { l -> anchorOf(l) >= 0 && (dropReason(l) == null || dropReason(l, relaxed = true) == null) }
        fun continuesConfirmed(l: OcrLine): Boolean {
            if (l.text.count { it.isLetter() } < 3 || dropReason(l, relaxed = true) != null) return false
            val h = l.box.height
            return confirmed.any { a ->
                val ratio = if (a.box.height <= 0f || h <= 0f) 0f else max(a.box.height, h) / min(a.box.height, h)
                a !== l && ratio <= 1.7f && shouldJoin(a, l) && l.box.top >= a.box.top
            }
        }
        val usable = lines.filter { l -> dropReason(l) == null || (anchorOf(l) >= 0 && dropReason(l, relaxed = true) == null) || continuesConfirmed(l) }
        val kept = dedupe(usable)
        if (kept.isEmpty()) return emptyList()
        val byAnchor = HashMap<Int, MutableList<OcrLine>>(); var loose = ArrayList<OcrLine>()
        for (l in kept) { val a = anchorOf(l); if (a >= 0) byAnchor.getOrPut(a) { ArrayList() }.add(l) else loose.add(l) }
        // le détecteur n'encadre parfois qu'une partie du texte (la première ligne seulement) : une ligne sans zone se rattache au bloc détecté
        // le plus proche si elle s'y joint géométriquement ou se trouve dans la même bulle ; sinon elle reste libre
        val rest = ArrayList<OcrLine>()
        for (l in loose) {
            var best = -1; var bestD = Float.MAX_VALUE
            for ((i, ls) in byAnchor) {
                val ub = ls.map { it.box }.reduce { a, b -> a.union(b) }
                val sameBubble = bubbles.any { b -> b.box.contains(l.box.centerX, l.box.centerY) && b.box.contains(ub.centerX, ub.centerY) }
                val d = rectGap(ub, l.box)
                // « même bulle » ne suffit pas : une ligne loin du texte (logo, filigrane, bruitage au bord de la boîte) n'en fait pas partie
                val lh = ls.map { it.box.height }.sorted()[ls.size / 2]
                if (!(sameBubble && d <= 2.2f * lh) && ls.none { shouldJoin(it, l) }) continue
                if (d < bestD) { bestD = d; best = i }
            }
            if (best >= 0) byAnchor[best]!!.add(l) else rest.add(l)
        }
        loose = rest
        val blocks = ArrayList<Triple<List<OcrLine>, BlockKind, Pair<RectF?, RectF?>>>()
        for ((i, ls) in byAnchor) {
            val vertical = ls.count { isVertical(it) } * 2 > ls.size
            val sorted = if (vertical) ls.sortedWith(compareByDescending<OcrLine> { it.box.centerX }.thenBy { it.box.top }) else readingOrder(ls)
            val box = sorted.map { it.box }.reduce { a, b -> a.union(b) }
            val container = bubbles.filter { b -> (box.intersection(b.box)?.area ?: 0f) >= 0.8f * box.area }.minByOrNull { it.box.area }?.box
            val kind = if (anchors[i].cls == RegionClass.TEXT_BUBBLE || container != null) BlockKind.IN_BUBBLE else BlockKind.FREE
            blocks.add(Triple(sorted, kind, Pair(container, anchors[i].box)))
        }
        for (b in group(loose)) blocks.add(Triple(b.lines, BlockKind.FREE, Pair(null, null)))
        val ordered = blocks.map { (ls, kind, cz) -> Triple(ls, ls.map { it.box }.reduce { a, b -> a.union(b) }, Triple(kind, cz.first, cz.second)) }.sortedWith(compareBy({ it.second.top }, { it.second.left }))
        return ordered.mapIndexed { i, (ls, box, kc) ->
            val vertical = ls.count { isVertical(it) } * 2 > ls.size
            TextBlock(i, ls, box, joinText(ls.map { it.text }), vertical, kc.first, kc.second, kc.third)
        }
    }

    /** Distance entre deux rectangles (0 s'ils se touchent ou se recouvrent). */
    private fun rectGap(a: RectF, b: RectF): Float {
        val dx = max(0f, max(a.left, b.left) - min(a.right, b.right)); val dy = max(0f, max(a.top, b.top) - min(a.bottom, b.bottom))
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Zones de texte sans doublons : deux zones qui se recouvrent à plus de 50 % (même classe ou non) sont une seule zone, la plus sûre l'emporte. */
    private fun mergeAnchors(ds: List<Detection>): List<Detection> {
        val out = ArrayList<Detection>()
        for (d in ds.sortedByDescending { it.score }) {
            val inter = { k: Detection -> d.box.intersection(k.box)?.area ?: 0f }
            val dup = out.any { k -> val i = inter(k); i / max(1f, d.box.area + k.box.area - i) > 0.5f || i >= 0.85f * min(d.box.area, k.box.area) }
            if (!dup) out.add(d)
        }
        return out
    }

    /** Motif de rejet d'une ligne OCR (null si elle est utilisable) : sert au regroupement et au mode diagnostic. */
    fun dropReason(l: OcrLine, relaxed: Boolean = false): String? {
        val t = l.text.trim()
        if (t.isEmpty()) return "vide"
        // filigrane du site : écarté ligne par ligne, avant tout regroupement (rattaché à une bulle voisine, il ferait supprimer toute la bulle)
        if (JunkFilter.isWatermark(t)) return "filigrane"
        // ligne située dans une zone confirmée par le détecteur : seuils assouplis
        val minConf = if (relaxed) 0.12f else if (t.any { it.isCjk() }) cfg.minConfidenceCjk else cfg.minConfidence
        if (l.confidence < minConf) return "confiance"
        if (l.box.isEmpty || min(l.box.width, l.box.height) < (if (relaxed) 4f else cfg.minSidePx)) return "trop petit"
        if (!t.any { it.isLetterOrDigit() || it.isCjk() }) return "ponctuation"
        return null
    }

    /** Ordre de lecture horizontal : les lignes se chevauchant verticalement forment une rangée, lue de gauche à droite ; rangées de haut en bas. */
    private fun readingOrder(ls: List<OcrLine>): List<OcrLine> {
        val rows = ArrayList<MutableList<OcrLine>>()
        for (l in ls.sortedBy { it.box.top }) {
            val row = rows.firstOrNull { r ->
                val ref = r.first().box
                val inter = min(ref.bottom, l.box.bottom) - max(ref.top, l.box.top)
                inter >= 0.5f * min(ref.height, l.box.height)
            }
            if (row != null) row.add(l) else rows.add(mutableListOf(l))
        }
        return rows.sortedBy { r -> r.map { it.box.centerY }.average() }.flatMap { r -> r.sortedBy { it.box.left } }
    }

    private fun isUsable(l: OcrLine): Boolean = dropReason(l) == null

    private fun dedupe(lines: List<OcrLine>): List<OcrLine> {
        val out = ArrayList<OcrLine>()
        for (l in lines.sortedByDescending { it.confidence }) {
            val dup = out.any { it.box.overlapRatio(l.box) > 0.8f && similar(it.text, l.text) }
            if (!dup) out.add(l)
        }
        return out
    }

    private fun similar(a: String, b: String): Boolean {
        val x = a.filter { !it.isWhitespace() }
        val y = b.filter { !it.isWhitespace() }
        return x == y || x.contains(y) || y.contains(x)
    }

    private fun isVertical(l: OcrLine) =
        l.vertical || (l.box.height >= 2f * l.box.width && l.text.trim().length >= 2)

    private fun shouldJoin(a: OcrLine, b: OcrLine): Boolean {
        val va = isVertical(a)
        val vb = isVertical(b)
        if (va != vb) return false
        return if (va) {
            val w = min(a.box.width, b.box.width)
            if (max(a.box.width, b.box.width) > 2.2f * w) return false // colonnes de tailles très différentes : éléments distincts
            val gap = max(a.box.left, b.box.left) - min(a.box.right, b.box.right)
            val ov = overlap1d(a.box.top, a.box.bottom, b.box.top, b.box.bottom)
            gap <= cfg.gapFactor * w && ov >= cfg.minOverlap
        } else {
            // lignes de hauteurs très différentes (gros lettrage, onomatopée penchée, petit texte) : éléments distincts
            val h = min(a.box.height, b.box.height)
            if (max(a.box.height, b.box.height) > 2.2f * h) return false
            val gapY = max(a.box.top, b.box.top) - min(a.box.bottom, b.box.bottom)
            val ovX = overlap1d(a.box.left, a.box.right, b.box.left, b.box.right)
            val ovY = overlap1d(a.box.top, a.box.bottom, b.box.top, b.box.bottom)
            val gapX = max(a.box.left, b.box.left) - min(a.box.right, b.box.right)
            val stacked = gapY <= cfg.gapFactor * h && ovX >= cfg.minOverlap
            val sameRow = ovY >= 0.5f && gapX <= h
            stacked || sameRow
        }
    }

    /** Recouvrement 1D rapporté au plus petit segment (peut être négatif si disjoints). */
    private fun overlap1d(a0: Float, a1: Float, b0: Float, b1: Float): Float {
        val inter = min(a1, b1) - max(a0, b0)
        val m = min(a1 - a0, b1 - b0)
        return if (m <= 0f) 0f else inter / m
    }

    private fun joinText(parts: List<String>): String {
        val sb = StringBuilder()
        for (p in parts.map { it.trim() }.filter { it.isNotEmpty() }) {
            if (sb.isNotEmpty() && !(sb.last().isCjk() && p.first().isCjk())) sb.append(' ')
            sb.append(p)
        }
        return sb.toString()
    }
}
