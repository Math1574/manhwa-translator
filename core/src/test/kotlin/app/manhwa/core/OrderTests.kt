package app.manhwa.core

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Ordre de la traduction : toujours du début vers la fin (lecture du texte, moteur, file des pages), sans sauter une page ni commencer plus loin. */
fun translationOrderTests() {
    val done = PageOutcome.NoText(1, 1)

    // ---------- rang des images ----------
    T.test("ordre de lecture : le rang de l'image départage les images dont la position relevée au chargement est la même (images du dessus pas encore chargées)") {
        // la deuxième image du chapitre se charge avant la première : toutes deux relevées à la hauteur 0 (la première n'a pas encore de hauteur)
        val first = ReadingOrder.priority(0.0, 900.0, 0.0, 800.0, index = 3)
        val second = ReadingOrder.priority(0.0, 900.0, 0.0, 800.0, index = 4)
        val third = ReadingOrder.priority(900.0, 900.0, 0.0, 800.0, index = 5)
        T.ok(first < second && second < third, "du haut vers le bas malgré les positions identiques : $first $second $third")
        // sans rang : comportement d'avant (position)
        T.ok(ReadingOrder.priority(0.0, 900.0, 0.0, 800.0) == ReadingOrder.priority(0.0, 900.0, 0.0, 800.0, index = -1), "rang négatif ignoré")
        // les catégories ne changent pas : visible, puis dessous (la plus proche d'abord), puis dessus (la plus proche d'abord)
        val seen = ReadingOrder.priority(100.0, 700.0, 50.0, 800.0, index = 40)
        val belowNear = ReadingOrder.priority(2000.0, 900.0, 50.0, 800.0, index = 41)
        val belowFar = ReadingOrder.priority(6000.0, 900.0, 50.0, 800.0, index = 47)
        val aboveNear = ReadingOrder.priority(-900.0, 800.0, 3000.0, 800.0, index = 38)
        val aboveFar = ReadingOrder.priority(-5000.0, 800.0, 3000.0, 800.0, index = 30)
        T.ok(seen < belowNear && belowNear < belowFar, "visible puis dessous : $seen $belowNear $belowFar")
        T.ok(ReadingOrder.priority(-900.0, 800.0, 3000.0, 800.0, index = 38) < ReadingOrder.priority(-5000.0, 800.0, 3000.0, 800.0, index = 30), "dessus : la plus proche d'abord")
        T.ok(belowFar < aboveNear && aboveNear < aboveFar, "dessous avant dessus : $belowFar $aboveNear $aboveFar")
    }

    // ---------- passage à tour de rôle ----------
    T.test("passage à tour de rôle : les fils qui attendent passent dans l'ordre de lecture, pas dans l'ordre d'arrivée, et un seul à la fois") {
        val turn = OrderedTurn(recheckMs = 20)
        val gate = CountDownLatch(1)
        val order = CopyOnWriteArrayList<Int>()
        val inside = AtomicInteger(0); val maxInside = AtomicInteger(0)
        // un premier fil occupe le passage pendant que les autres arrivent dans le désordre (3, 5, 2, 4, 1)
        val holder = Thread { turn.run({ 0 }) { gate.await(3, TimeUnit.SECONDS) } }.also { it.start() }
        Thread.sleep(60)
        val ths = listOf(3, 5, 2, 4, 1).map { p ->
            Thread {
                turn.run({ p }) {
                    val n = inside.incrementAndGet(); maxInside.updateAndGet { m -> maxOf(m, n) }
                    order.add(p); Thread.sleep(15); inside.decrementAndGet()
                }
            }.also { it.start(); Thread.sleep(25) }
        }
        T.ok(turn.waitingCount() == 5, "5 fils attendent : ${turn.waitingCount()}")
        gate.countDown(); ths.forEach { it.join(4000) }; holder.join(2000)
        T.eq(listOf(1, 2, 3, 4, 5), order.toList(), "ordre de passage")
        T.eq(1, maxInside.get(), "jamais deux à la fois")
    }
    T.test("passage à tour de rôle : la priorité est relue à chaque choix (le lecteur a défilé pendant l'attente) ; un fil interrompu quitte la file sans bloquer les autres") {
        val turn = OrderedTurn(recheckMs = 20)
        val gate = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val prioA = java.util.concurrent.atomic.AtomicInteger(1); val prioB = java.util.concurrent.atomic.AtomicInteger(2)
        val holder = Thread { turn.run({ 0 }) { gate.await(3, TimeUnit.SECONDS) } }.also { it.start() }
        Thread.sleep(60)
        val a = Thread { turn.run({ prioA.get() }) { order.add("A") } }.also { it.start() }
        val b = Thread { turn.run({ prioB.get() }) { order.add("B") } }.also { it.start() }
        val victim = Thread { try { turn.run({ -5 }) { order.add("X") } } catch (_: InterruptedException) { order.add("interrompu") } }.also { it.start() }
        Thread.sleep(60)
        victim.interrupt(); victim.join(2000)
        prioA.set(9)           // le lecteur a défilé : B devient plus proche que A
        Thread.sleep(80)
        gate.countDown(); a.join(4000); b.join(4000); holder.join(2000)
        T.eq(listOf("interrompu", "B", "A"), order.toList(), "ordre après défilement et interruption")
    }

    // ---------- file des pages ----------
    T.test("file : une page plus urgente prend la place de la moins urgente quand la file est pleine (celle-ci est rendue), une page moins urgente est refusée") {
        val displaced = CopyOnWriteArrayList<String>()
        val q = PageQueue(2, object : QueueListener { override fun onDisplaced(id: String) { displaced.add(id) } })
        val gate = CountDownLatch(1)
        q.maxExpress = 0
        q.submit(PageJob("run", 0) { gate.await(3, TimeUnit.SECONDS); done }); Thread.sleep(60)
        T.ok(q.submit(PageJob("p5", 5) { done }) && q.submit(PageJob("p7", 7) { done }), "deux pages en attente")
        T.ok(!q.submit(PageJob("p9", 9) { done }), "moins urgente que toutes : refusée")
        T.ok(displaced.isEmpty(), "rien rendu : $displaced")
        T.ok(q.submit(PageJob("p1", 1) { done }), "plus urgente : acceptée")
        T.eq(listOf("p7"), displaced.toList(), "la moins urgente est rendue")
        T.ok(q.submit(PageJob("p7", 7) { done }) == false, "la page rendue est refusée de nouveau tant que les deux autres sont plus urgentes")
        gate.countDown(); T.ok(q.awaitIdle(3000), "file non vidée"); q.shutdown()
    }
    T.test("file : une page arrivée en retard, plus urgente que toutes celles en cours, démarre tout de suite sur un fil de renfort ; deux renforts au plus ; ceux-ci s'arrêtent ensuite") {
        val started = CopyOnWriteArrayList<String>()
        val l = object : QueueListener { override fun onStarted(id: String) { started.add(id) } }
        val q = PageQueue(8, l, workers = 2)
        val gate = CountDownLatch(1)
        // les deux fils ordinaires sont pris par les pages 5 et 6
        q.submit(PageJob("p5", 5) { gate.await(5, TimeUnit.SECONDS); done }); q.submit(PageJob("p6", 6) { gate.await(5, TimeUnit.SECONDS); done })
        Thread.sleep(100)
        T.eq(setOf("p5", "p6"), started.toSet(), "pages en cours")
        // la page 2 arrive en retard : elle ne doit pas attendre la fin de 5 ou 6
        T.ok(q.submit(PageJob("p2", 2) { done }), "p2 acceptée")
        Thread.sleep(250)
        T.ok("p2" in started, "p2 démarre sans attendre : $started")
        // une page moins urgente que celles en cours attend un fil ordinaire
        T.ok(q.submit(PageJob("p9", 9) { gate.await(5, TimeUnit.SECONDS); done }), "p9 acceptée")
        Thread.sleep(250)
        T.ok("p9" !in started, "p9 (moins urgente) attend : $started")
        // renforts : 2 au plus à la fois
        val q2 = PageQueue(8, l, workers = 1)
        val g2 = CountDownLatch(1)
        started.clear()
        q2.submit(PageJob("r9", 9) { g2.await(5, TimeUnit.SECONDS); done }); Thread.sleep(100)
        for (k in 1..4) q2.submit(PageJob("e$k", k) { g2.await(5, TimeUnit.SECONDS); done })
        Thread.sleep(300)
        val extra = started.count { it.startsWith("e") }
        T.ok(extra == 2, "deux renforts au plus (en plus du fil ordinaire) : $started")
        g2.countDown(); T.ok(q2.awaitIdle(4000), "q2 non vidée : $started")
        T.ok(started.size == 5, "toutes les pages sont traitées une fois : $started")
        val snap = q2.snapshot()
        T.ok(snap.alive == 1 && snap.running == 0, "un seul fil ordinaire vivant, les renforts se sont arrêtés (${snap.alive}, ${snap.running})")
        gate.countDown(); T.ok(q.awaitIdle(4000), "q non vidée")
        T.ok(q.snapshot().alive == 2, "deux fils ordinaires")
        q.shutdown(); q2.shutdown()
    }
    T.test("file : sans renfort autorisé (téléphone chaud), une page en retard attend un fil libre") {
        val started = CopyOnWriteArrayList<String>()
        val q = PageQueue(8, object : QueueListener { override fun onStarted(id: String) { started.add(id) } }, workers = 1)
        q.maxExpress = 0
        val gate = CountDownLatch(1)
        q.submit(PageJob("p6", 6) { gate.await(5, TimeUnit.SECONDS); done }); Thread.sleep(80)
        q.submit(PageJob("p1", 1) { done }); Thread.sleep(200)
        T.ok("p1" !in started, "pas de renfort : p1 attend : $started")
        gate.countDown(); T.ok(q.awaitIdle(3000), "file non vidée"); T.ok("p1" in started, "p1 traitée ensuite"); q.shutdown()
    }

    // ---------- moteur de traduction ----------
    fun recordingEngine(calls: MutableList<List<String>>, ms: Long = 80) = object : TranslationEngine {
        override fun translate(texts: List<String>, source: String, target: String): List<String> = translateWithGlossary(texts, source, target, emptyList())
        override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> {
            calls.add(texts); Thread.sleep(ms); return texts.map { it.uppercase() }
        }
    }
    T.test("moteur : les pages sont traduites dans l'ordre de lecture (priorité relue à chaque choix), pas dans l'ordre d'arrivée") {
        val calls = Collections.synchronizedList(ArrayList<List<String>>())
        val b = TranslationBatcher(recordingEngine(calls, 250), maxPages = 1)
        // la page 0 occupe le moteur ; 3, 5, 2, 4 arrivent dans le désordre pendant ce temps
        val prio = ConcurrentHashMap<Int, Int>()
        fun ask(p: Int, t: String): Thread { prio[p] = p; return Thread { BatchContext.priority.set { prio[p] ?: p }; b.translateWithGlossary(listOf(t), "en", "fr", emptyList()) }.also { it.start() } }
        val ths = ArrayList<Thread>()
        ths.add(ask(0, "p0")); Thread.sleep(40)
        for (p in listOf(3, 5, 2, 4)) { ths.add(ask(p, "p$p")); Thread.sleep(15) }
        // le lecteur défile : la page 5 devient la plus proche
        prio[5] = -1
        ths.forEach { it.join(5000) }
        T.eq(listOf("p0", "p5", "p2", "p3", "p4"), calls.map { it.single() }, "ordre des appels au moteur")
        b.shutdown()
    }
    T.test("moteur : une page plus proche encore en route est attendue, puis traduite avec les suivantes ; l'attente est bornée, et on ne la recommence pas pour la même page") {
        val calls = Collections.synchronizedList(ArrayList<List<String>>())
        val b = TranslationBatcher(recordingEngine(calls, 30), maxPages = 3, holdMaxMs = 400)
        // la page 1 est « en route » (lecture du texte) jusqu'à ce qu'elle arrive au moteur ; la page 3 est déjà prête
        val inFlight = java.util.concurrent.atomic.AtomicReference<String?>("p1")
        b.holdBack = { priority, ignore -> inFlight.get()?.takeIf { it !in ignore && priority > 1.0 } }
        fun ask(p: Int, arrives: Boolean = false) = Thread { BatchContext.priority.set { p }; if (arrives) inFlight.set(null); b.translateWithGlossary(listOf("p$p"), "en", "fr", emptyList()) }.also { it.start() }
        val t3 = ask(3); Thread.sleep(150)
        T.ok(calls.isEmpty(), "le moteur attend la page 1 au lieu de partir avec la 3 : $calls")
        val t1 = ask(1, arrives = true)
        t1.join(3000); t3.join(3000)
        T.ok(calls.size == 1 && calls[0] == listOf("p1", "p3"), "un seul groupe, dans l'ordre : $calls")
        // attente bornée : la page « en route » n'arrive jamais
        calls.clear(); inFlight.set("jamais")
        val t0 = System.currentTimeMillis()
        val t4 = ask(4); t4.join(4000)
        val waited = System.currentTimeMillis() - t0
        T.ok(calls.size == 1 && waited in 350..2500, "après la limite, la page est traduite sans l'autre (attente $waited ms, appels $calls)")
        // on n'attend pas deux fois la même page : la suivante part tout de suite
        val t1b = System.currentTimeMillis()
        val t5 = ask(5); t5.join(3000)
        T.ok(calls.size == 2 && System.currentTimeMillis() - t1b < 300, "la page déjà abandonnée n'est plus attendue (${System.currentTimeMillis() - t1b} ms)")
        b.shutdown()
    }
    T.test("moteur : sans fonction d'attente ni priorité, comportement d'avant (ordre d'arrivée, groupes)") {
        val calls = Collections.synchronizedList(ArrayList<List<String>>())
        val b = TranslationBatcher(recordingEngine(calls, 100), maxPages = 3)
        val ths = (1..4).map { i -> Thread { b.translateWithGlossary(listOf("t$i"), "en", "fr", emptyList()) }.also { it.start(); Thread.sleep(20) } }
        ths.forEach { it.join(4000) }
        T.ok(calls.flatten().sorted() == listOf("t1", "t2", "t3", "t4"), "tout est traduit : $calls")
        T.ok(calls[0] == listOf("t1"), "la première part seule : $calls")
        T.ok(calls.drop(1).flatten() == listOf("t2", "t3", "t4"), "puis les suivantes ensemble, dans l'ordre d'arrivée : $calls")
        b.shutdown()
    }
}
