package app.manhwa.core

private fun det(cls: RegionClass, l: Float, t: Float, r: Float, b: Float, s: Float = 0.9f) = Detection(cls, RectF(l, t, r, b), s)

/** Tuiles du détecteur sur une page haute, et recollage des bulles / zones coupées par leurs limites (valeurs relevées sur le journal d'un vrai téléphone : page 900×16000). */
fun detectionTilesTests() {
    T.test("tuiles: une image courte tient en une seule tuile, une page haute est découpée avec 15 % de recouvrement") {
        T.eq(listOf(DetectionTiles.Tile(0, 1000)), DetectionTiles.tiles(800, 1000), "800×1000")
        T.eq(1, DetectionTiles.tiles(800, 1260).size, "jusqu'à 5 % de plus que la tuile : une seule")
        val two = DetectionTiles.tiles(800, 1261)
        T.eq(listOf(DetectionTiles.Tile(0, 1200), DetectionTiles.Tile(1020, 1261)), two, "juste au-delà : deux tuiles")
        val t = DetectionTiles.tiles(900, 16000)
        T.eq(DetectionTiles.Tile(0, 1350), t[0]); T.eq(DetectionTiles.Tile(1148, 2498), t[1]); T.eq(DetectionTiles.Tile(8036, 9386), t[7])
        T.eq(16000, t.last().end, "la dernière tuile va jusqu'en bas")
        T.ok(t.zipWithNext().all { (a, b) -> b.start == a.end - 202 }, "recouvrement de 202 px")
        T.eq(2, DetectionTiles.sample(2000)); T.eq(1, DetectionTiles.sample(900))
    }

    T.test("recollage: une bulle coupée au bas d'une tuile et au haut de la suivante redevient une seule bulle (olive, page 900×16000)") {
        // tuile 6888..8238 puis 8036..9386 : « 0,7711,900,8237 » finit sur la limite, « -1,8038,902,8578 » commence sur l'autre
        val top = det(RegionClass.BUBBLE, 0f, 7711f, 900f, 8237f, 0.93f); val bottom = det(RegionClass.BUBBLE, -1f, 8038f, 902f, 8578f, 0.88f)
        val r = DetectionTiles.stitch(listOf(top, bottom), 900, 16000)
        T.eq(1, r.size)
        T.eq(RectF(-1f, 7711f, 902f, 8578f), r[0].box)
        T.eq(RegionClass.BUBBLE, r[0].cls); T.eq(0.93f, r[0].score, "la meilleure des deux confiances")
    }

    T.test("recollage: deux zones de texte coupées au même endroit ne font plus qu'une zone (donc un seul bloc)") {
        val a = det(RegionClass.TEXT_FREE, 28f, 7929f, 893f, 8237f); val b = det(RegionClass.TEXT_FREE, 39f, 8038f, 890f, 8356f)
        val r = DetectionTiles.stitch(listOf(b, a), 900, 16000)
        T.eq(1, r.size); T.eq(RectF(28f, 7929f, 893f, 8356f), r[0].box)
    }

    T.test("recollage: les autres régions ne sont pas touchées, et l'ordre des morceaux n'a pas d'importance") {
        val bubble = det(RegionClass.BUBBLE, 0f, 7711f, 900f, 8237f); val bubble2 = det(RegionClass.BUBBLE, -1f, 8038f, 902f, 8578f)
        val zone = det(RegionClass.TEXT_BUBBLE, 100f, 7900f, 800f, 8237f); val zone2 = det(RegionClass.TEXT_BUBBLE, 100f, 8038f, 800f, 8300f)
        val other = det(RegionClass.BUBBLE, 100f, 3000f, 500f, 3300f)
        val r = DetectionTiles.stitch(listOf(other, zone2, bubble2, zone, bubble), 900, 16000)
        T.eq(3, r.size)
        T.ok(r.any { it.cls == RegionClass.BUBBLE && it.box == RectF(-1f, 7711f, 902f, 8578f) }, "bulle recollée")
        T.ok(r.any { it.cls == RegionClass.TEXT_BUBBLE && it.box == RectF(100f, 7900f, 800f, 8300f) }, "zone recollée")
        T.ok(r.any { it.box == other.box }, "l'autre bulle est intacte")
    }

    T.test("recollage: pas de recollage entre classes différentes, hors des limites de tuile, ou entre deux colonnes côte à côte") {
        val a = det(RegionClass.BUBBLE, 0f, 7711f, 900f, 8237f)
        T.eq(2, DetectionTiles.stitch(listOf(a, det(RegionClass.TEXT_BUBBLE, 0f, 8038f, 900f, 8578f)), 900, 16000).size, "classes différentes")
        T.eq(2, DetectionTiles.stitch(listOf(a, det(RegionClass.BUBBLE, 0f, 8100f, 900f, 8578f)), 900, 16000).size, "le second ne commence pas sur la limite de tuile")
        T.eq(2, DetectionTiles.stitch(listOf(det(RegionClass.BUBBLE, 0f, 7711f, 900f, 8100f), det(RegionClass.BUBBLE, 0f, 8038f, 900f, 8578f)), 900, 16000).size, "le premier ne finit pas sur la limite")
        val left = det(RegionClass.BUBBLE, 20f, 7711f, 400f, 8237f); val right = det(RegionClass.BUBBLE, 480f, 8038f, 880f, 8578f)
        T.eq(2, DetectionTiles.stitch(listOf(left, right), 900, 16000).size, "colonnes côte à côte")
        T.eq(2, DetectionTiles.stitch(listOf(a, det(RegionClass.BUBBLE, 0f, 8038f, 900f, 8578f)), 900, 600).size, "image à une seule tuile : rien à recoller")
        T.eq(1, DetectionTiles.stitch(listOf(a), 900, 16000).size, "une seule détection")
    }

    T.test("recollage: une bulle vue en entier par l'autre tuile écarte son morceau coupé, dans les deux sens") {
        // bulle entière dans la tuile suivante (haut 8100), son morceau vu par la tuile précédente finit sur la limite 8238
        val whole = det(RegionClass.BUBBLE, 100f, 8100f, 800f, 8600f, 0.9f); val cutBottom = det(RegionClass.BUBBLE, 100f, 8100f, 800f, 8237f, 0.7f)
        val r1 = DetectionTiles.stitch(listOf(cutBottom, whole), 900, 16000)
        T.eq(1, r1.size); T.eq(whole.box, r1[0].box)
        // bulle entière dans la tuile précédente (bas 8200), son morceau vu par la suivante commence sur la limite 8036
        val whole2 = det(RegionClass.BUBBLE, 100f, 7800f, 800f, 8200f, 0.9f); val cutTop = det(RegionClass.BUBBLE, 100f, 8038f, 800f, 8200f, 0.8f)
        val r2 = DetectionTiles.stitch(listOf(whole2, cutTop), 900, 16000)
        T.eq(1, r2.size); T.eq(whole2.box, r2[0].box)
        // une région coupée mais qui n'est contenue dans rien d'autre reste
        T.eq(1, DetectionTiles.stitch(listOf(cutTop, det(RegionClass.TEXT_FREE, 100f, 8000f, 800f, 8500f)), 900, 16000).count { it.cls == RegionClass.BUBBLE })
    }

    T.test("recollage: une région sur trois tuiles est recollée en chaîne") {
        // page 900×4000 : tuiles 0..1350, 1148..2498, 2296..3646, 3444..4000
        val t = DetectionTiles.tiles(900, 4000)
        T.eq(listOf(DetectionTiles.Tile(0, 1350), DetectionTiles.Tile(1148, 2498), DetectionTiles.Tile(2296, 3646), DetectionTiles.Tile(3444, 4000)), t)
        val p0 = det(RegionClass.TEXT_FREE, 50f, 1000f, 850f, 1349f); val p1 = det(RegionClass.TEXT_FREE, 50f, 1149f, 850f, 2497f); val p2 = det(RegionClass.TEXT_FREE, 50f, 2297f, 850f, 3000f)
        val r = DetectionTiles.stitch(listOf(p2, p0, p1), 900, 4000)
        T.eq(1, r.size); T.eq(RectF(50f, 1000f, 850f, 3000f), r[0].box)
    }

    T.test("recollage: grande image lue à demi résolution (écarts de deux pixels) : tolérance adaptée") {
        // 1800 px de large : tuiles de 2700 px, recouvrement 405 : 0..2700 puis 2295..4995
        val t = DetectionTiles.tiles(1800, 9000)
        T.eq(DetectionTiles.Tile(0, 2700), t[0]); T.eq(DetectionTiles.Tile(2295, 4995), t[1])
        val a = det(RegionClass.BUBBLE, 100f, 2300f, 1700f, 2696f); val b = det(RegionClass.BUBBLE, 110f, 2301f, 1690f, 3100f)
        val r = DetectionTiles.stitch(listOf(a, b), 1800, 9000)
        T.eq(1, r.size); T.eq(RectF(100f, 2300f, 1700f, 3100f), r[0].box)
        // le second ne commence pas sur la limite de tuile (à 30 px) et contient presque tout le premier : c'est la bulle entière, le premier morceau est écarté
        T.eq(1, DetectionTiles.stitch(listOf(a, det(RegionClass.BUBBLE, 110f, 2330f, 1690f, 3100f)), 1800, 9000).size)
        // ... et s'il n'en contient presque rien, ce sont deux régions distinctes
        T.eq(2, DetectionTiles.stitch(listOf(a, det(RegionClass.BUBBLE, 110f, 2600f, 1690f, 3100f)), 1800, 9000).size)
    }
}
