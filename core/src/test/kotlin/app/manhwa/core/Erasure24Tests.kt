package app.manhwa.core

/**
 * Effacement à pleine résolution (24/11) : banc d'essai à vérité terrain (texte connu écrit sur de vrais fonds). Aplat refusé quand une partie des lignes est sur un dessin ;
 * lueur prise pour le fond (le vrai fond passait pour un « trait fixe »).
 */
fun erasure24Tests() {
    fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun lumOf(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    /** Distance (8 voisins) au masque [m], jusqu'à [reach] ; -1 au-delà. */
    fun distTo(m: BooleanArray, w: Int, h: Int, reach: Int): IntArray {
        val d = IntArray(w * h) { if (m[it]) 0 else -1 }
        val q = IntArray(w * h); var hd = 0; var tl = 0
        for (i in 0 until w * h) if (m[i]) q[tl++] = i
        while (hd < tl) {
            val i = q[hd++]; if (d[i] >= reach) continue
            val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (d[j] < 0) { d[j] = d[i] + 1; q[tl++] = j }
            }
        }
        return d
    }

    T.test("raccord d'un aplat : lettres sur un fond blanc → pourtour conforme ; lettres posées sur une joue et recouvertes de blanc → pourtour différent dans la ligne") {
        val w = 200; val h = 60
        val line = listOf(intArrayOf(20, 20, 180, 40))
        // fond blanc, aplat blanc sur des lettres (barres)
        val px = IntArray(w * h) { rgb(255, 255, 255) }
        val mask = BooleanArray(w * h)
        for (g in 0 until 8) for (y in 20 until 40) for (x in 24 + g * 20 until 32 + g * 20) mask[y * w + x] = true
        val layer = IntArray(w * h) { if (mask[it]) rgb(255, 255, 255) else 0 }
        val ok = Erasure.seamMismatch(px, w, h, mask, layer, null, line, 4)
        T.ok(ok[2] < 0.05f && ok[3] < 0.05f, "fond blanc : écarts ${ok.toList()}")
        // même aplat blanc, mais la ligne est posée sur une joue (couleur peau) : la forme des lettres resterait en blanc sur la peau
        val skin = IntArray(w * h) { i -> if (i / w >= 14) rgb(214, 170, 150) else rgb(255, 255, 255) }
        val bad = Erasure.seamMismatch(skin, w, h, mask, layer, null, line, 4)
        T.ok(bad[2] > 0.6f && bad[3] > 0.6f, "sur la joue : écarts ${bad.toList()}")
    }

    T.test("texte libre noir cerné de blanc, à cheval sur une page blanche et un dessin → pas d'aplat blanc (lettres reconstruites d'après leur entourage)") {
        val w = 420; val h = 200
        // à gauche une joue (peau), à droite la page blanche ; la ligne de texte traverse les deux
        val px = IntArray(w * h) { i -> val x = i % w; if (x < 200) rgb(205 + (x % 7), 160 + (i / w) % 5, 140) else rgb(254, 253, 251) }
        val letters = BooleanArray(w * h)
        for (g in 0 until 14) for (y in 80 until 110) for (x in 60 + g * 22 until 68 + g * 22) letters[y * w + x] = true
        for (g in 0 until 14) for (y in 80 until 86) for (x in 60 + g * 22 until 76 + g * 22) letters[y * w + x] = true
        val d = distTo(letters, w, h, 4)
        for (i in px.indices) { if (letters[i]) px[i] = rgb(14, 14, 16) else if (d[i] in 1..3) px[i] = rgb(252, 252, 252) }
        val src = ArraySource(w, h, px)
        val line = RectF(56f, 76f, 370f, 114f)
        val p = Erasure.plan(src, 1f, listOf(line), 34f, w.toFloat(), h.toFloat(), free = true)
        T.ok(p != null, "aucun plan")
        p!!
        // sur la joue, le résultat ne doit pas garder des formes de lettres blanches : pixels des lettres et de leur contour côté peau, après effacement
        var n = 0; var white = 0
        for (y in 78 until 112) for (x in 58 until 196) {
            val i = y * w + x
            if (!(letters[i] || d[i] in 1..3)) continue
            val lx = x - p.x0; val ly = y - p.y0
            if (lx < 0 || ly < 0 || lx >= p.w || ly >= p.h) continue
            val c = p.layer[ly * p.w + lx]
            val out = if ((c ushr 24) >= 128) c else px[i]
            n++; if (lumOf(out) > 235) white++
        }
        T.ok(p.mode != Erasure.Mode.FLAT, "mode ${p.mode}")
        T.ok(n > 500 && white < n / 10, "lettres blanches restées sur la joue : $white / $n")
    }

    T.test("retouche par IA : un seul aplat autour du trou → reconstruction classique ; une joue et une page blanche autour du trou → IA (pas de mélange en taches)") {
        val w = 160; val h = 100
        val hole = TextErase.rectMask(w, h, listOf(intArrayOf(40, 30, 120, 70)), 0)
        val flat = IntArray(w * h) { rgb(201, 116, 87) }
        T.ok(AiFill.flatAround(flat, w, h, hole), "aplat de peau : devrait être uni")
        // peau en haut, page blanche sous le trou (bas de la case)
        val two = IntArray(w * h) { i -> if (i / w < 66) rgb(201, 116, 87) else rgb(252, 252, 252) }
        T.ok(!AiFill.flatAround(two, w, h, hole), "peau + blanc : ne doit pas passer pour uni")
    }

    T.test("fond : très gros lettrage noir qui remplit plus de la moitié de sa zone sur une page blanche → fond blanc (pas noir) ; plaque sombre ajustée autour d'un texte blanc → fond de la plaque") {
        val w = 320; val h = 110
        val px = IntArray(w * h) { rgb(253, 253, 252) }
        // lettres épaisses : barres de 15 px, 6 px d'écart, plus une barre haute (70 % de la zone en noir)
        for (g in 0 until 12) for (y in 24 until 86) for (x in 26 + g * 21 until 41 + g * 21) px[y * w + x] = rgb(10, 10, 12)
        for (y in 24 until 38) for (x in 26 until 290) px[y * w + x] = rgb(10, 10, 12)
        val allowed = TextErase.rectMask(w, h, listOf(intArrayOf(24, 22, 292, 88)), 3)
        val a = TextErase.analyze(px, w, h, allowed)
        T.ok(lumOf(a.bg) > 200, "fond retenu : ${"%06X".format(a.bg and 0xFFFFFF)}")
        // plaque bleu nuit exactement ajustée à la zone sur une page blanche (le cadre autour de la zone est tout blanc), texte blanc fin dessus : la plaque reste le fond
        val px2 = IntArray(w * h) { rgb(253, 253, 252) }
        for (y in 19 until 91) for (x in 21 until 295) px2[y * w + x] = rgb(30, 36, 70)
        for (g in 0 until 12) for (y in 30 until 80) for (x in 30 + g * 21 until 34 + g * 21) px2[y * w + x] = rgb(250, 250, 250)
        val b = TextErase.analyze(px2, w, h, allowed)
        T.ok(lumOf(b.bg) < 80, "fond de la plaque : ${"%06X".format(b.bg and 0xFFFFFF)}")
    }

    T.test("lueur violette dominante autour de lettres blanches sur une nuit sombre → la lueur part avec les lettres (pas de tache violette)") {
        val w = 360; val h = 260; val rnd = java.util.Random(11)
        val px = IntArray(w * h) { rgb(34 + rnd.nextInt(4), 38 + rnd.nextInt(4), 48 + rnd.nextInt(4)) }
        val letters = BooleanArray(w * h)
        // quatre lignes de lettres blanches serrées
        for (ln in 0 until 4) for (g in 0 until 9) for (y in 70 + ln * 32 until 90 + ln * 32) for (x in 100 + g * 18 until 106 + g * 18) letters[y * w + x] = true
        val d = distTo(letters, w, h, 30)
        for (i in px.indices) {
            if (letters[i]) { px[i] = rgb(255, 255, 255); continue }
            val k = d[i]; if (k < 1) continue
            // lueur violette (flou gaussien d'un tracé élargi, comme un lettrage exporté) : pleine au ras des lettres, éteinte vers 20 px
            val a = (1.6f * kotlin.math.exp(-(k * k) / (2f * 49f))).coerceIn(0f, 1f)
            val c = px[i]
            px[i] = rgb((((c shr 16) and 0xFF) * (1 - a) + 178 * a).toInt(), (((c shr 8) and 0xFF) * (1 - a) + 91 * a).toInt(), ((c and 0xFF) * (1 - a) + 249 * a).toInt())
        }
        val src = ArraySource(w, h, px)
        val lines = (0 until 4).map { ln -> RectF(98f, 68f + ln * 32, 266f, 92f + ln * 32) }
        val p = Erasure.plan(src, 1f, lines, 24f, w.toFloat(), h.toFloat(), free = true)
        T.ok(p != null, "aucun plan")
        p!!
        // lueur franche entre et autour des lettres (3 à 9 px) : après effacement, plus de violet
        var n = 0; var purple = 0
        for (i in px.indices) {
            val k = d[i]; if (k < 3 || k > 9) continue
            val x = i % w - p.x0; val y = i / w - p.y0
            if (x < 0 || y < 0 || x >= p.w || y >= p.h) { n++; purple++; continue }
            val c = p.layer[y * p.w + x]
            val out = if ((c ushr 24) >= 160) c else px[i]
            n++
            if ((out and 0xFF) - ((out shr 8) and 0xFF) > 60) purple++
        }
        T.ok(n > 1000 && purple < n / 10, "lueur restée violette : $purple / $n (mode ${p.mode})")
    }
}
