package app.manhwa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.manhwa.core.AniList
import app.manhwa.core.AniListWork
import app.manhwa.core.GlossaryEntry
import app.manhwa.core.NameProposals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Suggestions de noms pour le glossaire d'une œuvre d'après AniList : l'utilisateur lance la recherche (le titre seul est envoyé), choisit l'œuvre, puis coche les noms à garder tels
 * quels. Rien n'est ajouté sans qu'il l'ait coché ; l'ajout passe par le même chemin que « Ajouter à cette œuvre » (aperçu des textes concernés quand des pages de l'œuvre sont ouvertes).
 */
@Composable
fun AniListSuggestPage(graph: AppGraph, series: String, title: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = graph.controller
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(title) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var works by remember { mutableStateOf<List<AniListWork>?>(null) }
    var work by remember { mutableStateOf<AniListWork?>(null) }
    var proposals by remember { mutableStateOf<NameProposals?>(null) }
    var truncated by remember { mutableStateOf(false) }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    val stage = if (proposals != null) 2 else if (works != null) 1 else 0

    BackHandler {
        when (stage) {
            2 -> { proposals = null; picked.clear(); error = null }
            1 -> { works = null; error = null }
            else -> onClose()
        }
    }

    fun search() {
        if (busy || query.isBlank()) return
        busy = true; error = null
        val text = query.trim()
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { AniListClient.search(text) }
                if (found.isEmpty()) error = "Aucune œuvre trouvée pour « $text ». Essaie un titre plus court, ou son titre anglais." else works = found
            } catch (e: Exception) {
                error = AniListClient.explain(e)
            } finally {
                busy = false
            }
        }
    }

    fun choose(w: AniListWork) {
        if (busy) return
        busy = true; error = null
        val known = (graph.glossary.terms.map { it.source.lowercase() } + graph.glossary.seriesTerms.filter { it.series == series }.map { it.source.lowercase() }).toSet()
        scope.launch {
            try {
                val (page, found) = withContext(Dispatchers.IO) {
                    val p = AniListClient.characters(w.id)
                    p to AniList.proposals(p.characters, known, Lexicon.get(ctx))
                }
                if (page.characters.isEmpty()) error = "AniList ne connaît aucun personnage pour cette œuvre."
                else { work = w; truncated = page.hasNext; picked.clear(); proposals = found }
            } catch (e: Exception) {
                error = AniListClient.explain(e)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suggestions de noms · AniList", style = MaterialTheme.typography.titleMedium)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium) }
        when (stage) {
            0 -> {
                Text(
                    "Je peux chercher les personnages de l'œuvre sur AniList (anilist.co) et te proposer leurs noms, à garder tels quels. " +
                        "Seul le titre ci-dessous est envoyé, et seulement quand tu appuies sur « Chercher ». Rien n'est ajouté au glossaire sans que tu l'aies coché.",
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Titre à chercher") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                Button(onClick = { search() }, enabled = query.isNotBlank() && !busy) { Text("Chercher") }
                TextButton(onClick = onClose) { Text("Retour au glossaire") }
            }
            1 -> {
                Text("Choisis l'œuvre qui correspond :", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(works.orEmpty(), key = { it.id }) { w ->
                        Card(Modifier.fillMaxWidth().clickable(enabled = !busy) { choose(w) }, colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(w.title, style = MaterialTheme.typography.titleSmall)
                                w.altTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                                Text(w.details(), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            }
                        }
                    }
                }
                TextButton(onClick = { works = null; error = null }) { Text("Autre recherche") }
            }
            else -> {
                val p = proposals ?: NameProposals(emptyList(), emptyList(), 0)
                val chosen = p.candidates.filter { picked[it.name] == true }
                Text(
                    "Personnages de « ${work?.title.orEmpty()} » · ${p.candidates.size} nom${if (p.candidates.size > 1) "s" else ""} proposé${if (p.candidates.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (p.candidates.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { picked.clear(); p.candidates.filter { it.role == "MAIN" }.forEach { picked[it.name] = true } }, label = { Text("Principaux") })
                    FilterChip(selected = false, onClick = { p.candidates.forEach { picked[it.name] = true } }, label = { Text("Tous") })
                    FilterChip(selected = false, onClick = { picked.clear() }, label = { Text("Aucun") })
                }
                LazyColumn(Modifier.weight(1f)) {
                    listOf("MAIN" to "Personnages principaux", "SUPPORTING" to "Personnages secondaires", "BACKGROUND" to "Autres personnages").forEach { (role, label) ->
                        val rows = p.candidates.filter { it.role == role || (role == "BACKGROUND" && it.role != "MAIN" && it.role != "SUPPORTING") }
                        if (rows.isNotEmpty()) {
                            item(key = "h-$role") { Text(label, Modifier.padding(top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = cs.primary) }
                            items(rows, key = { it.name }) { n ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { picked[n.name] = picked[n.name] != true }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = picked[n.name] == true, onCheckedChange = null)
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(n.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(n.owner, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (p.candidates.isEmpty()) item(key = "none") {
                        Text("Aucun nom à proposer : ils sont déjà dans le glossaire, ou trop proches de mots anglais courants.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (p.already > 0) item(key = "already") {
                        Text("${p.already} nom${if (p.already > 1) "s" else ""} déjà dans le glossaire de cette œuvre ou de toutes les œuvres.", Modifier.padding(top = 10.dp), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.skipped.isNotEmpty()) item(key = "skipped") {
                        Text(
                            "Non proposés (mots anglais courants, noms trop courts, ou qui se cachent dans des mots courants ; à ajouter à la main si besoin) : " +
                                p.skipped.take(14).joinToString(", ") + if (p.skipped.size > 14) "…" else "",
                            Modifier.padding(top = 10.dp), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (truncated) item(key = "more") {
                        Text("Les 50 premiers personnages seulement (les plus importants d'abord).", Modifier.padding(top = 10.dp), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = { c.addTermsWithPreview(chosen.map { GlossaryEntry(it.name, it.name) }, series) { onClose() } },
                    enabled = chosen.isNotEmpty(),
                ) { Text(if (chosen.isEmpty()) "Coche des noms à ajouter" else "Ajouter ${chosen.size} nom${if (chosen.size > 1) "s" else ""} à cette œuvre") }
                TextButton(onClick = { proposals = null; picked.clear(); error = null }) { Text("Choisir une autre œuvre") }
            }
        }
    }
}
