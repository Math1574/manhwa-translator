package app.manhwa.core

/** Comparaison « normale / copie des consignes / copie sans décodage spéculatif » : calcul du gain et phrases du résultat. */
fun speedReportTests() {
    fun ok(ms: Long, same: Int = 6, failed: Int = 0, tries: Int = 4, diffs: List<LineDiff> = emptyList()) = SpeedTry(ms, same, failed, tries, diffs)

    T.test("vitesse : gain en pourcentage, durées en secondes avec la virgule française") {
        T.eq(20, SpeedReport.gainPercent(10_000, 8_000), "20 % de moins")
        T.eq(-25, SpeedReport.gainPercent(8_000, 10_000), "plus lente : négatif")
        T.eq(0, SpeedReport.gainPercent(0, 5_000), "durée normale inconnue")
        T.eq(0, SpeedReport.gainPercent(5_000, 0), "durée accélérée inconnue")
        T.eq(0, SpeedReport.gainPercent(5_000, 5_000))
        T.eq("5,8 s", SpeedReport.seconds(5_840)); T.eq("0,0 s", SpeedReport.seconds(-3))
    }

    T.test("vitesse : la copie gagne, texte identique → on peut l'utiliser, avec la façon de l'activer") {
        val t = SpeedReport.describe(ReuseOutcome(7_000, 6, 6, copy = ok(5_900)))
        T.ok(t.startsWith("Normale : 7,0 s\n"), t)
        T.ok(t.contains("Copie des consignes : 5,9 s (environ 16 % de moins) · texte identique (6 lignes sur 6)"), "durées : $t")
        T.ok(t.endsWith("Le plus rapide : « Copie des consignes », texte identique : tu peux l'utiliser (active « Traduction accélérée (essai) »)."), "verdict : $t")
    }

    T.test("vitesse : seule la copie sans décodage spéculatif gagne → c'est elle qui est conseillée, avec les deux réglages") {
        val t = SpeedReport.describe(ReuseOutcome(10_000, 6, 6, copy = ok(11_000), noSpec = ok(6_000)))
        T.ok(t.contains("Copie des consignes : 11,0 s (environ 10 % de plus)"), t)
        T.ok(t.contains("Copie sans décodage spéculatif : 6,0 s (environ 40 % de moins) · texte identique"), t)
        T.ok(t.endsWith("Le plus rapide : « Copie sans décodage spéculatif », texte identique : tu peux l'utiliser (active « Traduction accélérée (essai) » puis « Sans décodage spéculatif »)."), t)
    }

    T.test("vitesse : le plus rapide des deux est choisi") {
        val t = SpeedReport.describe(ReuseOutcome(10_000, 6, 6, copy = ok(5_000), noSpec = ok(7_000)))
        T.ok(t.contains("Le plus rapide : « Copie des consignes »"), t)
    }

    T.test("vitesse : gain trop faible, ou plus lente → rien à activer") {
        val flat = SpeedReport.describe(ReuseOutcome(7_000, 6, 6, copy = ok(6_800)))
        T.ok(flat.contains("(pas de différence nette)") && flat.endsWith("laisse l'essai désactivé."), flat)
        val slow = SpeedReport.describe(ReuseOutcome(5_000, 6, 6, copy = ok(6_000), noSpec = ok(7_000)))
        T.ok(slow.contains("(environ 20 % de plus)") && slow.contains("(environ 40 % de plus)") && slow.endsWith("laisse l'essai désactivé."), slow)
    }

    T.test("vitesse : des échecs de la copie sont dits ; trop d'échecs → écartée ; un échec isolé avec un vrai gain → possible") {
        val one = SpeedReport.describe(ReuseOutcome(10_000, 6, 6, copy = ok(6_000, failed = 1, tries = 4)))
        T.ok(one.contains("   La copie a échoué 1 fois sur 4 (la traduction normale a pris le relais ; ce temps est compté)."), one)
        T.ok(one.endsWith("Le plus rapide : « Copie des consignes » malgré cet échec, texte identique : tu peux l'utiliser (active « Traduction accélérée (essai) »)."), one)
        val half = SpeedReport.describe(ReuseOutcome(10_000, 6, 6, copy = ok(6_000, failed = 2, tries = 4)))
        T.ok(half.endsWith("ou la copie échoue trop souvent : laisse l'essai désactivé."), half)
        val none = SpeedReport.describe(ReuseOutcome(10_000, 6, 6, copy = ok(6_000, failed = 0, tries = 4)))
        T.ok(!none.contains("a échoué"), none)
        val fallback = SpeedReport.describe(ReuseOutcome(10_000, 6, 6, copy = ok(6_000, failed = 3, tries = 4), noSpec = ok(7_000)))
        T.ok(fallback.contains("« Copie sans décodage spéculatif »") && fallback.contains("Le plus rapide : « Copie sans décodage spéculatif »"), "la copie qui échoue est écartée, l'autre est conseillée : $fallback")
        val absurd = SpeedReport.describe(ReuseOutcome(10_000, 6, 6, copy = ok(6_000, failed = 9, tries = 2)))
        T.ok(absurd.contains("a échoué 2 fois sur 2"), "échecs bornés par les essais : $absurd")
    }

    T.test("vitesse : texte qui change → lignes montrées côte à côte (trois au plus) et conclusion qui laisse juger") {
        val diffs = listOf(LineDiff("A", "a1", "a2"), LineDiff("B", "b1", "b2"), LineDiff("C", "c1", "c2"), LineDiff("D", "d1", "d2"), LineDiff("E", "e1", "e2"))
        val t = SpeedReport.describe(ReuseOutcome(7_000, 6, 6, copy = ok(5_000, same = 2, diffs = diffs)))
        T.ok(t.contains("texte différent sur 4 ligne(s) sur 6"), t)
        T.ok(t.contains("Lignes qui changent avec « Copie des consignes » :\n• « A »\n   Normale : a1\n   Accélérée : a2") && t.contains("• « C »") && !t.contains("• « D »"), "trois lignes au plus : $t")
        T.ok(t.indexOf("• « A »") < t.indexOf("L'application ne juge pas la qualité"), "les lignes précèdent la conclusion : $t")
        T.ok(t.endsWith("utilise-le seulement si l'accélérée te paraît aussi bonne (active « Traduction accélérée (essai) »)."), t)
        val same = SpeedReport.describe(ReuseOutcome(7_000, 6, 6, copy = ok(5_000, same = 6, diffs = listOf(LineDiff("A", "a1", "a2")))))
        T.ok(!same.contains("Lignes qui changent"), "texte identique : aucune ligne montrée : $same")
    }

    T.test("vitesse : le modèle varie déjà sans l'option → comparaison indicative, à refaire") {
        val t = SpeedReport.describe(ReuseOutcome(7_000, 5, 6, copy = ok(5_000, same = 4)))
        T.ok(t.contains("Le modèle varie aussi d'un essai à l'autre sans l'option (5 lignes sur 6 identiques entre deux essais normaux)."), t)
        T.ok(t.endsWith("refais l'essai pour en juger."), t)
    }

    T.test("vitesse : modes non essayés — raisons dites, aucun mode → laisse désactivé") {
        val t = SpeedReport.describe(ReuseOutcome(7_000, 6, 6, copy = null, noSpec = ok(4_000), skipped = listOf("Copie des consignes : non essayée, elle a fait planter l'application sur ce téléphone.")))
        T.ok(t.contains("Copie des consignes : non essayée, elle a fait planter l'application sur ce téléphone.") && t.contains("Le plus rapide : « Copie sans décodage spéculatif »"), t)
        val none = SpeedReport.describe(ReuseOutcome(7_000, 6, 6, skipped = listOf("a", "b")))
        T.ok(none.endsWith("Aucun mode accéléré n'a pu être essayé sur ce téléphone : laisse l'essai désactivé."), none)
    }

    T.test("vitesse : valeurs absurdes ne plantent pas") {
        val t = SpeedReport.describe(ReuseOutcome(0, -4, 6, copy = SpeedTry(0, 99)))
        T.ok(t.contains("texte identique") && t.contains("pas de différence nette"), t)
        val u = SpeedReport.describe(ReuseOutcome(5_000, 0, 0, copy = SpeedTry(4_000, 0)))
        T.ok(u.contains("texte identique (0 lignes sur 0)"), u)
    }
}
