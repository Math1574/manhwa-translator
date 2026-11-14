package app.manhwa.core

import java.util.Locale

/** Une réplique dont le texte diffère entre le mode normal et le mode accéléré (pour que l'utilisateur juge lui-même). */
data class LineDiff(val source: String, val normal: String, val fast: String)

/** Résultat d'une comparaison « normale / accélérée » de la traduction par le modèle de langage, mesurée sur le téléphone avec les mêmes répliques. */
data class ReuseOutcome(
    val normalMs: Long,
    val fastMs: Long,
    /** Lignes (sur [lines]) dont le texte est le même dans les deux modes. */
    val same: Int,
    /** Lignes dont le texte est le même entre deux essais du mode normal : la variation naturelle du modèle d'un essai à l'autre. */
    val sameBaseline: Int,
    val lines: Int,
    /** Essais accélérés (sur [tries]) où la copie des consignes a échoué : la traduction normale a pris le relais et son temps est compté dans [fastMs]. */
    val failed: Int = 0,
    val tries: Int = 0,
    /** Les répliques dont le texte diffère (au plus [SpeedReport.SHOWN_DIFFS] sont montrées). */
    val diffs: List<LineDiff> = emptyList(),
)

/** Phrases qui expliquent le résultat de la comparaison, sans jargon : durées, texte identique ou non, et ce que l'on en conclut. */
object SpeedReport {
    /** Gain minimal (en %) pour parler d'un vrai gain. */
    const val MIN_GAIN = 5
    /** Nombre maximal de répliques différentes montrées dans le résultat. */
    const val SHOWN_DIFFS = 3

    /** Gain de durée en % (positif : l'accélérée est plus rapide) ; 0 si les durées sont inutilisables. */
    fun gainPercent(normalMs: Long, fastMs: Long): Int =
        if (normalMs <= 0 || fastMs <= 0) 0 else Math.round((normalMs - fastMs) * 100.0 / normalMs).toInt()

    fun seconds(ms: Long): String = String.format(Locale.FRANCE, "%.1f", ms.coerceAtLeast(0) / 1000.0) + " s"

    fun describe(o: ReuseOutcome): String {
        val gain = gainPercent(o.normalMs, o.fastMs)
        val times = "Normale : ${seconds(o.normalMs)} · Accélérée : ${seconds(o.fastMs)}" + when {
            gain >= MIN_GAIN -> " (environ $gain % de moins)"
            gain <= -MIN_GAIN -> " (environ ${-gain} % de plus)"
            else -> " (pas de différence nette)"
        }
        val n = o.lines.coerceAtLeast(0)
        val same = o.same.coerceIn(0, n)
        val base = o.sameBaseline.coerceIn(0, n)
        val text = if (same >= n) "Texte identique ($n lignes sur $n)."
        else "Texte différent sur ${n - same} ligne(s) sur $n" +
            (if (base < n) " ; le modèle varie aussi d'un essai à l'autre sans l'option ($base lignes sur $n identiques entre deux essais normaux)." else ".")
        val tries = o.tries.coerceAtLeast(0)
        val failed = o.failed.coerceIn(0, tries)
        val failures = if (failed > 0) "\nLa copie des consignes a échoué $failed fois sur $tries (la traduction normale a pris le relais ; ce temps est compté)." else ""
        val verdict = when {
            same < n && base >= n -> "Le texte change avec l'option : compare les lignes ci-dessus. L'application ne juge pas la qualité : active-la seulement si l'accélérée te paraît aussi bonne."
            same < n -> "Le modèle varie déjà un peu sans l'option : refais l'essai pour en juger."
            tries > 0 && failed * 2 >= tries -> "La copie échoue trop souvent sur ce téléphone : mieux vaut la laisser désactivée."
            gain >= MIN_GAIN && failed > 0 -> "Gain net malgré ${if (failed > 1) "ces échecs" else "cet échec"}, texte identique : tu peux l'activer."
            gain >= MIN_GAIN -> "Gain net, texte identique : tu peux l'activer."
            else -> "Gain trop faible pour compter : inutile de l'activer."
        }
        val shown = if (same < n) o.diffs.take(SHOWN_DIFFS) else emptyList()
        val diffs = if (shown.isEmpty()) "" else "\n" + shown.joinToString("\n") { "• « ${it.source} »\n   Normale : ${it.normal}\n   Accélérée : ${it.fast}" }
        return "$times\n$text$failures$diffs\n$verdict"
    }
}
