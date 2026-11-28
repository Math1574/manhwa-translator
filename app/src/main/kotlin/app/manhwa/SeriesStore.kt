package app.manhwa

import app.manhwa.core.SeriesMemory
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Mémoire de chaque œuvre (personnages, genre du narrateur, confusions de lettres de son lettrage, genre de l'œuvre : voir [SeriesMemory]), gardée sur le téléphone, un fichier par œuvre.
 * Chargée au début d'un chapitre, enregistrée en tâche de fond après chaque page qui l'a changée. En mode Ninja, l'œuvre est lue mais jamais écrite : une copie à part, oubliée à la sortie.
 */
class SeriesMemoryStore(private val dir: File) {
    private val cache = HashMap<String, SeriesMemory>()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "mt-series-memory").apply { isDaemon = true } }

    /** La mémoire de l'œuvre [key] (vide si elle n'a jamais été lue). */
    @Synchronized fun get(key: String): SeriesMemory {
        if (Ninja.on) return read(key)          // copie à part : rien de ce qui est appris en mode Ninja ne rejoint la mémoire gardée
        return cache.getOrPut(key) { read(key) }
    }

    private fun read(key: String): SeriesMemory = try {
        file(key).takeIf { it.exists() }?.let { SeriesMemory.parse(key, it.readText()) } ?: SeriesMemory(key)
    } catch (_: Throwable) { SeriesMemory(key) }

    /** Enregistre la mémoire si elle a changé (tâche de fond ; jamais en mode Ninja, ni pour une copie faite en mode Ninja). */
    fun saveSoon(m: SeriesMemory) {
        if (Ninja.on || !m.dirty) return
        synchronized(this) { if (cache[m.key] !== m) return }
        writer.execute {
            try {
                val text = synchronized(m) { m.dirty = false; m.serialize() }
                dir.mkdirs()
                val f = file(m.key); val tmp = File(f.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
            } catch (_: Throwable) {}
        }
    }

    /** Oublie tout ce qui a été appris sur les œuvres (réglages). */
    @Synchronized fun clearAll() {
        cache.clear()
        try { dir.listFiles()?.forEach { it.delete() } } catch (_: Throwable) {}
    }

    private fun file(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, h.take(20) + ".txt")
    }
}
