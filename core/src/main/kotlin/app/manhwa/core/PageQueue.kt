package app.manhwa.core

import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean

data class PageJob(val id: String, val priority: Int = 0, val work: (cancelled: () -> Boolean) -> PageOutcome)

interface QueueListener {
    fun onStarted(id: String) {}
    fun onFinished(id: String, outcome: PageOutcome) {}
    fun onCancelled(id: String) {}
}

/**
 * File d'attente séquentielle (un seul traitement à la fois pour ménager la mémoire).
 * - dédoublonnage par id ; priorité aux pages visibles (priorité basse = traité d'abord) ;
 * - nombre d'éléments en attente limité (maxPending) ;
 * - une erreur sur une page n'interrompt pas les suivantes ;
 * - annulation d'une page ou de toutes.
 */
class PageQueue(maxPending: Int = 8, private val listener: QueueListener) {
    /** Modifiable à chaud (réglage « pages prétraitées »). */
    @Volatile var maxPending: Int = maxPending
    private data class Item(val job: PageJob, val seq: Long)

    private val lock = Object()
    private val pending = PriorityQueue<Item>(compareBy<Item>({ it.job.priority }, { it.seq }))
    private val known = HashSet<String>()
    private val cancelled = HashSet<String>()
    private var seq = 0L
    private var current: String? = null
    private var stopped = false
    private var idle = true

    private val worker = Thread({ loop() }, "page-queue").apply { isDaemon = true; start() }

    /** @return false si le job est déjà connu ou si la file est pleine. */
    fun submit(job: PageJob): Boolean = synchronized(lock) {
        if (job.id in known || pending.size >= maxPending) return false
        known.add(job.id)
        pending.add(Item(job, seq++))
        lock.notifyAll()
        true
    }

    fun cancel(id: String) = synchronized(lock) {
        if (current == id) cancelled.add(id)
        else if (pending.removeIf { it.job.id == id }) { known.remove(id); listener.onCancelled(id) }
    }

    fun cancelAll() = synchronized(lock) {
        pending.toList().forEach { known.remove(it.job.id); listener.onCancelled(it.job.id) }
        pending.clear()
        current?.let { cancelled.add(it) }
    }

    fun shutdown() { synchronized(lock) { stopped = true; lock.notifyAll() } }

    /** Attend que la file soit vide (utile pour les tests). */
    fun awaitIdle(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (!(idle && pending.isEmpty())) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) return false
                lock.wait(left)
            }
        }
        return true
    }

    private fun loop() {
        while (true) {
            val item: Item
            synchronized(lock) {
                while (pending.isEmpty() && !stopped) { idle = true; lock.notifyAll(); lock.wait() }
                if (stopped) return
                item = pending.poll()
                current = item.job.id
                idle = false
            }
            listener.onStarted(item.job.id)
            val flag = AtomicBoolean(false)
            val outcome: PageOutcome = try {
                item.job.work { synchronized(lock) { item.job.id in cancelled }.also { flag.set(it) } }
            } catch (e: Throwable) {
                PageOutcome.Failed("queue", e.message ?: e.javaClass.simpleName)
            }
            val wasCancelled = synchronized(lock) { item.job.id in cancelled }
            if (wasCancelled) listener.onCancelled(item.job.id) else listener.onFinished(item.job.id, outcome)
            synchronized(lock) {
                known.remove(item.job.id); cancelled.remove(item.job.id); current = null
                if (pending.isEmpty()) { idle = true }
                lock.notifyAll()
            }
        }
    }
}
