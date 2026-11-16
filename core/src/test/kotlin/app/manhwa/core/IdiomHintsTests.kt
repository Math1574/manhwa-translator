package app.manhwa.core

/** Expressions toutes faites (conseils au modèle, contrôle des calques), bégaiements, prompt 4. Les phrases d'essai sont inventées. */
fun idiomHintsTests() {
    fun hint(vararg lines: String) = IdiomHints.find(lines.toList())
    fun frenchOf(line: String) = IdiomHints.find(listOf(line)).joinToString(" / ") { it.french }

    T.test("expressions : la table se lit entièrement, sans doublon") {
        T.ok(IdiomHints.size() >= 120, "table assez fournie : ${IdiomHints.size()}")
        val ph = IdiomHints.phrases(); T.eq(ph.size, ph.toSet().size, "doublons : " + ph.groupBy { it }.filter { it.value.size > 1 }.keys)
    }
    T.test("expressions : une expression toute faite est repérée sous toutes ses formes, avec l'équivalent français") {
        T.ok(frenchOf("Killing two birds with one stone is a total win.").contains("pierre deux coups"), "kill two birds")
        for (s in listOf("Those three are getting away!", "He got away again.", "Don't let her get away!", "It gets away every time.")) T.ok(frenchOf(s).contains("s'enfuir"), s)
        T.ok(frenchOf("I'm going to get that monster on my side.").contains("rallier"), "get * on * side")
        T.ok(frenchOf("Get that thing out of my sight!").contains("ôter"), "get * out of * sight")
        T.ok(frenchOf("Or you could question the scum who are always at his side.").contains("racaille"), "scum")
        T.ok(frenchOf("Was that you running your mouth just now?").contains("l'ouvrir"), "run * mouth")
        T.ok(frenchOf("It doesn't sit right with me.").contains("plaire"), "sit * right with")
        T.ok(frenchOf("Cover your ears, everyone!").contains("bouchez-vous"), "cover your ears")
        T.ok(frenchOf("He didn't touch a single hair on my body.").contains("cheveu"), "touch * hair")
        T.ok(frenchOf("It's raining cats and dogs.").contains("cordes"), "rain cats and dogs")
        T.ok(frenchOf("I'm fed up with this.").contains("marre") && frenchOf("She was fed up.").contains("marre"), "être fed up")
    }
    T.test("expressions : le sens ordinaire n'est pas pris pour une expression (« get over here », « getting even stronger », chaussures, bras tordu, « he beats me »)") {
        for (s in listOf("I'm getting even stronger!", "Put on your shoes.", "He twisted his arm behind his back.", "He beats me every day.", "You went to the wrong room.", "Let him go down the stairs.", "She touched her hair.",
            "I want to think that you're right.", "He will break a leg if he falls.", "The carriage ran over the soldier.", "Let's go down.", "Put the sword in its place."))
            T.eq(emptyList<IdiomHints.Hit>(), hint(s), "pas de conseil pour : $s")
        T.ok(frenchOf("Get over here!").contains("viens ici") && !frenchOf("Get over here!").contains("surmonter"), "get over here : le plus long l'emporte")
        T.ok(frenchOf("I'll get over it.").contains("surmonter"), "get over it")
        T.ok(frenchOf("I'll get even with you!").contains("se venger"), "get even with")
        T.ok(frenchOf("Don't get me wrong.").contains("méprends"), "don't get me wrong")
        T.ok(frenchOf("Put yourself in my shoes.").contains("à la place"), "put yourself in my shoes")
        T.ok(frenchOf("Put him in his place.").contains("remettre à sa place"), "put him in his place")
        T.ok(frenchOf("You've let me down.").contains("décevoir") && frenchOf("Don't let them down.").contains("décevoir"), "let me down")
        T.ok(frenchOf("Don't you dare lay a finger on her!").contains("toucher"), "lay a finger on")
        T.ok(IdiomHints.calques("Don't you dare lay a finger on her!", "N'ose pas poser un doigt sur elle !").isNotEmpty(), "calque : poser un doigt")
        T.ok(frenchOf("His mother died giving birth to him.").contains("mettre au monde") && IdiomHints.calques("His mother died giving birth to him.", "Sa mère est morte en le donnant naissance.").isNotEmpty()
            && IdiomHints.calques("His mother died giving birth to him.", "Sa mère est morte en lui donnant le jour.").isEmpty(), "give birth to")
        T.ok(frenchOf("No matter what he says, I won't listen.").contains("peu importe ce que") && IdiomHints.calques("No matter what I try.", "Peu importe quoi j'essaie.").isNotEmpty(), "no matter what")
    }
    T.test("expressions : titres d'arts martiaux, l'expression la plus longue l'emporte") {
        val h = hint("S-Senior brother?!")
        T.eq(1, h.size, "« senior » seul n'est pas ajouté à « senior brother » : $h"); T.ok(h[0].french.contains("grand frère d'armes"), "senior brother")
        T.ok(frenchOf("The eldest young master is here.").startsWith("jeune maître aîné"), "eldest young master")
        T.ok(frenchOf("Senior, please help me.").contains("aîné"), "senior seul")
        T.ok(hint("He got away with it.").single().french.contains("impunément"), "get away with l'emporte sur get away")
    }
    T.test("expressions : numéro de ligne, limites, et aucune expression dans un dialogue ordinaire") {
        val h = hint("Hello.", "Get away from her!", "Nothing here.")
        T.eq(1, h.size); T.eq(2, h[0].line, "numéro de ligne")
        val many = IdiomHints.find(List(30) { "He got away and the scum ran." }, maxTotal = 10); T.eq(10, many.size, "au plus 10 par demande")
        T.eq(2, IdiomHints.find(listOf("Scum! He got away, I'm fed up, in heat!"), maxPerLine = 2).size, "au plus 2 par ligne")
        for (s in listOf("I want to go home.", "Where are you going?", "He made a deal with a demon.", "Please take care of her.", "Let's eat together.", "I will go with you.", "The sword is heavy.", "Make sure to rest.", "This is my sword.")) T.eq(emptyList<IdiomHints.Hit>(), hint(s), "pas de conseil pour : $s")
    }
    T.test("calques : le rendu mot à mot d'une expression est repéré, la bonne traduction ne l'est pas") {
        fun bad(src: String, tr: String) = IdiomHints.calques(src, tr).isNotEmpty()
        T.ok(bad("Killing two birds with one stone is a total win.", "Tuer deux oiseaux avec une seule pierre, c'est une victoire totale."), "oiseaux")
        T.ok(!bad("Killing two birds with one stone is a total win.", "Faire d'une pierre deux coups, c'est tout bénéfice."), "bonne traduction")
        T.ok(bad("It made me realize how weak I am.", "Ça m'a fait réaliser à quel point je suis faible."), "réaliser")
        T.ok(!bad("It made me realize how weak I am.", "Ça m'a fait comprendre à quel point je suis faible."), "comprendre")
        T.ok(!bad("I will realize my dream.", "Je réaliserai mon rêve."), "« realize my dream » est bien « réaliser »")
        T.ok(bad("This world will definitely be destroyed.", "Ce monde sera définitivement détruit."), "définitivement")
        T.ok(!bad("This world will definitely be destroyed.", "Ce monde sera forcément détruit."), "forcément")
        T.ok(bad("Not a chance! I'm the senior here!", "Pas une chance ! Je suis l'aîné ici !"), "pas une chance")
        T.ok(bad("Or you could question the scum.", "Ou vous pouvez interroger l'écume."), "écume")
        T.ok(bad("S-Senior brother?!", "S-SENIOR FRÈRE ?!") && !bad("S-Senior brother?!", "G-Grand frère d'armes ?!"), "senior")
        T.ok(bad("The eldest young master is here.", "Le plus jeune maître aîné est là.") && !bad("The eldest young master is here.", "Le jeune maître aîné est là."), "eldest young master")
        T.ok(bad("Cover your ears, everyone!", "Couvrir vos oreilles, tout le monde !") && !bad("Cover your ears, everyone!", "Bouchez-vous les oreilles, tout le monde !"), "cover your ears")
        T.ok(!bad("Nothing special here.", "Rien de spécial ici."), "aucune expression")
    }
    T.test("contrôles : un calque est un problème de la ligne (code « calque »), détaillé avec l'équivalent") {
        val p = TranslationChecks.problems("Killing two birds with one stone is a total win.", "Tuer deux oiseaux avec une seule pierre, c'est une victoire totale.", "fr")
        T.ok(p.any { it.code == "calque" && it.detail.contains("pierre deux coups") }, "calque signalé : $p")
        T.ok(TranslationChecks.problems("Killing two birds with one stone is a total win.", "Faire d'une pierre deux coups, c'est tout bénéfice.", "fr").none { it.code == "calque" }, "bonne traduction")
        T.ok(TranslationChecks.problems("Killing two birds with one stone", "Tuer deux oiseaux avec une pierre", "en").none { it.code == "calque" }, "seulement vers le français")
    }
    T.test("bégaiement : la lettre répétée est celle du mot français ; perdu, il est remis ; sans bégaiement dans l'original, rien ne change") {
        fun s(src: String, tr: String) = TranslationPost.stutter(src, tr)
        T.eq("M-MAIS IL...", s("B-BUT HE...", "P-MAIS IL..."))
        T.eq("M-Mais il...", s("B-But he...", "Mais il..."))
        T.eq("A-attends...", s("W-wait...", "E-attends..."))
        T.eq("G-GAF ! A-ATTENDEZ !", s("G-GAF! W-WAIT!", "G-GAF ! E-ATTENDEZ !"))
        T.eq("L-LE DÉMON ESSAIE", s("TH-THE DEMON TRIES", "LE DÉMON ESSAIE"))
        T.eq("« A-ATTENDS ! »", s("W-WAIT!", "« ATTENDS ! »"))
        T.eq("J-J'AI PEUR", s("I-I'M SCARED", "J'AI PEUR"))
        T.eq("LE DÉ-DÉMON ESSAIE", s("TH-THE DEMON TRIES", "LE DÉ-DÉMON ESSAIE"))     // déjà un bégaiement : intact
        T.eq("H-HEIN ?", s("H-HUH.?", "H-HEIN ?"))
        T.eq("ATTENDS !", s("WAIT!", "ATTENDS !"))                                       // pas de bégaiement dans l'original
        T.eq("CHASSEUR DE RANG S-RANK", s("S-RANK HUNTER", "CHASSEUR DE RANG S-RANK"))   // « S-RANK » n'est pas un bégaiement
        T.eq("A-t-il dit ça ?", s("W-wait, did he say that?", "A-t-il dit ça ?"))         // « a-t-il » n'est pas touché
        T.eq("S-RANK, M-MAIS...", s("S-RANK? B-BUT...", "S-RANK, P-MAIS..."))            // seul le bégaiement de l'original est compté : « S-RANK » est écarté
    }
    T.test("prompt 4 : règles contre le mot à mot, conseils d'expressions seulement s'il y en a, version précédente intacte") {
        T.eq(4, LlmPrompt.CURRENT)
        val v4 = LlmPrompt.system("en", "fr", 4)
        T.ok(v4.contains("Pas question") && v4.contains("OCR") && v4.contains("stutter") && v4.contains("jeune maître aîné"), "règles v4")
        T.ok(!LlmPrompt.system("en", "fr", 3).contains("Expressions") && !LlmPrompt.system("en", "fr", 3).contains("stutter"), "v3 inchangée")
        T.ok(LlmPrompt.system("en", "en", 4).contains("expert translator"), "autres langues : prompt générique")
        val h = IdiomHints.find(listOf("Hi.", "Those three are getting away!"))
        val u = LlmPrompt.user(listOf("Hi.", "Those three are getting away!"), emptyList(), emptyList(), null, emptyList(), h)
        T.ok(u.contains("Expressions in the lines below") && u.contains("- line 2, \"getting away\": s'enfuir"), "conseils dans le message : $u")
        T.ok(!LlmPrompt.user(listOf("Hi."), emptyList(), emptyList()).contains("Expressions"), "pas de conseil, pas de section")
        T.ok(LlmPrompt.fixUser("Get away!", "Se lever !", listOf("calque: x"), emptyList(), emptyList(), emptyList(), IdiomHints.find(listOf("Get away!"))).contains("s'enfuir"), "conseils dans la correction")
    }
}
