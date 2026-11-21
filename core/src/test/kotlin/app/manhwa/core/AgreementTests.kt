package app.manhwa.core

/**
 * Accords de la traduction (tu / vous, genre du narrateur), noms courts du chapitre, signes japonais lus à la place de lettres, noms du chapitre mal lus.
 * Les phrases d'essai sont inventées ; elles reprennent la forme des défauts relevés (« REPRENDS-TOI, TOUT LE MONDE ! SI VOUS… », « G-GAF » traduit « A-ATTENDS »).
 */
fun agreementTests() {
    fun accord(src: String, tr: String, names: List<String> = emptyList(), style: StyleMemory? = null) =
        TranslationChecks.problems(src, tr, "fr", names, style).filter { it.code == "accord" }
    fun nom(src: String, tr: String, names: List<String>) = TranslationChecks.problems(src, tr, "fr", names).any { it.code == "nom" }

    T.test("tu / vous : une foule interpellée se vouvoie partout, y compris « reprends-toi » (défaut relevé)") {
        T.ok(accord("GET A GRIP, EVERYONE! IF YOU BARGAIN WITH A DEMON, IT'LL TAKE YOUR SOUL!!", "Reprends-toi, tout le monde ! Si vous faites un marché avec un démon, il vous prendra l'âme !!").isNotEmpty(), "tu + vous + foule")
        T.ok(accord("GET A GRIP, EVERYONE! IF YOU BARGAIN WITH A DEMON, IT'LL TAKE YOUR SOUL!!", "Reprenez-vous, tout le monde ! Si vous faites un marché avec un démon, il vous prendra l'âme !!").isEmpty(), "tout en vous")
        T.ok(accord("YOU ALL KNOW THE RULES.", "Tu connais les règles.").isNotEmpty(), "you all")
        T.ok(accord("PULL YOURSELVES TOGETHER!", "Ressaisis-toi !").isNotEmpty(), "yourselves")
        T.ok(accord("YOU FOOLS! DO YOU HAVE ANY IDEA WHAT YOU'VE DONE?", "Tu es fou ! Tu sais ce que tu as fait ?").isNotEmpty(), "you + nom pluriel")
        T.ok(accord("YOU TWO, COME WITH ME.", "Venez avec moi, vous deux.").isEmpty(), "you two en vous")
    }
    T.test("tu / vous : « everyone » complément n'est pas une foule interpellée") {
        T.ok(accord("YOU KILLED EVERYONE!", "Tu as tué tout le monde !").isEmpty(), "complément")
        T.ok(accord("WHY DID YOU TELL EVERYBODY ABOUT IT?", "Pourquoi as-tu tout raconté à tout le monde ?").isEmpty(), "complément 2")
        T.ok(accord("LISTEN UP, EVERYONE! YOU MUST HEAR THIS.", "Écoutez-moi, tout le monde ! Vous devez entendre ça.").isEmpty(), "apostrophe en vous")
        T.ok(accord("LISTEN UP, EVERYONE! YOU MUST HEAR THIS.", "Écoute-moi, tout le monde ! Tu dois entendre ça.").isNotEmpty(), "apostrophe en tu")
    }
    T.test("tu / vous : « tu » et « vous » mêlés dans la même ligne") {
        T.ok(accord("MAKE NO MISTAKE. YOU'RE THE ONES WHO NEED THIS, NOT ME.", "Ne te méprends pas. C'est vous qui en avez besoin, pas moi.").isNotEmpty(), "mélange")
        T.ok(accord("DO YOU KNOW YOUR PLACE?", "Sais-tu quelle est votre place ?").isNotEmpty(), "mélange 2")
        T.ok(accord("DO YOU KNOW YOUR PLACE?", "Sais-tu quelle est ta place ?").isEmpty(), "tu seulement")
        T.ok(accord("DO YOU KNOW YOUR PLACE?", "Connaissez-vous votre place ?").isEmpty(), "vous seulement")
        T.ok(accord("I'M SORRY. THE TONE OF YOUR VOICE SCARES ME.", "Désolé. Le ton de votre voix me fait peur.").isEmpty(), "« ton » nom commun")
        T.ok(accord("SHOW ME YOUR SWORD.", "Montre-moi ton épée.").isEmpty(), "ton possessif seul")
    }
    T.test("tu / vous : formes reconnues") {
        fun f(s: String) = FrenchAgreement.forms(s).let { it.tu to it.vous }
        T.eq(1 to 0, f("Reprends-toi !")); T.eq(1 to 0, f("T'es fou ?")); T.eq(1 to 0, f("Je t'aime.")); T.eq(2 to 0, f("Tu as tes raisons."))
        T.eq(0 to 2, f("Vous et votre épée.")); T.eq(0 to 1, f("Allez-vous partir ?")); T.eq(0 to 0, f("Il a dit qu'il y va.")); T.eq(0 to 0, f("Sur un ton sec, il part."))
    }
    T.test("tu / vous : l'interlocuteur nommé garde son registre d'une ligne à l'autre (défaut relevé : « Gaf » tutoyé puis vouvoyé)") {
        val st = StyleMemory(); val names = listOf("Gaf")
        for ((s, t) in listOf("GAF, WHAT DO YOU THINK?" to "Gaf, qu'en penses-tu ?", "WHAT ABOUT YOU, GAF? WILL YOU STAB ME?" to "Et toi, Gaf ? Vas-tu me transpercer ?", "GAF, YOU BASTARD!" to "Gaf, espèce de salaud ! Tu veux vraiment le détruire ?")) st.observe(s, t, names)
        T.eq(FrenchAgreement.Register.TU, st.registerWith("gaf"))
        T.ok(accord("G-GAF. PLEASE..!", "G-Gaf. S'il vous plaît..!", names, st).isNotEmpty(), "vouvoyé alors qu'on le tutoie")
        T.ok(accord("G-GAF, PLEASE SPARE ME.", "G-Gaf, s'il te plaît, épargne-moi.", names, st).isEmpty(), "tutoyé comme avant")
        T.ok(accord("GAF IS RIGHT. YOU SHOULD LISTEN.", "Gaf a raison. Vous devriez écouter.", names, st).isEmpty(), "nom cité sans être interpellé : aucune contrainte")
        // sans mémoire, pas de contrainte
        T.ok(accord("G-GAF. PLEASE..!", "G-Gaf. S'il vous plaît..!", names, StyleMemory()).isEmpty(), "pas de mémoire")
    }
    T.test("tu / vous : un chapitre qui tutoie très nettement signale la ligne vouvoyée sans foule ni supérieur, jamais les autres") {
        val st = StyleMemory()
        for (i in 1..12) st.observe("WHAT ARE YOU DOING $i?", "Qu'est-ce que tu fais $i ?", emptyList())
        T.eq(FrenchAgreement.Register.TU, st.usualRegister())
        T.ok(accord("ARE YOU REALLY GOING TO STOP THERE?", "Allez-vous vraiment vous arrêter là ?", emptyList(), st).isNotEmpty(), "vous seul dans un chapitre en tu")
        T.ok(accord("ARE YOU REALLY GOING TO STOP THERE?", "Tu vas vraiment t'arrêter là ?", emptyList(), st).isEmpty(), "tu")
        T.ok(accord("WILL YOU FOLLOW ME, MY LORD?", "Allez-vous me suivre, monseigneur ?", emptyList(), st).isEmpty(), "supérieur")
        T.ok(accord("YOU ALL STAY HERE.", "Restez ici, vous tous.", emptyList(), st).isEmpty(), "foule")
        val few = StyleMemory(); for (i in 1..5) few.observe("WHAT ARE YOU DOING $i?", "Qu'est-ce que tu fais $i ?", emptyList())
        T.ok(accord("ARE YOU REALLY GOING TO STOP THERE?", "Allez-vous vraiment vous arrêter là ?", emptyList(), few).isEmpty(), "trop peu de lignes pour conclure")
    }
    T.test("tu / vous : une foule ou un supérieur vouvoyé ne change pas le registre habituel du chapitre") {
        val st = StyleMemory()
        for (i in 1..8) st.observe("WHAT ARE YOU DOING $i?", "Qu'est-ce que tu fais $i ?", emptyList())
        for (i in 1..8) st.observe("EVERYONE, WHAT ARE YOU DOING $i?", "Tout le monde, que faites-vous $i ?", emptyList())
        for (i in 1..8) st.observe("MY LORD, WHAT ARE YOU DOING $i?", "Monseigneur, que faites-vous $i ?", emptyList())
        T.eq(FrenchAgreement.Register.TU, st.usualRegister())
        T.ok(st.hints().any { it.contains("\"tu\"") }, "rappel du tutoiement")
        val mixed = StyleMemory()
        for (i in 1..6) mixed.observe("WHAT ARE YOU DOING $i?", "Qu'est-ce que tu fais $i ?", emptyList())
        for (i in 1..6) mixed.observe("WHAT ARE YOU SAYING $i?", "Que dites-vous $i ?", emptyList())
        T.eq(null, mixed.usualRegister()); T.ok(mixed.hints().none { it.contains("Register") }, "pas de rappel quand c'est partagé")
    }
    T.test("genre du narrateur : l'accord de « je suis… » se lit, les mots sans genre sont ignorés") {
        fun g(s: String) = FrenchAgreement.narratorGenders(s).map { it.first }
        T.eq(listOf(FrenchAgreement.Gender.F), g("Je suis à moitié humaine, malgré mon apparence."))
        T.eq(listOf(FrenchAgreement.Gender.M), g("Je suis à moitié humain, malgré mon apparence."))
        T.eq(listOf(FrenchAgreement.Gender.M), g("Je me suis transmigré dans ce roman."))
        T.eq(listOf(FrenchAgreement.Gender.F), g("J'étais fatiguée."))
        T.eq(listOf(FrenchAgreement.Gender.M), g("Je ne suis pas prêt."))
        T.eq(listOf(FrenchAgreement.Gender.M), g("Comment suis-je censé lui faire confiance ?"))
        for (s in listOf("Je suis d'accord.", "Je suis ici.", "Je suis un étranger.", "Je suis en danger.", "Je suis là.", "Je suis honnête.", "Je suis toujours là.", "Je suis souvent seul.")) T.eq(emptyList<FrenchAgreement.Gender>(), g(s), s)
    }
    T.test("genre du narrateur : rappelé au modèle quand il est constant, jamais imposé par un contrôle") {
        val st = StyleMemory()
        for (s in listOf("Je suis prêt.", "Je suis fatigué.", "Je suis humain.", "Je suis désolé.")) st.observe("I'M READY.", s, emptyList())
        T.eq(FrenchAgreement.Gender.M, st.narratorGender())
        T.ok(st.hints().any { it.contains("male") }, "rappel")
        T.ok(accord("I'M HALF HUMAN.", "Je suis à moitié humaine.", emptyList(), st).isEmpty(), "pas de contrôle sur le genre : on ne sait pas qui parle")
        st.observe("I'M HAPPY.", "Je suis ravie.", emptyList())
        T.eq(null, st.narratorGender()); T.ok(st.hints().none { it.contains("male") }, "plus de rappel quand les deux genres paraissent")
        val few = StyleMemory(); for (s in listOf("Je suis prêt.", "Je suis fatigué.")) few.observe("I'M READY.", s, emptyList())
        T.eq(null, few.narratorGender())
    }
    T.test("style : la copie est indépendante, remise à zéro, rappels dans les consignes") {
        val st = StyleMemory(); for (i in 1..7) st.observe("WHAT ARE YOU DOING?", "Qu'est-ce que tu fais ?", emptyList())
        val c = st.copy(); for (i in 1..20) c.observe("WHAT ARE YOU SAYING?", "Que dites-vous ?", emptyList())
        T.eq(FrenchAgreement.Register.TU, st.usualRegister()); T.ok(c.usualRegister() != FrenchAgreement.Register.TU, "la copie a changé, pas l'original")
        st.reset(); T.eq(null, st.usualRegister()); T.eq(emptyList<String>(), st.hints())
        val hints = listOf("Register so far: one person is addressed with \"tu\"")
        T.ok(LlmPrompt.user(listOf("Hi"), emptyList(), emptyList(), null, emptyList(), emptyList(), hints).contains("Established in this chapter") && LlmPrompt.user(listOf("Hi"), emptyList(), emptyList(), null, emptyList(), emptyList(), hints).contains("Register so far"), "user")
        T.ok(LlmPrompt.fixUser("Hi", "Salut", listOf("accord: x"), emptyList(), emptyList(), emptyList(), emptyList(), hints).contains("Register so far"), "fixUser")
        T.ok(!LlmPrompt.user(listOf("Hi"), emptyList(), emptyList()).contains("Established in this chapter"), "rien quand il n'y a rien à rappeler")
        T.ok(LlmPrompt.system("en", "fr", 4).contains("several people") && LlmPrompt.system("en", "fr", 4).contains("never both in the same line"), "règle 5")
    }

    // ---------- noms courts et noms appris à tort ----------
    T.test("noms : un nom de trois lettres est appris après trois lignes, jamais un mot courant ni une interjection") {
        val m = ChapterMemory()
        m.observe("GAF, WHAT DO YOU THINK?", "Gaf, qu'en penses-tu ?"); m.observe("LOGICALLY, GAF IS RIGHT.", "Logiquement, Gaf a raison.")
        T.ok("Gaf" !in m.namesToKeep(), "deux lignes ne suffisent pas")
        m.observe("G-GAF, PLEASE SPARE ME.", "G-Gaf, épargne-moi.")
        T.ok("Gaf" in m.namesToKeep(), "noms ${m.namesToKeep()}"); T.ok(m.namesToKeep().none { it.contains('-') }, "pas de « G-gaf » : le bégaiement n'est pas un nom")
        val c = ChapterMemory()
        for (i in 1..4) { c.observe("THE AIR IS COLD. SON, AIR, THE ONE.", "L'air est froid. Son, air, le un."); c.observe("AHH! HEE HEE!", "Ahh ! Hee hee !") }
        T.eq(emptyList<String>(), c.namesToKeep())
        val k = ChapterMemory(isCommonWord = { it == "kim" })
        for (i in 1..4) k.observe("KIM IS HERE. ZOD TOO.", "Kim est là. Zod aussi.")
        T.ok("Zod" in k.namesToKeep() && "Kim" !in k.namesToKeep(), "le dictionnaire écarte « Kim » : ${k.namesToKeep()}")
    }
    T.test("noms : un mot écrit pareil dans les deux langues n'est pas un nom (« village », « long »), un vrai nom l'est même au possessif") {
        val m = ChapterMemory(isCommonWord = { it in setOf("village", "long", "change", "saint") })
        for (i in 1..3) {
            m.observe("IT'S A LONG WAY TO THE VILLAGE.", "C'est un long chemin jusqu'au village.")
            m.observe("THE VILLAGE WILL CHANGE.", "Le village va changer.")
            m.observe("LONG LIVE THE KING.", "Long règne au roi.")
            m.observe("KANDEYA'S MEN ARE HERE. THE KANDEYA COMPANY!", "Les hommes de Kandeya sont là. La compagnie Kandeya !")
        }
        T.eq(listOf("Kandeya"), m.namesToKeep(), "noms ${m.namesToKeep()}")
        // une traduction toute en majuscules ne permet pas de juger : comportement d'avant
        val u = ChapterMemory(); u.observe("HWANG IS HERE.", "HWANG EST LÀ."); u.observe("WHERE IS HWANG?", "OÙ EST HWANG ?")
        T.ok("Hwang" in u.namesToKeep(), "majuscules : ${u.namesToKeep()}")
        // un mot courant qui est aussi un nom (« Rose ») compte quand il porte une majuscule en cours de phrase
        val r = ChapterMemory(isCommonWord = { it == "rose" })
        r.observe("ROSE IS HERE.", "Rose est là."); r.observe("ROSE IS HERE AGAIN.", "Rose est encore là.")
        T.ok("Rose" !in r.namesToKeep(), "début de phrase seulement : pas de preuve")
        r.observe("I SAW ROSE.", "J'ai vu Rose."); r.observe("WITH ROSE.", "Avec Rose.")
        T.ok("Rose" in r.namesToKeep(), "en cours de phrase : ${r.namesToKeep()}")
    }
    T.test("noms : le contrôle « nom modifié » vaut pour les noms courts, en mot entier (le défaut « G-GAF » → « A-ATTENDS »)") {
        T.ok(nom("G-GAF! W-WAIT!", "A-Attends ! A-Attends !", listOf("Gaf")), "nom perdu")
        T.ok(!nom("G-GAF! W-WAIT!", "G-Gaf ! A-Attends !", listOf("Gaf")), "nom gardé")
        T.ok(!nom("IMPORTANT NEWS!", "Nouvelles importantes !", listOf("Por")), "« por » n'est pas dans « important »")
        T.ok(nom("WE SHOULD RIDE TO POR VILLAGE.", "Nous devrions aller au village important.", listOf("Por")), "« por » absent de la traduction")
        T.ok(!nom("WE SHOULD RIDE TO POR VILLAGE.", "Nous devrions aller au village de Por.", listOf("Por")), "« Por » présent")
        T.ok(nom("KANDEYA'S MEN!", "Les hommes de la compagnie !", listOf("Kandeya")), "nom long, comme avant")
        T.ok(!nom("KANDEYA'S MEN!", "Les hommes de Kandeya !", listOf("Kandeya")), "nom long gardé")
    }

    // ---------- lecture : signes japonais pris pour des lettres, noms du chapitre mal lus ----------
    val lex = EnglishLexicon(
        "and that bitch was considered premium merchandise too so i imagine he pocketed a much larger sum for her wait what here is the mercenary company ride to village waited".split(" "),
        "the and a to i he was so that for her is".split(" "),
    )
    fun fix(s: String, protect: Set<String> = emptySet()) = LookalikeGlyphs.fix(s, lex, protect)
    fun rep(s: String, protect: Set<String> = emptySet()) = OcrRepair.repair(s, lex, protect)
    T.test("lecture : « し » et « ル » lus à la place de « L » et « IL » dans un mot latin (défaut relevé : « ILEA »)") {
        T.eq("AND ILEA? THAT BITCH...", fix("AND IしEA? THAT BITCH..."))
        T.eq("ILEA WAS CONSIDERED PREMIUM MERCHANDISE, TOO, SO I IMAGINE HE POCKETED A MUCH LARGER SUM FOR HER.", rep("ルEA WAS CONSIDERED PREMIUM MERCHANDISE, TOO, SO 1 IMAGINE HE POCKETED A MUCH LARGER SUM FOR HER."))
        T.eq("ILEA!", fix("ルEA!")); T.eq("Ilea", fix("Iしea"))
        T.eq("AND ILEA?", rep("AND IしEA?", setOf("ilea")))
        // le nom du chapitre l'emporte sur un mot du dictionnaire quand les deux se lisent
        T.eq("ILEA", fix("ルEA", setOf("ilea")))
    }
    T.test("lecture : tiret, points et signes de largeur pleine lus à la japonaise dans un texte anglais") {
        T.eq("WAIT - WHAT", fix("WAIT ー WHAT")); T.eq("W-WAIT", fix("W一WAIT")); T.eq("HERE...", fix("HERE・・・")); T.eq("WHAT?!", fix("WHAT？！"))
        T.eq("WHAT ARE YOU DOING.", fix("WHAT ARE YOU DOING。"))
    }
    T.test("lecture : un signe sans équivalent connu disparaît, du vrai japonais n'est jamais touché") {
        T.eq("THE WAS OPEN", fix("THE 鬱 WAS OPEN")); T.eq("HELLO WORLD", fix("HELLO W鬱ORLD"))
        for (s in listOf("こんにちは", "こんにちは ABC", "HELLO こんにちは WORLD", "ありがとう。", "ドン!", "ルEA ありがとう")) T.eq(s, fix(s), s)
        T.eq("PLAIN TEXT", fix("PLAIN TEXT")); T.eq("PLAIN", fix("PLAIN"))
    }
    T.test("lecture : un nom du chapitre mal lu à une lettre près redevient ce nom, jamais un mot du dictionnaire") {
        val names = setOf("kandeya", "ilea")
        T.eq("THE KANDEYA MERCENARY COMPANY", rep("THE RANDEYA MERCENARY COMPANY", names))
        T.eq("THE KANDEYA MERCENARY COMPANY", rep("THE KANDEA MERCENARY COMPANY", names))
        T.eq("THE KANDEYA'S MERCENARY COMPANY", rep("THE RANDEYA'S MERCENARY COMPANY", names))
        T.eq("THE WAITED MERCENARY COMPANY", rep("THE WAITED MERCENARY COMPANY", setOf("waiter")))     // mot du dictionnaire : intact
        T.eq("THE XANDEYA MERCENARY COMPANY", rep("THE XANDEYA MERCENARY COMPANY", setOf("kandeya", "randeya")))   // deux noms également proches : on ne devine pas
        T.eq("THE KANDEYA MERCENARY COMPANY", rep("THE KANDEYA MERCENARY COMPANY", names))
        T.eq("THE GAP MERCENARY COMPANY", rep("THE GAP MERCENARY COMPANY", setOf("gaf")))             // un nom de trois lettres n'attire rien
    }
}
