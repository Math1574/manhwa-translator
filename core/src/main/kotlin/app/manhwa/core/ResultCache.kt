package app.manhwa.core

import java.io.File
import java.security.MessageDigest

data class PageResult(val imageWidth: Int, val imageHeight: Int, val blocks: List<TranslatedBlock>)

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

data class CacheKey(val imageHash: String, val source: String, val target: String, val engineId: String) {
    val id: String get() = sha256Hex("$imageHash|$source|$target|$engineId".toByteArray())
}

/**
 * Cache LRU en mémoire + persistance disque (un fichier texte par page). Chaque page garde le numéro de version du glossaire avec lequel elle a été traduite
 * (voir [Glossary.revision]) : les termes ajoutés depuis ne concernent que les textes qui les contiennent.
 */
class ResultCache(private val dir: File, private val maxEntries: Int = 64, private val maxDiskBytes: Long = 50L * 1024 * 1024) {
    private class Entry(val result: PageResult, val rev: Long)

    private val memory = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    init { dir.mkdirs() }

    @Synchronized fun get(key: CacheKey): PageResult? = entry(key)?.result

    /** Version du glossaire avec laquelle la page a été traduite ; 0 si on l'ignore (page enregistrée avant ce suivi) ou si la page n'est pas en cache. */
    @Synchronized fun revisionOf(key: CacheKey): Long = entry(key)?.rev ?: 0L

    private fun entry(key: CacheKey): Entry? {
        memory[key.id]?.let { return it }
        val f = File(dir, key.id + ".page")
        if (!f.exists()) return null
        return try {
            decodeEntry(f.readText()).let { (r, rev) -> Entry(r, rev) }.also { memory[key.id] = it; f.setLastModified(System.currentTimeMillis()) }
        } catch (e: Exception) {
            f.delete(); null // fichier corrompu : on le supprime et on recalculera
        }
    }

    @Synchronized fun put(key: CacheKey, result: PageResult, rev: Long = 0L) {
        memory[key.id] = Entry(result, rev)
        val tmp = File(dir, key.id + ".tmp")
        tmp.writeText(encode(result, rev))
        val dst = File(dir, key.id + ".page")
        if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
        evictDisk()
    }

    @Synchronized fun remove(key: CacheKey) {
        memory.remove(key.id)
        File(dir, key.id + ".page").delete()
    }

    @Synchronized fun diskBytes(): Long = dir.listFiles { f -> f.name.endsWith(".page") }?.sumOf { it.length() } ?: 0L

    @Synchronized fun clear() {
        memory.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun evictDisk() {
        val files = dir.listFiles { f -> f.name.endsWith(".page") }?.sortedBy { it.lastModified() }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        while (total > maxDiskBytes && files.size > 1) {
            val f = files.removeAt(0)
            total -= f.length()
            f.delete()
        }
    }

    companion object {
        fun encode(r: PageResult, rev: Long = 0L): String = buildString {
            append("V7\t${r.imageWidth}\t${r.imageHeight}").append(if (rev > 0L) "\t$rev" else "").append('\n')
            for (b in r.blocks) {
                append("B\t${b.id}\t${b.box.left}\t${b.box.top}\t${b.box.right}\t${b.box.bottom}\t${if (b.vertical) 1 else 0}\t${b.lineHeight}\t${b.lineBoxes.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }}\t${b.lineTexts.joinToString("\u001F") { esc(it) }}\t")
                append(esc(b.source)).append('\t').append(esc(b.repaired)).append('\t').append(esc(b.translated)).append('\t').append(b.kind).append('\t').append(b.bubble?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "").append('\t').append(b.zone?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "").append('\n')
            }
        }

        fun decode(s: String): PageResult = decodeEntry(s).first

        /** La page et la version du glossaire avec laquelle elle a été traduite (0 si le fichier date d'avant ce suivi). */
        private fun decodeEntry(s: String): Pair<PageResult, Long> {
            val lines = s.lines().filter { it.isNotEmpty() }
            val h = lines.first().split('\t')
            require((h.size == 3 || h.size == 4) && h[0] == "V7") { "en-tête invalide" }
            val blocks = lines.drop(1).map { l ->
                val p = l.split('\t')
                require(p.size == 16 && p[0] == "B") { "ligne invalide" }
                TranslatedBlock(
                    p[1].toInt(), RectF(p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()),
                    unesc(p[10]), unesc(p[12]), p[6] == "1", p[7].toFloat(),
                    if (p[8].isEmpty()) emptyList() else p[8].split(";").map { q -> q.split(",").map(String::toFloat).let { v -> RectF(v[0], v[1], v[2], v[3]) } },
                    if (p[9].isEmpty()) emptyList() else p[9].split("\u001F").map { unesc(it) },
                    unesc(p[11]),
                    p[13], if (p[14].isEmpty()) null else p[14].split(",").map(String::toFloat).let { v -> RectF(v[0], v[1], v[2], v[3]) },
                    if (p[15].isEmpty()) null else p[15].split(",").map(String::toFloat).let { v -> RectF(v[0], v[1], v[2], v[3]) },
                )
            }
            return PageResult(h[1].toInt(), h[2].toInt(), blocks) to (h.getOrNull(3)?.toLongOrNull() ?: 0L)
        }
    }
}
