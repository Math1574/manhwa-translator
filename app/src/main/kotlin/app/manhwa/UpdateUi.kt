package app.manhwa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import app.manhwa.core.UpdatePlanner
import java.util.Locale

private fun mo(bytes: Long) = String.format(Locale.FRANCE, "%.0f", bytes / 1_048_576.0)

/** Carte « Mise à jour » : version installée, nouveautés de la version disponible, téléchargement et installation. */
@Composable
fun UpdateCard(graph: AppGraph) {
    val u = graph.updater
    val cs = MaterialTheme.colorScheme
    var notesOpen by remember { mutableStateOf(false) }
    val latest = u.latest
    val latestNumber = latest?.let { UpdatePlanner.buildOf(it.tag) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Mise à jour de l'application", style = MaterialTheme.typography.titleMedium)
        Text(
            if (u.current > 0) "Version installée : build ${u.current}" else "Version de développement (sans numéro de build)",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        when (u.state) {
            UpdateState.IDLE -> {
                Text("Les mises à jour se vérifient toutes seules au lancement, au plus toutes les six heures. Rien ne se télécharge sans votre accord.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                GhostButton("Vérifier maintenant", { u.check() })
            }
            UpdateState.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Vérification…", style = MaterialTheme.typography.bodyMedium)
            }
            UpdateState.UP_TO_DATE -> {
                Text("Vous avez la dernière version.", style = MaterialTheme.typography.bodyMedium)
                if (u.lastCheckMs > 0) Text("Dernière vérification : " + app.manhwa.core.RelativeTime.fr(System.currentTimeMillis(), u.lastCheckMs), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                GhostButton("Vérifier de nouveau", { u.check() })
            }
            UpdateState.AVAILABLE, UpdateState.READY -> {
                val ready = u.state == UpdateState.READY
                Text(
                    (if (ready) "Version $latestNumber prête à installer" else "Version $latestNumber disponible") + (latest?.let { " (${mo(it.apkSize)} Mo)" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = cs.primary,
                )
                val preview = latest?.let { UpdatePlanner.cleanNotes(it.notes, 260) }.orEmpty()
                if (preview.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(preview, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ready) GradientButton("Installer", { u.install() }) else GradientButton("Télécharger", { u.download() })
                    GhostButton("Nouveautés", { notesOpen = true })
                }
                if (ready) {
                    Spacer(Modifier.height(8.dp))
                    Text("Android vous demandera de confirmer ; l'application se ferme pendant l'installation, rouvrez-la ensuite. Vos réglages, votre bibliothèque et vos modèles sont conservés.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            UpdateState.DOWNLOADING -> {
                Text("Téléchargement de la version $latestNumber : ${(u.progress * 100).toInt()} %" + (latest?.let { " (${u.receivedBytes / 1_048_576} sur ${mo(it.apkSize)} Mo)" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                GhostButton("Interrompre", { u.cancel() })
                Spacer(Modifier.height(6.dp))
                Text("Gardez l'application ouverte. Si la connexion coupe, le téléchargement reprend là où il s'est arrêté.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            UpdateState.ERROR -> {
                Text(u.message ?: "Une erreur est survenue.", style = MaterialTheme.typography.bodyMedium, color = cs.error)
                Spacer(Modifier.height(10.dp))
                GhostButton("Réessayer", { if (u.latest != null) u.download() else u.check() })
            }
        }
        val info = u.message
        if (info != null && u.state != UpdateState.ERROR) { Spacer(Modifier.height(8.dp)); Text(info, style = MaterialTheme.typography.bodySmall, color = cs.secondary) }
    }
    if (notesOpen) {
        AlertDialog(
            onDismissRequest = { notesOpen = false },
            title = { Text("Nouveautés") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(UpdatePlanner.combine(u.newer), style = MaterialTheme.typography.bodyMedium) } },
            confirmButton = { TextButton(onClick = { notesOpen = false }) { Text("Fermer") } },
        )
    }
}

/** Fenêtres affichées par-dessus l'application : nouveautés de la version qui vient d'être installée (une seule fois). */
@Composable
fun UpdateHost(graph: AppGraph) {
    val u = graph.updater
    val text = u.whatsNew ?: return
    AlertDialog(
        onDismissRequest = { u.dismissWhatsNew() },
        title = { Text("Mise à jour effectuée (build ${u.current})") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = { TextButton(onClick = { u.dismissWhatsNew() }) { Text("OK") } },
    )
}

/** Vrai quand une version plus récente attend (pastille sur l'onglet Réglages). */
fun UpdateManager.hasNews(): Boolean = state == UpdateState.AVAILABLE || state == UpdateState.READY || state == UpdateState.DOWNLOADING
