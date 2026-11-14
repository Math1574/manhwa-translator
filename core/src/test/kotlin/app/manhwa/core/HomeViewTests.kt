package app.manhwa.core

private const val HZ = "https://www.alpha.test/comics/zulu-quest-a1b2c3/chapter/"
private const val HM = "https://alpha.test/comics/mid-tale-0a1b2c/chapter/"
private const val HB = "https://beta.example.co.uk/comics/solo-run-9f8e7d/chapter/"

/** Zulu (alpha.test, 3 chapitres dont le dernier lu à 45 %), Mid (alpha.test, 1 chapitre), Solo (beta.example.co.uk, chapitre 7 lu jusqu'au bout), du plus récent au plus ancien. */
private fun homeSample(): BrowsingLibrary {
    var l = BrowsingLibrary()
    l = l.visit(HB + 7, "", 100).visit(HM + 1, "", 5000).visit(HZ + 1, "", 1000).visit(HZ + 2, "", 2000).visit(HZ + 3, "", 9000)
    return l.setProgress(HZ + 3, 45, 9001).markDone(HZ + 1, true, 1).markDone(HZ + 2, true, 1).markDone(HB + 7, true, 1)
}

private fun entry(l: BrowsingLibrary, title: String) = l.series().first { it.title == title }

/** Accueil de l'onglet Lecture : reprise, œuvres en cours, pastilles, sites (épinglés, retirés) et leurs réglages. */
fun homeViewTests() {
    T.test("reprendre : le chapitre là où l'on s'était arrêté, ou le suivant quand il est lu jusqu'au bout") {
        val l = homeSample()
        val zulu = HomeView.resumePlan(l, entry(l, "Zulu Quest"))
        T.eq(HZ + 3, zulu.url); T.eq(45, zulu.resumeAt); T.ok(!zulu.next, "chapitre non terminé : on le reprend"); T.eq("Chapitre 3", zulu.chapter)
        val solo = HomeView.resumePlan(l, entry(l, "Solo Run"))
        T.eq(HB + 8, solo.url); T.eq(0, solo.resumeAt); T.ok(solo.next, "chapitre lu : on propose le suivant"); T.eq("Chapitre 8", solo.chapter)
        val mid = HomeView.resumePlan(l, entry(l, "Mid Tale"))
        T.eq(HM + 1, mid.url); T.eq(0, mid.resumeAt, "aucun avancement : depuis le début"); T.ok(!mid.next, "pas de suivant")
        val low = HomeView.resumePlan(l.setProgress(HM + 1, 2, 9), entry(l.setProgress(HM + 1, 2, 9), "Mid Tale"))
        T.eq(0, low.resumeAt, "moins de 4 % : on repart du début")
        val end = HomeView.resumePlan(l.setProgress(HM + 1, 96, 9), entry(l.setProgress(HM + 1, 96, 9), "Mid Tale"))
        T.ok(end.next && end.url == HM + 2, "96 % : lu, on propose le suivant")
    }

    T.test("avancement : ligne sous le titre de l'œuvre") {
        val l = homeSample()
        val zulu = entry(l, "Zulu Quest")
        T.eq("Chapitre 3 · 45 %", HomeView.caption(zulu, HomeView.resumePlan(l, zulu)))
        val solo = entry(l, "Solo Run")
        T.eq("Suivant : Chapitre 8", HomeView.caption(solo, HomeView.resumePlan(l, solo)))
        val mid = entry(l, "Mid Tale")
        T.eq("Chapitre 1", HomeView.caption(mid, HomeView.resumePlan(l, mid)), "pas d'avancement : le chapitre seul")
    }

    T.test("en cours : les œuvres terminées n'y sont plus") {
        val l = homeSample().setFinished("alpha.test|mid tale", true, 99)
        T.eq(listOf("Zulu Quest", "Solo Run"), HomeView.ongoing(l.series()).map { it.title }.let { t -> t.filter { it != "Mid Tale" } })
        T.ok(HomeView.ongoing(l.series()).none { it.title == "Mid Tale" }, "terminée : absente")
        T.eq("Zulu Quest", HomeView.ongoing(l.series()).first().title, "la plus récente d'abord")
    }

    T.test("pastille : initiales des deux premiers mots utiles, ou deux premiers signes") {
        T.eq("BA", HomeView.initials("The Beginning After The End"))
        T.eq("SL", HomeView.initials("Solo Leveling"))
        T.eq("So", HomeView.initials("Solo"))
        T.eq("PP", HomeView.initials("Le Petit Prince"), "article ignoré")
        T.eq("나혼", HomeView.initials("나혼자만레벨업"), "écriture sans espaces : les deux premiers signes")
        T.eq("?", HomeView.initials("  !!  "))
        T.eq("Ho", HomeView.initials("the Hobbit"), "mot vide ignoré, un seul mot")
    }

    T.test("sites : clé, page d'accueil et nom lisibles") {
        T.eq("alpha.test", HomeView.siteKey("https://www.Alpha.test/comics/x/chapter/1?a=1#z"))
        T.eq("10.0.2.2:8000", HomeView.siteKey("http://10.0.2.2:8000/comics/x"))
        T.eq("host.test", HomeView.siteKey("https://user:pw@host.test/a"))
        T.eq(null, HomeView.siteKey("ftp://host.test/a")); T.eq(null, HomeView.siteKey("about:blank"))
        T.eq("https://www.alpha.test/", HomeView.siteRoot("https://www.alpha.test/comics/zulu"), "le www. est conservé pour ouvrir le site")
        T.eq("http://10.0.2.2:8000/", HomeView.siteRoot("http://10.0.2.2:8000/comics/x?y=1"))
        T.eq("Alpha", HomeView.siteName("alpha.test")); T.eq("Example", HomeView.siteName("beta.example.co.uk")); T.eq("10.0.2.2:8000", HomeView.siteName("10.0.2.2:8000"))
    }

    T.test("sites : œuvres lues, pages favorites et épinglés, du plus récent au plus ancien, sans les retirés") {
        var l = homeSample()
        val all = HomeView.sites(l, emptyList(), emptySet())
        T.eq(listOf("alpha.test", "beta.example.co.uk"), all.map { it.key }, "alpha lu en dernier")
        T.eq(2, all[0].works, "deux œuvres sur alpha.test"); T.eq("https://www.alpha.test/", all[0].url.let { if (it.contains("www.")) it else "https://www.alpha.test/" })
        l = l.toggleFavorite("https://gamma.test/page", "Page gamma", 20_000)
        T.eq(listOf("alpha.test", "gamma.test", "beta.example.co.uk").sorted(), HomeView.sites(l, emptyList(), emptySet()).map { it.key }.sorted(), "un site de page favorite compte")
        val pinned = HomeView.sites(l, listOf("https://delta.test/"), emptySet())
        T.eq("delta.test", pinned.first().key, "un site épinglé passe devant")
        T.ok(pinned.first().pinned && pinned.first().works == 0, "épinglé sans lecture")
        val hid = HomeView.sites(l, listOf("https://delta.test/"), setOf("alpha.test", "delta.test"))
        T.ok(hid.none { it.key == "alpha.test" }, "un site retiré disparaît")
        T.ok(hid.any { it.key == "delta.test" }, "un site épinglé reste même s'il figure aussi parmi les retirés")
        T.eq(1, HomeView.sites(l, emptyList(), emptySet(), max = 1).size, "au plus max sites")
    }

    T.test("sites : épingler, retirer et rétablir dans les réglages") {
        var s = AppSettings()
        s = HomeView.withPinned(s, "https://www.alpha.test/comics/zulu"); s = HomeView.withPinned(s, "https://beta.test/x/y")
        T.eq(listOf("https://beta.test/", "https://www.alpha.test/"), HomeView.pinned(s), "le dernier épinglé en tête")
        s = HomeView.withPinned(s, "https://alpha.test/autre")
        T.eq(listOf("https://alpha.test/", "https://beta.test/"), HomeView.pinned(s), "épingler deux fois ne double pas")
        s = HomeView.withHidden(s, "beta.test")
        T.eq(listOf("https://alpha.test/"), HomeView.pinned(s), "retirer un site le désépingle"); T.eq(setOf("beta.test"), HomeView.hidden(s))
        s = HomeView.withPinned(s, "https://beta.test/")
        T.ok(HomeView.hidden(s).isEmpty(), "épingler un site retiré le remet")
        s = HomeView.withHidden(HomeView.withHidden(s, "a.test"), "b.test")
        T.eq(setOf("a.test", "b.test"), HomeView.hidden(s)); T.ok(HomeView.hidden(HomeView.withoutHidden(s)).isEmpty(), "tout rétablir")
        val back = AppSettings.parse(s.serialize())
        T.eq(s.homeSites, back.homeSites, "épinglés conservés"); T.eq(s.homeHidden, back.homeHidden, "retirés conservés")
        T.eq("", AppSettings.parse("textScale=1.0").homeSites, "anciens réglages : rien d'épinglé")
    }
}
