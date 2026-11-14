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
        val min = 60_000L
        val h = 3600_000L
        var b = CoverBook()
        T.ok(b.wanted("k", 0), "jamais essayée : on cherche")
        b = b.failed("k", 1000)
        T.ok(!b.wanted("k", 1000 + 9 * min), "premier échec récent : on attend un peu")
        T.ok(b.wanted("k", 1000 + 10 * min), "premier échec : on réessaie vite (le site ou le réseau n'était peut-être pas prêt)")
        b = b.failed("k", 2000)
        T.ok(!b.wanted("k", 2000 + 29 * min) && b.wanted("k", 2000 + 30 * min), "deuxième échec : une demi-heure")
        b = b.failed("k", 3000).failed("k", 4000)
        T.ok(!b.wanted("k", 4000 + 5 * h) && b.wanted("k", 4000 + 6 * h), "quatrième échec : six heures")
        b = b.failed("k", 5000)
        T.ok(!b.wanted("k", 5000 + 23 * h) && b.wanted("k", 5000 + 24 * h), "ensuite : une fois par jour")
        for (i in 6..CoverBook.MAX_TRIES) b = b.failed("k", 1000L * i)
        T.ok(!b.wanted("k", Long.MAX_VALUE / 2), "trop d'échecs : on renonce")
        T.ok(b.again("k").wanted("k", 0), "sur demande de l'utilisateur : on cherche de nouveau tout de suite")
        T.ok(CoverBook().failed("k", 1000).again("k").wanted("k", 1001), "même un échec tout récent")
        val s = CoverBook().saved("k2", OG, 5)
        T.ok(s.accepted && s.book.has("k2") && s.book.urlOf("k2") == OG && !s.book.wanted("k2", Long.MAX_VALUE / 2), "trouvée : gardée et plus cherchée")
        T.ok(s.book.again("k2").has("k2"), "une illustration trouvée n'est pas perdue par une nouvelle demande")
        T.ok(!s.book.has("autre") && s.book.urlOf("autre") == null, "inconnue")
    }

    T.test("registre : la même image pour deux œuvres est celle du site, pour aucune") {
        val a = CoverBook().saved("s.test|alpha saga", "https://s.test/default.jpg", 1)
        T.ok(a.accepted, "la première la garde")
        val b = a.book.saved("s.test|zulu quest", "https://s.test/default.jpg", 2)
        T.ok(!b.accepted && b.dropped == listOf("s.test|alpha saga"), "la seconde la fait retirer aux deux")
        T.ok(!b.book.has("s.test|alpha saga") && !b.book.has("s.test|zulu quest"), "plus aucune des deux")
        T.ok(!b.book.wanted("s.test|alpha saga", 3) && b.book.wanted("s.test|alpha saga", 2 + 10 * 60_000L), "l'œuvre qui l'avait est cherchée de nouveau un peu plus tard (l'adresse bannie est alors écartée)")
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
    T.test("illustration : titres équivalents et adresses qui portent le titre") {
        T.ok(CoverPick.sameTitle("Solo Leveling: Ragnarok", "solo-leveling ragnarok") && CoverPick.sameTitle("The Beginning After The End", "Beginning After the End"), "mêmes lettres, article initial")
        T.ok(CoverPick.sameTitle("Is It Bad That The Main Character S A Roleplayer", "Is It Bad That the Main Character's a Roleplayer?"), "l'apostrophe d'un titre devient une espace dans une adresse")
        T.ok(!CoverPick.sameTitle("Solo Leveling", "Solo Leveling: Ragnarok") && !CoverPick.sameTitle("Ab", "Ab"), "une suite n'est pas l'œuvre ; titre trop court")
        val t = "Logging Out Is Too Hard"
        T.ok(CoverPick.urlNamesTitle("https://cdn.asurascans.test/asura-images/covers/logging-out-is-too-hard.5c1851-400.webp", t), "titre, code, taille, extension")
        T.ok(CoverPick.urlNamesTitle("https://x.test/uploads/Logging%20Out%20Is%20Too%20Hard.jpg", t), "adresse encodée")
        T.ok(!CoverPick.urlNamesTitle("https://x.test/covers/solo-leveling-ragnarok-ab12cd.jpg", "Solo Leveling"), "une suite : d'autres mots suivent le titre")
        T.ok(CoverPick.urlNamesTitle("https://x.test/covers/solo-leveling-ab12cd.jpg", "Solo Leveling"), "le titre seul, suivi d'un code")
        T.ok(!CoverPick.urlNamesTitle("https://x.test/covers/abc.jpg", t) && !CoverPick.urlNamesTitle("https://x.test/a.jpg", "??"), "autre image ; titre inutilisable")
    }

    T.test("illustration : classement, l'image du site ne passe pas devant celle de l'œuvre") {
        val title = "Logging Out Is Too Hard"
        val cover = CoverImg("https://cdn.s.test/asura-images/covers/logging-out-is-too-hard.5c1851-400.webp", alt = "Logging Out Is Too Hard", w = 0, h = 0)
        val c = CoverCandidates(shared = listOf("https://s.test/images/og-image.png"), images = listOf(CoverImg("https://cdn.s.test/covers/other-story.1a2b3c-400.webp", alt = "Other Story"), cover))
        T.eq(listOf(cover.src), CoverPick.rank(c, title).map { it.url }, "l'image de partage générique est écartée, la vignette d'une autre œuvre aussi")
        val both = CoverCandidates(shared = listOf("https://cdn.s.test/media/77/conversions/abc-optimized.webp"), images = listOf(cover))
        T.eq(listOf(cover.src, "https://cdn.s.test/media/77/conversions/abc-optimized.webp"), CoverPick.rank(both, title).map { it.url }, "l'image qui porte le titre passe devant celle du partage ; l'autre reste en secours")
        T.ok(CoverPick.rank(both, title).first().evidenced && !CoverPick.rank(both, title).last().evidenced, "preuve du titre notée")
        T.eq(emptyList<String>(), CoverPick.rank(CoverCandidates(shared = listOf("https://s.test/img/banner.jpg", "https://s.test/share.png")), title).map { it.url }, "des images de site seules : rien")
        T.eq(listOf("https://s.test/uploads/cover-9.jpg"), CoverPick.rank(CoverCandidates(shared = listOf("https://s.test/uploads/cover-9.jpg")), title).map { it.url }, "une image de partage ordinaire, sans titre dans l'adresse : retenue")
        val sequel = CoverImg("https://s.test/u/1.jpg", alt = "Solo Leveling: Ragnarok", w = 300, h = 450)
        val own = CoverImg("https://s.test/u/2.jpg", alt = "Solo Leveling", w = 300, h = 450)
        T.eq("https://s.test/u/2.jpg", CoverPick.rank(CoverCandidates(images = listOf(sequel, own)), "Solo Leveling").first().url, "le titre exact passe devant la suite")
        T.eq(emptyList<String>(), CoverPick.rank(CoverCandidates(images = listOf(sequel)), "Solo Leveling: Ragnarok Gaiden").map { it.url }, "un titre plus long que celui de l'image : pas une preuve")
        T.ok(CoverPick.rank(CoverCandidates(shared = listOf(OG, OG)), "Solo Leveling").size == 1, "sans doublon")
    }

    T.test("illustration : forme de l'image téléchargée, page d'attente d'un site protégé") {
        T.ok(CoverPick.fits(600, 900, false) && CoverPick.fits(500, 500, false) && CoverPick.fits(650, 500, false), "en hauteur, carrée ou à peine large")
        T.ok(!CoverPick.fits(1200, 630, false), "un bandeau large sans le titre n'est pas une illustration")
        T.ok(CoverPick.fits(1200, 630, true), "mais une image large qui porte le titre l'est")
        T.ok(!CoverPick.fits(60, 900, true) && !CoverPick.fits(1, 1, true), "toujours refusés : trait, pixel")
        T.ok(CoverPick.looksLikeChallenge("Just a moment...") && CoverPick.looksLikeChallenge("Un instant…") && CoverPick.looksLikeChallenge("Attention Required! | Cloudflare"), "pages d'attente")
        T.ok(!CoverPick.looksLikeChallenge("Logging Out Is Too Hard - Asura Scans"), "page normale")
    }

    T.test("illustration : couverture trouvée dans l'annuaire, seulement si le titre est exactement celui de l'œuvre") {
        val json = """{"data":{"Page":{"media":[
            {"format":"NOVEL","countryOfOrigin":"KR","popularity":9000,"title":{"romaji":"Solo Leveling","english":"Solo Leveling","native":"나 혼자만 레벨업"},"synonyms":[],"coverImage":{"extraLarge":"https://s4.anilist.co/novel.jpg","large":"https://s4.anilist.co/novel-l.jpg"}},
            {"format":"MANGA","countryOfOrigin":"KR","popularity":50000,"title":{"romaji":"Na Honjaman Level Up","english":"Solo Leveling","native":"나 혼자만 레벨업"},"synonyms":["Only I Level Up"],"coverImage":{"extraLarge":"https://s4.anilist.co/file/media/manga/cover/large/bx105398.jpg","large":"https://s4.anilist.co/file/media/manga/cover/medium/bx105398.jpg"}},
            {"format":"MANGA","countryOfOrigin":"KR","popularity":900,"title":{"romaji":"Solo Leveling: Ragnarok","english":null,"native":null},"synonyms":[],"coverImage":{"large":"https://s4.anilist.co/file/media/manga/cover/large/bx222.jpg"}},
            {"format":"MANGA","countryOfOrigin":"JP","popularity":10,"title":{"romaji":"Autre","english":null,"native":null},"synonyms":[],"coverImage":{"large":"https://s4.anilist.co/default.jpg"}}
        ]}}}"""
        T.eq("https://s4.anilist.co/file/media/manga/cover/large/bx105398.jpg", AniListCovers.cover(json, "Solo Leveling"), "le roman est écarté, la plus grande image prise")
        T.eq("https://s4.anilist.co/file/media/manga/cover/large/bx105398.jpg", AniListCovers.cover(json, "Only I Level Up"), "un titre alternatif convient")
        T.eq("https://s4.anilist.co/file/media/manga/cover/large/bx222.jpg", AniListCovers.cover(json, "Solo Leveling Ragnarok"), "la suite a sa propre couverture")
        T.eq(null, AniListCovers.cover(json, "Solo"), "un titre incomplet ne convient pas")
        T.eq(null, AniListCovers.cover(json, "Autre"), "une couverture par défaut n'est pas une illustration")
        T.eq(null, AniListCovers.cover("""{"data":{"Page":{"media":[]}}}""", "Solo Leveling"), "aucun résultat")
        T.eq(null, AniListCovers.cover("pas du json", "Solo Leveling"), "réponse illisible")
        T.eq(null, AniListCovers.cover("""{"errors":[{"message":"Too Many Requests"}]}""", "Solo Leveling"), "réponse d'erreur")
        val local = """{"data":{"Page":{"media":[{"format":"MANGA","popularity":1,"title":{"english":"Anilist Only"},"synonyms":[],"coverImage":{"extraLarge":"http://10.0.2.2:8000/c.png"}}]}}}"""
        T.eq(null, AniListCovers.cover(local, "Anilist Only"), "l'annuaire réel ne donne que des adresses en https")
        T.eq("http://10.0.2.2:8000/c.png", AniListCovers.cover(local, "Anilist Only", allowHttp = true), "le faux annuaire des tests peut en donner en http")
        val body = AniListCovers.requestBody("Is \"It\" Bad?\n")
        T.ok(body.startsWith("{\"query\":\"query(") && body.endsWith("\"variables\":{\"s\":\"Is \\\"It\\\" Bad?\\u000a\"}}"), "corps de la demande : titre protégé")
    }
}
