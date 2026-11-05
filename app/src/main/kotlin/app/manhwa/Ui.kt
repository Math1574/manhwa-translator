package app.manhwa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- fond, cartes, boutons, en-têtes : le vocabulaire visuel de l'application ----------

/** Fond de l'application : dégradé « crépuscule » avec une lune diffuse en haut, ou fond uni (nuit, noir pur) selon le réglage d'apparence. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val look = LocalLook.current
    val h = look.hue
    val base = when (look.background) {
        "noir" -> Modifier.background(Color.Black)
        "nuit" -> Modifier.background(MaterialTheme.colorScheme.background)
        else -> Modifier.background(Brush.verticalGradient(listOf(Color.hsv(h, 0.78f, 0.075f), Color.hsv((h + 8f) % 360f, 0.66f, 0.18f))))
    }
    val moon = if (look.background == "crepuscule") Modifier.drawBehind {
        val c = Offset(size.width * 0.82f, size.height * 0.05f); val r = size.width * 0.95f
        drawCircle(Brush.radialGradient(listOf(Color.hsv((h + 12f) % 360f, 0.40f, 1f).copy(alpha = 0.17f), Color.Transparent), center = c, radius = r), radius = r, center = c)
    } else Modifier
    Box(modifier.then(base).then(moon), content = content)
}

/** Carte « verre » : voile blanc translucide, liseré lumineux dégradé, et petit rebond quand on la touche. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, contentPadding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !look.reducedMotion) 0.985f else 1f, spring(dampingRatio = 0.6f, stiffness = 520f), label = "cardScale")
    val veil by animateFloatAsState(if (pressed) 0.15f else 0.085f, tween(140), label = "cardVeil")
    val shape = RoundedCornerShape(22.dp)
    var m = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = veil), Color.White.copy(alpha = veil * 0.5f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))), shape)
    if (onClick != null) m = m.clickable(interactionSource = src, indication = null, onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

/** Bouton principal : pastille à dégradé accent, rebond au toucher. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "btnScale")
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .background(if (enabled) Brush.horizontalGradient(listOf(cs.primary, cs.secondary)) else SolidColor(cs.surfaceVariant))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = cs.onPrimary); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) cs.onPrimary else cs.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

/** Bouton secondaire : contour fin, texte d'accent. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "ghostScale")
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .background(Color.White.copy(alpha = if (pressed) 0.10f else 0.04f))
            .border(1.dp, if (enabled) cs.primary.copy(alpha = 0.55f) else cs.outlineVariant, CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) { Text(text, color = if (enabled) cs.primary else cs.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
}

/** La mascotte, qui flotte doucement (immobile si les animations sont réduites ou la mascotte masquée dans les réglages). */
@Composable
fun MascotImage(size: Dp, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (!look.mascot) return
    val dy = if (look.reducedMotion) 0f else rememberInfiniteTransition(label = "mascot").animateFloat(-3f, 3f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob").value
    Image(painterResource(R.drawable.mascot), contentDescription = null, modifier = modifier.size(size).graphicsLayer { translationY = dy * density })
}

/** En-tête d'écran : grand titre arrondi, sous-titre et mascotte. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MascotImage(78.dp)
    }
}

/** Titre de section : petit, espacé, dans la couleur d'accent. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

/**
 * Arrivée en douceur d'un élément de liste (fondu et petit glissement, décalés selon la position). Une clé déjà vue n'est pas rejouée quand on fait défiler la liste ;
 * rien n'est animé si les animations sont réduites.
 */
@Composable
fun Entrance(key: Any, index: Int, seen: MutableSet<Any>, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val skip = remember { look.reducedMotion || key in seen || index > 9 }
    if (skip) { content(); return }
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(Unit) { seen.add(key) }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(380, delayMillis = 55 * index)) + slideInVertically(tween(380, delayMillis = 55 * index)) { it / 5 },
    ) { content() }
}
