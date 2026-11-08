package app.manhwa.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Une page à traiter. [priority] : valeur figée à la soumission (priorité basse = traitée d'abord) ;
 * [priorityNow], si elle est fournie, est relue au moment de choisir la page suivante : la file traite alors ce que le lecteur regarde à cet instant,
 * pas ce qui était proche quand l'image a été repérée.
 */
data class PageJob(val id: String, val priority: Int = 0, val priorityNow: (() -> Int)? = null, val work: (cancelled: () -> Boolean) -> PageOutcome)

interface QueueListener {
    fun onStarted(id: String) {}
    fun onFinished(id: String, outcome: PageOutcome) {}
    fun onCancelled(id: String) {}
}

/**
 * File d'attente des pages.
 * - dédoublonnage par id ; priorité aux pages les plus proches de l'écran (priorité basse = traité d'abord, relue à chaque choix si elle est dynamique) ;
 * - [workers] traitements en parallèle (1 par défaut) : pendant qu'une page attend sa traduction, la suivante est déjà téléchargée et lue ;
 * - nombre d'éléments en attente limité (maxPending) ;
 * - une erreur sur une page n'interrompt pas les suivantes ;
 * - annulation d'une page ou de toutes.
 */
class PageQueue(maxPending: Int = 8, private val listener: QueueListener, private val workers: Int = 1) {
    /** Modifiable à chaud (réglage « pages prétraitées »). */
    @Volatile var maxPending: Int = maxPending
    private data class Item(val job: PageJob, val seq: Long)

    private val lock = Object()
    private val pending = ArrayList<Item>()
    private val known = HashSet<String>()
    private val running = HashSet<String>()
    private val cancelled = HashSet<String>()
    /** Pages dont le fil de traitement a été abandonné parce qu'il était bloqué : le fil, s'il finit par rendre la main, s'arrête sans rien signaler. */
    private val abandoned = HashSet<String>()
    private val owner = HashMap<String, Thread>()
    private var seq = 0L
    private var stopped = false

    /** Fils de traitement vivants : un fil qui s'arrêterait sur une erreur imprévue serait remplacé (voir [ensureWorkers]). */
    private val alive = java.util.concurrent.atomic.AtomicInteger(0)
    private val wanted = workers.coerceAtLeast(1)

    init { repeat(wanted) { startWorker() } }

    private fun startWorker() {
        alive.incrementAndGet()
        Thread({ try { loop() } finally { alive.decrementAndGet() } }, "page-queue-${seq++}").apply { isDaemon = true; start() }
    }

    /** Relance les fils manquants (un fil arrêté par une erreur imprévue). @return le nombre de fils relancés. */
    fun ensureWorkers(): Int = synchronized(lock) {
        var started = 0
        while (alive.get() < wanted && !stopped) { startWorker(); started++ }
        started
    }

    class Snapshot(val pending: Int, val running: Int, val alive: Int)

    fun snapshot(): Snapshot = synchronized(lock) { Snapshot(pending.size, running.size, alive.get()) }

    /** @return false si le job est déjà connu ou si la file est pleine. */
    fun submit(job: PageJob): Boolean = synchronized(lock) {
        if (job.id in known || pending.size >= maxPending) return false
        known.add(job.id)
        pending.add(Item(job, seq++))
        lock.notifyAll()
        true
    }

    fun cancel(id: String) = synchronized(lock) {
        if (id in running) cancelled.add(id)
        else if (pending.removeIf { it.job.id == id }) { known.remove(id); listener.onCancelled(id) }
    }

    fun cancelAll() = synchronized(lock) {
        pending.toList().forEach { known.remove(it.job.id); listener.onCancelled(it.job.id) }
        pending.clear()
        running.forEach { cancelled.add(it) }
    }

    /**
     * Abandonne une page dont le traitement est bloqué : le fil est interrompu, la page est signalée comme annulée et un fil neuf prend sa place,
     * si bien que les pages suivantes continuent d'avancer. Sans effet si la page n'est pas en cours.
     */
    fun abandon(id: String) {
        val stuck: Thread?
        synchronized(lock) {
            if (id !in running) return
            abandoned.add(id); cancelled.add(id); running.remove(id); known.remove(id)
            stuck = owner.remove(id)
            startWorker()
            lock.notifyAll()
        }
        try { stuck?.interrupt() } catch (_: Throwable) {}
        listener.onCancelled(id)
    }

    fun shutdown() { synchronized(lock) { stopped = true; lock.notifyAll() } }

    /** Attend que la file soit vide et qu'aucune page ne soit en cours (utile pour les tests). */
    fun awaitIdle(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (!(running.isEmpty() && pending.isEmpty())) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) return false
                lock.wait(left)
            }
        }
        return true
    }

    /** Pages en cours de traitement (diagnostic : détection d'un blocage). */
    fun runningIds(): List<String> = synchronized(lock) { running.toList() }

    private fun effective(it: Item): Int = try { it.job.priorityNow?.invoke() ?: it.job.priority } catch (_: Throwable) { it.job.priority }

    /** La page à traiter : priorité la plus basse (relue maintenant si elle est dynamique), puis ordre d'arrivée. Appelée sous le verrou. */
    private fun takeNext(): Item {
        var best = pending[0]; var bestP = effective(best)
        for (i in 1 until pending.size) {
            val it = pending[i]; val p = effective(it)
            if (p < bestP || (p == bestP && it.seq < best.seq)) { best = it; bestP = p }
        }
        pending.remove(best)
        return best
    }

    private fun loop() {
        while (true) {
            val item: Item
            synchronized(lock) {
                while (pending.isEmpty() && !stopped) lock.wait()
                if (stopped) return
                item = takeNext()
                running.add(item.job.id)
                owner[item.job.id] = Thread.currentThread()
            }
            // une erreur dans un rappel (ou imprévue) ne doit jamais arrêter le fil : sans lui, les pages en attente ne seraient plus jamais traitées
            safely { listener.onStarted(item.job.id) }
            val flag = AtomicBoolean(false)
            val outcome: PageOutcome = try {
                item.job.work { synchronized(lock) { item.job.id in cancelled }.also { flag.set(it) } }
            } catch (e: Throwable) {
                PageOutcome.Failed("queue", e.message ?: e.javaClass.simpleName)
            }
            // un fil abandonné (voir abandon) a déjà été remplacé et la page signalée : il s'arrête là
            val wasAbandoned = synchronized(lock) { abandoned.remove(item.job.id).also { if (it) cancelled.remove(item.job.id) } }
            if (wasAbandoned) { Thread.interrupted(); return }
            val wasCancelled = synchronized(lock) { item.job.id in cancelled }
            safely { if (wasCancelled) listener.onCancelled(item.job.id) else listener.onFinished(item.job.id, outcome) }
            synchronized(lock) {
                known.remove(item.job.id); cancelled.remove(item.job.id); running.remove(item.job.id); owner.remove(item.job.id)
                lock.notifyAll()
            }
        }
    }

    private inline fun safely(f: () -> Unit) { try { f() } catch (_: Throwable) {} }
}
