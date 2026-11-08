package app.manhwa.core

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Renseigné par le fil qui traite une page, lu par le regroupeur : annulation de la page et ordre de lecture ; en retour : taille du groupe et durée du moteur. */
object BatchContext {
    val cancelled = ThreadLocal<() -> Boolean>()
    /** Position de la page dans le chapitre (les pages d'un même groupe sont traduites dans l'ordre de lecture). */
    val order = ThreadLocal<Double>()
    /** Dernière traduction faite par ce fil : (pages dans le groupe, durée du moteur en ms). */
    val lastBatch = ThreadLocal<Pair<Int, Long>>()
}

/**
 * Regroupe les demandes de traduction de plusieurs pages en un seul appel au moteur.
 * Chaque appel au moteur de langage coûte un surcoût fixe (consigne, contexte du chapitre : ~1,3 s) quelle que soit la quantité de texte : pendant qu'une traduction
 * est en cours, les pages suivantes s'accumulent, et sont traduites ensemble au tour suivant. Aucun délai artificiel : si le moteur est libre, une page seule part tout de suite.
 * Un groupe ne mélange que des demandes de mêmes langues et de même glossaire, et reste borné (pages, lignes, caractères).
 * Si un groupe échoue, chaque page est retentée seule : l'échec d'une page ne fait pas perdre les autres.
 */
class TranslationBatcher(
    private val engine: TranslationEngine,
    private val maxPages: Int = 3,
    private val maxTexts: Int = 28,
    private val maxChars: Int = 1500,
    private val waitLimitMs: Long = 10 * 60 * 1000L,
) : TranslationEngine {

    private class Req(val texts: List<String>, val source: String, val target: String, val glossary: List<GlossaryEntry>, val order: Double, val cancelled: () -> Boolean) {
        val done = CountDownLatch(1)
        @Volatile var result: List<String>? = null
        @Volatile var error: Throwable? = null
        @Volatile var pages = 1
        @Volatile var engineMs = 0L
    }

    private val lock = Object()
    private val waiting = ArrayList<Req>()
    @Volatile private var stopped = false
    private var counter = 0.0

    private val thread = Thread({ loop() }, "traduction-groupee").apply { isDaemon = true; start() }

    /** Pour les tests et le diagnostic : nombre d'appels faits au moteur et taille du dernier groupe. */
    @Volatile var engineCalls = 0; private set
    @Volatile var lastBatchPages = 0; private set

    override fun usesGlossaryPrompt(): Boolean = engine.usesGlossaryPrompt()

    override fun translate(texts: List<String>, source: String, target: String): List<String> = translateWithGlossary(texts, source, target, emptyList())

    override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> {
        if (texts.isEmpty()) return emptyList()
        val order = BatchContext.order.get() ?: synchronized(lock) { counter++ }
        val r = Req(texts, source, target, glossary, order, BatchContext.cancelled.get() ?: { false })
        synchronized(lock) { waiting.add(r); lock.notifyAll() }
        if (!r.done.await(waitLimitMs, TimeUnit.MILLISECONDS)) {
            synchronized(lock) { waiting.remove(r) }
            throw IllegalStateException("traduction : délai dépassé")
        }
        r.error?.let { throw it }
        BatchContext.lastBatch.set(r.pages to r.engineMs)
        return r.result ?: throw IllegalStateException("traduction : aucun résultat")
    }

    fun shutdown() { stopped = true; synchronized(lock) { lock.notifyAll() } }

    private fun sameKind(a: Req, b: Req) = a.source == b.source && a.target == b.target && a.glossary == b.glossary

    private fun loop() {
        while (!stopped) {
            val batch = ArrayList<Req>()
            synchronized(lock) {
                while (!stopped) {
                    // les pages annulées pendant l'attente ne sont pas traduites
                    val dead = waiting.filter { runCatching { it.cancelled() }.getOrDefault(false) }
                    if (dead.isNotEmpty()) { waiting.removeAll(dead.toSet()); dead.forEach { it.error = CancellationException("page annulée"); it.done.countDown() } }
                    if (waiting.isNotEmpty()) break
                    lock.wait()
                }
                if (stopped) return
                val first = waiting.minBy { it.order }
                batch.add(first)
                var texts = first.texts.size; var chars = first.texts.sumOf { it.length }
                for (r in waiting.filter { it !== first && sameKind(first, it) }.sortedBy { it.order }) {
                    if (batch.size >= maxPages) break
                    val n = r.texts.size; val c = r.texts.sumOf { it.length }
                    if (texts + n > maxTexts || chars + c > maxChars) break
                    batch.add(r); texts += n; chars += c
                }
                waiting.removeAll(batch.toSet())
            }
            run(batch)
        }
    }

    private fun run(batch: List<Req>) {
        try {
            val first = batch[0]
            val t0 = System.currentTimeMillis()
            val all = batch.flatMap { it.texts }
            var combined: List<String>? = null
            var failure: Throwable? = null
            try {
                val out = engine.translateWithGlossary(all, first.source, first.target, first.glossary)
                if (out.size == all.size) combined = out else failure = IllegalStateException("réponse de ${out.size} lignes pour ${all.size}")
            } catch (e: Throwable) { failure = e }
            engineCalls++
            if (combined != null) {
                val ms = System.currentTimeMillis() - t0
                var at = 0
                for (r in batch) { r.result = combined.subList(at, at + r.texts.size).toList(); at += r.texts.size; r.pages = batch.size; r.engineMs = ms }
                lastBatchPages = batch.size
            } else if (batch.size == 1) {
                batch[0].error = failure ?: IllegalStateException("traduction impossible")
            } else {
                // le groupe a échoué : chaque page est retentée seule, dans l'ordre
                for (r in batch) {
                    val t1 = System.currentTimeMillis()
                    try {
                        r.result = engine.translateWithGlossary(r.texts, r.source, r.target, r.glossary); r.pages = 1; r.engineMs = System.currentTimeMillis() - t1
                    } catch (e: Throwable) { r.error = e }
                    engineCalls++
                }
                lastBatchPages = 1
            }
        } finally {
            batch.forEach { if (it.result == null && it.error == null) it.error = IllegalStateException("traduction interrompue"); it.done.countDown() }
        }
    }
}
