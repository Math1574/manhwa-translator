package app.manhwa.core

import java.io.File

/** Polices stylisées : bibliothèque, détection des fenêtres système, choix automatique prudent (rendu simulé : le dessin réel est calibré par tools/fontlib/harness/StyledBench.kt). */
fun styledFontsTests() {
    fun bars(w: Int, h: Int, thick: Int, shift: Int = 0): BooleanArray = BooleanArray(w * h) { ((it % w) + shift) % (thick * 3) < thick }
    val W = 150; val H = 30
    val ink = bars(W, H, 3)
    fun block(text: String = "THE LEGEND OF THE DARK KING", regime: StyleMatcher.Regime = StyleMatcher.Regime.FLAT, weight: Double = 10.0) =
        StyleMatcher.Block(listOf(LineInk(text, ink, W, H)), FloatArray(StyleModel.DIM_IN), regime, FloatArray(StyleModel.DIM_OUT), FloatArray(StyleModel.DIM_OUT), weight, if (regime == StyleMatcher.Regime.FLAT) 1f else 0.5f, 0f)
    fun decision(refIou: Float, gap: Float = 0.05f) = StyleMatcher.Decision("comicneue-bold", listOf(StyleMatcher.Match("comicneue-bold", 3f, 0.2f, 0f, 0.2f, refIou, gap)), false, "SCRIPT")
    // rendu simulé : la police « parfaite » recopie l'encre, les autres ne la recouvrent presque pas ; « large » doit être étirée
    fun fake(perfect: String = "pirataone", stretch: Float = 1f) = GlyphRenderer { _, family, _, _, w, h ->
        if (family == perfect) GlyphRender(bars(w, h, 3), stretch) else GlyphRender(bars(w, h, 3, 4), 1f)
    }

    T.test("polices stylisées : bibliothèque cohérente (hors modèle appris, classes, fichiers présents dans les ressources)") {
        val styled = FontLibrary.styled
        T.ok(styled.size >= 12, "au moins une douzaine de polices stylisées : ${styled.size}")
        T.ok(styled.none { it.id in StyleModel.ids }, "les polices stylisées restent hors du modèle de style appris")
        T.ok(styled.none { it.auto }, "jamais choisies par le choix ordinaire")
        T.ok(styled.map { it.id }.toSet().size == styled.size && FontLibrary.specs.none { r -> styled.any { it.id == r.id } }, "identifiants uniques")
        val classes = setOf("tech", "gothic", "horror", "script", "stencil", "impact")
        T.ok(styled.all { it.cls in classes }, "classes : ${styled.map { it.cls }.toSet()}")
        T.ok(classes.all { c -> styled.any { it.cls == c } }, "chaque classe a au moins une police")
        T.ok(styled.all { FontLibrary.spec(it.id) === it }, "retrouvées par identifiant")
        T.ok(FontLibrary.spec(StyledFonts.SYSTEM_FONT)?.cls == "tech", "police des fenêtres système")
        val dir = listOf("app/src/main/assets/fonts", "../app/src/main/assets/fonts").map { File(it) }.firstOrNull { it.isDirectory }
        if (dir != null) {
            val missing = (FontLibrary.specs + styled).filter { !File(dir, it.file).isFile }.map { it.file }
            T.ok(missing.isEmpty(), "fichiers de police manquants : $missing")
            val licences = File(dir, "lib/LICENSES.txt").takeIf { it.isFile }?.readText() ?: ""
            val unlisted = styled.map { it.file.substringAfterLast('/') }.distinct().filter { it !in licences }
            T.ok(licences.isEmpty() || unlisted.isEmpty(), "licences non listées : $unlisted")
        }
    }

    T.test("fenêtre système : titre entre crochets, clair sur fond sombre") {
        T.ok(StyledFonts.bracketed("[Quest Info]"), "titre entre crochets")
        T.ok(StyledFonts.bracketed("[Level up!] You have gained 3 points"), "titre suivi d'un texte")
        T.ok(StyledFonts.bracketed("  【Quête】 Tuer cent gobelins"), "crochets pleins")
        T.ok(!StyledFonts.bracketed("Hello [there] friend"), "crochets au milieu d'une phrase")
        T.ok(!StyledFonts.bracketed("[A]") && !StyledFonts.bracketed("[ ]") && !StyledFonts.bracketed("[12]"), "trop court ou sans lettres")
        T.ok(!StyledFonts.bracketed("I AM THE STRONGEST"), "texte ordinaire")
        T.ok(StyledFonts.isSystemWindow(StyledFonts.Hint("[Quest Info]", true)), "fenêtre")
        T.ok(!StyledFonts.isSystemWindow(StyledFonts.Hint("[Quest Info]", false)), "entre crochets mais sombre sur clair : une bulle, pas une fenêtre")
        T.ok(!StyledFonts.isSystemWindow(StyledFonts.Hint("Come here", true)), "clair sur sombre mais sans crochets")
    }

    T.test("police stylisée : la fenêtre système reçoit la police technique, pas une bulle ni un groupe mêlé") {
        val b = block("[Quest Info]"); val d = decision(0.7f)
        val p = StyledFonts.refine(listOf(b), listOf(d), listOf(StyledFonts.Hint("[Quest Info]", true)), null)
        T.eq(StyledFonts.SYSTEM_FONT, p[0]?.id, "fenêtre système (sans dessin possible)")
        T.eq(null, StyledFonts.refine(listOf(b), listOf(d), listOf(StyledFonts.Hint("[Quest Info]", false)), null)[0], "texte foncé sur clair")
        // un groupe (même décision) dont un bloc n'est pas clair sur sombre : on ne le met pas en police technique
        val b2 = block("YOU ARE LATE"); val shared = decision(0.7f)
        val mixed = StyledFonts.refine(listOf(b, b2), listOf(shared, shared), listOf(StyledFonts.Hint("[Quest Info]", true), StyledFonts.Hint("YOU ARE LATE", false)), null)
        T.eq(listOf<String?>(null, null), mixed.map { it?.id }, "groupe mêlé")
        val both = StyledFonts.refine(listOf(b, b2), listOf(shared, shared), listOf(StyledFonts.Hint("[Quest Info]", true), StyledFonts.Hint("Kill ten goblins", true)), null)
        T.eq(listOf<String?>(StyledFonts.SYSTEM_FONT, StyledFonts.SYSTEM_FONT), both.map { it?.id }, "tout le groupe de la fenêtre")
    }

    T.test("police stylisée : lettrage que aucune police ordinaire ne copie → police stylisée qui le recouvre ; sinon la police ordinaire reste") {
        val hint = StyledFonts.Hint("THE LEGEND OF THE DARK KING", false)
        val pick = StyledFonts.refine(listOf(block()), listOf(decision(0.40f)), listOf(hint), fake())[0]
        T.eq("pirataone", pick?.id, "recouvrement 1,0 contre 0,40 pour la meilleure ordinaire")
        T.ok(pick!!.reason.contains("stylisé"), pick.reason)
        T.eq(null, StyledFonts.refine(listOf(block()), listOf(decision(0.80f)), listOf(hint), fake())[0], "une police ordinaire copie déjà bien (0,80)")
        // seuils exacts, sur des mesures données : avance sur la meilleure police ordinaire, recouvrement minimal, écart de largeur, texte lu, fond uni
        fun verdict(ref: Float, iou: Float, gap: Float = 0.05f, letters: Int = 20, flat: Boolean = true) =
            StyledFonts.Verdict(ref, StyleMatcher.IOU_W * ref - StyleMatcher.WIDTH_W * 0.05f, listOf(StyledFonts.Cand("pirataone", iou, gap)), letters, flat)
        T.ok(StyledFonts.passes(verdict(0.55f, 0.70f)), "avance de 0,15 : retenue")
        T.ok(!StyledFonts.passes(verdict(0.55f, 0.64f)), "avance de 0,09 : insuffisante")
        T.ok(!StyledFonts.passes(verdict(0.30f, 0.55f)), "recouvrement de 0,55 : trop faible même loin devant")
        T.ok(!StyledFonts.passes(verdict(0.62f, 0.90f)), "une police ordinaire à 0,62 copie déjà assez bien")
        T.ok(!StyledFonts.passes(verdict(0.40f, 0.85f, gap = 0.25f)), "étirement de 28 %")
        T.ok(!StyledFonts.passes(verdict(0.40f, 0.85f, letters = StyledFonts.MIN_LETTERS - 1)) && StyledFonts.passes(verdict(0.40f, 0.85f, letters = StyledFonts.MIN_LETTERS)), "nombre de lettres")
        T.ok(!StyledFonts.passes(verdict(0.40f, 0.85f, flat = false)), "fond chargé")
        T.eq(null, StyledFonts.refine(listOf(block()), listOf(decision(0.40f)), listOf(hint), fake(stretch = 1.5f))[0], "police qu'il faudrait étirer de 50 %")
        T.eq(null, StyledFonts.refine(listOf(block("OK NO")), listOf(decision(0.40f)), listOf(hint), fake())[0], "texte trop court pour juger")
        T.eq(null, StyledFonts.refine(listOf(block(regime = StyleMatcher.Regime.ART)), listOf(decision(0.40f)), listOf(hint), fake())[0], "mesure sur dessin : trop bruitée")
        T.eq(null, StyledFonts.refine(listOf(block()), listOf(decision(0.40f)), listOf(hint), null)[0], "sans dessin possible, rien ne change")
        // pochoir, titres épais et technique : jamais choisis par la règle B (ils remplaçaient à tort des polices ordinaires grasses sur le banc d'essai)
        for (id in listOf("blackopsone", "stardosstencil-bold", "titanone", "rajdhani-bold", "exo2-w800", "oxanium-w700"))
            T.eq(null, StyledFonts.refine(listOf(block()), listOf(decision(0.40f)), listOf(hint), fake(perfect = id))[0], "$id réservée au choix manuel")
        T.ok(StyledFonts.candidates.map { it.cls }.toSet() == setOf("gothic", "horror", "script", "impact") && StyledFonts.candidates.size == 10, "candidates : ${StyledFonts.candidates.map { it.id }}")
        T.eq("knewave", StyledFonts.refine(listOf(block()), listOf(decision(0.40f)), listOf(hint), fake(perfect = "knewave"))[0]?.id, "Knewave (brush) candidate")
    }

    T.test("police stylisée : un groupe reçoit une seule police ; un bloc sans mesure ou sans décision n'en reçoit pas") {
        val shared = decision(0.40f); val hint = StyledFonts.Hint("THE LEGEND OF THE DARK KING", false)
        val p = StyledFonts.refine(listOf(block(), block(), null, block()), listOf(shared, shared, null, null), listOf(hint, hint, hint, hint), fake())
        T.eq(listOf<String?>("pirataone", "pirataone", null, null), p.map { it?.id }, "groupe cohérent")
        T.eq(emptyList<StyledFonts.Pick?>(), StyledFonts.refine(emptyList(), emptyList(), emptyList(), fake()), "page vide")
        // décision sans classement (bloc voisin) : jamais de police stylisée
        val unmeasured = StyleMatcher.Decision("comicneue-bold", emptyList(), true, "SCRIPT")
        T.eq(null, StyledFonts.refine(listOf(block()), listOf(unmeasured), listOf(hint), fake())[0], "bloc sans classement")
    }
}
