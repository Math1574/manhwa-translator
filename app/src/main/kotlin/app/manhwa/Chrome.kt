package app.manhwa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class NavTab(val label: String, val icon: ImageVector, val badge: Boolean = false)

/**
 * Barre d'onglets flottante : une pastille de verre arrondie ; l'onglet choisi s'élargit en douceur (ressort) et affiche son nom sur une pastille à dégradé d'accent.
 * Petit retour haptique au changement d'onglet.
 */
@Composable
fun GlowNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val look = LocalLook.current
    val shape = RoundedCornerShape(34.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().shadow(16.dp, shape, clip = false, ambientColor = Color.Black, spotColor = cs.primary)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(cs.surfaceVariant.copy(alpha = 0.96f), cs.surface.copy(alpha = 0.98f))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f))), shape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                val weight by animateFloatAsState(if (sel) 1.75f else 1f, if (look.reducedMotion) spring(stiffness = 3000f) else spring(dampingRatio = 0.78f, stiffness = 380f), label = "tabWeight")
                val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.55f, stiffness = 650f), label = "tabScale")
                Row(
                    Modifier.weight(weight).height(50.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                        .background(if (sel) Brush.horizontalGradient(listOf(cs.primaryContainer, cs.secondaryContainer)) else SolidColor(Color.Transparent))
                        .clickable(interactionSource = src, indication = null) { if (!sel) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) } },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Icon(t.icon, t.label, tint = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        // pastille : quelque chose attend (par exemple une mise à jour de l'application)
                        if (t.badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(10.dp).clip(CircleShape).background(cs.tertiary).border(1.5.dp, cs.surface, CircleShape))
                    }
                    AnimatedVisibility(visible = sel, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Row { Spacer(Modifier.width(8.dp)); Text(t.label, style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer, maxLines = 1) }
                    }
                }
            }
        }
    }
}

/**
 * Bouton rond Original | Traduit, posé au bord de l'écran et déplaçable au doigt (il se range sur le bord le plus proche et mémorise sa place) : on peut l'écarter des boutons du site.
 * Sobre pour la lecture, sans halo : verre quand la traduction est éteinte, contour d'accent quand elle est activée mais que cette page n'est pas un chapitre,
 * pastille à dégradé quand la page est traduite ; un anneau fin montre l'avancement du travail.
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
        val orb = 46.dp
        val orbPx = with(density) { orb.toPx() }; val marginPx = with(density) { 6.dp.toPx() }
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        var drag by remember { mutableStateOf<Offset?>(null) }
        val baseX = if (right) w - orbPx - marginPx else marginPx
        val baseY = ((h - orbPx) * yFrac).coerceAtLeast(0f)
        val pos = drag ?: Offset(baseX, baseY)
        val desc = if (active) "Traduction activée : toucher pour revenir à l'original" else if (translateOn) "Traduire cette page" else "Traduire"
        val idleAlpha = if (active || drag != null) 1f else 0.88f
        Box(
            Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }.size(orb)
                .semantics { contentDescription = desc }
                .graphicsLayer { alpha = idleAlpha }
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
                .shadow(7.dp, CircleShape)
                .clip(CircleShape)
                .background(if (active) Brush.linearGradient(listOf(cs.primary, cs.secondary)) else Brush.linearGradient(listOf(cs.surfaceVariant.copy(alpha = 0.94f), cs.surface.copy(alpha = 0.94f))))
                .then(if (active) Modifier else Modifier.border(1.5.dp, if (translateOn) cs.primary else cs.outline, CircleShape))
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Translate, null, Modifier.size(24.dp), tint = if (active) cs.onPrimary else if (translateOn) cs.primary else cs.onSurfaceVariant)
            if (running) CircularProgressIndicator(progress = { progress.coerceIn(0.03f, 1f) }, modifier = Modifier.fillMaxSize(), strokeWidth = 2.5.dp, color = cs.tertiary, trackColor = Color.Transparent)
        }
    }
}
