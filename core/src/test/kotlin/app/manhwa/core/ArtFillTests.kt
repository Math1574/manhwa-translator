package app.manhwa.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Effacement d'un texte sur dessin (ArtFill) : lueur absorbée, dessin voisin préservé, raccords sans losange ni contour fantôme. */
fun artFillTests() {
    val W = 260; val H = 150
    fun gradient(): IntArray = IntArray(W * H) { i -> val x = i % W; val y = i / W; (0xFF shl 24) or ((20 + x / 4) shl 16) or ((60 + y / 2) shl 8) or (150 + x / 10) }
    /** Lettres : barres blanches de 5 px, tous les 11 px, sur [x0, x1) × [y0, y1). */
    fun letters(px: IntArray, x0: Int, x1: Int, y0: Int, y1: Int): BooleanArray {
        val m = BooleanArray(W * H)
        for (y in y0 until y1) for (x in x0 until x1) if ((x - x0) % 11 < 5) { px[y * W + x] = 0xFFFFFFFF.toInt(); m[y * W + x] = true }
        return m
    }
    /** Lueur douce autour des lettres : mélange vers le blanc, qui décroît avec la distance (rayon [reach] px). */
    fun glow(px: IntArray, letters: BooleanArray, reach: Int, strength: Float) {
        val d = IntArray(W * H) { if (letters[it]) 0 else -1 }
        val q = IntArray(W * H); var h = 0; var t = 0
        for (i in 0 until W * H) if (letters[i]) q[t++] = i
        while (h < t) {
            val i = q[h++]; val x = i % W; val y = i / W
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                val j = ny * W + nx
                if (d[j] >= 0 || d[i] >= reach) continue
                d[j] = d[i] + 1; q[t++] = j
            }
        }
        for (i in 0 until W * H) if (d[i] > 0) {
            val a = strength * exp(-d[i] / 3.5f)
            val c = px[i]
            fun mix(sh: Int) = (((c shr sh) and 0xFF) * (1 - a) + 255 * a).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
    fun blend(o: Int, c: Int, a: Int): Int {
        fun mix(sh: Int) = ((((o shr sh) and 0xFF) * (255 - a) + ((c shr sh) and 0xFF) * a) / 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
    fun apply(px: IntArray, plan: Erasure.Plan, width: Int = W): IntArray {
        val out = px.copyOf()
        for (i in plan.layer.indices) {
            val a = plan.layer[i] ushr 24; if (a == 0) continue
            val k = (plan.y0 + i / plan.w) * width + plan.x0 + i % plan.w
            out[k] = blend(out[k], plan.layer[i], a)
        }
        return out
    }

    T.test("ArtFill: texte blanc à lueur sur dégradé → fond reconstruit, ni fantôme de lueur ni mosaïque") {
        val clean = gradient(); val px = clean.copyOf()
        val lt = letters(px, 50, 210, 60, 90); glow(px, lt, 9, 0.75f)
        val core = TextErase.dilate(lt, W, H, 1)
        val limit = BooleanArray(W * H) { val x = it % W; val y = it / W; x in 20..240 && y in 30..120 }
        val r = ArtFill.reconstruct(px, W, H, core, limit, null, 14)!!
        T.ok(r.grown > 0, "la lueur doit rejoindre le masque (ajout ${r.grown})")
        val out = px.copyOf()
        for (i in 0 until W * H) if (r.alpha[i] > 0) out[i] = blend(px[i], r.fill[i], r.alpha[i])
        var worst = 0; var sum = 0L; var n = 0; var wx = 0; var wy = 0
        for (y in 45 until 105) for (x in 35 until 225) { val i = y * W + x; val e = TextErase.dist(out[i], clean[i]); if (e > worst) { worst = e; wx = x; wy = y }; sum += e; n++ }
        T.ok(worst <= 24, "écart maximal au fond d'origine $worst en ($wx,$wy) : alpha=${r.alpha[wy * W + wx]} sûr=${r.safe[wy * W + wx]} lettre=${lt[wy * W + wx]} grown=${r.grown}")
        T.ok(sum.toDouble() / n < 3.0, "écart moyen ${sum.toDouble() / n}")
        // rien n'est modifié hors de la couronne
        for (i in 0 until W * H) if (r.alpha[i] == 0) T.ok(!r.mask[i], "mask incohérent avec alpha en $i")
    }

    T.test("ArtFill: sans lueur, le masque ne grandit pas (le dessin autour du texte est conservé)") {
        val px = gradient()
        // fond granuleux (bruit déterministe ±5)
        val rnd = java.util.Random(3)
        for (i in px.indices) { val d = (rnd.nextGaussian() * 3).toInt().coerceIn(-6, 6); val c = px[i]; px[i] = (0xFF shl 24) or (((c shr 16 and 0xFF) + d).coerceIn(0, 255) shl 16) or (((c shr 8 and 0xFF) + d).coerceIn(0, 255) shl 8) or ((c and 0xFF) + d).coerceIn(0, 255) }
        val lt = letters(px, 50, 210, 60, 90)
        val core = TextErase.dilate(lt, W, H, 2)
        val limit = BooleanArray(W * H) { true }
        val r = ArtFill.reconstruct(px, W, H, core, limit, null, 18)!!
        val coreN = core.count { it }
        T.ok(r.grown < 0.08 * coreN, "lueur ajoutée sans lueur : ${r.grown} pixels pour un masque de $coreN")
    }

    T.test("ArtFill: un trait franc du dessin, à quelques pixels du texte, n'est pas effacé au-delà de la marge") {
        val px = gradient()
        // trait sombre horizontal à 16 px sous les lettres
        for (y in 106 until 109) for (x in 0 until W) px[y * W + x] = 0xFF101010.toInt()
        val lt = letters(px, 50, 210, 60, 90)
        val core = TextErase.dilate(lt, W, H, 2)
        val limit = BooleanArray(W * H) { true }
        val r = ArtFill.reconstruct(px, W, H, core, limit, null, 20)!!
        for (y in 106 until 109) for (x in 60 until 200) T.eq(0, r.alpha[y * W + x], "le trait du dessin en ($x,$y) ne doit pas être touché")
    }

    T.test("ArtFill: opacité pleine sur le masque sûr, dégradé sur la couronne extérieure, jamais sur les lettres") {
        val px = gradient(); val lt = letters(px, 60, 200, 60, 90)
        val core = TextErase.dilate(lt, W, H, 1)
        val r = ArtFill.reconstruct(px, W, H, core, BooleanArray(W * H) { true }, null, 10)!!
        for (i in 0 until W * H) {
            if (lt[i]) T.eq(255, r.alpha[i], "lettre non effacée à 100 % en $i")
            if (r.safe[i]) T.eq(255, r.alpha[i], "masque sûr en $i")
            else if (r.alpha[i] > 0) T.ok(r.alpha[i] < 255 && r.mask[i], "couronne en $i")
        }
    }

    T.test("ArtFill: un texte voisin n'est jamais utilisé comme référence ni effacé") {
        val px = gradient(); val lt = letters(px, 50, 140, 60, 90)
        // texte d'un autre bloc, collé à droite (blanc franc)
        val other = BooleanArray(W * H); for (y in 60 until 90) for (x in 150 until 230) if ((x - 150) % 9 < 5) { px[y * W + x] = 0xFFFFFFFF.toInt() }
        for (y in 55 until 95) for (x in 148 until 232) other[y * W + x] = true
        val core = TextErase.dilate(lt, W, H, 2)
        val limit = BooleanArray(W * H) { !other[it] }
        val r = ArtFill.reconstruct(px, W, H, core, limit, other, 14)!!
        for (i in 0 until W * H) if (other[i]) T.eq(0, r.alpha[i], "le texte voisin en $i ne doit pas être touché")
        // la reconstruction ne contient pas de blanc venu du voisin
        var bright = 0; for (i in 0 until W * H) if (r.safe[i] && (r.fill[i] shr 16 and 0xFF) > 150) bright++
        T.eq(0, bright, "blanc du texte voisin dans le remplissage")
    }

    T.test("plan ART: lueur autour d'un texte blanc sur dégradé → rien ne reste visible après effacement") {
        val clean = gradient(); val px = clean.copyOf()
        val lt = letters(px, 50, 210, 60, 90); glow(px, lt, 9, 0.75f)
        val plan = Erasure.plan(ArraySource(W, H, px), 1f, listOf(RectF(50f, 60f, 210f, 90f)), 28f, W.toFloat(), H.toFloat())!!
        val out = apply(px, plan)
        var worst = 0
        for (y in 50 until 100) for (x in 40 until 220) worst = max(worst, TextErase.dist(out[y * W + x], clean[y * W + x]))
        T.ok(worst <= 30, "mode ${plan.mode} : écart maximal au fond d'origine $worst")
    }
    T.test("plan ART: un contour de bulle frôlé par les coins de la zone de texte reste intact") {
        // bulle blanche sur page blanche (contour noir de 4 px, non détectée comme bulle) ; boîtes de lecture élargies : leurs coins bas passent sur le contour
        val w = 320; val h = 220
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        val outline = BooleanArray(w * h)
        val cx = 160.0; val cy = 110.0; val rx = 112.0; val ry = 100.0
        for (y in 0 until h) for (x in 0 until w) {
            val e = Math.sqrt(((x - cx) / rx) * ((x - cx) / rx) + ((y - cy) / ry) * ((y - cy) / ry))
            if (abs(e - 1.0) * 106.0 < 2.0) { px[y * w + x] = 0xFF101010.toInt(); outline[y * w + x] = true }
        }
        val lines = listOf(RectF(110f, 50f, 210f, 78f), RectF(70f, 80f, 250f, 108f), RectF(70f, 110f, 250f, 138f), RectF(90f, 140f, 230f, 168f), RectF(102f, 170f, 218f, 198f))
        for (l in lines) for (y in (l.top + 4).toInt() until (l.bottom - 4).toInt()) for (x in l.left.toInt() until l.right.toInt()) if ((x - l.left.toInt()) % 11 < 5) px[y * w + x] = 0xFF1A1A1A.toInt()
        val padded = lines.map { RectF(it.left - 8f, it.top - 6f, it.right + 8f, it.bottom + 8f) }
        fun damaged(protect: Boolean): Pair<Int, String> {
            Erasure.protectStructure = protect
            try {
                val plan = Erasure.plan(ArraySource(w, h, px), 1f, padded, 28f, w.toFloat(), h.toFloat())!!
                val out = apply(px, plan, w)
                var hurt = 0
                for (i in 0 until w * h) if (outline[i] && TextErase.dist(out[i], px[i]) > 40) hurt++
                return hurt to plan.mode.toString()
            } finally { Erasure.protectStructure = true }
        }
        val (bare, modeBare) = damaged(false)
        T.ok(bare >= 10, "le scénario doit abîmer le contour sans protection (mode $modeBare : $bare pixels) : le test ne prouverait rien")
        val (kept, mode) = damaged(true)
        T.ok(kept <= 4, "mode $mode : $kept pixels du contour de la bulle altérés (sans protection : $bare)")
    }

    // --- hachures et traits fins : ni effacés avec le texte, ni références du remplissage ---
    fun bar(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, c: Int = 0xFF101010.toInt()) { for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = c }

    T.test("thinDarkOnLight: un trait fin sombre sur fond clair en est un, une bordure épaisse, une grande masse sombre ou un trait clair sur fond sombre n'en sont pas") {
        val w = 200; val h = 120
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        bar(px, w, 20, 10, 22, 50)            // trait de 2 px
        bar(px, w, 40, 10, 49, 50)            // bordure de 9 px
        bar(px, w, 100, 60, 190, 115)         // grande masse sombre
        bar(px, w, 120, 70, 122, 100, 0xFFFFFFFF.toInt())   // trait clair sur la masse sombre
        val t = Erasure.thinDarkOnLight(px, w, h, 2)
        T.ok(t[30 * w + 20] && t[30 * w + 21], "le trait de 2 px doit être repéré")
        T.ok(!t[30 * w + 44], "une bordure de 9 px n'est pas un trait fin")
        T.ok(!t[90 * w + 150], "une grande masse sombre n'est pas un trait fin")
        T.ok(!t[85 * w + 120], "un trait clair sur fond sombre n'est pas un trait sombre sur fond clair")
        T.ok(!t[5 * w + 5], "le fond blanc n'est pas un trait")
    }

    T.test("ArtFill: les hachures qui touchent le trou ne tachent pas le remplissage de gris") {
        val w = 200; val h = 100
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        // hachures sombres de 1 px, tous les 6 px, à droite du texte et jusqu'au bord du trou
        for (x in 118 until 190 step 6) bar(px, w, x, 0, x + 1, h)
        val lt = BooleanArray(w * h)
        for (y in 40 until 62) for (x in 50 until 112) if ((x - 50) % 11 < 6) { px[y * w + x] = 0xFF101010.toInt(); lt[y * w + x] = true }
        val core = TextErase.dilate(lt, w, h, 2)
        val limit = BooleanArray(w * h) { val x = it % w; val y = it / w; x in 30..150 && y in 20..85 }
        val thin = Erasure.thinDarkOnLight(px, w, h, 2)
        fun brightness(r: ArtFill.Result): Int { var s = 0; var n = 0; for (i in 0 until w * h) if (r.safe[i] && (i % w) >= 100) { s += (r.fill[i] shr 8) and 0xFF; n++ }; return if (n == 0) 255 else s / n }
        val without = ArtFill.reconstruct(px, w, h, core, limit, null, 10)!!
        val with = ArtFill.reconstruct(px, w, h, core, limit, null, 10, hatch = thin)!!
        T.ok(brightness(with) >= 250, "avec les hachures écartées le remplissage reste blanc (clarté moyenne ${brightness(with)})")
        T.ok(brightness(without) < brightness(with), "sans cette précaution les hachures grisent le remplissage (${brightness(without)} contre ${brightness(with)}) : le test ne prouverait rien")
    }

    T.test("plan: les pointes d'un contour étoile, dans les coins de la zone détectée, ne sont pas effacées") {
        val w = 360; val h = 240
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        val lines = listOf(RectF(110f, 92f, 250f, 120f), RectF(110f, 126f, 250f, 154f))
        for (l in lines) for (y in (l.top + 4).toInt() until (l.bottom - 4).toInt()) for (x in l.left.toInt() until l.right.toInt()) if ((x - l.left.toInt()) % 11 < 6) px[y * w + x] = 0xFF1A1A1A.toInt()
        val zone = RectF(60f, 50f, 300f, 196f)
        // pointes : petits traits fins de 1 px, isolés, dans les quatre coins de la zone (loin des lignes lues)
        val spikes = ArrayList<Int>()
        for ((cx, cy, dir) in listOf(Triple(66, 56, 1), Triple(294, 56, -1), Triple(66, 190, 1), Triple(294, 190, -1))) for (k in 0 until 3) for (t in 0 until 14) {
            val x = cx + dir * (t / 2) + k * 5 * dir; val y = (if (cy < 120) cy + t else cy - t)
            px[y * w + x] = 0xFF101010.toInt(); spikes.add(y * w + x)
        }
        val plan = Erasure.plan(ArraySource(w, h, px), 1f, lines, 28f, w.toFloat(), h.toFloat(), extra = listOf(zone))!!
        val out = apply(px, plan, w)
        var hurt = 0
        for (k in spikes) if (TextErase.dist(out[k], px[k]) > 40) hurt++
        T.ok(hurt <= 2, "mode ${plan.mode} : $hurt pointes sur ${spikes.size} effacées")
        // et les lettres, elles, sont bien effacées
        var left = 0
        for (l in lines) for (y in l.top.toInt() until l.bottom.toInt()) for (x in l.left.toInt() until l.right.toInt()) if (px[y * w + x] == 0xFF1A1A1A.toInt() && TextErase.dist(out[y * w + x], 0xFFFFFFFF.toInt()) > 40) left++
        T.eq(0, left, "pixels de lettres restants")
    }

    T.test("withoutThinOutside: le peigne soudé aux lettres sort des lignes lues et perd ses traits fins, la lettre épaisse qui déborde un peu de sa boîte reste entière") {
        val w = 300; val h = 120
        val src = BooleanArray(w * h)
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int) { for (y in y0 until y1) for (x in x0 until x1) src[y * w + x] = true }
        val inner = BooleanArray(w * h); for (y in 40 until 80) for (x in 20 until 140) inner[y * w + x] = true
        rect(100, 44, 106, 76); rect(106, 44, 130, 50)         // lettre épaisse (6 px) dans la ligne
        rect(106, 76, 250, 78)                                  // contour de bulle fin (2 px) qui la prolonge, hors de la ligne
        for (k in 0 until 6) rect(160 + 14 * k, 52, 163 + 14 * k, 76)   // dents du peigne (3 px de large) hors de la ligne
        rect(30, 44, 36, 86)                                    // une autre lettre épaisse qui déborde de 6 px sous sa boîte, isolée
        val out = TextErase.withoutThinOutside(src, w, h, inner)
        T.ok(out[60 * w + 102] && out[46 * w + 120], "la lettre épaisse reste")
        T.ok(!out[77 * w + 200], "le contour fin hors de la ligne est retiré")
        for (k in 0 until 6) T.ok(!out[60 * w + 161 + 14 * k], "la dent $k du peigne est retirée")
        T.ok(out[84 * w + 32], "la lettre qui déborde un peu de sa boîte (6 px épais) est conservée")
        // un trait fin collé à la ligne (moins de 3 px) n'est jamais retiré : accent, bout de lettre qui déborde à peine
        val src2 = BooleanArray(w * h); for (y in 76 until 83) for (x in 50 until 52) src2[y * w + x] = true
        val out2 = TextErase.withoutThinOutside(src2, w, h, inner)
        T.ok(out2[81 * w + 50], "un trait fin collé à la ligne reste")
        // et rien n'est ajouté
        T.ok(out.indices.none { out[it] && !src[it] }, "aucun pixel ajouté")
    }

    T.test("artMask : le peigne de traits fins soudé au bout de la ligne par un contour n'est pas pris pour du texte, les lettres le sont") {
        val w = 380; val h = 220
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        val lines = listOf(RectF(60f, 80f, 220f, 108f), RectF(60f, 114f, 220f, 142f))
        val letters = ArrayList<Int>()
        for (l in lines) for (y in (l.top + 4).toInt() until (l.bottom - 4).toInt()) for (x in l.left.toInt() until l.right.toInt()) if ((x - l.left.toInt()) % 11 < 6) { px[y * w + x] = 0xFF1A1A1A.toInt(); letters.add(y * w + x) }
        // contour de bulle fin (2 px) qui part de la dernière lettre, dents d'un peigne (3 px de large, 22 px de haut) accrochées à lui, hors des lignes lues
        val teeth = ArrayList<Int>()
        for (y in 126 until 128) for (x in 214 until 360) px[y * w + x] = 0xFF101010.toInt()
        for (k in 0 until 9) for (y in 104 until 126) for (x in 236 + 13 * k until 239 + 13 * k) { px[y * w + x] = 0xFF101010.toInt(); teeth.add(y * w + x) }
        val zone = RectF(40f, 60f, 360f, 160f)
        fun rects(l: List<RectF>) = l.map { intArrayOf(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        val lr = rects(lines)
        val zoneArt = TextErase.rectMask(w, h, rects(lines + zone), 7)
        val tight = TextErase.rectMask(w, h, rects(lines + zone), 3)
        val near = TextErase.rectMask(w, h, lr, 9)
        val inner = TextErase.rectMask(w, h, lr, 0)
        val bare = TextErase.artMask(px, w, h, zoneArt, 0xFF1A1A1A.toInt(), 2, Int.MAX_VALUE, tight, 4, lr, null, near, null)
        T.ok(teeth.count { bare[it] } >= teeth.size / 2, "le scénario doit prendre le peigne pour du texte sans le filtre (${teeth.count { bare[it] }}/${teeth.size}) : le test ne prouverait rien")
        val m = TextErase.artMask(px, w, h, zoneArt, 0xFF1A1A1A.toInt(), 2, Int.MAX_VALUE, tight, 4, lr, null, near, inner)
        T.ok(teeth.count { m[it] } <= teeth.size / 20, "dents du peigne dans le masque : ${teeth.count { m[it] }}/${teeth.size}")
        T.eq(0, letters.count { !m[it] }, "pixels de lettres hors du masque")
    }
}
