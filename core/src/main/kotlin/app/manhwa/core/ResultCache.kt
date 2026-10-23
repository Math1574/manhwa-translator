package app.manhwa.core

import java.io.File
import java.security.MessageDigest

data class PageResult(val imageWidth: Int, val imageHeight: Int, val blocks: List<TranslatedBlock>)

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

data class CacheKey(val imageHash: String, val source: String, val target: String, val engineId: String) {
    val id: String get() = sha256Hex("$imageHash|$source|$target|$engineId".toByteArray())
}

/** Cache LRU en mémoire + persistance disque (un fichier texte par page). */
class ResultCache(private val dir: File, private val maxEntries: Int = 64, private val maxDiskBytes: Long = 50L * 1024 * 1024) {
    private val memory = object : LinkedHashMap<String, PageResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageResult>?) = size > maxEntries
    }

    init { dir.mkdirs() }

    @Synchronized fun get(key: CacheKey): PageResult? {
        memory[key.id]?.let { return it }
        val f = File(dir, key.id + ".page")
        if (!f.exists()) return null
        return try {
            decode(f.readText()).also { memory[key.id] = it; f.setLastModified(System.currentTimeMillis()) }
        } catch (e: Exception) {
            f.delete(); null // fichier corrompu : on le supprime et on recalculera
        }
    }

    @Synchronized fun put(key: CacheKey, result: PageResult) {
        memory[key.id] = result
        val tmp = File(dir, key.id + ".tmp")
        tmp.writeText(encode(result))
        val dst = File(dir, key.id + ".page")
        if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
        evictDisk()
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
        fun encode(r: PageResult): String = buildString {
            append("V3\t${r.imageWidth}\t${r.imageHeight}\n")
            for (b in r.blocks) {
                append("B\t${b.id}\t${b.box.left}\t${b.box.top}\t${b.box.right}\t${b.box.bottom}\t${if (b.vertical) 1 else 0}\t${b.lineHeight}\t${b.lineBoxes.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }}\t")
                append(esc(b.source)).append('\t').append(esc(b.translated)).append('\n')
            }
        }

        fun decode(s: String): PageResult {
            val lines = s.lines().filter { it.isNotEmpty() }
            val h = lines.first().split('\t')
            require(h.size == 3 && h[0] == "V3") { "en-tête invalide" }
            val blocks = lines.drop(1).map { l ->
                val p = l.split('\t')
                require(p.size == 11 && p[0] == "B") { "ligne invalide" }
                TranslatedBlock(
                    p[1].toInt(), RectF(p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()),
                    unesc(p[9]), unesc(p[10]), p[6] == "1", p[7].toFloat(),
                    if (p[8].isEmpty()) emptyList() else p[8].split(";").map { q -> q.split(",").map(String::toFloat).let { v -> RectF(v[0], v[1], v[2], v[3]) } },
                )
            }
            return PageResult(h[1].toInt(), h[2].toInt(), blocks)
        }
    }
}
