package app.manhwa.core

import java.util.Properties

enum class SourceLang(val code: String) { AUTO("auto"), JA("ja"), KO("ko"), ZH_HANS("zh-Hans"), ZH_HANT("zh-Hant"), EN("en") }
enum class TargetLang(val code: String) { FR("fr"), EN("en"), ES("es"), DE("de") }
enum class QualityMode { FAST, ENHANCED }

/** Valeurs permises pour l'apparence (une valeur inconnue retombe sur la valeur par défaut). */
object ThemeChoices {
    val accents = listOf("violet", "indigo", "rose", "emeraude", "ambre")
    val backgrounds = listOf("crepuscule", "nuit", "noir")
    val mascots = listOf("feuille", "chat", "hibou", "dragon", "nuage")
}

data class AppSettings(
    val source: SourceLang = SourceLang.AUTO,
    val target: TargetLang = TargetLang.FR,
    val textScale: Float = 1.0f,
    val autoTranslate: Boolean = true,
    val mode: QualityMode = QualityMode.FAST,
    val maxPrefetchPages: Int = 3,
    val cacheMaxMb: Int = 50,
    val engine: TranslationEngineChoice = TranslationEngineChoice.MLKIT,
    val useGpu: Boolean = false,
    val diagnostic: Boolean = false,
    /** Second moteur de lecture (PaddleOCR) : repère le texte stylisé ou hors bulles que ML Kit rate. */
    val enhancedOcr: Boolean = true,
    /** Relecture par le modèle (qualité maximale, environ deux fois plus lent). */
    val refinePass: Boolean = false,
    /** Blocage des publicités, fenêtres intempestives et redirections dans le navigateur. */
    val blockAds: Boolean = true,
    /** Relecture automatique des textes douteux par Gemma (modèle de vision) quand elle a été validée par le test. */
    val autoReread: Boolean = true,
    /** Traduire aussi les onomatopées (bruitages). Désactivé : elles restent telles quelles dans l'image. */
    val translateSfx: Boolean = false,
    /**
     * Traduire pendant la lecture (bouton Original | Traduit) : activé, les pages de chapitre sont traduites dès leur ouverture et pendant le défilement ;
     * les autres pages du site (accueil, listes d'œuvres) ne le sont pas. Mémorisé entre les lancements.
     */
    val readTranslate: Boolean = false,
    /** Position du bouton Original | Traduit : bord (droit ou gauche) et hauteur (0 = en haut, 1 = en bas). Il se déplace au doigt. */
    val orbRight: Boolean = true,
    val orbY: Float = 0.42f,
    /** Masque la barre d'adresse et la barre d'onglets quand on lit en descendant ; elles reviennent en remontant ou d'un appui sur la page. */
    val hideBarsWhileReading: Boolean = true,
    /** Apparence : couleur d'accent (violet, indigo, rose, emeraude, ambre), fond (crepuscule, nuit, noir) ; animations réduites ; illustrations de la mascotte. */
    val themeAccent: String = "violet",
    val themeBackground: String = "crepuscule",
    val reducedMotion: Boolean = false,
    val showMascot: Boolean = true,
    /** Mascotte des en-têtes : esprit-feuille, chat lofi, hibou savant, bébé dragon ou nuage rêveur. */
    val themeMascot: String = "feuille",
    /** Sites (domaines enregistrables, séparés par des virgules) sur lesquels le bloqueur est désactivé, et sites vers lesquels une page peut renvoyer sans confirmation. */
    val adAllowSites: String = "",
    val adAllowTargets: String = "",
    /** Tri de la bibliothèque (identifiant d'un [SeriesSort]) : mémorisé entre les lancements. */
    val librarySort: String = "recent",
    /** Accueil : sites épinglés (adresses d'accueil séparées par des espaces) et sites retirés de l'accueil (clés séparées par des virgules). */
    val homeSites: String = "",
    val homeHidden: String = "",
) {
    val adAllowSiteSet: Set<String> get() = adAllowSites.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val adAllowTargetSet: Set<String> get() = adAllowTargets.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    /** Ramène les valeurs dans des bornes valides. */
    fun sanitized() = copy(
        textScale = textScale.coerceIn(0.5f, 2.0f),
        maxPrefetchPages = maxPrefetchPages.coerceIn(0, 10),
        cacheMaxMb = cacheMaxMb.coerceIn(10, 500),
        orbY = orbY.coerceIn(0.04f, 0.96f),
    )

    fun serialize(): String = Properties().also {
        it["source"] = source.name; it["target"] = target.name; it["textScale"] = textScale.toString()
        it["autoTranslate"] = autoTranslate.toString(); it["mode"] = mode.name
        it["maxPrefetchPages"] = maxPrefetchPages.toString(); it["cacheMaxMb"] = cacheMaxMb.toString()
        it["schema"] = "2"; it["engine"] = engine.name; it["useGpu"] = useGpu.toString(); it["diagnostic"] = diagnostic.toString(); it["enhancedOcr"] = enhancedOcr.toString(); it["refinePass"] = refinePass.toString(); it["blockAds"] = blockAds.toString(); it["autoReread"] = autoReread.toString(); it["translateSfx"] = translateSfx.toString()
        it["themeAccent"] = themeAccent; it["themeBackground"] = themeBackground; it["reducedMotion"] = reducedMotion.toString(); it["showMascot"] = showMascot.toString(); it["themeMascot"] = themeMascot; it["adAllowSites"] = adAllowSites; it["adAllowTargets"] = adAllowTargets
        it["readTranslate"] = readTranslate.toString(); it["orbRight"] = orbRight.toString(); it["orbY"] = orbY.toString(); it["hideBarsWhileReading"] = hideBarsWhileReading.toString(); it["librarySort"] = librarySort; it["homeSites"] = homeSites; it["homeHidden"] = homeHidden
    }.let { p -> java.io.StringWriter().also { w -> p.store(w, null) }.toString() }

    companion object {
        /** Tolérant : une valeur absente ou invalide retombe sur la valeur par défaut. */
        fun parse(data: String?): AppSettings {
            val d = AppSettings()
            if (data.isNullOrBlank()) return d
            val p = Properties().apply { try { load(data.reader()) } catch (e: Exception) { return d } }
            fun <T : Enum<T>> en(name: String, all: Array<T>, def: T) = all.firstOrNull { it.name == p.getProperty(name) } ?: def
            return AppSettings(
                en("source", SourceLang.values(), d.source), en("target", TargetLang.values(), d.target),
                p.getProperty("textScale")?.toFloatOrNull() ?: d.textScale,
                if (p.getProperty("schema") == "2") (p.getProperty("autoTranslate")?.toBooleanStrictOrNull() ?: d.autoTranslate) else true, // anciens réglages : lecture sans interaction par défaut
                en("mode", QualityMode.values(), d.mode),
                p.getProperty("maxPrefetchPages")?.toIntOrNull() ?: d.maxPrefetchPages,
                p.getProperty("cacheMaxMb")?.toIntOrNull() ?: d.cacheMaxMb,
                en("engine", TranslationEngineChoice.values(), d.engine),
                p.getProperty("useGpu")?.toBooleanStrictOrNull() ?: d.useGpu,
                p.getProperty("diagnostic")?.toBooleanStrictOrNull() ?: d.diagnostic,
                p.getProperty("enhancedOcr")?.toBooleanStrictOrNull() ?: d.enhancedOcr,
                p.getProperty("refinePass")?.toBooleanStrictOrNull() ?: d.refinePass,
                p.getProperty("blockAds")?.toBooleanStrictOrNull() ?: d.blockAds,
                p.getProperty("autoReread")?.toBooleanStrictOrNull() ?: d.autoReread,
                p.getProperty("translateSfx")?.toBooleanStrictOrNull() ?: d.translateSfx,
                p.getProperty("readTranslate")?.toBooleanStrictOrNull() ?: d.readTranslate,
                p.getProperty("orbRight")?.toBooleanStrictOrNull() ?: d.orbRight,
                p.getProperty("orbY")?.toFloatOrNull() ?: d.orbY,
                p.getProperty("hideBarsWhileReading")?.toBooleanStrictOrNull() ?: d.hideBarsWhileReading,
                p.getProperty("themeAccent")?.takeIf { it in ThemeChoices.accents } ?: d.themeAccent,
                p.getProperty("themeBackground")?.takeIf { it in ThemeChoices.backgrounds } ?: d.themeBackground,
                p.getProperty("reducedMotion")?.toBooleanStrictOrNull() ?: d.reducedMotion,
                p.getProperty("showMascot")?.toBooleanStrictOrNull() ?: d.showMascot,
                p.getProperty("themeMascot")?.takeIf { it in ThemeChoices.mascots } ?: d.themeMascot,
                p.getProperty("adAllowSites") ?: d.adAllowSites,
                p.getProperty("adAllowTargets") ?: d.adAllowTargets,
                p.getProperty("librarySort")?.takeIf { v -> SeriesSort.values().any { it.id == v } } ?: d.librarySort,
                p.getProperty("homeSites") ?: d.homeSites,
                p.getProperty("homeHidden") ?: d.homeHidden,
            ).sanitized()
        }
    }
}
