package app.manhwa

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.manhwa.core.LlmModels
import app.manhwa.core.SearchEntry
import app.manhwa.core.SettingsSearch
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import app.manhwa.core.ThemeChoices
import app.manhwa.core.TranslationEngineChoice
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsPage { HOME, TRANSLATION, READING, APPEARANCE, OCR, BROWSING, DATA, ADVANCED, MODELS, GLOSSARY }

/** Où mène un résultat de recherche : une page et, dans cette page, le groupe à montrer (null : le haut de la page). */
private class Target(val page: SettingsPage, val anchor: String?)

/**
 * Tout ce que la recherche des réglages sait trouver. Les groupes (« anchor ») sont ceux des pages ci-dessous : la page s'ouvre déjà défilée sur le groupe, qui s'illumine un instant.
 * Les mots supplémentaires sont des synonymes ou des noms de valeurs, pour qu'on trouve un réglage sans connaître son titre exact.
 */
private val searchIndex: List<Pair<SearchEntry, Target>> = listOf(
    SearchEntry("src", "Langue du texte à traduire", "Traduction › Langues", "source japonais coréen chinois simplifié traditionnel anglais automatique") to Target(SettingsPage.TRANSLATION, "langues"),
    SearchEntry("dst", "Traduire vers", "Traduction › Langues", "langue cible français anglais espagnol allemand") to Target(SettingsPage.TRANSLATION, "langues"),
    SearchEntry("engine", "Moteur de traduction", "Traduction › Moteur", "ML Kit Gemma E2B E4B qualité contexte rapide") to Target(SettingsPage.TRANSLATION, "moteur"),
    SearchEntry("sfx", "Traduire les onomatopées", "Traduction › Options", "bruitages sfx crack boom effets sonores") to Target(SettingsPage.TRANSLATION, "options"),
    SearchEntry("orb", "Bouton Original | Traduit", "Lecture › Bouton rond", "replacer déplacer glisser position") to Target(SettingsPage.READING, "bouton"),
    SearchEntry("quick", "Réglages rapides de lecture", "Lecture › Bouton rond", "appui long panneau langue taille onomatopées retraduire page") to Target(SettingsPage.READING, "bouton"),
    SearchEntry("bars", "Masquer les barres en lisant", "Lecture › Affichage", "barre adresse onglets plein écran défilement") to Target(SettingsPage.READING, "affichage"),
    SearchEntry("size", "Taille du texte traduit", "Lecture › Affichage", "police agrandir réduire échelle") to Target(SettingsPage.READING, "affichage"),
    SearchEntry("prefetch", "Pages préparées en avance", "Lecture › Préparation en avance", "préchargement avance chauffe batterie anticiper") to Target(SettingsPage.READING, "avance"),
    SearchEntry("mascot", "Mascotte", "Apparence › Mascotte", "chat lofi hibou savant dragon nuage esprit-feuille personnage") to Target(SettingsPage.APPEARANCE, "mascotte"),
    SearchEntry("showmascot", "Afficher la mascotte", "Apparence › Mascotte", "masquer cacher illustration") to Target(SettingsPage.APPEARANCE, "mascotte"),
    SearchEntry("accent", "Couleur d'accent", "Apparence › Couleurs", "violet indigo rose émeraude ambre thème teinte") to Target(SettingsPage.APPEARANCE, "couleurs"),
    SearchEntry("bg", "Fond", "Apparence › Couleurs", "crépuscule nuit noir pur oled") to Target(SettingsPage.APPEARANCE, "couleurs"),
    SearchEntry("motion", "Animations réduites", "Apparence › Mouvement", "mouvement rebond flottement sobre économie") to Target(SettingsPage.APPEARANCE, "mouvement"),
    SearchEntry("enhanced", "Lecture renforcée du texte", "Reconnaissance du texte › Lecture", "paddleocr ocr second moteur lettrage stylisé hors bulle") to Target(SettingsPage.OCR, "lecture"),
    SearchEntry("reread", "Relecture des textes difficiles (Gemma)", "Reconnaissance du texte › Relecture", "gemma vision relire tester manuscrit incliné") to Target(SettingsPage.OCR, "relecture"),
    SearchEntry("ads", "Bloquer les publicités", "Navigation › Publicités", "pub adblock pop-up redirections liens invisibles") to Target(SettingsPage.BROWSING, "pubs"),
    SearchEntry("sites", "Sites sans bloqueur", "Navigation › Sites sans bloqueur", "exceptions rétablir autorisés destination") to Target(SettingsPage.BROWSING, "sites"),
    SearchEntry("cache", "Cache de traductions", "Données › Stockage", "vider effacer espace disque taille modèles gemma stockés") to Target(SettingsPage.DATA, "stockage"),
    SearchEntry("gpu", "Accélération GPU (expérimental)", "Avancé › Performance", "processeur cpu tester gemma vitesse") to Target(SettingsPage.ADVANCED, "performance"),
    SearchEntry("diag", "Diagnostic de la détection", "Avancé › Diagnostic", "cadres vert rouge ocr rejeté signaler problème") to Target(SettingsPage.ADVANCED, "diagnostic"),
    SearchEntry("journal", "Journal de traduction", "Avancé › Diagnostic", "exporter effacer erreurs signaler partager") to Target(SettingsPage.ADVANCED, "diagnostic"),
    SearchEntry("filters", "Listes de filtres", "Avancé › Bloqueur de publicités", "ublock easylist hagezi mise à jour règles") to Target(SettingsPage.ADVANCED, "bloqueur"),
    SearchEntry("blocklog", "Journal du bloqueur", "Avancé › Bloqueur de publicités", "règles bloquées image n'apparaît pas pop-up") to Target(SettingsPage.ADVANCED, "bloqueur"),
    SearchEntry("models", "Modèles de traduction", "Traduction › Modèles", "télécharger supprimer gemma ml kit langues japonais vertical manga-ocr module") to Target(SettingsPage.MODELS, null),
    SearchEntry("glossary", "Glossaire", "Traduction › Glossaire", "noms personnages termes œuvre remplacer") to Target(SettingsPage.GLOSSARY, null),
    SearchEntry("update", "Mise à jour de l'application", "Application", "version nouveautés télécharger installer build") to Target(SettingsPage.HOME, null),
)
private val searchEntries: List<SearchEntry> = searchIndex.map { it.first }
private val searchTargets: Map<String, Target> = searchIndex.associate { it.first.id to it.second }

/** Onglet Réglages : l'accueil range les pages en groupes et propose une recherche ; chaque page range ses options en groupes titrés. */
@Composable
fun SettingsRoot(graph: AppGraph) {
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    var focus by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val back = { page = SettingsPage.HOME; focus = null }
    val go = { p: SettingsPage, f: String? -> focus = f; page = p }
    BackHandler(enabled = page != SettingsPage.HOME) { back() }
    BackHandler(enabled = page == SettingsPage.HOME && query.isNotEmpty()) { query = "" }
    Crossfade(targetState = page, animationSpec = tween(180), label = "settings") { p ->
        when (p) {
            SettingsPage.HOME -> SettingsHome(graph, query, { query = it }, go)
            SettingsPage.TRANSLATION -> SubPageBody("Traduction", back) { TranslationSettings(graph, focus, go) }
            SettingsPage.READING -> SubPageBody("Lecture", back) { ReadingSettings(graph, focus) }
            SettingsPage.APPEARANCE -> SubPageBody("Apparence", back) { AppearanceSettings(graph, focus) }
            SettingsPage.OCR -> SubPageBody("Reconnaissance du texte", back) { OcrSettings(graph, focus) }
            SettingsPage.BROWSING -> SubPageBody("Navigation", back) { BrowsingSettings(graph, focus) }
            SettingsPage.DATA -> SubPageBody("Données", back) { DataSettings(graph, focus) }
            SettingsPage.ADVANCED -> SubPageBody("Avancé", back) { AdvancedSettings(graph, focus) }
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

/** Positions des groupes d'une page, relevées à la mise en page : c'est ce qui permet à la recherche d'ouvrir la page déjà défilée au bon endroit. */
private class PageCtx {
    var focus: String? = null
    val anchors = HashMap<String, Int>()
}

private val LocalPage = compositionLocalOf<PageCtx?> { null }

/** Page de réglages : défile, et si [focus] désigne un groupe (venant de la recherche), s'y rend une fois mise en page. */
@Composable
private fun PageScroll(focus: String?, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val ctx = remember { PageCtx() }
    ctx.focus = focus
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    LaunchedEffect(focus) {
        if (focus != null) {
            repeat(3) { withFrameNanos { } }
            ctx.anchors[focus]?.let { y -> scroll.animateScrollTo((y - gap).coerceAtLeast(0)) }
        }
    }
    CompositionLocalProvider(LocalPage provides ctx) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Groupe de réglages : un petit titre dans la couleur d'accent, puis une seule carte dont les lignes sont séparées par des filets. Il s'illumine quand la recherche y mène. */
@Composable
private fun SettingsGroup(title: String?, anchor: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalPage.current
    val cs = MaterialTheme.colorScheme
    val flash = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (anchor != null && ctx != null && ctx.focus == anchor) {
            flash.snapTo(1f); delay(800); flash.animateTo(0f, tween(1300))
        }
    }
    val place = if (anchor != null && ctx != null) Modifier.onGloballyPositioned { c -> ctx.anchors[anchor] = c.positionInParent().y.roundToInt() } else Modifier
    Column(Modifier.fillMaxWidth().then(place)) {
        if (title != null) SectionLabel(title)
        val glow = if (flash.value > 0.01f) Modifier.border(2.dp, cs.primary.copy(alpha = flash.value), RoundedCornerShape(22.dp)) else Modifier
        Box(Modifier.fillMaxWidth().then(glow)) {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

@Composable
internal fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
internal fun SwitchRow(title: String, desc: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Choix parmi quelques valeurs courtes (les langues) : des pastilles sur [columns] colonnes, au lieu d'une longue liste de boutons radio. [compact] resserre les pastilles (quatre mots courts sur une ligne). */
@Composable
internal fun <T> ChipGrid(options: List<Pair<T, String>>, selected: T, columns: Int = 2, compact: Boolean = false, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (value, label) ->
                    val sel = value == selected
                    Box(
                        Modifier.weight(1f).clip(CircleShape)
                            .background(if (sel) cs.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                            .border(if (sel) 1.5.dp else 1.dp, if (sel) cs.primary else Color.White.copy(alpha = 0.16f), CircleShape)
                            .clickable { onSelect(value) }.padding(horizontal = if (compact) 4.dp else 10.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) cs.primary else cs.onSurface, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

internal val sourceNames = mapOf(
    SourceLang.AUTO to "Automatique", SourceLang.JA to "Japonais", SourceLang.KO to "Coréen",
    SourceLang.ZH_HANS to "Chinois simplifié", SourceLang.ZH_HANT to "Chinois traditionnel", SourceLang.EN to "Anglais",
)
internal val targetNames = mapOf(TargetLang.FR to "Français", TargetLang.EN to "Anglais", TargetLang.ES to "Espagnol", TargetLang.DE to "Allemand")

// ---------- accueil ----------

@Composable
private fun SettingsHome(graph: AppGraph, query: String, onQuery: (String) -> Unit, open: (SettingsPage, String?) -> Unit) {
    val s = graph.settingsStore.settings
    val engine = when (s.engine) { TranslationEngineChoice.MLKIT -> "ML Kit"; TranslationEngineChoice.GEMMA_E2B -> "Gemma 4 E2B"; TranslationEngineChoice.GEMMA_E4B -> "Gemma 4 E4B" }
    val readyModels = listOf(LlmModels.E2B, LlmModels.E4B).count { graph.llm.ready[it.id] == true }
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Réglages", "Traduction, lecture et apparence")
        SearchField(query, onQuery)
        if (query.isNotBlank()) {
            SearchResults(query) { t ->
                focusManager.clearFocus()
                if (t.page == SettingsPage.HOME) onQuery("") else open(t.page, t.anchor)
            }
        } else {
            UpdateCard(graph)
            SectionRow(AppIcons.Translate, "Traduction", "${sourceNames.getValue(s.source)} → ${targetNames.getValue(s.target)} · $engine") { open(SettingsPage.TRANSLATION, null) }
            SectionRow(AppIcons.Eye, "Lecture", "Texte ×${String.format(Locale.FRANCE, "%.1f", s.textScale)} · ${s.maxPrefetchPages} page(s) en avance" + if (s.hideBarsWhileReading) " · barres masquées" else "") { open(SettingsPage.READING, null) }
            SectionRow(AppIcons.Palette, "Apparence", "${mascotName(s.themeMascot)} · accent ${accentName(s.themeAccent)} · fond ${backgroundName(s.themeBackground)}" + if (s.reducedMotion) " · animations réduites" else "") { open(SettingsPage.APPEARANCE, null) }
            SectionLabel("Texte et modèles")
            SectionRow(Icons.Filled.Search, "Reconnaissance du texte", (if (s.enhancedOcr && graph.paddleModels.available) "Lecture renforcée" else "Lecture standard") + (if (s.autoReread) " · relecture Gemma" else "")) { open(SettingsPage.OCR, null) }
            SectionRow(AppIcons.Bookmark, "Glossaire", "${graph.glossary.terms.size + graph.glossary.seriesTerms.size} terme(s)") { open(SettingsPage.GLOSSARY, null) }
            SectionRow(AppIcons.CloudDownload, "Modèles de traduction", if (readyModels > 0) "$readyModels modèle(s) Gemma prêt(s)" else "Aucun modèle Gemma téléchargé") { open(SettingsPage.MODELS, null) }
            SectionLabel("Application")
            SectionRow(AppIcons.Globe, "Navigation", if (s.blockAds) "Publicités bloquées" else "Publicités autorisées") { open(SettingsPage.BROWSING, null) }
            SectionRow(AppIcons.Storage, "Données", "Cache ${String.format(Locale.FRANCE, "%.0f", graph.cache.diskBytes() / 1_048_576.0)} Mo · modèles ${String.format(Locale.FRANCE, "%.1f", graph.llm.usedBytes() / 1_000_000_000.0)} Go") { open(SettingsPage.DATA, null) }
            SectionRow(AppIcons.Tune, "Avancé", "GPU, filtres, diagnostic et journaux") { open(SettingsPage.ADVANCED, null) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Champ de recherche en forme de pastille de verre, avec une croix pour l'effacer. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Rechercher un réglage", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Effacer la recherche", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
    }
}

/** Réglages qui correspondent à la recherche, sur une seule carte ; un appui ouvre la page au bon endroit. */
@Composable
private fun SearchResults(query: String, onPick: (Target) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hits = remember(query) { SettingsSearch.find(query, searchEntries) }
    if (hits.isEmpty()) {
        Text("Aucun réglage ne correspond à « ${query.trim()} ».", Modifier.padding(horizontal = 6.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        return
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 6.dp) {
        hits.forEachIndexed { i, h ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 10.dp))
            Row(
                Modifier.fillMaxWidth().clickable { searchTargets[h.id]?.let(onPick) }.padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(h.title, style = MaterialTheme.typography.titleMedium)
                    Text(h.path, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
            }
        }
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

private fun mascotName(m: String) = when (m) { "chat" -> "Chat lofi"; "hibou" -> "Hibou savant"; "dragon" -> "Bébé dragon"; "nuage" -> "Nuage rêveur"; else -> "Esprit-feuille" }
private fun mascotBlurb(m: String) = when (m) {
    "chat" -> "Chat lofi : il lit à la lueur de son livre, casque sur les oreilles, pendant que la nuit s'installe."
    "hibou" -> "Hibou savant : il relit chaque virgule, lunettes sur le bec et crayon à la patte."
    "dragon" -> "Bébé dragon : il fait des étincelles dès qu'un chapitre devient palpitant."
    "nuage" -> "Nuage rêveur : il flotte doucement d'un chapitre à l'autre, bonnet de nuit sur la tête."
    else -> "Esprit-feuille : un petit malin qui garde une pousse de lumière sur la tête."
}

private fun accentName(a: String) = when (a) { "indigo" -> "indigo"; "rose" -> "rose"; "emeraude" -> "émeraude"; "ambre" -> "ambre"; else -> "violet" }
private fun backgroundName(b: String) = when (b) { "nuit" -> "nuit"; "noir" -> "noir pur"; else -> "crépuscule" }

// ---------- pages ----------

@Composable
private fun TranslationSettings(graph: AppGraph, focus: String?, open: (SettingsPage, String?) -> Unit) {
    val store = graph.settingsStore
    val s = store.settings
    PageScroll(focus) {
        SettingsGroup("Langues", "langues") {
            SubTitle("Langue du texte à traduire")
            ChipGrid(SourceLang.values().map { it to sourceNames.getValue(it) }, s.source) { l -> store.update { it.copy(source = l) } }
            HorizontalDivider()
            SubTitle("Traduire vers")
            ChipGrid(TargetLang.values().map { it to targetNames.getValue(it) }, s.target) { l -> store.update { it.copy(target = l) } }
        }
        SettingsGroup("Moteur", "moteur") {
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
            HorizontalDivider()
            LinkRow("Modèles de traduction", "Gemma 4 et langues ML Kit") { open(SettingsPage.MODELS, null) }
        }
        SettingsGroup("Options", "options") {
            SwitchRow("Traduire les onomatopées", "Désactivé (recommandé) : les bruitages (CRACK, BOOM…) restent tels quels.", s.translateSfx) { v -> store.update { it.copy(translateSfx = v) } }
            HorizontalDivider()
            LinkRow("Glossaire", "Noms et termes à traduire toujours pareil") { open(SettingsPage.GLOSSARY, null) }
        }
    }
}

@Composable
private fun AppearanceSettings(graph: AppGraph, focus: String?) {
    val store = graph.settingsStore
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    PageScroll(focus) {
        SettingsGroup("Mascotte", "mascotte") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeChoices.mascots.forEach { id ->
                    val sel = s.themeMascot == id
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier.width(96.dp).clip(shape).background(Color.White.copy(alpha = if (sel) 0.13f else 0.05f))
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) cs.primary else Color.White.copy(alpha = 0.14f), shape)
                            .clickable { store.update { it.copy(themeMascot = id, showMascot = true) } }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MascotImage(68.dp, name = id, always = true, animated = false)
                        Text(mascotName(id), style = MaterialTheme.typography.labelMedium, color = if (sel) cs.primary else cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Hint(mascotBlurb(s.themeMascot))
            HorizontalDivider()
            SwitchRow("Afficher la mascotte", null, s.showMascot) { v -> store.update { it.copy(showMascot = v) } }
        }
        SettingsGroup("Couleurs", "couleurs") {
            SubTitle("Couleur d'accent")
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
            HorizontalDivider()
            SubTitle("Fond")
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
        }
        SettingsGroup("Mouvement", "mouvement") {
            SwitchRow("Animations réduites", "Supprime rebonds, glissements et flottement de la mascotte.", s.reducedMotion) { v -> store.update { it.copy(reducedMotion = v) } }
        }
    }
}

@Composable
private fun ReadingSettings(graph: AppGraph, focus: String?) {
    val store = graph.settingsStore
    val s = store.settings
    PageScroll(focus) {
        SettingsGroup("Bouton rond", "bouton") {
            Hint("Un appui lance la traduction, un second revient à l'original. Un appui long ouvre les réglages rapides (langue, taille du texte, onomatopées, « Retraduire la page »). Pour écarter le bouton des boutons du site, glissez-le sans attendre.")
            GhostButton("Replacer le bouton", { store.update { it.copy(orbRight = true, orbY = 0.42f) } })
        }
        SettingsGroup("Affichage", "affichage") {
            SwitchRow("Masquer les barres en lisant", "Les barres se rangent quand vous descendez dans la page.", s.hideBarsWhileReading) { v -> store.update { it.copy(hideBarsWhileReading = v) } }
            HorizontalDivider()
            Text("Taille du texte traduit : ${String.format(Locale.FRANCE, "%.1f", s.textScale)}×", style = MaterialTheme.typography.titleMedium)
            Slider(value = s.textScale, onValueChange = { v -> store.update { it.copy(textScale = v) } }, valueRange = 0.5f..2f)
            Hint("Pour l'appliquer à la page ouverte : « Retraduire la page » dans le menu ⋮ de la barre d'adresse.")
        }
        SettingsGroup("Préparation en avance", "avance") {
            Text("Pages préparées en avance : ${s.maxPrefetchPages}", style = MaterialTheme.typography.titleMedium)
            Slider(value = s.maxPrefetchPages.toFloat(), onValueChange = { v -> store.update { it.copy(maxPrefetchPages = v.toInt()) } }, valueRange = 0f..10f, steps = 9)
            Hint("Moins de pages, moins de chauffe. La préparation se suspend toute seule si le téléphone chauffe.")
            if (graph.controller.thermalHot) Hint("Le téléphone est chaud : préparation suspendue pour l'instant.", MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun OcrSettings(graph: AppGraph, focus: String?) {
    val store = graph.settingsStore
    val s = store.settings
    PageScroll(focus) {
        SettingsGroup("Lecture du texte", "lecture") {
            SwitchRow(
                "Lecture renforcée du texte",
                if (graph.paddleModels.available) "PaddleOCR en plus de ML Kit : lettrages stylisés et texte hors des bulles. Un peu plus lent."
                else "Indisponible : les modèles ne sont pas inclus dans cette version.",
                s.enhancedOcr && graph.paddleModels.available, graph.paddleModels.available,
            ) { v -> store.update { it.copy(enhancedOcr = v) } }
        }
        SettingsGroup("Relecture par Gemma", "relecture") {
            SwitchRow("Relecture des textes difficiles (Gemma)", "Gemma relit sur l'image les lectures douteuses (manuscrit, incliné…) avant de traduire. Appuyez une fois sur « Tester » ; ensuite c'est automatique.", s.autoReread) { v -> store.update { it.copy(autoReread = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Tester la relecture", { graph.visionTest.run() }, enabled = !graph.visionTest.running)
                if (graph.visionTest.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (graph.visionTest.summary.isNotEmpty()) Hint(graph.visionTest.summary)
        }
    }
}

@Composable
private fun BrowsingSettings(graph: AppGraph, focus: String?) {
    val store = graph.settingsStore
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    PageScroll(focus) {
        SettingsGroup("Publicités", "pubs") {
            SwitchRow("Bloquer les publicités", "Régies, pop-ups, redirections et liens invisibles. Désactivable site par site depuis le menu ⋮ du navigateur.", s.blockAds) { v -> store.update { it.copy(blockAds = v) } }
        }
        SettingsGroup("Sites sans bloqueur", "sites") {
            val sites = s.adAllowSiteSet.sorted()
            if (sites.isEmpty()) Hint("Aucun. Éteignez le bloqueur d'un site depuis le menu ⋮ du navigateur.")
            for (site in sites) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(site, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { store.update { st -> st.copy(adAllowSites = (st.adAllowSiteSet - site).joinToString(",")) } }) { Text("Rétablir") }
            }
            val targets = s.adAllowTargetSet.sorted()
            if (targets.isNotEmpty()) {
                HorizontalDivider()
                SubTitle("Sites autorisés comme destination")
                Hint("Autorisés avec « Ouvrir » : les pages de lecture ne peuvent pas vous y envoyer d'elles-mêmes.")
                for (site in targets) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(site, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { store.update { st -> st.copy(adAllowTargets = (st.adAllowTargetSet - site).joinToString(",")) } }) { Text("Retirer") }
                }
            }
        }
    }
}

@Composable
private fun DataSettings(graph: AppGraph, focus: String?) {
    val ctx = LocalContext.current
    var cacheBytes by remember { mutableStateOf(graph.cache.diskBytes()) }
    val appBytes = remember { ctx.filesDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L } }
    val modelBytes = remember { graph.llm.usedBytes() }
    PageScroll(focus) {
        SettingsGroup("Stockage", "stockage") {
            Text("Cache de traductions : ${String.format(Locale.FRANCE, "%.1f", cacheBytes / 1_048_576.0)} Mo", style = MaterialTheme.typography.bodyMedium)
            Text("Stockage de l'application : ${String.format(Locale.FRANCE, "%.1f", appBytes / 1_048_576.0)} Mo", style = MaterialTheme.typography.bodyMedium)
            Text("Modèles Gemma stockés : ${String.format(Locale.FRANCE, "%.2f", modelBytes / 1_000_000_000.0)} Go", style = MaterialTheme.typography.bodyMedium)
            GhostButton("Vider le cache", {
                graph.cache.clear(); graph.controller.clearOverlays(); cacheBytes = graph.cache.diskBytes()
                Toast.makeText(ctx, "Cache vidé", Toast.LENGTH_SHORT).show()
            })
        }
    }
}

/** Options techniques, rarement utiles : accélération, diagnostic, journaux, listes de filtres du bloqueur. */
@Composable
private fun AdvancedSettings(graph: AppGraph, focus: String?) {
    val store = graph.settingsStore
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var logOpen by remember { mutableStateOf(false) }
    PageScroll(focus) {
        SettingsGroup("Performance", "performance") {
            SwitchRow("Accélération GPU (expérimental)", "Plus rapide pour Gemma, mais peut échouer selon le téléphone (repli automatique sur le processeur).", s.useGpu) { v -> store.update { it.copy(useGpu = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Tester CPU / GPU", { graph.gpuTest.run() }, enabled = !graph.gpuTest.running)
                if (graph.gpuTest.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (graph.gpuTest.summary.isNotEmpty()) Hint(graph.gpuTest.summary)
        }
        SettingsGroup("Diagnostic", "diagnostic") {
            var journalTick by remember { mutableStateOf(0) }
            val entries = remember(journalTick) { graph.journal.entries() }
            val kb = remember(journalTick) { graph.journal.sizeBytes() / 1024 }
            SwitchRow("Diagnostic de la détection", "Encadre le texte repéré (vert : utilisé, rouge : rejeté, avec la confiance). Désactive le cache.", s.diagnostic) { v -> store.update { it.copy(diagnostic = v) } }
            HorizontalDivider()
            Text("Journal de traduction", style = MaterialTheme.typography.titleMedium)
            Hint("$entries entrée(s), $kb Ko : texte lu, traduction et problèmes repérés, page par page. Il reste sur le téléphone ; exportez-le pour signaler des erreurs.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Exporter", { graph.journal.share(ctx) }, enabled = entries > 0)
                GhostButton("Effacer", { graph.journal.clear(); journalTick++ }, enabled = entries > 0)
            }
        }
        SettingsGroup("Bloqueur de publicités", "bloqueur") {
            Text("Listes de filtres", style = MaterialTheme.typography.titleMedium)
            Hint(graph.adFilters.summary)
            Hint("Issues d'uBlock Origin, EasyList et HaGeZi ; mises à jour toutes seules tous les quelques jours quand l'application est ouverte.")
            GhostButton(if (graph.adFilters.updating) "Mise à jour…" else "Mettre à jour maintenant", { graph.adFilters.updateAsync { updateMsg = it } }, enabled = !graph.adFilters.updating)
            updateMsg?.let { Hint(it, cs.primary) }
            HorizontalDivider()
            Text("Journal du bloqueur", style = MaterialTheme.typography.titleMedium)
            Hint("${graph.adFilters.log.total()} action(s) depuis le démarrage. Utile si une image n'apparaît pas : il dit quelle règle l'a bloquée.")
            GhostButton("Voir le journal", { logOpen = true })
        }
    }
    if (logOpen) {
        val text = graph.adFilters.log.export()
        AlertDialog(
            onDismissRequest = { logOpen = false },
            title = { Text("Journal du bloqueur") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(text.take(12000), style = MaterialTheme.typography.bodySmall) } },
            confirmButton = {
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                    ctx.startActivity(android.content.Intent.createChooser(send, "Partager le journal").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Partager") }
            },
            dismissButton = { Row { TextButton(onClick = { graph.adFilters.log.clear(); logOpen = false }) { Text("Effacer") }; TextButton(onClick = { logOpen = false }) { Text("Fermer") } } },
        )
    }
}
