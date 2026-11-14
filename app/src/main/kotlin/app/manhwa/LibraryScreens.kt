package app.manhwa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manhwa.core.BrowsingLibrary
import app.manhwa.core.ChapterNav
import app.manhwa.core.LibraryView
import app.manhwa.core.PageTitles
import app.manhwa.core.RelativeTime
import app.manhwa.core.SeriesEntry
import app.manhwa.core.SeriesFilter
import app.manhwa.core.SeriesSort

/** Part de la page à partir de laquelle on propose de reprendre là où l'on s'était arrêté (en dessous, on repart du début). */
private fun resumeAt(percent: Int, done: Boolean) = if (!done && percent in 4..93) percent else 0

/** Ce qui est tapé ou choisi dans la liste des œuvres : conservé quand on ouvre une œuvre puis qu'on revient. */
private class LibraryUi {
    var query by mutableStateOf("")
    var filter by mutableStateOf(SeriesFilter.ALL)
}

/**
 * Bibliothèque : œuvres lues (recherche, filtre En cours / Terminées / Favoris, tri), chapitres de chaque œuvre, pages enregistrées et historique. La reprise de lecture est sur l'accueil.
 * [onOpen] reçoit l'adresse et l'avancement à reprendre (0 = depuis le début). Les titres sont toujours simples : l'œuvre et le chapitre, pas le titre brut de la page.
 */
@Composable
fun LibraryScreen(graph: AppGraph, onOpen: (url: String, resumeAt: Int) -> Unit) {
    val lib = graph.library.library
    val ui = remember { LibraryUi() }
    var seriesKey by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(false) }
    var glossary by remember { mutableStateOf(false) }
    BackHandler(enabled = seriesKey != null || history) { if (history) history = false else if (glossary) glossary = false else seriesKey = null }
    val key = seriesKey
    val entry = if (key == null) null else lib.series().firstOrNull { it.key == key }
    val exists = key == null || entry != null
    if (key != null && !exists) LaunchedEffect(key) { seriesKey = null; glossary = false }
    when {
        history -> HistoryPage(graph, onBack = { history = false }, onOpen = { u -> onOpen(u, 0) })
        key != null && entry != null && glossary -> SubPageBody("Glossaire · ${entry.title}", onBack = { glossary = false }) { GlossaryScreen(graph, key to entry.title) }
        key != null && exists -> SeriesPage(graph, key, onBack = { seriesKey = null }, onGlossary = { glossary = true }, onOpen = onOpen)
        else -> LibraryHome(graph, ui, onOpenSeries = { seriesKey = it }, onHistory = { history = true }, onOpen = onOpen)
    }
}

@Composable
private fun LibraryHome(graph: AppGraph, ui: LibraryUi, onOpenSeries: (String) -> Unit, onHistory: () -> Unit, onOpen: (String, Int) -> Unit) {
    val lib = graph.library.library
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val series = remember(lib) { lib.series() }
    val sort = SeriesSort.of(graph.settingsStore.settings.librarySort)
    val shown = remember(series, ui.query, ui.filter, sort) { LibraryView.series(series, ui.query, ui.filter, sort) }
    val counts = remember(series, ui.query) { LibraryView.counts(series, ui.query) }
    val saved = remember(lib.favorites, ui.query) {
        lib.favorites.map { it to PageTitles.simple(it.url, it.title) }.filter { (f, t) -> LibraryView.matches(ui.query, t.main, t.detail ?: "", PageTitles.site(f.url)) }
    }
    val seen = remember { HashSet<Any>() }
    val read = series.sumOf { it.doneChapters }
    val finished = series.count { it.finished }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                "Bibliothèque",
                if (series.isEmpty()) "Vos lectures apparaîtront ici"
                else "${series.size} œuvre${if (series.size > 1) "s" else ""} · $read chapitre${if (read > 1) "s" else ""} lu${if (read > 1) "s" else ""}" +
                    (if (finished > 0) " · $finished terminée${if (finished > 1) "s" else ""}" else ""),
            )
        }
        if (series.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 22.dp) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MascotImage(132.dp) }
                    Spacer(Modifier.height(8.dp))
                    Text("Rien à reprendre pour l'instant", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ouvrez un chapitre dans l'onglet Lecture : l'œuvre, ses chapitres et l'endroit où vous vous êtes arrêté s'ajouteront ici tout seuls.",
                        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            item {
                // recherche, tri (menu) et historique sur une seule ligne : la liste des œuvres reste en vue
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SearchField(ui.query, { ui.query = it }, "Rechercher une œuvre") }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(AppIcons.Sort, "Trier", tint = if (sort != SeriesSort.RECENT) cs.primary else cs.onSurfaceVariant) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            SeriesSort.values().forEach { option ->
                                val mark: (@Composable () -> Unit)? = if (option == sort) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null
                                DropdownMenuItem(
                                    text = { Text(option.label) }, leadingIcon = mark,
                                    onClick = { menu = false; graph.settingsStore.update { it.copy(librarySort = option.id) } },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onHistory) { Icon(AppIcons.Clock, "Historique", tint = cs.onSurfaceVariant) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeriesFilter.values().forEach { f ->
                        FilterChip(selected = ui.filter == f, onClick = { ui.filter = f }, label = { Text("${f.label} (${counts[f] ?: 0})") })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        when {
                            ui.query.isNotBlank() -> "Aucune œuvre ne correspond à « ${ui.query.trim()} »."
                            ui.filter == SeriesFilter.FAVORITES -> "Aucun favori : touchez le cœur dans la barre de lecture ou dans la fiche d'une œuvre."
                            ui.filter == SeriesFilter.FINISHED -> "Aucune œuvre terminée : ouvrez une œuvre et choisissez « Terminée »."
                            else -> "Aucune œuvre en cours."
                        },
                        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(shown.withIndex().toList(), key = { it.value.key }) { (i, e) -> Entrance(e.key, i + 1, seen) { SeriesRow(graph, e, now) { onOpenSeries(e.key) } } }
        }
        if (saved.isNotEmpty()) {
            item { SectionLabel("Pages enregistrées") }
            items(saved, key = { "f" + it.first.url }) { (f, t) ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(f.url, 0) }, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.main, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(t.detail, PageTitles.site(f.url)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { graph.library.removeFavorite(f.url) }) { Icon(Icons.Filled.Delete, "Retirer") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Pastille de statut (« Terminée »). */
@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

@Composable
private fun SeriesRow(graph: AppGraph, e: SeriesEntry, now: Long, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(seriesColor(e.key), seriesColor(e.key + "·").copy(alpha = 0.55f)))))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.finished) StatusChip("Terminée", cs.primary)
                    Text(
                        listOfNotNull(
                            if (e.chapters > 0) "${e.chapters} chapitre${if (e.chapters > 1) "s" else ""}" else null,
                            if (e.doneChapters > 0) "${e.doneChapters} lu${if (e.doneChapters > 1) "s" else ""}" else null,
                            RelativeTime.fr(now, e.lastVisited),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
                if (e.chapters > 0 && e.doneChapters > 0) {
                    LinearProgressIndicator(
                        progress = { (e.doneChapters.toFloat() / e.chapters).coerceIn(0f, 1f) }, color = cs.primary, trackColor = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(0.7f).padding(top = 4.dp).height(4.dp).clip(CircleShape),
                    )
                }
            }
            IconButton(onClick = { graph.library.toggleSeriesFavorite(e.key, e.title) }) {
                Icon(
                    if (e.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (e.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                    tint = if (e.favorite) cs.secondary else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** Fiche d'une œuvre : continuer, chapitre suivant, page de l'œuvre et liste des chapitres avec leur avancement. */
@Composable
private fun SeriesPage(graph: AppGraph, key: String, onBack: () -> Unit, onGlossary: () -> Unit, onOpen: (String, Int) -> Unit) {
    val lib = graph.library.library
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val entry = remember(lib, key) { lib.series().firstOrNull { it.key == key } }
    val chapters = remember(lib, key) { lib.chapters(key) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var chapterQuery by remember { mutableStateOf("") }
    val shownChapters = remember(chapters, chapterQuery) { LibraryView.chapters(chapters, chapterQuery) }
    val seen = remember { HashSet<Any>() }
    if (entry == null) return
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Oublier cette œuvre ?") },
            text = { Text("Ses chapitres, leur avancement et son favori seront retirés de la bibliothèque. Les traductions déjà calculées restent en cache.") },
            confirmButton = { Button(onClick = { confirm = false; graph.library.removeSeries(key); onBack() }) { Text("Oublier") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        )
    }
    val lastDone = lib.progressOf(entry.lastUrl)?.done == true
    val resume = resumeAt(entry.percent, lastDone)
    val nextUrl = if (lastDone) ChapterNav.next(entry.lastUrl) else null
    val seriesUrl = BrowsingLibrary.seriesUrlOf(entry.lastUrl)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { graph.library.toggleSeriesFavorite(key, entry.title) }) {
                    Icon(
                        if (entry.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (entry.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (entry.favorite) cs.secondary else cs.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus d'actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Oublier cette œuvre") }, onClick = { menu = false; confirm = true })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).fillMaxHeight().clip(CircleShape).background(Brush.verticalGradient(listOf(seriesColor(key), seriesColor(key + "·")))))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entry.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(entry.host, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !entry.finished, onClick = { graph.library.setFinished(key, false) }, label = { Text("En cours") })
                    FilterChip(selected = entry.finished, onClick = { graph.library.setFinished(key, true) }, label = { Text("Terminée") })
                }
                if (entry.finished) Text("Elle repasse en cours dès que vous ouvrez un nouveau chapitre.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (nextUrl != null) {
                    GradientButton("Lire le chapitre suivant", { onOpen(nextUrl, 0) }, Modifier.fillMaxWidth())
                    GhostButton("Relire · ${entry.chapter ?: "dernière page"}", { onOpen(entry.lastUrl, 0) }, Modifier.fillMaxWidth())
                } else {
                    GradientButton(
                        if (resume > 0) "Reprendre · ${entry.chapter ?: "dernière page"} ($resume %)" else "Continuer · ${entry.chapter ?: "dernière page"}",
                        { onOpen(entry.lastUrl, resume) }, Modifier.fillMaxWidth(),
                    )
                }
                if (seriesUrl != null) GhostButton("Page de l'œuvre sur le site", { onOpen(seriesUrl, 0) }, Modifier.fillMaxWidth())
            }
        }
        item {
            val n = graph.glossary.seriesTerms.count { it.series == key }
            GlassCard(Modifier.fillMaxWidth(), onClick = onGlossary, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Bookmark, null, Modifier.size(22.dp), tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Glossaire de cette œuvre", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (n == 0) "Aucun terme : noms et mots à traduire comme vous le voulez" else "$n terme${if (n > 1) "s" else ""} propre${if (n > 1) "s" else ""} à cette œuvre",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }
        item { SectionLabel(if (chapterQuery.isBlank()) "Chapitres (${chapters.size})" else "Chapitres (${shownChapters.size} sur ${chapters.size})") }
        if (chapters.size > 8) item { SearchField(chapterQuery, { chapterQuery = it }, "Chercher un chapitre (numéro ou mot)") }
        if (shownChapters.isEmpty() && chapterQuery.isNotBlank()) item { Text("Aucun chapitre ne correspond.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        items(shownChapters.withIndex().toList(), key = { it.value.url }) { (i, ch) ->
            Entrance(ch.url, i, seen) {
                GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(ch.url, resumeAt(ch.percent, ch.done)) }, contentPadding = 0.dp) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(ch.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val chipColor = if (ch.done) cs.primary else if (ch.percent > 0) cs.tertiary else cs.onSurfaceVariant
                                Text(
                                    if (ch.done) "Lu" else if (ch.percent > 0) "En cours · ${ch.percent} %" else "Ouvert",
                                    style = MaterialTheme.typography.labelMedium, color = chipColor,
                                    modifier = Modifier.clip(CircleShape).background(chipColor.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 2.dp),
                                )
                                Text(RelativeTime.fr(now, ch.lastVisited), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            if (!ch.done && ch.percent > 0) {
                                LinearProgressIndicator(
                                    progress = { ch.percent / 100f }, color = cs.tertiary, trackColor = Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 14.dp).height(4.dp).clip(CircleShape),
                                )
                            }
                        }
                        IconButton(onClick = { graph.library.markDone(ch.url, !ch.done) }) {
                            Icon(
                                AppIcons.DoneAll, if (ch.done) "Marquer comme non lu" else "Marquer comme lu",
                                tint = if (ch.done) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Historique des pages ouvertes, la plus récente d'abord, en titres simples (l'œuvre et le chapitre) ; la recherche porte sur ces titres et sur le site. */
@Composable
private fun HistoryPage(graph: AppGraph, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val lib = graph.library.library
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    var query by remember { mutableStateOf("") }
    val titled = remember(lib.history) { lib.history.map { it to PageTitles.simple(it.url, it.title) } }
    val rows = remember(titled, query) {
        (if (query.isBlank()) titled else titled.filter { (h, t) -> LibraryView.matches(query, t.main, t.detail ?: "", PageTitles.site(h.url)) }).take(200)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Text("Historique", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { graph.library.clearHistory() }, enabled = lib.history.isNotEmpty()) { Text("Tout effacer") }
            }
        }
        if (lib.history.isNotEmpty()) item { SearchField(query, { query = it }, "Rechercher dans l'historique") }
        if (lib.history.isEmpty()) item { Text("Aucune page ouverte pour l'instant.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        else if (rows.isEmpty()) item { Text("Aucune page ne correspond.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(rows, key = { "h" + it.first.url }) { (h, t) ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(h.url) }, contentPadding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(t.main, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(t.detail, PageTitles.site(h.url), RelativeTime.fr(now, h.visitedAt)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { graph.library.removeHistory(h.url) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
