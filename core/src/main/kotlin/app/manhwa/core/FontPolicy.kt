package app.manhwa.core

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Police du texte traduit, choisie UNE fois par page (et mémorisée par série) au lieu de bloc par bloc.
 *
 * Avant : chaque bloc faisait concourir treize polices en comparant leur forme à l'encre d'origine, avec épaississement, inclinaison et déformation en largeur libres.
 * Cette mesure, très sensible au halo, au contour et aux erreurs de lecture, donnait une police différente d'un bloc à l'autre d'une même page, souvent une police
 * très grasse et peu lisible (Londrina, Lilita) ; il n'y avait aucune mémoire d'une page à l'autre.
 *
 * Maintenant : un petit jeu de familles lisibles en français, un vote pondéré sur toutes les lignes de la page, une mémoire de la série qui ne change de famille
 * que si une autre l'emporte nettement, aucun épaississement ni déformation, et l'italique seulement si l'original l'est vraiment, sur toute la page.
 */
object FontPolicy {
    /** Familles candidates pour le texte ordinaire (parole, narration, texte libre) : lettrage comique fin ou rond, sans empattement moyen, gras condensé, condensé très gras. */
    val palette = listOf("comicneue", "noto-600-85", "noto-700-80", "anton")
    /**
     * Préférence a priori, ajoutée au score moyen : à ressemblance égale, la police comique par défaut (lisible, proche du lettrage de bulle) l'emporte sur un sans empattement,
     * qui l'emporte sur un gras condensé, lui-même préféré au très gras. Une famille plus grasse ne l'emporte donc que si elle ressemble nettement mieux à l'original.
     */
    private val prior = doubleArrayOf(0.08, 0.02, 0.0, 0.0)
    const val ITALIC = "comicneue-italic"
    const val DEFAULT = "comicneue"
    /** En dessous de ce score moyen, aucune famille ne ressemble : la famille par défaut est conservée (et non une famille fine ou grasse au hasard). */
    const val MIN_SCORE = 0.08f

    class Choice(val family: String, val scores: Map<String, Float>, val italic: Boolean)

    /** Somme pondérée des scores d'une famille (et poids total) : permet de cumuler les pages d'une série. */
    class Sums(val sum: DoubleArray, var weight: Double)

    /**
     * Mémoire par série : scores cumulés (décroissants d'une page à l'autre) de chaque famille et famille actuelle. Une autre famille ne la remplace que si son score moyen
     * dépasse nettement celui de la famille actuelle ([margin]).
     */
    class Memory(private val decay: Double = 0.85, private val margin: Float = 0.04f, private val maxSeries: Int = 64) {
        private class State(val sums: DoubleArray, var weight: Double, var current: String?, var italicVotes: Double, var pages: Double)
        private val states = object : LinkedHashMap<String, State>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State>?): Boolean = size > maxSeries
        }

        @Synchronized fun decide(series: String?, page: Sums, pageItalic: Float?): Pair<String, Boolean> {
            if (series == null) return Pair(best(page.sum, page.weight, null), (pageItalic ?: 0f) >= 0.7f)
            val st = states.getOrPut(series) { State(DoubleArray(palette.size), 0.0, null, 0.0, 0.0) }
            for (i in st.sums.indices) st.sums[i] = st.sums[i] * decay + page.sum[i]
            st.weight = st.weight * decay + page.weight
            if (pageItalic != null) { st.italicVotes = st.italicVotes * decay + pageItalic; st.pages = st.pages * decay + 1.0 }
            val chosen = best(st.sums, st.weight, st.current)
            st.current = chosen
            val italic = st.pages > 0 && st.italicVotes / st.pages >= 0.7
            return Pair(chosen, italic)
        }

        private fun best(sum: DoubleArray, weight: Double, current: String?): String {
            if (weight <= 0.0) return current ?: DEFAULT
            var bi = 0; var bm = Double.NEGATIVE_INFINITY; var raw = 0.0
            for (i in sum.indices) { val m = sum[i] / weight + prior[i]; if (m > bm) { bm = m; bi = i; raw = sum[i] / weight } }
            if (raw < MIN_SCORE) return current ?: DEFAULT
            if (current != null) {
                val ci = palette.indexOf(current)
                if (ci >= 0 && ci != bi && bm - (sum[ci] / weight + prior[ci]) < margin) return current
            }
            return palette[bi]
        }

        @Synchronized fun forget() = states.clear()
    }

    /**
     * Vote d'une page : score moyen de chaque famille sur ses lignes (au plus [maxLines], les plus grosses d'abord ; poids = racine de la surface d'encre).
     * @return cumul des scores et du poids (à confier à [Memory.decide]), et la part de lignes inclinées (null si on n'a pas pu la mesurer).
     */
    fun vote(lines: List<LineInk>, renderer: GlyphRenderer, maxLines: Int = 14): Pair<Sums, Float?> {
        val used = lines.sortedByDescending { it.w.toLong() * it.h }.take(maxLines)
        val sum = DoubleArray(palette.size); var weight = 0.0
        for (l in used) {
            val w = sqrt(max(1.0, l.mask.count { it }.toDouble()))
            var ok = false
            for ((i, f) in palette.withIndex()) { val sc = FontMatcher.lineScore(l, renderer, f) ?: continue; sum[i] += w * sc; ok = true }
            if (ok) weight += w
        }
        // italique : au moins 3 lignes mesurables, et au moins 70 % d'entre elles nettement inclinées vers la droite (une mesure bruitée ne suffit jamais)
        val shears = used.mapNotNull { FontMatcher.shear(it) }
        val italicShare = if (shears.size < 3) null else shears.count { it <= -0.14f && it >= -0.45f }.toFloat() / shears.size
        return Pair(Sums(sum, weight), italicShare)
    }

    /** Famille retenue pour une page : vote, mémoire de la série, puis italique si la page entière l'est (et que la famille retenue est la comique). */
    fun choose(lines: List<LineInk>, renderer: GlyphRenderer, memory: Memory?, series: String?): Choice {
        val (sums, italicShare) = vote(lines, renderer)
        val mem = memory ?: Memory()
        val (family, italic) = mem.decide(series, sums, italicShare)
        val scores = palette.withIndex().associate { (i, f) -> f to (if (sums.weight > 0) (sums.sum[i] / sums.weight).toFloat() else 0f) }
        val italicPage = italic && (italicShare ?: 0f) >= 0.7f
        return Choice(if (italicPage && family == "comicneue") ITALIC else family, scores, italicPage)
    }
}
