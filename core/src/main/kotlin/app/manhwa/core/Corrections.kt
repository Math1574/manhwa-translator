package app.manhwa.core

/**
 * Centre de corrections : ce que l'utilisateur a corrigé à la main, présenté par chapitre pour qu'il puisse le revoir et le défaire.
 * Les corrections d'un texte sont enregistrées par image (voir [BlockEdit]) ; l'image elle-même ne dit pas d'où elle vient, d'où [EditOrigin].
 */

/** D'où vient une correction : la page (le chapitre) qui était ouverte quand elle a été faite. */
data class EditOrigin(val url: String, val title: String, val at: Long)

/** Une image corrigée : sa clé (empreinte de ses octets), le moment de sa dernière correction et ses corrections. */
class EditedImage(val key: String, val at: Long, val edits: List<BlockEdit>)

/** Une correction décrite pour l'utilisateur. */
data class CorrectionItem(
    val imageKey: String,
    val editId: String,
    /** Ce qui a été fait, en quelques mots (« Traduction écrite », « Traduction supprimée »…). */
    val label: String,
    /** Le texte concerné : celui qui a été lu sur l'image, et ce qu'on lui fait dire quand une traduction a été écrite. */
    val detail: String,
    val edit: BlockEdit,
)

/** Les corrections d'un chapitre. [url] est null quand on ignore d'où elles viennent (corrections faites avant que l'application ne le retienne). */
data class ChapterCorrections(val url: String?, val series: String, val chapter: String, val lastAt: Long, val items: List<CorrectionItem>) {
    /** Titre affiché : « Œuvre — Chapitre 12 », ou ce que l'on sait. */
    val title: String
        get() = when {
            url == null -> "Chapitre inconnu"
            series.isEmpty() || chapter.contains(series, ignoreCase = true) -> chapter
            else -> "$series — $chapter"
        }
}

object Corrections {
    /** Texte raccourci sur une ligne. */
    private fun clip(s: String, max: Int = 110): String {
        val one = s.replace(Regex("\\s+"), " ").trim()
        return if (one.length <= max) one else one.take(max - 1).trimEnd() + "…"
    }

    /** Ce que fait cette correction : (ce qui a été fait, le texte concerné), ou null si elle ne change rien. */
    fun describe(e: BlockEdit): Pair<String, String>? {
        if (e.isNeutral) return null
        val parts = ArrayList<String>()
        val tr = e.translation?.takeIf { it.isNotBlank() }
        when {
            e.hidden -> parts += "traduction supprimée"
            e.kind == EditKind.MANUAL -> parts += if (tr == null) "zone ajoutée" else "texte ajouté à la main"
            else -> {
                if (tr != null) parts += "traduction écrite"
                if (e.source != null) parts += "texte d'origine corrigé"
            }
        }
        if (e.dx != 0f || e.dy != 0f) parts += "position changée"
        if (e.scale != 1f) parts += "taille changée"
        if (e.fontId != null) parts += "police changée"
        if (parts.isEmpty()) parts += "effacement précis"
        val label = parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
        val seen = clip(if (e.kind == EditKind.MANUAL) (e.source ?: "") else e.matchSource)
        val detail = buildString {
            append(if (seen.isNotEmpty()) "« $seen »" else "Texte sans lecture enregistrée")
            if (!e.hidden && tr != null) append("\n→ ").append(clip(tr))
            if (e.kind == EditKind.ADJUST && !e.hidden && e.source != null && clip(e.source) != clip(e.matchSource)) append("\nLu comme : « ").append(clip(e.source)).append(" »")
        }
        return label to detail
    }

    /** Position verticale d'une correction dans son image (pour les ranger dans l'ordre de lecture). */
    private fun top(e: BlockEdit): Float = e.match?.top ?: e.region?.bounds?.top ?: 0f

    /**
     * Les corrections rangées par chapitre : le plus récent d'abord, les corrections d'origine inconnue à la fin. Dans un chapitre, les images corrigées en dernier viennent en tête,
     * et les corrections d'une image suivent l'ordre de la page.
     */
    fun byChapter(images: List<EditedImage>, origins: Map<String, EditOrigin>): List<ChapterCorrections> {
        val groups = LinkedHashMap<String, MutableList<EditedImage>>()
        for (img in images.sortedByDescending { it.at }) {
            val o = origins[img.key]
            val k = if (o == null) "" else (BrowsingLibrary.normalize(o.url) ?: o.url)
            groups.getOrPut(k) { ArrayList() }.add(img)
        }
        val out = ArrayList<ChapterCorrections>()
        for ((k, imgs) in groups) {
            // seules comptent les images qui ont au moins une correction à montrer
            val parts = imgs.map { img -> img to img.edits.sortedBy { top(it) }.mapNotNull { e -> describe(e)?.let { (label, detail) -> CorrectionItem(img.key, e.id, label, detail, e) } } }
                .filter { it.second.isNotEmpty() }
            if (parts.isEmpty()) continue
            val o = if (k.isEmpty()) null else origins[parts.first().first.key]
            val chapter = if (o == null) "" else (BrowsingLibrary.chapterLabel(o.url, o.title) ?: o.title.trim().ifEmpty { o.url.substringAfter("://", o.url).take(60) })
            out.add(ChapterCorrections(o?.url, if (o == null) "" else (SeriesHint.fromUrl(o.url) ?: ""), chapter, parts.maxOf { it.first.at }, parts.flatMap { it.second }))
        }
        return out.sortedWith(compareBy<ChapterCorrections> { it.url == null }.thenByDescending { it.lastAt })
    }

    /**
     * Texte dont la traduction retenue ([Glossary.correctionFor]) s'en va avec cette correction quand on la défait : une traduction écrite à la main est aussi reprise partout où le même
     * texte est lu. On n'oublie cette traduction retenue que si c'est bien celle de cette correction et qu'aucune autre correction restante ([others]) ne la porte. Null sinon.
     */
    fun retainedTextToDrop(edit: BlockEdit, others: Collection<BlockEdit>, g: Glossary): String? {
        val tr = edit.translation?.takeIf { it.isNotBlank() } ?: return null
        if (edit.kind != EditKind.ADJUST || edit.matchSource.isBlank()) return null
        if (g.correctionFor(edit.matchSource) != tr) return null
        val key = edit.matchSource.filter { !it.isWhitespace() }
        if (others.any { o -> o.kind == EditKind.ADJUST && o.translation == tr && o.matchSource.filter { !it.isWhitespace() } == key }) return null
        return edit.matchSource
    }

    /**
     * Traductions retenues qu'aucune correction listée ne porte : celles dont la correction a été rétablie ou oubliée depuis. [edits] : toutes les corrections encore enregistrées.
     */
    fun orphanRetained(edits: Collection<BlockEdit>, g: Glossary): List<TextCorrection> {
        val carried = edits.filter { it.kind == EditKind.ADJUST && !it.translation.isNullOrBlank() && it.matchSource.isNotBlank() }
            .map { it.matchSource.filter { c -> !c.isWhitespace() } to it.translation }.toSet()
        return g.corrections().filter { c -> (c.source.filter { !it.isWhitespace() } to c.translation) !in carried }
    }
}

/** Enregistrement texte de l'origine des corrections (un fichier à part : le format des corrections reste inchangé). */
object OriginsCodec {
    fun serialize(origins: Map<String, EditOrigin>): String = buildString {
        for ((k, o) in origins) append("O\t").append(k).append('\t').append(o.at).append('\t').append(esc(o.url)).append('\t').append(esc(o.title)).append('\n')
    }

    /** Relit un fichier enregistré ; une ligne abîmée est sautée. */
    fun parse(data: String): LinkedHashMap<String, EditOrigin> {
        val out = LinkedHashMap<String, EditOrigin>()
        for (line in data.lines()) {
            val p = line.split('\t')
            if (p.size < 5 || p[0] != "O") continue
            val at = p[2].toLongOrNull() ?: continue
            out[p[1]] = EditOrigin(unesc(p[3]), unesc(p[4]), at)
        }
        return out
    }
}
