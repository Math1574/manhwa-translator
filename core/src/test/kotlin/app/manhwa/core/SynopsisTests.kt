package app.manhwa.core

/** Synopsis des œuvres (bibliothèque) : nettoyage du texte, choix parmi ce que la page annonce, refus des phrases de site, registre, annuaire. */
fun synopsisTests() {
    val title = "Solo Leveling"
    val real = "Ten years ago, \"the Gate\" appeared and connected the real world with the monster world. Sung Jinwoo, the weakest of all hunters, gets a second chance through a mysterious system."

    T.test("synopsis : balises, entités, spoilers, étiquette et lignes de métadonnées sont retirés") {
        val raw = "<p>Synopsis : Sung Jinwoo is the &quot;weakest&quot; hunter&hellip; but he levels up.<br><br>(Source: MangaUpdates)</p>~!He dies in chapter 1.!~\nGenres: Action, Fantasy\nAlternative Titles: Only I Level Up\nShow more"
        T.eq("Sung Jinwoo is the \"weakest\" hunter… but he levels up.", Synopsis.clean(raw))
        T.eq("Une histoire\n\nsur deux paragraphes", Synopsis.clean("Résumé :\nUne histoire\n\n\n\nsur deux paragraphes\n"), "retours à la ligne gardés, lignes vides compressées")
        T.eq("Texte avec des espaces", Synopsis.clean("  Texte   avec des \t espaces  "))
        T.eq("A hero rises. &unknown; stays", Synopsis.clean("A hero rises. &unknown; stays"), "entité inconnue laissée telle quelle")
        T.eq("Café – été", Synopsis.clean("Caf&#233; &ndash; &#xe9;t&#233;"), "entités numériques")
    }

    T.test("synopsis : un texte trop long est coupé à la fin d'une phrase") {
        val sentence = "He fights monsters every single night until the sun comes up. "
        val raw = sentence.repeat(40)
        val c = Synopsis.clean(raw)
        T.ok(c.length <= Synopsis.MAX_CHARS, "longueur ${c.length}")
        T.ok(c.endsWith("up."), "fini par une phrase entière : …${c.takeLast(20)}")
        val noStops = "word ".repeat(400)
        val d = Synopsis.clean(noStops)
        T.ok(d.length <= Synopsis.MAX_CHARS + 1 && d.endsWith("…"), "sans fin de phrase : coupé à un mot avec « … »")
    }

    T.test("synopsis : ce qui n'est pas un résumé est refusé") {
        T.ok(Synopsis.accept(real, title) != null, "un vrai résumé")
        T.eq(null, Synopsis.accept("Solo Leveling", title), "le titre seul")
        T.eq(null, Synopsis.accept("Solo Leveling - Manhwa", title), "le titre et son format")
        T.eq(null, Synopsis.accept("Read Solo Leveling manhwa online for free at AsuraScans", title), "phrase du site (lire en ligne)")
        T.eq(null, Synopsis.accept("Lisez Solo Leveling gratuitement en ligne sur notre site de scans", title), "phrase du site en français")
        T.eq(null, Synopsis.accept("Read the latest chapters of Solo Leveling free online. Updated daily with new releases.", title), "chapitres à lire en ligne")
        T.eq(null, Synopsis.accept("Just a moment... Checking your browser before accessing the website, please wait a few seconds.", title), "page d'attente")
        T.eq(null, Synopsis.accept("Action Fantasy", title), "trop court")
        T.eq(null, Synopsis.accept("Copyright 2026 all rights reserved by the site owners of this page", title), "mentions du site")
        T.ok(Synopsis.accept("A baker opens a cookie shop in a haunted castle and must feed every ghost in town before dawn.", "Ghost Bakery") != null, "le mot « cookie » n'est pas une mention de site")
        T.ok(Synopsis.accept("Read the legend: a boy who was never meant to hold a sword draws the king's blade from the stone.", "Stone Blade") != null, "« Read » dans un vrai résumé")
        T.ok(Synopsis.accept("Tower of God is the story of a boy who enters the Tower to chase after his only friend.", "Tower of God") != null, "un résumé qui commence par le titre")
        T.ok(Synopsis.accept("나 혼자만 레벨업 최약체 헌터 성진우는 시스템의 선택을 받아 강해진다", "나 혼자만 레벨업") != null, "coréen : pas d'espaces entre les mots")
    }

    T.test("synopsis : le texte sous le titre « Synopsis » passe devant la balise de partage ; une phrase de site ne passe pas") {
        val meta = SynopsisText("meta", "Read Solo Leveling manhwa online for free at AsuraScans. Updated daily.")
        val ogCut = SynopsisText("meta", "Ten years ago, \"the Gate\" appeared and connected the real world with the monster world. Sung Jinwoo, the weakest of…")
        val head = SynopsisText("head", real + "\nGenres: Action\nShow more")
        T.eq(real, Synopsis.pick(listOf(meta, ogCut, head), title), "bloc complet sous « Synopsis »")
        T.eq(Synopsis.clean(ogCut.text), Synopsis.pick(listOf(meta, ogCut), title), "seule la balise coupée, sans la phrase du site")
        T.eq(null, Synopsis.pick(listOf(meta), title), "rien que la phrase du site : aucun synopsis")
        T.eq(null, Synopsis.pick(emptyList(), title), "aucun texte")
        val ld = SynopsisText("ld", "A short LD description of the story that is long enough to be a synopsis.")
        val dom = SynopsisText("dom", real)
        T.eq(real, Synopsis.pick(listOf(ld, dom), title), "le bloc de la page passe devant les données structurées")
        T.eq(null, Synopsis.pick(listOf(SynopsisText("dom2", "Great chapter, thanks for the upload everyone!")), title), "un commentaire dans un bloc « description » n'est pas un synopsis")
        T.eq(real, Synopsis.pick(listOf(SynopsisText("dom2", real)), title), "mais un bloc « description » assez long l'est")
    }

    T.test("synopsis : l'annuaire — résumé de l'œuvre au titre exact, le plus populaire, sans les spoilers") {
        val json = """{"data":{"Page":{"media":[
            {"format":"NOVEL","popularity":9000,"title":{"romaji":"Solo Leveling","english":"Solo Leveling","native":null},"synonyms":[],"coverImage":{"large":"https://s4.anilist.co/novel.jpg"},"description":"Le roman."},
            {"format":"MANGA","popularity":50000,"title":{"romaji":"Na Honjaman Level Up","english":"Solo Leveling","native":null},"synonyms":["Only I Level Up"],"coverImage":{"extraLarge":"https://s4.anilist.co/c.jpg"},"description":"10 years ago, a gate opened.<br><br>~!He dies.!~<br>(Source: Webtoon)"},
            {"format":"MANGA","popularity":10,"title":{"romaji":"Solo Leveling: Ragnarok","english":null,"native":null},"synonyms":[],"coverImage":{"large":"https://s4.anilist.co/default.jpg"},"description":"The sequel with a long enough text to be accepted as such."},
            {"format":"MANGA","popularity":5,"title":{"romaji":"Sans Couverture","english":null,"native":null},"synonyms":[],"coverImage":null,"description":"Une œuvre sans couverture mais avec un résumé assez long pour être gardé."}
        ]}}}"""
        T.eq("10 years ago, a gate opened.", Synopsis.clean(AniListCovers.description(json, "Solo Leveling") ?: ""), "résumé de la bande dessinée, sans spoiler ni source")
        T.eq("10 years ago, a gate opened.", Synopsis.clean(AniListCovers.description(json, "Only I Level Up") ?: ""), "titre alternatif")
        T.eq("The sequel with a long enough text to be accepted as such.", Synopsis.clean(AniListCovers.description(json, "Solo Leveling Ragnarok") ?: ""), "la suite a son résumé (sa couverture par défaut est refusée)")
        T.eq(null, AniListCovers.cover(json, "Solo Leveling Ragnarok"), "mais pas de couverture")
        T.ok(AniListCovers.description(json, "Sans Couverture") != null && AniListCovers.cover(json, "Sans Couverture") == null, "résumé sans couverture")
        T.eq(null, AniListCovers.description(json, "Solo"), "titre incomplet")
        T.eq(null, AniListCovers.description("pas du json", "Solo Leveling"), "réponse illisible")
        T.ok(AniListCovers.requestBody("X").contains("description"), "la demande réclame le résumé")
    }

    T.test("synopsis : registre — aller-retour, essais espacés, texte de site écarté") {
        val a = "asura|Solo Leveling"; val b = "asura|Tower of God"; val c = "manga|Solo Leveling"
        var book = SynopsisBook()
        T.ok(book.wanted(a, 0), "jamais essayé : à chercher")
        book = book.saved(a, "Ligne un\nLigne deux\tavec tabulation et \\ barre", "head", 100).book
        T.eq("Ligne un\nLigne deux\tavec tabulation et \\ barre", book.textOf(a), "texte gardé tel quel")
        T.ok(!book.wanted(a, 10_000_000L), "trouvé : plus à chercher")
        val back = SynopsisBook.parse(book.serialize())
        T.eq(book.textOf(a), back.textOf(a), "aller-retour du texte")
        T.eq("head", back.sourceOf(a), "aller-retour de la source")
        var failed = SynopsisBook().failed(b, 1000)
        T.ok(!failed.wanted(b, 1000 + 60_000L), "essai raté : on attend")
        T.ok(failed.wanted(b, 1000 + 11 * 60_000L), "…puis on réessaie")
        for (i in 0 until CoverBook.MAX_TRIES) failed = failed.failed(b, 1000L + i)
        T.ok(!failed.wanted(b, Long.MAX_VALUE / 2), "renoncé après trop d'essais")
        T.ok(failed.again(b).wanted(b, 0), "demande de l'utilisateur : de nouveau à chercher")
        T.eq(book.textOf(a), book.again(a).textOf(a), "un synopsis trouvé reste")
        // le même texte pour deux œuvres de titres différents : c'est le texte du site
        val site = "Welcome to our site where you can follow every series we translate, updated daily with new chapters."
        val s1 = SynopsisBook().saved(a, site, "meta", 1)
        T.ok(s1.accepted, "la première œuvre le reçoit")
        val s2 = s1.book.saved(b, site, "meta", 2)
        T.ok(!s2.accepted && s2.book.textOf(a) == null && s2.book.textOf(b) == null, "la seconde le fait voir : écarté pour les deux")
        T.ok(!s2.book.saved("asura|Troisième", site, "meta", 3).accepted, "et pour les suivantes")
        // la même œuvre sur un autre site : le même résumé est normal
        val s3 = book.saved(c, book.textOf(a)!!, "head", 4)
        T.ok(s3.accepted && s3.book.textOf(c) != null, "même titre sur un autre site : gardé")
        T.eq(null, book.forget(a).textOf(a), "œuvre oubliée")
        T.ok(SynopsisBook.parse("ligne illisible\nS\tincomplet").records.isEmpty(), "ligne illisible ignorée")
    }

    // ---------- version française ----------
    val en1 = "Ten years ago, the Gate appeared and connected the real world with the monster world. Sung Jinwoo, the weakest of all hunters, gets a second chance through a mysterious system."
    val fr1 = "Il y a dix ans, la Porte est apparue et a relié le monde réel au monde des monstres. Sung Jinwoo, le plus faible de tous les chasseurs, reçoit une seconde chance grâce à un mystérieux système."

    T.test("synopsis : on reconnaît un texte déjà en français, sans prendre l'anglais, les noms ni le japonais pour du français") {
        T.ok(Synopsis.isFrench(fr1), "français")
        T.ok(!Synopsis.isFrench(en1), "anglais")
        T.ok(!Synopsis.isFrench("Le Seigneur Demon Sword Master of the Heavenly Cult returns after ten years to take his revenge on the sect that betrayed him."), "titre français dans un texte anglais")
        T.ok(Synopsis.isFrench("Un jeune homme sans talent entre dans la Tour pour retrouver son seul ami, et il découvre que tout est plus dangereux que prévu."), "français")
        T.ok(!Synopsis.isFrench("나 혼자만 레벨업 최약체 헌터 성진우는 시스템의 선택을 받아 강해진다"), "coréen")
        T.ok(!Synopsis.isFrench("L is the world's greatest detective and a master of disguise who hunts Kira."), "un personnage nommé L dans un texte anglais")
    }

    T.test("synopsis : la réponse du modèle est mise au propre, ou refusée si ce n'est pas une traduction entière") {
        T.eq(fr1, Synopsis.cleanFrench(fr1, en1), "traduction propre : inchangée")
        T.eq(fr1, Synopsis.cleanFrench("Voici la traduction :\n\n$fr1", en1), "phrase d'introduction retirée")
        T.eq(fr1, Synopsis.cleanFrench("Here is the translation:\n$fr1\n", en1), "introduction anglaise retirée")
        T.eq(fr1, Synopsis.cleanFrench("**Synopsis**\n$fr1", en1), "titre de section et gras retirés")
        T.eq(fr1, Synopsis.cleanFrench("« $fr1 »", en1), "guillemets autour du tout retirés")
        T.eq(fr1, Synopsis.cleanFrench("\"$fr1\"", en1), "guillemets droits retirés")
        T.eq("Un.\n\nDeux.".let { fr1.substring(0, 20) + "\n\n" + fr1.substring(20) }, Synopsis.cleanFrench(fr1.substring(0, 20) + "\n\n\n\n" + fr1.substring(20), en1), "paragraphes gardés, lignes vides compactées")
        T.eq(null, Synopsis.cleanFrench("", en1), "réponse vide")
        T.eq(null, Synopsis.cleanFrench("Désolé.", en1), "trop court")
        T.eq(null, Synopsis.cleanFrench(en1, en1), "texte recopié tel quel")
        T.eq(null, Synopsis.cleanFrench("Ten years ago, the Gate appeared and linked the real world to the world of monsters. Sung Jinwoo, the weakest hunter, is given a second chance by a strange system.", en1), "anglais reformulé : pas du français")
        T.eq(null, Synopsis.cleanFrench(fr1.dropLast(30), en1), "coupée en pleine phrase alors que l'original est fini")
        T.eq(null, Synopsis.cleanFrench(fr1 + " " + fr1 + " " + fr1 + " " + fr1, en1), "quatre fois plus longue : invraisemblable")
        T.eq(null, Synopsis.cleanFrench("Il y a dix ans, la Porte est apparue.", en1 + " " + en1), "deux fois trop courte")
        T.ok(Synopsis.cleanFrench(fr1.dropLast(30), en1.dropLast(30)) != null, "original lui-même coupé (sans ponctuation finale) : la traduction coupée est admise")
        T.ok(Synopsis.cleanFrench("Un jeune chasseur reçoit un pouvoir qui lui permet de devenir de plus en plus fort, et il part à la conquête du monde des donjons.", "나 혼자만 레벨업 최약체 헌터 성진우는 시스템의 선택을 받아 강해진다") != null, "source coréenne : pas de rapport de longueur")
    }

    T.test("synopsis : la consigne ne mélange rien à la mémoire du chapitre et la réponse a un plafond raisonnable") {
        T.ok(SynopsisPrompt.system().contains("French") && SynopsisPrompt.system().contains("proper names"), "consigne : français, noms propres")
        T.ok(SynopsisPrompt.user("  Un texte.  ").endsWith("\n\nUn texte."), "le texte est donné tel quel")
        T.eq(160, SynopsisPrompt.maxOutputTokens("Court."), "plancher")
        T.ok(SynopsisPrompt.maxOutputTokens(en1) in 120..400, "synopsis ordinaire : ${SynopsisPrompt.maxOutputTokens(en1)}")
        T.eq(1100, SynopsisPrompt.maxOutputTokens("x ".repeat(2500)), "plafond")
        T.ok(SynopsisPrompt.maxOutputTokens("나 혼자만 레벨업 ".repeat(30)) > SynopsisPrompt.maxOutputTokens("a ".repeat(120)), "le coréen a besoin de plus de place que le même nombre de caractères latins")
    }

    T.test("synopsis : la traduction française est gardée avec le synopsis, jamais pour un autre texte, et survit à l'enregistrement") {
        val a = "asura|Solo Leveling"; val b = "asura|Autre"
        var book = SynopsisBook().saved(a, en1, "head", 100).book
        T.ok(book.needsFrench(a) && book.frenchOf(a) == null, "trouvé en anglais : à traduire")
        T.ok(!book.needsFrench(b), "œuvre inconnue : rien à traduire")
        T.ok(book.withFrench(a, "un autre texte", fr1) === book, "le synopsis a changé : traduction ignorée")
        T.ok(book.withFrench(a, en1, "   ") === book, "traduction vide ignorée")
        book = book.withFrench(a, en1, fr1)
        T.eq(fr1, book.frenchOf(a)); T.ok(!book.needsFrench(a), "déjà traduit"); T.eq(en1, book.textOf(a), "l'original reste")
        val back = SynopsisBook.parse(book.serialize())
        T.eq(fr1, back.frenchOf(a), "aller-retour"); T.eq(en1, back.textOf(a))
        val multi = SynopsisBook().saved(a, "One.\nTwo\twith tab, a \\ slash and enough words to count as a synopsis here.", "head", 1).book.withFrench(a, "One.\nTwo\twith tab, a \\ slash and enough words to count as a synopsis here.", "Un.\nDeux\tavec tabulation, une \\ barre.")
        T.eq("Un.\nDeux\tavec tabulation, une \\ barre.", SynopsisBook.parse(multi.serialize()).frenchOf(a), "sauts de ligne, tabulations et barres")
        val old = SynopsisBook.parse("S\t" + a + "\t" + en1 + "\thead\t1\t0\t5\n")
        T.ok(old.has(a) && old.frenchOf(a) == null && old.needsFrench(a), "ancien fichier (sans traduction) : à traduire")
        val again = book.saved(a, en1 + " More.", "head", 200).book
        T.ok(again.frenchOf(a) == null && again.needsFrench(a), "un nouveau texte efface l'ancienne traduction")
        T.eq(null, book.forget(a).frenchOf(a), "œuvre oubliée : traduction aussi")
        T.ok(!SynopsisBook().saved(a, fr1, "head", 1).book.needsFrench(a), "déjà en français : rien à traduire")
        T.eq(null, book.failed(a, 5).frenchOf(a), "synopsis redevenu introuvable : pas de traduction")
    }
}
