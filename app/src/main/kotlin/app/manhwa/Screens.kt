package app.manhwa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import app.manhwa.core.LlmModels
import app.manhwa.core.ModelSpec
import app.manhwa.core.QualityMode
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import app.manhwa.core.TranslationEngineChoice
import app.manhwa.core.UrlInput
import java.util.Locale

// ---------------- Navigateur ----------------

@Composable
fun BrowserScreen(graph: AppGraph) {
    val c = graph.controller
    val ctx = LocalContext.current
    var address by remember { mutableStateOf(c.currentUrl) }
    LaunchedEffect(c.currentUrl) { address = c.currentUrl }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { c.back() }, enabled = c.canGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            IconButton(onClick = { c.forward() }, enabled = c.canGoForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Suivant") }
            IconButton(onClick = { if (c.loading) c.stopLoading() else c.reload() }) { Icon(Icons.Filled.Refresh, "Actualiser") }
            IconButton(onClick = { c.goHome() }) { Icon(Icons.Filled.Home, "Accueil") }
            TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adresse", c.currentUrl))
                Toast.makeText(ctx, "Adresse copiée", Toast.LENGTH_SHORT).show()
            }, enabled = c.currentUrl.isNotEmpty()) { Text("Copier") }
        }
        OutlinedTextField(
            value = address, onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            singleLine = true, placeholder = { Text("Adresse ou recherche") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { c.load(UrlInput.normalize(address)) }),
        )
        if (c.loading) LinearProgressIndicator(progress = { c.progress / 100f }, modifier = Modifier.fillMaxWidth())
        c.pageError?.let {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { c.reload() }) { Text("Réessayer") }
            }
        }
        if (c.active) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                val running = c.done + c.failed < c.total
                Text(
                    "Traduction : ${c.done}/${c.total}" + (if (c.failed > 0) " · ${c.failed} erreur(s)" else "") +
                        (if (c.noText > 0) " · ${c.noText} sans texte" else "") + (if (running) " …" else ""),
                    color = MaterialTheme.colorScheme.secondary,
                )
                c.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { c.obtainWebView(it) }, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (c.active) {
                    SmallFloatingActionButton(onClick = { c.toggleOverlays() }) { Text(if (c.overlaysVisible) "Masquer" else "Afficher", Modifier.padding(horizontal = 12.dp)) }
                }
                ExtendedFloatingActionButton(
                    onClick = { c.start(force = c.active) },
                    containerColor = MaterialTheme.colorScheme.secondary,
                ) { Text(if (c.active) "Retraduire" else "Traduire") }
            }
        }
    }
}

// ---------------- Modèles ----------------

@Composable
fun ModelsScreen(graph: AppGraph) {
    val m = graph.models
    LaunchedEffect(Unit) { m.refresh(); graph.llm.refresh() }
    val llm = graph.llm
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
                Text("Prête : modèles japonais, coréen, chinois et latin intégrés à l'application.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

private fun gb(bytes: Long) = String.format(Locale.FRANCE, "%.1f", bytes / 1_000_000_000.0)
private fun gemmaName(spec: ModelSpec) = if (spec == LlmModels.E2B) "Gemma 4 E2B" else "Gemma 4 E4B"

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

// ---------------- Glossaire ----------------

@Composable
fun GlossaryScreen(graph: AppGraph) {
    val g = graph.glossary
    var src by remember { mutableStateOf("") }
    var dst by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Glossaire", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Noms de personnages et termes récurrents : le terme d'origine est remplacé par votre traduction. Les données restent sur le téléphone. " +
                "Effacez le cache puis retraduisez pour appliquer un nouveau terme aux pages déjà traduites.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(src, { src = it }, Modifier.fillMaxWidth(), label = { Text("Terme d'origine (ex. 성진우)") }, singleLine = true)
        OutlinedTextField(dst, { dst = it }, Modifier.fillMaxWidth(), label = { Text("Traduction (ex. Sung Jin-Woo)") }, singleLine = true)
        Button(onClick = { g.add(src, dst); src = ""; dst = "" }, enabled = src.isNotBlank() && dst.isNotBlank()) { Text("Ajouter") }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(g.terms.toList()) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.source}  →  ${e.target}", Modifier.weight(1f))
                    TextButton(onClick = { g.remove(e.source) }) { Text("Retirer") }
                }
            }
        }
    }
}

// ---------------- Réglages ----------------

@Composable
fun SettingsScreen(graph: AppGraph) {
    val store = graph.settingsStore
    val s = store.settings
    var cacheBytes by remember { mutableStateOf(graph.cache.diskBytes()) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall)

        Text("Langue source", style = MaterialTheme.typography.titleMedium)
        val sourceNames = mapOf(
            SourceLang.AUTO to "Automatique", SourceLang.JA to "Japonais", SourceLang.KO to "Coréen",
            SourceLang.ZH_HANS to "Chinois simplifié", SourceLang.ZH_HANT to "Chinois traditionnel", SourceLang.EN to "Anglais",
        )
        SourceLang.values().forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.source == l, onClick = { store.update { it.copy(source = l) } })
                Text(sourceNames.getValue(l))
            }
        }
        Text("Chinois simplifié et traditionnel utilisent le même moteur de traduction (à vérifier sur des pages réelles).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Langue cible", style = MaterialTheme.typography.titleMedium)
        val targetNames = mapOf(TargetLang.FR to "Français", TargetLang.EN to "Anglais", TargetLang.ES to "Espagnol", TargetLang.DE to "Allemand")
        TargetLang.values().forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.target == l, onClick = { store.update { it.copy(target = l) } })
                Text(targetNames.getValue(l))
            }
        }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.engine == c, onClick = { store.update { it.copy(engine = c) } }, enabled = available)
                Column {
                    Text(engineNames.getValue(c), color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!available) Text("À télécharger dans l'onglet Modèles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Accélération GPU (expérimental)", style = MaterialTheme.typography.titleMedium)
                Text("Plus rapide pour Gemma, mais peut échouer selon le téléphone (repli automatique sur le processeur).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.useGpu, onCheckedChange = { v -> store.update { it.copy(useGpu = v) } })
        }

        HorizontalDivider()
        Text("Taille du texte : ${String.format(Locale.FRANCE, "%.1f", s.textScale)}×", style = MaterialTheme.typography.titleMedium)
        Slider(value = s.textScale, onValueChange = { v -> store.update { it.copy(textScale = v) } }, valueRange = 0.5f..2f)
        Text("Appuyez sur « Retraduire » pour appliquer à la page ouverte (résultats repris du cache, sans nouveau calcul).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Traduction automatique", style = MaterialTheme.typography.titleMedium)
                Text("Lance la traduction dès qu'une page est chargée.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.autoTranslate, onCheckedChange = { v -> store.update { it.copy(autoTranslate = v) } })
        }

        HorizontalDivider()
        Text("Mode de rendu", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = s.mode == QualityMode.FAST, onClick = { store.update { it.copy(mode = QualityMode.FAST) } })
            Text("Rapide : bulle de la couleur du fond d'origine")
        }
        Text("Le mode amélioré (effacement du texte d'origine) n'est pas encore disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Pages prétraitées en avance : ${s.maxPrefetchPages}", style = MaterialTheme.typography.titleMedium)
        Slider(value = s.maxPrefetchPages.toFloat(), onValueChange = { v -> store.update { it.copy(maxPrefetchPages = v.toInt()) } }, valueRange = 0f..10f, steps = 9)
        Text("Plus la valeur est basse, moins le téléphone est sollicité.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Moteurs et stockage", style = MaterialTheme.typography.titleMedium)
        Text("OCR : ML Kit, local (japonais, coréen, chinois, latin)")
        val ready = graph.models.languages.filter { graph.models.status[it.first] == "ready" }.joinToString(", ") { it.second }
        Text("Traduction : ML Kit, local. Modèles prêts : " + ready.ifEmpty { "aucun (voir l'onglet Modèles)" })
        Text("Cache de traductions : ${String.format(Locale.FRANCE, "%.1f", cacheBytes / 1_048_576.0)} Mo")
        Text("Stockage de l'application : ${String.format(Locale.FRANCE, "%.1f", ctx.filesDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L } / 1_048_576.0)} Mo")
        OutlinedButton(onClick = {
            graph.cache.clear(); graph.controller.clearOverlays(); cacheBytes = graph.cache.diskBytes()
            Toast.makeText(ctx, "Cache vidé", Toast.LENGTH_SHORT).show()
        }) { Text("Vider le cache") }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Diagnostic de la détection", style = MaterialTheme.typography.titleMedium)
                Text("Encadre le texte repéré par l'OCR (vert : utilisé, rouge : rejeté, avec la confiance). Désactive le cache. Utile pour signaler un problème.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.diagnostic, onCheckedChange = { v -> store.update { it.copy(diagnostic = v) } })
        }
        Text("Modèles Gemma stockés : ${String.format(Locale.FRANCE, "%.2f", graph.llm.usedBytes() / 1_000_000_000.0)} Go")
        Spacer(Modifier.height(24.dp))
    }
}
