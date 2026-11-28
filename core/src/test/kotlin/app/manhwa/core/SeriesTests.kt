package app.manhwa.core

import java.io.File

private val seriesLexicon: EnglishLexicon by lazy {
    val dir = listOf("app/src/main/assets/lexicon", "../app/src/main/assets/lexicon", "../../app/src/main/assets/lexicon").map { File(it) }.firstOrNull { File(it, "enable1.txt").isFile }
        ?: throw AssertionError("lexique introuvable (dossier app/src/main/assets/lexicon)")
    EnglishLexicon(File(dir, "enable1.txt").readLines(), File(dir, "g10k.txt").readLines(), File(dir, "genre.txt").readLines())
}

/**
 * Mémoire de l'œuvre (personnages, registre, héros, lettres mal lues), vocabulaire d'usage par genre, retouches des nombres et des mots doublés, prompt 5 et orchestration
 * de la traduction avec un faux modèle. Les phrases d'essai sont inventées, sur le modèle des défauts relevés dans le journal du 27/11.
 */
fun seriesTests() {
    val lex = seriesLexicon
    // lettrage où le « D » est lu « P » (constaté sur une œuvre entière du journal : « PUKE », « ANP », « BLOOP »…)
    val dAsP = listOf("ANP", "WORP", "BLOOP", "SWORP", "PEMON", "PANGER", "PAMN", "HANP", "MINP", "KINP", "NEEP", "FINP", "PEATH", "PEFEAT", "SECONP", "THIRP", "GOOP", "OLP", "COLP", "PIFFERENT")

    T.test("lettres mal lues : une confusion n'est établie qu'après assez de mots différents, et seulement la plus nette pour la lettre lue") {
        val c = LetterConfusions()
        for (w in dAsP.take(5)) c.observe("$w!", lex)
        T.eq(emptyList<Pair<Char, Char>>(), c.active(), "cinq mots ne suffisent pas")
        for (w in dAsP) c.observe("I SAID $w.", lex)
        T.ok(c.active().contains('P' to 'D'), "P lu pour D : ${c.active()}")
        T.ok(c.active().none { it.first == 'P' && it.second != 'D' }, "une seule lettre voulue pour P")
        // les mots connus et les noms protégés ne comptent pas
        val d = LetterConfusions()
        for (w in dAsP) d.observe(w, lex, protect = dAsP.map { it.lowercase() }.toSet())
        T.eq(emptyList<Pair<Char, Char>>(), d.active(), "noms protégés")
    }
    T.test("lettres mal lues : un mot inconnu devient le mot courant que la confusion explique ; un mot courant, un nom ou un mot hors confusion restent") {
        val c = LetterConfusions(); for (w in dAsP) c.observe(w, lex)
        T.eq("AND", c.fix("ANP", lex)); T.eq("DEMON", c.fix("PEMON", lex)); T.eq("Demon", c.fix("Pemon", lex)); T.eq("demon", c.fix("pemon", lex))
        T.eq("PREPARED", c.fix("PREPAREP", lex), "seules les lettres en cause changent (pas « DREDARED »)")
        T.eq(null, c.fix("PAY", lex), "mot courant gardé"); T.eq(null, c.fix("PARK", lex), "mot courant gardé")
        T.eq(null, c.fix("PUPA", lex, protect = setOf("pupa")), "nom protégé")
        T.eq(null, c.fix("XQZW", lex), "mot que la confusion n'explique pas")
        T.eq("DO", c.fix("PO", lex), "petit mot très courant"); T.eq(null, c.fix("UP", lex), "un petit mot courant n'est jamais changé")
        T.eq("I DON'T KNOW AND I DON'T CARE.", OcrRepair.applyConfusions("I DON'T KNOW ANP I DON'T CARE.", lex, emptySet(), c))
        T.eq("I DON'T KNOW ANP I DON'T CARE.", OcrRepair.applyConfusions("I DON'T KNOW ANP I DON'T CARE.", lex, emptySet(), LetterConfusions()), "aucune confusion établie : texte intact")
    }
    T.test("noms coupés par la lecture : recollés s'ils épellent exactement un nom de l'œuvre ou du synopsis, jamais deux vrais mots par hasard") {
        val names = SeriesMemory.premiseNames("Une étudiante devient Penelope Eckhart, la méchante d'un jeu. Callisto, le prince héritier, la déteste.", lex)
        T.ok("penelope" in names && "eckhart" in names && "callisto" in names && "prince" !in names, "noms du synopsis : $names")
        T.eq("I'M PENELOPE NOW.", OcrRepair.joinSplitNames("I'M PEN ELOPE NOW.", names))
        T.eq("Penelope died helplessly", OcrRepair.joinSplitNames("Pen elope died helplessly", names))
        T.eq("Interact more with Callisto!", OcrRepair.joinSplitNames("Interact more with Call is to!", names))
        T.eq("A PEN AND A ROPE.", OcrRepair.joinSplitNames("A PEN AND A ROPE.", names), "vrais mots")
        T.eq("I'M PENELOPE NOW.", OcrRepair.repairWithSeries("I'M PEN ELOPE NOW.", lex, emptySet(), null, "Penelope Eckhart wakes up as the villainess."))
    }
    T.test("mémoire de l'œuvre : enregistrée puis relue à l'identique (confusions, personnages, genre de l'œuvre)") {
        val m = SeriesMemory("demo")
        for (w in dAsP) m.observeReading("$w?", lex)
        repeat(3) { m.observePage(listOf("LADY PENELOPE IS HERE."), listOf("Dame Penelope est là."), listOf("Penelope")) }
        repeat(3) { m.observePage(listOf("PENELOPE, YOU'RE LATE AGAIN."), listOf("Penelope, tu es encore en retard."), listOf("Penelope")) }
        val back = SeriesMemory.parse("demo", m.serialize())
        T.eq(m.serialize(), back.serialize(), "relu à l'identique")
        T.ok(back.confusions.active().contains('P' to 'D'), "confusion relue")
        T.eq(FrenchAgreement.Gender.F, back.name("penelope")?.gender); T.eq(FrenchAgreement.Register.TU, back.name("penelope")?.register)
        T.ok(back.summary().contains("lettres=P→D") && back.summary().contains("noms=1"), back.summary())
        T.ok(SeriesMemory.parse("x", "n'importe quoi\nP\tcassé\nC\tPD").nameCount() == 0, "fichier abîmé : rien de cassé")
    }
    T.test("mémoire de l'œuvre : genre d'un personnage par les pronoms (jamais quand on lui parle ni quand la bulle nomme deux personnes), titres de civilité") {
        val m = SeriesMemory("g")
        repeat(4) { m.observePage(listOf("GAF, DID YOU SEE HER?"), listOf("Gaf, tu l'as vue ?"), listOf("Gaf")) }
        T.eq(null, m.name("gaf")?.gender, "on parle à Gaf : « her » est quelqu'un d'autre")
        repeat(4) { m.observePage(listOf("ROY TOLD GAF THAT SHE WAS LEAVING."), listOf("Roy a dit à Gaf qu'elle partait."), listOf("Gaf", "Roy")) }
        T.eq(null, m.name("gaf")?.gender, "deux noms dans la bulle : le pronom ne dit pas lequel")
        repeat(3) { m.observePage(listOf("GAF SAID HE WOULD COME."), listOf("Gaf a dit qu'il viendrait."), listOf("Gaf")) }
        T.eq(FrenchAgreement.Gender.M, m.name("gaf")?.gender)
        val n = SeriesMemory("t")
        for (l in listOf("YOUNG LADY ELIA, PLEASE WAIT.", "THANK YOU, LADY ELIA.", "LADY ELIA IS HERE.")) n.observePage(listOf(l), listOf("Dame Elia."), listOf("Elia"))
        T.eq(FrenchAgreement.Gender.F, n.name("elia")?.gender, "titres féminins")
        // mots anglais gardés tels quels par la mémoire du chapitre (traduction en majuscules) : pas des personnages, sauf interpellés ou titrés
        val w = SeriesMemory("w")
        repeat(4) { w.observePage(listOf("SHE SEEMED SO EVIL IN NORMAL MODE."), listOf("ELLE SEMBLAIT SI MÉCHANTE EN MODE NORMAL."), listOf("Normal", "Mode"), lex) }
        T.eq(null, w.name("normal")); T.eq(null, w.name("mode"))
        repeat(3) { w.observePage(listOf("PARK, ARE YOU COMING?"), listOf("PARK, TU VIENS ?"), listOf("Park"), lex) }
        T.eq(FrenchAgreement.Register.TU, w.name("park")?.register, "interpellé : c'est un nom")
        // interjections et mots mal lus gardés par la mémoire du chapitre : pas des personnages
        val j = SeriesMemory("j")
        for (w in listOf("SHLT", "YOL", "BECALSE", "STLDY", "THOLGHT", "TLRN", "HLNGRY", "ENOLGH", "POLRING", "FOLND", "SOLND")) j.observeReading(w, lex)
        repeat(3) { j.observePage(listOf("LGH... HEHE, GAF!"), listOf("LGH... HEHE, GAF !"), listOf("Lgh", "Hehe", "Gaf"), lex) }
        T.eq(null, j.name("lgh"), "mot mal lu"); T.eq(null, j.name("hehe"), "interjection"); T.ok(j.name("gaf") != null, "vrai nom gardé")
    }
    T.test("mémoire de l'œuvre : rappels au modèle — personnages de la page d'abord, héros d'après le synopsis, lettres mal lues avec exemples") {
        val m = SeriesMemory("r")
        repeat(3) { m.observePage(listOf("PENELOPE SAID SHE WAS TIRED."), listOf("Penelope a dit qu'elle était fatiguée."), listOf("Penelope")) }
        repeat(4) { m.observePage(listOf("DERRICK, YOU'RE SO RUDE."), listOf("Derrick, vous êtes si grossier."), listOf("Derrick")) }
        repeat(3) { m.observePage(listOf("DERRICK SAID HE WOULD WAIT."), listOf("Derrick a dit qu'il attendrait."), listOf("Derrick")) }
        for (w in dAsP) m.observeReading(w, lex)
        val notes = m.promptNotes(listOf("WHERE IS DERRICK?"), lex, "Penelope Eckart wakes up as the villainess of a game. She must win the love of one of the male leads, or she dies. Her only hope…")
        val chars = notes.first { it.startsWith("Characters of this series") }
        T.ok(chars.indexOf("Derrick") < chars.indexOf("Penelope"), "le personnage de la page en premier : $chars")
        T.ok(chars.contains("Derrick (male, addressed with \"vous\")") && chars.contains("Penelope (female)"), chars)
        T.ok(notes.any { it.startsWith("The protagonist of this series is a woman") }, "héroïne d'après le synopsis : $notes")
        T.ok(notes.any { it.contains("read \"D\" as \"P\"") && it.contains("ANP = AND") }, "lettres : $notes")
        T.eq(FrenchAgreement.Gender.M, SeriesMemory.protagonistGender("After his death, he returns to the past to save his sect. He swears…"))
        T.eq(FrenchAgreement.Gender.F, SeriesMemory.protagonistGender("Après sa mort, elle se réveille dans le corps d'une méchante. Elle doit survivre ; elle n'a pas le choix."))
        T.eq(null, SeriesMemory.protagonistGender("Il y a mille ans, il faut le dire, il était une fois une secte."), "« il » impersonnel")
        T.eq(null, SeriesMemory.protagonistGender("He meets her. She hates him. He loves her."), "synopsis partagé")
        T.eq(FrenchAgreement.Gender.F, SeriesMemory.protagonistGender("Une étudiante se retrouve dans la peau de Penelope, la méchante d'un jeu dont toutes les fins mènent à sa mort. Pour survivre, elle doit séduire."), "héroïne nommée par des noms")
    }
    T.test("termes de l'œuvre : une suite de mots qui revient est montrée au modèle avec sa traduction précédente (la plus courte réplique), jamais une ligne de la page ni un nom seul") {
        val m = SeriesMemory("terms")
        m.observePage(listOf("THE NORTHERN HEAVENLY SECT WAS CREATED BY THE ALLIANCE TO PROTECT THE MAINLAND."), listOf("La Secte Céleste du Nord a été créée par l'Alliance pour protéger le continent."), emptyList(), lex)
        m.observePage(listOf("WELCOME TO THE NORTHERN HEAVENLY SECT."), listOf("Bienvenue à la Secte Céleste du Nord."), emptyList(), lex)
        m.observePage(listOf("CHUNG MYUNG LEFT THE NORTHERN HEAVENLY SECT!"), listOf("Chung Myung a quitté la Secte Céleste du Nord !"), listOf("Chung", "Myung"), lex)
        val ex = m.termExamples(listOf("THE NORTHERN HEAVENLY SECT HAS FALLEN."), lex)
        T.eq(listOf("WELCOME TO THE NORTHERN HEAVENLY SECT." to "Bienvenue à la Secte Céleste du Nord."), ex, "la plus courte réplique, une seule fois pour le terme le plus long")
        T.eq(emptyList<Pair<String, String>>(), m.termExamples(listOf("WELCOME TO THE NORTHERN HEAVENLY SECT."), lex), "jamais la ligne de la page elle-même")
        T.ok(SeriesMemory.termsOf("CHUNG MYUNG!", lex, listOf("chung", "myung")).isEmpty(), "noms seuls : pas un terme")
        T.ok(SeriesMemory.termsOf("THE MOUNT HUA SECT IS HERE.", lex, emptyList()).none { it.contains("mount hua sect") }, "terme des tables par genre : déjà fixé")
        val back = SeriesMemory.parse("terms", m.serialize())
        T.eq(ex, back.termExamples(listOf("THE NORTHERN HEAVENLY SECT HAS FALLEN."), lex), "relu à l'identique")
        val warm = SeriesMemory("w"); warm.learnExamples = false
        repeat(3) { warm.observePage(listOf("THE NORTHERN HEAVENLY SECT."), listOf("La secte."), emptyList(), lex) }
        T.eq(emptyList<Pair<String, String>>(), warm.termExamples(listOf("NORTHERN HEAVENLY SECT!"), lex), "préchauffage : exemples non retenus")
        val u = LlmPrompt.user(listOf("Hi."), emptyList(), emptyList(), examples = listOf("WELCOME TO THE NORTHERN HEAVENLY SECT." to "Bienvenue à la Secte Céleste du Nord."))
        T.ok(u.contains("Earlier lines of this series") && u.contains("- WELCOME TO THE NORTHERN HEAVENLY SECT. => Bienvenue à la Secte Céleste du Nord."), u)
    }
    T.test("genre de l'œuvre : reconnu au titre, au synopsis et aux bulles ; termes d'usage trouvés dans la page, le plus long d'abord, pluriels compris") {
        T.ok("murim" in GenrePacks.detect(emptyMap(), "Return Of The Mount Hua Sect", "The Plum Blossom Sword Saint, a disciple of the sect, mastered martial arts…"), "murim")
        T.eq(emptySet<String>(), GenrePacks.detect(emptyMap(), "Waiting In The Wheat Field", "A quiet story."), "aucun genre")
        T.ok("hunter" in GenrePacks.detect(mapOf("gate" to 3, "hunter" to 5, "dungeon" to 2), null, null), "par les bulles")
        val hits = GenrePacks.find(setOf("murim"), listOf("THE BEGGARS’ UNION IS HERE.", "ELDER, PLEASE.", "MOUNT HUA SECT!")).associate { it.source to it.french }
        T.eq("Union des Mendiants", hits["beggars' union"]); T.eq("ancien", hits["elder"]); T.eq("secte du Mont Hua", hits["mount hua sect"])
        T.ok("mount hua" !in hits, "« Mount Hua » est dans « Mount Hua Sect » : pas répété")
        val h2 = GenrePacks.find(setOf("hunter"), listOf("THE GATES OPENED.", "TWO DUNGEONS!")).associate { it.source to it.french }
        T.eq("portail", h2["gates"]); T.eq("donjon", h2["dungeon"])
        T.eq(emptyList<GenrePacks.Hit>(), GenrePacks.find(emptySet(), listOf("THE GATES OPENED.")))
    }
    T.test("nombres : un chiffre doublé ou perdu à la génération est remis tel qu'à l'original ; l'heure et les nombres justes ne bougent pas") {
        T.eq("J'ai attendu 4 jours", TranslationPost.numbers("I WAITED 4 DAYS", "J'ai attendu 44 jours"))
        T.eq("Les 1 000 soldats", TranslationPost.numbers("THE 1,000 SOLDIERS", "Les 10 000 soldats"))
        T.eq("Ça coûte 300 pièces d'or", TranslationPost.numbers("IT COSTS 300 GOLD", "Ça coûte 300 pièces d'or"))
        T.eq("Rendez-vous à 13 h", TranslationPost.numbers("MEET ME AT 1 PM", "Rendez-vous à 13 h"))
        T.eq("Il a 26 ans", TranslationPost.numbers("HE IS 26", "Il a 266 ans"))
        T.eq("Entre 2 et 3", TranslationPost.numbers("BETWEEN 2 AND 3", "Entre 2 et 3"))
    }
    T.test("bégaiement à points : la lettre bégayée est celle du mot français") {
        T.eq("D... DÉSOLÉ LOGAN...", TranslationPost.stutter("S.. SORRY LOGAN..", "S... DÉSOLÉ LOGAN..."))
        T.eq("M... MAMAN", TranslationPost.stutter("M.. MOM", "M... MAMAN"))
        T.eq("FAIS... DOCTEUR", TranslationPost.stutter("DO... DOC, TER...?", "FAIS... DOCTEUR"), "un mot entier n'est pas un bégaiement")
        T.eq("Bon... d'accord.", TranslationPost.stutter("Well... okay.", "Bon... d'accord."), "pas de bégaiement dans l'original")
    }
    T.test("mots doublés : un mot-outil répété par la génération est retiré, jamais un bégaiement de l'original ni « vous vous »") {
        T.eq("Quelle que soit la raison", TranslationPost.doubledWords("WHATEVER THE REASON", "Quelle que que soit la raison"))
        T.eq("Je le veux", TranslationPost.doubledWords("I WANT IT", "Je je le veux"))
        T.eq("À quel point", TranslationPost.doubledWords("HOW MUCH", "À à quel point"))
        T.eq("Vous vous trompez", TranslationPost.doubledWords("YOU ARE WRONG", "Vous vous trompez"))
        T.eq("Vous vous trompez", TranslationPost.doubledWords("YOU ARE WRONG", "Vous vous vous trompez"))
        T.eq("Je je ne sais pas", TranslationPost.doubledWords("I, I DON'T KNOW", "Je je ne sais pas"))
    }
    fun g(tr: String, src: String = "") = TranslationPost.grammar(src, tr)
    T.test("lenteur (journal du 28/11) : réponse sans « | » lue quand même, faux problèmes qui faisaient refaire des lignes pour rien") {
        // Mount Hua 1, page 12 : « 1| … » puis « 2 Pa ! », « 1111 … » : la page entière était refaite, puis ses 21 lignes une à une
        val reply = "1| Aaaack !\n2 Pa !\n3 Aaaack !\n4 Uaaaak !\n5 Kuaa aak !\n6 P-Pitié !\n7 Je reviendrai plus tard\n8 Kuheung.\n9 Aïe, aïe...\n10 Ce maudit sale gosse...\n1111 Je n'ai jamais été battu si fort.\n1212 Même pour ce morveux."
        val p = LlmPrompt.parse(reply, 12)
        T.eq("Pa !", p[1]); T.eq("Ce maudit sale gosse...", p[9]); T.eq("Je n'ai jamais été battu si fort.", p[10]); T.eq("Même pour ce morveux.", p[11])
        T.eq(listOf("Il a 10 ans et 3 amis.", null), LlmPrompt.parse("1| Il a 10 ans et\n3 amis.", 2).let { listOf(it[0]?.let { _ -> "Il a 10 ans et 3 amis." }, it[1]) }, "un nombre qui n'est pas le numéro attendu continue la ligne")
        fun probs(src: String, tr: String) = TranslationChecks.problems(src, tr, "fr", listOf("Moowon")).map { it.code }
        T.eq(emptyList<String>(), probs("Y, YOU...", "T-TOI..."), "bégaiement à virgule"); T.eq(emptyList<String>(), probs("A.. ARE YOU LAUGHING?!", "T-TU TE MOQUES DE MOI ?!"), "bégaiement à points")
        T.ok("répétition" in probs("WHAT IS IT?", "QU'EST-EST-CE QUE C'EST ?"), "syllabe doublée sans bégaiement dans l'original : toujours signalée")
        T.eq(emptyList<String>(), probs("0-OUCHY OUCHY...", "AÏE, AÏE..."), "0 mis pour O"); T.eq(emptyList<String>(), probs("IS SHE CALLING FOR ME..2", "EST-CE QU'ELLE M'APPELLE... ?"), "? lu 2")
        T.eq(emptyList<String>(), probs("*LI: ONE LI IS EQUAL TO 0.393 KILOMETERS.", "*LI : UN LI ÉQUIVAUT À 0,393 KILOMÈTRE."), "décimale")
        T.ok("chiffres" in probs("I WAITED 4 DAYS.", "J'AI ATTENDU DES JOURS."), "vrai chiffre perdu : toujours signalé")
        T.eq(emptyList<String>(), probs("KUAA AAK!", "KUAA AAK !"), "cri recopié"); T.eq(emptyList<String>(), probs("KEKEKE KEK!!", "KEKEKE KEK!!"), "rire recopié")
        T.eq(emptyList<String>(), probs("JIN MOOWON", "JIN MOOWON"), "nom recopié")
        T.ok("non-traduit" in probs("THE NORTHERN HEAVENLY SECT", "THE NORTHERN HEAVENLY SECT"), "vraie phrase anglaise recopiée : toujours signalée")
    }
    T.test("numéros bégayés par le modèle (journal du 28/11 14:05) : une suite de chiffres n'est ni une ligne ni la suite de la précédente") {
        // Mount Hua 2, page 17 : « 10| … » puis « 1111111… » jusqu'à la limite ; la suite était collée à la ligne 10
        val reply = "9| Sssshh\n10| Le saint de l'épée de la Fleur de Prunier !!!\n" + "1".repeat(300)
        val p = LlmPrompt.parse(reply, 12)
        T.eq("Le saint de l'épée de la Fleur de Prunier !!!", p[9]); T.eq(null, p[10]); T.eq(null, p[11])
        T.eq("GAH !!!", LlmPrompt.parse("1| Bon.\n2| GAH !!!\n11111111111111\n", 2)[1], "Legend of the Northern Blade, 27/11")
        T.eq("Une ligne\u00a0: 1111 pièces", LlmPrompt.parse("1| Une ligne\u00a0: 1111 pièces", 1)[0], "chiffres d'une vraie traduction : gardés")
        T.eq("GAH !!!", TranslationPost.digitRuns("GAH!!!", "GAH !!! 11111111111111111"))
        T.eq("IL A 11111 PIÈCES", TranslationPost.digitRuns("HE HAS 11111 COINS", "IL A 11111 PIÈCES"), "présent dans l'original : gardé")
        T.eq("ÇA COÛTE 100 €", TranslationPost.digitRuns("IT COSTS 100 €", "ÇA COÛTE 100 €"))
    }
    T.test("grammaire : élisions oubliées, subjonctif après « bien que », « pour que »…, infinitif après « faire » — le français juste n'est jamais touché") {
        T.eq("AUJOURD'HUI, L'ÉPILOGUE SERA EN LIGNE.", g("AUJOURD'HUI, LE ÉPILOGUE SERA EN LIGNE."))
        T.eq("Le fruit de l'ancien vagabond.", g("Le fruit du ancien vagabond."))
        T.eq("Je ne sais pas s'il viendra, mais j'ai un plan et qu'il le sache.", g("Je ne sais pas si il viendra, mais je ai un plan et que il le sache."))
        T.eq("C'est fini. D'un coup, il n'y a plus rien.", g("Ce est fini. De un coup, il ne y a plus rien."))
        T.eq("J'y vais. Elle s'est levée.", g("Je y vais. Elle se est levée."))
        for (ok in listOf("Fais-le encore !", "Le onze de l'équipe.", "Je crois que oui.", "Le héros de la haine.", "J'aurais du aller le voir.", "Parle de Yoo Joonghyuk.", "La une du journal ? Non : le yaourt."))
            T.eq(ok, g(ok), "intact : $ok")
        T.eq("Bien qu'il soit actuellement esclave, il était noble.", g("Bien qu'il est actuellement esclave, il était noble."))
        T.eq("POUR QUE TU PUISSES PARTIR.", g("POUR QUE TU PEUX PARTIR."))
        T.eq("Il faut que je fasse vite, avant qu'ils ne le sachent.", g("Il faut que je fais vite, avant qu'ils ne le savent."))
        T.eq("Bien que j'aie peur.", g("Bien que j'ai peur."))
        T.eq("Je veux que vous soyez là.", g("Je veux que vous êtes là."))
        T.eq("C'est pour ça que tu es là.", g("C'est pour ça que tu es là."), "« pour ça que » : indicatif")
        T.eq("QUAND LA MALCHANCE M'A FAIT STATIONNER AU FRONT.", g("QUAND LA MALCHANCE M'A FAIT STATIONNÉ AU FRONT."))
        T.eq("Il s'est fait tuer.", g("Il s'est fait tué."))
        T.eq("C'est un fait avéré.", g("C'est un fait avéré."))
        T.eq("TU VAS LUI DEMANDER DE TE PRÊTER DE L'ARGENT ?", g("TU VAS LUI DEMANDER DE T'PRÊTER DE L'ARGENT ?")); T.eq("Je ne sais pas.", g("Je n'sais pas."))
        T.eq("L'homme d'honneur qu'il aime : j'hésite, je l'ai.", g("L'homme d'honneur qu'il aime : j'hésite, je l'ai."), "élisions justes intactes")
        T.eq("COMMENT EST-CE POSSIBLE ?!!!", g("COMMENT EST-CE POSSIBLE ? !!!")); T.eq("Quoi ?!", g("Quoi ? !"))
        // journal du 27/11 : titre anglais recopié, nom, « à la une », « de ouf »
        T.eq("BIENVENUE DANS PICK ME UP !", g("BIENVENUE DANS PICK ME UP !", "WELCOME TO PICK ME UP!"))
        T.eq("C'EST LA FILLE D'UIHYEOK JEONG.", g("C'EST LA FILLE DU UIHYEOK JEONG.", "SHE'S UIHYEOK JEONG'S DAUGHTER."))
        T.eq("AUSSI FIER DE TOI QUE D'EUGENE.", g("AUSSI FIER DE TOI QUE DE EUGENE.", "AS PROUD OF YOU AS OF EUGENE."))
        T.eq("CONTENU À LA UNE", g("CONTENU À LA UNE", "FEATURED")); T.eq("TAIS-TOI DE OUF !", g("TAIS-TOI DE OUF !", "SHUT UP!"))
        T.eq("CONTRAIREMENT À MOI, LA A STAR SHAY", g("CONTRAIREMENT À MOI, LA A STAR SHAY", "UNLIKE ME, A STAR SHAY"), "lettre seule")
    }
    T.test("contrôle des chiffres : un nombre écrit en lettres ou regroupé à la française n'est pas perdu ; un chiffre doublé l'est") {
        fun lost(src: String, tr: String) = TranslationChecks.problems(src, tr, "fr", emptyList()).any { it.code == "chiffres" }
        T.ok(!lost("I HAVE 3 SWORDS.", "J'ai trois épées."), "trois"); T.ok(!lost("I HAVE 1 SWORD.", "J'ai une épée."), "une")
        T.ok(!lost("IN 21 DAYS.", "Dans vingt et un jours."), "vingt et un"); T.ok(!lost("IN 21 DAYS.", "Dans vingt-et-un jours."), "orthographe de 1990")
        T.ok(!lost("80 MEN.", "Quatre-vingts hommes."), "quatre-vingts"); T.ok(!lost("250 MEN.", "Deux cent cinquante hommes."), "deux cent cinquante")
        T.ok(!lost("1,000 MEN.", "1 000 hommes."), "milliers groupés"); T.ok(!lost("1,000 MEN.", "Mille hommes."), "mille"); T.ok(!lost("2000 MEN.", "2 000 hommes."), "groupés dans la traduction seulement")
        T.ok(lost("HE IS 26.", "Il a 266 ans."), "chiffre doublé"); T.ok(lost("I HAVE 3 SWORDS.", "J'ai des épées."), "nombre perdu")
        T.ok(FrenchNumbers.pattern(105)!!.containsMatchIn("cent cinq") && FrenchNumbers.pattern(105)!!.containsMatchIn("cent-cinq"), "cent cinq, cent-cinq")
        T.eq("soixante et onze", FrenchNumbers.words(71)); T.eq("deux mille", FrenchNumbers.words(2000)); T.ok(FrenchNumbers.pattern(80)!!.containsMatchIn("quatre-vingt"), "vingt sans s")
    }
    T.test("expressions du journal du 27/11 : repérées avec leur équivalent, calques reconnus, sens ordinaire laissé") {
        fun fr(s: String) = IdiomHints.find(listOf(s)).joinToString(" / ") { it.french }
        T.ok(fr("I'LL MAKE SURE TO TEACH HIM A LESSON.").contains("ne pas manquer de") && fr("MAKE SURE TO REST.").isEmpty(), "make sure to")
        T.ok(fr("BIG DEAL.").contains("belle affaire") && fr("IT'S NO BIG DEAL.").contains("ce n'est rien") && fr("WHAT'S THE BIG DEAL?").contains("problème"), "big deal")
        T.ok(IdiomHints.calques("I'LL MAKE SURE TO TEACH HIM A LESSON.", "JE VAIS M'ASSURER DE LUI DONNER UNE LEÇON.").isNotEmpty(), "calque make sure to")
        T.ok(IdiomHints.calques("WHAT'S THE POINT?", "QUEL EST LE POINT ?").isNotEmpty() && fr("WHAT'S THE POINT?").contains("à quoi bon"), "what's the point")
        T.ok(IdiomHints.calques("SHIN-JOON FOR SHORT.", "SHIN-JOON POUR COURT.").isNotEmpty(), "for short")
        T.ok(IdiomHints.calques("YOU MESSED WITH THE WRONG PERSON.", "TU T'ES MÊLÉ DE QUELQU'UN.").isNotEmpty(), "mess with the wrong")
        T.ok(fr("I CAN BARELY MAKE ENDS MEET.").contains("joindre les deux bouts"), "make ends meet")
        T.ok(fr("WHO CARES ABOUT FRIENDS?").contains("on s'en fiche"), "who cares")
        T.ok(fr("HE'S AT IT AGAIN...").contains("ça le reprend"), "at it again")
        T.ok(fr("YOU'RE DEAD TO ME TODAY.").contains("homme mort"), "dead to me today")
        T.ok(fr("I AM.").contains("oui") && fr("I AM THE STRONGEST.").isEmpty(), "« I am. » seul")
        T.ok(fr("HOLY...").contains("la vache") && fr("THE HOLY KNIGHT ARRIVED.").isEmpty(), "« holy » seul")
        T.ok(fr("YOU'VE GOT SOME NERVE.").contains("culot") && fr("HE HAS A POINT.").contains("raison"), "verbes irréguliers have, get")
        T.ok(fr("GET YOUR S*** TOGETHER!").contains("ressaisir") && fr("LET'S GET TOGETHER TOMORROW.").contains("se retrouver") && IdiomHints.calques("GET YOUR S*** TOGETHER!", "RÉUNIS-TOI !").isNotEmpty(), "get your s*** together")
        T.ok(fr("GO AHEAD AND REJECT ME.").contains("vas-y") && IdiomHints.calques("GO AHEAD AND REJECT ME.", "ALLEZ-VOUS REJETER MOI.").isNotEmpty(), "go ahead and")
        for (s in listOf("WE CAN BEAT IT!", "THIS IS A BIG DEAL TO ME.", "HE BEATS ME EVERY DAY.", "SHE SLEPT IN THE BED.", "IT'S ALL OVER THE NEWS.", "HE RAN INTO THE BUILDING.", "IT'S MADE OUT OF WOOD.",
            "I'M GETTING EVEN STRONGER!", "TURN OUT THE LIGHTS.", "HE CAME UP TO YOU.", "I GOT HIT ON THE HEAD."))
            T.ok(IdiomHints.find(listOf(s)).none { it.french.contains("dégage") || it.french.contains("belle affaire") || it.french.contains("aucune idée") || it.french.contains("grasse matinée") || it.french.contains("c'est fini") || it.french.contains("tomber sur")
                || it.french.contains("se venger") || it.french.contains("s'avérer") || it.french.contains("toi qui vois") || it.french.contains("draguer") }, "sens ordinaire : $s -> ${fr(s)}")
    }
    T.test("prompt 5 : version courante, notes de l'œuvre et termes d'usage dans le message, version 4 intacte") {
        T.eq(6, LlmPrompt.CURRENT)
        val v5 = LlmPrompt.system("en", "fr", 5)
        T.ok(v5.contains("published French edition") && v5.contains("Ça le reprend") && v5.contains("Usual French terms") && v5.contains("N| translation"), "règles v5")
        T.ok(LlmPrompt.system("en", "fr", 4).contains("Pas question") && !LlmPrompt.system("en", "fr", 4).contains("published French edition"), "v4 intacte")
        val u = LlmPrompt.user(listOf("ELDER, PLEASE."), emptyList(), emptyList(), "Return Of The Mount Hua Sect", emptyList(), emptyList(), emptyList(), emptyList(), null,
            listOf("Characters of this series: Chung Myung (male)."), listOf(GenrePacks.Hit("elder", "ancien")))
        T.ok(u.contains("Series notes") && u.contains("Chung Myung (male)") && u.contains("Usual French terms") && u.contains("elder = ancien"), u)
        T.ok(!LlmPrompt.user(listOf("Hi."), emptyList(), emptyList()).contains("Series notes"), "pas de note, pas de section")
    }
    T.test("mémoire fiable (prompt 6) : la traduction n'apprend que des lignes réussies du premier coup ; un exemple ne fait référence que confirmé par deux chapitres ; le glossaire gagne") {
        // tutoiement et narrateur : seulement des lignes sûres
        val m = SeriesMemory("fiable")
        repeat(4) { m.observePage(listOf("DERRICK, YOU'RE SO RUDE."), listOf("Derrick, tu es si grossier."), listOf("Derrick"), lex, trusted = listOf(false)) }
        T.eq(null, m.name("derrick")?.register, "registre appris d'une ligne refaite : non")
        repeat(4) { m.observePage(listOf("DERRICK, YOU'RE SO RUDE."), listOf("Derrick, vous êtes si grossier."), listOf("Derrick"), lex, trusted = listOf(true)) }
        T.eq(FrenchAgreement.Register.VOUS, m.name("derrick")?.register)
        repeat(8) { m.observePage(listOf("I'M READY."), listOf("Je suis prête."), emptyList(), lex, trusted = listOf(true), outside = listOf(false)) }
        T.eq(null, m.narratorGender(), "dans une bulle, « je » n'est pas forcément le narrateur")
        repeat(8) { m.observePage(listOf("I WAS READY."), listOf("J'étais prêt."), emptyList(), lex, trusted = listOf(true), outside = listOf(true)) }
        T.eq(FrenchAgreement.Gender.M, m.narratorGender(), "narration hors bulle")
        // exemples : un seul chapitre ne suffit plus
        val t = SeriesMemory("ref")
        val sect = listOf(
            Triple("THE NORTHERN HEAVENLY SECT WAS CREATED BY THE ALLIANCE.", "La Secte Céleste du Nord a été créée par l'Alliance.", "ch1"),
            Triple("WELCOME TO THE NORTHERN HEAVENLY SECT.", "Bienvenue à la Secte Céleste du Nord.", "ch1"),
            Triple("CHUNG MYUNG LEFT THE NORTHERN HEAVENLY SECT!", "Chung Myung a quitté la Secte Céleste du Nord !", "ch1"),
        )
        for ((s1, f1, c) in sect) t.observePage(listOf(s1), listOf(f1), emptyList(), lex, trusted = listOf(true), chapter = c)
        val q = listOf("THE NORTHERN HEAVENLY SECT HAS FALLEN.")
        T.eq(emptyList<Pair<String, String>>(), t.termExamples(q, lex, agreed = true), "un seul chapitre : pas encore une référence")
        T.eq(1, t.termExamples(q, lex).size, "prompt 5 : comme avant")
        t.observePage(listOf("THEY ATTACKED THE NORTHERN HEAVENLY SECT."), listOf("Ils ont attaqué la Secte du Paradis Boréal."), emptyList(), lex, trusted = listOf(true), chapter = "ch2")
        T.eq(emptyList<Pair<String, String>>(), t.termExamples(q, lex, agreed = true), "deux chapitres en désaccord : aucune référence")
        t.observePage(listOf("THE NORTHERN HEAVENLY SECT IS STRONG."), listOf("La Secte Céleste du Nord est forte."), emptyList(), lex, trusted = listOf(true), chapter = "ch3")
        T.eq(listOf("THE NORTHERN HEAVENLY SECT IS STRONG." to "La Secte Céleste du Nord est forte."), t.termExamples(q, lex, agreed = true), "confirmée par un autre chapitre : la plus courte des deux")
        t.observePage(listOf("THE NORTHERN HEAVENLY SECT, AGAIN."), listOf("La secte, encore."), emptyList(), lex, trusted = listOf(false), chapter = "ch4")
        T.ok(t.serialize().lines().none { it.contains("La secte, encore.") }, "ligne refaite : jamais un exemple")
        T.eq(emptyList<Pair<String, String>>(), t.termExamples(q, lex, agreed = true, glossary = listOf(GlossaryEntry("Northern Heavenly Sect", "Secte du Ciel du Nord"))), "le glossaire fixe le terme : pas d'exemple")
        T.eq(emptyList<Pair<String, String>>(), t.termExamples(q, lex, agreed = true, glossary = listOf(GlossaryEntry("Strong", "puissante"))), "exemple contraire au glossaire : écarté")
        val back = SeriesMemory.parse("ref", t.serialize())
        T.eq(t.termExamples(q, lex, agreed = true), back.termExamples(q, lex, agreed = true), "relu à l'identique")
        // exemple enregistré avant ce build : chapitre inconnu, il attend la confirmation d'un chapitre traduit depuis
        val old = SeriesMemory.parse("old", "V\t1\nT\tnorthern heavenly sect\t5\nE\tnorthern heavenly sect\tWELCOME TO THE NORTHERN HEAVENLY SECT.\tBienvenue à la Secte Céleste du Nord.\n")
        T.eq(emptyList<Pair<String, String>>(), old.termExamples(q, lex, agreed = true), "ancien exemple seul : pas une référence")
        T.eq(1, old.termExamples(q, lex).size, "mais toujours lu")
        T.ok(SeriesMemory.agree(SeriesMemory.Example("THE HEAVENLY DEMON!", "Le Démon Céleste !", "a"), SeriesMemory.Example("FEAR THE HEAVENLY DEMON.", "Craignez le Démon Céleste.", "b"), "heavenly demon"), "Démon Céleste deux fois")
        T.ok(!SeriesMemory.agree(SeriesMemory.Example("MOUNT HUA SECT.", "La secte du Mont Hua.", "a"), SeriesMemory.Example("THE MOUNT HUA SECT FELL.", "Le Mont Hua est tombé.", "b"), "the mount hua sect"), "« Hua » recopié et un seul mot commun : pas assez pour trois mots")
    }
    T.test("mémoire de l'œuvre vue et effacée par l'utilisateur : un élément effacé n'est plus rappelé ni réappris, il se rétablit, « tout oublier » garde les effacements") {
        val m = SeriesMemory("vue")
        repeat(4) { m.observePage(listOf("PENELOPE SAID SHE WAS TIRED."), listOf("Penelope a dit qu'elle était fatiguée."), listOf("Penelope"), lex) }
        repeat(4) { m.observePage(listOf("PUKE, YOU'RE LATE."), listOf("Puke, tu es en retard."), listOf("Puke"), lex) }
        repeat(8) { m.observePage(listOf("I WAS READY."), listOf("J'étais prête."), emptyList(), lex) }
        for ((s1, f1, c) in listOf(Triple("WELCOME TO THE NORTHERN HEAVENLY SECT.", "Bienvenue à la Secte Céleste du Nord.", "a"), Triple("THE NORTHERN HEAVENLY SECT IS STRONG.", "La Secte Céleste du Nord est forte.", "b"), Triple("LEAVE THE NORTHERN HEAVENLY SECT.", "Quitte la Secte Céleste du Nord.", "b")))
            m.observePage(listOf(s1), listOf(f1), emptyList(), lex, chapter = c)
        val v = m.view(null, lex)
        T.ok(v.characters.any { it.display == "Penelope" && it.gender == FrenchAgreement.Gender.F } && v.characters.any { it.key == "puke" }, "personnages")
        T.eq(FrenchAgreement.Gender.F, v.narrator)
        T.ok(v.terms.any { it.term == "northern heavenly sect" && it.reference && it.chapters == 2 }, "terme confirmé : ${v.terms.map { it.term + "/" + it.reference }}")
        m.forgetName("puke"); m.forgetTerm("northern heavenly sect"); m.forgetNarrator()
        repeat(4) { m.observePage(listOf("PUKE, YOU'RE LATE."), listOf("Puke, tu es en retard."), listOf("Puke"), lex) }
        repeat(8) { m.observePage(listOf("I WAS READY."), listOf("J'étais prête."), emptyList(), lex) }
        m.observePage(listOf("THE NORTHERN HEAVENLY SECT IS STRONG."), listOf("La Secte Céleste du Nord est forte."), emptyList(), lex, chapter = "c")
        val v2 = m.view("She wakes up as the villainess. She must survive. Her fate…", lex)
        T.ok(v2.characters.none { it.key == "puke" } && v2.terms.none { it.term == "northern heavenly sect" } && v2.narrator == null, "effacés et pas réappris")
        T.ok(m.promptNotes(listOf("HELLO."), lex, "She wakes up as the villainess. She must survive. Her fate…").none { it.contains("protagonist") }, "narrateur effacé : ni appris ni tiré du synopsis")
        T.eq(setOf("nom:puke", "terme:northern heavenly sect", "narrateur:narrateur"), v2.rejected.map { it.kind + ":" + it.key }.toSet())
        val back = SeriesMemory.parse("vue", m.serialize())
        T.eq(3, back.view(null, lex).rejected.size, "effacements enregistrés")
        back.restore("nom", "puke")
        repeat(4) { back.observePage(listOf("PUKE, YOU'RE LATE."), listOf("Puke, tu es en retard."), listOf("Puke"), lex) }
        T.ok(back.view(null, lex).characters.any { it.key == "puke" }, "rétabli : réappris")
        back.clearLearned()
        T.ok(back.view(null, lex).isEmpty && back.view(null, lex).rejected.size == 2, "tout oublié, effacements gardés")
    }
    T.test("contexte (prompt 6) : lignes hors bulle signalées, pensées entre parenthèses, ligne refaite avec ses voisines, ses notes et le synopsis") {
        val prompts = ArrayList<String>()
        val backend = LlmBackend { _, user, _, _ ->
            prompts.add(user)
            if (user.contains("was rejected by an automatic check")) return@LlmBackend LlmReply("1| Il fait froid aujourd'hui.")
            val lines = user.lines().filter { Regex("^\\d+\\| ").containsMatchIn(it) }.map { it.substringAfter("| ") }
            LlmReply(lines.mapIndexed { i, l -> "${i + 1}| " + when {
                l.contains("COLD") -> "It's cold today."            // anglais recopié : contrôle raté, ligne refaite seule
                l.startsWith("(") -> "(Quel idiot.)"
                else -> "Réponse $i."
            } }.joinToString("\n"))
        }
        val tr = LlmTranslator(backend) { lex }
        tr.seriesPremise = "Chung Myung, the Plum Blossom Sword Saint, wakes up a hundred years later in the body of a beggar. He must restore his sect."
        val mem = SeriesMemory("ctx"); tr.series = mem
        repeat(4) { mem.observePage(listOf("CHUNG MYUNG SAID HE WOULD COME."), listOf("Chung Myung a dit qu'il viendrait."), listOf("Chung Myung"), lex) }
        val texts = listOf("THE MOUNTAIN WAS SILENT.", "(WHAT AN IDIOT.)", "IT'S COLD TODAY.", "WHERE IS CHUNG MYUNG?")
        tr.translate(texts, "en", "fr", emptyList(), kinds = listOf("FREE", "IN_BUBBLE", "IN_BUBBLE", ""))
        T.ok(prompts[0].contains("Lines outside speech bubbles: 1.") && prompts[0].contains("Lines in parentheses are inner thoughts or asides (keep the parentheses): 2."), prompts[0])
        val fix = prompts.firstOrNull { it.contains("was rejected by an automatic check") } ?: ""
        T.ok(fix.contains("The lines just before it on this page") && fix.contains("(WHAT AN IDIOT.) => (Quel idiot.)") && fix.contains("The lines just after it on this page") && fix.contains("WHERE IS CHUNG MYUNG?"), "voisines : $fix")
        T.ok(fix.contains("Story premise") && fix.contains("Series notes"), "synopsis et notes : $fix")
        prompts.clear()
        tr.translate(listOf("A.", "B.", "C."), "en", "fr", emptyList(), kinds = listOf("FREE", "FREE", "FREE"))
        T.ok(!prompts[0].contains("outside speech bubbles"), "page entière hors bulle (détecteur sans bulle) : rien à distinguer")
        prompts.clear(); tr.promptVariant = 5
        tr.translate(texts, "en", "fr", emptyList(), kinds = listOf("FREE", "IN_BUBBLE", "IN_BUBBLE", ""))
        T.ok(!prompts[0].contains("outside speech bubbles") && prompts.none { it.contains("just before it on this page") }, "prompt 5 intact")
    }
    T.test("traduction (prompt 5, faux modèle) : notes et termes envoyés, nombres retouchés sans nouvel appel, mémoire de l'œuvre nourrie, message tenu dans la fenêtre") {
        val prompts = ArrayList<String>()
        val backend = LlmBackend { _, user, _, _ ->
            prompts.add(user)
            val lines = user.lines().filter { Regex("^\\d+\\| ").containsMatchIn(it) }.map { it.substringAfter("| ") }
            // traduction factice : chaque ligne numérotée du lot reçoit une réponse ; « 4 DAYS » devient « 44 jours » (défaut de génération)
            LlmReply(lines.mapIndexed { i, l -> "${i + 1}| " + when {
                l.contains("4 DAYS") -> "J'ai attendu 44 jours."
                l.contains("ELDER") -> "Ancien, je vous en prie."
                else -> "Réponse $i."
            } }.joinToString("\n"))
        }
        val tr = LlmTranslator(backend) { lex }
        tr.promptVariant = 5; tr.seriesTitle = "Return Of The Mount Hua Sect"; tr.seriesPremise = "A disciple of the sect who mastered martial arts returns. He…"
        val mem = SeriesMemory("hua"); tr.series = mem
        tr.beginCallLog()
        val out = tr.translate(listOf("I WAITED 4 DAYS.", "ELDER, PLEASE."), "en", "fr", emptyList())
        T.eq("J'ai attendu 4 jours.", out[0], "nombre retouché")
        T.eq(1, prompts.size, "aucun appel de correction : ${tr.lastCallDetail()}")
        T.ok(prompts[0].contains("Usual French terms") && prompts[0].contains("elder = ancien"), "terme d'usage : ${prompts[0]}")
        T.ok(mem.serialize().contains("G\telder"), "mots du genre comptés")
        // crédits coréens dans une page anglaise : gardés tels quels (le modèle y inventait une phrase) ; un bruit coréen court passe au modèle
        val credits = tr.translate(listOf("글태선그림도빈어시흑밀민섭", "사박"), "en", "fr", emptyList())
        T.eq("글태선그림도빈어시흑밀민섭", credits[0]); T.ok(credits[1] != "사박", "bruit court traduit : ${credits[1]}")
        // un nom de l'œuvre connu des chapitres précédents est gardé dès la première page d'un nouveau chapitre (« AIN » n'est pas « hein »)
        val known = SeriesMemory("ain"); repeat(3) { known.observePage(listOf("AIN, LOOK."), listOf("Ain, regarde."), listOf("Ain"), lex) }
        val tr2 = LlmTranslator(backend) { lex }; tr2.promptVariant = 5; tr2.series = known; prompts.clear()
        tr2.translate(listOf("BUT REMEMBER THIS, AIN."), "en", "fr", emptyList())
        T.ok(prompts[0].contains("Names and terms of this chapter to keep exactly as written") && prompts[0].contains("Ain"), "nom de l'œuvre gardé : ${prompts[0]}")
        // fenêtre étroite : le dialogue précédent est raccourci, le message tient
        repeat(5) { tr.translate(listOf("THIS IS A LONG LINE OF DIALOGUE NUMBER $it THAT FILLS THE CONTEXT WINDOW QUICKLY AND KEEPS GOING."), "en", "fr", emptyList()) }
        val window = (LlmPrompt.system("en", "fr", 5).length / 3.0).toInt() + LlmPrompt.maxOutputTokens(listOf("ELDER, PLEASE.")) + 64 + 300
        tr.contextTokens = window; tr.beginCallLog(); prompts.clear()
        tr.translate(listOf("ELDER, PLEASE."), "en", "fr", emptyList())
        T.ok((tr.lastCallDetail() ?: "").contains("ctx:") && !(tr.lastCallDetail() ?: "").contains("ctx:over"), "raccourci noté : ${tr.lastCallDetail()}")
        T.ok((LlmPrompt.system("en", "fr", 5).length + prompts[0].length) / 3.0 <= window - LlmPrompt.maxOutputTokens(listOf("ELDER, PLEASE.")) - 64, "le message tient dans la fenêtre")
        T.ok(prompts[0].contains("Previous dialogue"), "une partie du dialogue précédent est gardée")
    }
}
