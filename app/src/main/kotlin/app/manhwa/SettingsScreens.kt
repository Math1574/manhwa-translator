package app.manhwa

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.manhwa.core.LlmModels
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import app.manhwa.core.TranslationEngineChoice
import java.util.Locale

private enum class SettingsPage { HOME, MODELS, GLOSSARY }

/** Onglet Réglages : l'accueil regroupe les options par thème ; les modèles de traduction et le glossaire sont des sous-pages. */
@Composable
fun SettingsRoot(graph: AppGraph) {
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    BackHandler(enabled = page != SettingsPage.HOME) { page = SettingsPage.HOME }
    when (page) {
        SettingsPage.HOME -> SettingsHome(graph) { page = it }
        SettingsPage.MODELS -> SubPageBody("Modèles de traduction", { page = SettingsPage.HOME }) { ModelsScreen(graph) }
        SettingsPage.GLOSSARY -> SubPageBody("Glossaire", { page = SettingsPage.HOME }) { GlossaryScreen(graph) }
    }
}

@Composable
private fun SubPageBody(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LinkRow(title: String, desc: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean = true, hint: String? = null, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelect() }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Column {
            Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsHome(graph: AppGraph, open: (SettingsPage) -> Unit) {
    val store = graph.settingsStore
    val s = store.settings
    var cacheBytes by remember { mutableStateOf(graph.cache.diskBytes()) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp))

        Section("Traduction") {
            Text("Langue du texte à traduire", style = MaterialTheme.typography.titleMedium)
            val sourceNames = mapOf(
                SourceLang.AUTO to "Automatique", SourceLang.JA to "Japonais", SourceLang.KO to "Coréen",
                SourceLang.ZH_HANS to "Chinois simplifié", SourceLang.ZH_HANT to "Chinois traditionnel", SourceLang.EN to "Anglais",
            )
            SourceLang.values().forEach { l -> ChoiceRow(sourceNames.getValue(l), s.source == l) { store.update { it.copy(source = l) } } }
            HorizontalDivider()
            Text("Traduire vers", style = MaterialTheme.typography.titleMedium)
            val targetNames = mapOf(TargetLang.FR to "Français", TargetLang.EN to "Anglais", TargetLang.ES to "Espagnol", TargetLang.DE to "Allemand")
            TargetLang.values().forEach { l -> ChoiceRow(targetNames.getValue(l), s.target == l) { store.update { it.copy(target = l) } } }
            HorizontalDivider()
            Text("Moteur de traduction", style = MaterialTheme.typography.titleMedium)
            val engineNames = mapOf(
                TranslationEngineChoice.MLKIT to "ML Kit : léger, bulle par bulle, sans contexte",
                TranslationEngineChoice.GEMMA_E2B to "Gemma 4 E2B : avec contexte (${gb(LlmModels.E2B.sizeBytes)} Go)",
                TranslationEngineChoice.GEMMA_E4B to "Gemma 4 E4B : meilleure qualité, plus lent (${gb(LlmModels.E4B.sizeBytes)} Go)",
            )
            TranslationEngineChoice.values().forEach { c ->
                val spec = LlmModels.specFor(c)
                val available = spec == null || graph.llm.ready[spec.id] == true
                ChoiceRow(engineNames.getValue(c), s.engine == c, available, if (!available) "À télécharger dans Modèles de traduction" else null) { store.update { it.copy(engine = c) } }
            }
            LinkRow("Modèles de traduction", "Télécharger ou supprimer Gemma 4 et les langues ML Kit") { open(SettingsPage.MODELS) }
            HorizontalDivider()
            SwitchRow("Traduire les onomatopées", "Désactivé (recommandé) : les bruitages (CRACK, BOOM, THUD…) restent tels quels dans l'image.", s.translateSfx) { v -> store.update { it.copy(translateSfx = v) } }
            LinkRow("Glossaire", "Noms de personnages et termes à traduire toujours de la même façon") { open(SettingsPage.GLOSSARY) }
        }

        Section("Lecture") {
            SwitchRow("Lecture automatique", "Traduit chaque page dès son ouverture et pendant le défilement, sans rien toucher (recommandé).", s.autoTranslate) { v -> store.update { it.copy(autoTranslate = v) } }
            HorizontalDivider()
            Text("Taille du texte traduit : ${String.format(Locale.FRANCE, "%.1f", s.textScale)}×", style = MaterialTheme.typography.titleMedium)
            Slider(value = s.textScale, onValueChange = { v -> store.update { it.copy(textScale = v) } }, valueRange = 0.5f..2f)
            Text("Pour l'appliquer à la page ouverte, touchez « Retraduire » (résultats repris du cache, sans nouveau calcul).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Pages préparées en avance : ${s.maxPrefetchPages}", style = MaterialTheme.typography.titleMedium)
            Slider(value = s.maxPrefetchPages.toFloat(), onValueChange = { v -> store.update { it.copy(maxPrefetchPages = v.toInt()) } }, valueRange = 0f..10f, steps = 9)
            Text("Plus la valeur est basse, moins le téléphone est sollicité (et moins il chauffe).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            SwitchRow("Bloquer les publicités", "Filtre les régies publicitaires connues, les fenêtres intempestives et les redirections. Désactivez-le si un site de lecture ne s'affiche plus correctement.", s.blockAds) { v -> store.update { it.copy(blockAds = v) } }
        }

        Section("Qualité de lecture du texte") {
            SwitchRow(
                "Lecture renforcée du texte",
                if (graph.paddleModels.available) "Second moteur (PaddleOCR) : repère les lettrages stylisés et le texte hors des bulles. Un peu plus lent."
                else "Indisponible : les modèles ne sont pas inclus dans cette version de l'application.",
                s.enhancedOcr && graph.paddleModels.available, graph.paddleModels.available,
            ) { v -> store.update { it.copy(enhancedOcr = v) } }
            HorizontalDivider()
            SwitchRow("Relecture des textes difficiles (Gemma)", "Quand une lecture paraît incomplète ou absurde (lettrage manuscrit, incliné, traversé par un contour), Gemma relit la zone sur l'image avant la traduction. Appuyez une fois sur « Tester » ; ensuite c'est automatique.", s.autoReread) { v -> store.update { it.copy(autoReread = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { graph.visionTest.run() }, enabled = !graph.visionTest.running) { Text("Tester la relecture") }
                if (graph.visionTest.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (graph.visionTest.summary.isNotEmpty()) Text(graph.visionTest.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            SwitchRow("Accélération GPU (expérimental)", "Plus rapide pour Gemma, mais peut échouer selon le téléphone (repli automatique sur le processeur).", s.useGpu) { v -> store.update { it.copy(useGpu = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { graph.gpuTest.run() }, enabled = !graph.gpuTest.running) { Text("Tester CPU / GPU") }
                if (graph.gpuTest.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (graph.gpuTest.summary.isNotEmpty()) Text(graph.gpuTest.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("Données et diagnostic") {
            Text("Cache de traductions : ${String.format(Locale.FRANCE, "%.1f", cacheBytes / 1_048_576.0)} Mo", style = MaterialTheme.typography.bodyMedium)
            Text("Stockage de l'application : ${String.format(Locale.FRANCE, "%.1f", ctx.filesDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L } / 1_048_576.0)} Mo", style = MaterialTheme.typography.bodyMedium)
            Text("Modèles Gemma stockés : ${String.format(Locale.FRANCE, "%.2f", graph.llm.usedBytes() / 1_000_000_000.0)} Go", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                graph.cache.clear(); graph.controller.clearOverlays(); cacheBytes = graph.cache.diskBytes()
                Toast.makeText(ctx, "Cache vidé", Toast.LENGTH_SHORT).show()
            }) { Text("Vider le cache") }
            HorizontalDivider()
            SwitchRow("Diagnostic de la détection", "Encadre le texte repéré par l'OCR (vert : utilisé, rouge : rejeté, avec la confiance). Désactive le cache. Utile pour signaler un problème.", s.diagnostic) { v -> store.update { it.copy(diagnostic = v) } }
            HorizontalDivider()
            var journalTick by remember { mutableStateOf(0) }
            val entries = remember(journalTick) { graph.journal.entries() }; val kb = remember(journalTick) { graph.journal.sizeBytes() / 1024 }
            Text("Journal de traduction", style = MaterialTheme.typography.titleMedium)
            Text(
                "$entries entrée(s), $kb Ko. Pour chaque page : texte lu, traduction et problèmes détectés automatiquement. Il reste sur le téléphone. " +
                    "Exportez-le pour signaler des erreurs. Astuce : un appui long sur une traduction montre l'original et permet de la corriger.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { graph.journal.share(ctx) }, enabled = entries > 0) { Text("Exporter") }
                OutlinedButton(onClick = { graph.journal.clear(); journalTick++ }, enabled = entries > 0) { Text("Effacer") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
