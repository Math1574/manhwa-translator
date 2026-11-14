package app.manhwa.core

private const val OG = "https://cdn.site.test/covers/solo-leveling.jpg"

/** Illustrations des œuvres : quelle image retenir, où la chercher, quand réessayer, et l'image « du site » qui n'est celle d'aucune œuvre. */
fun coverTests() {
    T.test("illustration : adresses acceptables et refusées") {
        T.ok(CoverPick.acceptable(OG), "une image ordinaire convient")
        T.ok(CoverPick.acceptable("https://cdn.site.test/media/12/conversions/cover-optimized.webp"), "webp")
        T.ok(CoverPick.acceptable("http://10.0.2.2:8000/comics/test-series-1a2b3c/cover.jpg"), "http local")
        T.ok(CoverPick.acceptable("https://cdn.site.test/img/iconic-hero.jpg"), "« iconic » n'est pas une icône")
        T.ok(!CoverPick.acceptable("https://site.test/images/logo-og.png"), "logo")
        T.ok(!CoverPick.acceptable("https://site.test/favicon.png"), "favicon")
        T.ok(!CoverPick.acceptable("https://site.test/static/icon.png"), "icône")
        T.ok(!CoverPick.acceptable("https://site.test/img/placeholder.jpg"), "image d'attente")
        T.ok(!CoverPick.acceptable("https://site.test/img/default-og.jpg"), "image par défaut")
        T.ok(!CoverPick.acceptable("https://site.test/a.svg") && !CoverPick.acceptable("https://site.test/a.gif?x=1") && !CoverPick.acceptable("https://site.test/a.ico"), "formats vectoriel, animé, icône")
        T.ok(!CoverPick.acceptable("data:image/png;base64,AAAA") && !CoverPick.acceptable("/uploads/a.jpg") && !CoverPick.acceptable("//cdn.site.test/a.jpg"), "adresse incomplète ou intégrée")
        T.ok(!CoverPick.acceptable("https://a.b"), "adresse trop courte")
    }

    T.test("illustration : la balise de partage passe avant tout, un logo est sauté") {
        val c = CoverCandidates(shared = listOf("https://site.test/img/logo.png", OG, "https://x.test/other.jpg"), structured = listOf("https://x.test/ld.jpg"))
        T.eq(OG, CoverPick.choose(c, "Solo Leveling"))
        T.eq("https://x.test/ld.jpg", CoverPick.choose(CoverCandidates(shared = listOf("https://site.test/logo.png"), structured = listOf("https://x.test/ld.jpg")), "Solo Leveling"), "données structurées ensuite")
    }

    T.test("illustration : sans balise, seule une image qui porte le titre de l'œuvre est retenue") {
        val related = CoverImg("https://x.test/thumb/other-title.jpg", alt = "Another Story", cls = "thumb poster")
        val own = CoverImg("https://x.test/uploads/abc.jpg", alt = "Solo Leveling cover", cls = "", w = 300, h = 450)
        T.eq("https://x.test/uploads/abc.jpg", CoverPick.choose(CoverCandidates(images = listOf(related, own)), "Solo Leveling"), "l'image au bon titre")
        T.eq(null, CoverPick.choose(CoverCandidates(images = listOf(related)), "Solo Leveling"), "une vignette d'une autre œuvre ne convient jamais")
        T.eq(null, CoverPick.choose(CoverCandidates(images = listOf(CoverImg("https://x.test/cover/a.jpg", alt = "", cls = "cover poster", w = 400, h = 600))), "Solo Leveling"), "une classe « cover » seule ne suffit pas")
        T.eq(null, CoverPick.choose(CoverCandidates(), "Solo Leveling"), "rien du tout")
        T.eq(null, CoverPick.choose(CoverCandidates(images = listOf(own)), "??"), "titre inutilisable")
        // plusieurs images au bon titre : la plus grande et la mieux classée
        val small = CoverImg("https://x.test/s.jpg", alt = "Solo Leveling", w = 50, h = 50)
        val big = CoverImg("https://x.test/cover/b.jpg", alt = "Solo Leveling", cls = "cover", w = 300, h = 450)
        T.eq("https://x.test/cover/b.jpg", CoverPick.choose(CoverCandidates(images = listOf(small, big)), "Solo Leveling"))
    }

    T.test("illustration : pages où la chercher") {
        val ch = "https://s.test/comics/zulu-quest-a1b2c3/chapter/3"
        T.eq(listOf("https://s.test/comics/zulu-quest-a1b2c3"), CoverPick.pagesFor(listOf(ch)), "depuis un chapitre")
        T.eq(listOf("https://s.test/comics/zulu-quest-a1b2c3"), CoverPick.pagesFor(listOf(ch, "https://s.test/comics/zulu-quest-a1b2c3/chapter/2?x=1#top")), "sans doublon")
        T.eq(listOf("https://s.test/comics/zulu-quest-a1b2c3/"), CoverPick.pagesFor(listOf(ch, "https://s.test/comics/zulu-quest-a1b2c3/#x")), "la page de l'œuvre lue passe en premier, sans doublon à la barre près")
        T.eq(emptyList<String>(), CoverPick.pagesFor(listOf("https://s.test/", "https://s.test/latest")), "pas de page d'œuvre devinable")
        val three = listOf("https://s.test/manga/aaa-bbb-ccc", "https://s.test/series/aaa-bbb-ccc", "https://s.test/comics/aaa-bbb-ccc")
        T.eq(3, CoverPick.pagesFor(three).size, "trois pages différentes")
        T.eq(three.take(2), CoverPick.pagesFor(three, max = 2), "au plus max")
    }

    T.test("illustration : taille plausible et nom de fichier stable") {
        T.ok(CoverPick.plausible(600, 900) && CoverPick.plausible(1200, 630) && CoverPick.plausible(100, 100), "tailles d'illustration")
        T.ok(!CoverPick.plausible(1, 1) && !CoverPick.plausible(60, 400) && !CoverPick.plausible(2000, 100) && !CoverPick.plausible(100, 900), "pixel, bandeau, trait")
        val a = CoverPick.fileKey("s.test|zulu quest")
        T.eq(a, CoverPick.fileKey("s.test|zulu quest"), "stable")
        T.ok(a != CoverPick.fileKey("s.test|alpha saga") && a.length == 16 && a.all { it in "0123456789abcdef" }, "propre à l'œuvre, caractères sûrs")
    }

    T.test("registre : quand chercher, quand réessayer, quand renoncer") {
        val h = 3600_000L
        var b = CoverBook()
        T.ok(b.wanted("k", 0), "jamais essayée : on cherche")
        b = b.failed("k", 1000)
        T.ok(!b.wanted("k", 1000 + 11 * h), "échec récent : on attend")
        T.ok(b.wanted("k", 1000 + 12 * h), "échec ancien : on réessaie")
        for (i in 2..CoverBook.MAX_TRIES) b = b.failed("k", 1000L * i)
        T.ok(!b.wanted("k", Long.MAX_VALUE / 2), "trop d'échecs : on renonce")
        val s = CoverBook().saved("k2", OG, 5)
        T.ok(s.accepted && s.book.has("k2") && s.book.urlOf("k2") == OG && !s.book.wanted("k2", Long.MAX_VALUE / 2), "trouvée : gardée et plus cherchée")
        T.ok(!s.book.has("autre") && s.book.urlOf("autre") == null, "inconnue")
    }

    T.test("registre : la même image pour deux œuvres est celle du site, pour aucune") {
        val a = CoverBook().saved("s.test|alpha saga", "https://s.test/default.jpg", 1)
        T.ok(a.accepted, "la première la garde")
        val b = a.book.saved("s.test|zulu quest", "https://s.test/default.jpg", 2)
        T.ok(!b.accepted && b.dropped == listOf("s.test|alpha saga"), "la seconde la fait retirer aux deux")
        T.ok(!b.book.has("s.test|alpha saga") && !b.book.has("s.test|zulu quest"), "plus aucune des deux")
        T.ok(!b.book.wanted("s.test|alpha saga", Long.MAX_VALUE / 2) && !b.book.wanted("s.test|zulu quest", Long.MAX_VALUE / 2), "et on ne les cherche plus")
        val c = b.book.saved("s.test|mid tale", "https://s.test/default.jpg", 3)
        T.ok(!c.accepted && !c.book.has("s.test|mid tale"), "la troisième ne la garde pas non plus")
        val d = c.book.saved("s.test|other", "https://s.test/cover-other.jpg", 4)
        T.ok(d.accepted && d.book.has("s.test|other"), "une vraie illustration reste possible")
    }

    T.test("registre : la même œuvre sur deux sites garde son illustration") {
        val a = CoverBook().saved("one.test|solo leveling", OG, 1)
        val b = a.book.saved("two.test|solo leveling", OG, 2)
        T.ok(b.accepted && b.dropped.isEmpty() && b.book.has("one.test|solo leveling") && b.book.has("two.test|solo leveling"), "même titre : même œuvre")
    }

    T.test("registre : oubli d'une œuvre") {
        val b = CoverBook().saved("k", OG, 1).book.forget("k")
        T.ok(!b.has("k") && b.wanted("k", 2), "oubliée : cherchée de nouveau si on la relit")
    }

    T.test("registre : enregistrement et relecture, y compris adresses étranges et lignes abîmées") {
        var b = CoverBook().saved("s.test|a\tb", "https://x.test/a\tb\\c\nd.jpg", 7).book
        b = b.failed("s.test|b", 9)
        val x = b.saved("s.test|dup1", "https://x.test/dflt.jpg", 1).book.saved("s.test|dup2", "https://x.test/dflt.jpg", 2).book
        val r = CoverBook.parse(x.serialize())
        T.eq(x.records, r.records, "enregistrements")
        T.eq(x.banned, r.banned, "adresses bannies")
        T.eq("https://x.test/a\tb\\c\nd.jpg", r.urlOf("s.test|a\tb"))
        val noisy = CoverBook.parse("n'importe quoi\nC\tseul\nC\tk\tu\t1\tx\t3\nB\t\nC\tok\thttps://a.test/i.jpg\t1\t0\t5\n")
        T.eq(listOf("ok"), noisy.records.map { it.key }, "les lignes illisibles sont ignorées")
        T.eq(0, CoverBook.parse(null).records.size + CoverBook.parse("  ").records.size)
    }
}
