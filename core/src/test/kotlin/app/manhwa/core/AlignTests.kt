package app.manhwa.core

import java.io.File

private val alignLexicon: EnglishLexicon by lazy {
    val dir = listOf("app/src/main/assets/lexicon", "../app/src/main/assets/lexicon", "../../app/src/main/assets/lexicon").map { File(it) }.firstOrNull { File(it, "enable1.txt").isFile }
        ?: throw AssertionError("lexique introuvable (dossier app/src/main/assets/lexicon)")
    EnglishLexicon(File(dir, "enable1.txt").readLines(), File(dir, "g10k.txt").readLines(), File(dir, "genre.txt").readLines())
}

/** Réponses du modèle décalées ou mélangées (journal du 22/11) : repérées ligne par ligne ; charabia de l'OCR et crédits écartés avant la traduction. */
fun alignTests() {
    T.test("alignement : réponse décalée d'un cran (la note « Yuzu » dans la bulle d'avant) — les lignes décalées sont repérées, les justes jamais") {
        val src = listOf(
            "I WAS WORRIED THAT... YOU MIGHT FEEL SICK OR BE UPSET IF YOU SAW MY EYES... THAT'S WHY I WAS WEARING IT...",
            "GRIAN MIATRO... THAT PIECE OF ABSOLUTE GARBAGE.",
            "AFTER DOING THIS TO HIS OWN SON, HE STILL HAD THE NERVE TO SAY SOMETHING LIKE THAT?!",
            "I DON'T PARTICULARLY CARE ABOUT SOMETHING LIKE THAT.",
            "TAKE THAT BLINDFOLD OFF. IT'S MORE UNPLEASANT TO LOOK AT.",
            "THANK YOU.",
            "UM... WHAT SHOULD I CALL YOU?",
            "I DIDN'T GIVE YOU MY NAME WHEN WE MADE THE CONTRACT? CALL ME JOHANN.",
            "Yuzu: Even though his original name is Kim Yohan. I decided to stylize it as Johann to fit the non-Asian setting.",
            "JOHANN, THERE'S SOMETHING I WAS WONDERING ABOUT IN THE BOOK YOU READ TO ME YESTERDAY.",
        )
        val tr = listOf(
            "J'avais peur que... tu te sentes mal ou que tu sois contrarié en voyant mes yeux... C'est pour ça que je le portais...",
            "Grian Miatro... ce tas d'ordures.",
            "Après avoir fait ça à son propre fils, il a encore le culot de dire une chose pareille ?!",
            "Ça ne m'importe pas particulièrement.",
            "Enlève ce bandeau. C'est plus désagréable à regarder.",
            "Merci.",
            "Euh... Comment dois-je t'appeler ?",
            "Yuzu : même si son vrai nom est Kim Yohan, j'ai décidé de le styliser en Johann pour coller au cadre non asiatique.",
            "Johann, il y a quelque chose que je me demandais à propos du livre que tu m'as lu hier.",
            "Johann, j'avais une question concernant le livre que tu m'as lu hier.",
        )
        val s = TranslationAlign.suspects(src, tr, alignLexicon)
        T.ok(7 in s && 8 in s, "lignes décalées repérées : $s")
        T.ok(s.none { it < 7 }, "les lignes justes ne sont pas touchées : $s")
        T.eq(emptySet<Int>(), TranslationAlign.suspects(src.take(7), tr.take(7), alignLexicon), "les mêmes lignes, bien placées : rien")
    }

    T.test("alignement : traductions de lignes plus loin placées sous d'autres numéros (même sens deux fois) — repérées") {
        val src = listOf(
            "NOW THAT WE'RE DONE, AREN'T YOU GOING BACK?",
            "YOU SAID THERE MIGHT BE ANOTHER THREE EYES!",
            "I have to obey my master's orders!",
            "... NOT A BAD OFFER, BUT I DON'T TRUST PEOPLE EASILY.",
            "I KEEP MY PROMISES! I HELD UP MY END OF OUR \"DEAL\" DIDN'T I?!",
        )
        val tr = listOf(
            "Maintenant que c'est fait, tu ne rentres pas ?",
            "... Pas une mauvaise offre, mais je ne fais pas confiance aux gens facilement.",
            "Je tiens mes promesses ! J'ai tenu ma part de notre « accord », n'est-ce pas ?!",
            "... Ce n'est pas une mauvaise offre, mais je ne fais pas confiance aux gens facilement.",
            "Je tiens mes promesses ! J'ai tenu ma part de notre « accord », n'est-ce pas ?!",
        )
        val s = TranslationAlign.suspects(src, tr, alignLexicon)
        T.ok(1 in s && 2 in s, "les deux lignes mal placées : $s")
        T.ok(0 !in s, "la première ligne est juste : $s")
    }

    T.test("alignement : une page normale (noms, répliques courtes, variantes de lecture d'un nom) ne déclenche rien") {
        val src = listOf(
            "GAF, WHAT DO YOU THINK?", "GAF? DID HE SAY GAF?", "GAF, COMMANDER OF THE KANDEYA MERCENARY COMPANY?", "... THAT'S A REASONABLE ALTERNATIVE.",
            "KANDEA? COULD IT BE FATHER?", "THANK YOU.", "YES.", "OR BARGAIN WITH ME AND FIND YOUR DAUGHTER, ILEA?", "I SAID. STOP!!",
        )
        val tr = listOf(
            "Gaf, qu'en penses-tu ?", "Gaf ? Il a dit Gaf ?", "Gaf, le commandant de la compagnie de mercenaires Kandeya ?", "... C'est une alternative raisonnable.",
            "Kandeya ? Serait-ce mon père ?", "Merci.", "Oui.", "Ou faire un marché avec moi et retrouver ta fille, Ilea ?", "J'ai dit : arrête !",
        )
        T.eq(emptySet<Int>(), TranslationAlign.suspects(src, tr, alignLexicon))
        T.eq(emptySet<Int>(), TranslationAlign.suspects(src, tr.mapIndexed { i, t -> if (i == 3) null else t }, alignLexicon), "une ligne absente n'est pas « suspecte » (elle est refaite à part)")
    }

    T.test("alignement : une traduction démesurée (trois fois plus longue que son texte) est suspecte") {
        val src = listOf("THAT'S AN UNDERSTATEMENT ABOUT ALL OF THIS...", "OK.")
        val tr = listOf("C'est un euphémisme. Il continue de consommer du mana jusqu'à ce que tu le retires, et ensuite il faut encore le recharger pendant des heures, ce qui prend un temps fou chaque jour.", "D'accord.")
        T.ok(0 in TranslationAlign.suspects(src, tr, alignLexicon), "démesurée")
    }

    T.test("lecture : charabia d'un bruitage (signes et chiffres mêlés) écarté ; crédits et bandeau du site aussi ; vraies répliques gardées") {
        for (t in listOf("#<M sQN <1r √I > U8S W>Y8 XI JV1>U8S W>xX 8<M SQn<1MF JJ1 UXS LX", "J<M SEn <11 1313 U8S 4>18 eien sen <IPT 1N41> U8S U>8 eSM SEn CIPT", "XINT U8>OW>tX no<ir"))
            T.ok(JunkFilter.isGibberish(t, alignLexicon), "charabia : $t")
        for (t in listOf("WHAT THE-Z! IS THIS ME?!", "S/X ORDINARY NOBLES AT THE HOUSE HEAD LEVEL... AND YET, ABSORBING THEIR MANA BARELY MADE A DIFFERENCE.", "GAF, COMMANDER OF THE KANDEYA MERCENARY COMPANY?",
            "AAAAAGH!!", "N. H... AAAAAG!!", "THE GORGE OF DEATH...", "I HEARD THE OTHER SIDE HAS ALREADY GATHERED ABOUT 300 MEN.", "IS THIS `BODY POSSESSION\" LIKE WHAT HAPPENS SO OFTEN IN NOVELS...?",
            "MORE, I NEED MORE; I NEEDS MORE SUFFERING! MORE SUED", "<ILEA THE GREAT WITCH)", "[COMMANDER OF THE RANDEYA MERCENARY COMPANY"))
            T.ok(!JunkFilter.isGibberish(t, alignLexicon), "réplique gardée : $t")
        for (t in listOf("ORIGINAL: WATERMELON PEACH STORY: KIM HONEYLICK ART: YU", "THE GRIMM ENTERTAINMENT", "for the fastest releases"))
            T.ok(JunkFilter.isWatermark(t), "crédits / bandeau : $t")
        for (t in listOf("Yuzu: Even though his original name is Kim Yohan.", "THE ART OF WAR", "WHAT'S YOUR STORY?", "THAT'S THE ORIGINAL PLAN."))
            T.ok(!JunkFilter.isWatermark(t), "réplique : $t")
    }

    T.test("prompt : les autres lignes du lot sont données comme contexte quand seules quelques lignes sont refaites") {
        val u = LlmPrompt.user(listOf("CALL ME JOHANN."), emptyList(), emptyList(), page = listOf("THANK YOU." to "Merci."))
        T.ok(u.contains("Other lines of these pages, already translated") && u.contains("- THANK YOU. => Merci.") && u.trimEnd().endsWith("1| CALL ME JOHANN."), u)
        T.ok(!LlmPrompt.user(listOf("A"), emptyList(), emptyList()).contains("Other lines"), "sans lot : pas de section")
    }

    T.test("expressions : calques relevés le 22/11 (« what's wrong with », « to think », « malice », « his lordship »…) — repérées et rendu mot à mot reconnu") {
        fun hint(s: String) = IdiomHints.find(listOf(s)).joinToString(" ") { it.french }
        T.ok(hint("WHAT'S WRONG WITH MY HANDS...?").contains("qu'est-ce qu'"), "what's wrong with")
        T.ok(IdiomHints.calques("WHAT'S WRONG WITH MY HANDS...?", "Qu'est-ce qui ne va pas avec mes mains... ?").isNotEmpty(), "calque what's wrong with")
        T.ok(IdiomHints.calques("TO THINK I'VE TRANSMIGRATED INTO THIS NOVEL...", "De penser que je me suis transmigré dans ce roman...").isNotEmpty(), "calque to think")
        T.ok(IdiomHints.calques("TO THINK I'VE TRANSMIGRATED INTO THIS NOVEL...", "Dire que je me suis retrouvé dans ce roman...").isEmpty(), "bonne traduction de « to think »")
        T.ok(IdiomHints.calques("HAS IT ALLOWED MALICE TO CONSUME ME?", "Est-ce que ça a laissé la malice me consumer ?").isNotEmpty(), "malice")
        T.ok(IdiomHints.calques("WE'VE CREMATED THE BODIES.", "Nous avons crémé les corps.").isNotEmpty(), "cremate")
        T.ok(IdiomHints.calques("HIS LORDSHIP SHOULD HAVE NO TROUBLE PAYING.", "Votre Seigneurie ne devrait pas avoir de mal à payer.").isNotEmpty(), "his lordship")
        T.ok(IdiomHints.calques("HIS LORDSHIP SHOULD HAVE NO TROUBLE PAYING.", "Sa Seigneurie ne devrait pas avoir de mal à payer.").isEmpty(), "Sa Seigneurie juste")
        T.ok(IdiomHints.calques("SOME COLOR HAS RETURNED TO HIS FACE.", "Une couleur est revenue sur son visage.").isNotEmpty(), "couleurs")
        T.ok(IdiomHints.calques("IF THE ORDER LEARNS OF THIS, YOU'LL BURN.", "Si l'Ordre en apprend, vous brûlerez.").isNotEmpty(), "learn of")
        T.ok(IdiomHints.calques("MAKE NO MISTAKE. YOU'RE THE ONES WHO NEED THIS, NOT ME.", "Ne te méprends pas. C'est toi qui en as besoin, pas moi.").isNotEmpty(), "you're the ones")
    }

    T.test("prompt : le synopsis de l'œuvre est donné comme contexte (qui sont les personnages), raccourci à une fin de phrase") {
        val syn = "Kim Yohan, un homme qui a toujours aidé les autres, meurt en sauvant des orphelins. Il se réveille dans le corps d'un demi-démon, au cœur du roman le plus cruel qu'il ait lu. " +
            "Pour survivre, il passe un pacte avec Gaf, un mercenaire qui cherche sa fille Ilea. " + "Mais les démons ont besoin de souffrance humaine pour grandir, et l'Ordre traque tous les sangs-mêlés. ".repeat(3)
        val p = LlmPrompt.premiseOf(syn)!!
        T.ok(p.length <= LlmPrompt.PREMISE_MAX && p.endsWith(".") && p.startsWith("Kim Yohan, un homme"), p)
        val u = LlmPrompt.user(listOf("I'M HALF HUMAN."), emptyList(), emptyList(), premise = syn)
        T.ok(u.contains("Story premise (background only") && u.contains("Kim Yohan, un homme"), u)
        T.eq(null, LlmPrompt.premiseOf("  "), "vide")
        T.ok(!LlmPrompt.user(listOf("A"), emptyList(), emptyList(), premise = null).contains("Story premise"), "sans synopsis : rien")
    }

    T.test("contrôles : syllabe doublée inventée (« QU'EST-EST-CE », « Y A-A-T-IL ») signalée ; vrai bégaiement de l'original jamais") {
        T.ok("répétition" in TranslationChecks.problems("WHAT IS IT? IS SOMETHING WRONG?", "Qu'est-est-ce que c'est ? Y a-a-t-il un problème ?", "fr").map { it.code }, "inventée")
        T.ok("répétition" !in TranslationChecks.problems("WHAT IS IT? IS SOMETHING WRONG?", "Qu'est-ce que c'est ? Y a-t-il un problème ?", "fr").map { it.code }, "normale")
        T.ok("répétition" !in TranslationChecks.problems("B-BUT... W-WAIT!", "M-Mais... A-Attends !", "fr").map { it.code }, "bégaiement de l'original")
    }

    T.test("relecture : consigne d'éditeur avec exemples du journal, contexte (synopsis, noms, tutoiement), étiquette recopiée retirée") {
        val sys = LlmPrompt.refineSystem()
        T.ok(sys.contains("Qu'est-ce qu'elles ont, mes mains") && sys.contains("copy it unchanged") && sys.contains("N| edited translation"), "consigne")
        val u = LlmPrompt.refineUser(listOf("WHAT'S WRONG WITH MY HANDS...?"), listOf("Qu'est-ce qui ne va pas avec mes mains... ?"), emptyList(), emptyList(), "Whispers", "Kim Yohan, un homme bon, se réincarne en demi-démon.", listOf("Gaf"), listOf("narrateur masculin"))
        T.ok(u.contains("Story premise") && u.contains("Names to keep exactly as written: Gaf") && u.contains("narrateur masculin") && u.contains("1| draft: Qu'est-ce qui ne va pas"), u)
        T.eq("Qu'est-ce qu'elles ont, mes mains... ?", LlmPrompt.stripRefineLabel("final: Qu'est-ce qu'elles ont, mes mains... ?"))
        T.eq("Bon, d'accord.", LlmPrompt.stripRefineLabel("Bon, d'accord."))
    }
}
