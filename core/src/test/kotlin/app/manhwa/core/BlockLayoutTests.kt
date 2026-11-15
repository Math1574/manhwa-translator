package app.manhwa.core

import app.manhwa.core.TextErase.FontStyle
import kotlin.math.abs
import kotlin.math.hypot

/** Mise en page de la traduction : axe du texte d'origine, taille visée, texte dans l'image, collisions sans déplacement lointain. */
fun blockLayoutTests() {
    val m = TextMeasurer { t, size -> t.length * size * 0.5f }   // 0,5 em par caractère

    fun rect(l: Float, t: Float, r: Float, b: Float) = RowShape.rect(l, t, r, b)

    T.test("ancrage: une ligne se centre sur l'axe du texte d'origine quand la forme le permet") {
        val f = ShapeLayout.fit(rect(0f, 0f, 400f, 100f), 50f, "Non !", m, 20f, 6f, 1.2f, 4f, anchorX = 100f)
        T.ok(f.fits && f.lines.size == 1, "une ligne")
        T.eq(100f, f.lines[0].cx, "axe d'origine")
    }
    T.test("ancrage: sans ancre, la ligne se centre dans la forme comme avant") {
        val f = ShapeLayout.fit(rect(0f, 0f, 400f, 100f), 50f, "Non !", m, 20f, 6f, 1.2f, 4f)
        T.eq(200f, f.lines[0].cx, "centre de la forme")
    }
    T.test("ancrage: l'axe est ramené dans la forme quand la ligne n'y tient pas autour de lui") {
        val f = ShapeLayout.fit(rect(0f, 0f, 400f, 100f), 50f, "Non !", m, 20f, 6f, 1.2f, 4f, anchorX = 5f)
        val ln = f.lines[0]
        T.ok(ln.cx - ln.width / 2f >= 4f - 0.01f, "marge gauche respectée : ${ln.cx - ln.width / 2f}")
        T.ok(ln.cx < 60f, "au plus près de l'ancre : ${ln.cx}")
    }
    T.test("ancrage: toutes les lignes partagent le même axe quand la forme le permet (pas de lignes en escalier)") {
        val f = ShapeLayout.fit(rect(0f, 0f, 300f, 200f), 100f, "Peu importe la situation, je dois rassembler plus d'âmes ce soir", m, 40f, 8f, 1.2f, 4f, anchorX = 120f)
        T.ok(f.lines.size >= 2, "plusieurs lignes : ${f.lines.size}")
        T.ok(f.lines.all { abs(it.cx - f.lines[0].cx) < 0.01f }, "même axe : ${f.lines.map { it.cx }}")
        T.ok(abs(f.lines[0].cx - 120f) < 40f, "près de l'ancre : ${f.lines[0].cx}")
    }
    T.test("ancrage: quand aucun axe ne convient à toutes les lignes, chaque ligne reste dans l'étendue de sa rangée") {
        // le haut de la forme est étroit et décalé à droite, le bas est large : la ligne du haut ne peut pas suivre l'axe de la ligne du bas
        val l = FloatArray(120); val r = FloatArray(120)
        for (i in 0 until 120) { if (i < 50) { l[i] = 250f; r[i] = 400f } else { l[i] = 0f; r[i] = 400f } }
        val s = RowShape(0f, 1f, l, r)
        val f = ShapeLayout.fit(s, 60f, "Ça a l'air dégoûtant vraiment", m, 24f, 8f, 1.1f, 4f, anchorX = 100f)
        for (ln in f.lines) {
            val sp = s.span(ln.top, ln.top + f.lineStep - 0.01f)!!
            T.ok(ln.cx - ln.width / 2f >= sp[0] + 3.5f && ln.cx + ln.width / 2f <= sp[1] - 3.5f, "ligne « ${ln.text} » dans sa rangée")
        }
    }
    T.test("stagger: des lignes sur le même axe ne sont pas décalées ; des lignes décalées perdent de la valeur") {
        val aligned = ShapeFit(40f, 48f, listOf(PlacedLine("a", 100f, 0f, 80f), PlacedLine("b", 100f, 48f, 120f)), true)
        val stepped = ShapeFit(40f, 48f, listOf(PlacedLine("a", 160f, 0f, 80f), PlacedLine("b", 100f, 48f, 120f)), true)
        T.eq(0f, BlockLayout.stagger(aligned)); T.eq(40f, BlockLayout.quality(aligned))
        T.ok(BlockLayout.stagger(stepped) > 0.4f, "décalage ${BlockLayout.stagger(stepped)}")
        T.ok(BlockLayout.quality(stepped) < 0.8f * 40f, "valeur réduite ${BlockLayout.quality(stepped)}")
    }

    T.test("corps visé: mesure d'encre fiable → taille d'origine (5 % de plus au plus, jamais plus haut que la ligne) ; sinon l'estimation de repli") {
        T.ok(abs(BlockLayout.targetEm(100f, 200f, true, 60f) - 105f) < 0.01f, "5 % de plus")
        T.eq(90f, BlockLayout.targetEm(100f, 90f, true, 60f), "borné par la ligne")
        T.eq(60f, BlockLayout.targetEm(100f, 200f, false, 60f), "mesure peu fiable : repli")
        T.eq(5f, BlockLayout.targetEm(1f, 200f, true, 60f), "jamais sous 5 px")
    }

    T.test("dans l'image: le texte placé ne sort jamais de la page") {
        val f = ShapeFit(30f, 36f, listOf(PlacedLine("bord gauche", 10f, 40f, 100f), PlacedLine("bord droit", 790f, 76f, 100f)), true)
        val c = BlockLayout.clampToImage(f, 800f, 600f)
        for (ln in c.lines) T.ok(ln.cx - ln.width / 2f >= 2f - 0.01f && ln.cx + ln.width / 2f <= 798f + 0.01f, "ligne « ${ln.text} » dans la page : ${ln.cx}")
        val bas = ShapeFit(30f, 36f, listOf(PlacedLine("a", 400f, 580f, 100f), PlacedLine("b", 400f, 616f, 100f)), true)
        val cb = BlockLayout.clampToImage(bas, 800f, 600f)
        T.ok(BlockLayout.placedOf(cb).bottom <= 598.01f, "bloc ramené en bas : ${BlockLayout.placedOf(cb).bottom}")
        T.eq(36f, cb.lines[1].top - cb.lines[0].top, "interligne conservé")
        val ok = ShapeFit(30f, 36f, listOf(PlacedLine("a", 400f, 100f, 100f)), true)
        T.ok(BlockLayout.clampToImage(ok, 800f, 600f) === ok, "rien à corriger : même objet")
        val large = ShapeFit(30f, 36f, listOf(PlacedLine("trop large", 400f, 100f, 900f)), true)
        T.eq(400f, BlockLayout.clampToImage(large, 800f, 600f).lines[0].cx, "ligne plus large que la page : non touchée")
    }

    // ----- collisions -----
    fun prep(id: Int, box: RectF): BlockPrep =
        BlockPrep(TranslatedBlock(id, box, "SRC", "TR", false, box.height, listOf(box)), null, FontStyle.SPEECH, "TR")
    /** Mise en page factice : deux lignes de corps 60 × k centrées sur le bloc ; avec des obstacles durs et un corps ≥ 0,9, le texte « se loge » 400 px plus bas (ancien comportement). */
    fun fake(farWhenHard: Boolean): (BlockPrep, Float) -> BlockLayout.Result? = { pr, k ->
        val u = pr.block.lineBoxes.reduce { a, c -> a.union(c) }
        val size = 60f * k; val step = size
        val cy = u.centerY + (if (farWhenHard && pr.hardObstacles.isNotEmpty() && k >= 0.9f) 400f else 0f)
        val top = cy - step
        val fit = ShapeFit(size, step, listOf(PlacedLine("l1", u.centerX, top, 300f * k), PlacedLine("l2", u.centerX, top + step, 300f * k)), true)
        BlockLayout.Result(fit, false, true, size, 1.0f)
    }
    T.test("collisions: le texte rétrécit en place plutôt que d'aller se loger loin de son emplacement") {
        val a = RectF(100f, 100f, 400f, 160f); val b = RectF(100f, 200f, 400f, 260f)
        val preps = listOf(prep(0, a), prep(1, b))
        val laids = ArrayList<BlockLayout.Result?>(preps.map { fake(false)(it, 1f) })   // 120 px de haut chacun : ils se recouvrent
        T.ok(laids[0]!!.placed(0f).intersection(laids[1]!!.placed(0f)) != null, "départ : les deux textes se recouvrent")
        val before = laids[0]!!.fit.size
        BlockLayout.resolveCollisions(preps, laids, { false }, fake(true))
        for (i in 0..1) {
            val u = preps[i].block.lineBoxes[0]; val r = laids[i]!!.placed(0f)
            T.ok(hypot((r.centerX - u.centerX).toDouble(), (r.centerY - u.centerY).toDouble()) < 100.0, "bloc $i resté près de l'original : ${r.centerY - u.centerY}")
        }
        val p0 = laids[0]!!.placed(2f); val p1 = laids[1]!!.placed(2f)
        T.ok((p0.intersection(p1)?.area ?: 0f) <= 0.04f * p0.area, "plus de recouvrement")
        T.ok(laids[0]!!.fit.size < before || laids[1]!!.fit.size < before, "au moins un texte a rétréci")
    }
    T.test("collisions: aucun déplacement lointain même quand aucun corps ne convient en place") {
        val a = RectF(100f, 100f, 400f, 160f); val b = RectF(100f, 150f, 400f, 210f)   // blocs d'origine déjà collés l'un à l'autre
        val preps = listOf(prep(0, a), prep(1, b))
        val laids = ArrayList<BlockLayout.Result?>(preps.map { fake(false)(it, 1f) })
        val rects0 = laids.map { it!!.placed(0f) }
        // même avec k petit, le texte factice recouvre toujours l'autre : seule solution « lointaine » = ancien comportement, qui est refusé
        val always = { pr: BlockPrep, k: Float -> fake(false)(pr, 1f)?.let { r -> if (pr.hardObstacles.isNotEmpty()) r.shifted(0f, 500f) else r } }
        BlockLayout.resolveCollisions(preps, laids, { false }, always)
        for (i in 0..1) T.ok(laids[i]!!.placed(0f).centerY == rects0[i].centerY, "bloc $i non déplacé loin : ${laids[i]!!.placed(0f).centerY} / ${rects0[i].centerY}")
    }
    T.test("collisions: un texte épinglé par l'utilisateur ou dans une bulle n'est pas touché") {
        val a = RectF(100f, 100f, 400f, 160f); val b = RectF(100f, 200f, 400f, 260f)
        val preps = listOf(prep(0, a), prep(1, b))
        val laids = ArrayList<BlockLayout.Result?>(preps.map { fake(false)(it, 1f) })
        val first = laids.map { it }
        BlockLayout.resolveCollisions(preps, laids, { true }, fake(false))
        T.ok(laids[0] === first[0] && laids[1] === first[1], "épinglés : inchangés")
    }
}
