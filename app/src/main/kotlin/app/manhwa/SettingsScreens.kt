package app.manhwa

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.manhwa.core.LlmModels
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import app.manhwa.core.ThemeChoices
import app.manhwa.core.TranslationEngineChoice
import java.util.Locale

private enum class SettingsPage { HOME, TRANSLATION, READING, APPEARANCE, OCR, BROWSING, DATA, MODELS, GLOSSARY }

/** Onglet Réglages : une carte par thème ; chacune ouvre la page de ses options. */
@Composable
fun SettingsRoot(graph: AppGraph) {
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    BackHandler(enabled = page != SettingsPage.HOME) { page = SettingsPage.HOME }
    val back = { page = SettingsPage.HOME }
    Crossfade(targetState = page, animationSpec = tween(180), label = "settings") { p ->
        when (p) {
            SettingsPage.HOME -> SettingsHome(graph) { page = it }
            SettingsPage.TRANSLATION -> SubPageBody("Traduction", back) { TranslationSettings(graph) { page = it } }
            SettingsPage.READING -> SubPageBody("Lecture", back) { ReadingSettings(graph) }
            SettingsPage.APPEARANCE -> SubPageBody("Apparence", back) { AppearanceSettings(graph) }
            SettingsPage.OCR -> SubPageBody("Reconnaissance du texte", back) { OcrSettings(graph) }
            SettingsPage.BROWSING -> SubPageBody("Navigation", back) { BrowsingSettings(graph) }
            SettingsPage.DATA -> SubPageBody("Données et diagnostic", back) { DataSettings(graph) }
            SettingsPage.MODELS -> SubPageBody("Modèles de traduction", back) { ModelsScreen(graph) }
            SettingsPage.GLOSSARY -> SubPageBody("Glossaire", back) { GlossaryScreen(graph) }
        }
    }
}

@Composable
private fun SubPageBody(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

// ---------- éléments communs ----------

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
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
private fun PageScroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

private val sourceNames = mapOf(
    SourceLang.AUTO to "Automatique", SourceLang.JA to "Japonais", SourceLang.KO to "Coréen",
    SourceLang.ZH_HANS to "Chinois simplifié", SourceLang.ZH_HANT to "Chinois traditionnel", SourceLang.EN to "Anglais",
)
private val targetNames = mapOf(TargetLang.FR to "Français", TargetLang.EN to "Anglais", TargetLang.ES to "Espagnol", TargetLang.DE to "Allemand")

// ---------- accueil ----------

@Composable
private fun SettingsHome(graph: AppGraph, open: (SettingsPage) -> Unit) {
    val s = graph.settingsStore.settings
    val engine = when (s.engine) { TranslationEngineChoice.MLKIT -> "ML Kit"; TranslationEngineChoice.GEMMA_E2B -> "Gemma 4 E2B"; TranslationEngineChoice.GEMMA_E4B -> "Gemma 4 E4B" }
    val readyModels = listOf(LlmModels.E2B, LlmModels.E4B).count { graph.llm.ready[it.id] == true }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Réglages", "Lecture, traduction et apparence")
        SectionRow(AppIcons.Translate, "Traduction", "${sourceNames.getValue(s.source)} → ${targetNames.getValue(s.target)} · $engine") { open(SettingsPage.TRANSLATION) }
        SectionRow(AppIcons.Eye, "Lecture", "Texte ×${String.format(Locale.FRANCE, "%.1f", s.textScale)} · ${s.maxPrefetchPages} page(s) en avance" + if (s.hideBarsWhileReading) " · barres masquées" else "") { open(SettingsPage.READING) }
        SectionRow(AppIcons.Palette, "Apparence", "Accent ${accentName(s.themeAccent)} · fond ${backgroundName(s.themeBackground)}" + if (s.reducedMotion) " · animations réduites" else "") { open(SettingsPage.APPEARANCE) }
        SectionRow(Icons.Filled.Search, "Reconnaissance du texte", (if (s.enhancedOcr && graph.paddleModels.available) "Lecture renforcée" else "Lecture standard") + (if (s.autoReread) " · relecture Gemma" else "")) { open(SettingsPage.OCR) }
        SectionRow(AppIcons.Globe, "Navigation", if (s.blockAds) "Publicités bloquées" else "Publicités autorisées") { open(SettingsPage.BROWSING) }
        SectionRow(AppIcons.Bookmark, "Glossaire", "${graph.glossary.terms.size + graph.glossary.seriesTerms.size} terme(s)") { open(SettingsPage.GLOSSARY) }
        SectionRow(AppIcons.CloudDownload, "Modèles de traduction", if (readyModels > 0) "$readyModels modèle(s) Gemma prêt(s)" else "Aucun modèle Gemma téléchargé") { open(SettingsPage.MODELS) }
        SectionRow(AppIcons.Storage, "Données et diagnostic", "Cache ${String.format(Locale.FRANCE, "%.0f", graph.cache.diskBytes() / 1_048_576.0)} Mo · journal") { open(SettingsPage.DATA) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(cs.primaryContainer, cs.secondaryContainer))),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(23.dp), tint = cs.onPrimaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

private fun accentName(a: String) = when (a) { "indigo" -> "indigo"; "rose" -> "rose"; "emeraude" -> "émeraude"; "ambre" -> "ambre"; else -> "violet" }
private fun backgroundName(b: String) = when (b) { "nuit" -> "nuit"; "noir" -> "noir pur"; else -> "crépuscule" }

// ---------- pages ----------

@Composable
private fun TranslationSettings(graph: AppGraph, open: (SettingsPage) -> Unit) {
    val store = graph.settingsStore
    val s = store.settings
    PageScroll {
        SettingsCard {
            Text("Langue du texte à traduire", style = MaterialTheme.typography.titleMedium)
            SourceLang.values().forEach { l -> ChoiceRow(sourceNames.getValue(l), s.source == l) { store.update { it.copy(source = l) } } }
        }
        SettingsCard {
            Text("Traduire vers", style = MaterialTheme.typography.titleMedium)
            TargetLang.values().forEach { l -> ChoiceRow(targetNames.getValue(l), s.target == l) { store.update { it.copy(target = l) } } }
        }
        SettingsCard {
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
        }
        SettingsCard {
            SwitchRow("Traduire les onomatopées", "Désactivé (recommandé) : les bruitages (CRACK, BOOM, THUD…) restent tels quels dans l'image.", s.translateSfx) { v -> store.update { it.copy(translateSfx = v) } }
            HorizontalDivider()
            LinkRow("Glossaire", "Noms de personnages et termes à traduire toujours de la même façon") { open(SettingsPage.GLOSSARY) }
        }
    }
}

@Composable
private fun AppearanceSettings(graph: AppGraph) {
    val store = graph.settingsStore
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    PageScroll {
        SettingsCard {
            Text("Couleur d'accent", style = MaterialTheme.typography.titleMedium)
            Text("Elle teinte les boutons, les barres de progression et tout le fond de l'application.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ThemeChoices.accents.forEach { name ->
                    val (c1, c2) = accentSwatch(name)
                    val sel = s.themeAccent == name
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { store.update { it.copy(themeAccent = name) } }) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2)))
                                .then(if (sel) Modifier.border(3.dp, Color.White, CircleShape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                        Text(accentName(name).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = if (sel) cs.primary else cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        SettingsCard {
            Text("Fond", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("crepuscule" to "Crépuscule", "nuit" to "Nuit", "noir" to "Noir pur").forEach { (id, label) ->
                    val sel = s.themeBackground == id
                    val h = accentHueOf(s.themeAccent)
                    val preview = when (id) {
                        "noir" -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
                        "nuit" -> Brush.verticalGradient(listOf(Color.hsv(h, 0.62f, 0.095f), Color.hsv(h, 0.62f, 0.095f)))
                        else -> Brush.verticalGradient(listOf(Color.hsv(h, 0.78f, 0.075f), Color.hsv((h + 8f) % 360f, 0.66f, 0.22f)))
                    }
                    Column(Modifier.weight(1f).clickable { store.update { it.copy(themeBackground = id) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(preview)
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) cs.primary else Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Filled.Check, null, tint = cs.primary) }
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) cs.primary else cs.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            Text("Le noir pur éteint complètement les pixels sur un écran OLED : sobre et économe en batterie.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        SettingsCard {
            SwitchRow("Animations réduites", "Supprime les rebonds, les arrivées en glissant et le flottement de la mascotte. Plus sobre, un peu plus économe.", s.reducedMotion) { v -> store.update { it.copy(reducedMotion = v) } }
            HorizontalDivider()
            SwitchRow("Afficher la mascotte", "Les illustrations de la petite créature en haut des écrans.", s.showMascot) { v -> store.update { it.copy(showMascot = v) } }
        }
    }
}

@Composable
private fun ReadingSettings(graph: AppGraph) {
    val store = graph.settingsStore
    val s = store.settings
    PageScroll {
        SettingsCard {
            Text("Bouton Original | Traduit", style = MaterialTheme.typography.titleMedium)
            Text(
                "Le bouton rond posé au bord de l'écran lance la traduction quand vous lisez un chapitre, et revient à l'original d'un second appui. Les accueils et les listes d'œuvres d'un site ne sont jamais traduits tout seuls. " +
                    "Glissez le bouton pour l'écarter des boutons du site.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton("Replacer le bouton", { store.update { it.copy(orbRight = true, orbY = 0.42f) } })
        }
        SettingsCard {
            SwitchRow("Masquer les barres en lisant", "La barre d'adresse et la barre d'onglets se rangent quand vous descendez dans la page ; elles reviennent en remontant ou d'un appui sur la page.", s.hideBarsWhileReading) { v -> store.update { it.copy(hideBarsWhileReading = v) } }
        }
        SettingsCard {
            Text("Taille du texte traduit : ${String.format(Locale.FRANCE, "%.1f", s.textScale)}×", style = MaterialTheme.typography.titleMedium)
            Slider(value = s.textScale, onValueChange = { v -> store.update { it.copy(textScale = v) } }, valueRange = 0.5f..2f)
            Text("Pour l'appliquer à la page ouverte, utilisez « Retraduire la page » dans le menu ⋮ de la barre d'adresse (résultats repris du cache, sans nouveau calcul).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        SettingsCard {
            Text("Pages préparées en avance : ${s.maxPrefetchPages}", style = MaterialTheme.typography.titleMedium)
            Slider(value = s.maxPrefetchPages.toFloat(), onValueChange = { v -> store.update { it.copy(maxPrefetchPages = v.toInt()) } }, valueRange = 0f..10f, steps = 9)
            Text("Plus la valeur est basse, moins le téléphone est sollicité (et moins il chauffe). Si le téléphone chauffe, la préparation en avance se suspend toute seule.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (graph.controller.thermalHot) Text("Le téléphone est chaud : préparation en avance suspendue pour l'instant.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OcrSettings(graph: AppGraph) {
    val store = graph.settingsStore
    val s = store.settings
    PageScroll {
        SettingsCard {
            SwitchRow(
                "Lecture renforcée du texte",
                if (graph.paddleModels.available) "Second moteur (PaddleOCR) : repère les lettrages stylisés et le texte hors des bulles. Un peu plus lent."
                else "Indisponible : les modèles ne sont pas inclus dans cette version de l'application.",
                s.enhancedOcr && graph.paddleModels.available, graph.paddleModels.available,
            ) { v -> store.update { it.copy(enhancedOcr = v) } }
        }
        SettingsCard {
            SwitchRow("Relecture des textes difficiles (Gemma)", "Quand une lecture paraît incomplète ou absurde (lettrage manuscrit, incliné, traversé par un contour), Gemma relit la zone sur l'image avant la traduction. Appuyez une fois sur « Tester » ; ensuite c'est automatique.", s.autoReread) { v -> store.update { it.copy(autoReread = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Tester la relecture", { graph.visionTest.run() }, enabled = !graph.visionTest.running)
                if (graph.visionTest.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (graph.visionTest.summary.isNotEmpty()) Text(graph.visionTest.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsCard {
            SwitchRow("Accélération GPU (expérimental)", "Plus rapide pour Gemma, mais peut échouer selon le téléphone (repli automatique sur le processeur).", s.useGpu) { v -> store.update { it.copy(useGpu = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Tester CPU / GPU", { graph.gpuTest.run() }, enabled = !graph.gpuTest.running)
                if (graph.gpuTest.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (graph.gpuTest.summary.isNotEmpty()) Text(graph.gpuTest.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BrowsingSettings(graph: AppGraph) {
    val store = graph.settingsStore
    val s = store.settings
    PageScroll {
        SettingsCard {
            SwitchRow("Bloquer les publicités", "Filtre les régies publicitaires connues, les fenêtres intempestives et les redirections. Désactivez-le si un site de lecture ne s'affiche plus correctement.", s.blockAds) { v -> store.update { it.copy(blockAds = v) } }
        }
    }
}

@Composable
private fun DataSettings(graph: AppGraph) {
    val store = graph.settingsStore
    val s = store.settings
    val ctx = LocalContext.current
    var cacheBytes by remember { mutableStateOf(graph.cache.diskBytes()) }
    PageScroll {
        SettingsCard {
            Text("Cache de traductions : ${String.format(Locale.FRANCE, "%.1f", cacheBytes / 1_048_576.0)} Mo", style = MaterialTheme.typography.bodyMedium)
            Text("Stockage de l'application : ${String.format(Locale.FRANCE, "%.1f", ctx.filesDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L } / 1_048_576.0)} Mo", style = MaterialTheme.typography.bodyMedium)
            Text("Modèles Gemma stockés : ${String.format(Locale.FRANCE, "%.2f", graph.llm.usedBytes() / 1_000_000_000.0)} Go", style = MaterialTheme.typography.bodyMedium)
            GhostButton("Vider le cache", {
                graph.cache.clear(); graph.controller.clearOverlays(); cacheBytes = graph.cache.diskBytes()
                Toast.makeText(ctx, "Cache vidé", Toast.LENGTH_SHORT).show()
            })
        }
        SettingsCard {
            SwitchRow("Diagnostic de la détection", "Encadre le texte repéré par l'OCR (vert : utilisé, rouge : rejeté, avec la confiance). Désactive le cache. Utile pour signaler un problème.", s.diagnostic) { v -> store.update { it.copy(diagnostic = v) } }
        }
        SettingsCard {
            var journalTick by remember { mutableStateOf(0) }
            val entries = remember(journalTick) { graph.journal.entries() }; val kb = remember(journalTick) { graph.journal.sizeBytes() / 1024 }
            Text("Journal de traduction", style = MaterialTheme.typography.titleMedium)
            Text(
                "$entries entrée(s), $kb Ko. Pour chaque page : texte lu, traduction et problèmes détectés automatiquement. Il reste sur le téléphone. " +
                    "Exportez-le pour signaler des erreurs. Astuce : maintenez le doigt sur une traduction pour voir l'original et la corriger.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Exporter", { graph.journal.share(ctx) }, enabled = entries > 0)
                GhostButton("Effacer", { graph.journal.clear(); journalTick++ }, enabled = entries > 0)
            }
        }
    }
}
