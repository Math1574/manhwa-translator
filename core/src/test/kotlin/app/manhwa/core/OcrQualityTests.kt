package app.manhwa.core

import java.io.File

/**
 * Lexique réel de l'application (mots valides + mots fréquents + vocabulaire du genre) : la réparation du texte lu est testée comme elle tourne sur le téléphone.
 * Les phrases d'essai sont inventées ; elles reprennent seulement la forme des erreurs de lecture constatées sur de vrais chapitres.
 */
private val realLexicon: EnglishLexicon by lazy {
    val dir = listOf("app/src/main/assets/lexicon", "../app/src/main/assets/lexicon", "../../app/src/main/assets/lexicon").map { File(it) }.firstOrNull { File(it, "enable1.txt").isFile }
        ?: throw AssertionError("lexique introuvable (dossier app/src/main/assets/lexicon)")
    EnglishLexicon(File(dir, "enable1.txt").readLines(), File(dir, "g10k.txt").readLines(), File(dir, "genre.txt").readLines())
}

fun ocrQualityTests() {
    fun fix(s: String, names: Set<String> = emptySet()): String = OcrRepair.dropJunkTail(OcrRepair.repair(s, realLexicon, names), realLexicon)
    fun same(vararg texts: String) { for (t in texts) T.eq(t, fix(t), "à laisser tel quel : $t") }
    fun cases(vararg pairs: Pair<String, String>) { for ((src, want) in pairs) T.eq(want, fix(src), "« $src »") }

    T.test("lecture : apostrophes perdues (DONT, YOURE, IM, ILL, LETS, I M, CAN T, PEOPLE S) remises, vrais mots intacts") {
        cases(
            "I DONT KNOW WHY YOURE LATE" to "I DON'T KNOW WHY YOU'RE LATE",
            "THATS WHY I CANT GO" to "THAT'S WHY I CAN'T GO",
            "LETS GO HOME" to "LET'S GO HOME",
            "IM NOT AFRAID" to "I'M NOT AFRAID",
            "ILL TELL HIM" to "I'LL TELL HIM",
            "YOU WILL PAY. ILL KILL YOU" to "YOU WILL PAY. I'LL KILL YOU",
            "I M NOT SORRY" to "I'M NOT SORRY",
            "YOU CAN T DO THIS" to "YOU CAN'T DO THIS",
            "PEOPLE S LIVES ARE AT STAKE" to "PEOPLE'S LIVES ARE AT STAKE",
            "RM NOT AFRAID OF YOU" to "I'M NOT AFRAID OF YOU",
            "T'VE SEEN IT BEFORE" to "I'VE SEEN IT BEFORE",
            "CANIT BE HELPED" to "CAN'T BE HELPED",
        )
        // « ILL » (malade), « LETS » et « IM » restent des mots quand rien n'annonce une contraction
        same("THE ILL KING WAS DEAD", "HE IS ILL", "I FEEL ILL TODAY", "LETS", "YOU MUSTN'T GO", "THE DEMON'S SWORD")
    }
    T.test("lecture : « I » lu « | », « 1 » ou « ! » (en tête, après une virgule ou une fin de phrase) ; les vrais nombres restent") {
        cases(
            "| WILL NOT LOSE" to "I WILL NOT LOSE",
            "1 WILL NOT LOSE" to "I WILL NOT LOSE",
            "l WILL NOT LOSE" to "I WILL NOT LOSE",
            "1 AM HERE" to "I AM HERE",
            "I KNOW, 1 SAW IT" to "I KNOW, I SAW IT",
            "YOU DID IT. 1 WILL GO" to "YOU DID IT. I WILL GO",
            "BECAUSE! COULDN'T STAND IT" to "BECAUSE I COULDN'T STAND IT",
        )
        same("CHAPTER 1 WILL BEGIN", "100 PEOPLE CAME", "LEVEL 1 WILL BE EASY")
    }
    T.test("lecture : points de suspension lus comme un nombre, un point ou une lettre") {
        cases(
            "910 HE WAS GONE" to "... HE WAS GONE",
            "2L HE WAS GONE" to "... HE WAS GONE",
            "RELICS. I.." to "RELICS...",
            ".ARE THEY HERE" to "... ARE THEY HERE",
            "POWER. R. ALLOWING IT" to "POWER. ALLOWING IT",
        )
        same("I KNOW. I...", "J. K. ROWLING")
    }
    T.test("lecture : ordinaux, « 3D », « 50K », « 5X » ne sont jamais pris pour des fautes (« 2ND » ne devient pas « ND »)") {
        same("2ND PLACE", "THE 4TH FLOOR IS CLOSED", "THE 1ST YOUNG MASTER", "THE 3RD PRINCE SAID", "3D MODELS ARE COOL", "A 50K REWARD AND 5X DAMAGE")
        T.ok(OcrRepair.isLegitMixed("10TH") && OcrRepair.isLegitMixed("3d") && OcrRepair.isLegitMixed("1080P") && !OcrRepair.isLegitMixed("2L") && !OcrRepair.isLegitMixed("5O"), "reconnaissance")
    }
    T.test("lecture : cris et rires étirés jamais coupés ni corrigés") {
        same("AAAAARGH", "HEHEHE", "KUKUKU", "PWAHAHA", "HEHEHE... YOU FOOL")
    }
    T.test("lecture : mots collés et fautes de lettres corrigés, noms et mots du genre intacts") {
        cases(
            "AUSELESS WEAPON" to "A USELESS WEAPON",
            "IFILET HIM GO" to "IF I LET HIM GO",
            "ASWORD WAS FOUND" to "A SWORD WAS FOUND",
            "WECAN'TJUST LEAVE" to "WE CAN'T JUST LEAVE",
            "DON'TWORRY ABOUT IT" to "DON'T WORRY ABOUT IT",
            "CASTIE WALLS" to "CASTLE WALLS",
            "DALIGHTER OF THE KING" to "DAUGHTER OF THE KING",
            "CHIEFZ'S ORDERS" to "CHIEF'S ORDERS",
            "DEMONSPAWN" to "DEMON SPAWN",
        )
        // un nom propre avec « 'S » garde son nom ; « TRENERGY » n'est pas changé en « ENERGY » ; les mots du genre sont des mots
        same("CHAERIN'S SWORD", "TRENERGY", "THE DEMON LORD", "THE SECT DISCIPLES", "A CONCUBINE OF THE BARONY")
        T.eq("MASTER TAESAN", fix("MASTER TAESAN", setOf("taesan")))
    }
    T.test("lexique : le vocabulaire du genre est courant (jamais « corrigé »), les interjections aussi") {
        for (w in listOf("sect", "disciple", "demon", "cultivator", "murim", "hyung", "argh", "hehe", "ahem", "hmph")) T.ok(realLexicon.known(w) && realLexicon.isCommon(w), "courant : $w")
        T.ok(!realLexicon.isCommon("zzqx") && !realLexicon.isCommon("castie"), "inconnus")
    }
    T.test("filtre : filigranes (arobase, .co.kr…) ; le vrai dialogue n'en est pas un") {
        listOf("@asura_scans", "READ AT WEBSITE.CO.KR", "www.example.com", "JOIN US ON DISCORD", "MANGA.KR").forEach { T.ok(JunkFilter.isWatermark(it), "filigrane : $it") }
        listOf("I WANT TO GO HOME", "THE DEMON'S ROAR", "MEET ME AT NOON").forEach { T.ok(!JunkFilter.isWatermark(it), "dialogue : $it") }
    }
    T.test("filtre : un reste de décor de deux à quatre lettres est écarté, jamais un petit mot, un cri, un nom du chapitre ni un autre alphabet") {
        listOf("IE", "TE", "SA", "OE", "FIE").forEach { T.ok(JunkFilter.isTinyDebris(it, realLexicon), "débris : $it") }
        listOf("AH", "NO", "OK", "HEH", "HUH", "YES", "ZZZ", "HMM", "WHY?", "...OH", "TY").forEach { T.ok(!JunkFilter.isTinyDebris(it, realLexicon), "à garder : $it") }
        T.ok(!JunkFilter.isTinyDebris("GAF", realLexicon, setOf("gaf")), "nom du chapitre")
        T.ok(!JunkFilter.isTinyDebris("제발", realLexicon) && !JunkFilter.isTinyDebris("DEMONS", realLexicon), "autre alphabet, mot plus long")
    }
    T.test("fusion des lectures : un jeton parasite (« 5X9Q », « | », lettre seule) fait perdre la lecture qui le contient, pas un vrai nombre") {
        T.eq(0, OcrMerge.junkTokens("WE MUST GO AT 5 TODAY, THE 2ND TIME, 36 TIMES, I AM A MAN"))
        T.eq(1, OcrMerge.junkTokens("WE MUST 5X9Q GO NOW"))
        T.eq(2, OcrMerge.junkTokens("WE | MUST R. GO"))
        val clean = OcrMerge.plausibility("WE MUST GO NOW", realLexicon)
        T.ok(OcrMerge.plausibility("WE MUST 5X9Q GO NOW", realLexicon) < clean, "lecture parasitée moins plausible")
        T.ok(OcrMerge.plausibility("WE MUST GO AT THE 2ND TIME", realLexicon) >= 0.99f, "un ordinal n'est pas un parasite")
        val line = { t: String, c: Float -> OcrLine(t, RectF(100f, 100f, 400f, 140f), c) }
        T.eq("WE MUST GO NOW", OcrMerge.merge(listOf(line("WE MUST 5X9Q GO NOW", 0.97f)), listOf(line("WE MUST GO NOW", 0.90f)), "en", realLexicon).single().text)
    }

    // ----- croisement de deux lectures d'une même ligne (lecteurs v3 / v5, ou ML Kit / Paddle) -----
    fun fuse(base: String, alt: String, names: Set<String> = emptySet()): String = OcrFusion.fuse(base, alt, realLexicon, names).text
    T.test("croisement : un mot inconnu d'une lecture est repris à l'autre quand c'est un vrai mot (lettre fausse, mot collé, chiffre pour lettre)") {
        T.eq("NO CHOICE BUT TO", fuse("NO CHUICE BUT TO", "NO CHOICE 5UT TO"))           // chaque lecteur a raison sur un mot
        T.eq("THIS IS ALL FOR", fuse("THISISALLFOR", "THIS IS ALL FOR"))                   // mots collés
        T.eq("HE SHOULD BE ON", fuse("HE SHOULD E ON", "HE SHOUD BE ON"))                  // un mot perdu, un autre mal écrit
        T.eq("TOTAL ATTRIBUTES ARE SO", fuse("TOTAL ATTRIBLITES ARE SO", "TOTAL ATTRIBUTES ARE SO"))
        T.eq("IMPLEMENTED FLAWLESSLY", fuse("IMPLEMENTEDFLAWLESSLY", "IMPLEMENTED FLAWLESSLY"))
        T.eq("20 POINTS, HOW COME MY", fuse("2O POINTS, HOW COME MY", "20 POINTS, HOW COME MY"))
        T.eq("I M THE DEMON WHO CAME BECAUSE I", fuse("I M THE DEMON WHO CAMEBECAUSE I", "I M THE DEMON WHO CAME BECAUSE I"))
        T.eq("STAGE, THIS IS NOT", fuse("STAGE,THIS IS NOT", "STAGE, THIS IS NOT"))     // ponctuation collée
        T.eq("TALENT", fuse("ALENT.", "TALENT"))
    }
    T.test("croisement : sans preuve par le dictionnaire la lecture de base reste telle quelle") {
        for ((b, a) in listOf("SHUT UP." to "SHUT UP,", "Him." to "HIM", "KILL HIM?" to "KILL HIM", "WELL..." to "WE...", "BOULDER" to "BOLDER", "NO IDEA." to "NO IDEA")) T.eq(b, fuse(b, a), "$b / $a")
        // deux lectures fausses : l'égalité garde la base
        T.eq("YOUR CRITCAHI AMAGEIS", fuse("YOUR CRITCAHI AMAGEIS", "YOUR CRITICAHAE I"))
        // un nom du chapitre inconnu du dictionnaire n'est jamais remplacé, ni son possessif
        T.eq("KAZAN'S RING", fuse("KAZAN'S RING", "KAZAM'S RING", setOf("kazan")))
        T.eq(0.0, OcrFusion.problems("KAZAN'S RING", realLexicon, setOf("kazan")))
        T.ok(OcrFusion.problems("KAZAN'S RING", realLexicon) >= 1.0, "sans la liste des noms, « KAZAN'S » est inconnu")
    }
    T.test("croisement : jamais un texte beaucoup plus court ou plus long que le morceau remplacé") {
        T.eq("OF [ARRIVE SAFELY AT SHENGYAO", fuse("OF [ARRIVE SAFELY AT SHENGYAO", "OF [ARRIVE FY"))
        T.eq("A RESTRICTION ON YOUR", fuse("A.RESNRICTIONON'YOUR", "A RESTRICTION ON YOUR"))
        T.eq("WE MUST GO", fuse("WE MUST GO", ""))
        T.eq("", fuse("", "WE MUST GO"))
    }
    T.test("croisement : ML Kit et Paddle sur un même texte : le moteur retenu est corrigé par l'autre, pas l'inverse quand tout va bien") {
        val ml = { t: String, c: Float -> OcrLine(t, RectF(100f, 100f, 500f, 140f), c) }
        val pp = { t: String, c: Float -> OcrLine(t, RectF(102f, 101f, 498f, 141f), c) }
        T.eq("THE DEMON WHO CAME BECAUSE I", OcrMerge.merge(listOf(ml("THE DEMON WHO CAME BECAUSE I", 0.80f)), listOf(pp("THE DEMON WHO CAMEBECAUSE I", 0.95f)), "en", realLexicon).single().text)
        T.eq("TOTAL ATTRIBUTES ARE SO", OcrMerge.merge(listOf(ml("TOTAL ATTRIBUTES ARE SO", 0.70f)), listOf(pp("TOTAL ATTRIBLITES ARE SO", 0.95f)), "en", realLexicon).single().text)
        T.eq("WE MUST GO NOW", OcrMerge.merge(listOf(ml("WE MUST GO NOW", 0.99f)), listOf(pp("WE MUST GO NOW", 0.99f)), "en", realLexicon).single().text)
        // alphabet non latin : pas de croisement
        T.eq("제발 도와줘", OcrMerge.merge(listOf(ml("제발 도와줘", 0.9f)), listOf(pp("제발 도와줘요", 0.5f)), "ko", realLexicon).single().text)
    }
    T.test("relecture : seules les lignes à mot inconnu valent une seconde lecture ; largeur d'entrée élargie") {
        T.ok(OcrFusion.worthRetry("WEL...", realLexicon), "mot inconnu")
        T.ok(OcrFusion.worthRetry("THISISALLFOR", realLexicon), "mots collés")
        T.ok(!OcrFusion.worthRetry("WE MUST GO NOW", realLexicon), "texte sans défaut")
        T.ok(!OcrFusion.worthRetry("XA", realLexicon), "icône : trop peu de lettres")
        T.ok(!OcrFusion.worthRetry("manganato.gg/m...", realLexicon), "adresse")
        T.ok(!OcrFusion.worthRetry("KAZAN'S RING", realLexicon, setOf("kazan")), "nom du chapitre")
        T.eq(240, Ctc.inputSize(200, 40, 48).second)
        T.eq(312, Ctc.inputSize(200, 40, 48, stretch = 1.3f).second)
        T.eq(1600, Ctc.inputSize(2000, 40, 48, stretch = 1.3f).second)
    }
    T.test("lettres étroites : étirement de relecture adapté à la densité de la ligne") {
        // « Well… » (boîte 166 × 95 px, « Wel.. » lu : 5 caractères) : lettres deux fois trop étroites pour le lecteur → élargie de ~43 %
        val well = Ctc.narrowStretch(166, 95, 5)
        T.ok(well in 1.40f..1.46f, "Well : $well")
        // ligne ordinaire (0,5 de large par caractère) : pas de relecture imposée
        T.eq(1f, Ctc.narrowStretch(240, 48, 10))
        T.eq(1f, Ctc.narrowStretch(300, 40, 20))              // 0,375 : au-dessus du seuil
        // trop peu de caractères pour juger (icône, « A »), boîte vide
        T.eq(1f, Ctc.narrowStretch(100, 100, 2))
        T.eq(1f, Ctc.narrowStretch(0, 0, 5))
        // police très condensée : plafonnée à 2,6 ; ligne à peine sous le seuil : au moins 1,3
        T.eq(2.6f, Ctc.narrowStretch(500, 100, 40))
        T.ok(Ctc.narrowStretch(300, 40, 21) in 1.3f..1.45f, "juste sous le seuil")
        // la largeur d'entrée du lecteur suit : 95 px de haut, 166 de large → 84 px à 48 de haut ; élargie → 120
        T.eq(84, Ctc.inputSize(166, 95, 48).second)
        T.ok(Ctc.inputSize(166, 95, 48, stretch = well).second in 118..122, "largeur élargie")
    }
}
