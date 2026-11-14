package app.manhwa.core

import java.io.File
import java.nio.file.Files

private fun tdir(): File = Files.createTempDirectory("mt-tr").toFile()
private fun ol(t: String, l: Float, top: Float, r: Float, b: Float) = OcrLine(t, RectF(l, top, r, b), 0.95f, false)
private fun tb(id: Int, src: String, tr: String, repaired: String = "") = TranslatedBlock(id, RectF(0f, id * 100f, 100f, id * 100f + 30f), src, tr, repaired = repaired)

fun termRefreshTests() {
    val frEngine = TranslationEngine { t, _, _ -> t.map { "FR($it)" } }
    val page = PageResult(800, 1200, listOf(
        tb(0, "Please take me as your disciple", "FR(Please take me as your disciple)"),
        tb(1, "I cannot believe you came here", "FR(I cannot believe you came here)"),
        tb(2, "The Disciple will not see anyone", "FR(The Disciple will not see anyone)"),
    ))

    // ---------- versions du glossaire ----------
    T.test("glossaire: chaque terme ajouté ou changé porte un numéro de version ; un terme déjà connu tel quel n'en change pas") {
        val g = Glossary()
        T.eq(0L, g.revision)
        g.addTerm("Kim", "Kim"); T.eq(1L, g.revision)
        g.addTerm("Kim", "Kim"); T.eq(1L, g.revision, "même terme : rien ne change")
        g.addTerm("Master", "Maître"); T.eq(2L, g.revision)
        g.addTerm("Master", "Seigneur"); T.eq(3L, g.revision, "traduction changée")
        T.eq(listOf("Kim", "Master"), g.termsNewerThan(0L).map { it.source })
        T.eq(listOf("Master"), g.termsNewerThan(2L).map { it.source })
        T.eq(emptyList<GlossaryEntry>(), g.termsNewerThan(3L))
        g.removeTerm("Master"); g.addTerm("Master", "Maître"); T.eq(4L, g.revision, "retiré puis remis : nouveau numéro")
        T.ok(g.hasTerm(null, "Kim", "Kim") && !g.hasTerm(null, "Kim", "Autre") && !g.hasTerm("s", "Kim", "Kim"), "hasTerm")
    }
    T.test("glossaire: un terme d'œuvre garde sa version dans le glossaire fusionné et remplace le terme commun") {
        val g = Glossary(); g.addTerm("Master", "Maître")                  // version 1
        g.addSeriesTerm("s|a", "Master", "Seigneur")                     // 2
        g.addSeriesTerm("s|a", "Jin", "Jin")                             // 3
        val a = g.forSeries("s|a"); val b = g.forSeries("s|b"); val n = g.forSeries(null)
        T.eq(3L, a.revision); T.eq(3L, b.revision)
        T.eq(listOf("Master" to "Seigneur", "Jin" to "Jin"), a.termsNewerThan(0L).map { it.source to it.target })
        T.eq(listOf("Master", "Jin"), a.termsNewerThan(1L).map { it.source }, "le terme d'œuvre est plus récent que le terme commun")
        T.eq(listOf("Master" to "Maître"), b.termsNewerThan(0L).map { it.source to it.target })
        T.eq(emptyList<GlossaryEntry>(), b.termsNewerThan(1L), "autre œuvre : rien de plus récent")
        T.eq(listOf("Master"), n.terms().map { it.source })
        g.removeSeriesTerm("s|a", "Jin"); T.eq(listOf("Master"), g.forSeries("s|a").termsNewerThan(1L).map { it.source })
    }
    T.test("glossaire: les versions survivent à l'enregistrement ; un ancien fichier (sans versions) se lit tel quel, sans terme récent") {
        val g = Glossary(); g.addTerm("A", "B"); g.addSeriesTerm("k|x", "Kim", "Kim"); g.addTerm("A", "C"); g.addCorrection("Hello there", "Salut")
        val r = Glossary.parse(g.serialize())
        T.eq(g.revision, r.revision); T.eq(g.terms(), r.terms()); T.eq(g.seriesTerms(), r.seriesTerms()); T.eq("Salut", r.correctionFor("Hello there"))
        T.eq(g.termsNewerThan(0L), r.termsNewerThan(0L))
        T.eq(g.forSeries("k|x").termsNewerThan(1L), r.forSeries("k|x").termsNewerThan(1L))
        val old = Glossary.parse("T\tA\tB\nC\tx\ty\nS\tk|x\tKim\tKim\n")
        T.eq(0L, old.revision); T.eq(emptyList<GlossaryEntry>(), old.termsNewerThan(0L)); T.eq(1, old.seriesTerms().size); T.eq(1, old.terms().size)
        old.addTerm("Z", "Y"); T.eq(1L, old.revision); T.eq(listOf("Z"), old.termsNewerThan(0L).map { it.source }, "seul le terme ajouté ensuite est récent")
    }
    T.test("glossaire: la copie est indépendante (simulation d'un ajout)") {
        val g = Glossary(); g.addTerm("Kim", "Kim")
        val c = g.copy(); c.addTerm("Master", "Maître"); c.addSeriesTerm("s", "X", "Y")
        T.eq(1, g.terms().size); T.eq(1L, g.revision); T.eq(0, g.seriesTerms().size)
        T.eq(2, c.terms().size); T.eq(3L, c.revision); T.eq(1, c.seriesTerms().size)
    }

    // ---------- cache : version du glossaire de chaque page ----------
    T.test("cache: la version du glossaire est conservée en mémoire et sur disque ; une page enregistrée avant ce suivi vaut 0") {
        val d = tdir(); val c = ResultCache(d); val k = CacheKey("h", "en", "fr", "e")
        c.put(k, page, 7L)
        T.eq(7L, c.revisionOf(k)); T.eq(3, c.get(k)!!.blocks.size)
        T.eq(7L, ResultCache(d).revisionOf(k), "relu depuis le disque")
        T.eq(3, ResultCache(d).get(k)!!.blocks.size, "la page se relit aussi avec la version")
        c.put(k, page); T.eq(0L, ResultCache(d).revisionOf(k), "sans version")
        File(d, k.id + ".page").writeText(ResultCache.encode(page))               // fichier d'avant ce suivi : en-tête sans version
        T.eq(0L, ResultCache(d).revisionOf(k)); T.eq("FR(Please take me as your disciple)", ResultCache(d).get(k)!!.blocks[0].translated)
        T.eq(0L, c.revisionOf(CacheKey("absente", "en", "fr", "e")), "page absente")
    }

    // ---------- textes concernés ----------
    T.test("retraduction ciblée: seuls les blocs qui contiennent un terme récent sont concernés") {
        val g = Glossary(); g.addTerm("disciple", "apprenti")
        T.eq(listOf(0, 2), TermRefresh.concerned(page, g, 0L), "casse ignorée")
        T.eq(emptyList<Int>(), TermRefresh.concerned(page, g, 1L), "terme plus ancien que la page : rien à refaire")
        T.eq(listOf(2), TermRefresh.concerned(page, g, 0L) { it.id == 0 }, "bloc écarté par l'appelant")
        val gc = Glossary(corrections = mapOf("The Disciple will not see anyone" to "Fixé")); gc.addTerm("disciple", "apprenti")
        T.eq(listOf(0), TermRefresh.concerned(page, gc, 0L), "une correction exacte garde la priorité")
        val done = page.copy(blocks = page.blocks.map { if (it.id == 0) it.copy(translated = "Prenez-moi comme apprenti") else it })
        T.eq(listOf(2), TermRefresh.concerned(done, g, 0L), "la traduction contient déjà le terme")
        val fixed = page.copy(blocks = page.blocks.map { if (it.id == 1) it.copy(source = "I cannot belive you came here", repaired = "I cannot believe you came here") else it })
        val gb = Glossary(); gb.addTerm("believe", "croire")
        T.eq(listOf(1), TermRefresh.concerned(fixed, gb, 0L), "le texte réparé (celui que le traducteur a reçu) fait foi")
    }

    // ---------- retraduction ----------
    T.test("retraduction ciblée: les termes sont restitués, seuls les blocs demandés sont traduits") {
        val g = Glossary(); g.addTerm("disciple", "apprenti")
        val seen = ArrayList<List<String>>()
        val eng = TranslationEngine { t, _, _ -> seen.add(t); t.map { "FR($it)" } }
        val out = TermRefresh(eng).retranslate(page, listOf(0, 2), g, "en", "fr")
        T.eq(setOf(0, 2), out.keys); T.eq("FR(Please take me as your apprenti)", out[0]); T.eq("FR(The apprenti will not see anyone)", out[2])
        T.eq(listOf(listOf("Please take me as your ZQ0X", "The ZQ0X will not see anyone")), seen, "un seul lot, termes protégés, bloc 1 absent")
        val upd = TermRefresh.withTranslations(page, out)
        T.eq("FR(I cannot believe you came here)", upd.blocks[1].translated); T.eq("FR(Please take me as your apprenti)", upd.blocks[0].translated)
        T.eq(page.blocks[1], upd.blocks[1], "le bloc non concerné est strictement identique")
    }
    T.test("retraduction ciblée: texte d'origine en majuscules → traduction en majuscules ; jeton perdu → retraduction sans protection") {
        val g = Glossary(); g.addTerm("disciple", "apprenti")
        val caps = PageResult(10, 10, listOf(tb(0, "PLEASE TAKE ME AS YOUR DISCIPLE", "x")))
        T.eq("FR(PLEASE TAKE ME AS YOUR APPRENTI)", TermRefresh(frEngine).retranslate(caps, listOf(0), g, "en", "fr")[0])
        val lossy = TranslationEngine { t, _, _ -> t.map { if (it.contains("ZQ")) "perdu" else "SANS(" + it + ")" } }
        T.eq("SANS(Please take me as your disciple)", TermRefresh(lossy).retranslate(page, listOf(0), g, "en", "fr")[0])
    }
    T.test("retraduction ciblée: avec un modèle de langage, seuls les termes présents dans les textes retraduits sont transmis, sans jetons") {
        val g = Glossary(); g.addTerm("disciple", "apprenti"); g.addTerm("zzz", "inutile")
        var seenTerms: List<GlossaryEntry> = emptyList(); var seenTexts: List<String> = emptyList()
        val llm = object : TranslationEngine {
            override fun translate(texts: List<String>, source: String, target: String) = texts
            override fun usesGlossaryPrompt() = true
            override fun translateWithGlossary(texts: List<String>, source: String, target: String, glossary: List<GlossaryEntry>): List<String> { seenTerms = glossary; seenTexts = texts; return texts.map { "LLM:$it" } }
        }
        val out = TermRefresh(llm).retranslate(page, listOf(0), g, "en", "fr")
        T.eq(listOf(GlossaryEntry("disciple", "apprenti")), seenTerms); T.eq(listOf("Please take me as your disciple"), seenTexts); T.eq("LLM:Please take me as your disciple", out[0])
    }
    T.test("retraduction ciblée: un lot en échec est retenté bloc par bloc ; un moteur en panne ne change rien") {
        val g = Glossary(); g.addTerm("disciple", "apprenti")
        val flaky = TranslationEngine { t, _, _ -> if (t.size > 1 || t[0].startsWith("The")) error("boom") else t.map { "FR($it)" } }
        val out = TermRefresh(flaky).retranslate(page, listOf(0, 2), g, "en", "fr")
        T.eq(setOf(0), out.keys, "seul le bloc fautif est perdu")
        val dead = TranslationEngine { _, _, _ -> error("absent") }
        T.eq(emptyMap<Int, String>(), TermRefresh(dead).retranslate(page, listOf(0, 2), g, "en", "fr"))
        T.eq(emptyMap<Int, String>(), TermRefresh(dead).retranslate(page, emptyList(), g, "en", "fr"))
    }

    // ---------- au fil de la lecture : page reprise du cache ----------
    val img = byteArrayOf(9, 9, 9)
    val ocrLines = listOf(ol("Please take me as your disciple", 100f, 100f, 400f, 130f), ol("I cannot believe you came here", 100f, 600f, 400f, 630f), ol("Nothing else to say today", 100f, 1100f, 400f, 1130f))
    fun translatedOf(o: PageOutcome.Done, start: String) = o.result.blocks.first { it.source.startsWith(start) }.translated
    T.test("pipeline: un terme ajouté après coup ne retraduit, à l'affichage, que les textes concernés ; la page est remise à jour dans le cache") {
        val calls = ArrayList<List<String>>()
        val eng = TranslationEngine { t, _, _ -> calls.add(t); t.map { "FR($it)" } }
        val g = Glossary(); val cache = ResultCache(tdir())
        val p = PagePipeline(cache, FakeOcr(ocrLines), eng, "e", { g })
        val key = CacheKey(sha256Hex(img), "en", "fr", "e")
        val first = p.process(img, "en", "fr") as PageOutcome.Done
        T.eq(3, first.result.blocks.size); T.eq(0, first.refreshed); T.eq(1, calls.size); T.eq(0L, cache.revisionOf(key))
        calls.clear()
        val same = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(same.fromCache && same.refreshed == 0 && calls.isEmpty(), "rien à refaire : $calls")
        g.addTerm("disciple", "apprenti")
        val upd = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(upd.fromCache, "repris du cache"); T.eq(1, upd.refreshed)
        T.eq(listOf(listOf("Please take me as your ZQ0X")), calls, "un seul texte envoyé au traducteur")
        T.eq("FR(Please take me as your apprenti)", translatedOf(upd, "Please")); T.eq("FR(I cannot believe you came here)", translatedOf(upd, "I cannot"))
        T.eq(g.revision, cache.revisionOf(key)); T.eq("FR(Please take me as your apprenti)", translatedOf(PageOutcome.Done(cache.get(key)!!, true), "Please"))
        calls.clear()
        val again = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(calls.isEmpty() && again.refreshed == 0, "la page est à jour : $calls"); T.eq("FR(Please take me as your apprenti)", translatedOf(again, "Please"))
        // une page neuve est traduite avec le glossaire tel qu'il est, et porte sa version
        val c2 = ResultCache(tdir()); val p2 = PagePipeline(c2, FakeOcr(ocrLines), eng, "e", { g })
        val n = p2.process(img, "en", "fr") as PageOutcome.Done
        T.eq("FR(Please take me as your apprenti)", translatedOf(n, "Please")); T.eq(g.revision, c2.revisionOf(key))
        calls.clear(); p2.process(img, "en", "fr"); T.ok(calls.isEmpty(), "pas de retraduction d'une page traduite avec ce glossaire")
    }
    T.test("pipeline: une page en cache d'avant le suivi des versions n'est retraduite que pour les termes ajoutés ensuite") {
        val calls = ArrayList<List<String>>()
        val eng = TranslationEngine { t, _, _ -> calls.add(t); t.map { "FR($it)" } }
        val g = Glossary.parse("T\tdisciple\tapprenti\n")                      // ancien glossaire : terme sans version
        val cache = ResultCache(tdir()); val key = CacheKey(sha256Hex(img), "en", "fr", "e")
        cache.put(key, page)                                                  // enregistrée avant ce suivi : version 0
        val p = PagePipeline(cache, FakeOcr(ocrLines), eng, "e", { g })
        val a = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(a.refreshed == 0 && calls.isEmpty(), "le terme ancien ne fait rien : $calls"); T.eq(0L, cache.revisionOf(key))
        g.addTerm("believe", "croire")
        val b = p.process(img, "en", "fr") as PageOutcome.Done
        T.eq(1, b.refreshed); T.eq(listOf(listOf("I cannot ZQ0X you came here")), calls)
        T.eq("FR(I cannot croire you came here)", b.result.blocks[1].translated); T.eq("FR(Please take me as your disciple)", b.result.blocks[0].translated, "l'ancien terme n'a rien changé ailleurs")
    }
    T.test("pipeline: une retraduction qui échoue laisse la page telle quelle et sera retentée ; une correction exacte n'est pas retraduite") {
        var fail = true
        val eng = TranslationEngine { t, _, _ -> if (fail) error("modèle absent") else t.map { "FR($it)" } }
        val g = Glossary(); val cache = ResultCache(tdir()); val key = CacheKey(sha256Hex(img), "en", "fr", "e")
        cache.put(key, page, 0L)
        val p = PagePipeline(cache, FakeOcr(ocrLines), eng, "e", { g })
        g.addTerm("disciple", "apprenti")
        val a = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(a.fromCache && a.refreshed == 0, "page gardée"); T.eq("FR(Please take me as your disciple)", a.result.blocks[0].translated); T.eq(0L, cache.revisionOf(key), "version inchangée : sera retentée")
        fail = false
        val b = p.process(img, "en", "fr") as PageOutcome.Done
        T.eq(2, b.refreshed, "les deux textes qui contiennent le terme"); T.eq("FR(The apprenti will not see anyone)", b.result.blocks[2].translated); T.eq(g.revision, cache.revisionOf(key))
        // correction exacte : jamais retraduite
        val cache2 = ResultCache(tdir()); cache2.put(key, page, 0L)
        val g2 = Glossary(corrections = mapOf("The Disciple will not see anyone" to "Fixé")); g2.addTerm("disciple", "apprenti")
        val c = PagePipeline(cache2, FakeOcr(ocrLines), eng, "e", { g2 }).process(img, "en", "fr") as PageOutcome.Done
        T.eq(1, c.refreshed); T.eq("FR(The Disciple will not see anyone)", c.result.blocks[2].translated, "texte corrigé : intact")
    }
    T.test("pipeline: un terme propre à une œuvre ne retraduit que les pages de cette œuvre") {
        val calls = ArrayList<List<String>>()
        val eng = TranslationEngine { t, _, _ -> calls.add(t); t.map { "FR($it)" } }
        val base = Glossary(); var series = "s|a"
        val cache = ResultCache(tdir()); val key = CacheKey(sha256Hex(img), "en", "fr", "e")
        cache.put(key, page, 0L)
        val p = PagePipeline(cache, FakeOcr(ocrLines), eng, "e", { base.forSeries(series) })
        base.addSeriesTerm("s|a", "disciple", "apprenti")
        series = "s|b"
        val other = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(other.refreshed == 0 && calls.isEmpty(), "autre œuvre : rien à refaire")
        series = "s|a"
        val mine = p.process(img, "en", "fr") as PageOutcome.Done
        T.eq(2, mine.refreshed)
    }
}
