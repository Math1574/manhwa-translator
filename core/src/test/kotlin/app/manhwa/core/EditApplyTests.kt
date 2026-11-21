package app.manhwa.core

/**
 * Corrections enregistrées à la main : elles retrouvent leur texte même quand la relecture n'a pas rendu exactement les mêmes lettres ni la même boîte, et une correction qui ne
 * trouve aucun texte est signalée (elle ne s'affichait pas, sans rien dire).
 */
fun editApplyTests() {
    fun tb(id: Int, l: Float, t: Float, r: Float, b: Float, src: String, tr: String) = TranslatedBlock(id, RectF(l, t, r, b), src, tr, false, (b - t) / 2f, listOf(RectF(l, t, r, b)), listOf(src))
    fun adjust(id: String, box: RectF, src: String, tr: String?) = BlockEdit(id, EditKind.ADJUST, box, src, translation = tr)

    T.test("correction : le texte relu à une lettre près, au même endroit, retrouve sa correction") {
        val page = PageResult(800, 1500, listOf(tb(0, 100f, 400f, 340f, 460f, "I WILL NEVER FORGIVE YOU", "Je ne te pardonnerai jamais")))
        // la correction avait été prise sur un texte lu « I WILL NEVER FORGIVE YOU! » avec une boîte un peu plus haute : le recouvrement est faible
        val e = adjust("e1", RectF(120f, 380f, 330f, 520f), "I WILL NEVER FORGlVE YOU!", "Jamais je ne te pardonnerai")
        val a = EditApplier.apply(page, listOf(e), 0f)
        T.eq("Jamais je ne te pardonnerai", a.page.blocks.single().translated, "traduction corrigée")
        T.eq(emptyList<BlockEdit>(), a.lost(listOf(e)), "rien de perdu")
    }
    T.test("correction : un autre texte au même endroit n'est pas confondu avec elle") {
        val page = PageResult(800, 1500, listOf(tb(0, 100f, 400f, 340f, 460f, "WHERE ARE YOU GOING", "Où vas-tu")))
        val e = adjust("e1", RectF(100f, 400f, 340f, 460f), "I WILL NEVER FORGIVE YOU", "Jamais")
        // même boîte : c'est la boîte qui désigne le bloc (recouvrement), le texte n'a pas à être identique
        T.eq("Jamais", EditApplier.apply(page, listOf(e), 0f).page.blocks.single().translated)
        // boîte ailleurs et texte différent : aucune cible
        val far = adjust("e2", RectF(500f, 900f, 700f, 960f), "I WILL NEVER FORGIVE YOU", "Jamais")
        val a = EditApplier.apply(page, listOf(far), 0f)
        T.eq("Où vas-tu", a.page.blocks.single().translated, "texte inchangé")
        T.eq(listOf("e2"), a.lost(listOf(far)).map { it.id }, "correction signalée comme perdue")
    }
    T.test("correction : le même texte lu au même endroit mais dans une boîte très différente est retrouvé") {
        val page = PageResult(800, 1500, listOf(tb(0, 100f, 400f, 340f, 600f, "WE MUST LEAVE", "Il faut partir")))
        val e = adjust("e1", RectF(150f, 450f, 220f, 480f), "WE MUST LEAVE", "Partons")
        T.eq("Partons", EditApplier.apply(page, listOf(e), 0f).page.blocks.single().translated)
    }
    T.test("correction : un bloc défini à la main n'est jamais signalé comme perdu") {
        val m = BlockEdit("m1", EditKind.MANUAL, null, "", UserRegion(RectF(90f, 90f, 310f, 230f)), "HELLO", "Bonjour", false, 0f, 0f, 1f, null, false, 30f, listOf(RectF(100f, 100f, 300f, 130f)), listOf("HELLO"), "FREE", null)
        val a = EditApplier.apply(PageResult(800, 1500, emptyList()), listOf(m), 0f)
        T.eq(emptyList<BlockEdit>(), a.lost(listOf(m)))
    }
    T.test("correction : décalage de la fenêtre de jonction (coordonnées de l'image décalées du contexte)") {
        // le même texte, traité une fois avec 300 px de contexte et une fois sans : la correction (en coordonnées de l'image) trouve le bloc dans les deux
        val e = adjust("e1", RectF(100f, 100f, 340f, 160f), "HELLO THERE", "Salut")
        val withCtx = PageResult(800, 1800, listOf(tb(0, 100f, 400f, 340f, 460f, "HELLO THERE", "Bonjour")))
        val noCtx = PageResult(800, 1500, listOf(tb(0, 100f, 100f, 340f, 160f, "HELLO THERE", "Bonjour")))
        T.eq("Salut", EditApplier.apply(withCtx, listOf(e), 300f).page.blocks.single().translated, "avec contexte")
        T.eq("Salut", EditApplier.apply(noCtx, listOf(e), 0f).page.blocks.single().translated, "sans contexte")
    }
}
