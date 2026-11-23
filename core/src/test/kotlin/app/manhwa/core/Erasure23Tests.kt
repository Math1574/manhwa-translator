package app.manhwa.core

/** Journal du 23/11 : couleur des lettres (lueur, contour), lueur et décor, formes pleines, encre des seules lettres. */
fun erasure23Tests() {
    fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun lumOf(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    /** Distance (8 voisins) au masque [m], jusqu'à [reach]. */
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

    T.test("couleur des lettres : lettres blanches dans une lueur rose sur un mur clair (« PARADISE ») → blanches") {
        val w = 220; val h = 90; val rnd = java.util.Random(5)
        val px = IntArray(w * h) { rgb(232 + rnd.nextInt(5), 206 + rnd.nextInt(5), 186 + rnd.nextInt(5)) }
        val letters = BooleanArray(w * h)
        // lettres condensées : barres de 9 px, 9 px d'écart (la lueur remplit l'espace entre les lettres)
        for (g in 0 until 9) for (y in 25 until 65) for (x in 25 + g * 18 until 34 + g * 18) letters[y * w + x] = true
        val d = distTo(letters, w, h, 14)
        for (i in px.indices) {
            if (letters[i]) { px[i] = rgb(255, 254, 254); continue }
            val k = d[i]; if (k < 1) continue
            // lueur rose foncée au ras des lettres, qui s'éclaircit vers le mur
            val a = (1f - (k - 1) / 13f).coerceIn(0f, 1f)
            val c = px[i]
            px[i] = rgb((((c shr 16) and 0xFF) * (1 - a) + 214 * a).toInt(), (((c shr 8) and 0xFF) * (1 - a) + 110 * a).toInt(), ((c and 0xFF) * (1 - a) + 122 * a).toInt())
        }
        val box = TextErase.rectMask(w, h, listOf(intArrayOf(23, 23, 188, 67)), 0)
        val zone = TextErase.rectMask(w, h, listOf(intArrayOf(23, 23, 188, 67)), 17)
        val c = TextErase.extremeTextColor(px, zone, box, w, h, 44)
        T.ok(lumOf(c) > 200, "couleur retenue : ${"%06X".format(c and 0xFFFFFF)}")
    }

    T.test("couleur des lettres : texte noir cerné d'un épais contour blanc sur un treillis gris (« Oho, of course~ ») → noir") {
        val w = 220; val h = 90
        // treillis : cases claires et cadres plus sombres
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x % 22 < 4 || y % 16 < 3) rgb(96, 104, 108) else rgb(150, 158, 160) }
        val letters = BooleanArray(w * h)
        for (g in 0 until 8) for (y in 28 until 62) for (x in 26 + g * 20 until 32 + g * 20) letters[y * w + x] = true
        for (y in 28 until 34) for (x in 26 until 180) if ((x - 26) % 40 < 26) letters[y * w + x] = true
        val d = distTo(letters, w, h, 6)
        for (i in px.indices) { if (letters[i]) px[i] = rgb(12, 12, 14) else if (d[i] in 1..5) px[i] = rgb(250, 250, 250) }
        val box = TextErase.rectMask(w, h, listOf(intArrayOf(24, 26, 184, 64)), 0)
        val zone = TextErase.rectMask(w, h, listOf(intArrayOf(24, 26, 184, 64)), 12)
        val c = TextErase.extremeTextColor(px, zone, box, w, h, 38)
        T.ok(lumOf(c) < 60, "couleur retenue : ${"%06X".format(c and 0xFFFFFF)}")
    }

    T.test("encre d'un texte noir : les carreaux sombres d'une fenêtre sous le texte (aussi sombres que lui) n'en sont pas") {
        val w = 300; val h = 160
        val px = IntArray(w * h) { rgb(140, 150, 152) }
        val letters = BooleanArray(w * h)
        // texte : traits de 4 px (des « lettres » de 4 × 26), une dizaine
        for (g in 0 until 12) for (y in 30 until 56) for (x in 40 + g * 18 until 44 + g * 18) letters[y * w + x] = true
        for (g in 0 until 12) for (y in 30 until 34) for (x in 40 + g * 18 until 52 + g * 18) letters[y * w + x] = true
        for (i in px.indices) if (letters[i]) px[i] = rgb(24, 24, 22)
        // deux carreaux brun sombre juste sous le texte, dans la zone
        val panes = BooleanArray(w * h)
        for (y in 66 until 92) for (x in 50 until 120) panes[y * w + x] = true
        for (y in 66 until 92) for (x in 140 until 210) panes[y * w + x] = true
        for (i in px.indices) if (panes[i]) px[i] = rgb(70, 60, 56)
        val zone = TextErase.rectMask(w, h, listOf(intArrayOf(30, 24, 270, 100)), 6)
        val m = TextErase.artMaskInfo(px, w, h, zone, rgb(24, 24, 22), 0).mask
        var inPanes = 0; var inLetters = 0
        for (i in 0 until w * h) { if (m[i] && panes[i]) inPanes++; if (m[i] && letters[i]) inLetters++ }
        T.ok(inLetters >= 0.9 * letters.count { it }, "lettres dans le masque : $inLetters / ${letters.count { it }}")
        T.ok(inPanes < 0.05 * panes.count { it }, "carreaux dans le masque : $inPanes / ${panes.count { it }}")
    }

    T.test("ArtFill : décor (treillis) collé aux lettres sans lueur → le masque ne s'étend pas dans le treillis ; vraie lueur dégradée → toute sa traîne est effacée") {
        val w = 240; val h = 140
        // treillis contrasté
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x % 12 < 3 || y % 10 < 2) rgb(90, 96, 100) else rgb(132, 140, 142) }
        val lt = BooleanArray(w * h)
        for (g in 0 until 10) for (y in 55 until 85) for (x in 40 + g * 16 until 46 + g * 16) lt[y * w + x] = true
        for (i in px.indices) if (lt[i]) px[i] = rgb(10, 10, 12)
        val core = TextErase.dilate(lt, w, h, 1)
        val r = ArtFill.reconstruct(px, w, h, core, BooleanArray(w * h) { true }, null, 20)!!
        T.ok(r.thick <= 2, "épaisseur de lueur sur un treillis : ${r.thick}")
        // lueur rouge diffuse (décroissance régulière sur 16 px) autour de lettres blanches, sur un fond sombre
        val bg = IntArray(w * h) { rgb(30, 26, 40) }
        val lt2 = BooleanArray(w * h)
        for (g in 0 until 10) for (y in 55 until 85) for (x in 40 + g * 16 until 46 + g * 16) lt2[y * w + x] = true
        val d = distTo(lt2, w, h, 18)
        val px2 = bg.copyOf()
        for (i in px2.indices) {
            if (lt2[i]) { px2[i] = rgb(250, 250, 250); continue }
            val k = d[i]; if (k < 1) continue
            val a = (1f - (k - 1) / 16f).coerceIn(0f, 1f) * 0.85f
            px2[i] = rgb((30 * (1 - a) + 200 * a).toInt(), (26 * (1 - a) + 40 * a).toInt(), (40 * (1 - a) + 50 * a).toInt())
        }
        val r2 = ArtFill.reconstruct(px2, w, h, TextErase.dilate(lt2, w, h, 1), BooleanArray(w * h) { true }, null, 24)!!
        T.ok(r2.thick >= 10, "épaisseur de la lueur dégradée : ${r2.thick}")
        // la traîne pâle (au-delà de 10 px) est dans le masque
        var tail = 0; var tailIn = 0
        for (i in 0 until w * h) if (d[i] in 11..13 && (i / w) in 60..80) { tail++; if (r2.mask[i]) tailIn++ }
        T.ok(tailIn >= 0.8 * tail, "traîne de la lueur effacée : $tailIn / $tail")
    }

    T.test("angle et police : encre des seules lettres — une ligne qui traverse la région (bord de case) en est retirée, l'encre entière reste si presque tout la touche") {
        val w = 300; val h = 120
        val ink = BooleanArray(w * h)
        for (g in 0 until 14) for (y in 40 until 70) for (x in 20 + g * 18 until 26 + g * 18) ink[y * w + x] = true
        for (y in 80 until 85) for (x in 0 until w) ink[y * w + x] = true          // bord de case : d'un bout à l'autre
        val only = BlockLayout.lettersOnly(ink, w, h)
        T.ok(only.count { it } == 14 * 30 * 6, "lettres seules : ${only.count { it }}")
        T.ok((0 until w).none { only[82 * w + it] }, "la ligne est retirée")
        // une encre qui touche presque partout le bord : gardée telle quelle
        val edge = BooleanArray(w * h) { val y = it / w; y in 0 until 60 }
        T.ok(BlockLayout.lettersOnly(edge, w, h).contentEquals(edge), "encre gardée")
    }
}
