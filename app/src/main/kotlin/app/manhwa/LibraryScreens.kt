package app.manhwa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import app.manhwa.core.RelativeTime
import app.manhwa.core.SeriesEntry

/** Part de la page à partir de laquelle on propose de reprendre là où l'on s'était arrêté (en dessous, on repart du début). */
private fun resumeAt(percent: Int, done: Boolean) = if (!done && percent in 4..93) percent else 0

/** Bibliothèque : œuvres lues, reprise de lecture, chapitres de chaque œuvre, favoris et historique. [onOpen] reçoit l'adresse et l'avancement à reprendre (0 = depuis le début). */
@Composable
fun LibraryScreen(graph: AppGraph, onOpen: (url: String, resumeAt: Int) -> Unit) {
    val lib = graph.library.library
    var seriesKey by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(false) }
    BackHandler(enabled = seriesKey != null || history) { if (history) history = false else seriesKey = null }
    val key = seriesKey
    val exists = key == null || lib.series().any { it.key == key }
    if (key != null && !exists) LaunchedEffect(key) { seriesKey = null }
    when {
        history -> HistoryPage(graph, onBack = { history = false }, onOpen = { u -> onOpen(u, 0) })
        key != null && exists -> SeriesPage(graph, key, onBack = { seriesKey = null }, onOpen = onOpen)
        else -> LibraryHome(graph, onOpenSeries = { seriesKey = it }, onHistory = { history = true }, onOpen = onOpen)
    }
}

@Composable
private fun LibraryHome(graph: AppGraph, onOpenSeries: (String) -> Unit, onHistory: () -> Unit, onOpen: (String, Int) -> Unit) {
    val lib = graph.library.library
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val series = remember(lib) { lib.series() }
    var onlyFav by remember { mutableStateOf(false) }
    val shown = if (onlyFav) series.filter { it.favorite } else series
    val seen = remember { HashSet<Any>() }
    val read = series.sumOf { it.doneChapters }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                "Bibliothèque",
                if (series.isEmpty()) "Vos lectures apparaîtront ici" else "${series.size} œuvre${if (series.size > 1) "s" else ""} · $read chapitre${if (read > 1) "s" else ""} lu${if (read > 1) "s" else ""}",
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
            val last = series.first()
            item {
                Entrance("resume", 0, seen) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.secondaryContainer)))
                            .clickable { onOpen(last.lastUrl, resumeAt(last.percent, lib.progressOf(last.lastUrl)?.done == true)) }
                            .padding(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("CONTINUER LA LECTURE", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer.copy(alpha = 0.75f))
                            Text(last.title, style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(last.chapter, if (last.percent > 0) "${last.percent} %" else null, RelativeTime.fr(now, last.lastVisited)).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                            if (last.percent > 0) {
                                LinearProgressIndicator(
                                    progress = { last.percent / 100f }, color = cs.tertiary, trackColor = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(CircleShape),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            GradientButton("Reprendre", { onOpen(last.lastUrl, resumeAt(last.percent, lib.progressOf(last.lastUrl)?.done == true)) })
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(selected = !onlyFav, onClick = { onlyFav = false }, label = { Text("Toutes (${series.size})") })
                    FilterChip(selected = onlyFav, onClick = { onlyFav = true }, label = { Text("Favoris (${series.count { it.favorite }})") })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onHistory) {
                        Icon(AppIcons.Clock, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Historique")
                    }
                }
            }
            if (shown.isEmpty()) item { Text("Aucun favori : touchez le cœur dans la barre de lecture ou dans la fiche d'une œuvre.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            items(shown.withIndex().toList(), key = { it.value.key }) { (i, e) -> Entrance(e.key, i + 1, seen) { SeriesRow(graph, e, now) { onOpenSeries(e.key) } } }
        }
        if (lib.favorites.isNotEmpty()) {
            item { SectionLabel("Pages enregistrées") }
            items(lib.favorites, key = { "f" + it.url }) { f ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(f.url, 0) }, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.title.ifEmpty { f.url }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(f.url.substringAfter("://").substringBefore('/'), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { graph.library.removeFavorite(f.url) }) { Icon(Icons.Filled.Delete, "Retirer") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SeriesRow(graph: AppGraph, e: SeriesEntry, now: Long, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(seriesColor(e.key), seriesColor(e.key + "·").copy(alpha = 0.55f)))))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (e.chapters > 0) "${e.chapters} chapitre${if (e.chapters > 1) "s" else ""}" else null,
                        if (e.doneChapters > 0) "${e.doneChapters} lu${if (e.doneChapters > 1) "s" else ""}" else null,
                        RelativeTime.fr(now, e.lastVisited),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
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
private fun SeriesPage(graph: AppGraph, key: String, onBack: () -> Unit, onOpen: (String, Int) -> Unit) {
    val lib = graph.library.library
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val entry = remember(lib, key) { lib.series().firstOrNull { it.key == key } }
    val chapters = remember(lib, key) { lib.chapters(key) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
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
        item { SectionLabel("Chapitres (${chapters.size})") }
        items(chapters.withIndex().toList(), key = { it.value.url }) { (i, ch) ->
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

/** Historique des pages ouvertes, la plus récente d'abord. */
@Composable
private fun HistoryPage(graph: AppGraph, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val lib = graph.library.library
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Text("Historique", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { graph.library.clearHistory() }, enabled = lib.history.isNotEmpty()) { Text("Tout effacer") }
            }
        }
        if (lib.history.isEmpty()) item { Text("Aucune page ouverte pour l'instant.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(lib.history.take(200), key = { "h" + it.url }) { h ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(h.url) }, contentPadding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(h.title.ifEmpty { h.url }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(RelativeTime.fr(now, h.visitedAt) + " · " + h.url.substringAfter("://").substringBefore('/'), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { graph.library.removeHistory(h.url) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
