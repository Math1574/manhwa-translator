package app.manhwa.core

import kotlin.math.max

/**
 * Applique les corrections manuelles d'une image au résultat de sa lecture et de sa traduction. Les corrections sont conservées indépendamment du cache des résultats : elles
 * survivent à un changement de moteur ou de réglage, et se réappliquent à chaque affichage de la page.
 *
 * Les coordonnées des corrections sont celles de l'image ; le résultat, lui, est dans les coordonnées de la fenêtre traitée (l'image précédée du bas de l'image du dessus) : [shiftY]
 * est la hauteur de ce contexte.
 */
object EditApplier {
    /** Les blocs définis à la main ont des identifiants à partir de cette valeur (les blocs détectés vont de 0 à quelques dizaines). */
    const val MANUAL_ID_BASE = 100_000

    /** Résultat : page modifiée, directives de rendu par identifiant de bloc, blocs retirés (remplacés par un bloc défini à la main), correction appliquée à chaque bloc. */
    class Applied(val page: PageResult, val render: Map<Int, RenderEdit>, val removed: List<TranslatedBlock>, val editByBlock: Map<Int, BlockEdit>)

    fun apply(result: PageResult, edits: List<BlockEdit>, shiftY: Float = 0f): Applied {
        val blocks = ArrayList(result.blocks)
        val render = HashMap<Int, RenderEdit>()
        val removed = ArrayList<TranslatedBlock>()
        val byBlock = HashMap<Int, BlockEdit>()
        // 1) corrections de blocs détectés
        for (e in edits) {
            if (e.kind != EditKind.ADJUST) continue
            val target = findBlock(blocks, e, shiftY) ?: continue
            val i = blocks.indexOf(target)
            blocks[i] = target.copy(source = e.source ?: target.source, translated = e.translation ?: target.translated)
            render[target.id] = RenderEdit(e.dx, e.dy, e.scale, e.fontId, null, false, e.id, e.hidden, e.precise, anchorX(e), anchorY(e, shiftY))
            byBlock[target.id] = e
        }
        // 2) blocs définis à la main : ils remplacent le bloc détecté qu'ils corrigent et ceux qu'ils recouvrent
        var next = MANUAL_ID_BASE
        for (e in edits) {
            if (e.kind != EditKind.MANUAL) continue
            val region = e.region?.shifted(shiftY) ?: continue
            val replaced = if (e.match != null) findBlock(blocks, e, shiftY) else null
            if (replaced != null) { blocks.remove(replaced); removed.add(replaced) }
            val zone = region.bounds
            for (b in blocks.filter { it.id < MANUAL_ID_BASE && insideFraction(it.box, zone) >= 0.5f }) { blocks.remove(b); removed.add(b) }
            val boxes = e.lineBoxes.map { it.offset(0f, shiftY) }.ifEmpty { listOf(zone) }
            val box = boxes.reduce { a, b -> a.union(b) }
            val heights = boxes.map { if (e.vertical) it.width else it.height }.sorted()
            val lh = if (e.lineHeight > 0f) e.lineHeight else heights[heights.size / 2]
            val id = next++
            blocks.add(
                TranslatedBlock(
                    id, box, e.source ?: "", e.translation ?: "", e.vertical, lh, boxes, e.lineTexts.takeIf { it.size == boxes.size } ?: emptyList(), "",
                    e.blockKind, e.bubble?.offset(0f, shiftY), zone,
                ),
            )
            render[id] = RenderEdit(e.dx, e.dy, e.scale, e.fontId, region, true, e.id, e.hidden, e.precise, anchorX(e), anchorY(e, shiftY))
            byBlock[id] = e
        }
        return Applied(PageResult(result.imageWidth, result.imageHeight, blocks), render, removed, byBlock)
    }

    private fun anchorX(e: BlockEdit): Float? = if (e.anchored) e.anchorX else null
    private fun anchorY(e: BlockEdit, shiftY: Float): Float? = if (e.anchored) e.anchorY!! + shiftY else null

    /** Bloc détecté visé par une correction : celui dont la boîte recouvre le mieux la boîte conservée, ou à défaut le même texte lu au même endroit. */
    private fun findBlock(blocks: List<TranslatedBlock>, e: BlockEdit, shiftY: Float): TranslatedBlock? {
        val m = e.match?.offset(0f, shiftY) ?: return null
        var best: TranslatedBlock? = null; var bestIou = 0f
        for (b in blocks) {
            if (b.id >= MANUAL_ID_BASE) continue
            val iou = iou(b.box, m)
            val sameText = e.matchSource.isNotBlank() && norm(b.source) == norm(e.matchSource)
            val score = if (sameText && iou > 0.1f) max(iou, 0.45f) else iou
            if (score > bestIou) { bestIou = score; best = b }
        }
        return if (bestIou >= 0.4f) best else null
    }

    private fun norm(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    private fun iou(a: RectF, b: RectF): Float {
        val i = a.intersection(b)?.area ?: return 0f
        val u = a.area + b.area - i
        return if (u <= 0f) 0f else i / u
    }

    /** Part de la surface de [box] qui est dans [zone]. */
    private fun insideFraction(box: RectF, zone: RectF): Float {
        val a = box.area
        if (a <= 0f) return 0f
        return (box.intersection(zone)?.area ?: 0f) / a
    }

    /** Remplace ou ajoute une correction (même identifiant) dans une liste ; une correction neutre est retirée. */
    fun upsert(list: List<BlockEdit>, edit: BlockEdit): List<BlockEdit> {
        val rest = list.filter { it.id != edit.id }
        return if (edit.isNeutral) rest else rest + edit
    }

    /** Centre vers lequel ramener un texte pour le « centrer » : celui de la bulle si elle est connue, sinon de la zone, sinon du texte d'origine. */
    fun centerTarget(bubble: RectF?, zone: RectF?, original: RectF): Pair<Float, Float> {
        val r = bubble ?: zone ?: original
        return r.centerX to r.centerY
    }

    /** Décalage (dx, dy) qui amène le centre du texte placé [placed] sur celui de [target]. */
    fun centerOffset(placed: RectF, target: Pair<Float, Float>): Pair<Float, Float> = (target.first - placed.centerX) to (target.second - placed.centerY)

    /**
     * Décalage à appliquer à la mise en page automatique d'un bloc pour respecter la position choisie par l'utilisateur, ou null s'il n'y a rien à appliquer.
     * [base] : texte placé par la mise en page automatique d'aujourd'hui (taille choisie comprise) ; [original] : boîte du texte d'origine.
     *
     * – Position en absolu (ancre) : le centre du texte va exactement là où l'utilisateur l'a posé, quelle que soit la mise en page automatique.
     * – Position relative d'une ancienne version (dx, dy sans ancre) : elle compensait la mise en page de l'époque. Si la mise en page a changé depuis, la ré-appliquer pousse le texte
     *   loin de sa place (vu : 373 px au-dessus de son cadre, 100 px au-dessus de sa bulle). Elle n'est donc retenue que si le texte décalé reste sur le texte d'origine (voir [staysOnText]).
     *   Un bloc défini à la main garde son décalage : sa zone est celle de l'utilisateur, la mise en page ne s'y éloigne pas.
     */
    fun positionShift(ed: RenderEdit, base: RectF, original: RectF): Pair<Float, Float>? {
        val ax = ed.anchorX; val ay = ed.anchorY
        if (ax != null && ay != null) return (ax - base.centerX) to (ay - base.centerY)
        if (ed.dx == 0f && ed.dy == 0f) return null
        if (ed.manual) return ed.dx to ed.dy
        return if (staysOnText(base.offset(ed.dx, ed.dy), original)) ed.dx to ed.dy else null
    }

    /** Vrai si le texte placé [placed] recouvre encore le texte d'origine [original] (agrandi de 15 % de la hauteur) : au moins le quart du plus petit des deux. */
    fun staysOnText(placed: RectF, original: RectF): Boolean {
        val grow = 0.15f * max(original.height, placed.height)
        val zone = original.inflate(grow, grow)
        val i = placed.intersection(zone) ?: return false
        return i.area >= 0.25f * kotlin.math.min(placed.area, zone.area)
    }

    /** Centre absolu d'un texte décalé de ([dx], [dy]) depuis le texte placé automatiquement [base] ; null si le texte n'est pas déplacé ou si [base] est inconnu. */
    fun anchorOf(base: RectF?, dx: Float, dy: Float): Pair<Float, Float>? {
        if (base == null || (dx == 0f && dy == 0f)) return null
        return (base.centerX + dx) to (base.centerY + dy)
    }
}
