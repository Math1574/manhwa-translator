package app.manhwa.core

/** Comparaison « normale / accélérée » : calcul du gain et phrases du résultat. */
fun speedReportTests() {
    T.test("vitesse : gain en pourcentage, durées en secondes avec la virgule française") {
        T.eq(20, SpeedReport.gainPercent(10_000, 8_000), "20 % de moins")
        T.eq(-25, SpeedReport.gainPercent(8_000, 10_000), "plus lente : négatif")
        T.eq(0, SpeedReport.gainPercent(0, 5_000), "durée normale inconnue")
        T.eq(0, SpeedReport.gainPercent(5_000, 0), "durée accélérée inconnue")
        T.eq(0, SpeedReport.gainPercent(5_000, 5_000))
        T.eq("5,8 s", SpeedReport.seconds(5_840)); T.eq("0,0 s", SpeedReport.seconds(-3))
    }

    T.test("vitesse : gain net et texte identique → on peut l'activer") {
        val t = SpeedReport.describe(ReuseOutcome(7_000, 5_900, 6, 6, 6))
        T.ok(t.contains("Normale : 7,0 s · Accélérée : 5,9 s (environ 16 % de moins)"), "durées : $t")
        T.ok(t.contains("Texte identique (6 lignes sur 6)."), "texte : $t")
        T.ok(t.endsWith("Gain net, texte identique : tu peux l'activer."), "verdict : $t")
    }

    T.test("vitesse : gain trop faible, ou plus lente → inutile") {
        val flat = SpeedReport.describe(ReuseOutcome(7_000, 6_800, 6, 6, 6))
        T.ok(flat.contains("(pas de différence nette)") && flat.endsWith("inutile de l'activer."), flat)
        val slow = SpeedReport.describe(ReuseOutcome(5_000, 6_000, 6, 6, 6))
        T.ok(slow.contains("(environ 20 % de plus)") && slow.endsWith("inutile de l'activer."), slow)
    }

    T.test("vitesse : texte qui change à cause de l'option → laissée désactivée") {
        val t = SpeedReport.describe(ReuseOutcome(7_000, 5_000, 4, 6, 6))
        T.ok(t.contains("Texte différent sur 2 ligne(s) sur 6."), t)
        T.ok(t.endsWith("mieux vaut la laisser désactivée."), t)
    }

    T.test("vitesse : le modèle varie déjà sans l'option → comparaison indicative, à refaire") {
        val t = SpeedReport.describe(ReuseOutcome(7_000, 5_000, 4, 5, 6))
        T.ok(t.contains("le modèle varie aussi d'un essai à l'autre sans l'option (5 lignes sur 6 identiques entre deux essais normaux)"), t)
        T.ok(t.endsWith("refais l'essai pour en juger."), t)
    }

    T.test("vitesse : valeurs absurdes ne plantent pas") {
        val t = SpeedReport.describe(ReuseOutcome(0, 0, 99, -4, 6))
        T.ok(t.contains("Texte identique") && t.contains("pas de différence nette"), t)
        val u = SpeedReport.describe(ReuseOutcome(5_000, 4_000, 0, 0, 0))
        T.ok(u.contains("Texte identique (0 lignes sur 0)."), u)
    }
}
