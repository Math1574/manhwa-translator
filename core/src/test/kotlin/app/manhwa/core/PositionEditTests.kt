package app.manhwa.core

/**
 * Position choisie à la main pour un texte traduit : enregistrée en absolu (centre du texte), jamais déplacée par une mise en page automatique qui change ; une ancienne position
 * relative (enregistrée avant l'ancre) n'est appliquée que si elle laisse le texte sur son emplacement.
 * Les trois cas « texte mal placé » du journal du 17/11 (cadre 373 px trop haut, deux textes de 100 px et 124 px trop hauts) reprennent les mesures relevées sur les captures.
 */
fun positionEditTests() {
    fun tb(id: Int, l: Float, t: Float, r: Float, b: Float, src: String, tr: String) = TranslatedBlock(id, RectF(l, t, r, b), src, tr, false, (b - t) / 2f, listOf(RectF(l, t, r, b)), listOf(src))
    fun legacy(dx: Float, dy: Float, manual: Boolean = false) = RenderEdit(dx, dy, 1f, null, null, manual, "e")
    fun anchored(x: Float, y: Float, dx: Float = 0f, dy: Float = 0f) = RenderEdit(dx, dy, 1f, null, null, false, "e", anchorX = x, anchorY = y)

    T.test("position : en absolu, le centre du texte va exactement à l'ancre, quelle que soit la mise en page automatique") {
        val base = RectF(100f, 200f, 300f, 260f)                       // centre (200, 230)
        val s = EditApplier.positionShift(anchored(250f, 400f), base, RectF(90f, 190f, 310f, 270f))!!
        T.eq(50f, s.first, "dx"); T.eq(170f, s.second, "dy")
        // la mise en page automatique d'une autre version place le texte ailleurs : le texte revient quand même à l'ancre
        val other = RectF(600f, 20f, 700f, 80f)
        val s2 = EditApplier.positionShift(anchored(250f, 400f), other, RectF(90f, 190f, 310f, 270f))!!
        T.eq(250f, other.centerX + s2.first, "x final"); T.eq(400f, other.centerY + s2.second, "y final")
        // l'ancre prime sur un ancien décalage relatif
        val s3 = EditApplier.positionShift(anchored(250f, 400f, dx = 999f, dy = -999f), base, RectF(90f, 190f, 310f, 270f))!!
        T.eq(50f, s3.first, "dx (ancre)"); T.eq(170f, s3.second, "dy (ancre)")
    }
    T.test("position : aucune position choisie → rien à appliquer") {
        T.eq(null, EditApplier.positionShift(legacy(0f, 0f), RectF(0f, 0f, 100f, 50f), RectF(0f, 0f, 100f, 50f)))
        T.eq(null, EditApplier.positionShift(legacy(0f, 0f, manual = true), RectF(0f, 0f, 100f, 50f), RectF(0f, 0f, 100f, 50f)))
    }
    T.test("position : ancien décalage relatif petit ou modéré conservé (le texte reste sur son emplacement)") {
        val base = RectF(200f, 1000f, 420f, 1090f)
        val orig = RectF(190f, 990f, 430f, 1110f)
        T.eq(0f to 3f, EditApplier.positionShift(legacy(0f, 3f), base, orig), "poussé de 3 px")
        T.eq(20f to -30f, EditApplier.positionShift(legacy(20f, -30f), base, orig), "décalage modéré")
        T.eq(-40f to 45f, EditApplier.positionShift(legacy(-40f, 45f), base, orig), "à moitié sorti de l'emplacement, il y recouvre encore le quart")
    }
    T.test("position : ancien décalage relatif devenu faux ignoré — cadre posé 373 px trop haut (défaut relevé)") {
        // texte placé aujourd'hui [274,5353,638,5529] ; l'ancienne correction (1, −373) le remontait à [275,4980,639,5156], loin du texte d'origine [211,5313,698,5568]
        val base = RectF(274f, 5353f, 638f, 5529f)
        val orig = RectF(211f, 5313f, 698f, 5568f)
        T.ok(!EditApplier.staysOnText(base.offset(1f, -373f), orig), "texte décalé hors de son emplacement")
        T.eq(null, EditApplier.positionShift(legacy(1f, -373f), base, orig), "décalage ignoré")
    }
    T.test("position : ancien décalage relatif devenu faux ignoré — deux textes remontés de 99 et 124 px (défauts relevés)") {
        val base1 = RectF(622f, 10930f, 803f, 11009f); val orig1 = RectF(609f, 10915f, 816f, 11024f)
        T.eq(null, EditApplier.positionShift(legacy(-15f, -99f), base1, orig1), "« charge in blindly »")
        val base2 = RectF(329f, 15396f, 523f, 15497f); val orig2 = RectF(331f, 15387f, 522f, 15505f)
        T.eq(null, EditApplier.positionShift(legacy(-14f, -124f), base2, orig2), "« Il sait même… »")
    }
    T.test("position : un bloc défini à la main garde son décalage relatif (sa zone est celle de l'utilisateur)") {
        val s = EditApplier.positionShift(legacy(0f, -300f, manual = true), RectF(0f, 1000f, 100f, 1050f), RectF(0f, 1000f, 100f, 1050f))
        T.eq(0f to -300f, s)
    }
    T.test("position : centre absolu d'un texte décalé") {
        val base = RectF(100f, 200f, 300f, 260f)
        T.eq(null, EditApplier.anchorOf(base, 0f, 0f), "pas déplacé"); T.eq(null, EditApplier.anchorOf(null, 5f, 5f), "texte placé inconnu")
        T.eq(210f to 235f, EditApplier.anchorOf(base, 10f, 5f))
        // ce que l'éditeur enregistre est ce que la page applique : décalage → ancre → décalage
        val a = EditApplier.anchorOf(base, -12f, 40f)!!
        val s = EditApplier.positionShift(anchored(a.first, a.second), base, base)!!
        T.eq(-12f to 40f, s)
    }
    T.test("application : l'ancre est transmise au rendu, décalée comme la fenêtre ; la position est retenue (pinned)") {
        val page = PageResult(800, 1500, listOf(tb(0, 100f, 400f, 300f, 460f, "HELLO THERE", "Bonjour")))
        val e = BlockEdit("e1", EditKind.ADJUST, RectF(100f, 100f, 300f, 160f), "HELLO THERE", dx = 5f, dy = 6f, anchorX = 210f, anchorY = 140f)
        val a = EditApplier.apply(page, listOf(e), 300f)
        val r = a.render[0]!!
        T.ok(r.anchored && r.pinned && !r.manual, "ancre et position retenues")
        T.eq(210f, r.anchorX); T.eq(440f, r.anchorY, "y de la fenêtre = y de l'image + contexte")
        // sans ancre : comme avant
        val old = EditApplier.apply(page, listOf(e.copy(anchorX = null, anchorY = null)), 300f).render[0]!!
        T.ok(!old.anchored && old.pinned && old.anchorX == null && old.anchorY == null, "ancienne correction")
        // une demi-ancre n'est pas une ancre
        val half = EditApplier.apply(page, listOf(e.copy(anchorY = null)), 300f).render[0]!!
        T.ok(!half.anchored && half.anchorX == null, "demi-ancre ignorée")
        // bloc défini à la main
        val m = BlockEdit("m1", EditKind.MANUAL, null, "", UserRegion(RectF(90f, 90f, 310f, 230f)), "HELLO", "Bonjour", false, 0f, 0f, 1f, null, false, 30f, listOf(RectF(100f, 100f, 300f, 130f)), listOf("HELLO"), "FREE", null, anchorX = 200f, anchorY = 120f)
        val ma = EditApplier.apply(PageResult(800, 1500, emptyList()), listOf(m), 300f)
        val mr = ma.render[ma.page.blocks.single().id]!!
        T.ok(mr.manual && mr.anchored, "bloc manuel ancré"); T.eq(420f, mr.anchorY)
    }
    T.test("enregistrement : l'ancre fait l'aller-retour ; les lignes enregistrées avant l'ancre se lisent encore") {
        val e = BlockEdit("e1", EditKind.ADJUST, RectF(1f, 2f, 3f, 4f), "TEXT", translation = "Texte", dx = 4.5f, dy = -7f, scale = 1.25f, anchorX = 123.5f, anchorY = -8.25f)
        val d = EditsCodec.decode(EditsCodec.encode(e))!!
        T.eq(e, d, "aller-retour")
        T.eq(123.5f, d.anchorX); T.eq(-8.25f, d.anchorY)
        // sans ancre : « - » dans les deux champs
        val plain = EditsCodec.decode(EditsCodec.encode(e.copy(anchorX = null, anchorY = null)))!!
        T.ok(!plain.anchored && plain.anchorX == null && plain.anchorY == null, "pas d'ancre")
        // ancienne ligne (19 puis 20 champs, sans les champs d'ancre)
        val fields = EditsCodec.encode(e.copy(anchorX = null, anchorY = null)).split('\t')
        val l20 = EditsCodec.decode(fields.take(20).joinToString("\t"))!!
        val l19 = EditsCodec.decode(fields.take(19).joinToString("\t"))!!
        T.ok(!l20.anchored && !l19.anchored && l20.dx == 4.5f && l19.dy == -7f, "anciennes lignes")
        // champs abîmés : pas d'ancre plutôt qu'une ancre fausse
        val bad = EditsCodec.decode((fields.take(20) + listOf("NaN", "5")).joinToString("\t"))!!
        T.ok(!bad.anchored, "NaN ignoré")
        val half = EditsCodec.decode((fields.take(20) + listOf("5", "-")).joinToString("\t"))!!
        T.ok(!half.anchored && half.anchorX == null, "demi-ancre ignorée")
        // dans un fichier complet
        val parsed = EditsCodec.parse(EditsCodec.serialize(mapOf("img" to (1L to listOf(e)))))
        T.eq(e, parsed["img"]!!.second.single())
    }
    T.test("correction : une position posée seule est une correction (non neutre), conservée et décrite") {
        val only = BlockEdit("e1", EditKind.ADJUST, RectF(0f, 0f, 10f, 10f), "A", anchorX = 5f, anchorY = 5f)
        T.ok(!only.isNeutral, "non neutre")
        T.eq(1, EditApplier.upsert(emptyList(), only).size, "conservée")
        T.eq(0, EditApplier.upsert(listOf(only), only.copy(anchorX = null, anchorY = null)).size, "retirée quand la position est rétablie")
        val d = Corrections.describe(only)
        T.ok(d != null && d.first.contains("osition"), "décrite : " + d?.first)
    }
}
