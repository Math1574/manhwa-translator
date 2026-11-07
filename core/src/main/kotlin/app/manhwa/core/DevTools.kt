package app.manhwa.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Trames WebSocket (RFC 6455) : seulement ce qu'il faut pour parler au serveur de débogage de Chrome. */
object WebSocketFrames {
    private val random = SecureRandom()

    /** Trame émise par un client : toujours masquée. */
    fun clientFrame(opcode: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 14)
        out.write(0x80 or opcode)
        val n = payload.size
        when {
            n < 126 -> out.write(0x80 or n)
            n < 65536 -> { out.write(0x80 or 126); out.write(n ushr 8); out.write(n and 0xFF) }
            else -> { out.write(0x80 or 127); for (i in 7 downTo 0) out.write(((n.toLong() ushr (8 * i)) and 0xFF).toInt()) }
        }
        val mask = ByteArray(4).also { random.nextBytes(it) }
        out.write(mask)
        val masked = ByteArray(n) { (payload[it].toInt() xor mask[it and 3].toInt()).toByte() }
        out.write(masked)
        return out.toByteArray()
    }

    private fun readFully(input: InputStream, n: Int): ByteArray? {
        val b = ByteArray(n); var o = 0
        while (o < n) { val r = input.read(b, o, n - o); if (r < 0) return null; o += r }
        return b
    }

    /**
     * Lit un message complet (les fragments sont recollés). Rend null à la fin du flux ou quand l'autre côté ferme ; un « ping » reçoit sa réponse par [pong].
     * Un message plus gros que [maxBytes] lève une IOException.
     */
    fun readMessage(input: InputStream, pong: (ByteArray) -> Unit, maxBytes: Int = 80 * 1024 * 1024): ByteArray? {
        var acc: ByteArrayOutputStream? = null
        while (true) {
            val b0 = input.read(); if (b0 < 0) return null
            val b1 = input.read(); if (b1 < 0) return null
            val fin = b0 and 0x80 != 0
            val op = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) { val e = readFully(input, 2) ?: return null; len = (((e[0].toInt() and 0xFF) shl 8) or (e[1].toInt() and 0xFF)).toLong() }
            else if (len == 127L) { val e = readFully(input, 8) ?: return null; len = e.fold(0L) { a, x -> (a shl 8) or (x.toLong() and 0xFF) } }
            val key = if (b1 and 0x80 != 0) (readFully(input, 4) ?: return null) else null
            if (len < 0 || len > maxBytes) throw IOException("message trop volumineux ($len octets)")
            val payload = readFully(input, len.toInt()) ?: return null
            if (key != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor key[i and 3].toInt()).toByte()
            when (op) {
                0x8 -> return null
                0x9 -> pong(payload)
                0xA -> {}
                0x0, 0x1, 0x2 -> {
                    if (op != 0x0 || acc == null) acc = ByteArrayOutputStream(maxOf(16, payload.size))
                    acc.write(payload)
                    if (acc.size() > maxBytes) throw IOException("message trop volumineux")
                    if (fin) return acc.toByteArray()
                }
                else -> {}
            }
        }
    }
}

/** Lecteur JSON minimal (objets → Map, tableaux → List, nombres → Double) : le cœur n'a pas accès à org.json. */
object MiniJson {
    fun parse(s: String): Any? {
        val p = Parser(s)
        val v = p.value()
        p.skip()
        if (p.i != s.length) throw IllegalArgumentException("texte en trop après la valeur JSON")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun skip() { while (i < s.length && s[i] <= ' ') i++ }
        fun value(): Any? {
            skip()
            if (i >= s.length) throw IllegalArgumentException("fin inattendue")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { lit("true"); true }
                'f' -> { lit("false"); false }
                'n' -> { lit("null"); null }
                else -> num()
            }
        }
        private fun lit(w: String) { if (!s.startsWith(w, i)) throw IllegalArgumentException("« $w » attendu"); i += w.length }
        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; skip()
            if (s[i] == '}') { i++; return m }
            while (true) {
                skip(); val k = str(); skip()
                if (s[i] != ':') throw IllegalArgumentException("« : » attendu"); i++
                m[k] = value(); skip()
                when (s[i]) { ',' -> i++; '}' -> { i++; return m }; else -> throw IllegalArgumentException("« , » ou « } » attendu") }
            }
        }
        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; skip()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value()); skip()
                when (s[i]) { ',' -> i++; ']' -> { i++; return l }; else -> throw IllegalArgumentException("« , » ou « ] » attendu") }
            }
        }
        private fun str(): String {
            if (s[i] != '"') throw IllegalArgumentException("« \" » attendu"); i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("chaîne non terminée")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        private fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            if (st == i) throw IllegalArgumentException("valeur inattendue « ${s[i]} »")
            return s.substring(st, i).toDouble()
        }
    }
}

/**
 * Client minimal du protocole de débogage de Chrome, pour relire les octets d'une image que la page a DÉJÀ téléchargée : mêmes cookies, mêmes protections,
 * même pile réseau que la WebView, et pas de second téléchargement. Beaucoup de sites refusent (HTTP 403) un téléchargement fait à part par l'application.
 *
 * [open] ouvre une nouvelle connexion au serveur de débogage (sur Android : la prise locale `webview_devtools_remote_<pid>` de la WebView) avec ce délai de lecture (0 = aucun). Rien ici ne contourne une
 * protection : on ne lit que ce que la page a elle-même chargé et affiche.
 */
class CdpClient(private val open: (readTimeoutMs: Int) -> Conn, private val pageHint: () -> String? = { null }) {
    /** [setReadTimeout] : change le délai de lecture de la connexion (0 = aucun) ; [close] la ferme. */
    class Conn(val input: InputStream, val output: OutputStream, val setReadTimeout: (Int) -> Unit = {}, val close: () -> Unit)

    /** Dernier motif d'échec (pour le journal de diagnostic). */
    @Volatile var lastReason: String = ""; private set

    private val lock = Any()
    private var session: Session? = null
    private var failedAt = 0L
    /** Attente avant de réessayer après un échec : double à chaque échec (5 s, 10 s… 2 min), remise à 5 s dès qu'une connexion réussit. */
    private var backoffMs = BACKOFF_START_MS
    private val frames = HashMap<String, String>()
    private var treeAt = 0L

    private inner class Session(val conn: Conn) {
        val nextId = AtomicInteger(1)
        val waiting = ConcurrentHashMap<Int, CompletableFuture<String>>()
        @Volatile var alive = true
        private val writeLock = Any()
        fun send(bytes: ByteArray) = synchronized(writeLock) { conn.output.write(bytes); conn.output.flush() }
        fun start() { Thread({ readLoop() }, "cdp-lecture").also { it.isDaemon = true }.start() }
        private fun readLoop() {
            try {
                while (true) {
                    val m = WebSocketFrames.readMessage(conn.input, { p -> try { send(WebSocketFrames.clientFrame(0xA, p)) } catch (_: IOException) {} }) ?: break
                    val s = String(m, Charsets.UTF_8)
                    if (s.startsWith("{\"id\":")) {
                        var e = 6; while (e < s.length && s[e].isDigit()) e++
                        s.substring(6, e).toIntOrNull()?.let { waiting.remove(it)?.complete(s) }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                alive = false
                waiting.values.forEach { it.completeExceptionally(IOException("connexion fermée")) }
                try { conn.close() } catch (_: Throwable) {}
            }
        }
    }

    private fun readHeaders(input: InputStream): String {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) { val b = input.read(); if (b < 0) throw IOException("réponse tronquée"); sb.append(b.toChar()); if (sb.length > 16384) throw IOException("en-têtes trop longs") }
        return sb.toString()
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val b = ByteArray(n); var o = 0
        while (o < n) { val r = input.read(b, o, n - o); if (r < 0) throw IOException("réponse tronquée"); o += r }
        return b
    }

    private fun readChunked(input: InputStream): ByteArray {
        val body = ByteArrayOutputStream()
        while (true) {
            val line = StringBuilder()
            while (!line.endsWith("\r\n")) { val b = input.read(); if (b < 0) throw IOException("réponse tronquée"); line.append(b.toChar()); if (line.length > 64) throw IOException("taille de morceau illisible") }
            val size = line.toString().trim().substringBefore(';').toIntOrNull(16) ?: throw IOException("taille de morceau illisible")
            if (size == 0) { readExactly(input, 2); return body.toByteArray() }
            body.write(readExactly(input, size)); readExactly(input, 2)
            if (body.size() > MAX_LIST_BYTES) throw IOException("liste des cibles trop longue")
        }
    }

    /**
     * GET sur le serveur de débogage. Chrome garde la connexion ouverte après sa réponse (même avec « Connection: close ») : on lit exactement la longueur annoncée
     * au lieu d'attendre la fermeture, qui n'arrive jamais et ferait patienter jusqu'au délai de lecture à chaque appel.
     */
    private fun httpGet(path: String): String {
        val c = open(HTTP_TIMEOUT_MS)
        try {
            c.output.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nAccept: application/json\r\n\r\n".toByteArray()); c.output.flush()
            val head = readHeaders(c.input)
            if (!head.startsWith("HTTP/1.1 200") && !head.startsWith("HTTP/1.0 200")) throw IOException("réponse ${head.lineSequence().first()}")
            val len = Regex("(?i)content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull()
            val body = when {
                len != null -> { if (len > MAX_LIST_BYTES) throw IOException("liste des cibles trop longue"); readExactly(c.input, len) }
                head.contains("transfer-encoding: chunked", ignoreCase = true) -> readChunked(c.input)
                else -> { val out = ByteArrayOutputStream(); val buf = ByteArray(8192); while (true) { val n = c.input.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > MAX_LIST_BYTES) throw IOException("liste des cibles trop longue") }; out.toByteArray() }
            }
            return String(body, Charsets.UTF_8)
        } finally { try { c.close() } catch (_: Throwable) {} }
    }

    private fun connect(): Session? {
        try {
            @Suppress("UNCHECKED_CAST")
            val targets = (MiniJson.parse(httpGet("/json/list")) as? List<Any?>) ?: throw IOException("liste des cibles illisible")
            val pages = targets.mapNotNull { it as? Map<*, *> }.filter { (it["type"] as? String) == "page" && it["webSocketDebuggerUrl"] is String }
            val hint = pageHint()
            val target = pages.firstOrNull { hint != null && (it["url"] as? String)?.substringBefore('#') == hint.substringBefore('#') } ?: pages.firstOrNull() ?: throw IOException("aucune page à déboguer")
            val wsPath = java.net.URI(target["webSocketDebuggerUrl"] as String).rawPath
            val c = open(HANDSHAKE_TIMEOUT_MS)
            try {
                val key = java.util.Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
                c.output.write("GET $wsPath HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n".toByteArray()); c.output.flush()
                val head = readHeaders(c.input)
                if (!head.contains(" 101 ")) throw IOException("poignée de main refusée : ${head.lineSequence().first()}")
                c.setReadTimeout(0)
            } catch (e: Throwable) { try { c.close() } catch (_: Throwable) {}; throw e }
            val s = Session(c)
            s.start()
            return s
        } catch (e: Throwable) {
            lastReason = "débogage indisponible (${e.message ?: e.javaClass.simpleName})"
            return null
        }
    }

    private fun ensure(): Session? = synchronized(lock) {
        session?.let { if (it.alive) return it }
        session = null
        if (System.currentTimeMillis() - failedAt < backoffMs) return null          // on ne retente pas à chaque image
        fun failed(): Session? { failedAt = System.currentTimeMillis(); backoffMs = minOf(backoffMs * 2, BACKOFF_MAX_MS); return null }
        val s = connect() ?: return failed()
        session = s
        call(s, "Page.enable", null, ENABLE_TIMEOUT_MS) ?: run { dropLocked(s); return failed() }
        backoffMs = BACKOFF_START_MS
        s
    }

    private fun dropLocked(s: Session) { s.alive = false; try { s.conn.close() } catch (_: Throwable) {}; if (session === s) session = null }

    private fun call(s: Session, method: String, params: String?, timeoutMs: Long): String? {
        val id = s.nextId.getAndIncrement()
        val f = CompletableFuture<String>()
        s.waiting[id] = f
        val msg = "{\"id\":$id,\"method\":\"$method\"" + (if (params != null) ",\"params\":$params" else "") + "}"
        try { s.send(WebSocketFrames.clientFrame(1, msg.toByteArray(Charsets.UTF_8))) } catch (e: IOException) { s.waiting.remove(id); synchronized(lock) { dropLocked(s) }; lastReason = "écriture impossible"; return null }
        return try { f.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (e: Throwable) {
            s.waiting.remove(id)
            if (!s.alive) synchronized(lock) { dropLocked(s) }
            lastReason = if (e is java.util.concurrent.TimeoutException) "délai dépassé ($method)" else "connexion perdue ($method)"
            null
        }
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\""); c == '\\' -> sb.append("\\\\"); c < ' ' -> sb.append(String.format("\\u%04x", c.code)); else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun refreshTree(s: Session): Boolean {
        val raw = call(s, "Page.getResourceTree", null, TREE_TIMEOUT_MS) ?: return false
        val tree = try { MiniJson.parse(raw) as? Map<String, Any?> } catch (e: Throwable) { lastReason = "arbre des ressources illisible"; return false } ?: return false
        val ft = ((tree["result"] as? Map<String, Any?>)?.get("frameTree")) as? Map<String, Any?> ?: run { lastReason = "arbre des ressources vide"; return false }
        val fresh = HashMap<String, String>()
        fun walk(node: Map<String, Any?>) {
            val id = ((node["frame"] as? Map<String, Any?>)?.get("id")) as? String ?: return
            (node["resources"] as? List<*>)?.forEach { r -> ((r as? Map<String, Any?>)?.get("url") as? String)?.let { u -> if (u !in fresh) fresh[u] = id } }
            (node["childFrames"] as? List<*>)?.forEach { ch -> (ch as? Map<String, Any?>)?.let { walk(it) } }
        }
        walk(ft)
        synchronized(frames) { frames.clear(); frames.putAll(fresh); treeAt = System.currentTimeMillis() }
        return true
    }

    private fun frameOf(s: Session, url: String): String? {
        fun lookup(): String? = synchronized(frames) { frames[url] ?: frames[url.substringBefore('#')] }
        lookup()?.let { return it }
        if (System.currentTimeMillis() - synchronized(frames) { treeAt } < 600) return null      // l'arbre vient d'être lu : l'image n'y est pas
        if (!refreshTree(s)) return null
        return lookup()
    }

    /** Octets de l'image [url] déjà chargée par la page, ou null (la raison est dans [lastReason]) : l'appelant se rabat alors sur un téléchargement séparé. */
    fun imageBytes(url: String, maxBytes: Int): ByteArray? {
        val s = ensure() ?: return null
        val frame = frameOf(s, url) ?: run { if (lastReason.isEmpty() || lastReason.startsWith("débogage")) lastReason = "image absente des ressources de la page"; return null }
        var attempt = 0
        while (true) {
            val raw = call(s, "Page.getResourceContent", "{\"frameId\":${quote(frame)},\"url\":${quote(url)}}", CONTENT_TIMEOUT_MS) ?: return null
            val k = raw.indexOf("\"content\":\"")
            if (k >= 0 && raw.indexOf("\"base64Encoded\":true") >= 0) {
                val start = k + 11; val end = raw.indexOf('"', start)
                if (end > start) {
                    if ((end - start) / 4L * 3 > maxBytes + 3) { lastReason = "image trop volumineuse"; return null }
                    return try { java.util.Base64.getDecoder().decode(raw.substring(start, end).let { if (it.indexOf('\\') >= 0) it.replace("\\/", "/") else it }) }
                    catch (e: IllegalArgumentException) { lastReason = "contenu illisible"; null }
                }
            }
            // la ressource est connue mais son contenu n'est pas (encore) disponible : image en cours de chargement ; on réessaie brièvement
            if (++attempt >= 3) { lastReason = "contenu indisponible (${raw.take(120)})"; return null }
            Thread.sleep(400)
        }
    }

    fun close() = synchronized(lock) { session?.let { dropLocked(it) } }

    private companion object {
        const val HTTP_TIMEOUT_MS = 2500
        const val HANDSHAKE_TIMEOUT_MS = 3000
        const val ENABLE_TIMEOUT_MS = 3000L
        const val TREE_TIMEOUT_MS = 5000L
        const val CONTENT_TIMEOUT_MS = 10000L
        const val BACKOFF_START_MS = 5000L
        const val BACKOFF_MAX_MS = 120_000L
        const val MAX_LIST_BYTES = 4_000_000
    }
}
