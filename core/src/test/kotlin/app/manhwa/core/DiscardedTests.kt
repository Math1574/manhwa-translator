package app.manhwa.core

import java.io.File
import java.nio.file.Files

private fun ln(t: String, l: Float, top: Float, r: Float, b: Float, c: Float = 0.95f) = OcrLine(t, RectF(l, top, r, b), c, false)
private fun tmpDir(): File = Files.createTempDirectory("mtd").toFile()

/** Textes lus et laissés de côté : liste établie à la lecture, relecture sans traduction, jonctions, plafond. */
fun discardedTests() {
    val img = byteArrayOf(9, 8, 7)
    val up = TranslationEngine { t, _, _ -> t.map { it.uppercase() } }
    val sentences = listOf(
        ln("Where are you going now", 100f, 100f, 800f, 140f), ln("I will not go back there", 100f, 400f, 800f, 440f),
        ln("Please stay with me tonight", 100f, 700f, 800f, 740f),
    )
    val sfx = ln("BOOM", 300f, 900f, 520f, 1000f)
    val mark = ln("BRAND LOGO", 600f, 1150f, 790f, 1180f)
    val unsure = ln("THE LAST WORD", 100f, 1050f, 420f, 1090f, c = 0.30f)
    val tiny = ln("!", 50f, 50f, 60f, 70f)
    val page = sentences + listOf(sfx, mark, unsure, tiny)
    fun pipe(sink: (List<DiscardedBlock>) -> Unit, ocr: OcrEngine = FakeOcr(page), owns: (TextBlock) -> Boolean = { true }, tr: TranslationEngine = up, cache: ResultCache = ResultCache(tmpDir())) =
        PagePipeline(cache, ocr, tr, "e", junk = { t, _, _ -> t.contains("BRAND") }, isSfx = { t, _, _ -> t == "BOOM" }, owns = owns, discardSink = sink)

    T.test("écartés: la lecture complète donne la liste (onomatopée, filigrane, ligne peu sûre) sans les textes traduits ni les signes isolés") {
        var got: List<DiscardedBlock>? = null
        val o = pipe({ got = it }).process(img, "en", "fr") as PageOutcome.Done
        T.eq(3, o.result.blocks.size, "textes traduits")
        val d = got!!
        T.eq(listOf("BOOM", "BRAND LOGO", "THE LAST WORD"), d.map { it.source }.sorted().sortedBy { listOf("BOOM", "BRAND LOGO", "THE LAST WORD").indexOf(it) })
        T.eq(setOf("onomatopée laissée telle quelle", "débris, filigrane ou répété", "lecture peu sûre"), d.map { it.reason }.toSet())
        T.eq(listOf(0, 1, 2), d.map { it.id }.sorted(), "rangs 0, 1, 2")
        T.ok(d.none { it.source == "!" }, "un signe seul n'est pas proposé")
        val b = d.first { it.source == "BOOM" }
        T.eq(listOf(sfx.box), b.lineBoxes, "boîte de la ligne"); T.eq(listOf("BOOM"), b.lineTexts)
    }

    T.test("écartés: la relecture sans traduction donne la même liste, sans toucher au traducteur ni au cache") {
        var fromProcess: List<DiscardedBlock>? = null
        pipe({ fromProcess = it }).process(img, "en", "fr")
        val dead = TranslationEngine { _, _, _ -> error("le traducteur ne doit pas être appelé") }
        val cache = ResultCache(tmpDir())
        val scanned = pipe({}, tr = dead, cache = cache).scanDiscarded(img)
        T.eq(fromProcess!!.map { it.source to it.reason }, scanned.map { it.source to it.reason }, "même liste")
        T.eq(0L, cache.diskBytes(), "aucune page enregistrée")
    }

    T.test("écartés: une page reprise du cache ne renvoie pas de liste (elle se relit à la demande)") {
        var calls = 0
        val cache = ResultCache(tmpDir())
        val p = pipe({ calls++ }, cache = cache)
        p.process(img, "en", "fr"); T.eq(1, calls, "première lecture")
        val b = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(b.fromCache, "depuis le cache"); T.eq(1, calls, "pas de nouvelle liste")
    }

    T.test("écartés: un texte qui revient à l'image voisine (jonction) n'est pas proposé") {
        var got: List<DiscardedBlock>? = null
        pipe({ got = it }, owns = { b -> b.box.top >= 950f }).process(img, "en", "fr")
        T.eq(listOf("BRAND LOGO", "THE LAST WORD"), got!!.map { it.source }.sorted(), "seuls les textes sous la jonction : ${got!!.map { it.source }}")
    }

    T.test("écartés: une onomatopée collée à une phrase (même bloc) est proposée à part, la phrase reste traduite") {
        // deux lignes dans un bloc libre : la phrase et un « CRASH » en gros lettrage ; la ligne d'onomatopée est retirée du bloc
        val blocky = listOf(ln("They are coming for us all", 100f, 100f, 700f, 130f), ln("CRASH", 120f, 135f, 400f, 235f), ln("Run away from here quickly", 100f, 600f, 700f, 630f), ln("We have no time left", 100f, 900f, 700f, 930f))
        var got: List<DiscardedBlock>? = null
        val o = PagePipeline(ResultCache(tmpDir()), FakeOcr(blocky), up, "e", isSfx = { t, _, _ -> t == "CRASH" }, discardSink = { got = it }).process(img, "en", "fr") as PageOutcome.Done
        T.ok(o.result.blocks.none { it.source.contains("CRASH") || it.translated.contains("CRASH") }, "CRASH n'est pas traduit : ${o.result.blocks.map { it.source }}")
        T.ok(got!!.any { it.source == "CRASH" && it.reason == "onomatopée laissée telle quelle" }, "CRASH proposé : ${got!!.map { it.source to it.reason }}")
    }

    T.test("écartés: au plus 40 textes par page") {
        val noise = (0 until 60).map { i -> ln("ZZ" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"[i % 26] + i, 100f, 20f * i + 10f, 400f, 20f * i + 28f, c = 0.2f) }
        var got: List<DiscardedBlock>? = null
        PagePipeline(ResultCache(tmpDir()), FakeOcr(sentences + noise), up, "e", discardSink = { got = it }).process(img, "en", "fr")
        T.ok(got!!.size <= 40, "plafond : ${got!!.size}")
    }

    T.test("écartés: un échec de la liste ne gêne pas la traduction de la page") {
        val o = PagePipeline(ResultCache(tmpDir()), FakeOcr(page), up, "e", discardSink = { error("liste impossible") }).process(img, "en", "fr")
        T.ok(o is PageOutcome.Done, "page traduite malgré tout")
    }
}
