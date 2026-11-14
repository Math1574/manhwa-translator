package app.manhwa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manhwa.core.GlossaryEntry

/** Un texte concerné par des termes en cours d'ajout : le texte d'origine, sa traduction actuelle et celle qu'il aurait ensuite. */
class TermPreviewRow(val original: String, val before: String, val after: String)

/**
 * Fenêtre d'aperçu ouverte avant l'enregistrement de termes du glossaire : seuls les textes qui contiennent un des termes sont retraduits (à part), le reste des pages ne change pas.
 * Rien n'est enregistré avant la confirmation.
 */
class TermPreview(val terms: List<GlossaryEntry>, val scope: String) {
    var computing by mutableStateOf(true)
    /** Aucune retraduction n'a pu être calculée (moteur indisponible) : l'enregistrement reste possible, les pages se mettront à jour à l'affichage. */
    var failed by mutableStateOf(false)
    val rows = mutableStateListOf<TermPreviewRow>()
    /** Autres textes concernés, sur d'autres pages ouvertes : retraduits à l'enregistrement, sans aperçu. */
    var others by mutableStateOf(0)
    @Volatile var cancelled = false
    var onConfirm: () -> Unit = {}
}

/** Fenêtre « Retraduire les textes concernés ? » : posée sur toute l'application, donc au-dessus de l'éditeur comme des réglages. */
@Composable
fun TermPreviewHost(graph: AppGraph) {
    val c = graph.controller
    val pv = c.termPreview ?: return
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { c.cancelTermPreview() },
        title = { Text("Retraduire les textes concernés ?") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pv.terms.forEach { t -> Text("« ${t.source} » → « ${t.target} »", style = MaterialTheme.typography.titleSmall) }
                Text("Pour ${pv.scope}.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (pv.computing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Calcul de l'aperçu…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (pv.rows.isNotEmpty()) Text("Textes concernés", style = MaterialTheme.typography.labelLarge)
                    pv.rows.forEach { r ->
                        HorizontalDivider()
                        Text(r.original, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Avant : ${r.before}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Text("Après : ${r.after}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (pv.others > 0) Text("+ ${pv.others} autre(s) texte(s) sur d'autres pages ouvertes, retraduit(s) de la même façon.", style = MaterialTheme.typography.bodySmall)
                    if (pv.failed) Text("L'aperçu n'a pas pu être calculé (moteur de traduction indisponible). Vous pouvez enregistrer : les pages se mettront à jour à l'affichage.", style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
                HorizontalDivider()
                Text(
                    "Seuls les textes qui contiennent ces termes sont retraduits ; le reste de la page ne change pas. Les autres pages déjà traduites se mettent à jour quand vous les affichez.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { c.confirmTermPreview() }, enabled = !pv.computing) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = { c.cancelTermPreview() }) { Text("Annuler") } },
    )
}
