package app.manhwa.core

import kotlin.math.abs

/** Réglages manuels du texte traduit (disposition, apparence), formes de zone et zone effacée en entier. */
fun formatTests() {
    val m = TextMeasurer { t, size -> t.length * size * 0.5f }   // 0,5 em par caractère

    fun rect(l: Float, t: Float, r: Float, b: Float) = RowShape.rect(l, t, r, b)

    T.test("mise en forme : aller-retour, valeurs par défaut, valeurs absurdes ramenées dans leurs bornes") {
        T.eq("-", TextFormat().encode(), "rien de choisi")
        val f = TextFormat(TextFormat.ALIGN_LEFT, 3, 1.25f, TextFormat.CASE_UPPER, -12.5f)
        T.eq(f, TextFormat.decode(f.encode()), "aller-retour")
        T.eq(TextFormat(), TextFormat.decode("-"), "défaut")
        T.eq(TextFormat(), TextFormat.decode(null), "absent")
        T.eq(TextFormat(), TextFormat.decode("n'importe quoi"), "illisible")
        T.eq(TextFormat(TextFormat.ALIGN_RIGHT, 8, 1.8f, TextFormat.CASE_LOWER, 45f), TextFormat.decode("a=9;l=99;s=7;c=8;r=500"), "bornes")
        T.eq(TextFormat(TextFormat.ALIGN_LEFT), TextFormat.decode("a=1;zz=3;l=oups"), "champ inconnu ou illisible ignoré")
        T.ok(TextFormat(spacing = 1.001f).isDefault, "écart d'interligne négligeable = défaut")
        T.eq("BONJOUR ÉTÉ", TextFormat(caseMode = TextFormat.CASE_UPPER).applyCase("Bonjour été"), "majuscules accentuées")
        T.eq("bonjour été", TextFormat(caseMode = TextFormat.CASE_LOWER).applyCase("BONJOUR ÉTÉ"), "minuscules")
        T.eq("Bonjour", TextFormat().applyCase("Bonjour"), "casse inchangée")
    }

    T.test("apparence : aller-retour, automatique par défaut, couleurs sans canal alpha") {
        T.eq("-", TextLook().encode())
        val l = TextLook(0xFF8800, 0x112233, 0.18f, 0x00FFFF, 0.4f)
        T.eq(l, TextLook.decode(l.encode()), "aller-retour")
        T.eq(TextLook(outlineW = 0f), TextLook.decode(TextLook(outlineW = 0f).encode()), "contour supprimé (0) distinct de automatique (null)")
        T.eq(TextLook(), TextLook.decode("f=zzzz;ow=beaucoup"), "illisible : automatique")
        T.eq(0x00FFFF, TextLook.decode("g=FF00FFFF").glow, "alpha ignoré")
        T.eq(TextLook.MAX_OUTLINE, TextLook.decode("ow=9").outlineW, "borne du contour")
    }

    T.test("enregistrement : formes de zone, mise en forme, apparence et zone effacée font l'aller-retour ; un ancien enregistrement garde l'automatique") {
        val region = UserRegion(RectF(10f, 20f, 210f, 120f), emptyList(), ZoneShape.ELLIPSE)
        val e = BlockEdit("m1", EditKind.MANUAL, null, "", region, "AB", "Bonjour\nle monde", false, 0f, 0f, 1f, null, false, 21f, listOf(RectF(10f, 20f, 50f, 40f)), listOf("AB"), "FREE", null,
            format = TextFormat(TextFormat.ALIGN_RIGHT, 2, 1.2f, TextFormat.CASE_UPPER, 7f), look = TextLook(0xFF0000, null, 0f, 0x00FF00, 0.3f), wipe = true)
        val back = EditsCodec.decode(EditsCodec.encode(e))!!
        T.eq(ZoneShape.ELLIPSE, back.region!!.shape, "forme de zone")
        T.eq(e.format, back.format, "mise en forme"); T.eq(e.look, back.look, "apparence"); T.eq(true, back.wipe, "zone effacée")
        T.eq("Bonjour\nle monde", back.translation, "retour à la ligne gardé")
        T.ok(back.hasBreaks, "retour à la ligne reconnu")
        val rounded = e.copy(region = UserRegion(RectF(0f, 0f, 50f, 50f), listOf(BrushStroke(listOf(Pt(5f, 5f), Pt(30f, 30f)), 8f, false)), ZoneShape.ROUNDED))
        T.eq(ZoneShape.ROUNDED, EditsCodec.decode(EditsCodec.encode(rounded))!!.region!!.shape, "forme et traits ensemble")
        // une ligne d'avant ces réglages (22 champs) : tout est automatique
        val old = EditsCodec.encode(e).split('\t').take(22).joinToString("\t")
        val o = EditsCodec.decode(old)!!
        T.ok(o.format.isDefault && o.look.isDefault && !o.wipe, "ancien enregistrement : automatique")
        T.eq(ZoneShape.RECT, EditsCodec.decode(EditsCodec.encode(e.copy(region = UserRegion(RectF(1f, 2f, 3f, 4f)))))!!.region!!.shape, "rectangle simple")
        // une correction qui ne change que la mise en forme n'est pas neutre
        val adj = BlockEdit("a", EditKind.ADJUST, RectF(0f, 0f, 10f, 10f), "A")
        T.ok(adj.isNeutral, "rien changé : neutre")
        T.ok(!adj.copy(format = TextFormat(align = TextFormat.ALIGN_LEFT)).isNeutral, "mise en forme : à garder")
        T.ok(!adj.copy(look = TextLook(fill = 0x112233)).isNeutral, "apparence : à garder")
    }

    T.test("zone : l'ellipse et le rectangle arrondi rognent les coins, le rectangle les garde ; le masque suit") {
        val r = RectF(100f, 100f, 300f, 200f)
        val rect = UserRegion(r); val ell = UserRegion(r, emptyList(), ZoneShape.ELLIPSE); val rnd = UserRegion(r, emptyList(), ZoneShape.ROUNDED)
        T.ok(rect.contains(101f, 101f) && !ell.contains(101f, 101f) && !rnd.contains(101f, 101f), "coin : seul le rectangle")
        T.ok(ell.contains(200f, 150f) && rnd.contains(200f, 150f), "centre")
        T.ok(ell.contains(101f, 150f) && rnd.contains(101f, 150f), "milieu d'un bord")
        T.ok(rnd.contains(110f, 110f).not() || rnd.contains(125f, 125f), "arrondi : les coins sont rognés, plus loin on est dedans")
        val w = 400; val h = 300
        val mr = rect.mask(0, 0, w, h, 1f); val me = ell.mask(0, 0, w, h, 1f)
        fun count(a: BooleanArray) = a.count { it }
        T.ok(count(me) < count(mr) * 0.85f && count(me) > count(mr) * 0.7f, "l'ellipse couvre environ π/4 du rectangle : ${count(me)} / ${count(mr)}")
        T.ok(mr[101 * w + 101] && !me[101 * w + 101], "coin du masque")
        T.ok(me[150 * w + 200], "centre du masque")
        T.eq(ZoneShape.ELLIPSE, ell.shifted(40f).shape, "la forme suit un décalage")
        T.ok(ell.shifted(40f).contains(200f, 190f), "et le décalage")
        T.eq(r, ell.bounds, "boîte englobante : le rectangle")
    }

    T.test("lignes : un retour à la ligne tapé dans la traduction est toujours une fin de ligne") {
        val f = ShapeLayout.fit(rect(0f, 0f, 600f, 200f), 100f, "Bonjour\nle monde entier", m, 30f, 8f, 1.2f, 4f)
        T.eq(listOf("Bonjour", "le monde entier"), f.lines.map { it.text }, "deux lignes, comme tapé")
        val g = ShapeLayout.fit(rect(0f, 0f, 600f, 300f), 150f, "Un\ndeux\ntrois", m, 30f, 8f, 1.2f, 4f)
        T.eq(listOf("Un", "deux", "trois"), g.lines.map { it.text }, "trois lignes")
        val h = ShapeLayout.fit(rect(0f, 0f, 600f, 200f), 100f, "Bonjour\r\n\r\nle monde", m, 30f, 8f, 1.2f, 4f)
        T.eq(listOf("Bonjour", "le monde"), h.lines.map { it.text }, "lignes vides et retours Windows ignorés")
        val one = ShapeLayout.fit(rect(0f, 0f, 600f, 200f), 100f, "Bonjour le monde entier", m, 30f, 8f, 1.2f, 4f)
        T.eq(1, one.lines.size, "sans retour : une ligne quand elle tient")
        // trop long pour tenir à la taille demandée : le texte rétrécit mais le retour voulu reste
        val tight = ShapeLayout.fit(rect(0f, 0f, 160f, 200f), 100f, "Bonjour tout le monde\nici", m, 40f, 6f, 1.2f, 4f)
        T.ok(tight.lines.size >= 2 && tight.lines.last().text == "ici", "dernière ligne = « ici » : ${tight.lines.map { it.text }}")
        T.ok(tight.lines.none { it.text.contains("ici") && it.text != "ici" }, "« ici » jamais sur la ligne d'avant")
    }

    T.test("lignes : un nombre de lignes imposé est respecté, le texte rétrécit si la place manque") {
        val txt = "Je ne te laisserai jamais partir"
        val auto = ShapeLayout.fit(rect(0f, 0f, 900f, 300f), 150f, txt, m, 40f, 8f, 1.2f, 4f)
        T.eq(1, auto.lines.size, "automatique : une ligne")
        for (n in 1..4) {
            val f = ShapeLayout.fit(rect(0f, 0f, 900f, 300f), 150f, txt, m, 40f, 8f, 1.2f, 4f, null, TextFormat(lines = n))
            T.eq(n, f.lines.size, "$n lignes imposées : ${f.lines.map { it.text }}")
            T.eq(txt, f.lines.joinToString(" ") { it.text }, "$n lignes : le texte est complet")
        }
        val six = ShapeLayout.fit(rect(0f, 0f, 600f, 300f), 150f, "Un deux trois", m, 40f, 8f, 1.2f, 4f, null, TextFormat(lines = 6))
        T.eq(3, six.lines.size, "jamais plus de lignes que de mots")
        val narrow = ShapeLayout.fit(rect(0f, 0f, 200f, 300f), 150f, txt, m, 40f, 8f, 1.2f, 4f, null, TextFormat(lines = 1))
        T.eq(1, narrow.lines.size, "une seule ligne dans une forme étroite")
        T.ok(narrow.size < auto.size, "le texte rétrécit : ${narrow.size} < ${auto.size}")
        val withBreak = ShapeLayout.fit(rect(0f, 0f, 900f, 300f), 150f, "Un\ndeux trois quatre", m, 40f, 8f, 1.2f, 4f, null, TextFormat(lines = 1))
        T.eq(2, withBreak.lines.size, "un retour voulu l'emporte sur « une seule ligne »")
    }

    T.test("alignement : à gauche, toutes les lignes partent du même bord ; à droite, finissent au même bord ; centré : même axe") {
        val txt = "Peu importe la situation, je dois rassembler plus d'âmes ce soir"
        val shape = rect(0f, 0f, 300f, 200f)
        val center = ShapeLayout.fit(shape, 100f, txt, m, 40f, 8f, 1.2f, 4f, 150f)
        val left = ShapeLayout.fit(shape, 100f, txt, m, 40f, 8f, 1.2f, 4f, 150f, TextFormat(align = TextFormat.ALIGN_LEFT))
        val right = ShapeLayout.fit(shape, 100f, txt, m, 40f, 8f, 1.2f, 4f, 150f, TextFormat(align = TextFormat.ALIGN_RIGHT))
        T.ok(center.lines.size >= 2 && left.lines.size >= 2 && right.lines.size >= 2, "plusieurs lignes : ${center.lines.size}/${left.lines.size}/${right.lines.size}")
        T.ok(center.lines.all { abs(it.cx - center.lines[0].cx) < 0.01f }, "centré : même axe")
        T.ok(left.lines.all { abs((it.cx - it.width / 2f) - (left.lines[0].cx - left.lines[0].width / 2f)) < 0.01f }, "gauche : même bord gauche")
        T.ok(right.lines.all { abs((it.cx + it.width / 2f) - (right.lines[0].cx + right.lines[0].width / 2f)) < 0.01f }, "droite : même bord droit")
        T.ok(left.lines.map { it.width }.distinct().size > 1, "les lignes n'ont pas toutes la même largeur (sinon l'alignement ne se voit pas)")
        for (f in listOf(left, right)) for (ln in f.lines) T.ok(ln.cx - ln.width / 2f >= 3.99f && ln.cx + ln.width / 2f <= 296.01f, "dans la forme : ${ln.cx - ln.width / 2f}..${ln.cx + ln.width / 2f}")
        // le bloc est centré sur l'axe du texte d'origine
        val l0 = left.lines.minOf { it.cx - it.width / 2f }; val l1 = left.lines.maxOf { it.cx + it.width / 2f }
        T.ok(abs((l0 + l1) / 2f - 150f) < 1f, "bloc centré sur l'ancre : ${(l0 + l1) / 2f}")
        // le texte est le même quel que soit l'alignement
        T.eq(txt, left.lines.joinToString(" ") { it.text }, "texte complet")
    }

    T.test("alignement : sans réglage (null ou défaut) la mise en page est exactement celle d'avant") {
        val txt = "Peu importe la situation, je dois rassembler plus d'âmes ce soir"
        val a = ShapeLayout.fit(rect(0f, 0f, 300f, 200f), 100f, txt, m, 40f, 8f, 1.2f, 4f, 120f)
        val b = ShapeLayout.fit(rect(0f, 0f, 300f, 200f), 100f, txt, m, 40f, 8f, 1.2f, 4f, 120f, TextFormat())
        T.eq(a.lines, b.lines, "mêmes lignes"); T.eq(a.size, b.size, "même taille")
        val e = ShapeLayout.fit(RowShape.ellipse(0f, 0f, 300f, 200f), 100f, txt, m, 40f, 8f, 1.2f, 4f, 150f, TextFormat(align = TextFormat.ALIGN_LEFT))
        T.ok(e.lines.isNotEmpty(), "ellipse, alignée à gauche : une mise en page existe")
        T.eq(txt, e.lines.joinToString(" ") { it.text }, "ellipse : texte complet")
        // trop long pour la forme à toute taille : meilleure tentative, mais retour voulu respecté
        val tiny = ShapeLayout.fit(rect(0f, 0f, 60f, 40f), 20f, "Un très long texte qui ne tient pas\nici", m, 30f, 20f, 1.2f, 2f, null, TextFormat(align = TextFormat.ALIGN_LEFT))
        T.ok(tiny.lines.size == 2 && tiny.lines[1].text == "ici" && !tiny.fits, "repli : 2 lignes, retour respecté : ${tiny.lines.map { it.text }} tient=${tiny.fits}")
    }

    T.test("mise en page d'un bloc : interligne, casse, rotation et retours à la ligne passent du réglage à la mise en page") {
        val box = RectF(100f, 100f, 400f, 160f)
        val blk = TranslatedBlock(1, box, "HELLO", "Bonjour le monde", false, 60f, listOf(box))
        fun prep(edit: RenderEdit?, tr: String = blk.translated): BlockPrep {
            val b = blk.copy(translated = tr)
            return BlockLayout.prepare(b, null, 1f, 800f, 600f, 60f, "fr", emptyList(), listOf(b), emptyMap<Int, RectF?>(), { false }, edit)
        }
        val neutral = prep(null)
        T.eq("Bonjour le monde", neutral.text, "sans réglage")
        val up = prep(RenderEdit(0f, 0f, 1f, null, null, false, "e", format = TextFormat(caseMode = TextFormat.CASE_UPPER)))
        T.eq("BONJOUR LE MONDE", up.text, "majuscules")
        T.ok(up.format.caseMode == TextFormat.CASE_UPPER, "le réglage est dans le bloc")
        // retour à la ligne : espace dans une traduction automatique, conservé dans une saisie de l'utilisateur
        T.eq("Bonjour le monde", prep(null, "Bonjour\nle monde").text, "traduction automatique : espace")
        T.eq("Bonjour\nle monde", prep(RenderEdit(0f, 0f, 1f, null, null, false, "e", breaks = true), "Bonjour\nle monde").text, "saisie de l'utilisateur : retour gardé")
        val meas = TextMeasurer { t, size -> t.length * size * 0.5f }
        val base = BlockLayout.layout(neutral, 1f, 800f, 600f, 1f, null, meas, 0.7f)!!
        val spaced = BlockLayout.layout(prep(RenderEdit(0f, 0f, 1f, null, null, false, "e", format = TextFormat(spacing = 1.5f))), 1f, 800f, 600f, 1f, null, meas, 0.7f)!!
        T.ok(spaced.fit.lineStep > base.fit.lineStep * 1.3f || spaced.fit.lines.size == 1, "interligne : pas ${spaced.fit.lineStep} contre ${base.fit.lineStep}")
        val tilted = BlockLayout.layout(prep(RenderEdit(0f, 0f, 1f, null, null, false, "e", format = TextFormat(angle = 12f))), 1f, 800f, 600f, 1f, null, meas, 0.7f)!!
        T.ok(abs(tilted.tilt - 12f) < 0.01f, "rotation ${tilted.tilt}")
        val lines2 = BlockLayout.layout(prep(RenderEdit(0f, 0f, 1f, null, null, false, "e", format = TextFormat(lines = 2))), 1f, 800f, 600f, 1f, null, meas, 0.7f)!!
        T.eq(2, lines2.fit.lines.size, "deux lignes imposées")
        T.eq(0, BlockLayout.emptyResult().fit.lines.size, "mise en page vide")
        T.eq(RectF(0f, 0f, 0f, 0f), BlockLayout.emptyResult().placed(0f), "emprise vide")
    }

    T.test("zone effacée : un fond uni est rempli de sa couleur, tout le contenu de la zone disparaît, rien en dehors") {
        val w = 300; val h = 200
        val px = IntArray(w * h) { 0xFFF4F0E8.toInt() }                     // fond crème uni
        for (y in 80 until 100) for (x in 100 until 200) px[y * w + x] = 0xFF101010.toInt()      // du « texte »
        for (y in 120 until 140) for (x in 60 until 90) px[y * w + x] = 0xFFCC2222.toInt()       // une tache rouge hors zone
        val src = ArraySource(w, h, px)
        val region = UserRegion(RectF(90f, 70f, 210f, 110f))
        val p = Erasure.wipe(src, 1f, region)!!
        T.eq(Erasure.Mode.FLAT, p.mode, "fond uni : aplat")
        T.eq(0xF4F0E8, p.bg and 0xFFFFFF, "couleur du fond")
        fun inside(q: Erasure.Plan, x: Int, y: Int) = x >= q.x0 && y >= q.y0 && x < q.x0 + q.w && y < q.y0 + q.h
        fun masked(q: Erasure.Plan, x: Int, y: Int) = inside(q, x, y) && q.mask[(y - q.y0) * q.w + (x - q.x0)]
        fun layerAt(q: Erasure.Plan, x: Int, y: Int) = if (inside(q, x, y)) q.layer[(y - q.y0) * q.w + (x - q.x0)] else 0
        T.ok(masked(p, 150, 90) && masked(p, 95, 75) && masked(p, 205, 105), "dans la zone : remplacé")
        T.ok(!masked(p, 60, 130) && !masked(p, 215, 90) && !masked(p, 150, 120), "hors zone : intact")
        T.eq(0xF4F0E8, layerAt(p, 150, 90) and 0xFFFFFF, "le texte est recouvert par le fond")
        T.eq(0xFF, layerAt(p, 150, 90) ushr 24, "opaque")
        T.eq(0, layerAt(p, 60, 130), "rien à poser hors zone")
        val ell = Erasure.wipe(src, 1f, UserRegion(RectF(90f, 70f, 210f, 110f), emptyList(), ZoneShape.ELLIPSE))!!
        T.ok(!masked(ell, 91, 71) && masked(ell, 150, 90), "ellipse : coin épargné, centre effacé")
        T.eq(null, Erasure.wipe(src, 1f, UserRegion(RectF(-50f, -50f, -10f, -10f))), "zone hors de l'image : rien")
    }

    T.test("zone effacée : sur un dégradé, la zone est remplie en suivant le dégradé (pas d'aplat d'une seule couleur)") {
        val w = 300; val h = 200
        val px = IntArray(w * h) { val x = it % w; val g = 40 + x * 200 / w; (0xFF shl 24) or (g shl 16) or (g shl 8) or g }     // dégradé horizontal de gris
        for (y in 80 until 120) for (x in 100 until 200) px[y * w + x] = 0xFFFF00FF.toInt()                                  // objet à effacer
        val p = Erasure.wipe(ArraySource(w, h, px), 1f, UserRegion(RectF(95f, 75f, 205f, 125f)))!!
        T.eq(Erasure.Mode.SMOOTH, p.mode, "dégradé : mode lissé")
        fun col(x: Int, y: Int) = p.layer[(y - p.y0) * p.w + (x - p.x0)]
        val left = col(100, 100) and 0xFF; val right = col(199, 100) and 0xFF
        T.ok(right > left + 30, "le remplissage suit le dégradé : gauche $left, droite $right")
        T.ok((col(150, 100) shr 16 and 0xFF) < 255 && (col(150, 100) and 0xFF) > 40, "plus de magenta : ${col(150, 100).toString(16)}")
        T.ok(abs(((col(150, 100) shr 16) and 0xFF) - ((col(150, 100) shr 8) and 0xFF)) < 12, "remplissage gris")
        val d = Erasure.diffuse(IntArray(25) { 0xFF000000.toInt() }.also { it[12] = 0xFFFFFFFF.toInt() }, 5, 5, BooleanArray(25) { it == 12 }, 0x808080)
        T.eq(0xFF000000.toInt(), d[12], "un trou entouré de noir devient noir")
        val all = Erasure.diffuse(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2, BooleanArray(4) { true }, 0x336699)
        T.eq(0x336699, all[0] and 0xFFFFFF, "rien d'autour : couleur de repli")
    }

    T.test("zone effacée : avec le plan d'un texte, les mesures du lettrage sont gardées et seul l'effacement change") {
        val w = 400; val h = 200
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (y in 90 until 110) for (x in 100 until 300) if ((x / 6) % 2 == 0) px[y * w + x] = 0xFF000000.toInt()
        val src = ArraySource(w, h, px)
        val box = RectF(100f, 90f, 300f, 110f)
        val base = Erasure.plan(src, 1f, listOf(box), 20f, w.toFloat(), h.toFloat(), extra = listOf(RectF(60f, 60f, 340f, 140f)))!!
        val region = UserRegion(RectF(60f, 60f, 340f, 140f))
        val wiped = Erasure.wipe(src, 1f, region, base)!!
        T.eq(base.x0, wiped.x0, "même région d'analyse"); T.eq(base.w, wiped.w, "même largeur")
        T.ok(wiped.ink != null && wiped.ink === base.ink, "encre d'origine gardée (choix de la police)")
        T.eq(base.textColor, wiped.textColor, "couleur mesurée gardée")
        val n = wiped.mask.count { it }
        T.ok(n >= 280 * 80 * 0.95, "toute la zone est effacée : $n pixels")
        T.eq(Erasure.Mode.FLAT, wiped.mode, "fond blanc : aplat")
    }

    T.test("zone ovale : la traduction reste dans l'ovale ; un carré d'effacement sans texte n'est ni un obstacle ni une mesure de taille") {
        val zone = RectF(100f, 100f, 400f, 260f)
        val region = UserRegion(zone, emptyList(), ZoneShape.ELLIPSE)
        // image blanche avec une ligne de texte noir au milieu de la zone : le plan d'effacement existe (sans lui, aucune borne n'est posée)
        val w = 800; val h = 600
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (y in 170 until 190) for (x in 150 until 350) if ((x / 6) % 2 == 0) px[y * w + x] = 0xFF000000.toInt()
        val src = ArraySource(w, h, px)
        val line = RectF(150f, 170f, 350f, 190f)
        val blk = TranslatedBlock(EditApplier.MANUAL_ID_BASE, line, "HELLO", "Peu importe la situation, je dois rassembler plus d'âmes ce soir", false, 20f, listOf(line))
        val meas = TextMeasurer { t, size -> t.length * size * 0.5f }
        fun prepOf(reg: UserRegion) = BlockLayout.prepare(blk, src, 1f, w.toFloat(), h.toFloat(), 20f, "fr", emptyList(), listOf(blk), emptyMap<Int, RectF?>(), { false }, RenderEdit(0f, 0f, 1f, null, reg, true, "m"))
        val prep = prepOf(region)
        T.ok(prep.plan != null, "plan d'effacement")
        T.ok(prep.container != null && !prep.bubbleRect, "ovale : le texte est borné comme dans une bulle ronde")
        fun worstOf(pr: BlockPrep): Float {
            val r = BlockLayout.layout(pr, 1f, w.toFloat(), h.toFloat(), 1f, src, meas, 0.7f)!!
            var worst = 0f
            for (ln in r.fit.lines) for (x in listOf(ln.cx - ln.width / 2f, ln.cx + ln.width / 2f)) for (y in listOf(ln.top, ln.top + r.fit.lineStep)) {
                val ex = (x - zone.centerX) / (zone.width / 2f); val ey = (y - zone.centerY) / (zone.height / 2f)
                worst = maxOf(worst, ex * ex + ey * ey)
            }
            return worst
        }
        val worst = worstOf(prep)
        T.ok(worst <= 1.1f, "tous les coins de ligne sont dans l'ovale (pire : $worst)")
        // zone rectangulaire (par défaut) : aucune borne ajoutée
        val plain = prepOf(UserRegion(zone))
        T.ok(plain.container == null, "rectangle : pas de bulle ajoutée")
        // carré d'effacement sans traduction
        val big = RectF(0f, 0f, 700f, 500f)
        val wipeBlk = TranslatedBlock(EditApplier.MANUAL_ID_BASE + 1, big, "", "", false, 40f, listOf(big))
        val other = TranslatedBlock(1, RectF(100f, 100f, 400f, 160f), "HELLO", "Bonjour", false, 60f, listOf(RectF(100f, 100f, 400f, 160f)))
        val pOther = BlockLayout.prepare(other, null, 1f, 800f, 600f, 60f, "fr", emptyList(), listOf(other, wipeBlk), emptyMap<Int, RectF?>(), { false }, null)
        T.eq(0, pOther.obstacles.size, "un carré d'effacement n'est pas un obstacle")
        val pWipe = BlockLayout.prepare(wipeBlk, null, 1f, 800f, 600f, 40f, "fr", emptyList(), listOf(other, wipeBlk), emptyMap<Int, RectF?>(), { false }, RenderEdit(0f, 0f, 1f, null, UserRegion(big), true, "w", wipe = true))
        val hs = BlockLayout.harmonizedHeights(listOf(pOther, pWipe), 1f)
        T.eq(0f, hs[1], "pas de mesure de taille pour un bloc sans texte")
        val unchanged = BlockLayout.harmonizedHeights(listOf(pOther), 1f)
        T.eq(unchanged[0], hs[0], "les autres blocs gardent leur taille")
    }
}
