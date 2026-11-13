package app.manhwa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.manhwa.core.ChapterNav
import app.manhwa.core.LlmModels
import app.manhwa.core.ModelSpec
import app.manhwa.core.QualityMode
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import app.manhwa.core.TranslationEngineChoice
import app.manhwa.core.RelativeTime
import app.manhwa.core.UrlInput
import java.util.Locale

// ---------------- Modèles ----------------

@Composable
fun ModelsScreen(graph: AppGraph) {
    val m = graph.models
    LaunchedEffect(Unit) { m.refresh(); graph.llm.refresh(); graph.jaPack.refresh() }
    val llm = graph.llm
    val ctxForRam = LocalContext.current
    var confirm by remember { mutableStateOf<ModelSpec?>(null) }
    val anyDownload = llm.busy.values.any { it }
    val view = LocalView.current
    DisposableEffect(anyDownload) { view.keepScreenOn = anyDownload; onDispose { view.keepScreenOn = false } }

    confirm?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Télécharger ${gemmaName(spec)} ?") },
            text = {
                Text(
                    "Le fichier pèse ${gb(spec.sizeBytes)} Go. Utilisez le Wi-Fi et gardez l'application ouverte pendant le téléchargement ; " +
                        "il peut être repris s'il est interrompu. Une fois téléchargé, la traduction fonctionne sans connexion.",
                )
            },
            confirmButton = { Button(onClick = { llm.download(spec); confirm = null }) { Text("Télécharger") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Traduction de qualité (Gemma 4)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Modèle de langage exécuté sur le téléphone : il traduit toute la page d'un coup, en tenant compte du dialogue précédent et du glossaire. " +
                "Gratuit, sans compte, licence Apache 2.0. Choisissez-le ensuite dans les réglages.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val totalRamGb = remember {
            val mi = android.app.ActivityManager.MemoryInfo()
            (ctxForRam.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
            mi.totalMem / 1_073_741_824.0
        }
        Text(
            String.format(Locale.FRANCE, "Mémoire de ce téléphone : %.0f Go. ", totalRamGb) +
                if (totalRamGb >= 11) "E4B est recommandé (un peu plus précis) ; E2B reste plus rapide." else "E2B est recommandé.",
            color = MaterialTheme.colorScheme.secondary,
        )
        llm.specs.forEach { spec -> GemmaCard(spec, llm) { confirm = spec } }
        HorizontalDivider()
        Text("Traduction légère (ML Kit)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Chaque langue se télécharge une seule fois (environ 30 Mo, via les serveurs de Google). Ensuite la traduction fonctionne sans connexion. " +
                "L'anglais est intégré. Le japonais → français passe par l'anglais.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text("Reconnaissance du texte (OCR)", style = MaterialTheme.typography.titleMedium)
                Text("Prête : modèles coréen, chinois, latin et japonais horizontal intégrés à l'application. Le japonais vertical (manga) est un module à part, plus bas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        JapanesePackCard(graph)
        m.languages.forEach { (code, name) ->
            val st = m.status[code] ?: "absent"
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                st == "ready" -> "Prêt"
                                st == "downloading" -> "Téléchargement en cours…"
                                st.startsWith("error:") -> "Erreur : " + st.removePrefix("error:")
                                else -> "Absent (≈ 30 Mo)"
                            },
                            color = if (st.startsWith("error:")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (st == "downloading") LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    when (st) {
                        "ready" -> OutlinedButton(onClick = { m.delete(code) }) { Text("Supprimer") }
                        "downloading" -> {}
                        else -> Button(onClick = { m.download(code) }) { Text("Télécharger") }
                    }
                }
            }
        }
    }
}

internal fun gb(bytes: Long) = String.format(Locale.FRANCE, "%.1f", bytes / 1_000_000_000.0)
internal fun gemmaName(spec: ModelSpec) = if (spec == LlmModels.E2B) "Gemma 4 E2B" else "Gemma 4 E4B"

@Composable
private fun GemmaCard(spec: ModelSpec, llm: LlmModelsManager, onAskDownload: () -> Unit) {
    val ready = llm.ready[spec.id] == true
    val busy = llm.busy[spec.id] == true
    val pct = llm.progress[spec.id] ?: 0f
    val err = llm.error[spec.id]
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(gemmaName(spec), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (spec == LlmModels.E2B) "Rapide, bonne qualité · ${gb(spec.sizeBytes)} Go" else "Meilleure qualité, plus lent · ${gb(spec.sizeBytes)} Go",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when {
                        busy -> "Téléchargement : ${String.format(Locale.FRANCE, "%.1f", pct * 100)} % (${(llm.downloaded[spec.id] ?: 0L) / 1_000_000} Mo)"
                        ready -> "Prêt"
                        err != null -> "Erreur : $err"
                        else -> "Absent"
                    },
                    color = if (err != null && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (busy) LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            when {
                busy -> OutlinedButton(onClick = { llm.cancel(spec) }) { Text("Annuler") }
                ready -> OutlinedButton(onClick = { llm.delete(spec) }) { Text("Supprimer") }
                else -> Button(onClick = onAskDownload) { Text(if (err != null) "Reprendre" else "Télécharger") }
            }
        }
    }
}

@Composable
private fun JapanesePackCard(graph: AppGraph) {
    val pack = graph.jaPack
    var confirm by remember { mutableStateOf(false) }
    val size = String.format(Locale.FRANCE, "%.0f", pack.totalBytes / 1_048_576.0)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Télécharger le lecteur de japonais vertical ?") },
            text = { Text("Le module pèse environ $size Mo. Utilisez le Wi-Fi et gardez l'application ouverte ; le téléchargement reprend s'il est interrompu. Ensuite la lecture reste 100 % locale.") },
            confirmButton = { Button(onClick = { pack.download(); confirm = false }) { Text("Télécharger") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Japonais vertical (manga-ocr)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lit les colonnes de texte vertical des mangas japonais. Facultatif : sans lui, le japonais est lu par PaddleOCR et ML Kit. Module de $size Mo, téléchargé à la demande.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when {
                        pack.bundled -> "Intégré à cette version"
                        pack.busy -> "Téléchargement : ${String.format(Locale.FRANCE, "%.1f", pack.progress * 100)} %"
                        pack.ready -> "Prêt"
                        pack.error != null -> "Erreur : ${pack.error}"
                        !pack.offered -> "Indisponible dans cette version"
                        else -> "Non téléchargé"
                    },
                    color = if (pack.error != null && !pack.busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (pack.busy) LinearProgressIndicator(progress = { pack.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            when {
                pack.bundled || !pack.offered -> {}
                pack.busy -> OutlinedButton(onClick = { pack.cancel() }) { Text("Annuler") }
                pack.ready -> OutlinedButton(onClick = { try { graph.paddleModels.manga.close() } catch (_: Throwable) {}; pack.delete() }) { Text("Supprimer") }
                else -> Button(onClick = { confirm = true }) { Text(if (pack.error != null) "Reprendre" else "Télécharger") }
            }
        }
    }
}

// ---------------- Glossaire ----------------

private data class GlossaryRow(val series: String?, val source: String, val target: String)

@Composable
fun GlossaryScreen(graph: AppGraph) {
    val g = graph.glossary
    val c = graph.controller
    val series = c.currentSeriesKey
    val seriesName = if (series != null) (app.manhwa.core.SeriesHint.fromUrl(c.currentUrl) ?: series) else null
    var scopeSeries by remember { mutableStateOf(series != null) }
    var src by remember { mutableStateOf("") }
    var dst by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    val forSeries = scopeSeries && series != null
    val known = remember(tick, g.terms.size, g.seriesTerms.size) {
        (g.terms.map { it.source.lowercase() } + g.seriesTerms.filter { it.series == series }.map { it.source.lowercase() }).toSet()
    }
    val suggestions = remember(tick, series, known) { if (forSeries) graph.names.suggestions(series, known) else emptyList() }
    val rows = (if (forSeries) g.seriesTerms.filter { it.series == series }.map { GlossaryRow(it.series, it.source, it.target) } else emptyList()) +
        g.terms.map { GlossaryRow(null, it.source, it.target) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Noms de personnages et termes récurrents : le terme d'origine est remplacé par votre traduction. Un terme d'œuvre ne s'applique qu'à cette œuvre. " +
                "Effacez le cache puis retraduisez pour l'appliquer aux pages déjà traduites.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !forSeries, onClick = { scopeSeries = false }, label = { Text("Toutes les œuvres") })
            FilterChip(selected = forSeries, onClick = { scopeSeries = true }, enabled = series != null, label = { Text(if (seriesName != null) "Cette œuvre : $seriesName" else "Cette œuvre (ouvrez-en une)") })
        }
        if (suggestions.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Noms qui reviennent souvent", style = MaterialTheme.typography.titleSmall)
                    Text("Gardés tels quels, ils ne seront plus traduits de travers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    suggestions.forEach { name ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { g.addForSeries(series!!, name, name); tick++ }) { Text("Garder tel quel") }
                            TextButton(onClick = { graph.names.ignore(name); tick++ }) { Text("Ignorer") }
                        }
                    }
                }
            }
        }
        OutlinedTextField(src, { src = it }, Modifier.fillMaxWidth(), label = { Text("Terme d'origine (ex. 성진우)") }, singleLine = true)
        OutlinedTextField(dst, { dst = it }, Modifier.fillMaxWidth(), label = { Text("Traduction (ex. Sung Jin-Woo)") }, singleLine = true)
        Button(onClick = { if (forSeries) g.addForSeries(series!!, src, dst) else g.add(src, dst); src = ""; dst = "" }, enabled = src.isNotBlank() && dst.isNotBlank()) {
            Text(if (forSeries) "Ajouter à cette œuvre" else "Ajouter pour toutes les œuvres")
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { (it.series ?: "*") + "|" + it.source }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${e.source}  →  ${e.target}")
                        if (e.series != null) Text("Cette œuvre", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { if (e.series != null) g.removeForSeries(e.series, e.source) else g.remove(e.source) }) { Text("Retirer") }
                }
            }
        }
    }
}
