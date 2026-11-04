package app.manhwa

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private val ManhwaColors = darkColorScheme(
    primary = Color(0xFF9DB1FF),
    onPrimary = Color(0xFF0A1040),
    primaryContainer = Color(0xFF2B3470),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFC3A6FF),
    onSecondary = Color(0xFF200A40),
    secondaryContainer = Color(0xFF3A2D66),
    onSecondaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF0E0F1A),
    onBackground = Color(0xFFE3E4F4),
    surface = Color(0xFF161828),
    onSurface = Color(0xFFE3E4F4),
    surfaceVariant = Color(0xFF22253A),
    onSurfaceVariant = Color(0xFFB9BCD8),
    outline = Color(0xFF4A4E6B),
    error = Color(0xFFFF8A80),
)

private val ManhwaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ManhwaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ManhwaColors, shapes = ManhwaShapes, content = content)
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
    val Clock: ImageVector by lazy { vec("clock", "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z") }
}
