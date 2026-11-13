package app.manhwa.core

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files

private fun entry(
    tag: String, notes: String = "", draft: Boolean = false, pre: Boolean = false,
    apk: String? = UpdatePlanner.APK_NAME, url: String? = "https://example.test/$tag/apk", size: Long = 1000L, digest: String? = null,
) = ReleaseEntry(tag, tag, notes, draft, pre, "2026-11-14T00:00:00Z", apk, url, size, digest)

fun updateTests() {
    T.test("mises à jour: numéro de compilation lu dans l'étiquette, les autres étiquettes sont ignorées") {
        T.eq(118, UpdatePlanner.buildOf("build-118")); T.eq(7, UpdatePlanner.buildOf(" build-7 "))
        T.eq(null, UpdatePlanner.buildOf("models-ja-v1")); T.eq(null, UpdatePlanner.buildOf("build-118-rc1")); T.eq(null, UpdatePlanner.buildOf("build-")); T.eq(null, UpdatePlanner.buildOf("v118"))
        T.eq(null, UpdatePlanner.buildOf("build-99999999999"))
    }
    T.test("mises à jour: seules les versions plus récentes, publiées, avec leur APK, la plus récente d'abord") {
        val all = listOf(
            entry("build-116"), entry("build-118"), entry("build-119"), entry("build-121"),
            entry("build-122", draft = true), entry("build-123", pre = true), entry("build-124", apk = "autre.apk"), entry("build-125", url = null), entry("build-126", size = 0),
            entry("models-ja-v1"), entry("build-120"),
        )
        T.eq(listOf("build-121", "build-120", "build-119"), UpdatePlanner.newer(118, all).map { it.tag })
        T.eq(emptyList<String>(), UpdatePlanner.newer(121, all).map { it.tag })     // déjà à jour
        T.eq(emptyList<String>(), UpdatePlanner.newer(500, all).map { it.tag })     // installée plus récente que tout ce qui est publié : aucune « mise à jour » vers le passé
    }
    T.test("mises à jour: un même numéro publié deux fois n'apparaît qu'une fois") {
        T.eq(1, UpdatePlanner.newer(1, listOf(entry("build-5"), entry("build-5"))).size)
    }
    T.test("mises à jour: somme SHA-256 lue dans le champ digest de GitHub") {
        val h = "a".repeat(64)
        T.eq(h, UpdatePlanner.sha256Of("sha256:$h")); T.eq(h, UpdatePlanner.sha256Of("SHA256:" + h.uppercase()))
        T.eq(null, UpdatePlanner.sha256Of(null)); T.eq(null, UpdatePlanner.sha256Of("sha256:abc")); T.eq(null, UpdatePlanner.sha256Of("sha1:$h")); T.eq(null, UpdatePlanner.sha256Of("sha256:" + "g".repeat(64)))
    }
    T.test("mises à jour: nouveautés nettoyées (mentions techniques et liens retirés, lignes vides réduites)") {
        val raw = "Réglages simplifiés\n\n\n- une carte de mise à jour\n- japonais en option\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_x\n\n🤖 Generated with Claude Code\nhttps://claude.ai/code/session_x\n"
        T.eq("Réglages simplifiés\n\n- une carte de mise à jour\n- japonais en option", UpdatePlanner.cleanNotes(raw))
        T.eq("", UpdatePlanner.cleanNotes("\n\nCo-Authored-By: x\n"))
        T.eq("a\nb", UpdatePlanner.cleanNotes("a\r\nb\r\n"))
    }
    T.test("mises à jour: nouveautés trop longues coupées sur une fin de ligne") {
        val raw = (1..40).joinToString("\n") { "- ligne numéro $it avec un peu de texte" }
        val c = UpdatePlanner.cleanNotes(raw, 200)
        T.ok(c.endsWith(" …"), "points de suite"); T.ok(c.length <= 205, "longueur ${c.length}")
        T.ok(c.lines().dropLast(1).all { it.startsWith("- ligne numéro ") && it.endsWith("texte") }, "aucune ligne coupée en deux")
        T.eq("x".repeat(50) + " …", UpdatePlanner.cleanNotes("x".repeat(500), 50))
    }
    T.test("mises à jour: nouveautés de plusieurs versions regroupées, bornées, avec le nombre de versions omises") {
        val list = listOf(entry("build-12", "Douze"), entry("build-11", "Onze\n- point"), entry("build-10", ""))
        val s = UpdatePlanner.combine(list)
        T.eq("Version 12\nDouze\n\nVersion 11\nOnze\n- point\n\nVersion 10\nPas de détail pour cette version.", s)
        val big = (1..30).map { entry("build-$it", "x".repeat(600)) }.reversed()
        val b = UpdatePlanner.combine(big, 700, 2000)
        T.ok(b.length < 2200, "borné: ${b.length}"); T.ok(b.contains("versions plus anciennes"), "mention des versions omises"); T.ok(b.startsWith("Version 30"), "la plus récente d'abord")
    }
    T.test("mises à jour: vérification au plus toutes les six heures") {
        val h = 3600_000L
        T.ok(UpdatePlanner.dueForCheck(10 * h, 0L), "jamais faite")
        T.ok(!UpdatePlanner.dueForCheck(10 * h, 6 * h), "il y a 4 h")
        T.ok(UpdatePlanner.dueForCheck(10 * h, 4 * h), "il y a 6 h")
        T.ok(UpdatePlanner.dueForCheck(3 * h, 8 * h), "horloge revenue en arrière")
    }
    T.test("modules: manifeste lu, lignes vides et commentaires ignorés") {
        val h1 = "a".repeat(64); val h2 = "B".repeat(64)
        val p = PackManifest.parse("# pack japonais\n\nenc.onnx 100 $h1 https://x.test/a/enc.onnx\ndec.onnx   200\t$h2 https://x.test/a/dec.onnx\n")
        T.eq(listOf("enc.onnx", "dec.onnx"), p.map { it.name }); T.eq(listOf(100L, 200L), p.map { it.size }); T.eq("b".repeat(64), p[1].sha256); T.eq("https://x.test/a/dec.onnx", p[1].url)
    }
    T.test("modules: un manifeste mal formé est refusé en entier") {
        val h = "a".repeat(64)
        T.eq(0, PackManifest.parse(null).size); T.eq(0, PackManifest.parse("").size)
        T.eq(0, PackManifest.parse("../evil.onnx 10 $h https://x.test/e").size)             // chemin dans le nom
        T.eq(0, PackManifest.parse("a/b.onnx 10 $h https://x.test/e").size)
        T.eq(0, PackManifest.parse("ok.onnx 0 $h https://x.test/e").size)                   // taille nulle
        T.eq(0, PackManifest.parse("ok.onnx dix $h https://x.test/e").size)
        T.eq(0, PackManifest.parse("ok.onnx 10 abc https://x.test/e").size)                 // somme invalide
        T.eq(0, PackManifest.parse("ok.onnx 10 $h http://x.test/e").size)                   // adresse non sécurisée
        T.eq(0, PackManifest.parse("ok.onnx 10 $h").size)                                   // champ manquant
        T.eq(0, PackManifest.parse("ok.onnx 10 $h https://x.test/e\nok.onnx 11 $h https://x.test/f").size)   // doublon
        T.eq(0, PackManifest.parse("ok.onnx 10 $h https://x.test/e\nmauvaise ligne").size)  // une ligne fautive invalide tout
    }

    // ---- téléchargement de fichier ----
    val data = ByteArray(420_000) { (it * 17 % 251).toByte() }
    val hash = sha256Hex(data)
    fun server(cutAt: Int = -1, ignoreRange: Boolean = false, status: Int = 200): HttpServer {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/f") { ex ->
            if (status != 200) { ex.sendResponseHeaders(status, -1); ex.close(); return@createContext }
            val range = if (ignoreRange) null else ex.requestHeaders.getFirst("Range")
            val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            val body = data.copyOfRange(start, data.size)
            if (range != null) { ex.responseHeaders.add("Content-Range", "bytes $start-${data.size - 1}/${data.size}"); ex.sendResponseHeaders(206, body.size.toLong()) }
            else ex.sendResponseHeaders(200, body.size.toLong())
            val out = ex.responseBody
            if (cutAt >= 0) { out.write(body, 0, minOf(cutAt, body.size)); out.flush(); ex.close() } else { out.write(body); out.close() }
        }
        s.start(); return s
    }
    fun url(s: HttpServer) = "http://127.0.0.1:${s.address.port}/f"
    fun dir(): File = Files.createTempDirectory("dl").toFile()

    T.test("téléchargement: fichier complet vérifié (taille et SHA-256), progression croissante") {
        val s = server(); val d = File(dir(), "a.apk"); val prog = ArrayList<Long>()
        FileDownloader.download(url(s), d, data.size.toLong(), hash, requireHttps = false, onProgress = { done, _ -> prog.add(done) })
        T.ok(d.readBytes().contentEquals(data), "contenu"); T.ok(!File(d.path + ".part").exists(), "fichier temporaire retiré")
        T.eq(data.size.toLong(), prog.last()); T.ok(prog.zipWithNext().all { it.first <= it.second }, "progression croissante")
        s.stop(0)
    }
    T.test("téléchargement: reprise d'un fichier interrompu à partir de ce qui est déjà reçu") {
        val s = server(cutAt = 100_000); val d = File(dir(), "b.apk")
        T.fails<DownloadError>("coupure") { FileDownloader.download(url(s), d, data.size.toLong(), hash, requireHttps = false) }
        s.stop(0)
        val part = File(d.path + ".part"); val partLen = part.length(); T.ok(part.exists() && partLen in 1 until data.size.toLong(), "morceau conservé ($partLen)")
        val s2 = server(); val first = ArrayList<Long>()
        FileDownloader.download(url(s2), d, data.size.toLong(), hash, requireHttps = false, onProgress = { done, _ -> first.add(done) })
        T.ok(first.first() > partLen, "la reprise part de ce qui est déjà reçu (premier point ${first.first()}, reçu $partLen)"); T.ok(d.readBytes().contentEquals(data), "contenu complet après reprise")
        s2.stop(0)
    }
    T.test("téléchargement: serveur qui ignore la reprise, on repart de zéro sans corrompre le fichier") {
        val s = server(ignoreRange = true); val d = File(dir(), "c.apk")
        File(d.path + ".part").writeBytes(ByteArray(5000) { 9 })
        FileDownloader.download(url(s), d, data.size.toLong(), hash, requireHttps = false)
        T.ok(d.readBytes().contentEquals(data), "contenu"); s.stop(0)
    }
    T.test("téléchargement: somme de contrôle fausse, fichier supprimé et erreur") {
        val s = server(); val d = File(dir(), "d.apk")
        T.fails<ChecksumMismatch> { FileDownloader.download(url(s), d, data.size.toLong(), "0".repeat(64), requireHttps = false) }
        T.ok(!d.exists() && !File(d.path + ".part").exists(), "rien ne reste"); s.stop(0)
    }
    T.test("téléchargement: taille annoncée différente du contenu reçu refusée") {
        val s = server(); val d = File(dir(), "e.apk")
        T.fails<DownloadError> { FileDownloader.download(url(s), d, data.size.toLong() + 10, null, requireHttps = false) }
        T.ok(!d.exists(), "pas de fichier final"); s.stop(0)
    }
    T.test("téléchargement: sans somme ni taille connues, le fichier est pris tel quel") {
        val s = server(); val d = File(dir(), "f.apk")
        FileDownloader.download(url(s), d, 0L, null, requireHttps = false); T.ok(d.readBytes().contentEquals(data), "contenu"); s.stop(0)
    }
    T.test("téléchargement: adresse non sécurisée refusée, erreur HTTP et annulation signalées") {
        val s = server(); val d = File(dir(), "g.apk")
        T.fails<DownloadError> { FileDownloader.download(url(s), d, 10L, null) }                                // http refusé par défaut
        s.stop(0)
        val s404 = server(status = 404)
        val e = T.fails<DownloadError> { FileDownloader.download(url(s404), File(dir(), "h.apk"), 10L, null, requireHttps = false) }
        T.ok(e.message!!.contains("404"), "code HTTP"); s404.stop(0)
        val s2 = server(); var n = 0
        val c = T.fails<DownloadError> { FileDownloader.download(url(s2), File(dir(), "i.apk"), data.size.toLong(), hash, requireHttps = false, isCancelled = { ++n > 2 }) }
        T.ok(c.message!!.contains("annulé"), "annulation"); s2.stop(0)
    }
}
