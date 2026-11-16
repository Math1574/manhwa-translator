package app.manhwa.core

/**
 * Passage à tour de rôle, un seul à la fois, dans l'ordre de lecture : quand plusieurs fils attendent, celui dont la priorité est la plus basse passe d'abord
 * (priorité relue à chaque choix : le lecteur a pu défiler pendant l'attente), puis l'ordre d'arrivée.
 *
 * Un verrou ordinaire laisse passer n'importe lequel des fils qui attendent : quand plusieurs pages attendaient la lecture du texte (une seule à la fois, elle occupe tous les cœurs),
 * celle qui passait en premier n'était pas la première du chapitre, et la traduction commençait plus loin dans l'œuvre.
 */
class OrderedTurn(private val recheckMs: Long = 250L) {
    private class Waiter(val priority: () -> Int, val seq: Long)

    private val lock = Object()
    private val waiters = ArrayList<Waiter>()
    private var busy = false
    private var seq = 0L

    private fun prio(w: Waiter): Int = try { w.priority() } catch (_: Throwable) { Int.MAX_VALUE }

    /** Sous le verrou : celui qui doit passer maintenant (priorité la plus basse, puis ordre d'arrivée). */
    private fun best(): Waiter {
        var b = waiters[0]; var bp = prio(b)
        for (i in 1 until waiters.size) {
            val w = waiters[i]; val p = prio(w)
            if (p < bp || (p == bp && w.seq < b.seq)) { b = w; bp = p }
        }
        return b
    }

    /** Exécute [body] quand c'est le tour de l'appelant ; personne d'autre ne passe pendant ce temps. */
    fun <T> run(priority: () -> Int, body: () -> T): T {
        val me: Waiter
        synchronized(lock) {
            me = Waiter(priority, seq++)
            waiters.add(me)
            try {
                while (busy || best() !== me) lock.wait(recheckMs)
            } catch (e: Throwable) {
                // fil interrompu (page abandonnée) : il quitte la file sans bloquer les autres
                waiters.remove(me); lock.notifyAll()
                throw e
            }
            waiters.remove(me); busy = true
        }
        try {
            return body()
        } finally {
            synchronized(lock) { busy = false; lock.notifyAll() }
        }
    }

    /** Nombre de fils qui attendent leur tour (diagnostic et tests). */
    fun waitingCount(): Int = synchronized(lock) { waiters.size }
}
