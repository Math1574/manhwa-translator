package app.manhwa.core

import kotlin.math.abs

private fun near(a: Float, b: Float, eps: Float = 0.01f) = abs(a - b) <= eps

/** Zoom et déplacement de la vue de l'éditeur, historique défaire / refaire : calculs purs. */
fun editorViewTests() {
    val aw = 1000f; val ah = 800f        // zone d'affichage
    val iw = 900f; val ih = 4000f        // fenêtre traitée (une longue bande)

    T.test("vue de l'éditeur : l'image recadrée est centrée, à l'échelle la plus grande qui tient") {
        val l = EditorView.layout(RectF(100f, 200f, 600f, 600f), aw, ah)             // 500 × 400 : la largeur limite (2,0), hauteur 800 pile
        T.ok(near(l.scale, 2f) && near(l.offX, 0f) && near(l.offY, 0f), "échelle ${l.scale}, décalage ${l.offX}/${l.offY}")
        val tall = EditorView.layout(RectF(0f, 0f, 200f, 800f), aw, ah)              // 200 × 800 : la hauteur limite (1,0), bandes à gauche et à droite
        T.ok(near(tall.scale, 1f) && near(tall.offX, 400f) && near(tall.offY, 0f), "bandes latérales : ${tall.offX}")
        val crop = RectF(100f, 200f, 600f, 600f)
        T.ok(near(l.screenX(100f, crop), 0f) && near(l.screenY(600f, crop), 800f) && near(l.imageX(500f, crop), 350f) && near(l.imageY(400f, crop), 400f), "aller-retour")
    }

    T.test("zoom : sans changement, la vue reste la même (même avec des bandes autour)") {
        val crop = RectF(100f, 1000f, 500f, 1300f)      // 400 × 300 : format différent de la zone
        val r = EditorView.transform(crop, aw, ah, 300f, 300f, 300f, 300f, 1f, iw, ih)
        T.ok(near(r.left, crop.left) && near(r.top, crop.top) && near(r.right, crop.right) && near(r.bottom, crop.bottom), "inchangé : $r")
    }

    T.test("zoom : le point sous les doigts reste sous les doigts, les proportions sont gardées") {
        val crop = RectF(100f, 1000f, 500f, 1300f)
        val fx = 640f; val fy = 250f
        val before = EditorView.layout(crop, aw, ah)
        val px = before.imageX(fx, crop); val py = before.imageY(fy, crop)
        val r = EditorView.transform(crop, aw, ah, fx, fy, fx, fy, 2f, iw, ih)
        T.ok(near(r.width, 200f) && near(r.height, 150f), "moitié : ${r.width} × ${r.height}")
        val after = EditorView.layout(r, aw, ah)
        T.ok(near(after.screenX(px, r), fx, 0.05f) && near(after.screenY(py, r), fy, 0.05f), "point ancré : ${after.screenX(px, r)}, ${after.screenY(py, r)}")
        val out = EditorView.transform(crop, aw, ah, fx, fy, fx, fy, 0.5f, iw, ih)
        T.ok(near(out.width, 800f) && near(out.height, 600f) && out.right <= iw + 0.01f, "éloigné : ${out.width} × ${out.height}")
        T.ok(near(out.width / out.height, crop.width / crop.height, 0.001f), "proportions")
    }

    T.test("déplacement : le point suit les doigts (déplacement seul, sans zoom)") {
        val crop = RectF(100f, 1000f, 500f, 1300f)
        val before = EditorView.layout(crop, aw, ah)
        val px = before.imageX(500f, crop); val py = before.imageY(400f, crop)
        val r = EditorView.transform(crop, aw, ah, 500f, 400f, 560f, 340f, 1f, iw, ih)
        T.ok(near(r.width, crop.width) && near(r.height, crop.height), "taille inchangée")
        val after = EditorView.layout(r, aw, ah)
        T.ok(near(after.screenX(px, r), 560f, 0.05f) && near(after.screenY(py, r), 340f, 0.05f), "le point est sous la nouvelle position")
        T.ok(r.left < crop.left && r.top > crop.top, "tirer vers la droite montre ce qui est à gauche, tirer vers le haut montre ce qui est dessous")
    }

    T.test("zoom : limites — taille minimale, fenêtre entière, bords, valeurs absurdes") {
        var r = RectF(100f, 1000f, 500f, 1300f)
        repeat(30) { r = EditorView.transform(r, aw, ah, 500f, 400f, 500f, 400f, 1.5f, iw, ih) }
        T.ok(near(r.width, EditorView.MIN_W) && near(r.height, EditorView.MIN_W * 0.75f, 0.5f), "ne descend pas sous la taille minimale : ${r.width} × ${r.height}")
        var o = RectF(100f, 1000f, 500f, 1300f)
        repeat(30) { o = EditorView.transform(o, aw, ah, 500f, 400f, 500f, 400f, 0.6f, iw, ih) }
        T.ok(o.width <= iw + 0.01f && o.height <= ih + 0.01f && o.left >= 0f && o.top >= 0f && o.right <= iw + 0.01f && o.bottom <= ih + 0.01f, "reste dans la fenêtre : $o")
        T.ok(near(o.width, iw), "la largeur de la fenêtre est atteinte : ${o.width}")
        // bord gauche : tirer encore ne déforme pas la vue
        var e = RectF(0f, 1000f, 400f, 1300f)
        repeat(5) { e = EditorView.transform(e, aw, ah, 500f, 400f, 700f, 400f, 1f, iw, ih) }
        T.ok(near(e.left, 0f) && near(e.width, 400f) && near(e.height, 300f), "arrêtée au bord : $e")
        var f = RectF(500f, 3700f, 900f, 4000f)
        repeat(5) { f = EditorView.transform(f, aw, ah, 500f, 400f, 300f, 100f, 1f, iw, ih) }
        T.ok(near(f.right, 900f) && near(f.bottom, 4000f) && near(f.width, 400f) && near(f.height, 300f), "arrêtée au coin : $f")
        val base = RectF(100f, 1000f, 500f, 1300f)
        for (z in listOf(Float.NaN, 0f, -2f, Float.POSITIVE_INFINITY)) {
            val x = EditorView.transform(base, aw, ah, 500f, 400f, 500f, 400f, z, iw, ih)
            T.ok(near(x.width, base.width) && near(x.left, base.left), "facteur $z ignoré")
        }
        T.eq(base, EditorView.transform(base, 0f, ah, 1f, 1f, 2f, 2f, 2f, iw, ih), "zone vide : rien ne change")
        T.eq(base, EditorView.transform(base, aw, ah, Float.NaN, 1f, 2f, 2f, 2f, iw, ih), "position absurde : rien ne change")
    }

    T.test("zoom : une vue déjà plus petite que le minimum ne saute pas, et une fenêtre plus petite que le minimum reste utilisable") {
        val tiny = RectF(10f, 10f, 110f, 90f)                // 100 × 80 < 160 × 120
        val same = EditorView.transform(tiny, aw, ah, 500f, 400f, 500f, 400f, 1f, iw, ih)
        T.ok(near(same.width, 100f) && near(same.height, 80f), "taille gardée : ${same.width} × ${same.height}")
        val more = EditorView.transform(tiny, aw, ah, 500f, 400f, 500f, 400f, 3f, iw, ih)
        T.ok(near(more.width, 100f), "pas plus petit : ${more.width}")
        val small = EditorView.transform(RectF(0f, 0f, 100f, 100f), aw, ah, 500f, 400f, 500f, 400f, 4f, 100f, 100f)
        T.ok(small.width <= 100f && small.width > 0f && small.left >= 0f && small.right <= 100f, "fenêtre de 100 px : $small")
    }

    T.test("région à décoder : arrondie vers l'extérieur, dans la fenêtre, d'au moins un pixel") {
        val r = EditorView.decodeRegion(RectF(10.4f, 20.6f, 110.2f, 90.9f), iw, ih)
        T.eq(RectF(10f, 20f, 111f, 91f), r)
        val edge = EditorView.decodeRegion(RectF(-5f, -5f, 2000f, 5000f), iw, ih)
        T.eq(RectF(0f, 0f, iw, ih), edge)
        val flat = EditorView.decodeRegion(RectF(50f, 50f, 50f, 50f), iw, ih)
        T.ok(flat.width >= 1f && flat.height >= 1f, "au moins un pixel : $flat")
    }

    T.test("historique : défaire et refaire dans l'ordre, une nouvelle modification efface le « refaire »") {
        val h = History<String>()
        T.ok(!h.canUndo && !h.canRedo && h.undo("x") == null && h.redo("x") == null, "vide")
        h.record("A"); h.record("B")                         // états d'avant : A puis B ; l'état courant est « C »
        T.eq(2, h.undoCount)
        T.eq("B", h.undo("C"), "dernier état d'avant"); T.eq("A", h.undo("B")); T.ok(!h.canUndo && h.redoCount == 2, "tout défait")
        T.eq("B", h.redo("A")); T.eq("C", h.redo("B")); T.ok(!h.canRedo && h.undoCount == 2, "tout refait")
        T.eq("B", h.undo("C")); T.ok(h.canRedo, "un état à refaire")
        h.record("B2")                                       // nouvelle modification après avoir défait
        T.ok(!h.canRedo, "le « refaire » est effacé")
        T.eq("B2", h.undo("D"))
    }

    T.test("historique : borné, et un geste interrompu rend l'état sans perdre le « refaire »") {
        val h = History<Int>(3)
        for (i in 1..5) h.record(i)
        T.eq(3, h.undoCount, "les plus anciens sont oubliés")
        T.eq(5, h.undo(6)); T.eq(4, h.undo(5)); T.eq(3, h.undo(4)); T.eq(null, h.undo(3), "1 et 2 sont oubliés")
        val g = History<String>()
        g.record("a"); g.undo("b")                           // « b » peut être refait
        T.eq(1, g.redoCount)
        g.record("b")                                        // un geste commence : le « refaire » est effacé…
        T.eq(0, g.redoCount)
        T.eq("b", g.revert(), "…puis le geste est interrompu : état rendu")
        T.eq(1, g.redoCount, "…et le « refaire » est revenu")
        T.eq("b", g.redo("a"), "refaire fonctionne toujours")
        T.eq(null, History<Int>().revert())
        val c = History<Int>(); c.record(1); c.clear(); T.ok(!c.canUndo && !c.canRedo, "vidé")
    }
}
