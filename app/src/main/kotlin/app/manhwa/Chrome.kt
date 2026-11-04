package app.manhwa

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class NavTab(val label: String, val icon: ImageVector)

/** Barre d'onglets : la pastille de l'onglet choisi est entourée d'une lueur violette qui s'allume au toucher, avec un léger retour haptique. */
@Composable
fun GlowNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(cs.surface, cs.background)))
            .drawBehind { drawLine(color = cs.outlineVariant, start = Offset(0f, 0f), end = Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val glowA by animateFloatAsState(if (sel) 1f else if (pressed) 0.8f else 0f, tween(260), label = "tabGlow")
            val pill by animateFloatAsState(if (sel) 1f else 0f, tween(220), label = "tabPill")
            val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(110), label = "tabScale")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .clickable(interactionSource = src, indication = null) { if (!sel) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) } }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(width = 60.dp, height = 32.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .drawBehind {
                            if (glowA > 0.01f) {
                                val r = size.width * 0.8f
                                drawCircle(brush = Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.5f * glowA), Color.Transparent), radius = r), radius = r)
                            }
                        }
                        .clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer.copy(alpha = pill)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, t.label, tint = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (sel) cs.primary else cs.onSurfaceVariant, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/**
 * Bouton rond Original | Traduit, posé au bord de l'écran et déplaçable au doigt (il se range sur le bord le plus proche et mémorise sa place) : on peut l'écarter des boutons du site.
 * Éteint : traduction désactivée ; entouré : activée mais cette page n'est pas un chapitre ; allumé et pulsant : cette page est traduite.
 */
@Composable
fun TranslateOrb(
    translateOn: Boolean, active: Boolean, running: Boolean, progress: Float,
    right: Boolean, yFrac: Float, onTap: () -> Unit, onMoved: (right: Boolean, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val orb = 50.dp
        val orbPx = with(density) { orb.toPx() }; val marginPx = with(density) { 6.dp.toPx() }
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        var drag by remember { mutableStateOf<Offset?>(null) }
        val baseX = if (right) w - orbPx - marginPx else marginPx
        val baseY = ((h - orbPx) * yFrac).coerceAtLeast(0f)
        val pos = drag ?: Offset(baseX, baseY)
        val pulse = if (active) rememberInfiniteTransition(label = "orbPulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse").value else 0f
        val glowLevel = if (active) 0.25f + 0.35f * pulse else if (translateOn) 0.22f else 0f
        val desc = if (active) "Traduction activée : toucher pour revenir à l'original" else if (translateOn) "Traduire cette page" else "Traduire"
        Box(
            Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }.size(orb)
                .semantics { contentDescription = desc }
                .drawBehind {
                    if (glowLevel > 0.01f) {
                        val r = size.width * 0.95f
                        drawCircle(brush = Brush.radialGradient(listOf(cs.primary.copy(alpha = glowLevel), cs.secondary.copy(alpha = glowLevel * 0.35f), Color.Transparent), radius = r), radius = r)
                    }
                }
                .pointerInput(w, h, right, yFrac) {
                    detectDragGestures(
                        onDragStart = { drag = Offset(baseX, baseY) },
                        onDragEnd = { val d = drag; drag = null; if (d != null) onMoved(d.x + orbPx / 2f > w / 2f, (d.y / (h - orbPx).coerceAtLeast(1f)).coerceIn(0.04f, 0.96f)) },
                        onDragCancel = { drag = null },
                        onDrag = { change, amount ->
                            change.consume()
                            val cur = drag ?: Offset(baseX, baseY)
                            drag = Offset((cur.x + amount.x).coerceIn(0f, w - orbPx), (cur.y + amount.y).coerceIn(0f, h - orbPx))
                        },
                    )
                }
                .clip(CircleShape)
                .background(if (active) Brush.linearGradient(listOf(cs.primary, cs.secondary)) else Brush.linearGradient(listOf(cs.surface.copy(alpha = 0.92f), cs.surfaceVariant.copy(alpha = 0.92f))))
                .then(if (active) Modifier else Modifier.border(1.5.dp, if (translateOn) cs.primary else cs.outline, CircleShape))
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Translate, null, Modifier.size(26.dp), tint = if (active) cs.onPrimary else if (translateOn) cs.primary else cs.onSurfaceVariant)
            if (running) CircularProgressIndicator(progress = { progress.coerceIn(0.03f, 1f) }, modifier = Modifier.fillMaxSize(), strokeWidth = 2.5.dp, color = cs.tertiary, trackColor = Color.Transparent)
        }
    }
}
