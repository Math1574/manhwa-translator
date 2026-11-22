package app.manhwa.core

/** L'original que le modèle recopie devant sa traduction (« SOURCE| TRADUCTION ») est retiré ; une traduction qui n'en contient pas n'est jamais modifiée. */
fun echoTests() {
    T.test("recopie : « SOURCE| TRADUCTION » (les cas vus dans les bulles) rend la traduction seule") {
        T.eq("TU ES UN PEU TROP SÉVÈRE.", TranslationEcho.strip("You're being a little excessive.", "YOU'RE BEING A LITTLE EXCESSIVE.| TU ES UN PEU TROP SÉVÈRE."), "source envoyée en casse de phrase, texte gardé en majuscules")
        T.eq("... OH, TU VIENS JUSTE DE RENTRER ?", TranslationEcho.strip("... OH, YOU JUST GOT BACK?", "... OH, YOU JUST GOT BACK?| ... OH, TU VIENS JUSTE DE RENTRER ?"), "les points de suspension de la traduction restent")
        T.eq("MÈRE ?", TranslationEcho.strip("MJOTHEK !", "MJOTHEK !| MÈRE ?"), "mot mal lu recopié")
        T.eq("MÈRE ?", TranslationEcho.strip("MJOTHEK !", "MJOTHEK !\nMÈRE ?"), "traduction sur la ligne suivante")
        T.eq("MÈRE ?", TranslationEcho.strip("MJOTHEK !", "MJOTHEK ! MÈRE ?"), "sans séparateur (lignes jointes par un espace)")
    }

    T.test("recopie : autres séparateurs, source coupée sur deux lignes, double recopie, lecture corrigée au passage") {
        T.eq("Il n'y a rien à faire.", TranslationEcho.strip("There is nothing to be done.", "There is nothing to be done. => Il n'y a rien à faire."))
        T.eq("Il n'y a rien à faire.", TranslationEcho.strip("There is nothing to be done.", "There is nothing to be done. -> Il n'y a rien à faire."))
        T.eq("Il n'y a rien à faire.", TranslationEcho.strip("There is nothing to be done.", "There is nothing\nto be done.| Il n'y a rien à faire."), "la recopie est coupée en deux lignes")
        T.eq("SALUT L'AMI", TranslationEcho.strip("HELLO THERE FRIEND", "HELLO THERE FRIEND| HELLO THERE FRIEND| SALUT L'AMI"), "recopiée deux fois")
        T.eq("Je n'aurais jamais cru que tu reviendrais.", TranslationEcho.strip("I NEVER THOUGHT YOU WOULD COME BACK HERE", "I NEVER THOUGH YOU WOULD COME BACK HERE| Je n'aurais jamais cru que tu reviendrais."), "une lettre de différence sur un long texte")
        T.eq("Mère ?", TranslationEcho.strip("MJ0THEK !", "MJOTHEK !| Mère ?", alt = "MJOTHEK !"), "la forme relue de la source (alt) est reconnue aussi")
    }

    T.test("recopie : une traduction sans original recopié n'est jamais modifiée") {
        for ((src, tr) in listOf(
            "NO!" to "NON !", "Thank you." to "Merci.", "HE LEFT." to "Il est parti.", "SOPHIA!" to "SOPHIA !", "MOTHER!" to "MÈRE !",
            "HE'S HERE" to "Il est là | Il est ici", "WAIT" to "ATTENDS | JE T'ATTENDS", "STOP IT" to "Arrête.", "OK" to "OK", "WHAT?!" to "QUOI ?!",
            "HELLO WORLD" to "HELLO WORLD| !!", "I SAID, STOP!" to "J'ai dit, stop !", "" to "Salut",
        )) T.eq(tr, TranslationEcho.strip(src, tr), "« $src » → « $tr »")
        T.eq("", TranslationEcho.strip("X", ""), "traduction vide")
    }

    T.test("recopie : un « | » seul au début ou à la fin est retiré") {
        T.eq("ATTENDS", TranslationEcho.strip("WAIT", "| ATTENDS"))
        T.eq("ATTENDS", TranslationEcho.strip("WAIT", "ATTENDS |"))
        T.eq("|", TranslationEcho.strip("WAIT", "|"), "rien d'autre que le trait : laissé")
    }

    T.test("recopie : la réponse du modèle est nettoyée ligne par ligne quand les lignes envoyées sont données") {
        val sent = listOf("You're being a little excessive.", "... Oh, you just got back?", "Mjothek !")
        val reply = "1| YOU'RE BEING A LITTLE EXCESSIVE.| TU ES UN PEU TROP SÉVÈRE.\n2| ... OH, YOU JUST GOT BACK?| ... OH, TU VIENS JUSTE DE RENTRER ?\n3| MJOTHEK !\nMÈRE ?"
        T.eq(listOf("TU ES UN PEU TROP SÉVÈRE.", "... OH, TU VIENS JUSTE DE RENTRER ?", "MÈRE ?"), LlmPrompt.parse(reply, 3, sent))
        T.eq(listOf("YOU'RE BEING A LITTLE EXCESSIVE.| TU ES UN PEU TROP SÉVÈRE.", "... OH, YOU JUST GOT BACK?| ... OH, TU VIENS JUSTE DE RENTRER ?", "MJOTHEK ! MÈRE ?"), LlmPrompt.parse(reply, 3), "sans les lignes envoyées, la réponse est lue telle quelle (comportement d'avant)")
        T.eq(listOf("Salut !", "Comment vas-tu ?", null), LlmPrompt.parse("1| Salut !\n2| Comment vas-tu ?", 3, listOf("Hi!", "How are you?", "Fine.")), "une réponse normale ne change pas, une ligne absente reste absente")
        T.eq(listOf("Salut !", "Bien."), LlmPrompt.parse("1| Salut !\n2| Bien.", 2, listOf("Hi!")), "moins de lignes envoyées que de réponses : le reste est laissé tel quel")
    }

    T.test("contrôles : l'original recopié et le « | » resté dans le texte sont signalés ; ni l'un ni l'autre sur une traduction normale") {
        val a = TranslationChecks.problems("MJOTHEK !", "MJOTHEK !| MÈRE ?", "fr").map { it.code }
        T.ok("recopie" in a, "recopie signalée : $a")
        val b = TranslationChecks.problems("Hello there my friend", "Salut | mon ami", "fr").map { it.code }
        T.ok("format" in b && "recopie" !in b, "« | » signalé comme résidu de format : $b")
        val c = TranslationChecks.problems("Hello there my friend", "Salut mon ami", "fr").map { it.code }
        T.ok("recopie" !in c && "format" !in c, "traduction normale : $c")
        val d = TranslationChecks.problems("I | AM HERE", "Je | suis ici", "fr").map { it.code }
        T.ok("format" !in d, "un « | » de l'original gardé dans la traduction n'est pas un résidu : $d")
    }

    T.test("recopie : une page gardée avant le filtre est nettoyée (même instance quand rien ne change)") {
        fun block(id: Int, src: String, tr: String, repaired: String = "") = TranslatedBlock(id, RectF(0f, 0f, 10f, 10f), src, tr, repaired = repaired)
        val clean = PageResult(100, 100, listOf(block(1, "HELLO", "SALUT"), block(2, "BYE", "AU REVOIR")))
        T.ok(TranslationEcho.cleanPage(clean) === clean, "page propre : la même instance")
        val dirty = PageResult(100, 100, listOf(
            block(1, "YOU'RE BEING A LITTLE EXCESSIVE.", "YOU'RE BEING A LITTLE EXCESSIVE.| TU ES UN PEU TROP SÉVÈRE."),
            block(2, "MJ0THEK !", "MJOTHEK !| MÈRE ?", repaired = "MJOTHEK !"),
            block(3, "BYE", "AU REVOIR"),
        ))
        val fixed = TranslationEcho.cleanPage(dirty)
        T.eq(listOf("TU ES UN PEU TROP SÉVÈRE.", "MÈRE ?", "AU REVOIR"), fixed.blocks.map { it.translated })
        T.eq(listOf("YOU'RE BEING A LITTLE EXCESSIVE.", "MJ0THEK !", "BYE"), fixed.blocks.map { it.source }, "les textes d'origine ne changent pas")
    }

    T.test("recopie : une traduction fraîche est nettoyée dans le pipeline, et une page recopiée du cache l'est au rangement suivant") {
        val img = byteArrayOf(9, 8, 7)
        fun ocrLine(t: String, l: Float, top: Float, r: Float, b: Float) = OcrLine(t, RectF(l, top, r, b), 0.95f, false)
        fun tmp(): java.io.File = java.nio.file.Files.createTempDirectory("mt").toFile()
        val lines = listOf(ocrLine("Hello", 100f, 100f, 300f, 130f), ocrLine("Bye", 100f, 600f, 300f, 630f))
        val echoing = TranslationEngine { t, _, _ -> t.map { "$it| " + it.uppercase() } }
        val o = PagePipeline(ResultCache(tmp()), FakeOcr(lines), echoing, "e").process(img, "en", "fr") as PageOutcome.Done
        T.eq(listOf("HELLO", "BYE"), o.result.blocks.map { it.translated }, "la recopie ne passe pas dans le résultat")

        // page déjà gardée avec la recopie (cache d'avant le filtre)
        val cache = ResultCache(tmp()); val key = CacheKey(sha256Hex(img), "en", "fr", "e")
        cache.put(key, PageResult(900, 900, listOf(TranslatedBlock(0, RectF(100f, 100f, 300f, 130f), "Hello", "Hello| SALUT"))), 0L)
        val p = PagePipeline(cache, FakeOcr(lines), echoing, "e")
        val c = p.process(img, "en", "fr") as PageOutcome.Done
        T.ok(c.fromCache, "reprise du cache")
        T.eq(listOf("SALUT"), c.result.blocks.map { it.translated }, "affichée sans l'original")
        T.eq("SALUT", cache.get(key)!!.blocks.single().translated, "et rangée nettoyée")
    }
}
