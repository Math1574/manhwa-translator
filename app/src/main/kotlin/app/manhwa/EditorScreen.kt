package app.manhwa

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.manhwa.core.BrushStroke
import app.manhwa.core.EditKind
import app.manhwa.core.FontLibrary
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.RectF
import app.manhwa.core.Pt
import app.manhwa.core.UserRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------- Éditeur de correction d'un texte traduit ----------------
// Ouvert par l'appui long sur une traduction puis « Corriger cette traduction » : zone du texte (rectangle ou pinceau), texte lu et traduction, position et taille,
// police, termes du glossaire. L'aperçu au-dessus est calculé par le moteur de rendu lui-même : ce que l'on voit est ce qui sera posé sur la page.

private enum class EditorTab(val label: String) { TEXT("Texte"), ZONE("Zone"), POSITION("Position"), FONT("Police"), GLOSSARY("Glossaire") }
private enum class ZoneMode { RECT, BRUSH }
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
    var translation by mutableStateOf(block.translated)
    var hidden by mutableStateOf(existing?.hidden == true)
    val isNew = sn.isNew

    // zone du texte (coordonnées de la fenêtre traitée)
    var zoneMode by mutableStateOf(ZoneMode.RECT)
    var rect by mutableStateOf(initialRect())
    val strokes = mutableStateListOf<BrushStroke>()
    var brush by mutableFloatStateOf(max(8f, min(40f, (block.lineHeight.takeIf { it > 0f } ?: 30f) * 0.6f)))
    var zoneChanged by mutableStateOf(sn.isNew)
    var read by mutableStateOf<TranslationController.RegionRead?>(null)
    /** Après la lecture d'une zone, celle-ci est ajustée aux lignes lues (effacement plus précis). */
    var autoFit by mutableStateOf(true)
    /** Effacement précis : seules les lettres sont remplacées, pas toute la zone. */
    var precise by mutableStateOf(existing?.precise == true)

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
    var crop by mutableStateOf(initialCrop())

    init {
        val reg = existing?.region
        if (existing?.kind == EditKind.MANUAL && reg != null) {
            val shifted = reg.shifted(sn.info.ctxH.toFloat())
            rect = shifted.bounds
            if (shifted.painted) { strokes.addAll(shifted.strokes); zoneMode = ZoneMode.BRUSH }
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

    fun region(): UserRegion = if (zoneMode == ZoneMode.BRUSH && strokes.isNotEmpty()) UserRegion(rect, strokes.toList()) else UserRegion(norm(rect))

    private fun norm(r: RectF) = RectF(min(r.left, r.right), min(r.top, r.bottom), max(r.left, r.right), max(r.top, r.bottom))

    fun normalizeRect() { rect = norm(rect) }

    fun syncBrushRect() { if (strokes.isNotEmpty()) rect = UserRegion(rect, strokes.toList()).bounds }

    fun resetZone() { strokes.clear(); rect = initialRect(); zoneChanged = sn.isNew; read = null; zoneMode = ZoneMode.RECT }

    /** Lignes dont la zone peut épouser la forme : celles qui viennent d'être lues, sinon celles du texte détecté. */
    fun fitLines(): List<RectF> = (read?.lineBoxes ?: block.lineBoxes)

    /**
     * Ajuste la zone aux lignes de texte : une barre arrondie par ligne (trait de pinceau le long de la ligne, un peu plus épais que le texte). La zone est ainsi plus précise qu'un
     * grand rectangle, et l'effacement aussi ; l'effacement précis des lettres est activé.
     */
    fun snapToLines() {
        val lines = fitLines()
        if (lines.isEmpty()) return
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

    val canSave: Boolean get() = !(isNew && translation.isBlank())

    fun fields(forPreview: Boolean) = TranslationController.EditFields(
        src, translation, hidden && !forPreview, if (zoneChanged || existing?.kind == EditKind.MANUAL) region() else null, zoneChanged, read,
        if (forPreview) 0f else dx, if (forPreview) 0f else dy, scale, fontId, precise,
    )

    /** Change à chaque modification qui demande un nouveau calcul de l'aperçu (le déplacement du texte, lui, se voit sans recalcul). */
    val previewKey: Int get() = listOf(translation, fontId, scale, rect, strokes.size, zoneChanged, zoneMode, precise).hashCode()

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

    fun termList(): List<GlossaryEntry> = terms.filter { it.src.isNotBlank() && it.tgt.isNotBlank() }.map { GlossaryEntry(it.src.trim(), it.tgt.trim()) }
}

@Composable
fun BlockEditor(graph: AppGraph, target: EditTarget, onClose: () -> Unit) {
    val c = graph.controller
    val sn = remember(target) { c.editSession(target) }
    if (sn == null) { LaunchedEffect(target) { onClose() }; return }
    val st = remember(sn) { EditorState(sn) }
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<Pair<Bitmap, Float>?>(null) }
    BackHandler(onBack = onClose)

    // vue : l'image d'origine autour du texte (recadrage de la fenêtre traitée)
    LaunchedEffect(st.crop) { base = withContext(Dispatchers.IO) { c.decodeCrop(sn.info.bytes, st.crop, 1600) } }
    // aperçu en direct, recalculé 250 ms après la dernière modification
    LaunchedEffect(st.previewKey) {
        delay(250)
        st.busy = true
        val p = withContext(Dispatchers.Default) { try { c.renderEditPreview(sn, c.makeEdit(sn, st.fields(true))) } catch (e: Throwable) { null } }
        st.busy = false
        st.preview = p
    }

    // posé sur toute l'application (même fenêtre, mêmes marges que le reste) ; la surface empêche les touchers de passer à la page dessous
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Annuler") }
                Text(
                    if (st.isNew) "Ajouter une traduction" else "Corriger la traduction", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Button(onClick = { c.saveEdit(sn, c.makeEdit(sn, st.fields(false)), st.termList(), st.termsForSeries && sn.info.series != null) }, enabled = st.canSave) { Text("Enregistrer") }
            }
            EditCanvas(st, base, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st.tab != EditorTab.ZONE) FilterChip(selected = st.showOriginal, onClick = { st.showOriginal = !st.showOriginal }, label = { Text("Voir l'original") })
                TextButton(onClick = { st.widen() }) { Text("Élargir la vue") }
                if (sn.existing != null) TextButton(onClick = { c.resetEdit(sn) }) { Text("Rétablir l'automatique") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorTab.values().forEach { t -> FilterChip(selected = st.tab == t, onClick = { st.tab = t }, label = { Text(t.label) }) }
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
                    else -> GlossaryTab(graph, st)
                }
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun EditCanvas(st: EditorState, base: Pair<Bitmap, Float>?, modifier: Modifier) {
    val baseImage: ImageBitmap? = remember(base) { base?.first?.asImageBitmap() }
    val pv = st.preview
    val eraseImage: ImageBitmap? = remember(pv) { pv?.sink?.erase?.asImageBitmap() }
    val textImage: ImageBitmap? = remember(pv) { pv?.sink?.text?.asImageBitmap() }
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.background(Color(0xFF1C1C1C)), contentAlignment = Alignment.Center) {
        val crop = st.crop
        val view = min(constraints.maxWidth.toFloat() / crop.width, constraints.maxHeight.toFloat() / crop.height)
        val density = LocalDensity.current
        val wDp = with(density) { (crop.width * view).toDp() }
        val hDp = with(density) { (crop.height * view).toDp() }
        Canvas(
            Modifier.size(wDp, hDp).pointerInput(st.tab, st.zoneMode, view, crop) {
                var handle = Handle.NONE
                var pts: MutableList<Pt>? = null
                val grab = 32.dp.toPx() / view
                detectDragGestures(
                    onDragStart = { o ->
                        val x = crop.left + o.x / view; val y = crop.top + o.y / view
                        if (st.tab == EditorTab.ZONE) {
                            st.zoneChanged = true
                            if (st.zoneMode == ZoneMode.RECT) {
                                handle = st.hit(x, y, grab)
                                if (handle == Handle.NONE) { st.rect = RectF(x, y, x, y); handle = Handle.BR }
                            } else {
                                pts = mutableListOf(Pt(x, y))
                                st.strokes.add(BrushStroke(listOf(Pt(x, y)), st.brush))
                            }
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        when (st.tab) {
                            EditorTab.ZONE -> if (st.zoneMode == ZoneMode.RECT) st.dragRect(handle, delta.x / view, delta.y / view) else {
                                val p = pts
                                if (p != null) {
                                    p.add(Pt(crop.left + change.position.x / view, crop.top + change.position.y / view))
                                    st.strokes[st.strokes.lastIndex] = BrushStroke(p.toList(), st.brush)
                                }
                            }
                            EditorTab.POSITION -> { st.dx += delta.x / view; st.dy += delta.y / view }
                            else -> {}
                        }
                    },
                    onDragEnd = { handle = Handle.NONE; pts = null; st.normalizeRect(); st.syncBrushRect() },
                    onDragCancel = { handle = Handle.NONE; pts = null; st.normalizeRect(); st.syncBrushRect() },
                )
            },
        ) {
            fun vx(x: Float) = (x - crop.left) * view
            fun vy(y: Float) = (y - crop.top) * view
            if (baseImage != null) drawImage(baseImage, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val sink = pv?.sink
            val r = sink?.rect
            if (sink != null && r != null && eraseImage != null && textImage != null && st.tab != EditorTab.ZONE && !st.showOriginal) {
                val ox = vx(r.left).roundToInt(); val oy = vy(r.top).roundToInt()
                val sz = IntSize((r.width * view).roundToInt(), (r.height * view).roundToInt())
                drawImage(eraseImage, dstOffset = IntOffset(ox, oy), dstSize = sz)
                drawImage(textImage, dstOffset = IntOffset(ox + (st.dx * view).roundToInt(), oy + (st.dy * view).roundToInt()), dstSize = sz)
            }
            if (st.tab == EditorTab.ZONE) {
                st.read?.lineBoxes?.forEach { b ->
                    drawRect(Color(0xFF19E36B), Offset(vx(b.left), vy(b.top)), Size(b.width * view, b.height * view), style = Stroke(1.dp.toPx()))
                }
                if (st.zoneMode == ZoneMode.BRUSH && st.strokes.isNotEmpty()) {
                    for (s in st.strokes) {
                        val w = s.radius * 2f * view
                        if (s.points.size == 1) drawCircle(primary.copy(alpha = 0.45f), s.radius * view, Offset(vx(s.points[0].x), vy(s.points[0].y)))
                        else {
                            val path = Path()
                            s.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(vx(p.x), vy(p.y)) else path.lineTo(vx(p.x), vy(p.y)) }
                            drawPath(path, primary.copy(alpha = 0.45f), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                } else {
                    val q = RectF(min(st.rect.left, st.rect.right), min(st.rect.top, st.rect.bottom), max(st.rect.left, st.rect.right), max(st.rect.top, st.rect.bottom))
                    val tl = Offset(vx(q.left), vy(q.top)); val sz = Size(q.width * view, q.height * view)
                    drawRect(primary.copy(alpha = 0.16f), tl, sz)
                    drawRect(primary, tl, sz, style = Stroke(2.dp.toPx()))
                    val hs = listOf(q.left to q.top, q.centerX to q.top, q.right to q.top, q.right to q.centerY, q.right to q.bottom, q.centerX to q.bottom, q.left to q.bottom, q.left to q.centerY)
                    for ((hx, hy) in hs) { drawCircle(Color.White, 8.dp.toPx(), Offset(vx(hx), vy(hy))); drawCircle(primary, 5.5.dp.toPx(), Offset(vx(hx), vy(hy))) }
                }
            }
            if (st.tab == EditorTab.POSITION && sink != null) {
                val b = sink.baseRect
                if (b != null) {
                    val tl = Offset(vx(b.left + st.dx), vy(b.top + st.dy)); val sz = Size(b.width * view, b.height * view)
                    drawRect(primary, tl, sz, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                }
            }
        }
        if (st.busy || st.reading || st.translating) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp), strokeWidth = 2.5.dp)
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
                if (t.isNullOrBlank()) st.message = "La traduction a échoué : réessayez." else { st.translation = t; st.message = null }
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
    st.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ZoneTab(c: TranslationController, st: EditorState, scope: kotlinx.coroutines.CoroutineScope) {
    if (st.isNew) Text(
        "Placez le rectangle sur le texte à traduire (ou dessinez-le au pinceau), puis touchez « Lire le texte de cette zone ».",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = st.zoneMode == ZoneMode.RECT, onClick = { st.zoneMode = ZoneMode.RECT }, label = { Text("Rectangle") })
        FilterChip(selected = st.zoneMode == ZoneMode.BRUSH, onClick = { st.zoneMode = ZoneMode.BRUSH }, label = { Text("Pinceau libre") })
    }
    Text(
        if (st.zoneMode == ZoneMode.RECT) "Glissez les poignées pour ajuster le rectangle, glissez à l'intérieur pour le déplacer, ou tracez-en un nouveau en dehors."
        else "Dessinez du doigt sur le texte à traduire : seule la zone peinte est lue et effacée.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (st.zoneMode == ZoneMode.BRUSH) {
        Text("Taille du pinceau", style = MaterialTheme.typography.labelMedium)
        Slider(st.brush, { st.brush = it }, valueRange = 6f..90f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (st.strokes.isNotEmpty()) { st.strokes.removeAt(st.strokes.lastIndex); st.syncBrushRect() } }, enabled = st.strokes.isNotEmpty(), modifier = Modifier.weight(1f),
            ) { Text("Annuler le trait", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            OutlinedButton(onClick = { st.strokes.clear() }, enabled = st.strokes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Tout effacer", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
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
                    st.src = r.source; st.translation = r.translation; st.read = r; st.zoneChanged = true
                    if (st.autoFit) st.snapToLines()       // la zone épouse les lignes lues : cadres verts = lignes lues
                }
            }
        },
        enabled = !st.reading, modifier = Modifier.fillMaxWidth(),
    ) { Text(if (st.reading) "Lecture en cours…" else "Lire le texte de cette zone") }
    ToggleRow(st.autoFit, { st.autoFit = it }, "Ajuster automatiquement la zone aux lignes lues")
    OutlinedButton(onClick = { st.snapToLines() }, enabled = st.fitLines().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Ajuster la zone au texte lu") }
    ToggleRow(st.precise, { st.precise = it }, "Effacer seulement les lettres (plus précis)")
    Text(
        "Ajustée aux lignes, la zone n'efface que le texte : plus de grand rectangle clair derrière la traduction. Si des restes de l'original apparaissent, désactivez l'effacement précis.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = { st.resetZone() }, enabled = st.zoneChanged && !st.isNew || st.strokes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Rétablir la zone détectée") }
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
    Text("Taille du texte : ${(st.scale * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium)
    Slider(st.scale, { st.scale = it }, valueRange = 0.5f..2f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { st.dx -= 3f }, modifier = Modifier.weight(1f)) { Text("◀") }
        OutlinedButton(onClick = { st.dy -= 3f }, modifier = Modifier.weight(1f)) { Text("▲") }
        OutlinedButton(onClick = { st.dy += 3f }, modifier = Modifier.weight(1f)) { Text("▼") }
        OutlinedButton(onClick = { st.dx += 3f }, modifier = Modifier.weight(1f)) { Text("▶") }
    }
    OutlinedButton(
        onClick = {
            val sink = st.preview?.sink; val b = sink?.baseRect; val t = sink?.target
            if (b != null && t != null) { st.dx = t.first - b.centerX; st.dy = t.second - b.centerY }
        },
        enabled = st.preview != null, modifier = Modifier.fillMaxWidth(),
    ) { Text("Centrer dans la bulle") }
    OutlinedButton(onClick = { st.dx = 0f; st.dy = 0f; st.scale = 1f }, enabled = st.dx != 0f || st.dy != 0f || st.scale != 1f, modifier = Modifier.fillMaxWidth()) { Text("Réinitialiser position et taille") }
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
        "Ajoutez les noms ou les termes à toujours traduire de la même façon : le texte est retraduit avec eux dès l'enregistrement (sauf si vous avez saisi la traduction vous-même).",
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
