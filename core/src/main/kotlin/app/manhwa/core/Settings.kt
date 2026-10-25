package app.manhwa.core

import java.util.Properties

enum class SourceLang(val code: String) { AUTO("auto"), JA("ja"), KO("ko"), ZH_HANS("zh-Hans"), ZH_HANT("zh-Hant"), EN("en") }
enum class TargetLang(val code: String) { FR("fr"), EN("en"), ES("es"), DE("de") }
enum class QualityMode { FAST, ENHANCED }

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
) {
    /** Ramène les valeurs dans des bornes valides. */
    fun sanitized() = copy(
        textScale = textScale.coerceIn(0.5f, 2.0f),
        maxPrefetchPages = maxPrefetchPages.coerceIn(0, 10),
        cacheMaxMb = cacheMaxMb.coerceIn(10, 500),
    )

    fun serialize(): String = Properties().also {
        it["source"] = source.name; it["target"] = target.name; it["textScale"] = textScale.toString()
        it["autoTranslate"] = autoTranslate.toString(); it["mode"] = mode.name
        it["maxPrefetchPages"] = maxPrefetchPages.toString(); it["cacheMaxMb"] = cacheMaxMb.toString()
        it["schema"] = "2"; it["engine"] = engine.name; it["useGpu"] = useGpu.toString(); it["diagnostic"] = diagnostic.toString(); it["enhancedOcr"] = enhancedOcr.toString()
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
            ).sanitized()
        }
    }
}
