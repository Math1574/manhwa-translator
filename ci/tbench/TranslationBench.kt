package app.manhwa.core

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Banc d'essai de traduction : rejoue des chapitres lus (texte lu de chaque bulle, page par page, dans l'ordre de lecture) avec le même code de traduction que l'application
 * ([LlmTranslator]) et le même modèle, servi par ci/tbench/server.py. Une page à la fois, comme sur le téléphone ; la mémoire du chapitre (et de l'œuvre) suit les pages.
 *
 * Usage : TranslationBench <entrée.tsv> <sortie.tsv> <adresse du serveur> <dossier du lexique> [clé=valeur…]
 * Entrée (une ligne par bulle) : chapitre \t œuvre \t page \t texte lu (réparé) \t texte brut ; synopsis optionnels dans <entrée>.premises (œuvre \t synopsis) ;
 * chapitres déjà lus optionnels dans <entrée sans .tsv>.warm.tsv (mêmes colonnes + traduction du journal) : ils nourrissent la mémoire de l'œuvre sans appeler le modèle,
 * comme si le lecteur les avait lus avant.
 * Sortie : chapitre \t page \t n° \t texte lu \t traduction \t durée de la page (ms) \t détail des appels ; <sortie>.memory : la mémoire de chaque œuvre à la fin.
 * Options : variant=<n> (version du prompt) ; memory=1 (mémoire de l'œuvre et lettres mal lues, comme l'application à partir du prompt 5).
 */
fun main(args: Array<String>) {
    val input = File(args[0]); val output = File(args[1]); val url = args[2]; val lexDir = File(args[3])
    val opts = args.drop(4).mapNotNull { a -> a.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    val lex = EnglishLexicon(File(lexDir, "enable1.txt").readLines(), File(lexDir, "g10k.txt").readLines(), File(lexDir, "genre.txt").readLines().filter { !it.startsWith("#") })
    val premises = File(input.path + ".premises").takeIf { it.exists() }?.readLines()?.mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }?.toMap() ?: emptyMap()
    val useMemory = opts["memory"] == "1"
    val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    fun esc(s: String) = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
        }
        append('"')
    }
    val backend = LlmBackend { system, user, sampling, maxTokens ->
        val body = "{\"system\":${esc(system)},\"user\":${esc(user)},\"top_k\":${sampling.topK},\"top_p\":${sampling.topP},\"temperature\":${sampling.temperature},\"max_tokens\":$maxTokens}"
        val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(20)).header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8)).build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (resp.statusCode() != 200) throw IllegalStateException("serveur : ${resp.statusCode()} ${resp.body().take(200)}")
        LlmReply(resp.body())
    }
    val tr = LlmTranslator(backend) { lex }
    opts["variant"]?.toIntOrNull()?.let { tr.promptVariant = it }

    class Row(val chapter: String, val series: String, val page: String, val clean: String, val src: String, val tr: String = "")
    fun read(f: File) = f.readLines().filter { it.isNotBlank() }.map { l -> val p = l.split('\t'); Row(p[0], p[1], p[2], p[3], p.getOrElse(4) { p[3] }, p.getOrElse(5) { "" }) }
    fun pagesOf(rows: List<Row>): LinkedHashMap<Pair<String, String>, MutableList<Row>> {
        val pages = LinkedHashMap<Pair<String, String>, MutableList<Row>>()
        for (r in rows) pages.getOrPut(r.chapter to r.page) { ArrayList() }.add(r)
        return pages
    }
    val memories = LinkedHashMap<String, SeriesMemory>()
    fun memoryOf(series: String) = memories.getOrPut(series) { SeriesMemory(series) }

    // chapitres déjà lus : lettres mal lues, personnages, registre et genre de l'œuvre appris sur les traductions du journal (aucun appel au modèle)
    val warm = File(input.path.removeSuffix(".tsv") + ".warm.tsv")
    if (useMemory && warm.exists()) {
        val chapterMemory = ChapterMemory(isCommonWord = { w -> lex.isCommon(w) })
        var chapter = ""
        for ((key, blocks) in pagesOf(read(warm))) {
            if (key.first != chapter) { chapter = key.first; chapterMemory.reset() }
            val m = memoryOf(blocks[0].series)
            for (b in blocks) m.observeReading(b.src, lex)
            val texts = blocks.map { TextNormalizer.fixCase(it.clean) }
            texts.indices.forEach { chapterMemory.observe(texts[it], blocks[it].tr) }
            m.observePage(texts, blocks.map { it.tr }, chapterMemory.namesToKeep(), lex)
        }
        memories.forEach { (s, m) -> println("préchauffage de « $s » : ${m.summary()}") }
    }

    val out = output.bufferedWriter()
    var chapter = ""
    val t00 = System.currentTimeMillis()
    for ((key, blocks) in pagesOf(read(input))) {
        if (key.first != chapter) {
            chapter = key.first
            tr.resetContext()
            tr.seriesTitle = blocks[0].series
            tr.seriesPremise = premises[blocks[0].series]
            println("== chapitre $chapter" + (if (useMemory) " (mémoire : ${memoryOf(blocks[0].series).summary()})" else ""))
        }
        // comme l'application : chaque bulle lue nourrit les lettres mal lues de l'œuvre, puis le texte est réparé avec les confusions établies
        val m = if (useMemory) memoryOf(blocks[0].series) else null
        tr.series = m
        val cleans = blocks.map { b ->
            if (m == null) b.clean else {
                val names = tr.chapterNames()
                m.observeReading(b.src, lex)
                val pre = OcrRepair.applyConfusions(b.src, lex, names + m.knownNames(), m.confusions)
                if (pre != b.src) OcrRepair.dropJunkTail(OcrRepair.repair(pre, lex, names), lex) else b.clean
            }
        }
        val texts = cleans.map { TextNormalizer.fixCase(it) }
        tr.beginCallLog()
        val t0 = System.currentTimeMillis()
        val res = try { tr.translate(texts, "en", "fr", emptyList()) } catch (e: Throwable) { println("échec de la page ${key.second} : $e"); null }
        val ms = System.currentTimeMillis() - t0
        val calls = (tr.lastCallDetail() ?: "").replace('\t', ' ').replace('\n', ' ')
        blocks.forEachIndexed { i, b ->
            val clean = cleans[i]
            val t = res?.getOrNull(i)?.let { TranslationEcho.strip(clean, TextNormalizer.matchCase(clean, it), b.src) } ?: "<ÉCHEC>"
            out.write(listOf(key.first, key.second, i.toString(), clean, t, ms.toString(), calls).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') })
            out.newLine()
        }
        out.flush()
        println("page ${key.second} : ${blocks.size} bulles, ${ms / 1000} s (${(System.currentTimeMillis() - t00) / 60000} min au total)")
    }
    out.close()
    if (memories.isNotEmpty()) File(output.path + ".memory").writeText(memories.values.joinToString("\n") { "# ${it.key} : ${it.summary()}\n" + it.serialize() })
}
