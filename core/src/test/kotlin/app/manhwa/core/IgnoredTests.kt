package app.manhwa.core

import java.util.Random

private fun grid(seed: Long): IntArray { val r = Random(seed); return IntArray(Fingerprint.COLS * Fingerprint.ROWS) { r.nextInt(256) } }

private fun fp(w: Int, h: Int, g: IntArray) = Fingerprint.fromGrid(w, h, g)

fun ignoredTests() {
    T.test("ignorés: la comparaison des textes ne retient que lettres et chiffres, sans casse ni accents") {
        T.eq("translatedbyxscans", IgnoredMatch.normalize("Translated by: X-Scans!"))
        T.eq("cafe2", IgnoredMatch.normalize("  Café  2 "))
        T.eq("", IgnoredMatch.normalize("... ?!"))
    }
    T.test("ignorés: un texte court doit être identique, un texte long tolère une ou deux lettres mal lues") {
        T.ok(IgnoredMatch.same("ok", "ok"), "identiques")
        T.ok(!IgnoredMatch.same("no", "now"), "court : pas de tolérance")
        T.ok(!IgnoredMatch.same("hello", "hallo"), "moins de dix caractères : pas de tolérance")
        T.ok(IgnoredMatch.same("translatedbyxyzscans", "translatedbyxyzscan5"), "une lettre mal lue")
        T.ok(IgnoredMatch.same("translatedbyxyzscans", "translatedbyxyzscens"), "une lettre changée")
        T.ok(!IgnoredMatch.same("translatedbyxyzscans", "joinourdiscordserver"), "deux textes sans rapport")
        T.ok(!IgnoredMatch.same("translatedbyxyzscans", "translatedbyxyzscansjoinourdiscord"), "longueurs trop différentes")
    }
    T.test("ignorés: une règle « partout » vaut pour toute œuvre, une règle d'œuvre seulement pour elle") {
        val r = IgnoreRules().withText(IgnoredText.EVERYWHERE, "Join our Discord server!").withText("site|oeuvre-a", "Read this first")
        T.ok(r.textIgnored("site|oeuvre-b", "JOIN OUR DISCORD SERVER"), "partout, autre œuvre")
        T.ok(r.textIgnored(null, "join our discord server"), "partout, sans œuvre")
        T.ok(r.textIgnored("site|oeuvre-a", "Read this first"), "œuvre A")
        T.ok(!r.textIgnored("site|oeuvre-b", "Read this first"), "la règle d'œuvre ne vaut pas pour B")
        T.ok(!r.textIgnored(null, "Read this first"), "ni sans œuvre")
        T.ok(!r.textIgnored("site|oeuvre-a", "Read this second"), "un autre texte n'est pas ignoré")
    }
    T.test("ignorés: les lectures un peu bruitées du même texte sont reconnues, pas les mots courts voisins") {
        val r = IgnoreRules().withText(IgnoredText.EVERYWHERE, "TRANSLATED BY XYZ SCANS").withText(IgnoredText.EVERYWHERE, "NO")
        T.ok(r.textIgnored(null, "TRANSLATED BY XYZ SCAN5"), "chiffre pour une lettre")
        T.ok(r.textIgnored(null, "translated by xyz scans."), "ponctuation et casse")
        T.ok(r.textIgnored(null, "No!"), "mot court identique")
        T.ok(!r.textIgnored(null, "NOW"), "mot court voisin")
        T.ok(!r.textIgnored(null, "?!"), "texte sans lettre")
        T.ok(!r.textIgnored(null, ""), "texte vide")
    }
    T.test("ignorés: ajouter deux fois la même règle ne la double pas, un texte vide est refusé, retirer une règle la retire") {
        val a = IgnoreRules().withText(IgnoredText.EVERYWHERE, "Hello there!")
        val b = a.withText(IgnoredText.EVERYWHERE, "hello  THERE").withText(IgnoredText.EVERYWHERE, " ?! ")
        T.eq(1, b.texts.size)
        T.eq(2, b.withText("s|o", "hello there").texts.size)   // même texte, autre portée : règle distincte
        T.eq(0, b.withoutText(IgnoredText.EVERYWHERE, "Hello there!").texts.size)
        T.eq(1, b.withoutText(IgnoredText.EVERYWHERE, "autre").texts.size)
    }
    T.test("empreinte: la même image légèrement altérée a une empreinte proche, une autre image une empreinte très différente") {
        val g = grid(7)
        val noisy = IntArray(g.size) { (g[it] + (it % 5) - 2).coerceIn(0, 255) }   // réencodage : variations de quelques niveaux
        val a = fp(800, 300, g); val b = fp(800, 300, noisy); val c = fp(800, 300, grid(99))
        T.ok(a.usable && c.usable, "images riches en contenu")
        T.ok(a.distance(b) <= Fingerprint.MAX_DISTANCE, "même image : écart ${a.distance(b)}")
        T.ok(a.distance(c) > 60, "autre image : écart ${a.distance(c)}")
        T.eq(0, a.distance(a))
    }
    T.test("empreinte: l'écriture en hexadécimal se relit à l'identique") {
        val a = fp(640, 200, grid(3))
        T.eq(64, a.hex().length)
        val back = Fingerprint.fromHex(640, 200, a.hex())
        T.ok(back != null && a.distance(back) == 0, "relue")
        T.ok(Fingerprint.fromHex(1, 1, "zz") == null, "texte invalide refusé")
        T.ok(Fingerprint.fromHex(1, 1, "00") == null, "longueur invalide refusée")
    }
    T.test("images ignorées: mêmes octets ou même empreinte, jamais une autre image ni une page presque unie") {
        val g = grid(11); val f = fp(800, 300, g)
        val rules = IgnoreRules().withImage(IgnoredImage("i1", "sha-banner", 800, 300, f.hex(), "bandeau"))
        T.eq("i1", rules.imageMatch("sha-banner", null)?.id)                                  // mêmes octets, sans empreinte
        T.eq("i1", rules.imageMatch("sha-autre", fp(800, 300, IntArray(g.size) { (g[it] + (it % 3) - 1).coerceIn(0, 255) }))?.id)   // réencodée
        T.eq("i1", rules.imageMatch("sha-autre", fp(400, 150, g))?.id)                         // redimensionnée, mêmes proportions
        T.ok(rules.imageMatch("sha-autre", fp(800, 300, grid(12))) == null, "autre image")
        T.ok(rules.imageMatch("sha-autre", fp(800, 420, g)) == null, "mêmes points mais proportions différentes")
        T.ok(rules.imageMatch("sha-autre", null) == null, "sans empreinte, seuls les octets comptent")
        val blank = fp(800, 300, IntArray(g.size) { 255 })
        T.ok(!blank.usable, "page unie : empreinte inutilisable")
        T.ok(rules.imageMatch("sha-autre", blank) == null, "une page unie n'est pas reconnue")
        val blankRule = IgnoreRules().withImage(IgnoredImage("i2", "sha-blanc", 800, 300, blank.hex()))
        T.ok(blankRule.imageMatch("sha-autre", blank) == null, "une règle d'image unie ne reconnaît que ses octets")
        T.eq("i2", blankRule.imageMatch("sha-blanc", null)?.id)
    }
    T.test("images ignorées: ajouter la même image remplace la règle, retirer l'enlève") {
        val a = IgnoredImage("i1", "sha1", 100, 50, "", "premier")
        val r = IgnoreRules().withImage(a).withImage(IgnoredImage("i9", "sha1", 100, 50, "", "même octets"))
        T.eq(1, r.images.size); T.eq("même octets", r.images[0].label)
        T.eq(0, r.withoutImage("i9").images.size)
        T.eq(1, r.withoutImage("autre").images.size)
    }
    T.test("ignorés: l'enregistrement se relit à l'identique, même avec des tabulations, retours à la ligne et barres obliques") {
        val tricky = "ligne 1\nligne\t2 \\ fin"
        val img = IgnoredImage("i1", "abc123", 800, 300, fp(800, 300, grid(5)).hex(), "bandeau\tde fin", 1234567890123L)
        val r = IgnoreRules().withText(IgnoredText.EVERYWHERE, tricky).withText("site|titre", "autre texte").withImage(img)
        val back = IgnoreRules.parse(r.serialize())
        T.eq(r.texts, back.texts); T.eq(r.images, back.images)
        T.eq(tricky, back.texts[0].text)
        T.eq("bandeau\tde fin", back.images[0].label)
    }
    T.test("ignorés: une ligne abîmée est sautée sans perdre le reste") {
        val good = IgnoreRules().withText(IgnoredText.EVERYWHERE, "texte un").withText(IgnoredText.EVERYWHERE, "texte deux").serialize()
        val damaged = "I\tx\ty\n" + "I\tid\tsha\tpasunnombre\t5\tbits\tl\t0\n" + "T\tseul\n" + good + "n'importe quoi\n"
        val back = IgnoreRules.parse(damaged)
        T.eq(2, back.texts.size); T.eq(0, back.images.size)
        T.ok(IgnoreRules.parse("").isEmpty, "fichier vide")
    }
}
