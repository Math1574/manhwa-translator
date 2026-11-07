package app.manhwa.core

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/** Une action du bloqueur, pour le journal de diagnostic (« qu'est-ce qui a été bloqué, par quelle règle »). */
class BlockEntry(val time: Long, val kind: String, val url: String, val rule: String?, val page: String?)

/**
 * Journal du bloqueur : les dernières actions (requête bloquée, pop-up refusé, redirection refusée, superposition retirée…) et des compteurs par type.
 * Sûr entre fils : les requêtes sont filtrées sur un fil de la WebView, l'interface les lit sur le fil principal.
 */
class BlockLog(private val capacity: Int = 400) {
    private val entries = ArrayDeque<BlockEntry>()
    private val counts = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    /** Nombre d'actions depuis l'ouverture de la page en cours. */
    val pageCount = AtomicInteger(0)

    fun add(kind: String, url: String, rule: String?, page: String?) {
        counts.getOrPut(kind) { AtomicInteger(0) }.incrementAndGet()
        pageCount.incrementAndGet()
        synchronized(entries) {
            if (entries.size >= capacity) entries.pollFirst()
            entries.addLast(BlockEntry(System.currentTimeMillis(), kind, url, rule, page))
        }
    }

    fun newPage() { pageCount.set(0) }
    fun count(kind: String): Int = counts[kind]?.get() ?: 0
    fun total(): Int = counts.values.sumOf { it.get() }
    fun recent(): List<BlockEntry> = synchronized(entries) { ArrayList(entries).also { it.reverse() } }
    fun clear() { synchronized(entries) { entries.clear() }; counts.clear(); pageCount.set(0) }

    /** Texte exportable (partage depuis les réglages) : une ligne par action, la plus récente d'abord. */
    fun export(): String = buildString {
        append("Journal du bloqueur — ").append(total()).append(" action(s)\n")
        for ((k, v) in counts.entries.sortedBy { it.key }) append("  ").append(k).append(" : ").append(v.get()).append('\n')
        append('\n')
        for (e in recent()) {
            append(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date(e.time))).append("  ").append(e.kind).append("  ").append(e.url)
            if (e.rule != null) append("  ←  ").append(e.rule)
            if (e.page != null) append("  (page : ").append(e.page).append(')')
            append('\n')
        }
    }
}
