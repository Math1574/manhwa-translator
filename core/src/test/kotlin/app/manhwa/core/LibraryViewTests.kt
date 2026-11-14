package app.manhwa.core

private const val ZULU = "https://s.test/comics/zulu-quest-a1b2c3/chapter/"
private const val ALPHA = "https://s.test/comics/alpha-saga-d4e5f6/chapter/"
private const val MID = "https://s.test/comics/mid-tale-0a1b2c/chapter/"
private const val K_ZULU = "s.test|zulu quest"
private const val K_ALPHA = "s.test|alpha saga"
private const val K_MID = "s.test|mid tale"

/** Trois œuvres aux dates, avancements et chapitres connus : Zulu (3 chapitres, 2 lus, le plus récent), Mid (1 chapitre), Alpha (3 chapitres lus, le plus ancien). */
private fun sample(): BrowsingLibrary {
    var l = BrowsingLibrary()
    l = l.visit(ZULU + 1, "", 1000).visit(ZULU + 2, "", 2000).visit(ZULU + 3, "", 9000)
    l = l.visit(ALPHA + 1, "", 100).visit(ALPHA + 2, "", 200).visit(ALPHA + 3, "", 300)
    l = l.visit(MID + 1, "", 5000)
    return l.markDone(ZULU + 1, true, 1).markDone(ZULU + 2, true, 1).markDone(ALPHA + 1, true, 1).markDone(ALPHA + 2, true, 1).markDone(ALPHA + 3, true, 1)
}

private fun titles(l: List<SeriesEntry>) = l.map { it.title }

/** Bibliothèque : recherche sans accents, filtres, tris, statut « terminée » et titres simples. */
fun libraryViewTests() {
    T.test("recherche : majuscules, accents, ligatures et ponctuation ne comptent pas") {
        T.eq("eleve oeuvre test", LibraryView.fold("  Élève ! Œuvre-Test  "))
        T.eq("ca c est l ete", LibraryView.fold("Ça, c'est l'été"))
        T.eq("", LibraryView.fold("  ...  "))
        T.ok(LibraryView.matches("OEUVRE élève", "Une Œuvre", "pour l'eleve"), "mots trouvés dans des champs différents")
        T.ok(!LibraryView.matches("oeuvre absent", "Une Œuvre"), "un mot manquant suffit à écarter")
        T.ok(LibraryView.matches("   ", "n'importe quoi") && LibraryView.matches("", ""), "recherche vide : tout convient")
        T.ok(LibraryView.matches("Solo Lev", "Solo Leveling"), "début de mot")
    }

    T.test("liste : filtre par statut et favoris, tri récent, titre ou chapitres lus") {
        var l = sample()
        val all = l.series()
        T.eq(listOf("Zulu Quest", "Mid Tale", "Alpha Saga"), titles(LibraryView.series(all, "", SeriesFilter.ALL, SeriesSort.RECENT)), "récent")
        T.eq(listOf("Alpha Saga", "Mid Tale", "Zulu Quest"), titles(LibraryView.series(all, "", SeriesFilter.ALL, SeriesSort.TITLE)), "titre")
        T.eq(listOf("Alpha Saga", "Zulu Quest", "Mid Tale"), titles(LibraryView.series(all, "", SeriesFilter.ALL, SeriesSort.READ)), "chapitres lus")
        l = l.toggleSeriesFavorite(K_MID, "Mid Tale", 1).setFinished(K_ALPHA, true, 400)
        val s = l.series()
        T.eq(listOf("Mid Tale"), titles(LibraryView.series(s, "", SeriesFilter.FAVORITES, SeriesSort.RECENT)))
        T.eq(listOf("Alpha Saga"), titles(LibraryView.series(s, "", SeriesFilter.FINISHED, SeriesSort.RECENT)))
        T.eq(listOf("Zulu Quest", "Mid Tale"), titles(LibraryView.series(s, "", SeriesFilter.ONGOING, SeriesSort.RECENT)))
        T.eq(listOf("Zulu Quest"), titles(LibraryView.series(s, "QUEST", SeriesFilter.ALL, SeriesSort.RECENT)), "recherche dans le titre")
        T.eq(3, LibraryView.series(s, "s.test", SeriesFilter.ALL, SeriesSort.RECENT).size, "recherche dans le site")
        T.eq(emptyList<SeriesEntry>(), LibraryView.series(s, "quest", SeriesFilter.FINISHED, SeriesSort.RECENT), "recherche et filtre se cumulent")
    }

    T.test("liste : le tri par titre ignore accents et majuscules, les ex æquo vont du plus récent au plus ancien") {
        val l = BrowsingLibrary().visit("https://s.test/comics/zebre-aaaaaa/chapter/1", "", 10).visit("https://s.test/comics/Ecole-bbbbbb/chapter/1", "", 20)
            .visit("https://s.test/comics/abeille-cccccc/chapter/1", "", 30)
        T.eq(listOf("Abeille", "Ecole", "Zebre"), titles(LibraryView.series(l.series(), "", SeriesFilter.ALL, SeriesSort.TITLE)))
        val tie = BrowsingLibrary().visit("https://a.test/comics/same-name-aaaaaa/chapter/1", "", 10).visit("https://b.test/comics/same-name-bbbbbb/chapter/1", "", 20)
        T.eq(listOf("b.test", "a.test"), LibraryView.series(tie.series(), "", SeriesFilter.ALL, SeriesSort.TITLE).map { it.host }, "même titre sur deux sites : le plus récent d'abord")
        T.eq(SeriesSort.RECENT, SeriesSort.of("inconnu")); T.eq(SeriesSort.TITLE, SeriesSort.of("title")); T.eq(SeriesSort.RECENT, SeriesSort.of(null))
    }

    T.test("liste : les pastilles comptent selon la recherche en cours") {
        val l = sample().toggleSeriesFavorite(K_MID, "Mid Tale", 1).setFinished(K_ALPHA, true, 400)
        val all = LibraryView.counts(l.series(), "")
        T.eq(3, all[SeriesFilter.ALL]); T.eq(2, all[SeriesFilter.ONGOING]); T.eq(1, all[SeriesFilter.FINISHED]); T.eq(1, all[SeriesFilter.FAVORITES])
        val q = LibraryView.counts(l.series(), "saga")
        T.eq(1, q[SeriesFilter.ALL]); T.eq(0, q[SeriesFilter.ONGOING]); T.eq(1, q[SeriesFilter.FINISHED]); T.eq(0, q[SeriesFilter.FAVORITES])
    }

    T.test("statut : terminée se retient, relire un ancien chapitre ou ouvrir la page de l'œuvre n'y change rien, un nouveau chapitre la remet en cours") {
        var l = sample()
        T.ok(!l.isFinished(K_ALPHA) && l.series().none { it.finished }, "en cours par défaut")
        l = l.setFinished(K_ALPHA, true, 500)
        T.ok(l.isFinished(K_ALPHA) && l.series().first { it.key == K_ALPHA }.finished && !l.isFinished(K_ZULU), "terminée")
        l = l.visit(ALPHA + 1, "", 600)
        T.ok(l.isFinished(K_ALPHA), "relire un ancien chapitre")
        l = l.visit("https://s.test/comics/alpha-saga-d4e5f6", "", 700)
        T.ok(l.isFinished(K_ALPHA), "la page de l'œuvre n'est pas un chapitre")
        l = l.visit(ALPHA + 4, "", 800)
        T.ok(!l.isFinished(K_ALPHA) && !l.series().first { it.key == K_ALPHA }.finished, "un nouveau chapitre la remet en cours")
        l = l.setFinished(K_ALPHA, true, 900)
        T.ok(l.isFinished(K_ALPHA), "terminée à nouveau"); T.eq(4, l.finishedSeries.single().chaptersAtMark, "avec les quatre chapitres connus")
        l = l.setFinished(K_ALPHA, false, 950)
        T.ok(!l.isFinished(K_ALPHA) && l.finishedSeries.isEmpty(), "remise en cours")
    }

    T.test("statut : enregistré avec la bibliothèque, oublié avec l'œuvre, fichier ancien ou abîmé toléré") {
        val l = sample().setFinished(K_ALPHA, true, 500).setFinished("autre|clé\tbizarre", true, 600)
        T.eq(l, BrowsingLibrary.parse(l.serialize()), "relecture identique")
        T.eq(0, BrowsingLibrary.parse(sample().serialize()).finishedSeries.size, "ancien fichier sans statut")
        T.eq(0, BrowsingLibrary.parse("D\tcourte\nD\tclé\tpas-un-nombre\t3\nD\tclé\t5\tpas-un-nombre\n").finishedSeries.size, "lignes abîmées sautées")
        T.eq(emptyList<SeriesFinished>(), l.removeSeries(K_ALPHA).finishedSeries.filter { it.key == K_ALPHA }, "oubliée avec l'œuvre")
        T.eq(BrowsingLibrary.MAX_FINISHED, (1..BrowsingLibrary.MAX_FINISHED + 5).fold(BrowsingLibrary()) { a, i -> a.setFinished("k$i", true, i.toLong()) }.finishedSeries.size, "borné")
    }

    T.test("chapitres : un nombre cherche le début du numéro, des mots cherchent dans le libellé") {
        val chs = listOf(12.0, 12.5, 112.0, 120.0, 3.0).map { ChapterEntry("u$it", "Chapitre " + LibraryView.numberText(it), it, 0, 1, 0, false) } + ChapterEntry("p", "Prologue", null, 0, 1, 0, false)
        fun found(q: String) = LibraryView.chapters(chs, q).map { it.label }
        T.eq(listOf("Chapitre 12", "Chapitre 12.5", "Chapitre 120"), found("12"), "12 trouve 12, 12.5 et 120 mais pas 112")
        T.eq(listOf("Chapitre 12.5"), found("12.5")); T.eq(listOf("Chapitre 12.5"), found("12,5"), "virgule décimale")
        T.eq(listOf("Prologue"), found("PROLOG")); T.eq(listOf("Chapitre 3"), found("chapitre 3"))
        T.eq(chs.size, found("").size); T.eq(chs.size, found("  ").size); T.eq(emptyList<String>(), found("zzz"))
        T.eq("12", LibraryView.numberText(12.0)); T.eq("12.5", LibraryView.numberText(12.5))
    }

    T.test("titres simples : l'œuvre et le chapitre viennent de l'adresse, pas du titre brut de la page") {
        T.eq(
            PageTitles.Simple("The Heavenly Demon Wants A Quiet Life", "Chapitre 8"),
            PageTitles.simple("https://asurascans.com/comics/the-heavenly-demon-wants-a-quiet-life-11fe7da3/chapter/8", "The Heavenly Demon Wants A Quiet Life Chapter 8 - Asura Scans"),
        )
        T.eq(PageTitles.Simple("Solo Leveling", "Page de l'œuvre"), PageTitles.simple("https://x.test/series/solo-leveling-9f8e7d6c", "Solo Leveling | X"))
        T.eq(PageTitles.Simple("Zulu Quest", "Chapitre 3"), PageTitles.simple(ZULU + 3, ""), "titre de page vide")
    }

    T.test("titres simples : sans œuvre dans l'adresse, on retire le nom du site et les mentions d'usage") {
        T.eq("Prologue — Le Début", PageTitles.clean("Prologue - Le Début | Mon Site - Lire en ligne", "x.test"))
        T.eq("Spider-Man: Chapter 3", PageTitles.clean("Spider-Man: Chapter 3 | MangaSite", "www.mangasite.test"), "le tiret d'un nom n'est pas un séparateur")
        T.eq("Spider-Man: Chapter 3", PageTitles.clean("MangaSite.com » Spider-Man: Chapter 3 » Read Online Free", "mangasite.com"), "nom du site avec extension et fil d'Ariane")
        T.eq("", PageTitles.clean("Asura Scans", "asurascans.com"), "rien d'autre que le nom du site")
        T.eq("asurascans.com", PageTitles.simple("https://www.asurascans.com/", "Asura Scans").main, "sans titre utile, le site")
        val long = PageTitles.clean("mot ".repeat(60), "x.test")
        T.ok(long.length <= 80 && long.endsWith("…"), "titre raccourci : ${long.length}")
        // le chapitre n'est répété que s'il manque au titre
        T.eq(PageTitles.Simple("Spider-Man: Chapter 3", null), PageTitles.simple("https://mangasite.test/p/1", "Spider-Man: Chapter 3 | MangaSite"))
        T.eq(PageTitles.Simple("Prologue", "Chapitre 12"), PageTitles.simple("https://mangasite.test/p/chapter-12", "Prologue | MangaSite"))
        T.eq("asurascans.com", PageTitles.site("https://www.asurascans.com/comics/x"))
    }

    T.test("réglage : le tri de la bibliothèque est retenu, une valeur inconnue retombe sur « récent »") {
        val s = AppSettings().copy(librarySort = "title")
        T.eq("title", AppSettings.parse(s.serialize()).librarySort)
        T.eq("recent", AppSettings.parse(s.serialize().replace("title", "nimporte")).librarySort)
        T.eq("recent", AppSettings.parse(null).librarySort)
    }
}
