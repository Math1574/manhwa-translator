package app.manhwa.core

private const val CH1 = "https://site.test/comics/test-series-1a2b3c/chapter/1.html"
private const val CH2 = "https://site.test/comics/test-series-1a2b3c/chapter/2.html"

private fun adj(id: String, src: String, top: Float = 100f, translation: String? = null, hidden: Boolean = false, dx: Float = 0f, scale: Float = 1f, font: String? = null, source: String? = null, precise: Boolean = false) =
    BlockEdit(id, EditKind.ADJUST, RectF(10f, top, 200f, top + 40f), src, translation = translation, hidden = hidden, dx = dx, scale = scale, fontId = font, source = source, precise = precise)

private fun manual(id: String, src: String?, translation: String?, hidden: Boolean = false) =
    BlockEdit(id, EditKind.MANUAL, null, "", UserRegion(RectF(0f, 300f, 100f, 340f)), src, translation, hidden)

/** Centre de corrections : description des corrections, rangement par chapitre, traductions retenues, origines enregistrées. */
fun correctionsTests() {
    T.test("corrections: une traduction supprimée, une traduction écrite et un texte corrigé sont décrits") {
        val h = Corrections.describe(adj("a", "HELLO THERE", hidden = true))!!
        T.eq("Traduction supprimée", h.first); T.eq("« HELLO THERE »", h.second)
        val t = Corrections.describe(adj("b", "HELLO THERE", translation = "Salut toi"))!!
        T.eq("Traduction écrite", t.first); T.eq("« HELLO THERE »\n→ Salut toi", t.second)
        val s = Corrections.describe(adj("c", "HELL0 THERE", source = "HELLO THERE"))!!
        T.eq("Texte d'origine corrigé", s.first); T.ok(s.second.contains("Lu comme : « HELLO THERE »"), "texte lu en plus : ${s.second}")
    }

    T.test("corrections: plusieurs changements sur un même texte se cumulent dans l'ordre") {
        val d = Corrections.describe(adj("a", "TEXT", translation = "Texte", dx = 12f, scale = 1.2f, font = "chewy"))!!
        T.eq("Traduction écrite · position changée · taille changée · police changée", d.first)
        T.eq("Taille changée", Corrections.describe(adj("b", "TEXT", scale = 0.8f))!!.first)
        T.eq("Effacement précis", Corrections.describe(adj("c", "TEXT", precise = true))!!.first)
    }

    T.test("corrections: un texte ajouté à la main montre son texte et sa traduction ; une zone sans traduction est une zone ajoutée") {
        val m = Corrections.describe(manual("m", "Boom!", "Boum !"))!!
        T.eq("Texte ajouté à la main", m.first); T.eq("« Boom! »\n→ Boum !", m.second)
        T.eq("Zone ajoutée", Corrections.describe(manual("n", "Boom!", null))!!.first)
        T.eq("Traduction supprimée", Corrections.describe(manual("o", "Boom!", "Boum !", hidden = true))!!.first)
    }

    T.test("corrections: une correction qui ne change rien n'est pas décrite") {
        T.ok(Corrections.describe(adj("a", "TEXT")) == null, "correction neutre")
    }

    T.test("corrections: un long texte est raccourci sur une ligne") {
        val long = "word ".repeat(80)
        val d = Corrections.describe(adj("a", long, hidden = true))!!
        T.ok(d.second.length < 130 && !d.second.contains('\n') && d.second.contains("…"), "raccourci : ${d.second.length}")
    }

    T.test("corrections: les images d'une même page se regroupent, la plus récente d'abord, l'origine inconnue à la fin") {
        val imgs = listOf(
            EditedImage("k1", 100L, listOf(adj("e1", "ONE", hidden = true))),
            EditedImage("k2", 300L, listOf(adj("e2", "TWO", translation = "Deux"))),
            EditedImage("k3", 200L, listOf(adj("e3", "THREE", hidden = true))),
            EditedImage("k4", 400L, listOf(adj("e4", "FOUR", hidden = true))),
            EditedImage("k5", 500L, listOf(adj("e5", "FIVE"))),         // ne change rien : jamais listée
        )
        val origins = mapOf(
            "k1" to EditOrigin("$CH1#haut", "Chap 1", 100L), "k2" to EditOrigin(CH1, "Chap 1", 300L), "k3" to EditOrigin(CH2, "Chap 2", 200L),
            "k5" to EditOrigin(CH2, "Chap 2", 500L),
        )
        val g = Corrections.byChapter(imgs, origins)
        T.eq(3, g.size, "chapitre 1, chapitre 2, inconnu")
        T.eq(CH1, g[0].url, "le chapitre dont la dernière correction est la plus récente"); T.eq(listOf("e2", "e1"), g[0].items.map { it.editId }, "images récentes d'abord")
        T.eq(CH2, g[1].url); T.eq(listOf("e3"), g[1].items.map { it.editId })
        T.eq(null, g[2].url, "origine inconnue en dernier"); T.eq(listOf("e4"), g[2].items.map { it.editId })
        T.eq("Test Series — Chapitre 1", g[0].title); T.eq("Chapitre inconnu", g[2].title)
        T.eq(300L, g[0].lastAt)
    }

    T.test("corrections: dans une image, l'ordre est celui de la page ; sans numéro de chapitre, le titre de la page sert") {
        val imgs = listOf(EditedImage("k", 10L, listOf(adj("bas", "B", top = 900f, hidden = true), adj("haut", "A", top = 50f, hidden = true), manual("main", "C", "c"))))
        val g = Corrections.byChapter(imgs, mapOf("k" to EditOrigin("https://x.test/lire/abc", "Prologue — Le début", 10L)))
        T.eq(listOf("haut", "main", "bas"), g.single().items.map { it.editId }, "rangées de haut en bas (le texte ajouté est à 300)")
        T.eq("Prologue — Le début", g.single().title, "titre de la page, pas d'œuvre devinée")
    }

    T.test("corrections: défaire une traduction écrite oublie la traduction retenue du même texte, sauf si une autre correction la porte") {
        val g = Glossary(); g.addCorrection("HELLO  THERE", "Salut toi")
        val a = adj("a", "HELLO THERE", translation = "Salut toi")
        T.eq("HELLO THERE", Corrections.retainedTextToDrop(a, emptyList(), g), "la traduction retenue est celle de la correction")
        T.eq(null, Corrections.retainedTextToDrop(a, listOf(adj("b", "HELLO\nTHERE", translation = "Salut toi")), g), "une autre correction identique la porte encore")
        T.eq("HELLO THERE", Corrections.retainedTextToDrop(a, listOf(adj("b", "HELLO THERE", translation = "Autre")), g), "une autre traduction ne la porte pas")
        g.addCorrection("HELLO THERE", "Coucou")
        T.eq(null, Corrections.retainedTextToDrop(a, emptyList(), g), "la traduction retenue a changé depuis : on n'y touche pas")
        T.eq(null, Corrections.retainedTextToDrop(adj("c", "HELLO THERE", hidden = true), emptyList(), g), "une suppression ne retient rien")
        T.eq(null, Corrections.retainedTextToDrop(manual("m", "HELLO THERE", "Coucou"), emptyList(), g), "un texte ajouté à la main ne retient rien")
    }

    T.test("corrections: les traductions retenues sans correction sont listées à part") {
        val g = Glossary(); g.addCorrection("HELLO THERE", "Salut toi"); g.addCorrection("GOOD BYE", "Au revoir")
        val orphans = Corrections.orphanRetained(listOf(adj("a", "HELLO  THERE", translation = "Salut toi"), adj("b", "GOOD BYE", hidden = true)), g)
        T.eq(listOf(TextCorrection("GOOD BYE", "Au revoir")), orphans, "seule « GOOD BYE » n'est portée par aucune correction")
    }

    T.test("origines: l'enregistrement se relit tel quel, une ligne abîmée est sautée") {
        val m = linkedMapOf("k1" to EditOrigin(CH1, "Chap\t1\nsuite", 5L), "k2" to EditOrigin(CH2, "", 9L))
        val txt = OriginsCodec.serialize(m)
        T.eq(m, OriginsCodec.parse(txt))
        T.eq(m, OriginsCodec.parse("n'importe quoi\nO\tk\tpas-un-nombre\tu\tt\n" + txt + "O\tcourte\n"), "lignes abîmées sautées")
        T.eq(0, OriginsCodec.parse("").size)
    }

    T.test("glossaire: une traduction retenue se liste avec son texte lu, s'oublie et survit à l'enregistrement") {
        val g = Glossary(); g.addCorrection("  HEY   YOU\nWAIT ", "Hé, toi, attends !")
        T.eq(listOf(TextCorrection("HEY YOU WAIT", "Hé, toi, attends !")), g.corrections())
        val back = Glossary.parse(g.serialize())
        T.eq(g.corrections(), back.corrections(), "texte lu conservé à l'enregistrement")
        T.eq(g.corrections(), g.copy().corrections(), "copie")
        T.eq(g.corrections(), g.forSeries("une-oeuvre").corrections(), "glossaire d'une œuvre")
        T.eq("Hé, toi, attends !", back.correctionFor("HEY YOU WAIT"), "elle s'applique toujours")
        T.ok(back.removeCorrection("HEY  YOU WAIT"), "oubliée")
        T.eq(emptyList<TextCorrection>(), back.corrections()); T.eq(null, back.correctionFor("HEY YOU WAIT"))
        T.ok(!back.removeCorrection("HEY YOU WAIT"), "déjà oubliée")
    }

    T.test("glossaire: un ancien fichier (corrections sans texte lu) se relit ; le texte est alors montré sans espaces") {
        val g = Glossary.parse("T\thero\theros\nC\tHEYYOUWAIT\tHé, toi, attends !\n")
        T.eq(listOf(TextCorrection("HEYYOUWAIT", "Hé, toi, attends !")), g.corrections())
        T.eq("Hé, toi, attends !", g.correctionFor("HEY YOU WAIT"))
        T.eq(listOf(GlossaryEntry("hero", "heros")), g.terms())
    }
}
