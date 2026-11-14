package app.manhwa

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.manhwa.core.AppSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- direction artistique « crépuscule » : palettes dérivées d'un accent, polices rondes, fond en dégradé ----------

private class Accent(val hue: Float, val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color, val secondary: Color, val onSecondary: Color, val secContainer: Color, val onSecContainer: Color, val tertiary: Color)

private fun accentOf(name: String): Accent = when (name) {
    "indigo" -> Accent(228f, Color(0xFF9DB4FF), Color(0xFF0B1650), Color(0xFF2D3F9E), Color(0xFFDDE4FF), Color(0xFF7FD6FF), Color(0xFF00324A), Color(0xFF0B5575), Color(0xFFD3F0FF), Color(0xFFFFC56B))
    "rose" -> Accent(330f, Color(0xFFFF9EC7), Color(0xFF50002B), Color(0xFF8A2A5E), Color(0xFFFFE0EE), Color(0xFFC9A6FF), Color(0xFF2E0F5A), Color(0xFF52318A), Color(0xFFEADDFF), Color(0xFFFFD08A))
    "emeraude" -> Accent(165f, Color(0xFF7EE3C0), Color(0xFF00382B), Color(0xFF0F6B58), Color(0xFFCFFBEA), Color(0xFF9CCBFF), Color(0xFF00305B), Color(0xFF1F4D8A), Color(0xFFD7E8FF), Color(0xFFFFD08A))
    "ambre" -> Accent(32f, Color(0xFFFFC56B), Color(0xFF4A2A00), Color(0xFF8A5300), Color(0xFFFFE8C2), Color(0xFFFF9E7A), Color(0xFF560F00), Color(0xFF8A3A22), Color(0xFFFFE0D5), Color(0xFFB79CFF))
    else -> Accent(262f, Color(0xFFB69CFF), Color(0xFF1E0B50), Color(0xFF4A2CA6), Color(0xFFE9DFFF), Color(0xFFF0A6FF), Color(0xFF3F0A52), Color(0xFF6A2A86), Color(0xFFFBDDFF), Color(0xFFFFC56B))
}

/** Réglages visuels exposés aux écrans : fond choisi, mascotte, animations réduites, teinte de l'accent. */
class Look(val background: String, val mascot: Boolean, val reducedMotion: Boolean, val hue: Float, val mascotName: String = "feuille")

val LocalLook = compositionLocalOf { Look("crepuscule", true, false, 262f) }

private fun schemeFor(accent: Accent, background: String): ColorScheme {
    val h = accent.hue
    val noir = background == "noir"
    val bg = if (noir) Color.Black else Color.hsv(h, 0.62f, if (background == "nuit") 0.085f else 0.095f)
    val surface = Color.hsv(h, if (noir) 0.40f else 0.52f, if (noir) 0.075f else 0.155f)
    val variant = Color.hsv(h, if (noir) 0.34f else 0.44f, if (noir) 0.13f else 0.24f)
    val high = Color.hsv(h, if (noir) 0.34f else 0.42f, if (noir) 0.17f else 0.28f)
    return darkColorScheme(
        primary = accent.primary, onPrimary = accent.onPrimary, primaryContainer = accent.container, onPrimaryContainer = accent.onContainer,
        secondary = accent.secondary, onSecondary = accent.onSecondary, secondaryContainer = accent.secContainer, onSecondaryContainer = accent.onSecContainer,
        tertiary = accent.tertiary, background = bg, onBackground = Color.hsv(h, 0.06f, 0.96f),
        surface = surface, onSurface = Color.hsv(h, 0.06f, 0.96f), surfaceVariant = variant, onSurfaceVariant = Color.hsv(h, 0.16f, 0.84f),
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface, surfaceContainerHigh = high, surfaceContainerHighest = high,
        outline = Color.hsv(h, 0.32f, 0.50f), outlineVariant = Color.hsv(h, 0.38f, 0.30f), error = Color(0xFFFF8A9B),
    )
}

private val Heading = FontFamily(Font(R.font.fredoka_medium, FontWeight.Medium), Font(R.font.fredoka_semibold, FontWeight.SemiBold), Font(R.font.fredoka_bold, FontWeight.Bold))
private val Body = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal), Font(R.font.nunito_semibold, FontWeight.Medium), Font(R.font.nunito_semibold, FontWeight.SemiBold), Font(R.font.nunito_extrabold, FontWeight.Bold),
)

private fun appTypography(): Typography {
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.copy(fontFamily = Heading, fontWeight = FontWeight.SemiBold),
        displayMedium = b.displayMedium.copy(fontFamily = Heading, fontWeight = FontWeight.SemiBold),
        displaySmall = b.displaySmall.copy(fontFamily = Heading, fontWeight = FontWeight.SemiBold),
        headlineLarge = b.headlineLarge.copy(fontFamily = Heading, fontWeight = FontWeight.SemiBold),
        headlineMedium = b.headlineMedium.copy(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 29.sp, lineHeight = 34.sp),
        headlineSmall = b.headlineSmall.copy(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 29.sp),
        titleLarge = b.titleLarge.copy(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
        titleMedium = b.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        titleSmall = b.titleSmall.copy(fontFamily = Heading, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.4.sp),
        bodyLarge = b.bodyLarge.copy(fontFamily = Body, fontWeight = FontWeight.Normal),
        bodyMedium = b.bodyMedium.copy(fontFamily = Body, fontWeight = FontWeight.Normal),
        bodySmall = b.bodySmall.copy(fontFamily = Body, fontWeight = FontWeight.Normal),
        labelLarge = b.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
        labelMedium = b.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
        labelSmall = b.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
    )
}

private val ManhwaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ManhwaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val accent = accentOf(settings.themeAccent)
    val look = Look(settings.themeBackground, settings.showMascot, settings.reducedMotion, accent.hue, settings.themeMascot)
    val scheme = remember(settings.themeAccent, settings.themeBackground) { schemeFor(accent, settings.themeBackground) }
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(colorScheme = scheme, shapes = ManhwaShapes, typography = remember { appTypography() }, content = content)
    }
}

/** Teinte de fond associée à un accent (aperçus des fonds dans les réglages). */
fun accentHueOf(name: String): Float = accentOf(name).hue

/** Couleurs de l'aperçu d'un accent dans les réglages (pastille). */
fun accentSwatch(name: String): Pair<Color, Color> = accentOf(name).let { it.primary to it.secondary }

/**
 * Lueur violette douce autour de l'élément quand [active] (on le touche, il est sélectionné) : trois halos de plus en plus larges et transparents, qui s'allument vite et
 * s'éteignent lentement. Dessinée derrière l'élément et hors de ses bornes.
 */
@Composable
fun Modifier.glow(active: Boolean, color: Color, corner: Dp = 16.dp, spread: Dp = 14.dp, strength: Float = 0.22f): Modifier {
    val a by animateFloatAsState(if (active) 1f else 0f, tween(if (active) 90 else 360), label = "glow")
    return this.drawBehind {
        if (a > 0.01f) {
            val sp = spread.toPx(); val cr = corner.toPx()
            for (i in 3 downTo 1) {
                val g = sp * i / 3f
                drawRoundRect(
                    color = color.copy(alpha = strength * a * (4 - i) / 3f),
                    topLeft = Offset(-g, -g), size = Size(size.width + 2 * g, size.height + 2 * g), cornerRadius = CornerRadius(cr + g),
                )
            }
        }
    }
}

/** Clic avec lueur violette tant qu'on appuie (en plus de l'ondulation habituelle). */
@Composable
fun Modifier.glowClickable(corner: Dp = 16.dp, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return this.glow(pressed, MaterialTheme.colorScheme.primary, corner).clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Couleur propre à une œuvre (stable : déduite de son titre), pour sa pastille dans la bibliothèque. */
fun seriesColor(key: String): Color {
    val h = (key.hashCode().toLong() and 0x7fffffffL) % 360L
    return Color.hsv(h.toFloat(), 0.42f, 0.62f)
}

private fun vec(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

/** Icônes absentes du jeu de base de Material (dessinées d'après les icônes Material, licence Apache 2.0). */
object AppIcons {
    val Fullscreen: ImageVector by lazy { vec("fullscreen", "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z") }
    val FullscreenExit: ImageVector by lazy { vec("fullscreen_exit", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z") }
    val Library: ImageVector by lazy { vec("library", "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8C6.9,2 6,2.9 6,4v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4C22,2.9 21.1,2 20,2zM19,11H9V9h10V11zM15,15H9v-2h6V15zM19,7H9V5h10V7z") }
    val Book: ImageVector by lazy { vec("book", "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5v11.5z") }
    val DoneAll: ImageVector by lazy { vec("done_all", "M18,7l-1.41,-1.41l-6.34,6.34l1.41,1.41L18,7zM22.24,5.59L11.66,16.17L7.48,12l-1.41,1.41L11.66,19l12,-12l-1.42,-1.41zM0.41,13.41L6,19l1.41,-1.41L1.83,12L0.41,13.41z") }
    val Translate: ImageVector by lazy { vec("translate", "M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z") }
    val Tune: ImageVector by lazy { vec("tune", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z") }
    val Undo: ImageVector by lazy { vec("undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z") }
    val Block: ImageVector by lazy { vec("block", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z") }
    val Bookmark: ImageVector by lazy { vec("bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z") }
    val CloudDownload: ImageVector by lazy { vec("cloud_download", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM17,13l-5,5 -5,-5h3V9h4v4h3z") }
    val Storage: ImageVector by lazy { vec("storage", "M2,20h20v-4H2v4zM4,17h2v2H4v-2zM2,4v4h20V4H2zM6,7H4V5h2v2zM2,14h20v-4H2v4zM4,11h2v2H4v-2z") }
    val Palette: ImageVector by lazy { vec("palette", "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5H16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5S18.33,12 17.5,12z") }
    val Eye: ImageVector by lazy { vec("eye", "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z") }
    val Globe: ImageVector by lazy { vec("globe", "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 1.84,0.63 3.37,1.91 4.33,3.56zM12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96zM4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2 0,0.68 0.06,1.34 0.14,2H4.26zM5.08,16h2.95c0.32,1.25 0.78,2.45 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56zM8.03,8H5.08c0.96,-1.66 2.49,-2.93 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43,1.43 -1.08,2.76 -1.91,3.96zM14.34,14H9.66c-0.09,-0.66 -0.16,-1.32 -0.16,-2 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32 0.16,2 0,0.68 -0.07,1.34 -0.16,2zM14.59,19.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56zM16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34 -0.14,-2h3.38c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z") }
    val Clock: ImageVector by lazy { vec("clock", "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z") }
}
