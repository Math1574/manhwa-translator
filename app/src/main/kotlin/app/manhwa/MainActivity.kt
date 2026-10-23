package app.manhwa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val ManhwaColors = darkColorScheme(
    primary = Color(0xFF8FA8FF),
    onPrimary = Color(0xFF0A1040),
    secondary = Color(0xFFB388FF),
    onSecondary = Color(0xFF200A40),
    background = Color(0xFF0E0F1A),
    onBackground = Color(0xFFE3E4F4),
    surface = Color(0xFF161828),
    onSurface = Color(0xFFE3E4F4),
    surfaceVariant = Color(0xFF22253A),
    onSurfaceVariant = Color(0xFFB9BCD8),
    error = Color(0xFFFF8A80),
)

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        TestHooks.maybeRun(applicationContext, (application as App).graph, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as App).graph
        TestHooks.maybeRun(applicationContext, graph, intent)
        setContent {
            MaterialTheme(colorScheme = ManhwaColors) { Root(graph) }
        }
    }
}

@Composable
private fun Root(graph: AppGraph) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Navigateur", "Modèles", "Glossaire", "Réglages")
    val c = graph.controller

    // Retour : historique du navigateur, sinon retour à l'onglet navigateur, sinon sortie.
    BackHandler(enabled = tab != 0 || c.canGoBack) {
        if (tab != 0) tab = 0 else c.back()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                titles.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) }, alwaysShowLabel = true)
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            // Le navigateur reste toujours composé pour conserver la page ouverte quand on change d'onglet.
            BrowserScreen(graph)
            if (tab != 0) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (tab) {
                        1 -> ModelsScreen(graph)
                        2 -> GlossaryScreen(graph)
                        else -> SettingsScreen(graph)
                    }
                }
            }
        }
    }
}
