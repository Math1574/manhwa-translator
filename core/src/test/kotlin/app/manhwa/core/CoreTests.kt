package app.manhwa.core

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Mini-harnais sans dépendance : exécutable avec `main` ou via le wrapper JUnit côté Gradle. */
object T {
    val results = ArrayList<Triple<String, Boolean, String>>()
    fun test(name: String, body: () -> Unit) {
        try { body(); results.add(Triple(name, true, "")) }
        catch (e: Throwable) { results.add(Triple(name, false, e.toString())) }
    }
    fun ok(cond: Boolean, msg: String) { if (!cond) throw AssertionError(msg) }
    fun <A> eq(expected: A, actual: A, msg: String = "") { if (expected != actual) throw AssertionError("$msg attendu=<$expected> obtenu=<$actual>") }
    inline fun <reified E : Throwable> fails(msg: String = "", body: () -> Unit): E {
        try { body() } catch (e: Throwable) { if (e is E) return e; throw AssertionError("$msg exception inattendue: $e") }
        throw AssertionError("$msg exception ${E::class.simpleName} attendue")
    }
}

private fun line(t: String, l: Float, top: Float, r: Float, b: Float, c: Float = 0.95f, v: Boolean = false) = OcrLine(t, RectF(l, top, r, b), c, v)
private fun tmp(): File = Files.createTempDirectory("mt").toFile()

fun runAll(): Boolean {
    T.results.clear()
    groupingTests(); layoutTests(); glossaryTests(); cacheTests(); pipelineTests(); queueTests(); modelTests(); settingsTests(); utilTests(); textTests(); layoutV2Tests()
    val failed = T.results.filter { !it.second }
    T.results.forEach { println((if (it.second) "PASS " else "FAIL ") + it.first + (if (it.second) "" else "  -> " + it.third)) }
    println("\n${T.results.size - failed.size}/${T.results.size} tests réussis")
    return failed.isEmpty()
}

fun main() { if (!runAll()) System.exit(1) }

// ---------- regroupement ----------
fun groupingTests() {
    val g = BlockGrouper()
    T.test("groupement: deux lignes d'une même bulle fusionnent") {
        val b = g.group(listOf(line("Hello there", 100f, 100f, 300f, 130f), line("how are you", 110f, 135f, 290f, 165f)))
        T.eq(1, b.size); T.eq("Hello there how are you", b[0].text)
    }
    T.test("groupement: deux bulles éloignées restent séparées, ordre de lecture haut→bas") {
        val b = g.group(listOf(line("Second", 100f, 800f, 300f, 830f), line("First", 100f, 100f, 300f, 130f)))
        T.eq(2, b.size); T.eq("First", b[0].text); T.eq("Second", b[1].text)
    }
    T.test("groupement: texte CJK sans espace parasite") {
        val b = g.group(listOf(line("こんにちは", 100f, 100f, 300f, 130f), line("元気ですか", 100f, 135f, 300f, 165f)))
        T.eq("こんにちは元気ですか", b.single().text)
    }
    T.test("groupement: texte vertical lu de droite à gauche") {
        val b = g.group(listOf(
            line("左の列", 100f, 100f, 130f, 300f, v = true), line("右の列", 140f, 100f, 170f, 300f, v = true)))
        T.eq(1, b.size); T.ok(b[0].vertical, "vertical"); T.eq("右の列左の列", b[0].text)
    }
    T.test("groupement: bruit, faible confiance et lignes minuscules ignorés") {
        val b = g.group(listOf(line("...", 0f, 0f, 50f, 20f), line("abc", 0f, 100f, 50f, 120f, c = 0.1f), line("x", 0f, 200f, 3f, 203f)))
        T.eq(0, b.size)
    }
    T.test("groupement: doublons de détection supprimés") {
        val b = g.group(listOf(line("Same text", 100f, 100f, 300f, 130f, 0.9f), line("Same text", 101f, 101f, 301f, 131f, 0.8f)))
        T.eq(1, b.size); T.eq(1, b[0].lines.size)
    }
    T.test("groupement: aucune ligne → aucun bloc") { T.eq(0, g.group(emptyList()).size) }
}

// ---------- mise en page ----------
fun layoutTests() {
    val w = 800; val h = 1200
    fun tb(id: Int, l: Float, t: Float, r: Float, b: Float, text: String) = TranslatedBlock(id, RectF(l, t, r, b), "src", text)
    T.test("layout: tous les blocs restent dans l'image (même aux bords)") {
        val p = OverlayLayout.place(listOf(
            tb(0, 0f, 0f, 120f, 40f, "Salut tout le monde, comment ça va aujourd'hui ?"),
            tb(1, 700f, 1150f, 800f, 1200f, "Un texte de traduction nettement plus long que la bulle d'origine, vraiment."),
        ), w, h)
        T.eq(2, p.size); p.forEach { T.ok(it.box.isInside(w.toFloat(), h.toFloat()), "bloc ${it.id} hors image: ${it.box}") }
    }
    T.test("layout: pas de chevauchement notable entre blocs voisins") {
        val p = OverlayLayout.place(listOf(
            tb(0, 100f, 100f, 300f, 160f, "Une phrase assez longue pour déborder de sa bulle d'origine"),
            tb(1, 100f, 165f, 300f, 225f, "Une autre phrase assez longue pour déborder aussi de sa bulle"),
        ), w, h)
        for (i in p.indices) for (j in i + 1 until p.size) T.ok(p[i].box.overlapRatio(p[j].box) < 0.05f, "chevauchement ${p[i].box} / ${p[j].box}")
        p.forEach { T.ok(it.box.isInside(w.toFloat(), h.toFloat()), "hors image") }
    }
    T.test("layout: le texte tient dans la zone calculée") {
        val p = OverlayLayout.place(listOf(tb(0, 100f, 100f, 400f, 250f, "Bonjour, je m'appelle Kim et je suis ravi de te rencontrer ici.")), w, h).single()
        T.ok(p.lines.size * p.fontSizePx * 1.15f <= p.box.height + 1f, "texte déborde verticalement")
        p.lines.forEach { T.ok(EstimatedMeasurer.width(it, p.fontSizePx) <= p.box.width + 1f, "ligne trop large: $it") }
    }
    T.test("layout: l'échelle utilisateur agrandit le texte (si la place le permet)") {
        val b = listOf(tb(0, 100f, 100f, 500f, 160f, "Bonjour"))
        T.ok(OverlayLayout.place(b, w, h, 1.5f).single().fontSizePx > OverlayLayout.place(b, w, h, 0.7f).single().fontSizePx, "échelle sans effet")
    }
    T.test("layout: traductions vides ignorées, liste vide acceptée") {
        T.eq(0, OverlayLayout.place(listOf(tb(0, 1f, 1f, 50f, 50f, "  ")), w, h).size)
        T.eq(0, OverlayLayout.place(emptyList(), w, h).size)
    }
    T.test("layout: très long texte dans petite bulle reste borné à l'image") {
        val p = OverlayLayout.place(listOf(tb(0, 10f, 10f, 60f, 30f, "mot ".repeat(200))), 200, 200).single()
        T.ok(p.box.isInside(200f, 200f), "hors image ${p.box}")
    }
}

// ---------- glossaire ----------
fun glossaryTests() {
    T.test("glossaire: protection puis restitution des noms propres") {
        val g = Glossary(listOf(GlossaryEntry("성진우", "Sung Jin-Woo")))
        val p = g.protect("성진우가 왔다")
        T.ok(!p.text.contains("성진우"), "terme non remplacé")
        T.eq("Sung Jin-Woo est arrivé", g.restore(p.text.replace("가 왔다", " est arrivé"), p))
    }
    T.test("glossaire: jeton perdu par le traducteur → null") {
        val g = Glossary(listOf(GlossaryEntry("田中", "Tanaka")))
        T.eq(null, g.restore("rien", g.protect("田中さん")))
    }
    T.test("glossaire: le terme le plus long gagne") {
        val g = Glossary(listOf(GlossaryEntry("田中", "Tanaka"), GlossaryEntry("田中先生", "Maître Tanaka")))
        T.eq(1, g.protect("田中先生").tokens.size)
    }
    T.test("glossaire: correction exacte insensible aux espaces") {
        val g = Glossary(corrections = mapOf("Hello  world" to "Salut le monde"))
        T.eq("Salut le monde", g.correctionFor("Hello world"))
    }
    T.test("glossaire: sérialisation aller-retour (tabulations et retours ligne)") {
        val g = Glossary(listOf(GlossaryEntry("a\tb", "c\nd")), mapOf("x" to "y"))
        val r = Glossary.parse(g.serialize())
        T.eq(g.terms(), r.terms()); T.eq("y", r.correctionFor("x"))
    }
}

// ---------- cache ----------
fun cacheTests() {
    val key = CacheKey("abc", "ja", "fr", "eng1")
    val res = PageResult(100, 200, listOf(TranslatedBlock(0, RectF(1f, 2f, 3f, 4f), "原文\tx", "tra\nduction", true, 12.5f)))
    T.test("cache: écriture puis lecture, persistance après redémarrage") {
        val d = tmp(); ResultCache(d).put(key, res)
        T.eq(res, ResultCache(d).get(key))
    }
    T.test("cache: clé différente (langue ou moteur) = pas de collision") {
        val d = tmp(); val c = ResultCache(d); c.put(key, res)
        T.eq(null, c.get(key.copy(target = "en"))); T.eq(null, c.get(key.copy(engineId = "eng2")))
    }
    T.test("cache: fichier corrompu → miss et suppression, pas de crash") {
        val d = tmp(); val c = ResultCache(d); c.put(key, res)
        File(d, key.id + ".page").writeText("n'importe quoi")
        T.eq(null, ResultCache(d).get(key)); T.ok(!File(d, key.id + ".page").exists(), "fichier non supprimé")
    }
    T.test("cache: vidage") {
        val d = tmp(); val c = ResultCache(d); c.put(key, res); c.clear()
        T.eq(0L, c.diskBytes()); T.eq(null, c.get(key))
    }
    T.test("cache: éviction disque au-delà de la limite") {
        val d = tmp(); val c = ResultCache(d, maxDiskBytes = 400)
        repeat(10) { i -> c.put(CacheKey("h$i", "ja", "fr", "e"), res); Thread.sleep(5) }
        T.ok(c.diskBytes() <= 400 + 200, "limite dépassée: ${c.diskBytes()}")
    }
}

// ---------- pipeline ----------
class FakeOcr(val lines: List<OcrLine>, val fail: Boolean = false) : OcrEngine {
    val calls = AtomicInteger()
    override fun recognize(imageBytes: ByteArray): OcrResult { calls.incrementAndGet(); if (fail) error("OCR HS"); return OcrResult(800, 1200, lines) }
}
fun pipelineTests() {
    val img = byteArrayOf(1, 2, 3)
    val lines = listOf(line("Hello", 100f, 100f, 300f, 130f), line("Bye", 100f, 600f, 300f, 630f))
    val up = TranslationEngine { t, _, _ -> t.map { it.uppercase() } }
    T.test("pipeline: OCR → traduction → résultat, puis cache (OCR non rappelé)") {
        val ocr = FakeOcr(lines); val p = PagePipeline(ResultCache(tmp()), ocr, up, "e")
        val a = p.process(img, "en", "fr") as PageOutcome.Done
        T.eq(listOf("HELLO", "BYE"), a.result.blocks.map { it.translated }); T.ok(!a.fromCache, "1er appel pas du cache")
        val b = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(b.fromCache, "2e appel du cache"); T.eq(1, ocr.calls.get())
    }
    T.test("pipeline: erreur OCR → Failed(ocr) sans exception") {
        val o = PagePipeline(ResultCache(tmp()), FakeOcr(emptyList(), fail = true), up, "e").process(img, "ja", "fr")
        T.eq("ocr", (o as PageOutcome.Failed).stage)
    }
    T.test("pipeline: aucune bulle détectée → NoText") {
        T.ok(PagePipeline(ResultCache(tmp()), FakeOcr(emptyList()), up, "e").process(img, "ja", "fr") is PageOutcome.NoText, "NoText attendu")
    }
    T.test("pipeline: lot en échec → repli bloc par bloc, bloc fautif ignoré, page non mise en cache") {
        val flaky = TranslationEngine { t, _, _ -> if (t.size > 1 || t[0] == "Bye") error("boom") else listOf("Salut") }
        val c = ResultCache(tmp()); val p = PagePipeline(c, FakeOcr(lines), flaky, "e")
        val o = p.process(img, "en", "fr") as PageOutcome.Done
        T.eq(listOf("Salut"), o.result.blocks.map { it.translated })
        T.eq(null, c.get(CacheKey(sha256Hex(img), "en", "fr", "e")))
    }
    T.test("pipeline: traducteur totalement en panne → Failed(translation)") {
        val dead = TranslationEngine { _, _, _ -> error("modèle absent") }
        T.eq("translation", (PagePipeline(ResultCache(tmp()), FakeOcr(lines), dead, "e").process(img, "en", "fr") as PageOutcome.Failed).stage)
    }
    T.test("pipeline: glossaire (jeton restitué, jeton perdu → retraduction sans protection)") {
        val g = Glossary(listOf(GlossaryEntry("Hello", "Salutations")))
        val keep = PagePipeline(ResultCache(tmp()), FakeOcr(lines.take(1)), up, "e", { g })
        T.eq("Salutations", ((keep.process(img, "en", "fr") as PageOutcome.Done).result.blocks.single().translated))
        val lossy = TranslationEngine { t, _, _ -> t.map { if (it.contains("ZQ")) "perdu" else it.uppercase() } }
        T.eq("HELLO", ((PagePipeline(ResultCache(tmp()), FakeOcr(lines.take(1)), lossy, "e", { g }).process(img, "en", "fr") as PageOutcome.Done).result.blocks.single().translated))
    }
    T.test("pipeline: correction mémorisée prioritaire sur le traducteur") {
        val g = Glossary(corrections = mapOf("Hello" to "Coucou"))
        val o = PagePipeline(ResultCache(tmp()), FakeOcr(lines.take(1)), up, "e", { g }).process(img, "en", "fr") as PageOutcome.Done
        T.eq("Coucou", o.result.blocks.single().translated)
    }
}

// ---------- file d'attente ----------
fun queueTests() {
    class L : QueueListener {
        val events = CopyOnWriteArrayList<String>()
        override fun onStarted(id: String) { events.add("start:$id") }
        override fun onFinished(id: String, outcome: PageOutcome) { events.add("end:$id:" + outcome.javaClass.simpleName) }
        override fun onCancelled(id: String) { events.add("cancel:$id") }
    }
    val done = PageOutcome.NoText(1, 1)
    T.test("file: traitement séquentiel par priorité, erreur isolée") {
        val l = L(); val q = PageQueue(8, l)
        val gate = java.util.concurrent.CountDownLatch(1)
        q.submit(PageJob("a", 0) { gate.await(); done })
        Thread.sleep(50)
        q.submit(PageJob("low", 5) { done }); q.submit(PageJob("boom", 1) { error("x") }); q.submit(PageJob("high", 0) { done })
        gate.countDown(); T.ok(q.awaitIdle(3000), "file non vidée")
        T.eq(listOf("start:a", "end:a:NoText", "start:high", "end:high:NoText", "start:boom", "end:boom:Failed", "start:low", "end:low:NoText"), l.events.toList())
        q.shutdown()
    }
    T.test("file: doublon et file pleine refusés") {
        val q = PageQueue(2, L()); val gate = java.util.concurrent.CountDownLatch(1)
        q.submit(PageJob("run") { gate.await(); done }); Thread.sleep(50)
        T.ok(q.submit(PageJob("p1") { done }), "p1"); T.ok(!q.submit(PageJob("p1") { done }), "doublon accepté")
        T.ok(q.submit(PageJob("p2") { done }), "p2"); T.ok(!q.submit(PageJob("p3") { done }), "file pleine acceptée")
        gate.countDown(); q.awaitIdle(3000); q.shutdown()
    }
    T.test("file: annulation d'une page en attente et d'une page en cours") {
        val l = L(); val q = PageQueue(8, l); val started = java.util.concurrent.CountDownLatch(1)
        q.submit(PageJob("run") { c -> started.countDown(); while (!c()) Thread.sleep(5); done })
        started.await(); q.submit(PageJob("wait") { done })
        q.cancel("wait"); q.cancel("run")
        T.ok(q.awaitIdle(3000), "non terminée")
        T.ok("cancel:wait" in l.events && "cancel:run" in l.events, "annulations manquantes: ${l.events}")
        T.ok(l.events.none { it.startsWith("start:wait") }, "page annulée exécutée")
        q.shutdown()
    }
    T.test("file: un id peut être resoumis après traitement (reprise)") {
        val q = PageQueue(8, L()); q.submit(PageJob("p") { done }); q.awaitIdle(2000)
        T.ok(q.submit(PageJob("p") { done }), "resoumission refusée"); q.awaitIdle(2000); q.shutdown()
    }
}

// ---------- modèles (vrai serveur HTTP local) ----------
fun modelTests() {
    val data = ByteArray(300_000) { (it * 31 % 251).toByte() }
    val hash = sha256Hex(data)
    fun server(cutAt: Int = -1, status: Int = 200, payload: ByteArray = data): HttpServer {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/m") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            if (status != 200) { ex.sendResponseHeaders(status, -1); ex.close(); return@createContext }
            val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            val body = payload.copyOfRange(start, payload.size)
            if (range != null) { ex.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}"); ex.sendResponseHeaders(206, body.size.toLong()) }
            else ex.sendResponseHeaders(200, body.size.toLong())
            val out = ex.responseBody
            if (cutAt >= 0) { out.write(body, 0, minOf(cutAt, body.size)); out.flush(); ex.close() } else { out.write(body); out.close() }
        }
        s.start(); return s
    }
    fun spec(s: HttpServer, sha: String = hash, size: Long = data.size.toLong()) = ModelSpec("m", "http://127.0.0.1:${s.address.port}/m", size, sha, "m.bin")
    fun store(free: Long = Long.MAX_VALUE / 2, large: Long = 100L * 1024 * 1024) = ModelStore(tmp(), { free }, requireHttps = false, largeThresholdBytes = large)

    T.test("modèles: téléchargement, progression, vérification, statut Prêt") {
        val s = server(); val st = store(); val sp = spec(s); val prog = ArrayList<Long>()
        T.eq(ModelStatus.Missing, st.status(sp)); st.download(sp, true, { d, _ -> prog.add(d) })
        T.eq(ModelStatus.Ready, st.status(sp)); T.eq(data.size.toLong(), prog.last()); T.ok(st.fileOf(sp).readBytes().contentEquals(data), "contenu")
        s.stop(0)
    }
    T.test("modèles: fichier volumineux sans confirmation explicite refusé") {
        val s = server(); T.fails<ConfirmationRequired> { store(large = 1000).download(spec(s), confirmed = false) }; s.stop(0)
    }
    T.test("modèles: espace disque insuffisant détecté avant de télécharger") {
        val s = server(); T.fails<InsufficientSpace> { store(free = 1000).download(spec(s), true) }; s.stop(0)
    }
    T.test("modèles: somme de contrôle invalide → fichier supprimé, statut Erreur") {
        val s = server(); val st = store(); val sp = spec(s, sha = "0".repeat(64))
        T.fails<ChecksumMismatch> { st.download(sp, true) }
        T.ok(st.status(sp) is ModelStatus.Error, "statut erreur"); T.ok(!st.fileOf(sp).exists(), "fichier corrompu conservé"); s.stop(0)
    }
    T.test("modèles: coupure réseau en cours → erreur claire puis reprise (Range) réussie") {
        val cut = server(cutAt = 120_000); val st = store(); val sp = spec(cut)
        T.fails<DownloadError> { st.download(sp, true) }
        cut.stop(0)
        val part = File(st.fileOf(sp).path + ".part"); T.ok(part.exists() && part.length() in 1 until data.size.toLong(), "partie conservée: ${part.length()}")
        val ok = server(); val sp2 = sp.copy(url = "http://127.0.0.1:${ok.address.port}/m")
        st.download(sp2, true); T.eq(ModelStatus.Ready, st.status(sp2)); ok.stop(0)
    }
    T.test("modèles: erreur HTTP 404 et serveur injoignable → DownloadError") {
        val s = server(status = 404); T.fails<DownloadError> { store().download(spec(s), true) }; val sp = spec(s); s.stop(0)
        T.fails<DownloadError> { store().download(sp, true) }
    }
    T.test("modèles: URL non https refusée par défaut") {
        val st = ModelStore(tmp(), { Long.MAX_VALUE / 2 })
        T.fails<DownloadError> { st.download(ModelSpec("x", "http://exemple.invalid/m", 10, "0".repeat(64), "x.bin"), true) }
    }
    T.test("modèles: suppression remet le statut à Absent") {
        val s = server(); val st = store(); val sp = spec(s); st.download(sp, true); st.delete(sp)
        T.eq(ModelStatus.Missing, st.status(sp)); T.eq(0L, st.usedBytes()); s.stop(0)
    }
}

// ---------- paramètres ----------
fun settingsTests() {
    T.test("paramètres: aller-retour complet") {
        val s = AppSettings(SourceLang.KO, TargetLang.DE, 1.4f, true, QualityMode.ENHANCED, 5, 120)
        T.eq(s, AppSettings.parse(s.serialize()))
    }
    T.test("paramètres: valeurs absentes/invalides → défauts, bornes appliquées") {
        T.eq(AppSettings(), AppSettings.parse(null)); T.eq(AppSettings(), AppSettings.parse("%%%"))
        val s = AppSettings.parse("textScale=99\nmaxPrefetchPages=-4\nsource=KLINGON\ncacheMaxMb=1")
        T.eq(2.0f, s.textScale); T.eq(0, s.maxPrefetchPages); T.eq(SourceLang.AUTO, s.source); T.eq(10, s.cacheMaxMb)
    }
}

// ---------- utilitaires ----------
fun utilTests() {
    T.test("écriture: japonais, coréen, chinois, anglais") {
        T.eq("ja", ScriptDetector.detect("これは日本語です")); T.eq("ko", ScriptDetector.detect("안녕하세요"))
        T.eq("zh", ScriptDetector.detect("你好世界")); T.eq("en", ScriptDetector.detect("Hello"))
        T.eq("ja", ScriptDetector.detect("田中さん")); T.eq("en", ScriptDetector.detect(""))
    }
    T.test("couleurs: médiane, contraste du texte, format CSS") {
        T.eq(0xFF101010.toInt(), ColorMath.median(intArrayOf(0xFF101010.toInt(), 0xFF101010.toInt(), 0xFFFFFFFF.toInt())))
        T.eq(0xFFFFFFFF.toInt(), ColorMath.median(IntArray(0)))
        T.eq(0xFF000000.toInt(), ColorMath.textColorFor(0xFFFFFFFF.toInt())); T.eq(0xFFFFFFFF.toInt(), ColorMath.textColorFor(0xFF202030.toInt()))
        T.eq("#0a0b0c", ColorMath.css(0xFF0A0B0C.toInt()))
    }
    T.test("adresse: domaine, URL complète, recherche, schémas dangereux") {
        T.eq("https://exemple.com/a", UrlInput.normalize("exemple.com/a")); T.eq("http://x.org", UrlInput.normalize(" http://x.org "))
        T.eq("", UrlInput.normalize("   "))
        T.ok(UrlInput.normalize("manhwa solo leveling").startsWith("https://duckduckgo.com/?q=manhwa+solo"), "recherche")
        T.ok(UrlInput.normalize("javascript:alert(1)").startsWith("https://duckduckgo.com/?q="), "javascript: doit devenir une recherche")
        T.ok(UrlInput.normalize("file:///etc/passwd").startsWith("https://duckduckgo.com/?q="), "file: doit devenir une recherche")
    }
    T.test("file: la limite d'attente peut changer à chaud") {
        val q = PageQueue(1, object : QueueListener {}); q.maxPending = 3
        val gate = java.util.concurrent.CountDownLatch(1)
        q.submit(PageJob("r") { gate.await(); PageOutcome.NoText(1, 1) }); Thread.sleep(50)
        T.ok(q.submit(PageJob("a") { PageOutcome.NoText(1, 1) }) && q.submit(PageJob("b") { PageOutcome.NoText(1, 1) }), "2 en attente acceptés")
        gate.countDown(); q.awaitIdle(2000); q.shutdown()
    }
}

// ---------- normalisation du texte ----------
fun textTests() {
    T.test("texte: bruit (onomatopées sans voyelle, débris) filtré, vrai texte conservé") {
        listOf("HMPH", "SHH", "..", "?!", "a", "#%&*", "TCH").forEach { T.ok(TextNormalizer.isNoise(it), "devrait être du bruit: $it") }
        listOf("Hello", "PLEASE...", "No way!", "털썩", "こんにちは", "你好", "I see").forEach { T.ok(!TextNormalizer.isNoise(it), "ne devrait pas être du bruit: $it") }
    }
    T.test("texte: majuscules ramenées en casse de phrase, texte déjà mixte inchangé") {
        T.eq("Please... Take me as your disciple...!", TextNormalizer.fixCase("PLEASE... TAKE ME AS YOUR DISCIPLE...!"))
        T.eq("I think I know. Do you?", TextNormalizer.fixCase("I THINK I KNOW. DO YOU?"))
        T.eq("Hello there, Kim", TextNormalizer.fixCase("Hello there, Kim")); T.eq("OK", TextNormalizer.fixCase("OK"))
    }
    T.test("texte: jetons du glossaire préservés par le changement de casse") {
        T.eq("Where is ZQ0X going?", TextNormalizer.fixCase("WHERE IS ZQ0X GOING?"))
    }
    T.test("texte: découpage en phrases (abréviations et ponctuation seule)") {
        T.eq(listOf("Hello.", "How are you?", "Mr. Kim is here!"), TextNormalizer.splitSentences("Hello. How are you? Mr. Kim is here!"))
        T.eq(listOf("... I see."), TextNormalizer.splitSentences("... I see."))
        T.eq(listOf("こんにちは。", "元気ですか？"), TextNormalizer.splitSentences("こんにちは。元気ですか？"))
        T.eq(emptyList<String>(), TextNormalizer.splitSentences("   "))
    }
    T.test("pipeline: le traducteur reçoit du texte en casse de phrase ; les blocs de bruit sont ignorés") {
        val seen = ArrayList<String>()
        val spy = TranslationEngine { t, _, _ -> seen.addAll(t); t }
        val o = PagePipeline(ResultCache(tmp()), FakeOcr(listOf(line("PLEASE TAKE ME", 100f, 100f, 300f, 130f), line("HMPH", 100f, 600f, 300f, 630f))), spy, "e")
            .process(byteArrayOf(9), "en", "fr") as PageOutcome.Done
        T.eq(listOf("Please take me"), seen); T.eq(1, o.result.blocks.size)
        T.ok(PagePipeline(ResultCache(tmp()), FakeOcr(listOf(line("HMPH", 1f, 1f, 90f, 40f))), spy, "e").process(byteArrayOf(8), "en", "fr") is PageOutcome.NoText, "page de bruit = NoText")
    }
}

// ---------- mise en page v2 ----------
fun layoutV2Tests() {
    val text = "Aidez-moi s'il vous plaît, monsieur."
    T.test("layout: aucun mot coupé, police issue de la hauteur de LIGNE (et non du bloc entier)") {
        val b = TranslatedBlock(0, RectF(250f, 150f, 560f, 330f), "src", text, false, 30f) // bloc de 3 lignes de 30 px
        val p = OverlayLayout.place(listOf(b), 800, 1200).single()
        text.split(" ").forEach { w -> T.ok(p.lines.any { it.contains(w) }, "mot coupé: $w dans ${p.lines}") }
        T.ok(p.fontSizePx <= 30f * 0.9f + 0.5f, "police trop grande: ${p.fontSizePx}")
        T.ok(p.lines.size * p.fontSizePx * 1.15f <= p.box.height + 1f, "déborde en hauteur")
        p.lines.forEach { T.ok(EstimatedMeasurer.width(it, p.fontSizePx) <= p.box.width + 1f, "ligne trop large: $it") }
    }
    T.test("layout: texte vertical → zone élargie pour une traduction horizontale lisible") {
        val b = TranslatedBlock(0, RectF(400f, 100f, 440f, 400f), "src", "Je ne te pardonnerai jamais.", true, 40f)
        val p = OverlayLayout.place(listOf(b), 800, 1200).single()
        T.ok(p.box.width >= 190f, "zone trop étroite: ${p.box.width}"); T.ok(p.fontSizePx >= 12f, "police illisible: ${p.fontSizePx}")
        T.ok(p.box.isInside(800f, 1200f), "hors image")
    }
}
