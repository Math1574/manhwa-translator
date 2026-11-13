package app.manhwa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.manhwa.core.BrowsingLibrary
import app.manhwa.core.ChapterNav
import app.manhwa.core.UrlInput
import kotlinx.coroutines.delay

/** Adresse lisible : sans schéma, sans « www. » ni barre finale. */
private fun prettyAddress(url: String) = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

// ---------------- Navigateur ----------------

@Composable
fun BrowserScreen(graph: AppGraph) {
    val c = graph.controller
    val st = graph.settingsStore.settings
    val running = c.active && c.done + c.failed < c.total

    Column(Modifier.fillMaxSize()) {
        // Barre d'adresse : visible à l'arrêt, se masque quand on lit en descendant et revient en remontant ou en touchant la page.
        AnimatedVisibility(visible = !c.fullscreen && !c.barsHidden, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))) {
                AddressBar(graph)
                if (c.loading) LinearProgressIndicator(progress = { c.progress / 100f }, modifier = Modifier.fillMaxWidth())
                c.pageError?.let {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = { c.reload() }) { Text("Réessayer") }
                    }
                }
                // Rien n'est affiché tant que tout va bien : seulement une fine barre sur la page.
                if (c.active && (c.failed > 0 || c.lastError != null)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(
                            "Traduction : ${c.done}/${c.total}" + (if (c.failed > 0) " · ${c.failed} erreur(s)" else "") +
                                (if (c.noText > 0) " · ${c.noText} sans texte" else "") + (if (running) " …" else ""),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        c.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { c.obtainWebView(it) },
                update = { it.keepScreenOn = c.fullscreen || c.active },
                modifier = Modifier.fillMaxSize(),
            )
            if (running && !c.fullscreen && c.total > 0) {
                LinearProgressIndicator(
                    progress = { (c.done + c.failed).toFloat() / c.total }, trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
                )
            }
            if (!c.fullscreen) {
                // Bouton Original | Traduit : rond, déplaçable au doigt, rangé au bord de l'écran pour ne pas gêner les boutons des sites.
                TranslateOrb(
                    translateOn = c.translateOn, active = c.active, running = running, progress = if (c.total > 0) (c.done + c.failed).toFloat() / c.total else 0f,
                    right = st.orbRight, yFrac = st.orbY, onTap = { c.onTranslateButton() },
                    onMoved = { right, y -> graph.settingsStore.update { it.copy(orbRight = right, orbY = y) } },
                )
                val nextUrl = if (c.nearBottom) ChapterNav.next(c.currentUrl) else null
                if (nextUrl != null) {
                    ExtendedFloatingActionButton(
                        onClick = { c.load(nextUrl) }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    ) { Text("Chapitre suivant") }
                }
            } else {
                ReaderOverlay(graph)
            }
            // propositions discrètes : reprendre le chapitre où l'on s'était arrêté ; corriger la traduction dont on regarde l'original
            if (!(c.fullscreen && c.readerBar) && (c.resumeOffer > 0 || c.peekTarget != null || c.peekEmpty != null || c.navBlocked != null)) {
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = if (c.fullscreen) 28.dp else 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.resumeOffer > 0) {
                        LaunchedEffect(c.resumeOffer) { delay(9000); c.dismissResumeOffer() }
                        Pill("Reprendre à ${c.resumeOffer} %", onClick = { c.resumeNow() }, onClose = { c.dismissResumeOffer() })
                    }
                    if (c.peekTarget != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("Corriger cette traduction", onClick = { c.openPeekEdit() })
                        // supprimer d'un geste (petits artefacts pris pour du texte) : l'original est rétabli à cet endroit
                        RoundPill(Icons.Filled.Delete, "Supprimer cette traduction") { c.deletePeek() }
                    }
                    c.peekEmpty?.let { Pill("Ajouter une traduction ici", onClick = { c.openNewEdit() }, onClose = { c.peekEmpty = null }) }
                    c.navBlocked?.let { nb ->
                        if (nb.popup) Pill(nb.reason, onClick = { c.dismissNavBlocked() })
                        else Pill("Redirection bloquée vers ${android.net.Uri.parse(nb.url).host ?: "un autre site"} · Ouvrir", onClick = { c.openBlockedNav() }, onClose = { c.dismissNavBlocked() })
                    }
                }
            }
        }
    }
}

/** Petit bouton rond de la même teinte que [Pill] : une action d'un geste, sans texte. */
@Composable
private fun RoundPill(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit, onClose: (() -> Unit)? = null) {
    Row(
        Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))).clickable { onClick() }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
        if (onClose != null) {
            Spacer(Modifier.width(12.dp))
            Text("✕", modifier = Modifier.clickable { onClose() }, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Interrupteur à deux positions : on voit ce qui est affiché et ce que le toucher va afficher. */
@Composable
private fun TranslationToggle(translated: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    @Composable
    fun Seg(label: String, selected: Boolean) {
        Text(
            label,
            modifier = Modifier.clip(CircleShape)
                .background(if (selected) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)) else SolidColor(Color.Transparent))
                .clickable(enabled = !selected) { onToggle() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    Row(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape).padding(3.dp)) {
        Seg("Original", !translated)
        Seg("Traduit", translated)
    }
}

/** Barre d'adresse compacte : retour, adresse (touchez pour la modifier), favori, plein écran, menu. */
@Composable
private fun AddressBar(graph: AppGraph) {
    val c = graph.controller
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val requester = remember { FocusRequester() }
    val heart = graph.library.isHeart(c.currentUrl)
    val canHeart = c.currentUrl.startsWith("http")
    val seriesUrl = BrowsingLibrary.seriesUrlOf(c.currentUrl)

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { c.back() }, enabled = c.canGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
        Box(
            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(21.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (!editing) {
                Text(
                    if (c.currentUrl.isEmpty()) "Adresse ou recherche" else prettyAddress(c.currentUrl),
                    modifier = Modifier.fillMaxWidth().clickable { field = TextFieldValue(c.currentUrl, TextRange(0, c.currentUrl.length)); editing = true },
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                    color = if (c.currentUrl.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            } else {
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { requester.requestFocus() }
                BasicTextField(
                    value = field, onValueChange = { field = it }, singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { c.load(UrlInput.normalize(field.text)); editing = false; focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { st ->
                        if (st.isFocused) hadFocus = true else if (hadFocus) editing = false
                    },
                )
            }
        }
        IconButton(onClick = { graph.library.toggleHeart(c.currentUrl, c.pageTitle) }, enabled = canHeart) {
            Icon(
                if (heart) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (heart) "Retirer des favoris" else "Ajouter aux favoris",
                tint = if (heart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canHeart) 0.8f else 0.35f),
            )
        }
        IconButton(onClick = { c.enterFullscreen() }) { Icon(AppIcons.Fullscreen, "Plein écran") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus d'actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (c.translateOn && c.active) "Revenir à l'original" else "Traduire cette page") }, onClick = { menu = false; c.onTranslateButton() })
                if (c.translateOn && c.active) DropdownMenuItem(text = { Text("Retraduire la page") }, onClick = { menu = false; c.start(force = true) })
                DropdownMenuItem(text = { Text(if (c.loading) "Arrêter le chargement" else "Actualiser") }, onClick = { menu = false; if (c.loading) c.stopLoading() else c.reload() })
                DropdownMenuItem(text = { Text("Page suivante") }, enabled = c.canGoForward, onClick = { menu = false; c.forward() })
                DropdownMenuItem(text = { Text("Accueil") }, onClick = { menu = false; c.goHome() })
                if (seriesUrl != null) DropdownMenuItem(text = { Text("Page de l'œuvre") }, onClick = { menu = false; c.load(seriesUrl) })
                if (c.currentUrl.startsWith("http")) {
                    val on = c.adsActiveHere()
                    DropdownMenuItem(
                        text = { Text(if (on) "Bloqueur actif ici · ${c.adCountOnPage()} bloqué${if (c.adCountOnPage() > 1) "s" else ""} (désactiver)" else "Bloqueur désactivé ici (réactiver)") },
                        onClick = { menu = false; c.toggleAdsHere() },
                    )
                }
                DropdownMenuItem(text = { Text("Copier l'adresse") }, enabled = c.currentUrl.isNotEmpty(), onClick = {
                    menu = false
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("adresse", c.currentUrl))
                    Toast.makeText(ctx, "Adresse copiée", Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}

/** Plein écran : rien à l'écran que la page. Un appui sur la page ouvre ce menu (quitter, chapitres, original ou traduit) ; il se referme seul. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ReaderOverlay(graph: AppGraph) {
    val c = graph.controller
    val prev = ChapterNav.previous(c.currentUrl)
    val next = ChapterNav.next(c.currentUrl)
    AnimatedVisibility(visible = c.readerBar, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent))).displayCutoutPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { c.exitFullscreen() }) { Icon(AppIcons.FullscreenExit, "Quitter le plein écran", tint = Color.White) }
            Text(c.pageTitle.ifEmpty { prettyAddress(c.currentUrl) }, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text("${c.pageProgress} %", color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelLarge)
        }
    }
    AnimatedVisibility(visible = c.readerBar, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(horizontal = 12.dp).padding(top = 28.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { prev?.let { c.load(it) } }, enabled = prev != null) { Text("‹ Précédent", color = if (prev != null) Color.White else Color(0x66FFFFFF)) }
            TranslationToggle(translated = c.translateOn && c.active) { c.onTranslateButton(); c.showReaderBar(true) }
            TextButton(onClick = { next?.let { c.load(it) } }, enabled = next != null) { Text("Suivant ›", color = if (next != null) Color.White else Color(0x66FFFFFF)) }
        }
    }
    // fin de chapitre : discret bouton centré en bas, même sans le menu
    if (c.nearBottom && !c.readerBar) {
        next?.let { nextUrl ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).background(Color(0xB3000000), CircleShape).clickable { c.load(nextUrl) }.padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Chapitre suivant ›", color = Color.White) }
        }
    }
}
