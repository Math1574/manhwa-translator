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
}
