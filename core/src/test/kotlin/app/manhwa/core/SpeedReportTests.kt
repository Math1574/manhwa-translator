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

    T.test("vitesse : des échecs de la copie sont dits ; trop d'échecs → laissée désactivée, un échec isolé avec un vrai gain → possible") {
        val one = SpeedReport.describe(ReuseOutcome(10_000, 6_000, 6, 6, 6, failed = 1, tries = 4))
        T.ok(one.contains("La copie des consignes a échoué 1 fois sur 4 (la traduction normale a pris le relais ; ce temps est compté)."), one)
        T.ok(one.endsWith("Gain net malgré cet échec, texte identique : tu peux l'activer."), one)
        val two = SpeedReport.describe(ReuseOutcome(10_000, 6_000, 6, 6, 6, failed = 1, tries = 2))
        T.ok(two.endsWith("La copie échoue trop souvent sur ce téléphone : mieux vaut la laisser désactivée."), two)
        val none = SpeedReport.describe(ReuseOutcome(10_000, 6_000, 6, 6, 6, failed = 0, tries = 4))
        T.ok(!none.contains("a échoué") && none.endsWith("Gain net, texte identique : tu peux l'activer."), none)
        val changed = SpeedReport.describe(ReuseOutcome(10_000, 6_000, 4, 6, 6, failed = 3, tries = 4))
        T.ok(changed.endsWith("Le texte change avec l'option : mieux vaut la laisser désactivée."), "le texte prime : $changed")
        val absurd = SpeedReport.describe(ReuseOutcome(10_000, 6_000, 6, 6, 6, failed = 9, tries = 2))
        T.ok(absurd.contains("a échoué 2 fois sur 2"), "échecs bornés par les essais : $absurd")
    }

    T.test("vitesse : valeurs absurdes ne plantent pas") {
        val t = SpeedReport.describe(ReuseOutcome(0, 0, 99, -4, 6))
        T.ok(t.contains("Texte identique") && t.contains("pas de différence nette"), t)
        val u = SpeedReport.describe(ReuseOutcome(5_000, 4_000, 0, 0, 0))
        T.ok(u.contains("Texte identique (0 lignes sur 0)."), u)
    }
}
