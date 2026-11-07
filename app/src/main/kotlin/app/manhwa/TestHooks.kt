package app.manhwa

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.manhwa.core.LlmPrompt
import app.manhwa.core.QualityMode
import app.manhwa.core.SourceLang
import app.manhwa.core.TargetLang
import app.manhwa.core.TranslationEngineChoice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * Crochets de test automatique (builds debug uniquement) : un intent « am start --es mt_url … » charge une page,
 * lance la traduction, attend la fin et écrit un rapport JSON détaillé dans files/report.json.
 */
object TestHooks {
    private const val TAG = "MTTEST"
    @Volatile private var running = false

    fun maybeRun(ctx: Context, graph: AppGraph, intent: Intent) {
        val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable && intent.getStringExtra("mt_lab") != null && !running) { runLab(ctx, graph, intent); return }
        if (debuggable && intent.getStringExtra("mt_adtest") != null && !running) { runAdTest(ctx, graph, intent); return }
        val url = intent.getStringExtra("mt_url")
        if (!debuggable || url == null || running) return
        running = true
        val c = graph.controller
        val main = Handler(Looper.getMainLooper())
        fun s(name: String) = intent.getStringExtra(name)

        graph.settingsStore.update { st ->
            st.copy(
                source = SourceLang.values().firstOrNull { it.name == s("mt_source") } ?: st.source,
                target = TargetLang.values().firstOrNull { it.name == s("mt_target") } ?: st.target,
                engine = TranslationEngineChoice.values().firstOrNull { it.name == s("mt_engine") } ?: st.engine,
                useGpu = s("mt_gpu") == "true",
                diagnostic = false,
                enhancedOcr = s("mt_enhanced") != "false",
                autoTranslate = true,
                readTranslate = true,
                mode = QualityMode.FAST,
                textScale = s("mt_scale")?.toFloatOrNull() ?: st.textScale,
            )
        }
        val timeoutMs = (s("mt_timeout")?.toLongOrNull() ?: 900L) * 1000
        val models = s("mt_models")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val reportName = s("mt_report") ?: "report.json"
        c.testNoDevTools = s("mt_nocdp") == "true"

        thread(name = "mt-test") {
            val t0 = System.currentTimeMillis()
            val notes = JSONArray()
            var timedOut = false
            try {
                // 1) modèles de traduction ML Kit demandés
                for (lang in models) {
                    val t1 = System.currentTimeMillis()
                    main.post { graph.models.download(lang) }
                    while (System.currentTimeMillis() - t1 < 600_000) {
                        Thread.sleep(1500)
                        val st = graph.models.status[lang]
                        if (st == "ready") break
                        if (st != null && st.startsWith("error:")) { notes.put("modèle $lang : $st"); break }
                    }
                    notes.put("modèle $lang : ${graph.models.status[lang]} (${System.currentTimeMillis() - t1} ms)")
                }
                graph.llm.refresh()
                notes.put("gemma prêt : E2B=${graph.llm.ready["gemma4-e2b"]} E4B=${graph.llm.ready["gemma4-e4b"]}")

                // 2) attendre que le navigateur soit créé puis charger la page de test
                var waited = 0
                while (!c.isReady() && waited < 60) { Thread.sleep(500); waited++ }
                c.testing = true
                if (s("mt_fullscreen") == "true") main.post { c.enterFullscreen() }
                val tLoad = System.currentTimeMillis()
                main.post { c.load(url) }

                // 3) attendre la fin de la traduction (compteurs stables)
                var lastSig = ""; var stableSince = System.currentTimeMillis()
                while (System.currentTimeMillis() - tLoad < timeoutMs) {
                    Thread.sleep(2000)
                    val sig = "${c.total}/${c.done}/${c.failed}/${c.loading}"
                    val now = System.currentTimeMillis()
                    if (sig != lastSig) { lastSig = sig; stableSince = now }
                    val finished = !c.loading && c.total > 0 && c.done + c.failed >= c.total && now - stableSince > 8000
                    val nothing = !c.loading && c.total == 0 && now - stableSince > 60_000
                    if (finished || nothing) break
                    if (now - tLoad >= timeoutMs) timedOut = true
                }
                if (System.currentTimeMillis() - tLoad >= timeoutMs) timedOut = true
            } catch (e: Throwable) {
                notes.put("exception : $e")
            }
            val settings = graph.settingsStore.settings
            val report = JSONObject()
                .put("url", url).put("elapsedMs", System.currentTimeMillis() - t0).put("timeout", timedOut)
                .put("settings", JSONObject().put("source", settings.source.name).put("target", settings.target.name).put("engine", settings.engine.name).put("textScale", settings.textScale.toDouble()).put("enhancedOcr", settings.enhancedOcr && graph.paddleModels.available))
                .put("counters", JSONObject().put("total", c.total).put("done", c.done).put("failed", c.failed).put("noText", c.noText).put("lastError", c.lastError ?: ""))
                .put("blocked", graph.adFilters.log.total()).put("finalUrl", c.currentUrl).put("backend", graph.gemma.backendLabel).put("ocrNote", OcrDebug.note)
                .put("notes", notes)
                .put("images", JSONArray(c.testLog.toList()))
            val tmp = File(ctx.filesDir, "$reportName.tmp")
            tmp.writeText(report.toString())
            tmp.renameTo(File(ctx.filesDir, reportName))
            Log.i(TAG, "REPORT_READY $reportName")
            c.testing = false
            running = false
        }
    }


    /**
     * Scénario du bloqueur de publicités : une page piégée (fausses régies sur des domaines « .test » que l'application route vers le faux site local, pop-up, pop-under,
     * redirection automatique, superposition invisible, vrais liens) ; des appuis réels sont envoyés à la WebView. Écrit files/<mt_report>.
     */
    /** Résultat de evaluateJavascript pour une chaîne : un littéral JSON entre guillemets, qui contient lui-même du JSON. */
    private fun parseState(v: String): JSONObject = try { JSONObject((org.json.JSONTokener(v).nextValue() as? String) ?: v) } catch (e: Throwable) { JSONObject() }

    private fun runAdTest(ctx: Context, graph: AppGraph, intent: Intent) {
        running = true
        val c = graph.controller
        val main = Handler(Looper.getMainLooper())
        val reportName = intent.getStringExtra("mt_report") ?: "adtest.json"
        val checks = JSONArray()
        val notes = JSONArray()
        fun check(name: String, ok: Boolean, detail: String = "") { checks.put(JSONObject().put("name", name).put("ok", ok).put("detail", detail)); Log.i(TAG, "ADTEST ${if (ok) "OK" else "ÉCHEC"} $name $detail") }
        fun js(code: String): String {
            val latch = java.util.concurrent.CountDownLatch(1); var out = ""
            main.post { c.evalForTest(code) { v -> out = v ?: ""; latch.countDown() } }
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS); return out
        }
        fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean { val t0 = System.currentTimeMillis(); while (System.currentTimeMillis() - t0 < timeoutMs) { if (cond()) return true; Thread.sleep(250) }; return cond() }
        fun load(url: String) { main.post { c.load(url) }; Thread.sleep(800); waitFor(30_000) { !c.loading }; Thread.sleep(500) }
        fun tap(x: Double, y: Double) { main.post { c.tapForTest(x.toFloat(), y.toFloat()) }; Thread.sleep(1200) }
        fun center(id: String): DoubleArray {
            val r = js("(function(){var e=document.getElementById('$id');if(!e)return '';var r=e.getBoundingClientRect();return (r.left+r.width/2)+','+(r.top+r.height/2)})()").trim('"').split(',')
            return if (r.size == 2) doubleArrayOf(r[0].toDouble(), r[1].toDouble()) else doubleArrayOf(-1.0, -1.0)
        }
        fun logCount(kind: String) = graph.adFilters.log.count(kind)
        val base = "http://10.0.2.2:8000/adtest2.html"
        thread(name = "mt-adtest") {
            try {
                graph.settingsStore.update { it.copy(blockAds = true, adAllowSites = "", adAllowTargets = "", hideBarsWhileReading = false) }
                graph.adFilters.testRules = listOf("||adnet.test^", "||popunder.test^", "||cdn.test/ads/", "/bannieres-generiques/")
                graph.adFilters.rebuild()
                check("moteur : les règles de test sont chargées", waitFor(90_000) { graph.adFilters.engine?.check("http://adnet.test/x.js", "http://10.0.2.2:8000/", app.manhwa.core.ResType.SCRIPT)?.blocked == true },
                    graph.adFilters.summary)
                var waited = 0
                while (!c.isReady() && waited < 120) { Thread.sleep(500); waited++ }
                c.testSiteProxy = true
                c.testing = true
                load(base)
                Thread.sleep(5000)
                val st = js("JSON.stringify(window.__state||{})")
                notes.put("état de la page : $st")
                val state = parseState(st)
                fun flag(name: String) = state.optBoolean(name, false)
                check("réseau : le script publicitaire n'est pas exécuté", !flag("adLoaded") && state.length() > 0, st)
                check("réseau : le cadre publicitaire est vide", !flag("frameLoaded"))
                check("réseau : la bannière d'une régie tierce est remplacée par un pixel", !flag("bannerReal"))
                check("réseau : l'image d'une règle de domaine (cdn.test/ads/) est bloquée", !flag("adImgReal"))
                check("images : les images du chapitre servies par un CDN tiers s'affichent", flag("cdn1Real") && flag("cdn2Real"))
                check("images : une règle générique ne bloque pas une image tierce", flag("genericReal"))
                check("images : l'image du site lui-même s'affiche", flag("sameReal"))
                check("pop-up : window.open par script est neutralisé", logCount("pop-up") >= 1 && flag("popupFake"), "pop-ups journalisés : ${logCount("pop-up")}")
                check("redirection : le changement de site automatique est refusé", c.currentUrl == base && logCount("redirection (liste)") + logCount("redirection") >= 1, c.currentUrl)
                // superposition invisible plein écran : le premier appui la retire sans quitter la page
                val ov0 = logCount("superposition")
                tap(100.0, 400.0)
                check("superposition : un lien invisible plein écran est retiré au premier appui", js("document.getElementById('ov')===null") == "true" && c.currentUrl == base && logCount("superposition") > ov0, c.currentUrl)
                // pop-up par un bouton, avec un vrai geste
                val p0 = logCount("pop-up"); val bp = center("btnPop"); tap(bp[0], bp[1])
                check("pop-up : window.open après un vrai geste est neutralisé", logCount("pop-up") > p0 && c.currentUrl == base, "pop-ups : ${logCount("pop-up")}")
                // vrai lien vers un domaine publicitaire : refusé
                val r0 = logCount("redirection (liste)"); val al = center("adlink"); tap(al[0], al[1])
                check("liste : un lien vers une régie connue est refusé même touché", c.currentUrl == base && logCount("redirection (liste)") > r0, c.currentUrl)
                // vrai lien vers un autre site : autorisé
                val ex = center("ext"); tap(ex[0], ex[1])
                check("lien : un vrai lien visible vers un autre site est suivi", waitFor(8000) { c.currentUrl.startsWith("http://friend.test/") }, c.currentUrl)
                // lien du site en nouvel onglet : suivi dans la page
                load(base); Thread.sleep(2500); tap(100.0, 400.0)
                val nx = center("next"); tap(nx[0], nx[1])
                check("lien : un lien du site en nouvel onglet s'ouvre dans la page", waitFor(8000) { c.currentUrl.endsWith("/adtest2_ch2.html") }, c.currentUrl)
                // bloqueur désactivé pour ce site : les régies redeviennent visibles
                load(base); main.post { c.toggleAdsHere() }; Thread.sleep(1500); waitFor(30_000) { !c.loading }; Thread.sleep(4500)
                val st2 = js("JSON.stringify(window.__state||{})")
                check("site exclu : bloqueur éteint, le script publicitaire s'exécute", parseState(st2).optBoolean("adLoaded", false), st2)
                main.post { c.toggleAdsHere() }; Thread.sleep(1500)
                check("site exclu : on peut le réactiver", graph.settingsStore.settings.adAllowSiteSet.isEmpty())
            } catch (e: Throwable) {
                notes.put("exception : $e"); check("scénario : aucune exception", false, e.toString())
            }
            val report = JSONObject().put("checks", checks).put("notes", notes)
                .put("served", JSONArray(c.testServed.toList())).put("log", graph.adFilters.log.export())
                .put("counts", JSONObject().put("total", graph.adFilters.log.total()).put("popup", graph.adFilters.log.count("pop-up")))
            val tmp = File(ctx.filesDir, "$reportName.tmp"); tmp.writeText(report.toString()); tmp.renameTo(File(ctx.filesDir, reportName))
            Log.i(TAG, "REPORT_READY $reportName")
            c.testSiteProxy = false; c.testing = false; running = false
        }
    }

    /**
     * Banc d'essai de traduction : lit files/lab.json (pages de répliques), traduit chaque page avec chaque variante
     * (v1, v2, v2r = v2 + relecture) et écrit files/lab_<nom>.json. Le contexte de chapitre est conservé d'une page à l'autre.
     */
    private fun runLab(ctx: Context, graph: AppGraph, intent: Intent) {
        running = true
        val name = intent.getStringExtra("mt_lab") ?: "lab"
        val variants = (intent.getStringExtra("mt_variants") ?: "v2").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val engine = TranslationEngineChoice.values().firstOrNull { it.name == intent.getStringExtra("mt_engine") } ?: TranslationEngineChoice.GEMMA_E2B
        graph.settingsStore.update { it.copy(engine = engine, useGpu = false, source = SourceLang.EN, target = TargetLang.FR) }
        thread(name = "mt-lab") {
            val result = JSONObject(); val variantsJson = JSONObject(); val notes = JSONArray()
            try {
                graph.llm.refresh()
                val pages = JSONObject(File(ctx.filesDir, "lab.json").readText()).getJSONArray("pages")
                for (v in variants) {
                    graph.gemma.promptVariant = when { v.startsWith("v1") -> 1; v.startsWith("v3") -> 3; else -> 2 }
                    graph.gemma.refine = v.endsWith("r")
                    graph.gemma.resetContext()
                    graph.gemma.seriesTitle = intent.getStringExtra("mt_series")
                    val out = JSONArray()
                    for (i in 0 until pages.length()) {
                        val raw = (0 until pages.getJSONArray(i).length()).map { pages.getJSONArray(i).getString(it) }
                        val texts = raw.map { app.manhwa.core.TextNormalizer.fixCase(it) } // comme dans le pipeline réel
                        val t0 = System.currentTimeMillis()
                        val tr = try { graph.gemma.translateWithGlossary(texts, "en", "fr", emptyList()) } catch (e: Throwable) { notes.put("$v page $i : $e"); texts.map { "" } }
                        out.put(JSONObject().put("page", i).put("ms", System.currentTimeMillis() - t0).put("src", JSONArray(raw)).put("tr", JSONArray(tr.mapIndexed { k, t -> app.manhwa.core.TextNormalizer.matchCase(raw[k], t) })))
                    }
                    variantsJson.put(v, out)
                    notes.put("variante $v terminée")
                    File(ctx.filesDir, "lab_$name.partial.json").writeText(JSONObject().put("engine", engine.name).put("variants", variantsJson).put("notes", notes).toString())
                }
            } catch (e: Throwable) {
                notes.put("exception : $e")
            }
            graph.gemma.promptVariant = LlmPrompt.CURRENT; graph.gemma.refine = false
            result.put("engine", engine.name).put("variants", variantsJson).put("notes", notes)
            val tmp = File(ctx.filesDir, "lab_$name.json.tmp"); tmp.writeText(result.toString()); tmp.renameTo(File(ctx.filesDir, "lab_$name.json"))
            Log.i(TAG, "REPORT_READY lab_$name.json")
            running = false
        }
    }
}
