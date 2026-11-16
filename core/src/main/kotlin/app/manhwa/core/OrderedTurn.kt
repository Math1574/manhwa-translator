package app.manhwa.core

/**
 * Passage à tour de rôle, un seul à la fois, dans l'ordre de lecture : quand plusieurs fils attendent, celui dont la priorité est la plus basse passe d'abord
 * (priorité relue à chaque choix : le lecteur a pu défiler pendant l'attente), puis l'ordre d'arrivée.
 *
 * Un verrou ordinaire laisse passer n'importe lequel des fils qui attendent : quand plusieurs pages attendaient la lecture du texte (une seule à la fois, elle occupe tous les cœurs),
 * celle qui passait en premier n'était pas la première du chapitre, et la traduction commençait plus loin dans l'œuvre.
 */
class OrderedTurn(private val recheckMs: Long = 250L, private val holdMaxMs: Long = 0L) {
    private class Waiter(val priority: () -> Int, val seq: Long)

    /**
     * Rend l'identifiant d'un fil plus proche du lecteur que la priorité donnée, encore en route vers ce passage (page en téléchargement) et qui n'est pas dans [ignore], ou null : le premier
     * de la file l'attend alors un instant ([holdMaxMs] au plus par fil attendu) au lieu de passer devant. Une page dont le téléchargement finit quelques centaines de millisecondes plus
     * tard ne doit pas attendre toute la lecture du texte d'une page plus loin (5 à 10 s) : c'est le début de la lecture. Un fil dont l'attente a été abandonnée n'est plus attendu
     * (pendant 5 minutes) : un téléchargement bloqué ne retarde pas chaque page de la même durée.
     */
    @Volatile var holdBack: ((priority: Int, ignore: Set<String>) -> String?)? = null

    private val lock = Object()
    private val waiters = ArrayList<Waiter>()
    private val gaveUp = HashMap<String, Long>()
    private var busy = false
    private var seq = 0L

    private companion object { const val GIVE_UP_MEMORY_MS = 5 * 60 * 1000L }

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
            var holdId: String? = null; var holdSince = 0L
            try {
                while (true) {
                    if (busy || best() !== me) { lock.wait(recheckMs); continue }
                    val hb = holdBack
                    if (hb == null || holdMaxMs <= 0L) break
                    val now = System.currentTimeMillis()
                    gaveUp.values.removeIf { now - it > GIVE_UP_MEMORY_MS }
                    val coming = runCatching { hb(prio(me), gaveUp.keys.toSet()) }.getOrNull() ?: break
                    if (coming != holdId) { holdId = coming; holdSince = now }
                    if (now - holdSince >= holdMaxMs) { gaveUp[coming] = now; holdId = null; continue }
                    lock.wait(minOf(recheckMs, holdMaxMs - (now - holdSince)).coerceAtLeast(1L))
                }
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

    /** Oublie les attentes abandonnées : à chaque nouvelle page du navigateur les identifiants repartent de zéro, un ancien abandon ne doit pas valoir pour une autre page. */
    fun forgetGiveUps() { synchronized(lock) { gaveUp.clear() } }
}
