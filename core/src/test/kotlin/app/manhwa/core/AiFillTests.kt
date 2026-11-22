package app.manhwa.core

import kotlin.math.abs
import kotlin.math.exp

/** Retouche par IA (AiFill) : rectangle vu par le modèle, réduction des grands lettrages, entrée du modèle, repli sur la méthode classique, branchement dans l'effacement des textes posés sur un dessin. */
fun aiFillTests() {
    /** Faux modèle : couleur constante [r], [g], [b] (0..1), entrées gardées pour contrôle. */
    class Fake(val r: Float, val g: Float, val b: Float) : AiInpainter {
        val calls = ArrayList<Triple<Int, Int, Pair<FloatArray, FloatArray>>>()
        override fun run(image: FloatArray, mask: FloatArray, w: Int, h: Int): FloatArray {
            calls.add(Triple(w, h, image to mask))
            val pp = w * h
            return FloatArray(3 * pp) { i -> when (i / pp) { 0 -> r; 1 -> g; else -> b } }
        }
    }
    fun region(w: Int, h: Int): IntArray = IntArray(w * h) { i -> (0xFF shl 24) or ((i % w) % 256 shl 16) or ((i / w) % 256 shl 8) or 77 }

    T.test("AiFill: seuls les pixels du trou prennent la couleur du modèle ; entrée complétée au multiple de 8, trou mis à 0 et marqué 1") {
        val w = 101; val h = 37
        val px = region(w, h)
        val hole = BooleanArray(w * h) { i -> val x = i % w; val y = i / w; x in 20..60 && y in 10..25 }
        val m = Fake(0.25f, 0.5f, 0.75f)
        val out = AiFill.fill(px, w, h, hole, m)!!
        T.eq(1, m.calls.size, "une seule tuile")
        val (cw, ch, io) = m.calls[0]
        T.ok(cw == 104 && ch == 40, "taille complétée ${cw}x$ch (attendu 104x40)")
        val (image, mask) = io
        val pp = cw * ch
        T.ok(mask[15 * cw + 30] == 1f && image[15 * cw + 30] == 0f && image[pp + 15 * cw + 30] == 0f, "pixel du trou : masque 1, image 0")
        T.ok(mask[2 * cw + 5] == 0f && abs(image[2 * cw + 5] - 5 / 255f) < 1e-6f && abs(image[2 * pp + 2 * cw + 5] - 77 / 255f) < 1e-6f, "pixel connu : masque 0, couleur d'origine")
        // complément par réflexion : la colonne 101 reprend la colonne 99, la ligne 37 reprend la ligne 35
        T.ok(abs(image[3 * cw + 101] - 99 / 255f) < 1e-6f && abs(image[pp + 37 * cw + 4] - 35 / 255f) < 1e-6f, "complément réfléchi")
        var changed = 0; var wrong = 0
        for (i in 0 until w * h) {
            if (hole[i]) { if (out[i] != (0xFF shl 24 or (64 shl 16) or (128 shl 8) or 191)) wrong++ }
            else if (out[i] != px[i]) changed++
        }
        T.eq(0, wrong, "pixels du trou sans la couleur du modèle")
        T.eq(0, changed, "pixels hors du trou modifiés")
    }

    T.test("AiFill: le modèle ne voit que le trou et 48 px de dessin autour, à la résolution d'origine") {
        val w = 300; val h = 200
        val px = region(w, h)
        val hole = BooleanArray(w * h) { i -> val x = i % w; val y = i / w; x in 200..220 && y in 150..160 }
        val m = Fake(0.1f, 0.2f, 0.3f)
        val out = AiFill.fill(px, w, h, hole, m)!!
        T.eq(1, m.calls.size, "une passe")
        val (cw, ch, io) = m.calls[0]
        T.ok(cw == 120 && ch == 104, "taille vue par le modèle ${cw}x$ch (attendu 117x98 complété en 120x104)")
        val pp = cw * ch
        T.ok(abs(io.first[0] - 152 / 255f) < 1e-6f && abs(io.first[pp] - 102 / 255f) < 1e-6f, "coin du rectangle vu = pixel (152, 102) de la région")
        var changed = 0; for (i in 0 until w * h) if (!hole[i] && out[i] != px[i]) changed++
        T.eq(0, changed, "pixels hors du trou modifiés")
    }

    T.test("AiFill: grand lettrage réduit à 640 px pour une seule passe, puis agrandi sans saut") {
        val w = 1100; val h = 300
        val px = region(w, h)
        val hole = BooleanArray(w * h) { i -> val x = i % w; val y = i / w; y in 100..140 && (x in 30..80 || x in 350..700) }
        val seen = ArrayList<Pair<Int, Int>>()
        // faux modèle : rouge croissant de gauche à droite de l'entrée réduite
        val m = AiInpainter { _, _, tw, th -> seen.add(tw to th); FloatArray(3 * tw * th) { i -> if (i < tw * th) (i % tw) / (tw - 1f) else 0.5f } }
        val out = AiFill.fill(px, w, h, hole, m)!!
        T.eq(listOf(640 to 120), seen, "une passe de 640 px de large (rectangle 749x137 réduit en 640x118, complété en 640x120)")
        var maxJump = 0; var prev = -1; var down = 0
        for (x in 350..700) { val r = (out[120 * w + x] shr 16) and 0xFF; if (prev >= 0) { maxJump = maxOf(maxJump, abs(r - prev)); if (r < prev) down++ }; prev = r }
        T.ok(maxJump <= 2 && down == 0, "rouge le long de la ligne : saut maximal $maxJump, $down baisses")
        val r30 = (out[120 * w + 30] shr 16) and 0xFF
        T.ok(r30 in 8..12, "rouge au début du trou : $r30 (environ 10 attendu)")
        var changed = 0; for (i in 0 until w * h) if (!hole[i] && out[i] != px[i]) changed++
        T.eq(0, changed, "pixels hors du trou modifiés")
    }

    T.test("AiFill: réflexion des indices, échecs et limites rendent null (la méthode classique prend le relais)") {
        T.eq(listOf(1, 0, 1, 2, 3, 4, 3, 2, 1, 0, 1), (-1..9).map { AiFill.mirror(it, 5) }, "indices réfléchis")
        T.eq(0, AiFill.mirror(3, 1), "côté d'un pixel")
        val w = 40; val h = 30; val px = region(w, h)
        val hole = BooleanArray(w * h) { it % 7 == 0 }
        T.ok(AiFill.fill(px, w, h, hole, AiInpainter { _, _, _, _ -> null }) == null, "modèle en échec")
        T.ok(AiFill.fill(px, w, h, hole, AiInpainter { _, _, _, _ -> throw IllegalStateException("x") }) == null, "modèle qui lève une exception")
        T.ok(AiFill.fill(px, w, h, hole, AiInpainter { _, _, tw, th -> FloatArray(tw * th) }) == null, "sortie trop courte")
        T.ok(AiFill.fill(px, w, h, BooleanArray(w * h), Fake(0f, 0f, 0f)) == null, "rien à reconstruire")
        val big = 2100; val bh = 2000
        T.ok(AiFill.fill(IntArray(big * bh), big, bh, BooleanArray(big * bh) { true }, Fake(0f, 0f, 0f)) == null, "région démesurée")
    }

    // effacement complet : texte blanc à lueur sur un dégradé (mode ART), puis texte noir sur bulle blanche (pas de retouche par IA)
    val W = 260; val H = 150
    fun gradient(): IntArray = IntArray(W * H) { i -> val x = i % W; val y = i / W; (0xFF shl 24) or ((20 + x / 4) shl 16) or ((60 + y / 2) shl 8) or (150 + x / 10) }
    /** Lettres blanches (barres de 5 px tous les 11 px) avec une lueur douce autour (comme dans artFillTests). */
    fun whiteLetters(px: IntArray) {
        val m = BooleanArray(W * H)
        for (y in 60 until 90) for (x in 50 until 210) if ((x - 50) % 11 < 5) { px[y * W + x] = 0xFFFFFFFF.toInt(); m[y * W + x] = true }
        val d = IntArray(W * H) { if (m[it]) 0 else -1 }
        val q = IntArray(W * H); var qh = 0; var qt = 0
        for (i in 0 until W * H) if (m[i]) q[qt++] = i
        while (qh < qt) {
            val i = q[qh++]; val x = i % W; val y = i / W
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                val j = ny * W + nx
                if (d[j] >= 0 || d[i] >= 9) continue
                d[j] = d[i] + 1; q[qt++] = j
            }
        }
        for (i in 0 until W * H) if (d[i] > 0) {
            val a = 0.75f * exp(-d[i] / 3.5f); val c = px[i]
            fun mix(sh: Int) = (((c shr sh) and 0xFF) * (1 - a) + 255 * a).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
    /** Dégradé strié de traits sombres en diagonale (un décor dessiné, pas un aplat). */
    fun textured(): IntArray {
        val px = gradient()
        for (i in px.indices) { val x = i % W; val y = i / W; if ((x + y) % 9 < 2) { val c = px[i]; px[i] = (0xFF shl 24) or ((((c shr 16) and 0xFF) / 3) shl 16) or ((((c shr 8) and 0xFF) / 3) shl 8) or ((c and 0xFF) / 3) } }
        return px
    }
    T.test("AiFill: fond uni autour du trou (noir, dégradé doux) reconnu ; un décor strié ou bruité ne l'est pas") {
        val w = 120; val h = 80
        val hole = BooleanArray(w * h) { i -> val x = i % w; val y = i / w; x in 40..80 && y in 25..55 }
        T.ok(AiFill.flatAround(IntArray(w * h) { 0xFF000000.toInt() }, w, h, hole), "noir")
        T.ok(AiFill.flatAround(IntArray(w * h) { i -> val x = i % w; (0xFF shl 24) or ((40 + x / 2) shl 16) or ((90 + x / 3) shl 8) or 150 }, w, h, hole), "dégradé doux")
        val rnd = java.util.Random(7)
        T.ok(!AiFill.flatAround(IntArray(w * h) { val v = 100 + rnd.nextInt(60); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }, w, h, hole), "bruit")
        T.ok(!AiFill.flatAround(IntArray(w * h) { i -> val x = i % w; val y = i / w; if ((x + y) % 9 < 2) 0xFF202020.toInt() else 0xFFC0C0C0.toInt() }, w, h, hole), "traits")
        T.ok(!AiFill.flatAround(IntArray(w * h) { 0xFF000000.toInt() }, w, h, BooleanArray(w * h) { true }), "rien autour pour juger")
    }
    T.test("plan ART sur fond uni (dégradé) : pas de retouche par IA, la reconstruction classique suffit") {
        val px = gradient(); whiteLetters(px)
        val m = Fake(0f, 1f, 0f)
        val before = Erasure.inpainter
        try {
            Erasure.inpainter = m
            val plan = Erasure.plan(ArraySource(W, H, px), 1f, listOf(RectF(50f, 60f, 210f, 90f)), 28f, W.toFloat(), H.toFloat())!!
            T.eq(Erasure.Mode.ART, plan.mode, "mode")
            T.eq(0, m.calls.size, "appels du modèle")
        } finally { Erasure.inpainter = before }
    }
    T.test("plan ART avec retouche par IA : le remplissage du texte effacé vient du modèle ; sans modèle, la reconstruction classique") {
        val px = textured(); whiteLetters(px)
        val src = ArraySource(W, H, px)
        val lines = listOf(RectF(50f, 60f, 210f, 90f))
        val m = Fake(0f, 1f, 0f)
        val before = Erasure.inpainter
        try {
            Erasure.inpainter = m
            val plan = Erasure.plan(src, 1f, lines, 28f, W.toFloat(), H.toFloat())!!
            T.eq(Erasure.Mode.ART, plan.mode, "mode")
            T.ok(m.calls.isNotEmpty(), "le modèle est appelé")
            var green = 0; var other = 0
            for (i in 0 until plan.w * plan.h) if (plan.mask[i] && (plan.layer[i] ushr 24) > 0) { if ((plan.layer[i] and 0xFFFFFF) == 0x00FF00) green++ else other++ }
            T.ok(green > 0 && other == 0, "pixels effacés : $green du modèle, $other d'ailleurs")
            Erasure.inpainter = null
            val classic = Erasure.plan(src, 1f, lines, 28f, W.toFloat(), H.toFloat())!!
            var g2 = 0; for (i in 0 until classic.w * classic.h) if (classic.mask[i] && (classic.layer[i] and 0xFFFFFF) == 0x00FF00) g2++
            T.eq(0, g2, "sans modèle, aucun pixel du faux modèle")
        } finally { Erasure.inpainter = before }
    }
    T.test("modèle pas prêt (module absent, réglage coupé) : jamais appelé, reconstruction classique ; réglage « effacement par IA » retenu") {
        val px = textured(); whiteLetters(px)
        var calls = 0
        val before = Erasure.inpainter
        try {
            Erasure.inpainter = object : AiInpainter {
                override fun run(image: FloatArray, mask: FloatArray, w: Int, h: Int): FloatArray? { calls++; return FloatArray(3 * w * h) }
                override fun ready() = false
            }
            val plan = Erasure.plan(ArraySource(W, H, px), 1f, listOf(RectF(50f, 60f, 210f, 90f)), 28f, W.toFloat(), H.toFloat())!!
            T.eq(Erasure.Mode.ART, plan.mode, "mode")
            T.eq(0, calls, "appels du modèle")
        } finally { Erasure.inpainter = before }
        T.ok(AppSettings().aiErase && AppSettings.parse(null).aiErase, "activé par défaut")
        T.ok(!AppSettings.parse(AppSettings().copy(aiErase = false).serialize()).aiErase, "coupé : retenu")
        T.ok(AppSettings.parse(AppSettings().serialize().lines().filter { !it.startsWith("aiErase") }.joinToString("\n")).aiErase, "ancien fichier : activé")
    }
    T.test("bulle blanche (texte noir) : pas de retouche par IA, le blanc exact reste") {
        val px = IntArray(W * H) { 0xFFFFFFFF.toInt() }
        for (y in 62 until 86) for (x in 60 until 200) if ((x - 60) % 9 < 4) px[y * W + x] = 0xFF111111.toInt()
        val m = Fake(0f, 1f, 0f)
        val before = Erasure.inpainter
        try {
            Erasure.inpainter = m
            val plan = Erasure.plan(ArraySource(W, H, px), 1f, listOf(RectF(60f, 62f, 200f, 86f)), 24f, W.toFloat(), H.toFloat())!!
            T.eq(0, m.calls.size, "appels du modèle (mode ${plan.mode})")
        } finally { Erasure.inpainter = before }
    }
}
