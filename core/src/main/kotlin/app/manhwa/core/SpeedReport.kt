package app.manhwa.core

import java.util.Locale

/** Une réplique dont le texte diffère entre le mode normal et un mode accéléré (pour que l'utilisateur juge lui-même). */
data class LineDiff(val source: String, val normal: String, val fast: String)

/** Un mode accéléré essayé sur le téléphone : durée moyenne, lignes identiques au mode normal, échecs de la copie et lignes qui diffèrent. */
data class SpeedTry(
    val ms: Long,
    /** Lignes (sur [ReuseOutcome.lines]) dont le texte est le même qu'en mode normal. */
    val same: Int,
    /** Essais (sur [tries]) où la copie des consignes a échoué : la traduction normale a pris le relais et son temps est compté dans [ms]. */
    val failed: Int = 0,
    val tries: Int = 0,
    /** Les répliques dont le texte diffère (au plus [SpeedReport.SHOWN_DIFFS] sont montrées). */
    val diffs: List<LineDiff> = emptyList(),
)

/**
 * Résultat d'une comparaison sur le téléphone, avec les mêmes répliques : le mode normal, la copie des consignes avec le moteur tel que l'application le crée
 * ([copy]) et la copie sans décodage spéculatif ([noSpec]). Un mode non essayé vaut null ; [skipped] dit pourquoi.
 */
data class ReuseOutcome(
    val normalMs: Long,
    /** Lignes dont le texte est le même entre deux essais du mode normal : la variation naturelle du modèle d'un essai à l'autre. */
    val sameBaseline: Int,
    val lines: Int,
    val copy: SpeedTry? = null,
    val noSpec: SpeedTry? = null,
    val skipped: List<String> = emptyList(),
)

/** Phrases qui expliquent le résultat de la comparaison, sans jargon : durées, texte identique ou non, et ce que l'on en conclut. */
object SpeedReport {
    /** Gain minimal (en %) pour parler d'un vrai gain. */
    const val MIN_GAIN = 5
    /** Nombre maximal de répliques différentes montrées dans le résultat. */
    const val SHOWN_DIFFS = 3
    const val COPY = "Copie des consignes"
    const val NO_SPEC = "Copie sans décodage spéculatif"

    /** Gain de durée en % (positif : le mode accéléré est plus rapide) ; 0 si les durées sont inutilisables. */
    fun gainPercent(normalMs: Long, fastMs: Long): Int =
        if (normalMs <= 0 || fastMs <= 0) 0 else Math.round((normalMs - fastMs) * 100.0 / normalMs).toInt()

    fun seconds(ms: Long): String = String.format(Locale.FRANCE, "%.1f", ms.coerceAtLeast(0) / 1000.0) + " s"

    private fun delta(normalMs: Long, ms: Long): String {
        val gain = gainPercent(normalMs, ms)
        return when {
            gain >= MIN_GAIN -> " (environ $gain % de moins)"
            gain <= -MIN_GAIN -> " (environ ${-gain} % de plus)"
            else -> " (pas de différence nette)"
        }
    }

    /** Comment l'activer dans les réglages. */
    private fun how(name: String) = if (name == NO_SPEC) "active « Traduction accélérée (essai) » puis « Sans décodage spéculatif »" else "active « Traduction accélérée (essai) »"

    fun describe(o: ReuseOutcome): String {
        val n = o.lines.coerceAtLeast(0)
        val base = o.sameBaseline.coerceIn(0, n)
        val modes = listOfNotNull(o.copy?.let { COPY to it }, o.noSpec?.let { NO_SPEC to it })
        val out = ArrayList<String>()
        out.add("Normale : ${seconds(o.normalMs)}")
        var anyDiffers = false
        for ((name, t) in modes) {
            val same = t.same.coerceIn(0, n)
            if (same < n) anyDiffers = true
            val text = if (same >= n) "texte identique ($n lignes sur $n)" else "texte différent sur ${n - same} ligne(s) sur $n"
            out.add("$name : ${seconds(t.ms)}${delta(o.normalMs, t.ms)} · $text")
            val tries = t.tries.coerceAtLeast(0)
            val failed = t.failed.coerceIn(0, tries)
            if (failed > 0) out.add("   La copie a échoué $failed fois sur $tries (la traduction normale a pris le relais ; ce temps est compté).")
        }
        o.skipped.forEach { out.add(it) }
        if (anyDiffers && base < n) out.add("Le modèle varie aussi d'un essai à l'autre sans l'option ($base lignes sur $n identiques entre deux essais normaux).")
        // les lignes qui changent, pour le premier mode dont le texte diffère
        modes.firstOrNull { (_, t) -> t.same.coerceIn(0, n) < n && t.diffs.isNotEmpty() }?.let { (name, t) ->
            out.add("Lignes qui changent avec « $name » :")
            t.diffs.take(SHOWN_DIFFS).forEach { out.add("• « ${it.source} »\n   Normale : ${it.normal}\n   Accélérée : ${it.fast}") }
        }
        // le mode le plus rapide parmi ceux qui ne ratent pas trop souvent
        val usable = modes.filter { (_, t) ->
            val tries = t.tries.coerceAtLeast(0)
            (tries == 0 || t.failed.coerceIn(0, tries) * 2 < tries) && gainPercent(o.normalMs, t.ms) >= MIN_GAIN
        }
        val best = usable.maxByOrNull { (_, t) -> gainPercent(o.normalMs, t.ms) }
        out.add(when {
            modes.isEmpty() -> "Aucun mode accéléré n'a pu être essayé sur ce téléphone : laisse l'essai désactivé."
            best == null -> "Aucun mode accéléré n'est assez plus rapide que le mode normal sur ce téléphone, ou la copie échoue trop souvent : laisse l'essai désactivé."
            else -> {
                val (name, t) = best
                val same = t.same.coerceIn(0, n)
                val tries = t.tries.coerceAtLeast(0)
                val failed = t.failed.coerceIn(0, tries)
                val despite = if (failed > 0) " malgré ${if (failed > 1) "ces échecs" else "cet échec"}" else ""
                when {
                    same >= n -> "Le plus rapide : « $name »$despite, texte identique : tu peux l'utiliser (${how(name)})."
                    base >= n -> "Le plus rapide : « $name »$despite, mais le texte change : compare les lignes ci-dessus. L'application ne juge pas la qualité : utilise-le seulement si l'accélérée te paraît aussi bonne (${how(name)})."
                    else -> "Le modèle varie déjà un peu sans l'option : refais l'essai pour en juger."
                }
            }
        })
        return out.joinToString("\n")
    }
}
