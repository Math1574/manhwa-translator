package app.manhwa

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manhwa.core.HomeView
import app.manhwa.core.PageTitles
import app.manhwa.core.RelativeTime
import app.manhwa.core.ResumePlan
import app.manhwa.core.SeriesEntry
import app.manhwa.core.SiteTile
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang

/** Nombre de tuiles de sites par ligne. */
private const val SITE_COLUMNS = 4

/**
 * Accueil de l'onglet Lecture, affiché tant qu'aucune page n'est ouverte : « Reprendre » en un geste, les œuvres en cours avec leur avancement et les sites que vous lisez.
 * Ce n'est pas la bibliothèque : ni recherche, ni tri, ni statut, ni historique, seulement des raccourcis (« Tout voir » mène à la bibliothèque).
 */
@Composable
fun HomeScreen(graph: AppGraph, onLibrary: () -> Unit) {
    val c = graph.controller
    val lib = graph.library.library
    val s = graph.settingsStore.settings
    val now = System.currentTimeMillis()
    val series = remember(lib) { lib.series() }
    val ongoing = remember(series) { HomeView.ongoing(series) }
    val hero = ongoing.firstOrNull()
    val others = remember(ongoing) { ongoing.drop(1).take(HomeView.MAX_ONGOING) }
    val sites = remember(lib, s.homeSites, s.homeHidden) { HomeView.sites(lib, HomeView.pinned(s), HomeView.hidden(s), series = series) }
    val hiddenCount = remember(s.homeHidden) { HomeView.hidden(s).size }
    val saved = remember(lib.favorites) { lib.favorites.take(12).map { it to PageTitles.simple(it.url, it.title) } }
    val seen = remember { HashSet<Any>() }
    AppBackground(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { HomeHeader(s.source, s.target) }
            if (series.isEmpty() && sites.isEmpty() && saved.isEmpty()) {
                item { Welcome(graph) }
            } else {
                if (hero != null) item { Entrance("hero", 0, seen) { ResumeCard(graph, hero, now) } }
                else if (series.isNotEmpty()) item { AllFinished(onLibrary) }
                if (others.isNotEmpty()) {
                    item { SectionHeader("En cours", "Tout voir", onLibrary) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(others, key = { it.key }) { e ->
                                val plan = remember(lib, e) { HomeView.resumePlan(lib, e) }
                                PosterTile(e, plan) { c.load(plan.url, plan.resumeAt) }
                            }
                        }
                    }
                }
                item { SectionHeader("Vos sites") }
                item { SiteGrid(graph, sites) }
                if (hiddenCount > 0) item {
                    TextButton(onClick = { c.restoreSites() }) { Text("Rétablir les sites retirés ($hiddenCount)", style = MaterialTheme.typography.labelLarge) }
                }
                if (saved.isNotEmpty()) {
                    item { SectionHeader("Favoris") }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            saved.forEach { (f, t) -> AssistChip(onClick = { c.load(f.url) }, label = { Text(t.main, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
                        }
                    }
                }
            }
        }
    }
}

/** Nom de l'application, langues de la traduction en cours et mascotte. */
@Composable
private fun HomeHeader(source: SourceLang, target: TargetLang) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Manhwa Translator", style = MaterialTheme.typography.headlineSmall)
            Text(
                (if (source == SourceLang.AUTO) "Langue détectée" else sourceNames.getValue(source)) + " → " + targetNames.getValue(target) + " · sur le téléphone",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MascotImage(64.dp)
    }
}

/** Première ouverture : rien n'a encore été lu. */
@Composable
private fun Welcome(graph: AppGraph) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Text("Commencez par un site de lecture", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("1. Entrez l'adresse du site que vous lisez.", style = MaterialTheme.typography.bodyMedium)
            Text("2. Ouvrez un chapitre, puis touchez le bouton rond Original | Traduit pour le traduire.", style = MaterialTheme.typography.bodyMedium)
            Text("3. Avant la première traduction, téléchargez les modèles dans Réglages › Modèles de traduction.", style = MaterialTheme.typography.bodyMedium)
            Text("Vos œuvres, votre avancement et vos sites s'afficheront ici tout seuls.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        GradientButton("Saisir une adresse", { graph.controller.requestAddress(pin = true) }, Modifier.fillMaxWidth())
    }
}

/** Toutes les œuvres lues sont marquées terminées : rien à reprendre. */
@Composable
private fun AllFinished(onLibrary: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Text("Rien en cours", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Toutes vos œuvres sont marquées terminées. Retrouvez-les, ou rouvrez-en une, dans la bibliothèque.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        GhostButton("Ouvrir la bibliothèque", onLibrary)
    }
}

/** Titre de section, avec un lien à droite quand [action] est donné. */
@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = cs.primary)
        if (action != null) Text(action, Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = cs.primary)
    }
}

/** Couverture d'une œuvre : dégradé à la couleur de l'œuvre et ses initiales (l'application n'enregistre aucune image du chapitre). */
@Composable
private fun Poster(e: SeriesEntry, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(seriesColor(e.key), seriesColor(e.key + "·").copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(HomeView.initials(e.title), style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.92f), fontWeight = FontWeight.Bold)
    }
}

/** « Reprendre » : la dernière œuvre lue, son avancement, et un bouton qui rouvre le chapitre là où la lecture s'était arrêtée (ou ouvre le suivant s'il est lu). */
@Composable
private fun ResumeCard(graph: AppGraph, e: SeriesEntry, now: Long) {
    val c = graph.controller
    val cs = MaterialTheme.colorScheme
    val lib = graph.library.library
    val plan = remember(lib, e) { HomeView.resumePlan(lib, e) }
    val open = { c.load(plan.url, plan.resumeAt) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(cs.primaryContainer, cs.secondaryContainer))).clickable(onClick = open).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(e, Modifier.width(88.dp).height(128.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Dernière lecture", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer.copy(alpha = 0.75f))
            Text(e.title, style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(HomeView.caption(e, plan), style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.9f))
            if (!plan.next && e.percent > 0) {
                LinearProgressIndicator(
                    progress = { e.percent / 100f }, color = cs.tertiary, trackColor = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape),
                )
            }
            Text(
                listOfNotNull(if (e.doneChapters > 0) "${e.doneChapters} chapitre${if (e.doneChapters > 1) "s" else ""} lu${if (e.doneChapters > 1) "s" else ""}" else null, RelativeTime.fr(now, e.lastVisited)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            GradientButton(if (plan.next) "Chapitre suivant" else "Reprendre", open, Modifier.fillMaxWidth())
        }
    }
}

/** Une œuvre en cours : sa couverture, une barre d'avancement dans le chapitre (ou « Suivant » quand il est lu), son titre et où elle en est. */
@Composable
private fun PosterTile(e: SeriesEntry, plan: ResumePlan, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(136.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            Poster(e, Modifier.fillMaxSize())
            if (plan.next) {
                Text(
                    "Suivant", Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(cs.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = cs.onPrimary,
                )
            } else if (e.percent > 0) {
                LinearProgressIndicator(
                    progress = { e.percent / 100f }, color = cs.tertiary, trackColor = Color.Black.copy(alpha = 0.30f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp),
                )
            }
        }
        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(HomeView.caption(e, plan), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Les sites sur [SITE_COLUMNS] colonnes, avec en dernier la tuile « Ajouter ». */
@Composable
private fun SiteGrid(graph: AppGraph, sites: List<SiteTile>) {
    val cells: List<SiteTile?> = sites + listOf<SiteTile?>(null)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(SITE_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { cell ->
                    Box(Modifier.weight(1f)) { if (cell == null) AddSiteCell(graph) else SiteCell(graph, cell) }
                }
                repeat(SITE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Pastille d'un site : son icône quand le navigateur l'a reçue, sinon sa première lettre sur un fond à sa couleur. */
@Composable
private fun SiteIcon(graph: AppGraph, site: SiteTile, size: Dp) {
    val icon = remember(site.key) { graph.favicons.get(site.key) }
    val shape = RoundedCornerShape(size * 0.3f)
    val fill = if (icon != null) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.09f)))
    else Brush.linearGradient(listOf(seriesColor(site.key), seriesColor(site.key + "·").copy(alpha = 0.75f)))
    Box(Modifier.size(size).clip(shape).background(fill), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.size(size * 0.58f))
        else Text(site.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.95f))
    }
}

/** Tuile d'un site : un appui l'ouvre, un appui long propose de le retirer de l'accueil. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteCell(graph: AppGraph, site: SiteTile) {
    val c = graph.controller
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = { c.load(site.url) }, onLongClick = { menu = true }, onLongClickLabel = "Retirer de l'accueil").padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            SiteIcon(graph, site, 52.dp)
            Text(site.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (site.works > 0) "${site.works} œuvre${if (site.works > 1) "s" else ""}" else if (site.pinned) "Épinglé" else "Favori",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Ouvrir") }, onClick = { menu = false; c.load(site.url) })
            DropdownMenuItem(text = { Text("Retirer de l'accueil") }, onClick = { menu = false; c.hideSite(site.key) })
        }
    }
}

/** Dernière tuile : ouvre la barre d'adresse ; le site saisi est aussi épinglé à l'accueil. */
@Composable
private fun AddSiteCell(graph: AppGraph) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { graph.controller.requestAddress(pin = true) }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).border(1.5.dp, cs.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, "Ajouter un site", tint = cs.primary)
        }
        Text("Ajouter", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text("un site", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}
