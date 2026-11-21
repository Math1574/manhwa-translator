package app.manhwa

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.manhwa.core.BrushStroke
import app.manhwa.core.EditApplier
import app.manhwa.core.EditKind
import app.manhwa.core.EditorView
import app.manhwa.core.FontLibrary
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.History
import app.manhwa.core.RectF
import app.manhwa.core.Pt
import app.manhwa.core.TextFormat
import app.manhwa.core.TextLook
import app.manhwa.core.UserRegion
import app.manhwa.core.ZoneShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------- Éditeur de correction d'un texte traduit ----------------
// Ouvert par l'appui long sur une traduction puis « Corriger cette traduction » : zone du texte (rectangle ou pinceau), texte lu et traduction, position et taille,
// police, termes du glossaire. L'aperçu au-dessus est calculé par le moteur de rendu lui-même : ce que l'on voit est ce qui sera posé sur la page.

private enum class EditorTab(val label: String) { TEXT("Texte"), ZONE("Zone"), POSITION("Position"), LAYOUT("Disposition"), FONT("Police"), COLOR("Couleur"), GLOSSARY("Glossaire") }
/** Forme de la zone : rectangle, ovale ou rectangle arrondi (formes prédéfinies, poignées sur le rectangle englobant) ou pinceau libre. */
private enum class ZoneMode { RECT, ELLIPSE, ROUNDED, BRUSH }
/** Ce que règle l'onglet « Couleur » : le remplissage du texte, son contour ou sa lueur. */
private enum class LookTarget(val label: String) { FILL("Lettres"), OUTLINE("Contour"), GLOW("Lueur") }
private enum class Handle { TL, T, TR, R, BR, B, BL, L, MOVE, NONE }

private class TermRow(s0: String = "", t0: String = "") {
    var src by mutableStateOf(s0)
    var tgt by mutableStateOf(t0)
}

private class EditorState(val sn: TranslationController.EditSession) {
    val block = sn.block
    val existing = sn.existing
    val iw = sn.info.result.imageWidth.toFloat()
    val ih = sn.info.result.imageHeight.toFloat()

    var tab by mutableStateOf(if (sn.isNew) EditorTab.ZONE else EditorTab.TEXT)
    var src by mutableStateOf(block.source)
    // les retours à la ligne d'une traduction automatique sont des espaces : seuls ceux tapés par l'utilisateur sont conservés (voir BlockEdit.hasBreaks)
    var translation by mutableStateOf(if (existing?.hasBreaks == true) block.translated else block.translated.replace('\n', ' '))
    var hidden by mutableStateOf(existing?.hidden == true)
    val isNew = sn.isNew

    // zone du texte (coordonnées de la fenêtre traitée)
    var zoneMode by mutableStateOf(ZoneMode.RECT)
    /** Pinceau libre : la gomme retire la zone peinte là où l'on passe, au lieu d'en ajouter. */
    var erasing by mutableStateOf(false)
    var rect by mutableStateOf(initialRect())
    val strokes = mutableStateListOf<BrushStroke>()
    var brush by mutableFloatStateOf(max(8f, min(40f, (block.lineHeight.takeIf { it > 0f } ?: 30f) * 0.6f)))
    var zoneChanged by mutableStateOf(sn.isNew)
    var read by mutableStateOf<TranslationController.RegionRead?>(null)
    /** Après la lecture d'une zone, celle-ci est ajustée aux lignes lues (effacement plus précis). */
    var autoFit by mutableStateOf(true)
    /** Effacement précis : seules les lettres sont remplacées, pas toute la zone. */
    var precise by mutableStateOf(existing?.precise == true)
    /** Carré d'effacement : toute la zone est effacée (remplie avec ce qui l'entoure), même là où aucun texte n'a été lu. */
    var wipe by mutableStateOf(existing?.wipe == true || sn.startWipe)

    // mise en forme (onglet Disposition) et apparence (onglet Couleur) du texte traduit
    var format by mutableStateOf(existing?.format ?: TextFormat())
    var look by mutableStateOf(existing?.look ?: TextLook())
    var lookTarget by mutableStateOf(LookTarget.FILL)
    /** Pipette : un toucher sur l'image prélève sa couleur pour [lookTarget]. */
    var pipette by mutableStateOf(false)
    /** Image d'origine affichée et région qu'elle couvre : la pipette y prélève ses couleurs. */
    var baseBmp: Bitmap? = null
    var baseCovers: RectF? = null

    // position et taille du texte traduit
    var dx by mutableFloatStateOf(existing?.dx ?: 0f)
    var dy by mutableFloatStateOf(existing?.dy ?: 0f)
    var scale by mutableFloatStateOf(existing?.scale ?: 1f)
    var fontId by mutableStateOf(existing?.fontId)

    // glossaire
    val terms = mutableStateListOf(TermRow())
    var termsForSeries by mutableStateOf(sn.seriesName != null)

    // aperçu et vue
    var preview by mutableStateOf<TranslationController.EditPreview?>(null)
    var busy by mutableStateOf(false)
    var reading by mutableStateOf(false)
    var translating by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var showOriginal by mutableStateOf(false)
    private val crop0: RectF = initialCrop()
    /** Vue affichée (pixels de la fenêtre traitée) : se déplace et se zoome à deux doigts. */
    var crop by mutableStateOf(crop0)
    /** Vrai quand la vue n'est plus celle du départ (calculé à part : l'écran ne se redessine que lorsque ce changement-là survient, pas à chaque pas du geste). */
    val zoomed: Boolean by derivedStateOf { crop != crop0 }
    fun resetView() { crop = crop0 }

    // historique « défaire / refaire » des gestes sur la zone et la position du texte (le texte lui-même a son propre défaire, au clavier)
    private class Geo(
        val rect: RectF, val strokes: List<BrushStroke>, val mode: ZoneMode, val zoneChanged: Boolean, val precise: Boolean, val dx: Float, val dy: Float, val scale: Float,
        val wipe: Boolean, val format: TextFormat, val look: TextLook,
    )
    private val history = History<Geo>(80)
    var undoCount by mutableIntStateOf(0)
    var redoCount by mutableIntStateOf(0)
    /** Vrai pendant qu'un curseur de taille est tiré : un seul état est enregistré pour tout le glissement. */
    var sliding = false

    private fun geo() = Geo(rect, strokes.toList(), zoneMode, zoneChanged, precise, dx, dy, scale, wipe, format, look)

    private fun restore(g: Geo) {
        rect = g.rect; strokes.clear(); strokes.addAll(g.strokes); zoneMode = g.mode
        zoneChanged = g.zoneChanged; precise = g.precise; dx = g.dx; dy = g.dy; scale = g.scale; wipe = g.wipe; format = g.format; look = g.look
    }

    private fun syncHistory() { undoCount = history.undoCount; redoCount = history.redoCount }

    /** À appeler juste avant un changement de zone ou de position : l'état d'avant pourra être rétabli. */
    fun mark() { history.record(geo()); syncHistory() }
    fun undo() { history.undo(geo())?.let { restore(it) }; syncHistory() }
    fun redo() { history.redo(geo())?.let { restore(it) }; syncHistory() }
    /** Geste interrompu aussitôt (un second doigt se pose) : l'état d'avant le geste revient, sans rien laisser à refaire. */
    fun revert() { history.revert()?.let { restore(it) }; syncHistory() }

    init {
        val reg = existing?.region
        if (existing?.kind == EditKind.MANUAL && reg != null) {
            val shifted = reg.shifted(sn.info.ctxH.toFloat())
            rect = shifted.bounds
            if (shifted.painted) { strokes.addAll(shifted.strokes); zoneMode = ZoneMode.BRUSH }
            else zoneMode = when (shifted.shape) { ZoneShape.ELLIPSE -> ZoneMode.ELLIPSE; ZoneShape.ROUNDED -> ZoneMode.ROUNDED; else -> ZoneMode.RECT }
        }
    }

    private fun textBox(): RectF = block.lineBoxes.ifEmpty { listOf(block.box) }.reduce { a, b -> a.union(b) }

    private fun initialRect(): RectF {
        val u = textBox(); val z = block.zone
        return if (z != null && z.area <= 6f * u.area) z.union(u) else u
    }

    private fun initialCrop(): RectF {
        var r = textBox().union(initialRect())
        block.bubble?.let { b -> if (b.area <= 4f * r.area) r = r.union(b) }
        val k = if (sn.isNew) 0.7f else 0.35f           // texte à ajouter : plus de contexte autour du rectangle de départ
        val px = max(70f, r.width * k); val py = max(70f, r.height * k)
        return fit(RectF(r.left - px, r.top - py, r.right + px, r.bottom + py))
    }

    /** Recadrage borné à la fenêtre, d'au moins 320 × 240 pixels, aux coordonnées entières (la correspondance vue ↔ fenêtre reste exacte). */
    fun fit(r: RectF): RectF {
        var l = r.left; var t = r.top; var rr = r.right; var b = r.bottom
        if (rr - l < 320f) { val c = (l + rr) / 2f; l = c - 160f; rr = c + 160f }
        if (b - t < 240f) { val c = (t + b) / 2f; t = c - 120f; b = c + 120f }
        l = max(0f, l); t = max(0f, t); rr = min(iw, rr); b = min(ih, b)
        return RectF(l.toInt().toFloat(), t.toInt().toFloat(), max(l + 8f, rr).toInt().toFloat(), max(t + 8f, b).toInt().toFloat())
    }

    fun widen() {
        val c = crop; val cx = c.centerX; val cy = c.centerY
        crop = fit(RectF(cx - c.width * 0.8f, cy - c.height * 0.8f, cx + c.width * 0.8f, cy + c.height * 0.8f))
    }

    private fun shapeOf(m: ZoneMode): ZoneShape = when (m) { ZoneMode.ELLIPSE -> ZoneShape.ELLIPSE; ZoneMode.ROUNDED -> ZoneShape.ROUNDED; else -> ZoneShape.RECT }

    fun region(): UserRegion = if (zoneMode == ZoneMode.BRUSH && strokes.isNotEmpty()) UserRegion(rect, strokes.toList()) else UserRegion(norm(rect), emptyList(), shapeOf(zoneMode))

    /** Choisit la forme de la zone (rectangle, ovale, arrondi) ; changer de forme est une modification de la zone. */
    fun chooseShape(m: ZoneMode) {
        if (zoneMode == m) return
        val wasShape = zoneMode != ZoneMode.BRUSH
        zoneMode = m
        if (wasShape && m != ZoneMode.BRUSH) { zoneChanged = true }
    }

    /**
     * « Modeler au pinceau » : la forme choisie (rectangle, ovale, arrondi) devient une zone peinte, que l'on peut ensuite agrandir au pinceau ou creuser à la gomme. Elle est remplie de barres
     * arrondies qui se recouvrent, une par rangée, aussi larges que la forme à cette hauteur.
     */
    fun shapeToBrush() {
        if (zoneMode == ZoneMode.BRUSH) return
        val r = norm(rect)
        if (r.width < 8f || r.height < 8f) return
        val shape = UserRegion(r, emptyList(), shapeOf(zoneMode))
        val rad = (min(r.width, r.height) / 14f).coerceIn(5f, 24f)
        mark()
        strokes.clear()
        var y = r.top + rad
        val last = r.bottom - rad
        while (y <= last + 0.5f) {
            var x0 = Float.NaN; var x1 = Float.NaN
            var x = r.left
            while (x <= r.right) { if (shape.contains(x, y)) { if (x0.isNaN()) x0 = x; x1 = x }; x += 2f }
            if (!x0.isNaN()) {
                val a = x0 + rad; val b = x1 - rad
                strokes.add(if (b > a) BrushStroke(listOf(Pt(a, y), Pt(b, y)), rad) else BrushStroke(listOf(Pt((x0 + x1) / 2f, y)), rad))
            }
            y += rad * 1.2f
        }
        // dernière rangée collée au bord bas, si la dernière barre ne l'atteint pas
        if (strokes.isEmpty()) { strokes.add(BrushStroke(listOf(Pt(r.centerX, r.centerY)), rad)) }
        brush = rad
        zoneMode = ZoneMode.BRUSH; zoneChanged = true
        syncBrushRect()
    }

    /** Modifie la mise en forme du texte ([slider] : un seul état d'historique pour tout le glissement d'un curseur). */
    fun changeFormat(f: TextFormat, slider: Boolean = false) {
        val n = f.sane()
        if (n == format) return
        if (slider) { if (!sliding) { mark(); sliding = true } } else mark()
        format = n
    }

    /** Modifie l'apparence du texte (couleur, contour, lueur). */
    fun changeLook(l: TextLook, slider: Boolean = false) {
        val n = l.sane()
        if (n == look) return
        if (slider) { if (!sliding) { mark(); sliding = true } } else mark()
        look = n
    }

    /** Couleur du texte telle que le dernier aperçu l'a dessinée (RVB), pour partir d'elle quand on quitte le réglage automatique. */
    fun previewTextColor(): Int? = try { preview?.sink?.report?.optString("fg")?.removePrefix("#")?.toLong(16)?.toInt()?.and(0xFFFFFF) } catch (e: Throwable) { null }

    /** Couleur (RVB) de l'image d'origine au point (x, y) de la fenêtre : moyenne de 3 × 3 pixels ; null si l'image n'est pas affichée. */
    fun sampleColor(x: Float, y: Float): Int? {
        val b = baseBmp ?: return null; val c = baseCovers ?: return null
        if (c.width <= 0f || c.height <= 0f || b.width < 1 || b.height < 1) return null
        return try {
            val px = ((x - c.left) / c.width * b.width).toInt().coerceIn(0, b.width - 1); val py = ((y - c.top) / c.height * b.height).toInt().coerceIn(0, b.height - 1)
            var r = 0; var g = 0; var bl = 0; var n = 0
            for (j in -1..1) for (i in -1..1) {
                val v = b.getPixel((px + i).coerceIn(0, b.width - 1), (py + j).coerceIn(0, b.height - 1))
                r += (v shr 16) and 255; g += (v shr 8) and 255; bl += v and 255; n++
            }
            ((r / n) shl 16) or ((g / n) shl 8) or (bl / n)
        } catch (e: Throwable) { null }
    }

    /** Pipette : la couleur prélevée devient celle du réglage en cours (texte, contour ou lueur). */
    fun pickColor(x: Float, y: Float) {
        val c = sampleColor(x, y) ?: return
        when (lookTarget) {
            LookTarget.FILL -> changeLook(look.copy(fill = c))
            LookTarget.OUTLINE -> changeLook(look.copy(outline = c, outlineW = look.outlineW ?: DEFAULT_OUTLINE))
            LookTarget.GLOW -> changeLook(look.copy(glow = c, glowW = look.glowW ?: DEFAULT_GLOW))
        }
    }

    private fun norm(r: RectF) = RectF(min(r.left, r.right), min(r.top, r.bottom), max(r.left, r.right), max(r.top, r.bottom))

    fun normalizeRect() { rect = norm(rect) }

    fun syncBrushRect() { if (strokes.isNotEmpty()) rect = UserRegion(rect, strokes.toList()).bounds }

    fun resetZone() { mark(); strokes.clear(); rect = initialRect(); zoneChanged = sn.isNew; read = null; zoneMode = ZoneMode.RECT; wipe = sn.startWipe }

    /** Lignes dont la zone peut épouser la forme : celles qui viennent d'être lues, sinon celles du texte détecté. */
    fun fitLines(): List<RectF> = (read?.lineBoxes ?: block.lineBoxes)

    /**
     * Ajuste la zone aux lignes de texte : une barre arrondie par ligne (trait de pinceau le long de la ligne, un peu plus épais que le texte). La zone est ainsi plus précise qu'un
     * grand rectangle, et l'effacement aussi ; l'effacement précis des lettres est activé.
     */
    fun snapToLines() {
        val lines = fitLines()
        if (lines.isEmpty()) return
        mark()
        val vertical = read?.vertical ?: block.vertical
        strokes.clear()
        for (b in lines) {
            val thick = if (vertical) b.width else b.height
            val pad = max(3f, thick * 0.25f)
            val r = thick / 2f + pad
            if (vertical) {
                val y0 = b.top - pad + r; val y1 = max(y0, b.bottom + pad - r)
                strokes.add(BrushStroke(listOf(Pt(b.centerX, y0), Pt(b.centerX, y1)), r))
            } else {
                val x0 = b.left - pad + r; val x1 = max(x0, b.right + pad - r)
                strokes.add(BrushStroke(listOf(Pt(x0, b.centerY), Pt(x1, b.centerY)), r))
            }
        }
        zoneMode = ZoneMode.BRUSH; zoneChanged = true; precise = true
        syncBrushRect()
    }

    val canSave: Boolean get() = !(isNew && translation.isBlank() && !wipe)

    fun fields(forPreview: Boolean): TranslationController.EditFields {
        val a = if (forPreview) null else anchor()
        return TranslationController.EditFields(
            src, translation, hidden && !forPreview, if (zoneChanged || wipe || existing?.kind == EditKind.MANUAL) region() else null, zoneChanged || wipe, read,
            if (forPreview) 0f else dx, if (forPreview) 0f else dy, scale, fontId, precise, a?.first, a?.second, format, look, wipe,
        )
    }

    // Position posée à la main. L'aperçu est calculé sans décalage (c'est le texte placé automatiquement) ; le décalage (dx, dy) est relatif à lui. À l'enregistrement, la position est
    // conservée en absolu (centre du texte) : une mise en page automatique qui change plus tard ne la déplace pas. À l'ouverture, on affiche la position que la page applique vraiment :
    // une ancienne position relative qui éloignerait le texte de sa place est abandonnée (voir EditApplier.positionShift).
    private var adopted = false
    private var adoptedDx = 0f
    private var adoptedDy = 0f

    /** À appeler à chaque nouvel aperçu (le texte placé automatiquement est alors connu). */
    fun onPreview() {
        val base = preview?.sink?.baseRect ?: return
        val ex = existing
        if (!adopted) {
            adopted = true
            if (ex == null || (ex.dx == 0f && ex.dy == 0f && !ex.anchored)) return
            val applied = sn.info.applied.render[block.id]
            val sh = if (applied != null) EditApplier.positionShift(applied, base, textBox()) else null
            dx = sh?.first ?: 0f; dy = sh?.second ?: 0f
            adoptedDx = dx; adoptedDy = dy
        } else if (ex != null && ex.anchored && dx == adoptedDx && dy == adoptedDy) {
            // position enregistrée en absolu et pas touchée : elle reste où elle est même si le texte placé automatiquement a changé (traduction ou police modifiée)
            dx = ex.anchorX!! - base.centerX; dy = ex.anchorY!! + sn.info.ctxH - base.centerY
            adoptedDx = dx; adoptedDy = dy
        }
    }

    /** Centre absolu du texte posé à la main (coordonnées de la fenêtre traitée) ; null si le texte n'est pas déplacé (ou si l'aperçu n'est pas encore là). */
    fun anchor(): Pair<Float, Float>? {
        if (dx == 0f && dy == 0f) return null
        val ex = existing
        if (ex != null && ex.anchored && (!adopted || (dx == adoptedDx && dy == adoptedDy))) return ex.anchorX!! to (ex.anchorY!! + sn.info.ctxH)
        return EditApplier.anchorOf(preview?.sink?.baseRect, dx, dy)
    }

    /** Change à chaque modification qui demande un nouveau calcul de l'aperçu (le déplacement du texte, lui, se voit sans recalcul). */
    val previewKey: Int get() = listOf(translation, fontId, scale, rect, strokes.fold(0) { h, s -> h * 31 + s.points.size * 2 + (if (s.erase) 1 else 0) }, zoneChanged, zoneMode, precise, wipe, format, look).hashCode()

    fun hit(x: Float, y: Float, grab: Float): Handle {
        val r = norm(rect)
        val pts = listOf(
            Handle.TL to (r.left to r.top), Handle.T to (r.centerX to r.top), Handle.TR to (r.right to r.top), Handle.R to (r.right to r.centerY),
            Handle.BR to (r.right to r.bottom), Handle.B to (r.centerX to r.bottom), Handle.BL to (r.left to r.bottom), Handle.L to (r.left to r.centerY),
        )
        val best = pts.minByOrNull { (_, p) -> abs(p.first - x) + abs(p.second - y) }
        if (best != null && abs(best.second.first - x) <= grab && abs(best.second.second - y) <= grab) return best.first
        return if (r.contains(x, y)) Handle.MOVE else Handle.NONE
    }

    fun dragRect(h: Handle, dx: Float, dy: Float) {
        var l = rect.left; var t = rect.top; var r = rect.right; var b = rect.bottom
        when (h) {
            Handle.TL -> { l += dx; t += dy }
            Handle.T -> t += dy
            Handle.TR -> { r += dx; t += dy }
            Handle.R -> r += dx
            Handle.BR -> { r += dx; b += dy }
            Handle.B -> b += dy
            Handle.BL -> { l += dx; b += dy }
            Handle.L -> l += dx
            Handle.MOVE -> { l += dx; r += dx; t += dy; b += dy }
            Handle.NONE -> {}
        }
        rect = RectF(l.coerceIn(0f, iw), t.coerceIn(0f, ih), r.coerceIn(0f, iw), b.coerceIn(0f, ih))
    }

    /** Relevé pour les scénarios d'interface (voir [EditorProbe]) : l'éditeur ouvert, sa vue, ses traits, son historique. [covers] : région couverte par l'image décodée. */
    fun probe(covers: RectF?): Map<String, Any> {
        fun f(r: RectF) = "${r.left.roundToInt()},${r.top.roundToInt()},${r.right.roundToInt()},${r.bottom.roundToInt()}"
        val area = EditorProbe.windowArea
        return mapOf(
            "edTab" to tab.name, "edMode" to zoneMode.name, "edErasing" to erasing, "edStrokes" to strokes.count { !it.erase }, "edErase" to strokes.count { it.erase },
            "edUndo" to undoCount, "edRedo" to redoCount, "edCrop" to f(crop), "edCropW" to crop.width.roundToInt(), "edCropL" to crop.left.roundToInt(), "edCropT" to crop.top.roundToInt(),
            "edZoomed" to zoomed, "edBase" to (covers?.let { f(it) } ?: ""), "edRect" to f(rect), "edDx" to dx.roundToInt(), "edDy" to dy.roundToInt(), "edScale" to scale,
            "edArea" to (area?.joinToString(",") { it.roundToInt().toString() } ?: ""), "edWin" to "${iw.roundToInt()},${ih.roundToInt()}",
            "edWipe" to wipe, "edFmt" to format.encode(), "edLook" to look.encode(),
        )
    }

    fun termList(): List<GlossaryEntry> = terms.filter { it.src.isNotBlank() && it.tgt.isNotBlank() }.map { GlossaryEntry(it.src.trim(), it.tgt.trim()) }
}

@Composable
fun BlockEditor(graph: AppGraph, target: EditTarget, onClose: () -> Unit) {
    val c = graph.controller
    val sn = remember(target) { c.editSession(target) }
    if (sn == null) { LaunchedEffect(target) { onClose() }; return }
    val st = remember(sn) { EditorState(sn) }
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<EditBase?>(null) }
    BackHandler(onBack = onClose)

    // vue : l'image d'origine autour du texte (recadrage de la fenêtre traitée). Pendant un geste à deux doigts la vue bouge sans cesse : l'image déjà décodée suit le geste, et
    // le décodage plus net n'a lieu que quand la vue s'est posée.
    LaunchedEffect(st) {
        snapshotFlow { st.crop }.collectLatest { crop ->
            if (base != null) delay(90)
            val region = EditorView.decodeRegion(crop, st.iw, st.ih)
            val dec = withContext(Dispatchers.IO) { c.decodeCrop(sn.info.bytes, region, 1600) }
            if (dec != null) { st.baseBmp = dec.first; st.baseCovers = region; base = EditBase(dec.first, region) }
        }
    }
    DisposableEffect(st) {
        EditorProbe.info = { st.probe(base?.covers) }
        onDispose { EditorProbe.info = null; EditorProbe.host = null; EditorProbe.rootArea = null; EditorProbe.windowArea = null }
    }
    // aperçu en direct, recalculé 250 ms après la dernière modification
    LaunchedEffect(st.previewKey) {
        delay(250)
        st.busy = true
        val p = withContext(Dispatchers.Default) { try { c.renderEditPreview(sn, c.makeEdit(sn, st.fields(true))) } catch (e: Throwable) { null } }
        st.busy = false
        st.preview = p
        st.onPreview()
    }

    // posé sur toute l'application (même fenêtre, mêmes marges que le reste) ; la surface empêche les touchers de passer à la page dessous
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Annuler") }
                Text(
                    if (st.isNew) (if (sn.startWipe) "Effacer une zone" else "Ajouter une traduction") else "Corriger la traduction", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Button(onClick = { c.saveEdit(sn, c.makeEdit(sn, st.fields(false)), st.termList(), st.termsForSeries && sn.info.series != null) }, enabled = st.canSave) { Text("Enregistrer") }
            }
            EditCanvas(st, base, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st.tab != EditorTab.ZONE || st.wipe) FilterChip(selected = st.showOriginal, onClick = { st.showOriginal = !st.showOriginal }, label = { Text("Voir l'original") })
                TextButton(onClick = { st.widen() }) { Text("Élargir la vue") }
                if (st.zoomed) TextButton(onClick = { st.resetView() }) { Text("Vue d'origine") }
                if (sn.existing != null) TextButton(onClick = { c.resetEdit(sn) }) { Text("Rétablir l'automatique") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorTab.values().forEach { t -> FilterChip(selected = st.tab == t, onClick = { st.tab = t; if (t != EditorTab.COLOR && st.pipette) { st.pipette = false; st.showOriginal = false } }, label = { Text(t.label) }) }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp))
            // zone des réglages : elle prend la place restante et défile ; la marge du bas laisse remonter le dernier élément au-dessus du bord de l'écran
            val panel = Modifier.weight(0.9f).fillMaxWidth()
            if (st.tab == EditorTab.FONT) FontTab(c, st, panel)
            else Column(panel.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (st.tab) {
                    EditorTab.TEXT -> TextTab(c, st, scope)
                    EditorTab.ZONE -> ZoneTab(c, st, scope)
                    EditorTab.POSITION -> PositionTab(st)
                    EditorTab.LAYOUT -> LayoutTab(st)
                    EditorTab.COLOR -> ColorTab(st)
                    else -> GlossaryTab(graph, st)
                }
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

/** Image sur laquelle on travaille : le recadrage décodé et la région de la fenêtre qu'il couvre (la vue a pu bouger depuis : l'image est alors posée là où elle va). */
private class EditBase(val bitmap: Bitmap, val covers: RectF)

/** Ce que fait le geste en cours : rien encore, un doigt qui travaille (rectangle, pinceau, gomme, texte), la vue qui se déplace ou se zoome, ou un doigt de trop (ignoré). */
private enum class Act { NONE, DRAG, XFORM, IGNORE }

/** Un second doigt qui se pose dans ce délai après le premier (en ms) annule le trait ou le glissement commencé : c'était le début d'un zoom. */
private const val PINCH_GRACE_MS = 250L

/** Épaisseur de contour et taille de lueur proposées quand on les règle à la main (relatives : voir [TextLook]). */
private const val DEFAULT_OUTLINE = 0.12f
private const val DEFAULT_GLOW = 0.32f

/**
 * Gestes sur l'image. Un doigt : selon l'onglet, rectangle de zone, pinceau ou gomme, déplacement du texte ; dans les autres onglets il déplace la vue. Deux doigts : pincer pour zoomer,
 * glisser pour déplacer la vue, dans tous les onglets. Un doigt qui travaille depuis un moment n'est pas interrompu par un second doigt posé par mégarde.
 */
private suspend fun PointerInputScope.editGestures(st: EditorState) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val slop = viewConfiguration.touchSlop
        val tStart = down.uptimeMillis
        var act = Act.NONE
        val mainId: PointerId = down.id
        val anchor = down.position           // là où le premier doigt s'est posé
        var last = anchor                    // dernière position traitée de ce doigt
        var handle = Handle.NONE
        var pts: MutableList<Pt>? = null

        fun layout() = EditorView.layout(st.crop, size.width.toFloat(), size.height.toFloat())

        /** Commence l'action du doigt à l'endroit où il s'est posé ; faux si l'onglet n'a rien à faire à un doigt. */
        fun startDrag(): Boolean {
            val lay = layout()
            val x = lay.imageX(anchor.x, st.crop); val y = lay.imageY(anchor.y, st.crop)
            when (st.tab) {
                EditorTab.ZONE -> {
                    st.mark(); st.zoneChanged = true
                    if (st.zoneMode != ZoneMode.BRUSH) {
                        handle = st.hit(x, y, 32.dp.toPx() / lay.scale)
                        if (handle == Handle.NONE) { st.rect = RectF(x, y, x, y); handle = Handle.BR }
                    } else {
                        val p = mutableListOf(Pt(x, y)); pts = p
                        st.strokes.add(BrushStroke(p.toList(), st.brush, st.erasing))
                    }
                    return true
                }
                EditorTab.POSITION -> { st.mark(); return true }
                else -> return false
            }
        }

        fun dragTo(pos: Offset) {
            val lay = layout()
            when (st.tab) {
                EditorTab.ZONE -> if (st.zoneMode != ZoneMode.BRUSH) st.dragRect(handle, (pos.x - last.x) / lay.scale, (pos.y - last.y) / lay.scale) else {
                    val p = pts
                    if (p != null) {
                        p.add(Pt(lay.imageX(pos.x, st.crop), lay.imageY(pos.y, st.crop)))
                        st.strokes[st.strokes.lastIndex] = BrushStroke(p.toList(), st.brush, st.erasing)
                    }
                }
                EditorTab.POSITION -> { st.dx += (pos.x - last.x) / lay.scale; st.dy += (pos.y - last.y) / lay.scale }
                else -> {}
            }
            last = pos
        }

        fun endDrag(cancel: Boolean) {
            if (cancel) st.revert() else if (st.tab == EditorTab.ZONE) { st.normalizeRect(); st.syncBrushRect() }
            handle = Handle.NONE; pts = null
        }

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                if (act == Act.DRAG) endDrag(false)
                else if (act == Act.NONE && st.tab == EditorTab.COLOR && st.pipette) { val lay = layout(); st.pickColor(lay.imageX(anchor.x, st.crop), lay.imageY(anchor.y, st.crop)) }       // simple toucher : la pipette prélève la couleur de l'image
                break
            }

            if (act != Act.XFORM && pressed >= 2) {
                val young = event.changes.first().uptimeMillis - tStart < PINCH_GRACE_MS
                if (act == Act.NONE || (act == Act.DRAG && young)) {
                    if (act == Act.DRAG) endDrag(true)
                    act = Act.XFORM
                }
            }

            when (act) {
                Act.NONE -> {
                    val ch = event.changes.firstOrNull { it.id == mainId } ?: continue
                    if ((ch.position - anchor).getDistance() > slop) {
                        if (startDrag()) act = Act.DRAG else act = Act.XFORM
                    }
                }
                else -> {}
            }

            when (act) {
                Act.DRAG -> {
                    val ch = event.changes.firstOrNull { it.id == mainId }
                    if (ch == null || !ch.pressed) { endDrag(false); act = Act.IGNORE }
                    else if (ch.position != last) { dragTo(ch.position); ch.consume() }
                }
                Act.XFORM -> {
                    val zoom = event.calculateZoom(); val pan = event.calculatePan(); val from = event.calculateCentroid(useCurrent = false)
                    if (from.isSpecified && (zoom != 1f || pan != Offset.Zero)) {
                        st.crop = EditorView.transform(st.crop, size.width.toFloat(), size.height.toFloat(), from.x, from.y, from.x + pan.x, from.y + pan.y, zoom, st.iw, st.ih)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun EditCanvas(st: EditorState, base: EditBase?, modifier: Modifier) {
    val baseImage: ImageBitmap? = remember(base) { base?.bitmap?.asImageBitmap() }
    val pv = st.preview
    val eraseImage: ImageBitmap? = remember(pv) { pv?.sink?.erase?.asImageBitmap() }
    val textImage: ImageBitmap? = remember(pv) { pv?.sink?.text?.asImageBitmap() }
    val primary = MaterialTheme.colorScheme.primary
    val host = LocalView.current
    Box(
        modifier.background(Color(0xFF1C1C1C)).clipToBounds()
            .onGloballyPositioned { EditorProbe.place(host, it) }
            .semantics { contentDescription = "Zone de dessin de l'image" }
            .pointerInput(st) { editGestures(st) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val crop = st.crop
            val lay = EditorView.layout(crop, size.width, size.height)
            val view = lay.scale
            fun vx(x: Float) = lay.screenX(x, crop)
            fun vy(y: Float) = lay.screenY(y, crop)
            clipRect(lay.offX, lay.offY, lay.offX + crop.width * view, lay.offY + crop.height * view) {
                val b = base
                if (baseImage != null && b != null) {
                    val cv = b.covers
                    drawImage(baseImage, dstOffset = IntOffset(vx(cv.left).roundToInt(), vy(cv.top).roundToInt()), dstSize = IntSize((cv.width * view).roundToInt(), (cv.height * view).roundToInt()))
                }
                val sink = pv?.sink
                val r = sink?.rect
                if (sink != null && r != null && eraseImage != null && textImage != null && (st.tab != EditorTab.ZONE || st.wipe) && !st.showOriginal) {
                    val ox = vx(r.left).roundToInt(); val oy = vy(r.top).roundToInt()
                    val sz = IntSize((r.width * view).roundToInt(), (r.height * view).roundToInt())
                    drawImage(eraseImage, dstOffset = IntOffset(ox, oy), dstSize = sz)
                    // onglet Zone : on voit le fond effacé (carré d'effacement), pas encore le texte
                    if (st.tab != EditorTab.ZONE) drawImage(textImage, dstOffset = IntOffset(ox + (st.dx * view).roundToInt(), oy + (st.dy * view).roundToInt()), dstSize = sz)
                }
                if (st.tab == EditorTab.ZONE) {
                    st.read?.lineBoxes?.forEach { lb ->
                        drawRect(Color(0xFF19E36B), Offset(vx(lb.left), vy(lb.top)), Size(lb.width * view, lb.height * view), style = Stroke(1.dp.toPx()))
                    }
                    if (st.zoneMode == ZoneMode.BRUSH && st.strokes.isNotEmpty()) {
                        // la zone peinte est dessinée d'un bloc (une seule transparence, les traits qui se recouvrent ne foncent pas) ; la gomme y creuse des trous
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint().apply { alpha = 0.45f })
                            for (s in st.strokes) {
                                val mode = if (s.erase) BlendMode.Clear else BlendMode.SrcOver
                                if (s.points.size == 1) drawCircle(primary, s.radius * view, Offset(vx(s.points[0].x), vy(s.points[0].y)), blendMode = mode)
                                else {
                                    val path = Path()
                                    s.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(vx(p.x), vy(p.y)) else path.lineTo(vx(p.x), vy(p.y)) }
                                    drawPath(path, primary, style = Stroke(s.radius * 2f * view, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = mode)
                                }
                            }
                            canvas.restore()
                        }
                    } else {
                        val q = RectF(min(st.rect.left, st.rect.right), min(st.rect.top, st.rect.bottom), max(st.rect.left, st.rect.right), max(st.rect.top, st.rect.bottom))
                        val tl = Offset(vx(q.left), vy(q.top)); val sz = Size(q.width * view, q.height * view)
                        val fillA = if (st.wipe) 0.07f else 0.16f            // zone effacée : le fond effacé reste lisible sous la forme
                        when (st.zoneMode) {
                            ZoneMode.ELLIPSE -> {
                                drawOval(primary.copy(alpha = fillA), tl, sz)
                                drawOval(primary, tl, sz, style = Stroke(2.dp.toPx()))
                                drawRect(primary.copy(alpha = 0.55f), tl, sz, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))       // rectangle englobant : c'est lui que les poignées déplacent
                            }
                            ZoneMode.ROUNDED -> {
                                val k = min(q.width, q.height) / 4f * view
                                drawRoundRect(primary.copy(alpha = fillA), tl, sz, CornerRadius(k, k))
                                drawRoundRect(primary, tl, sz, CornerRadius(k, k), style = Stroke(2.dp.toPx()))
                            }
                            else -> {
                                drawRect(primary.copy(alpha = fillA), tl, sz)
                                drawRect(primary, tl, sz, style = Stroke(2.dp.toPx()))
                            }
                        }
                        val hs = listOf(q.left to q.top, q.centerX to q.top, q.right to q.top, q.right to q.centerY, q.right to q.bottom, q.centerX to q.bottom, q.left to q.bottom, q.left to q.centerY)
                        for ((hx, hy) in hs) { drawCircle(Color.White, 8.dp.toPx(), Offset(vx(hx), vy(hy))); drawCircle(primary, 5.5.dp.toPx(), Offset(vx(hx), vy(hy))) }
                    }
                }
                if (st.tab == EditorTab.POSITION && sink != null) {
                    val bx = sink.baseRect
                    if (bx != null && bx.width > 0f) {
                        val tl = Offset(vx(bx.left + st.dx), vy(bx.top + st.dy)); val sz = Size(bx.width * view, bx.height * view)
                        drawRect(primary, tl, sz, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HistoryButton(UndoIcon, "Défaire", st.undoCount > 0) { st.undo() }
            HistoryButton(RedoIcon, "Refaire", st.redoCount > 0) { st.redo() }
        }
        if (st.busy || st.reading || st.translating) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp), strokeWidth = 2.5.dp)
    }
}

/** Petit bouton rond posé sur l'image : défaire / refaire. */
@Composable
private fun HistoryButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xB3000000), contentColor = Color.White, disabledContainerColor = Color(0x59000000), disabledContentColor = Color(0x73FFFFFF),
        ),
    ) { Icon(icon, label) }
}

// icônes « défaire » et « refaire » (tracés Material, absents de la bibliothèque d'icônes de base)
private val UndoIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Undo") {
        materialPath {
            moveTo(12.5f, 8f)
            curveToRelative(-2.65f, 0f, -5.05f, 0.99f, -6.9f, 2.6f)
            lineTo(2f, 7f)
            verticalLineToRelative(9f)
            horizontalLineToRelative(9f)
            lineToRelative(-3.62f, -3.62f)
            curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
            curveToRelative(3.54f, 0f, 6.55f, 2.31f, 7.6f, 5.5f)
            lineToRelative(2.37f, -0.78f)
            curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
            close()
        }
    }
}

private val RedoIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Redo") {
        materialPath {
            moveTo(18.4f, 10.6f)
            curveTo(16.55f, 8.99f, 14.15f, 8f, 11.5f, 8f)
            curveToRelative(-4.65f, 0f, -8.58f, 3.03f, -9.96f, 7.22f)
            lineTo(3.9f, 16f)
            curveToRelative(1.05f, -3.19f, 4.05f, -5.5f, 7.6f, -5.5f)
            curveToRelative(1.95f, 0f, 3.73f, 0.72f, 5.12f, 1.88f)
            lineTo(13f, 16f)
            horizontalLineToRelative(9f)
            verticalLineTo(7f)
            lineToRelative(-3.6f, 3.6f)
            close()
        }
    }
}

/**
 * Relevé de l'éditeur pour les scénarios d'interface automatiques (lecture seule : rien n'agit sur l'application). [info] décrit l'éditeur ouvert ; [host] et les zones permettent à un
 * test d'envoyer de vrais gestes à deux doigts sur l'image (builds debug).
 */
internal object EditorProbe {
    @Volatile var info: (() -> Map<String, Any>)? = null
    @Volatile var host: android.view.View? = null
    /** Zone de l'image dans la vue de l'application (pour envoyer des gestes) et dans la fenêtre (pour des gestes par adb) : gauche, haut, droite, bas. */
    @Volatile var rootArea: FloatArray? = null
    @Volatile var windowArea: FloatArray? = null

    fun place(view: android.view.View, c: LayoutCoordinates) {
        host = view
        val r = c.boundsInRoot(); val w = c.boundsInWindow()
        rootArea = floatArrayOf(r.left, r.top, r.right, r.bottom)
        windowArea = floatArrayOf(w.left, w.top, w.right, w.bottom)
    }
}

/** Interrupteur avec son texte, toute la ligne se touche. */
@Composable
private fun ToggleRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TextTab(c: TranslationController, st: EditorState, scope: kotlinx.coroutines.CoroutineScope) {
    OutlinedTextField(st.src, { st.src = it }, Modifier.fillMaxWidth(), label = { Text("Texte d'origine (tel que lu)") }, minLines = 1, maxLines = 4)
    OutlinedButton(
        onClick = {
            scope.launch {
                st.translating = true
                val t = withContext(Dispatchers.IO) { try { c.translateText(st.src.trim()) } catch (e: Throwable) { null } }
                st.translating = false
                if (t.isNullOrBlank()) st.message = "La traduction a échoué : réessayez." else { st.translation = t.replace('\n', ' '); st.message = null }
            }
        },
        enabled = st.src.isNotBlank() && !st.translating,
    ) { Text(if (st.translating) "Traduction en cours…" else "Retraduire le texte d'origine") }
    OutlinedTextField(st.translation, { st.translation = it }, Modifier.fillMaxWidth(), label = { Text("Traduction") }, minLines = 2, maxLines = 6)
    if (!st.isNew) ToggleRow(st.hidden, { st.hidden = it }, "Ne pas traduire : laisser l'original tel quel")
    Text(
        "Votre traduction est reprise chaque fois que ce même texte est lu. Si le texte d'origine est mal lu, corrigez-le puis touchez « Retraduire », ou redessinez la zone (onglet Zone).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Pour couper une ligne à un endroit précis, touchez Entrée dans la traduction. L'onglet Disposition règle l'alignement, le nombre de lignes, l'interligne et la casse.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    st.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ZoneTab(c: TranslationController, st: EditorState, scope: kotlinx.coroutines.CoroutineScope) {
    if (st.isNew) Text(
        if (st.wipe) "Placez la forme sur ce qu'il faut effacer (filigrane, texte non lu, dessin), puis touchez « Enregistrer ». Vous pouvez aussi saisir une traduction (onglet Texte) : elle sera posée sur la zone effacée."
        else "Placez le rectangle sur le texte à traduire (ou dessinez-le au pinceau), puis touchez « Lire le texte de cette zone ».",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
    )
    // rôle de la zone : détecter le texte à traduire, ou effacer tout ce qu'elle contient (carré d'effacement)
    Text("Cette zone sert à", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !st.wipe, onClick = { if (st.wipe) { st.mark(); st.wipe = false } }, label = { Text("Zone de détection") })
        FilterChip(selected = st.wipe, onClick = { if (!st.wipe) { st.mark(); st.wipe = true; st.zoneChanged = true } }, label = { Text("Zone d'effacement") })
    }
    Text(
        if (st.wipe) "Toute la zone est effacée : elle est remplie avec ce qui l'entoure, même sans texte (filigrane, texte non lu, dessin). Une traduction saisie est posée dessus. « Voir l'original » compare avec l'image d'origine."
        else "Le texte lu dans la zone est effacé puis remplacé par la traduction.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // forme de la zone : formes prédéfinies (les poignées déplacent le rectangle englobant) ou pinceau libre
    Text("Forme de la zone", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = st.zoneMode == ZoneMode.RECT, onClick = { st.chooseShape(ZoneMode.RECT) }, label = { Text("Rectangle") })
        FilterChip(selected = st.zoneMode == ZoneMode.ELLIPSE, onClick = { st.chooseShape(ZoneMode.ELLIPSE) }, label = { Text("Ovale") })
        FilterChip(selected = st.zoneMode == ZoneMode.ROUNDED, onClick = { st.chooseShape(ZoneMode.ROUNDED) }, label = { Text("Arrondi") })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = st.zoneMode == ZoneMode.BRUSH, onClick = { st.zoneMode = ZoneMode.BRUSH }, label = { Text("Pinceau libre") })
        if (st.zoneMode != ZoneMode.BRUSH) OutlinedButton(onClick = { st.shapeToBrush() }) { Text("Modeler au pinceau", maxLines = 1) }
    }
    Text(
        if (st.zoneMode != ZoneMode.BRUSH) "Glissez les poignées pour ajuster la forme, glissez à l'intérieur pour la déplacer, ou tracez-en une nouvelle en dehors. « Modeler au pinceau » la transforme en zone peinte, que vous pouvez agrandir au pinceau ou creuser à la gomme."
        else "Dessinez du doigt sur la zone voulue : seule la zone peinte est lue et effacée. La gomme retire la zone peinte là où vous passez.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "À deux doigts : pincez pour zoomer, glissez pour déplacer la vue. Les flèches en haut à gauche de l'image défont et refont vos gestes.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (st.zoneMode == ZoneMode.BRUSH) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !st.erasing, onClick = { st.erasing = false }, label = { Text("Peindre") })
            FilterChip(selected = st.erasing, onClick = { st.erasing = true }, label = { Text("Gommer") })
        }
        Text(if (st.erasing) "Taille de la gomme" else "Taille du pinceau", style = MaterialTheme.typography.labelMedium)
        Slider(st.brush, { st.brush = it }, valueRange = 6f..90f)
        OutlinedButton(onClick = { st.mark(); st.strokes.clear() }, enabled = st.strokes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Tout effacer", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    Button(
        onClick = {
            scope.launch {
                st.reading = true; st.message = null
                val region = st.region()
                val r = withContext(Dispatchers.IO) { try { c.readRegion(st.sn.info, region) } catch (e: Throwable) { null } }
                st.reading = false
                if (r == null) st.message = "Aucun texte n'a pu être lu dans cette zone. Agrandissez-la un peu, ou saisissez le texte à la main (onglet Texte)."
                else {
                    st.src = r.source; st.translation = r.translation.replace('\n', ' '); st.read = r; st.zoneChanged = true
                    if (st.autoFit && !st.wipe) st.snapToLines()       // la zone épouse les lignes lues : cadres verts = lignes lues (pas pour une zone d'effacement : elle garde sa forme)
                }
            }
        },
        enabled = !st.reading, modifier = Modifier.fillMaxWidth(),
    ) { Text(if (st.reading) "Lecture en cours…" else "Lire le texte de cette zone") }
    if (!st.wipe) {
        ToggleRow(st.autoFit, { st.autoFit = it }, "Ajuster automatiquement la zone aux lignes lues")
        OutlinedButton(onClick = { st.snapToLines() }, enabled = st.fitLines().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Ajuster la zone au texte lu") }
        ToggleRow(st.precise, { st.precise = it }, "Effacer seulement les lettres (plus précis)")
        Text(
            "Ajustée aux lignes, la zone n'efface que le texte : plus de grand rectangle clair derrière la traduction. Si des restes de l'original apparaissent, désactivez l'effacement précis.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(onClick = { st.resetZone() }, enabled = st.zoneChanged && !st.isNew || st.strokes.isNotEmpty() || st.zoneMode != ZoneMode.RECT, modifier = Modifier.fillMaxWidth()) { Text("Rétablir la zone détectée") }
    st.read?.let { r ->
        Text("Texte lu : ${r.source}", style = MaterialTheme.typography.bodySmall)
        if (r.translation.isNotBlank()) Text("Traduction : ${r.translation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text("Les cadres verts sur l'image sont les lignes lues. Corrigez le texte dans l'onglet Texte si besoin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    st.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun PositionTab(st: EditorState) {
    Text("Faites glisser le texte sur l'image (le cadre en pointillés montre sa place).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        "À deux doigts : pincez pour zoomer, glissez pour déplacer la vue. Les flèches en haut à gauche de l'image défont et refont vos gestes.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Taille du texte : ${(st.scale * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium)
    Slider(st.scale, { if (!st.sliding) { st.mark(); st.sliding = true }; st.scale = it }, onValueChangeFinished = { st.sliding = false }, valueRange = 0.5f..2f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { st.mark(); st.dx -= 3f }, modifier = Modifier.weight(1f)) { Text("◀") }
        OutlinedButton(onClick = { st.mark(); st.dy -= 3f }, modifier = Modifier.weight(1f)) { Text("▲") }
        OutlinedButton(onClick = { st.mark(); st.dy += 3f }, modifier = Modifier.weight(1f)) { Text("▼") }
        OutlinedButton(onClick = { st.mark(); st.dx += 3f }, modifier = Modifier.weight(1f)) { Text("▶") }
    }
    OutlinedButton(
        onClick = {
            val sink = st.preview?.sink; val b = sink?.baseRect; val t = sink?.target
            if (b != null && t != null) { st.mark(); st.dx = t.first - b.centerX; st.dy = t.second - b.centerY }
        },
        enabled = st.preview != null, modifier = Modifier.fillMaxWidth(),
    ) { Text("Centrer dans la bulle") }
    OutlinedButton(onClick = { st.mark(); st.dx = 0f; st.dy = 0f; st.scale = 1f }, enabled = st.dx != 0f || st.dy != 0f || st.scale != 1f, modifier = Modifier.fillMaxWidth()) { Text("Réinitialiser position et taille") }
}

/** Disposition du texte : alignement des lignes, nombre de lignes, interligne, casse, inclinaison. Les valeurs par défaut sont la mise en page automatique. */
@Composable
private fun LayoutTab(st: EditorState) {
    val f = st.format
    Text("Alignement du texte", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TextFormat.ALIGN_CENTER to "Centré", TextFormat.ALIGN_LEFT to "À gauche", TextFormat.ALIGN_RIGHT to "À droite").forEach { (v, label) ->
            FilterChip(selected = f.align == v, onClick = { st.changeFormat(f.copy(align = v)) }, label = { Text(label) })
        }
    }
    Text("Nombre de lignes", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in 0..6) FilterChip(selected = f.lines == n, onClick = { st.changeFormat(f.copy(lines = n)) }, label = { Text(if (n == 0) "Auto" else n.toString()) })
    }
    Text("Interligne : ${(f.spacing * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium)
    Slider(f.spacing, { st.changeFormat(f.copy(spacing = it), slider = true) }, onValueChangeFinished = { st.sliding = false }, valueRange = TextFormat.MIN_SPACING..TextFormat.MAX_SPACING)
    Text("Casse", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TextFormat.CASE_AUTO to "Telle quelle", TextFormat.CASE_UPPER to "MAJUSCULES", TextFormat.CASE_LOWER to "minuscules").forEach { (v, label) ->
            FilterChip(selected = f.caseMode == v, onClick = { st.changeFormat(f.copy(caseMode = v)) }, label = { Text(label) })
        }
    }
    Text("Inclinaison du texte : ${f.angle.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
    Slider(f.angle, { st.changeFormat(f.copy(angle = it), slider = true) }, onValueChangeFinished = { st.sliding = false }, valueRange = -TextFormat.MAX_ANGLE..TextFormat.MAX_ANGLE)
    Text(
        "Pour couper une ligne à un endroit précis, touchez Entrée dans la traduction (onglet Texte). Le nombre de lignes est un maximum visé : le texte rétrécit si la place manque.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = { st.changeFormat(TextFormat()) }, enabled = !f.isDefault, modifier = Modifier.fillMaxWidth()) { Text("Rétablir la disposition automatique") }
}

/** Couleurs proposées pour le texte, son contour et sa lueur (RVB). */
private val SWATCHES = intArrayOf(0xFFFFFF, 0x000000, 0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x5E35B1, 0xD81B60, 0x8D6E63, 0x9E9E9E)

/** Noir ou blanc, selon ce qui contraste le mieux avec la couleur [rgb]. */
private fun contrastOf(rgb: Int): Int {
    val r = (rgb shr 16) and 255; val g = (rgb shr 8) and 255; val b = rgb and 255
    return if (0.299f * r + 0.587f * g + 0.114f * b > 140f) 0x000000 else 0xFFFFFF
}

@Composable
private fun Swatch(rgb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or rgb))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x88888888), CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Choix d'une couleur pour le réglage en cours : pastilles, pipette qui prélève la couleur d'un point de l'image d'origine. [current] : couleur actuelle (RVB). */
@Composable
private fun ColorPicker(st: EditorState, current: Int?) {
    val pick: (Int) -> Unit = { rgb ->
        val l = st.look
        when (st.lookTarget) {
            LookTarget.FILL -> st.changeLook(l.copy(fill = rgb))
            LookTarget.OUTLINE -> st.changeLook(l.copy(outline = rgb, outlineW = l.outlineW ?: DEFAULT_OUTLINE))
            LookTarget.GLOW -> st.changeLook(l.copy(glow = rgb, glowW = l.glowW ?: DEFAULT_GLOW))
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (rgb in SWATCHES) Swatch(rgb, current != null && (current and 0xFFFFFF) == rgb) { pick(rgb) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = st.pipette, onClick = { st.pipette = !st.pipette; st.showOriginal = st.pipette }, label = { Text(if (st.pipette) "Pipette : touchez l'image" else "Pipette") })
        if (current != null) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or (current and 0xFFFFFF))).border(1.dp, Color(0x88888888), CircleShape))
            Text("#%06X".format(current and 0xFFFFFF), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (st.pipette) Text("Touchez un point de l'image d'origine (une lettre, un fond) : sa couleur est reprise.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Apparence du texte : couleur des lettres, contour et lueur. Chaque réglage reste automatique (mesuré sur le lettrage d'origine) tant qu'on ne le change pas. */
@Composable
private fun ColorTab(st: EditorState) {
    val l = st.look
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LookTarget.values().forEach { t -> FilterChip(selected = st.lookTarget == t, onClick = { st.lookTarget = t }, label = { Text(t.label) }) }
    }
    when (st.lookTarget) {
        LookTarget.FILL -> {
            ToggleRow(l.fill == null, { on -> st.changeLook(if (on) l.copy(fill = null) else l.copy(fill = st.previewTextColor() ?: 0x000000)) }, "Couleur automatique (celle du texte d'origine)")
            if (l.fill != null) ColorPicker(st, l.fill)
        }
        LookTarget.OUTLINE -> {
            val auto = l.outline == null && l.outlineW == null
            val base = l.fill ?: st.previewTextColor() ?: 0x000000
            ToggleRow(auto, { on -> st.changeLook(if (on) l.copy(outline = null, outlineW = null) else l.copy(outline = contrastOf(base), outlineW = DEFAULT_OUTLINE)) }, "Contour automatique")
            if (!auto) {
                ColorPicker(st, l.outline ?: contrastOf(base))
                val w = l.outlineW ?: DEFAULT_OUTLINE
                Text("Épaisseur du contour : ${(w / TextLook.MAX_OUTLINE * 100).roundToInt()} %" + (if (w <= 0f) " (aucun)" else ""), style = MaterialTheme.typography.labelMedium)
                Slider(w, { st.changeLook(l.copy(outlineW = it), slider = true) }, onValueChangeFinished = { st.sliding = false }, valueRange = 0f..TextLook.MAX_OUTLINE)
            }
        }
        LookTarget.GLOW -> {
            val auto = l.glow == null && l.glowW == null
            val base = l.fill ?: st.previewTextColor() ?: 0x000000
            ToggleRow(auto, { on -> st.changeLook(if (on) l.copy(glow = null, glowW = null) else l.copy(glow = contrastOf(base), glowW = DEFAULT_GLOW)) }, "Lueur automatique")
            if (!auto) {
                ColorPicker(st, l.glow ?: contrastOf(base))
                val w = l.glowW ?: DEFAULT_GLOW
                Text("Taille de la lueur : ${(w * 100).roundToInt()} %" + (if (w <= 0f) " (aucune)" else ""), style = MaterialTheme.typography.labelMedium)
                Slider(w, { st.changeLook(l.copy(glowW = it), slider = true) }, onValueChangeFinished = { st.sliding = false }, valueRange = 0f..TextLook.MAX_GLOW)
            }
        }
    }
    Text(
        "Automatique : le texte reprend la couleur, le contour et la lueur du lettrage d'origine. Un réglage à la main ne s'applique qu'à ce texte.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = { st.changeLook(TextLook()) }, enabled = !l.isDefault, modifier = Modifier.fillMaxWidth()) { Text("Rétablir les couleurs automatiques") }
}

/** Familles du sélecteur de police, dans l'ordre d'affichage. */
private val FONT_CLASSES = listOf(
    "comic" to "Bande dessinée", "condensed" to "Condensé", "sans" to "Sans empattement", "hand" to "Écriture à la main", "brush" to "Pinceau",
    "display" to "Titre", "serif" to "Avec empattement", "mono" to "Chasse fixe",
)

private fun fontLabel(sp: FontLibrary.Spec): String {
    val fam = sp.file.substringAfterLast('/').substringBefore('-').substringBefore('.').replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    val rest = sp.id.substringAfter('-', "").split('-').filter { it.isNotEmpty() }.joinToString(", ") { t ->
        when {
            Regex("w\\d+").matches(t) -> "graisse ${t.drop(1)}"
            Regex("d\\d+").matches(t) -> "largeur ${t.drop(1)}"
            Regex("c\\d").matches(t) -> "style ${t.drop(1)}"
            else -> t
        }
    }
    return if (rest.isEmpty()) fam else "$fam · $rest"
}

private sealed class FontRow {
    class Header(val text: String) : FontRow()
    class Auto : FontRow()
    class Item(val spec: FontLibrary.Spec) : FontRow()
}

@Composable
private fun FontTab(c: TranslationController, st: EditorState, modifier: Modifier) {
    val rows = remember {
        val l = ArrayList<FontRow>()
        l.add(FontRow.Auto())
        l.add(FontRow.Header("Recommandées"))
        FontLibrary.specs.filter { it.auto }.forEach { l.add(FontRow.Item(it)) }
        for ((cls, name) in FONT_CLASSES) {
            val items = FontLibrary.specs.filter { it.cls == cls && !it.auto }
            if (items.isNotEmpty()) { l.add(FontRow.Header(name)); items.forEach { l.add(FontRow.Item(it)) } }
        }
        l
    }
    val sample = st.translation.trim().replace('\n', ' ').take(36).ifBlank { "Aperçu du texte" }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 88.dp)) {
        items(rows) { row ->
            when (row) {
                is FontRow.Header -> Text(row.text, Modifier.padding(top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                is FontRow.Auto -> Row(Modifier.fillMaxWidth().clickable { st.fontId = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = st.fontId == null, onClick = { st.fontId = null })
                    Column {
                        Text("Automatique", style = MaterialTheme.typography.bodyLarge)
                        Text("Copie le lettrage d'origine de chaque texte", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is FontRow.Item -> {
                    val family = remember(row.spec.id) { try { FontFamily(c.typeface(row.spec.id)) } catch (e: Throwable) { FontFamily.Default } }
                    Row(Modifier.fillMaxWidth().clickable { st.fontId = row.spec.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = st.fontId == row.spec.id, onClick = { st.fontId = row.spec.id })
                        Column {
                            Text(sample, fontFamily = family, fontSize = 19.sp, maxLines = 1)
                            Text(fontLabel(row.spec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlossaryTab(graph: AppGraph, st: EditorState) {
    val series = st.sn.info.series
    Text(
        "Ajoutez les noms ou les termes à toujours traduire de la même façon. Seuls les textes qui les contiennent sont retraduits : un aperçu (avant → après) vous est montré avant l'enregistrement. Une traduction saisie par vous passe toujours avant.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = st.termsForSeries && series != null, onClick = { st.termsForSeries = true }, enabled = series != null, label = { Text("Cette œuvre", maxLines = 1) })
        FilterChip(selected = !st.termsForSeries || series == null, onClick = { st.termsForSeries = false }, label = { Text("Toutes les œuvres", maxLines = 1) })
    }
    st.sn.seriesName?.let { Text("Œuvre : $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    // mots du texte d'origine proposés d'un toucher (noms propres probables en premier)
    val words = remember(st.src) {
        Regex("[\\p{L}][\\p{L}'’-]{2,}").findAll(st.src).map { it.value }.distinctBy { it.lowercase() }
            .sortedByDescending { w -> if (w.first().isUpperCase() && w.any { it.isLowerCase() }) 1 else 0 }.take(10).toList()
    }
    if (words.isNotEmpty()) {
        Text("Mots du texte (touchez pour en faire un terme) :", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            words.forEach { w ->
                AssistChip(onClick = {
                    val empty = st.terms.firstOrNull { it.src.isBlank() }
                    if (empty != null) empty.src = w else st.terms.add(TermRow(w, ""))
                }, label = { Text(w) })
            }
        }
    }
    st.terms.forEachIndexed { i, t ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(t.src, { t.src = it }, Modifier.weight(1f), label = { Text("Dans l'original") }, singleLine = true)
            OutlinedTextField(t.tgt, { t.tgt = it }, Modifier.weight(1f), label = { Text("Traduction") }, singleLine = true)
            if (st.terms.size > 1) TextButton(onClick = { st.terms.removeAt(i) }, modifier = Modifier.width(44.dp)) { Text("✕", maxLines = 1) }
        }
    }
    OutlinedButton(onClick = { st.terms.add(TermRow()) }) { Text("Ajouter un autre terme") }
    // termes déjà connus qui apparaissent dans ce texte
    val g = graph.glossary.current(series)
    val known = g.terms().filter { e -> e.source.isNotBlank() && st.src.contains(e.source, ignoreCase = true) }
    if (known.isNotEmpty()) {
        HorizontalDivider()
        Text("Déjà dans le glossaire pour ce texte :", style = MaterialTheme.typography.labelMedium)
        known.forEach { e -> Text("${e.source}  →  ${e.target}", style = MaterialTheme.typography.bodyMedium) }
    }
}
