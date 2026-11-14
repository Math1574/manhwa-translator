package app.manhwa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import java.util.Locale

/**
 * Réglages rapides de la lecture, ouverts par un appui long sur le bouton Original | Traduit : langue, taille du texte traduit, onomatopées et « Retraduire la page ».
 * Rien ne se lance tout seul : changer un réglage ne traduit rien ; seul « Retraduire la page » relance le travail sur la page ouverte.
 * Un appui à côté du panneau, la croix ou le retour le referment.
 */
@Composable
fun BoxScope.QuickSettings(graph: AppGraph, onClose: () -> Unit) {
    val c = graph.controller
    val store = graph.settingsStore
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    val scrimSource = remember { MutableInteractionSource() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.74f).dp
    val shape = RoundedCornerShape(24.dp)
    BackHandler(onBack = onClose)
    // voile sur la page : un appui à côté du panneau le referme
    Box(Modifier.fillMaxSize().background(Color(0x80000000)).clickable(interactionSource = scrimSource, indication = null) { onClose() })
    // fond opaque sous la carte de verre (la page de lecture passerait au travers) ; le panneau garde ses appuis : ils n'atteignent pas le voile
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)
            .clip(shape).background(cs.background)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
            Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Réglages rapides", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fermer les réglages rapides", tint = cs.onSurfaceVariant) }
                }
                SubTitle("Langue du texte à traduire")
                ChipGrid(SourceLang.values().map { it to sourceNames.getValue(it) }, s.source) { l -> store.update { it.copy(source = l) } }
                SubTitle("Traduire vers")
                ChipGrid(TargetLang.values().map { it to targetNames.getValue(it) }, s.target, columns = TargetLang.values().size, compact = true) { l -> store.update { it.copy(target = l) } }
                Text("Taille du texte traduit : ${String.format(Locale.FRANCE, "%.1f", s.textScale)}×", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                Slider(value = s.textScale, onValueChange = { v -> store.update { it.copy(textScale = v) } }, valueRange = 0.5f..2f)
                SwitchRow("Traduire les onomatopées", "Désactivé : CRACK, BOOM… restent tels quels.", s.translateSfx) { v -> store.update { it.copy(translateSfx = v) } }
                val canRetranslate = c.translateOn && c.active
                SwitchRow("Gomme à traductions", "Un appui sur une traduction la supprime (« Annuler » reste proposé 3 secondes). Se coupe en changeant de page.", c.eraser, enabled = canRetranslate || c.eraser) { v -> c.switchEraser(v); if (v) onClose() }
                GradientButton("Retraduire la page", { c.start(force = true); onClose() }, Modifier.fillMaxWidth(), enabled = canRetranslate)
                Hint(if (canRetranslate) "« Retraduire la page » applique ces choix à la page ouverte." else "Touchez d'abord le bouton rond pour traduire la page.")
            }
        }
    }
}
