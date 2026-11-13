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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleResume(intent)
        TestHooks.maybeRun(applicationContext, (application as App).graph, intent)
    }

    /** Ouverture par le raccourci « Reprendre la lecture » de l'icône. */
    private fun handleResume(intent: android.content.Intent?) {
        val url = intent?.getStringExtra(ResumeShortcut.EXTRA_URL) ?: return
        val pct = intent.getIntExtra(ResumeShortcut.EXTRA_PCT, 0)
        intent.removeExtra(ResumeShortcut.EXTRA_URL)
        (application as App).graph.controller.openFromShortcut(url, pct)
    }

    /** L'avancement de lecture n'est écrit qu'à intervalles espacés : on le range quand l'application quitte l'écran. */
    override fun onStop() {
        super.onStop()
        (application as App).graph.library.flush()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as App).graph
        handleResume(intent)
        TestHooks.maybeRun(applicationContext, graph, intent)
        setContent {
            ManhwaTheme(graph.settingsStore.settings) { Root(graph) }
        }
    }
}

@Composable
private fun Root(graph: AppGraph) {
    var tab by remember { mutableIntStateOf(0) }
    val c = graph.controller
    val tabs = listOf(NavTab("Lecture", AppIcons.Book), NavTab("Bibliothèque", AppIcons.Library), NavTab("Réglages", Icons.Filled.Settings, badge = graph.updater.hasNews()))

    // Retour : plein écran, puis onglet courant, puis historique du navigateur, sinon sortie.
    BackHandler(enabled = c.fullscreen || tab != 0 || c.canGoBack) {
        if (c.fullscreen) c.exitFullscreen() else if (tab != 0) tab = 0 else c.back()
    }

    // ouverture par le raccourci de l'icône : retour sur l'onglet Lecture
    LaunchedEffect(c.readingTick) { if (c.readingTick > 0) tab = 0 }

    // couleur des barres système : celle du fond choisi dans les réglages d'apparence
    val look = LocalLook.current
    val bgColor = if (look.background == "noir") Color.Black else androidx.compose.ui.graphics.Color.hsv(look.hue, 0.78f, 0.075f)
    val barActivity = LocalContext.current as? Activity
    SideEffect { barActivity?.window?.let { it.statusBarColor = bgColor.toArgb(); it.navigationBarColor = bgColor.toArgb() } }

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

    // Quand on lit en descendant, la barre d'onglets se range avec la barre d'adresse (elles reviennent en remontant ou d'un appui sur la page).
    val readingFree = tab == 0 && c.barsHidden && graph.settingsStore.settings.hideBarsWhileReading
    // onglet affiché par-dessus la lecture : on garde le dernier pour que son contenu ne change pas pendant l'animation de sortie
    var lastOther by remember { mutableIntStateOf(1) }
    val shownTab = if (tab != 0) tab else lastOther
    SideEffect { if (tab != 0) lastOther = tab }

    AppBackground(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            AnimatedVisibility(visible = !c.fullscreen && !readingFree, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                GlowNavBar(tabs, tab) { tab = it }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            // Le navigateur reste toujours composé pour conserver la page ouverte quand on change d'onglet.
            BrowserScreen(graph)
            AnimatedVisibility(
                visible = tab != 0,
                enter = fadeIn(tween(190)) + slideInVertically(tween(260)) { it / 28 },
                exit = fadeOut(tween(130)),
            ) {
                AppBackground(Modifier.fillMaxSize()) {
                    when (shownTab) {
                        1 -> LibraryScreen(graph) { url, resumeAt -> c.load(url, resumeAt); tab = 0 }
                        else -> SettingsRoot(graph)
                    }
                }
            }
        }
    }
    // correction d'un texte traduit : éditeur plein écran posé sur toute l'application (zone, texte, position, police, glossaire)
    c.editTarget?.let { et -> BlockEditor(graph, et) { c.dismissEdit() } }
    // nouveautés de la version qui vient d'être installée (une seule fois)
    UpdateHost(graph)
    }
    }
}
