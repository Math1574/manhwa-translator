package app.manhwa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manhwa.core.FrenchAgreement
import app.manhwa.core.SeriesMemory

/** Résumé de la mémoire d'une œuvre pour sa fiche dans la bibliothèque. */
internal fun memorySummary(v: SeriesMemory.View?): String {
    if (v == null || v.isEmpty) return "Rien de retenu pour l'instant : elle se remplit à mesure que vous traduisez"
    val parts = ArrayList<String>()
    if (v.characters.isNotEmpty()) parts.add("${v.characters.size} personnage${if (v.characters.size > 1) "s" else ""}")
    if (v.terms.isNotEmpty()) parts.add("${v.terms.size} terme${if (v.terms.size > 1) "s" else ""}")
    if (v.narrator != null) parts.add("narrateur")
    if (v.confusions.isNotEmpty()) parts.add("lettres mal lues")
    return parts.joinToString(", ") + " : à vérifier et corriger"
}

/**
 * Mémoire d'une œuvre (voir [SeriesMemory]) : ce que la traduction en a retenu et rappelle au modèle — personnages (genre, tutoiement ou vouvoiement), genre du narrateur, termes
 * avec la réplique qui sert de référence, lettres que le lettrage fait mal lire. Chaque élément faux s'efface ; un élément effacé n'est plus réappris, et se rétablit en bas de la page.
 */
@Composable
fun SeriesMemoryScreen(graph: AppGraph, key: String) {
    val cs = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    var confirmAll by remember { mutableStateOf(false) }
    val premise = remember(key) { try { graph.covers.synopsisFrench(key) ?: graph.covers.synopsisOf(key) } catch (_: Throwable) { null } }
    val view = remember(key, tick) { try { graph.seriesMemory.stored(key).let { m -> synchronized(m) { m.view(premise, Lexicon.peek()) } } } catch (_: Throwable) { null } }
    fun edit(change: (SeriesMemory) -> Unit) { try { graph.seriesMemory.edit(key, change) } catch (_: Throwable) {}; tick++ }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Tout oublier sur cette œuvre ?") },
            text = { Text("Personnages, narrateur, termes et lettres mal lues seront oubliés ; ils se réapprendront au fil des prochains chapitres. Ce que vous avez effacé un par un le reste.") },
            confirmButton = { Button(onClick = { confirmAll = false; edit { it.clearLearned() } }) { Text("Tout oublier") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annuler") } },
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "intro") {
            Text(
                "Ce que l'application a retenu de cette œuvre en traduisant ses chapitres, et qu'elle rappelle au modèle pour rester cohérente. " +
                    "Elle n'apprend que des répliques réussies du premier coup. Effacez ce qui est faux : un élément effacé n'est plus réappris (il se rétablit en bas de la page).",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
        if (view == null || view.isEmpty) item(key = "empty") {
            Text("Rien de retenu pour l'instant : la mémoire se remplit à mesure que vous traduisez des chapitres de cette œuvre.", style = MaterialTheme.typography.bodyMedium)
        }
        if (view != null && view.characters.isNotEmpty()) {
            item(key = "h-c") { SectionLabel("Personnages (${view.characters.size})") }
            items(view.characters, key = { "c:" + it.key }) { c ->
                val bits = listOfNotNull(
                    when (c.gender) { FrenchAgreement.Gender.M -> "homme"; FrenchAgreement.Gender.F -> "femme"; null -> null },
                    when (c.register) { FrenchAgreement.Register.TU -> "tutoiement"; FrenchAgreement.Register.VOUS -> "vouvoiement"; null -> null },
                    "vu ${c.seen} fois",
                )
                MemoryRow(c.display, bits.joinToString(" · ")) { edit { it.forgetName(c.key) } }
            }
        }
        if (view?.narrator != null) {
            item(key = "h-n") { SectionLabel("Narrateur") }
            item(key = "n") {
                MemoryRow(
                    if (view.narrator == FrenchAgreement.Gender.M) "Le narrateur est un homme" else "La narratrice est une femme",
                    if (view.narratorFromPremise) "D'après le synopsis : ses pensées et sa narration sont accordées ainsi" else "D'après les accords des chapitres traduits",
                ) { edit { it.forgetNarrator() } }
            }
        }
        if (view != null && view.terms.isNotEmpty()) {
            item(key = "h-t") { SectionLabel("Termes de l'œuvre (${view.terms.size})") }
            item(key = "t-intro") {
                Text(
                    "Une réplique déjà traduite qui contient le terme est montrée au modèle pour qu'il le traduise pareil, une fois que deux chapitres l'ont traduit de la même façon.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            items(view.terms, key = { "t:" + it.term }) { t ->
                MemoryRow(
                    t.term, "« ${t.src} » → « ${t.tr} »",
                    badge = if (t.reference) "Référence (confirmée par ${t.chapters} chapitres)" else "En attente d'un autre chapitre",
                ) { edit { it.forgetTerm(t.term) } }
            }
        }
        if (view != null && view.confusions.isNotEmpty()) {
            item(key = "h-l") { SectionLabel("Lettres mal lues") }
            items(view.confusions, key = { "l:" + it.key }) { l ->
                MemoryRow(
                    "« ${l.meant} » lu comme « ${l.read} »",
                    "${l.words} mots" + (if (l.examples.isNotEmpty()) " · ex. " + l.examples.joinToString(", ") { "${it.first} → ${it.second}" } else ""),
                ) { edit { it.forgetConfusion(l.key) } }
            }
        }
        if (view != null && view.rejected.isNotEmpty()) {
            item(key = "h-x") { SectionLabel("Effacés (${view.rejected.size})") }
            items(view.rejected, key = { "x:" + it.kind + ":" + it.key }) { x ->
                val title = when (x.kind) {
                    "nom" -> "Personnage : ${x.key.replaceFirstChar { it.uppercase() }}"
                    "terme" -> "Terme : ${x.key}"
                    "lettres" -> "Lettres : « ${x.key.getOrNull(1) ?: '?'} » lu comme « ${x.key.getOrNull(0) ?: '?'} »"
                    else -> "Genre du narrateur"
                }
                MemoryRow(title, "N'est plus appris", action = "Rétablir") { edit { it.restore(x.kind, x.key) } }
            }
        }
        if (view != null && !view.isEmpty) item(key = "all") {
            GhostButton("Tout oublier sur cette œuvre", { confirmAll = true }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun MemoryRow(title: String, detail: String?, badge: String? = null, action: String = "Effacer", onAction: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = cs.primary)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
