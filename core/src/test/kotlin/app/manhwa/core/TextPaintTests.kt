package app.manhwa.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Peinture du texte traduit (couleur, contour) et rotation du texte d'origine. */
fun textPaintTests() {
    fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b
    fun hue(c: Int): Float {
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        if (d < 1e-6f) return 0f
        val h = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
        return if (h < 0f) h + 360f else h
    }
    fun hueGap(a: Int, b: Int): Float { val d = abs(hue(a) - hue(b)); return minOf(d, 360f - d) }

    T.test("peinture: la couleur du corps des traits ignore les extrêmes et le bord des lettres") {
        // 40 pixels de corps (120,40,150), 20 de bord mélangé au fond blanc, 4 reflets très clairs : texte sombre → corps
        val cols = ArrayList<Int>()
        repeat(40) { cols.add(rgb(120, 40, 150)) }
        repeat(20) { cols.add(rgb(230, 200, 240)) }
        repeat(4) { cols.add(rgb(255, 255, 255)) }
        val t = TextPaint.typical(cols.toIntArray(), cols.size, true)
        T.ok(abs(((t shr 16) and 0xFF) - 120) <= 3 && abs(((t shr 8) and 0xFF) - 40) <= 3 && abs((t and 0xFF) - 150) <= 3, "corps retrouvé : ${"%06X".format(t)}")
    }
    T.test("peinture: gris = neutre, couleur franche = chromatique") {
        T.ok(TextPaint.isNeutral(rgb(0, 0, 0)) && TextPaint.isNeutral(rgb(250, 250, 250)) && TextPaint.isNeutral(rgb(30, 30, 50)), "noir, blanc, gris bleuté")
        T.ok(!TextPaint.isNeutral(rgb(120, 40, 150)) && !TextPaint.isNeutral(rgb(255, 140, 0)), "violet, orange")
    }
    T.test("contraste: un orange ou un jaune sur fond blanc garde sa teinte et atteint le contraste, il ne devient pas noir") {
        for (c in listOf(rgb(255, 140, 0), rgb(255, 220, 0), rgb(0, 200, 120), rgb(0, 180, 255))) {
            val r = TextPaint.withContrast(c, 0xFFFFFF, TextPaint.MIN_CONTRAST)
            T.ok(abs(ColorMath.luminance(r) - 1f) >= TextPaint.MIN_CONTRAST - 0.01f, "contraste atteint pour ${"%06X".format(c)} → ${"%06X".format(r)}")
            T.ok(!TextPaint.isNeutral(r), "reste coloré : ${"%06X".format(r)}")
            T.ok(hueGap(c, r) < 14f, "teinte conservée : ${hue(c)} → ${hue(r)}")
        }
    }
    T.test("contraste: sur fond sombre la couleur s'éclaircit ; les gris passent au noir ou au blanc comme avant ; un écart suffisant ne change rien") {
        val r = TextPaint.withContrast(rgb(30, 60, 160), rgb(10, 20, 50), TextPaint.MIN_CONTRAST)
        T.ok(ColorMath.luminance(r) - ColorMath.luminance(rgb(10, 20, 50)) >= TextPaint.MIN_CONTRAST - 0.01f && hueGap(rgb(30, 60, 160), r) < 14f, "éclairci en gardant la teinte : ${"%06X".format(r)}")
        T.eq(0x000000, TextPaint.withContrast(rgb(230, 230, 230), 0xFFFFFF, TextPaint.MIN_CONTRAST), "gris clair sur blanc → noir")
        T.eq(0xFFFFFF, TextPaint.withContrast(rgb(40, 40, 40), rgb(20, 20, 20), TextPaint.MIN_CONTRAST), "gris sombre sur sombre → blanc")
        T.eq(rgb(200, 0, 0), TextPaint.withContrast(rgb(200, 0, 0), 0xFFFFFF, TextPaint.MIN_CONTRAST), "rouge déjà lisible : inchangé")
    }

    // image synthétique : fond blanc, une ligne de texte (bande 80 × 24) d'une couleur donnée
    fun band(w: Int, h: Int, c: Int, x0: Int = 10, y0: Int = 10, bw: Int = 80, bh: Int = 24): Triple<IntArray, BooleanArray, List<IntArray>> {
        val px = IntArray(w * h) { 0xFFFFFF }; val mask = BooleanArray(w * h)
        for (y in y0 until y0 + bh) for (x in x0 until x0 + bw) { px[y * w + x] = c; mask[y * w + x] = true }
        return Triple(px, mask, listOf(intArrayOf(x0 - 2, y0 - 2, x0 + bw + 2, y0 + bh + 2)))
    }
    T.test("mesure: la couleur d'une ligne est retrouvée") {
        val (px, mask, boxes) = band(100, 50, rgb(120, 40, 150))
        val l = TextPaint.measure(px, 100, 50, mask, boxes, true)[0]!!
        T.ok(!TextPaint.isNeutral(l) && abs(((l shr 16) and 0xFF) - 120) <= 3, "couleur : ${"%06X".format(l)}")
    }
    T.test("mesure: trop peu d'encre → pas de couleur") {
        val px = IntArray(100 * 50) { 0xFFFFFF }; val mask = BooleanArray(100 * 50)
        for (i in 0 until 20) mask[i + 500] = true
        T.ok(TextPaint.measure(px, 100, 50, mask, listOf(intArrayOf(0, 0, 100, 50)), true)[0] == null, "20 pixels : pas de mesure")
    }

    // décisions
    T.test("décision: texte noir sur fond blanc inchangé (noir pur)") {
        val s = TextPaint.decide(Erasure.Mode.FLAT, 0xFFFFFF, 0xFFFFFF, 0x000000, false, 0, 0f, 30f, listOf(0x0A0A0A))
        T.eq(0x000000, s.fill, "noir pur gardé")
        T.ok(s.outline == 0, "pas de contour")
    }
    T.test("décision: un lettrage coloré garde sa couleur, pas le noir imposé par l'ancien seuil de contraste") {
        // orange (255,140,0) sur blanc : écart de luminance 0,37 < 0,45, l'ancien seuil le rendait noir
        val s = TextPaint.decide(Erasure.Mode.FLAT, 0xFFFFFF, 0xFFFFFF, rgb(255, 140, 0), false, 0, 0f, 30f, emptyList())
        T.ok(!TextPaint.isNeutral(s.fill) && hueGap(rgb(255, 140, 0), s.fill) < 14f, "orange conservé : ${"%06X".format(s.fill)}")
    }
    T.test("décision: lettrage violet dégradé vers le noir : le remplissage est la couleur du corps des traits, plus vive que l'extrême sombre") {
        val s = TextPaint.decide(Erasure.Mode.FLAT, 0xFFFFFF, 0xFFFFFF, rgb(37, 12, 40), false, 0, 0f, 30f, listOf(rgb(95, 30, 115), rgb(80, 25, 100)))
        T.ok(ColorMath.luminance(s.fill) > ColorMath.luminance(rgb(37, 12, 40)) + 0.05f, "remplissage plus vif que l'extrême : ${"%06X".format(s.fill)}")
    }
    T.test("décision: sur dessin, un lettrage noir à halo gris clair sur fond sombre reste noir, sans inversion de polarité, avec le contour blanc habituel") {
        // cas réels vérifiés à fort grossissement : lettres noires cernées de blanc sur un fond presque noir (le halo mesuré est un gris moyen)
        val s = TextPaint.decide(Erasure.Mode.ART, rgb(20, 22, 26), rgb(20, 22, 26), rgb(10, 7, 7), true, rgb(0x99, 0x96, 0x96), 6f, 60f, emptyList())
        T.eq(rgb(10, 7, 7), s.fill, "remplissage noir conservé")
        T.eq(0, s.outline, "contour : blanc pur par défaut")
        T.eq(0f, s.outlineRel, "épaisseur habituelle")
    }
    T.test("décision: sur dessin, un contour coloré mesuré est repris avec son épaisseur (bornée)") {
        // texte blanc cassé cerné d'un contour rouge sombre de 4 px sur des lettres de 40 px
        val s = TextPaint.decide(Erasure.Mode.ART, rgb(120, 130, 140), rgb(120, 130, 140), rgb(235, 235, 235), true, rgb(64, 0, 0), 4f, 40f, emptyList())
        T.eq(rgb(235, 235, 235), s.fill, "remplissage inchangé")
        T.eq(rgb(64, 0, 0), s.outline, "contour mesuré")
        T.ok(s.outlineRel in 0.12f..0.22f, "épaisseur : ${s.outlineRel}")
    }
    T.test("décision: sur dessin, pas de contour mesuré quand le halo ressemble au remplissage ou n'existe pas") {
        val a = TextPaint.decide(Erasure.Mode.ART, rgb(120, 130, 140), rgb(120, 130, 140), rgb(235, 235, 235), true, rgb(225, 225, 225), 4f, 40f, emptyList())
        T.eq(0, a.outline, "halo semblable : contour déduit")
        val b = TextPaint.decide(Erasure.Mode.ART, rgb(120, 130, 140), rgb(120, 130, 140), rgb(235, 235, 235), true, 0, 0f, 40f, emptyList())
        T.eq(0, b.outline, "pas de halo")
    }
}

/** Rotation du texte d'origine mesurée sur son encre. */
fun textTiltTests() {
    // texte synthétique : lignes de « lettres » (rectangles) le long d'une ligne de base tournée de [deg]
    class Synth(val w: Int, val h: Int, val ink: BooleanArray, val boxes: List<IntArray>)
    fun synth(deg: Float, lines: Int, letters: Int, lw: Int = 14, lh: Int = 24, gap: Int = 7, pitch: Int = 52): Synth {
        val w = 700; val h = 500
        val ink = BooleanArray(w * h)
        val a = deg * PI.toFloat() / 180f; val ca = cos(a); val sa = sin(a)
        val boxes = ArrayList<IntArray>()
        val cx0 = w / 2f; val cy0 = h / 2f
        for (li in 0 until lines) {
            var minX = w; var minY = h; var maxX = 0; var maxY = 0
            val total = letters * (lw + gap) - gap
            val vy = (li - (lines - 1) / 2f) * pitch
            for (k in 0 until letters) {
                val u0 = -total / 2f + k * (lw + gap)
                for (du in 0 until lw) for (dv in -lh / 2 until lh / 2) {
                    val u = u0 + du; val v = vy + dv
                    val x = (cx0 + u * ca - v * sa).roundToInt(); val y = (cy0 + u * sa + v * ca).roundToInt()
                    if (x in 0 until w && y in 0 until h) { ink[y * w + x] = true; if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
                }
            }
            boxes.add(intArrayOf(minX - 2, minY - 2, maxX + 3, maxY + 3))
        }
        return Synth(w, h, ink, boxes)
    }
    T.test("rotation: un texte penché de +6° (descend vers la droite) est mesuré à ±0,7°") {
        val s = synth(6f, 2, 14)
        val r = TextTilt.estimate(s.ink, s.w, s.h, s.boxes)
        T.ok(r != null && abs(r.degrees - 6f) <= 0.7f, "angle : ${r?.degrees}")
    }
    T.test("rotation: un texte penché de −9° (monte vers la droite), une seule ligne") {
        val s = synth(-9f, 1, 16)
        val r = TextTilt.estimate(s.ink, s.w, s.h, s.boxes)
        T.ok(r != null && abs(r.degrees + 9f) <= 0.8f, "angle : ${r?.degrees}")
    }
    T.test("rotation: un texte droit n'est pas incliné") {
        val s = synth(0f, 3, 14)
        T.ok(TextTilt.estimate(s.ink, s.w, s.h, s.boxes) == null, "droit : aucune rotation")
        val s2 = synth(1.5f, 2, 14)
        T.ok(TextTilt.estimate(s2.ink, s2.w, s2.h, s2.boxes) == null, "1,5° : sous le seuil de rotation")
    }
    T.test("rotation: un mot court ou un bloc compact n'a pas d'angle mesurable") {
        val s = synth(8f, 1, 3)
        T.ok(TextTilt.estimate(s.ink, s.w, s.h, s.boxes) == null, "trois lettres : pas de mesure")
        T.ok(TextTilt.estimate(BooleanArray(100 * 100), 100, 100, listOf(intArrayOf(0, 0, 100, 100))) == null, "aucune encre")
        T.ok(TextTilt.estimate(BooleanArray(100), 10, 10, emptyList()) == null, "aucune zone")
    }
    T.test("rotation: seule l'encre des zones de lignes compte") {
        val s = synth(5f, 2, 14)
        // un grand trait horizontal parasite hors des zones ne change rien
        for (x in 20 until 680) { s.ink[10 * s.w + x] = true; s.ink[11 * s.w + x] = true; s.ink[12 * s.w + x] = true }
        val r = TextTilt.estimate(s.ink, s.w, s.h, s.boxes)
        T.ok(r != null && abs(r.degrees - 5f) <= 0.8f, "angle malgré le trait : ${r?.degrees}")
    }
}

/** Mise en page d'un bloc de texte penché : rectangles tournés autour du centre, rotation conservée par l'agrandissement et le décalage. */
fun tiltedResultTests() {
    fun result(tilt: Float) = BlockLayout.Result(ShapeFit(30f, 36f, listOf(PlacedLine("PREMIÈRE LIGNE", 300f, 100f, 200f), PlacedLine("SECONDE", 300f, 136f, 120f)), true), false, true, 30f, 1.2f, "", tilt)
    T.test("texte penché: le rectangle englobant grandit, son centre reste celui du texte droit") {
        val flat = result(0f).placed(0f); val tilted = result(10f).placed(0f)
        T.ok(tilted.width > flat.width && tilted.height > flat.height, "englobant plus grand : ${tilted.width}×${tilted.height} contre ${flat.width}×${flat.height}")
        T.ok(abs(tilted.centerX - flat.centerX) < 0.01f && abs(tilted.centerY - flat.centerY) < 0.01f, "même centre")
        T.eq(result(0f).flat().width, result(10f).flat().width, "le rectangle droit (pivot) ne change pas")
    }
    T.test("texte penché: la rotation est conservée par l'agrandissement et le décalage, et suit le texte") {
        val r = result(-7f)
        T.eq(-7f, r.scaled(1.2f).tilt, "agrandi")
        T.eq(-7f, r.shifted(50f, -20f).tilt, "décalé")
        val a = r.placed(0f); val b = r.shifted(50f, -20f).placed(0f)
        T.ok(abs((b.centerX - a.centerX) - 50f) < 0.01f && abs((b.centerY - a.centerY) + 20f) < 0.01f, "le rectangle englobant suit le décalage")
    }
    T.test("texte penché: les rectangles de lignes sont tournés avec le bloc (une ligne haute à gauche et basse à droite pour un angle positif)") {
        val rects = result(8f).lineRects(0f)
        T.eq(2, rects.size, "une par ligne")
        val straight = result(0f).lineRects(0f)
        T.ok(rects[0].width > straight[0].width && rects[0].height > straight[0].height, "rectangle englobant de la ligne tournée plus grand")
        // le texte tourné de 8° ne déborde pas du rectangle englobant du bloc
        val all = result(8f).placed(0.5f)
        T.ok(rects.all { all.contains(it.centerX, it.centerY) }, "lignes dans le bloc")
    }
}
