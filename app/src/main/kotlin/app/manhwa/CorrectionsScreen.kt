package app.manhwa

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.manhwa.core.ChapterCorrections
import app.manhwa.core.Corrections
import app.manhwa.core.RelativeTime

/** Résumé de la carte « Centre de corrections » des réglages : ce que l'on peut y défaire. */
internal fun correctionsSummary(graph: AppGraph): String {
    graph.edits.version      // lu ici pour que la carte se mette à jour après un changement
    val ignored = graph.ignored.texts.size + graph.ignored.images.size
    val terms = graph.glossary.terms.size + graph.glossary.seriesTerms.size
    val corrections = graph.edits.count()
    if (ignored + terms + corrections == 0) return "Rien à défaire pour l'instant"
    return "$corrections correction(s) · $terms terme(s) · $ignored ignoré(s)"
}

/**
 * Centre de corrections : tout ce que l'utilisateur a réglé à la main en lisant, au même endroit, avec de quoi le défaire : textes et images ignorés, termes du glossaire (communs et
 * d'une œuvre), corrections de texte rangées par chapitre. Ce centre sert à revoir et à défaire ; on ajoute toujours depuis la page lue (appui long) ou depuis le glossaire.
 */
@Composable
internal fun CorrectionsSettings(graph: AppGraph, focus: String?, openGlossary: () -> Unit) {
    val c = graph.controller
    val gl = graph.glossary
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val version = graph.edits.version
    val chapters = remember(version) { Corrections.byChapter(graph.edits.snapshot(), graph.edits.originsSnapshot()) }
    val retained = remember(version, gl.corrections.size) { Corrections.orphanRetained(graph.edits.snapshot().flatMap { it.edits }, gl.current(null)) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var confirm by remember { mutableStateOf<ChapterCorrections?>(null) }
    val now = remember(version) { System.currentTimeMillis() }
    val ignoredCount = graph.ignored.texts.size + graph.ignored.images.size
    val termCount = gl.terms.size + gl.seriesTerms.size
    val correctionCount = chapters.sumOf { it.items.size }
    PageScroll(focus) {
        Hint("Tout ce que vous avez réglé à la main en lisant, au même endroit : de quoi le revoir et le défaire. Rien n'est supprimé sans votre geste.")

        SettingsGroup("Textes et images ignorés ($ignoredCount)", "ignores") {
            IgnoredTextRows(graph)
            HorizontalDivider()
            IgnoredImageRows(graph)
        }

        SettingsGroup("Termes du glossaire ($termCount)", "glossaire") {
            Hint("Un terme est remplacé par votre traduction chaque fois qu'il apparaît. Le retirer ne change pas les pages déjà traduites : elles gardent leur traduction jusqu'à ce que la page soit retraduite (réglages rapides › Retraduire).")
            if (termCount == 0) Text("Aucun terme.", style = MaterialTheme.typography.bodyMedium)
            if (gl.terms.isNotEmpty()) {
                SubTitle("Toutes les œuvres")
                gl.terms.toList().forEach { t ->
                    TermRow(t.source, t.target, "Retirer") { gl.remove(t.source) }
                }
            }
            gl.seriesTerms.toList().groupBy { it.series }.forEach { (series, list) ->
                SubTitle(ignoredScopeLabel(series))
                list.forEach { t -> TermRow(t.source, t.target, "Retirer") { gl.removeForSeries(series, t.source) } }
            }
            HorizontalDivider()
            LinkRow("Ajouter ou modifier des termes", "Ouvre le glossaire", openGlossary)
        }

        SettingsGroup("Corrections par chapitre ($correctionCount)", "chapitres") {
            Hint("Une traduction écrite à la main, une traduction supprimée, un texte déplacé, agrandi ou ajouté : chaque correction est rangée sous le chapitre où elle a été faite. « Défaire » la retire et la traduction automatique revient.")
            if (chapters.isEmpty()) Text("Aucune correction.", style = MaterialTheme.typography.bodyMedium)
            chapters.forEachIndexed { i, ch ->
                val key = ch.url ?: "?"
                val isOpen = expanded[key] ?: (i == 0)
                if (i > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable { expanded[key] = !isOpen }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(ch.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${ch.items.size} correction(s)" + (if (ch.url != null) " · ${RelativeTime.fr(now, ch.lastAt)}" else " · faites avant que l'application retienne le chapitre"),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Icon(if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight, if (isOpen) "Replier" else "Déplier", tint = cs.onSurfaceVariant)
                }
                if (isOpen) {
                    ch.items.forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(item.label, style = MaterialTheme.typography.labelMedium, color = cs.primary)
                                Text(item.detail, style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = {
                                c.undoCorrections(listOf(item))
                                Toast.makeText(ctx, "Correction défaite", Toast.LENGTH_SHORT).show()
                            }) { Text("Défaire") }
                        }
                    }
                    if (ch.items.size > 1) TextButton(onClick = { confirm = ch }) { Text("Tout défaire pour ce chapitre") }
                }
            }
            if (retained.isNotEmpty()) {
                HorizontalDivider()
                SubTitle("Traductions retenues (${retained.size})")
                Hint("Une traduction écrite à la main est reprise chaque fois que le même texte est lu. Celles-ci ne sont plus liées à une correction listée ci-dessus (rétablie depuis l'éditeur, par exemple).")
                retained.forEach { r -> TermRow(r.source, r.translation, "Retirer") { gl.removeCorrection(r.source) } }
            }
        }
    }
    confirm?.let { ch ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Défaire les corrections de ce chapitre ?") },
            text = { Text("${ch.items.size} correction(s) de « ${ch.title} » seront retirées : les traductions automatiques reviennent sur ces pages.") },
            confirmButton = {
                TextButton(onClick = {
                    c.undoCorrections(ch.items); confirm = null
                    Toast.makeText(ctx, "Corrections défaites", Toast.LENGTH_SHORT).show()
                }) { Text("Tout défaire") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

/** Une ligne « texte → traduction » avec son bouton. */
@Composable
private fun TermRow(source: String, target: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$source  →  $target", Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick) { Text(action) }
    }
}
