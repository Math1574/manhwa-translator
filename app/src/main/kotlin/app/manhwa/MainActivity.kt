package app.manhwa

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        TestHooks.maybeRun(applicationContext, (application as App).graph, intent)
    }

    /** L'avancement de lecture n'est écrit qu'à intervalles espacés : on le range quand l'application quitte l'écran. */
    override fun onStop() {
        super.onStop()
        (application as App).graph.library.flush()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as App).graph
        TestHooks.maybeRun(applicationContext, graph, intent)
        setContent {
            ManhwaTheme { Root(graph) }
        }
    }
}

@Composable
private fun Root(graph: AppGraph) {
    var tab by remember { mutableIntStateOf(0) }
    val c = graph.controller
    val tabs = listOf("Lire" to AppIcons.Book, "Bibliothèque" to AppIcons.Library, "Réglages" to Icons.Filled.Settings)

    // Retour : plein écran, puis onglet courant, puis historique du navigateur, sinon sortie.
    BackHandler(enabled = c.fullscreen || tab != 0 || c.canGoBack) {
        if (c.fullscreen) c.exitFullscreen() else if (tab != 0) tab = 0 else c.back()
    }

    // Plein écran : barres système masquées (réapparition par balayage depuis le bord), contenu jusque dans l'encoche.
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    LaunchedEffect(c.fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val ctrl = WindowCompat.getInsetsController(window, view)
        if (c.fullscreen) {
            tab = 0
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        } else {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!c.fullscreen) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) }, alwaysShowLabel = true)
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
                        1 -> LibraryScreen(graph) { url, resumeAt -> c.load(url, resumeAt); tab = 0 }
                        else -> SettingsRoot(graph)
                    }
                }
            }
        }
    }
}
