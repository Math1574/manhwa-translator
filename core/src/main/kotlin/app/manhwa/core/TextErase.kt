package app.manhwa.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Effacement du texte d'origine : repérage des pixels de texte, remplissage du fond, choix de la police. */
object TextErase {

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    fun dist(a: Int, c: Int) = maxOf(abs(r(a) - r(c)), abs(g(a) - g(c)), abs(b(a) - b(c)))
    private fun pack(rr: Int, gg: Int, bb: Int) = (0xFF shl 24) or (rr.coerceIn(0, 255) shl 16) or (gg.coerceIn(0, 255) shl 8) or bb.coerceIn(0, 255)

    class Analysis(
        /** Fond estimé (médiane de l'anneau). */
        val bg: Int,
        /** Part de l'anneau proche du fond : 1 = aplat uni (bulle), proche de 0 = dessin. */
        val uniformity: Float,
        /** Couleur dominante du texte d'origine. */
        val textColor: Int,
        /** Pixels de texte (halo et contour compris, dilatés). */
        val mask: BooleanArray,
        /** Vrai si le texte d'origine semble cerné d'un contour/lueur de couleur différente. */
        val outlined: Boolean,
        /** Part de la zone de texte (hors lettres) qui a exactement la couleur du fond : 1 = vrai aplat. */
        val zoneFlat: Float = 0f,
        /** Seuil de distance au fond au-delà duquel un pixel est considéré comme du texte. */
        val threshold: Int = 40,
        /** Part de la zone de texte qui a la couleur du fond : une bulle a surtout du fond, un lettrage épais sur dessin en a peu. */
        val bgShare: Float = 1f,
    )

    /** Masque rectangulaire (coordonnées de la région) : x0, y0, x1, y1 exclus ; sert à limiter l'effacement aux lignes de texte. */
    fun rectMask(w: Int, h: Int, rects: List<IntArray>, inflate: Int): BooleanArray {
        val m = BooleanArray(w * h)
        for (r0 in rects) {
            val x0 = (r0[0] - inflate).coerceIn(0, w); val y0 = (r0[1] - inflate).coerceIn(0, h)
            val x1 = (r0[2] + inflate).coerceIn(0, w); val y1 = (r0[3] + inflate).coerceIn(0, h)
            for (y in y0 until y1) for (x in x0 until x1) m[y * w + x] = true
        }
        return m
    }

    /** Pixels des composantes connexes (8 voisins) qui ne touchent pas le bord de la région et n'en occupent pas plus de la moitié. */
    fun enclosedOnly(src: BooleanArray, w: Int, h: Int): BooleanArray {
        val seen = BooleanArray(w * h); val out = BooleanArray(w * h); val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!src[start] || seen[start]) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true; var border = false
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border = true
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (src[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (!border && tail < w * h / 2) for (k in 0 until tail) out[queue[k]] = true
        }
        return out
    }

    /** Garde les composantes connexes de [src] dont la boîte englobante tient dans [maxW] × [maxH] : une lettre ou un mot, pas un contour de bulle qui l'entoure. */
    fun limitSize(src: BooleanArray, w: Int, h: Int, maxW: Int, maxH: Int): BooleanArray {
        val seen = BooleanArray(w * h); val out = BooleanArray(w * h); val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!src[start] || seen[start]) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
            var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (src[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (x1 - x0 + 1 <= maxW && y1 - y0 + 1 <= maxH) for (k in 0 until tail) out[queue[k]] = true
        }
        return out
    }

    /** Pixels dont la couleur s'écarte de plus de [thr] du fond [bg]. */
    fun farMask(pixels: IntArray, bg: Int, thr: Int): BooleanArray = BooleanArray(pixels.size) { dist(pixels[it], bg) > thr }

    /**
     * Pixels qui s'écartent nettement de leur voisinage (moyenne locale sur un carré de côté 2·[radius]+1) : lettres et traits fins,
     * mais pas un dégradé lent. Sert quand le fond n'est pas uni.
     */
    fun localFarMask(pixels: IntArray, w: Int, h: Int, radius: Int, thr: Int): BooleanArray {
        val integ = Array(3) { LongArray((w + 1) * (h + 1)) }
        for (c in 0..2) {
            val sh = 16 - 8 * c
            for (y in 0 until h) { var row = 0L; for (x in 0 until w) { row += (pixels[y * w + x] shr sh) and 0xFF; integ[c][(y + 1) * (w + 1) + x + 1] = integ[c][y * (w + 1) + x + 1] + row } }
        }
        return BooleanArray(w * h) { i ->
            val x = i % w; val y = i / w
            val x0 = max(0, x - radius); val y0 = max(0, y - radius); val x1 = min(w, x + radius + 1); val y1 = min(h, y + radius + 1)
            val n = (x1 - x0) * (y1 - y0)
            var far = false
            for (c in 0..2) {
                val sum = integ[c][y1 * (w + 1) + x1] - integ[c][y0 * (w + 1) + x1] - integ[c][y1 * (w + 1) + x0] + integ[c][y0 * (w + 1) + x0]
                val v = (pixels[i] shr (16 - 8 * c)) and 0xFF
                if (abs(v - (sum / n).toInt()) > thr) far = true
            }
            far
        }
    }

    /**
     * Bouche les petits trous d'un masque : les composantes (4 voisins) de pixels hors du masque qui ne touchent pas le bord de la région et couvrent au plus [maxArea] pixels. Des pixels
     * isolés d'un bord adouci (pointes et sommets des lettres) restaient hors de l'effacement : leurs voisins n'étaient remplacés qu'en partie (bord adouci du masque) et ces points
     * ressortaient en fragments pointillés sous la traduction ; ils servaient aussi de références au remplissage.
     */
    fun fillHoles(mask: BooleanArray, w: Int, h: Int, maxArea: Int): BooleanArray {
        val out = mask.copyOf(); val seen = BooleanArray(w * h); val stack = IntArray(w * h)
        val members = IntArray(maxArea + 1)
        for (start in 0 until w * h) {
            if (mask[start] || seen[start]) continue
            var sp = 0; var n = 0; var border = false
            stack[sp++] = start; seen[start] = true
            while (sp > 0) {
                val i = stack[--sp]; if (n < members.size) members[n] = i; n++
                val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border = true
                if (x > 0 && !mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && !mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && !mask[i - w] && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && !mask[i + w] && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            if (!border && n <= maxArea) for (k in 0 until n) out[members[k]] = true
        }
        return out
    }

    /** Érosion (4 voisins) : retire [times] fois les pixels qui ont un voisin hors du masque (ou hors de l'image). */
    fun erode(src: BooleanArray, w: Int, h: Int, times: Int): BooleanArray {
        var cur = src
        repeat(times) {
            val nx = cur.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (!cur[i]) continue
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1 || !cur[i - 1] || !cur[i + 1] || !cur[i - w] || !cur[i + w]) nx[i] = false
            }
            cur = nx
        }
        return cur
    }

    fun dilate(src: BooleanArray, w: Int, h: Int, times: Int, limit: BooleanArray? = null): BooleanArray {
        var cur = src
        repeat(times) {
            val nx = cur.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (cur[i] || (limit != null && !limit[i])) continue
                if ((x > 0 && cur[i - 1]) || (x < w - 1 && cur[i + 1]) || (y > 0 && cur[i - w]) || (y < h - 1 && cur[i + w])) nx[i] = true
            }
            cur = nx
        }
        return cur
    }

    /**
     * Halo clair autour des lettres effacées sur un fond blanc : le bruit de compression et la lueur grise que le masque serré (lettres seules) laissait en « fantôme »
     * de la forme des lettres. Les pixels clairs, proches du fond, à moins de [reach] px du masque rejoignent le masque ; jamais ceux qui touchent un trait sombre
     * (contour de case ou de bulle, dessin) ni ceux d'une zone [avoid]. Sans effet si le fond n'est pas presque blanc.
     */
    /**
     * Vrai si le fond est un blanc franc tout autour du masque : sur l'anneau de 5 à 14 px qui l'entoure (hors zones [avoid]), 97 % des pixels sont à moins de 6 niveaux du fond.
     * Un dégradé, un ciel clair ou un contour dans l'anneau l'exclut : le nettoyage des halos ne doit servir que sur un blanc réellement uni, sans quoi il laisse des aplats visibles.
     */
    fun ringIsPlainWhite(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, bg: Int, avoid: BooleanArray?): Boolean {
        if (r(bg) < 235 || g(bg) < 235 || b(bg) < 235) return false
        val inner = dilate(mask, w, h, 5); val outer = dilate(mask, w, h, 14)
        var n = 0; var plain = 0
        for (i in pixels.indices) if (outer[i] && !inner[i] && (avoid == null || !avoid[i])) { n++; if (dist(pixels[i], bg) <= 6) plain++ }
        return n >= 200 && plain >= 0.97f * n
    }

    fun lightHalo(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, bg: Int, reach: Int, avoid: BooleanArray?): BooleanArray {
        if (r(bg) < 235 || g(bg) < 235 || b(bg) < 235) return mask
        fun lum(c: Int) = (r(c) * 299 + g(c) * 587 + b(c) * 114) / 1000
        // traits à préserver : pixels francs (nettement plus sombres que le fond) qui ne font pas partie du masque, élargis de 3 px
        val structure = BooleanArray(w * h) { !mask[it] && dist(pixels[it], bg) > 90 }
        val keep = dilate(structure, w, h, 3)
        val near = dilate(mask, w, h, reach)
        val out = mask.copyOf()
        for (i in out.indices) {
            if (out[i] || !near[i] || keep[i] || (avoid != null && avoid[i])) continue
            val c = pixels[i]
            if (lum(c) >= 185 && dist(c, bg) <= 75) out[i] = true
        }
        return out
    }

    /**
     * @param pixels région ARGB (w*h)
     * @param allowed zone où le texte peut se trouver (lignes OCR agrandies) : rien n'est effacé en dehors
     * Le fond est la couleur dominante de la zone ; l'uniformité est mesurée sur le cadre de 6 px autour de la zone ([ignoreFrame] : parties du cadre à ne pas compter, blocs voisins connus).
     */
    fun analyze(pixels: IntArray, w: Int, h: Int, allowed: BooleanArray, grow: Int = 2, ignoreFrame: BooleanArray? = null): Analysis {
        // fond : couleur dominante (quantifiée) dans la zone autorisée, affinée par la médiane des pixels proches
        val buckets = HashMap<Int, Int>()
        for (i in pixels.indices) if (allowed[i]) { val c = pixels[i]; val k = ((r(c) shr 4) shl 8) or ((g(c) shr 4) shl 4) or (b(c) shr 4); buckets[k] = (buckets[k] ?: 0) + 1 }
        val mode = buckets.maxByOrNull { it.value }?.key ?: 0
        val approx = pack(((mode shr 8) and 0xF) * 16 + 8, ((mode shr 4) and 0xF) * 16 + 8, (mode and 0xF) * 16 + 8)
        val near = ArrayList<Int>()
        for (i in pixels.indices) if (allowed[i] && dist(pixels[i], approx) <= 24) near.add(pixels[i])
        val bg = if (near.isEmpty()) approx else ColorMath.median(near.toIntArray())
        val allowedCount = allowed.count { it }.coerceAtLeast(1)
        val bgShare = near.size.toFloat() / allowedCount

        val frame = dilate(allowed, w, h, 6)
        var fc = 0; var fn = 0
        // le texte d'un bloc voisin (autre moitié du même paragraphe, par exemple) n'est pas du bruit de fond : il est ignoré dans la mesure
        for (i in pixels.indices) if (frame[i] && !allowed[i] && (ignoreFrame == null || !ignoreFrame[i])) { fc++; if (dist(pixels[i], bg) <= 14) fn++ }
        val uniformity = if (fc == 0) 1f else fn.toFloat() / fc

        val hist = IntArray(256)
        var total = 0
        for (i in pixels.indices) if (allowed[i]) { hist[dist(pixels[i], bg)]++; total++ }
        total = total.coerceAtLeast(1)
        var sumAll = 0L; for (i in 0 until 256) sumAll += i.toLong() * hist[i]
        var wB = 0; var sumB = 0L; var best = 0.0; var thr = 40
        for (t in 0 until 256) {
            wB += hist[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB; val mF = (sumAll - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }
        thr = thr.coerceIn(36, 120)

        val raw = BooleanArray(w * h)
        for (i in pixels.indices) if (allowed[i] && dist(pixels[i], bg) > thr) raw[i] = true

        // les lettres sont des composantes isolées ; contour de bulle et dessin extérieur touchent le bord de la région : on les ignore
        val enclosed = enclosedOnly(raw, w, h)
        val rawE = if (enclosed.any { it }) enclosed else raw
        val far = ArrayList<Int>()
        for (i in rawE.indices) if (rawE[i]) far.add(pixels[i])
        far.sortByDescending { dist(it, bg) }
        val top = far.take(max(1, far.size / 4))
        val textColor = if (top.isEmpty()) ColorMath.textColorFor(bg) else pack(top.sumOf { r(it) } / top.size, top.sumOf { g(it) } / top.size, top.sumOf { b(it) } / top.size)

        val rawCount = rawE.count { it }
        var other = 0
        for (i in rawE.indices) if (rawE[i] && dist(pixels[i], textColor) > 90) other++
        val outlined = rawCount > 0 && other.toFloat() / rawCount > 0.25f

        val mask = dilate(raw, w, h, if (outlined) grow + max(3, grow) else grow, allowed)
        // aplat vrai : hors masque, la zone autorisée a (presque) exactement la couleur du fond
        var rest = 0; var flat = 0
        for (i in pixels.indices) if (allowed[i] && !mask[i]) { rest++; if (dist(pixels[i], bg) <= 14) flat++ }
        val flatness = if (rest == 0) 1f else flat.toFloat() / rest
        return Analysis(bg, if (flatness >= 0.92f && uniformity >= 0.85f) 0.9f else min(uniformity, 0.6f), textColor, mask, outlined, flatness, thr, bgShare)
    }

    /**
     * Pixels de texte dans une bulle : composantes connexes de pixels éloignés du fond qui ne touchent pas le bord de la région
     * (le contour de la bulle et le dessin extérieur y sont reliés, pas les lettres) et dont une partie est dans [allowed].
     * La composante entière est effacée, même si la lettre dépasse un peu de la boîte de l'OCR.
     */
    fun textComponents(
        pixels: IntArray, w: Int, h: Int, bg: Int, thr: Int, allowed: BooleanArray, grow: Int,
        interior: BooleanArray? = null, core: BooleanArray? = null, thin: BooleanArray? = null, smallDim: Int = Int.MAX_VALUE,
        /** Fond et lettres entourées, sans refus de fuite : borne ce que l'on efface d'une composante reliée au bord (voir plus bas). Par défaut : [interior]. */
        reach: BooleanArray? = null,
        /** Reçoit, pour un texte incliné, la zone hors de laquelle rien ne doit être effacé (l'élargissement des lettres ne doit pas en sortir) ; sinon reste null. */
        limitOut: Array<BooleanArray?>? = null,
    ): BooleanArray {
        val raw = BooleanArray(w * h) { dist(pixels[it], bg) > thr }
        val seen = BooleanArray(w * h)
        val keep = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val maxArea = w * h / 2
        // Texte incliné (axe des lettres isolées en diagonale, entre 8° et 80°) : la boîte de lecture d'un texte incliné est un grand rectangle qui englobe du décor hors de la bulle
        // (étiquette blanche inclinée sur un fond sombre). Dans ce cas seulement, ce qui est relié au bord n'est effacé que s'il touche l'intérieur de la bulle (à 1 px près) : le décor
        // sombre n'est plus repeint en blanc. Pour un texte horizontal ou vertical, rien ne change (une lettre soudée au contour est effacée comme avant).
        val reachSrc = reach ?: interior
        val encAll = if (core != null && reachSrc != null) enclosedOnly(raw, w, h) else null
        val diagonal = encAll != null && diagonalAxis(BooleanArray(w * h) { encAll[it] && core!![it] }, w, h)
        val interiorNear = if (diagonal) reachSrc?.let { dilate(it, w, h, 1) } else null
        if (limitOut != null && limitOut.isNotEmpty()) limitOut[0] = interiorNear
        Erasure.trace?.invoke("texte incliné : ${if (diagonal) "oui, effacement des composantes reliées au bord limité à l'intérieur de la bulle" else "non"}")
        // boîte englobante du cœur du texte : une composante qui la dépasse largement n'est pas du texte seul (contour de bulle soudé aux lettres)
        var cx0 = w; var cx1 = -1; var cy0 = h; var cy1 = -1
        if (core != null) for (i in 0 until w * h) if (core[i]) { val x = i % w; val y = i / w; if (x < cx0) cx0 = x; if (x > cx1) cx1 = x; if (y < cy0) cy0 = y; if (y > cy1) cy1 = y }
        val coreW = cx1 - cx0 + 1; val coreH = cy1 - cy0 + 1
        for (start in 0 until w * h) {
            if (!raw[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var touchesBorder = false; var inAllowed = false; var inInterior = 0; var allowedN = 0; var outsideCore = 0; var inCoreN = 0
            var bx0 = w; var bx1 = -1; var by0 = h; var by1 = -1
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesBorder = true
                if (allowed[i]) { inAllowed = true; allowedN++ }
                if (core != null) { if (core[i]) inCoreN++ else outsideCore++ }
                if (interior != null && interior[i]) inInterior++
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (raw[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            // une vraie lettre (ou un mot) est pour l'essentiel dans la zone du texte ; une composante dont la majeure partie est hors de la zone est un contour de bulle
            // (ou un dessin) auquel des lettres se touchent : on n'en efface que le cœur des lignes de texte, jamais le contour entier
            val mostlyInZone = allowedN * 100 >= tail * 55
            val enclosed = !touchesBorder && tail < maxArea && (mostlyInZone || inInterior * 2 >= tail && allowedN > 0 && tail < 4 * allowedN)
            // composante qui déborde largement du cœur du texte : lettres épaisses soudées à un contour de bulle (fin). On sépare par ouverture morphologique : ce qui est plus
            // fin que le trait des lettres (le contour, la queue) n'est jamais du texte à effacer ; les lettres gardent leur forme.
            // un arc de contour de bulle (coupé par le seuil de détection) n'est pas du texte, même si la zone de lecture, élargie, le recouvre : une composante n'est du texte
            // que si elle touche vraiment le cœur des lignes, ou si elle est petite (ponctuation, accent)
            if (core != null && coreW > 0) {
                val small = (bx1 - bx0 + 1) <= smallDim && (by1 - by0 + 1) <= smallDim
                if (inCoreN * 100 < tail * 15 && !small) { if (thin != null) for (k in 0 until tail) thin[queue[k]] = true; continue }
            }
            val beyondCore = core != null && coreW > 0 && ((bx1 - bx0 + 1) > coreW * 1.25f + 6 || (by1 - by0 + 1) > coreH * 1.25f + 6)
            if (core != null && tail >= 400 && beyondCore && outsideCore * 100 >= tail * 35) {
                val comp = BooleanArray(w * h); val inCore = BooleanArray(w * h)
                for (k in 0 until tail) { val q = queue[k]; comp[q] = true; if (core[q]) inCore[q] = true }
                val sw = strokeWidth(inCore, w, h).let { if (it < 3f) strokeWidth(comp, w, h) else it }
                val r = (sw * 0.3f).toInt().coerceIn(2, 8)
                val opened = dilate(erode(comp, w, h, r), w, h, r, comp)
                // dans une bulle, seul ce qui touche son intérieur est du texte : un grand décor sombre qui dépasse de la bulle (ici, derrière une étiquette inclinée dont la boîte de lecture
                // est un grand rectangle) survit à l'ouverture comme une lettre épaisse, était effacé puis repeint en blanc
                for (k in 0 until tail) { val q = queue[k]; if (opened[q] && (interiorNear == null || interiorNear[q])) keep[q] = true else thin?.set(q, true) }
                continue
            }
            when {
                // lettre isolée : effacée entièrement, même si elle dépasse la boîte de l'OCR (mot omis, lettre débordante)
                enclosed && (inAllowed || inInterior * 2 >= tail) -> for (k in 0 until tail) keep[queue[k]] = true
                // composante reliée au bord ou à un contour (lettre collée au contour) : seulement le cœur des lignes de texte, et, dans une bulle, seulement ce qui touche son intérieur
                // (à 3 px près : une lettre soudée au contour y touche). La boîte de lecture d'un texte incliné est un grand rectangle qui englobe du décor sombre hors de la bulle :
                // ce décor n'est pas du texte, il était effacé puis repeint en blanc
                inAllowed && core != null -> for (k in 0 until tail) if (core[queue[k]] && (interiorNear == null || interiorNear[queue[k]])) keep[queue[k]] = true
            }
        }
        return dilate(keep, w, h, grow)
    }

    /** Épaisseur moyenne du trait (pixels) d'un masque de texte : 2 × aire / périmètre (exact pour un trait fin de longueur ≫ épaisseur). */
    /** Part de sa boîte englobante que couvre un masque (0 si vide). Un lettrage réel en couvre 30 à 60 % ; plus de 70 %, c'est une plaque. */
    fun boxFill(mask: BooleanArray, w: Int, h: Int): Float {
        var minX = w; var maxX = -1; var minY = h; var maxY = -1; var n = 0
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x]) { n++; if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
        if (n == 0) return 0f
        return n.toFloat() / ((maxX - minX + 1).toLong() * (maxY - minY + 1)).toFloat()
    }

    /**
     * Lettrage creux : lettres bleues cernées d'un fin trait blanc, dont l'encre détectée est l'anneau du contour et non le remplissage. Mesurée telle quelle, la graisse paraît minuscule
     * (la police choisie était alors très fine). Les creux de l'encre qui ne communiquent pas avec le bord de la région et dont la couleur n'est pas celle du fond ([bg], à [tol] près)
     * sont le remplissage des lettres : on les ajoute. Ceux qui ont la couleur du fond (l'intérieur d'un O, d'un D) restent ouverts ; un creux plus grand que [maxArea] pixels n'est pas rempli.
     */
    fun solidify(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, bg: Int, tol: Int = 60, maxArea: Int = Int.MAX_VALUE): BooleanArray {
        val outside = BooleanArray(w * h); val stack = IntArray(w * h); var sp = 0
        fun push(i: Int) { if (!mask[i] && !outside[i]) { outside[i] = true; stack[sp++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]; val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        val out = mask.copyOf(); val seen = BooleanArray(w * h)
        for (start in 0 until w * h) {
            if (mask[start] || outside[start] || seen[start]) continue
            // composante de creux
            val members = ArrayList<Int>(); sp = 0; stack[sp++] = start; seen[start] = true
            while (sp > 0) {
                val i = stack[--sp]; members.add(i); val x = i % w; val y = i / w
                for (d in 0 until 4) {
                    val nx = x + (if (d == 0) -1 else if (d == 1) 1 else 0); val ny = y + (if (d == 2) -1 else if (d == 3) 1 else 0)
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (!mask[j] && !outside[j] && !seen[j]) { seen[j] = true; stack[sp++] = j }
                }
            }
            if (members.size > maxArea) continue
            var far = 0; for (i in members) if (dist(pixels[i], bg) > tol) far++
            if (far * 2 > members.size) for (i in members) out[i] = true      // plus de la moitié du creux n'a pas la couleur du fond : c'est le remplissage d'une lettre
        }
        return out
    }

    /**
     * Cœur des lettres d'un lettrage cerné d'un halo ou d'un contour : [mask] ne contient que le remplissage des lettres, [color] est sa couleur, [haloColor] celle du halo
     * (0 s'il n'y en a pas) et [haloPx] son épaisseur moyenne en pixels échantillonnés.
     * Le texte d'origine peut être noir cerné de blanc sur un ciel sombre : la couleur « extrême » est alors le blanc du halo, et la traduction était dessinée à l'envers
     * (blanc cerné de noir) ; l'encre comptée avec son halo faisait aussi paraître les lettres bien plus grasses qu'elles ne sont.
     * Le cœur est la classe de luminosité dont les pixels touchent le moins l'extérieur de l'encre (les lettres sont entourées par le halo, qui touche le fond).
     */
    class LetterCore(val mask: BooleanArray, val color: Int, val haloColor: Int, val haloPx: Float)

    fun letterCore(pixels: IntArray, w: Int, h: Int, ink: BooleanArray, bg: Int, textColor: Int): LetterCore? {
        var n = 0; for (v in ink) if (v) n++
        if (n < 40) return null
        // un seul ton (pas de halo net) : tout est du texte, et la couleur reste celle que le plan a déjà estimée (0 = inconnue ici)
        fun single(): LetterCore = LetterCore(ink, 0, 0, 0f)
        fun meanColor(sel: (Int) -> Boolean): Int {
            var rr = 0L; var gg = 0L; var bb = 0L; var c = 0
            for (i in ink.indices) if (ink[i] && sel(i)) { rr += r(pixels[i]); gg += g(pixels[i]); bb += b(pixels[i]); c++ }
            return if (c == 0) 0 else pack((rr / c).toInt(), (gg / c).toInt(), (bb / c).toInt())
        }
        // profondeur de chaque pixel d'encre (distance 4-voisinage au plus proche pixel hors de l'encre)
        val depth = IntArray(w * h) { if (ink[it]) 1 shl 20 else 0 }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; if (!ink[i]) continue
            depth[i] = minOf(depth[i], if (x == 0) 1 else depth[i - 1] + 1, if (y == 0) 1 else depth[i - w] + 1)
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x; if (!ink[i]) continue
            depth[i] = minOf(depth[i], if (x == w - 1) 1 else depth[i + 1] + 1, if (y == h - 1) 1 else depth[i + w] + 1)
        }
        // cœur = pixels d'encre de la couleur du texte (estimée plus haut, lettres noires cernées de blanc comprises) ; on garde les composantes qui ont une vraie épaisseur :
        // le lissage des bords, l'anneau mince de fond que le masque englobe parfois autour du halo n'en sont pas
        val near = BooleanArray(w * h) { ink[it] && dist(pixels[it], textColor) <= 70 }
        var nearN = 0; for (v in near) if (v) nearN++
        if (nearN < 0.15 * n) return single()
        var dmax = 0; for (i in near.indices) if (near[i] && depth[i] > dmax) dmax = depth[i]
        val deepMin = maxOf(2, (0.35 * dmax).toInt())
        val core = BooleanArray(w * h); val stack = IntArray(w * h); val seen = BooleanArray(w * h)
        for (start in near.indices) {
            if (!near[start] || seen[start]) continue
            var sp = 0; stack[sp++] = start; seen[start] = true
            val members = ArrayList<Int>(); var deepest = 0
            while (sp > 0) {
                val i = stack[--sp]; members.add(i); if (depth[i] > deepest) deepest = depth[i]
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (near[j] && !seen[j]) { seen[j] = true; stack[sp++] = j }
                }
            }
            if (deepest >= deepMin) for (i in members) core[i] = true
        }
        var coreN = 0; var perim = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (!core[y * w + x]) continue
            coreN++
            if (x == 0 || !core[y * w + x - 1]) perim++
            if (x == w - 1 || !core[y * w + x + 1]) perim++
            if (y == 0 || !core[(y - 1) * w + x]) perim++
            if (y == h - 1 || !core[(y + 1) * w + x]) perim++
        }
        if (coreN < 20) return single()
        // halo : le reste de l'encre relié au cœur (ce qui n'y touche pas n'est pas son halo)
        val halo = BooleanArray(w * h); var sp = 0
        for (i in core.indices) if (core[i]) {
            val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (ink[j] && !core[j] && !halo[j]) { halo[j] = true; stack[sp++] = j }
            }
        }
        while (sp > 0) {
            val i = stack[--sp]; val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (ink[j] && !core[j] && !halo[j]) { halo[j] = true; stack[sp++] = j }
            }
        }
        var haloN = 0; for (v in halo) if (v) haloN++
        var deepN = 0; for (i in core.indices) if (core[i] && depth[i] >= 2) deepN++
        val coreColor = if (deepN >= 20) meanColor { i -> core[i] && depth[i] >= 2 } else meanColor { i -> core[i] }
        // sans halo net (moins d'un dixième de l'encre) : les lettres sont toute l'encre, la mesure du cœur vaut celle de l'encre
        if (haloN < 0.10 * n) return LetterCore(ink, coreColor, 0, 0f)
        var haloDeep = 0; for (i in halo.indices) if (halo[i] && depth[i] >= 2) haloDeep++
        val haloRef = if (haloDeep >= 20) meanColor { i -> halo[i] && depth[i] >= 2 } else meanColor { i -> halo[i] }
        // le cœur ne gardait que les pixels très proches de la couleur du texte : les bords adoucis des lettres (un pixel de chaque côté) étaient comptés dans le halo, ce qui amincissait
        // le trait mesuré (donc la graisse, et le choix de la police). On rattache au cœur les pixels de halo au contact d'une lettre qui sont plus proches de la couleur des lettres que de celle du halo.
        fun d2(a: Int, b: Int): Int { val dr = r(a) - r(b); val dg = g(a) - g(b); val db = b(a) - b(b); return dr * dr + dg * dg + db * db }
        repeat(2) {
            val grow = ArrayList<Int>()
            for (i in halo.indices) if (halo[i]) {
                val x = i % w; val y = i / w; var touch = false
                for (dy in -1..1) { for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    if (core[ny * w + nx]) { touch = true }
                } }
                if (touch && d2(pixels[i], coreColor) < d2(pixels[i], haloRef)) grow.add(i)
            }
            for (i in grow) { core[i] = true; halo[i] = false }
        }
        coreN = 0; perim = 0; haloN = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (halo[y * w + x]) haloN++
            if (!core[y * w + x]) continue
            coreN++
            if (x == 0 || !core[y * w + x - 1]) perim++
            if (x == w - 1 || !core[y * w + x + 1]) perim++
            if (y == 0 || !core[(y - 1) * w + x]) perim++
            if (y == h - 1 || !core[(y + 1) * w + x]) perim++
        }
        val haloColor = meanColor { i -> halo[i] }
        return LetterCore(core, coreColor, haloColor, if (perim == 0) 0f else haloN.toFloat() / perim)
    }

    /**
     * Vrai si les lettres isolées (composantes de [letters], d'au moins 30 px) sont alignées en diagonale : au moins 3 composantes, nuage de centres allongé, axe entre 8° et 80°
     * par rapport à l'horizontale.
     */
    fun diagonalAxis(letters: BooleanArray, w: Int, h: Int): Boolean {
        val seen = BooleanArray(w * h); val queue = IntArray(w * h)
        val cx = ArrayList<Double>(); val cy = ArrayList<Double>()
        for (start in 0 until w * h) {
            if (!letters[start] || seen[start]) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
            var sx = 0.0; var sy = 0.0
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w; sx += x; sy += y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (letters[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (tail >= 30) { cx.add(sx / tail); cy.add(sy / tail) }
        }
        if (cx.size < 3) return false
        val mx = cx.average(); val my = cy.average()
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (k in cx.indices) { val dx = cx[k] - mx; val dy = cy[k] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        val tr = sxx + syy; val det = sxx * syy - sxy * sxy
        val disc = Math.sqrt(Math.max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc; val l2 = tr / 2 - disc
        if (l1 < 4 * Math.max(l2, 1e-6)) return false                       // nuage rond : pas d'axe
        val angle = Math.abs(Math.toDegrees(0.5 * Math.atan2(2 * sxy, sxx - syy)))
        return angle in 8.0..80.0
    }

    fun strokeWidth(mask: BooleanArray, w: Int, h: Int): Float {
        var area = 0; var perim = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            area++
            if (x == 0 || !mask[y * w + x - 1]) perim++
            if (x == w - 1 || !mask[y * w + x + 1]) perim++
            if (y == 0 || !mask[(y - 1) * w + x]) perim++
            if (y == h - 1 || !mask[(y + 1) * w + x]) perim++
        }
        return if (perim == 0) 0f else 2f * area / perim
    }

    private fun lum(c: Int) = (0.299f * r(c) + 0.587f * g(c) + 0.114f * b(c)) / 255f

    /**
     * Couleur du remplissage des lettres sur un dessin : l'extrême de luminance (très clair ou très foncé) le plus éloigné de la médiane
     * de la zone. Le lettrage est presque toujours l'extrême : lettres blanches à contour sombre, ou noires sur fond clair.
     */
    /** Profondeur maximale (distance 4-voisinage au plus proche pixel hors de [sel]) d'un masque : l'épaisseur des plus grosses surfaces pleines. */
    private fun maxDepth(sel: BooleanArray, w: Int, h: Int): Int {
        val d = IntArray(w * h) { if (sel[it]) 1 shl 20 else 0 }
        for (y in 0 until h) for (x in 0 until w) { val i = y * w + x; if (!sel[i]) continue; d[i] = minOf(d[i], if (x == 0) 1 else d[i - 1] + 1, if (y == 0) 1 else d[i - w] + 1) }
        var best = 0
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) { val i = y * w + x; if (!sel[i]) continue; d[i] = minOf(d[i], if (x == w - 1) 1 else d[i + 1] + 1, if (y == h - 1) 1 else d[i + w] + 1); if (d[i] > best) best = d[i] }
        return best
    }

    /** Épaisseur typique des surfaces pleines d'un masque : 90e centile de la profondeur (distance 4-voisinage au bord) de ses pixels. Une surface isolée ne la fait pas, contrairement au maximum. */
    internal fun depthP90(sel: BooleanArray, w: Int, h: Int): Int {
        val d = IntArray(w * h) { if (sel[it]) 1 shl 20 else 0 }
        for (y in 0 until h) for (x in 0 until w) { val i = y * w + x; if (!sel[i]) continue; d[i] = minOf(d[i], if (x == 0) 1 else d[i - 1] + 1, if (y == 0) 1 else d[i - w] + 1) }
        val hist = IntArray(256); var tot = 0
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) { val i = y * w + x; if (!sel[i]) continue; d[i] = minOf(d[i], if (x == w - 1) 1 else d[i + 1] + 1, if (y == h - 1) 1 else d[i + w] + 1); hist[min(255, d[i])]++; tot++ }
        if (tot == 0) return 0
        var acc = 0; for (k in 0 until 256) { acc += hist[k]; if (acc * 10 >= tot * 9) return k }
        return 255
    }

    /**
     * Pixels de [mask] qui appartiennent à une forme fermée : composante connexe (8 voisins) qui ne touche ni le bord de [allowed] (la zone du texte élargie) ni le bord de l'image.
     * Les lettres sont des formes fermées dans la zone du texte ; le fond, une marge blanche, un contour de case, une cape ou une fleur qui passent derrière s'étendent au-delà.
     */
    internal fun enclosedIn(mask: BooleanArray, allowed: BooleanArray, w: Int, h: Int): BooleanArray {
        val n = w * h
        val out = BooleanArray(n); val seen = BooleanArray(n); val stack = IntArray(n); val comp = IntArray(n)
        for (st in 0 until n) {
            if (!mask[st] || seen[st]) continue
            var sp = 0; var cn = 0; var open = false
            stack[sp++] = st; seen[st] = true
            while (sp > 0) {
                val i = stack[--sp]; comp[cn++] = i
                val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) open = true
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (!allowed[j]) { open = true; continue }
                    if (mask[j] && !seen[j]) { seen[j] = true; stack[sp++] = j }
                }
            }
            if (!open) for (k in 0 until cn) out[comp[k]] = true
        }
        return out
    }

    /** (expérience) Composantes connexes d'un masque ayant la taille d'une lettre : nombre et part de la surface du masque qu'elles couvrent. */
    fun letterLike(mask: BooleanArray, w: Int, h: Int, lh: Int): Pair<Int, Float> {
        val seen = BooleanArray(w * h); val stack = IntArray(w * h); var total = 0; var letterArea = 0; var count = 0
        for (st in 0 until w * h) {
            if (!mask[st] || seen[st]) continue
            var sp = 0; stack[sp++] = st; seen[st] = true
            var minX = w; var maxX = 0; var minY = h; var maxY = 0; var area = 0
            while (sp > 0) {
                val i = stack[--sp]; val x = i % w; val y = i / w; area++
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (mask[j] && !seen[j]) { seen[j] = true; stack[sp++] = j }
                }
            }
            total += area
            val ch = maxY - minY + 1; val cw = maxX - minX + 1
            val fillRatio = area.toFloat() / (ch * cw)
            if (ch >= 0.3f * lh && ch <= 1.5f * lh && cw >= 0.05f * lh && cw <= 3f * lh && fillRatio in 0.15f..0.95f) { count++; letterArea += area }
        }
        return count to (if (total == 0) 0f else letterArea.toFloat() / total)
    }

    /** Arbitrage par l'épaisseur : le masque dont les surfaces pleines sont les plus épaisses (d'au moins 30 %, et 4 px) est celui des lettres ; sinon [current]. */
    private fun depthPick(dMask: BooleanArray, bMask: BooleanArray, w: Int, h: Int, dd: Float, db: Float, current: Boolean, allowed: BooleanArray? = null): Boolean {
        // seulement les formes fermées quand il y en a : une marge ou un fond qui traverse la boîte n'est pas une surface de lettre, même épaisse
        fun closed(m: BooleanArray): BooleanArray = if (allowed == null) m else enclosedIn(m, allowed, w, h).let { c -> if (c.count { it } >= 40) c else m }
        val dDeep = maxDepth(closed(dMask), w, h); val bDeep = maxDepth(closed(bMask), w, h)
        Erasure.trace?.invoke("couleur du texte : sombre ${"%.2f".format(dd)} (épaisseur $dDeep px) / clair ${"%.2f".format(db)} (épaisseur $bDeep px)")
        return if (dDeep >= 1.3f * bDeep && dDeep >= 4) false else if (bDeep >= 1.3f * dDeep && bDeep >= 4) true else current
    }

    /**
     * Couleur des lettres d'origine : l'extrême de luminosité (très clair ou très foncé) le plus éloigné de la médiane. Quand les deux extrêmes sont loin de la médiane
     * (lettres noires cernées d'une lueur blanche sur un ciel sombre, ou l'inverse), le plus éloigné est souvent la lueur : les lettres sont alors celles dont les pixels extrêmes,
     * dans les boîtes de lecture ([tight], région [w] × [h]), forment les plus grosses surfaces pleines (la lueur est mince).
     */
    fun extremeTextColor(pixels: IntArray, allowed: BooleanArray, tight: BooleanArray? = null, w: Int = 0, h: Int = 0, lineH: Int = 0): Int {
        val idx = pixels.indices.filter { allowed[it] }
        if (idx.isEmpty()) return 0xFF000000.toInt()
        val sorted = idx.sortedBy { lum(pixels[it]) }
        val n = max(1, sorted.size / 10)
        val darkIdx = sorted.take(n); val brightIdx = sorted.takeLast(n)
        val dark = darkIdx.map { pixels[it] }; val bright = brightIdx.map { pixels[it] }
        val med = lum(pixels[sorted[sorted.size / 2]])
        val dd = med - dark.map { lum(it) }.average().toFloat(); val db = bright.map { lum(it) }.average().toFloat() - med
        var pickBright = db >= dd
        var decided = false
        // 0) formes fermées (journal du 23/11) : les lettres sont des formes fermées dans la zone du texte, le fond s'étend au-delà. Lettres blanches à lueur rose sur un mur clair : la
        //    médiane est claire, l'extrême clair à peine plus clair (le blanc passait pour le fond, toute la boîte était effacée, fleur comprise) ; texte noir à contour blanc qui déborde sur la
        //    marge blanche au-dessus d'une case : la marge épaisse passait pour les lettres (le bord de la case était effacé, le texte ni penché ni dans sa police). La part des pixels de chaque
        //    extrême, dans les boîtes de lecture, qui appartiennent à une forme fermée départage quand l'écart est net.
        if (tight != null && w > 0 && h > 0 && pixels.size == w * h && dd >= 0.08f && db >= 0.08f) {
            val darkRef = (med - dd); val brightRef = (med + db)
            val dAll = BooleanArray(w * h); val bAll = BooleanArray(w * h)
            val dWin = min(0.12f, 0.5f * dd); val bWin = min(0.12f, 0.5f * db)
            for (i in idx) { val l = lum(pixels[i]); if (l <= darkRef + dWin) dAll[i] = true; if (l >= brightRef - bWin) bAll[i] = true }
            val dIn = enclosedIn(dAll, allowed, w, h); val bIn = enclosedIn(bAll, allowed, w, h)
            var dT = 0; var dE = 0; var bT = 0; var bE = 0
            for (i in idx) if (tight[i]) { if (dAll[i]) { dT++; if (dIn[i]) dE++ }; if (bAll[i]) { bT++; if (bIn[i]) bE++ } }
            if (dT >= 40 && bT >= 40) {
                val cd = dE.toFloat() / dT; val cb = bE.toFloat() / bT
                Erasure.trace?.invoke("couleur du texte : part dans des formes fermées : sombre ${"%.2f".format(cd)} / clair ${"%.2f".format(cb)}")
                if (cd >= 0.45f && cd - cb >= 0.30f) { pickBright = false; decided = true }
                else if (cb >= 0.45f && cb - cd >= 0.30f) { pickBright = true; decided = true }
                else if (cd >= 0.45f && cb >= 0.45f) {
                    // les deux forment des formes fermées : les lettres et leur contour (ou leur lueur). Les lettres sont entourées par l'autre extrême (texte noir dans son contour blanc,
                    // lettres blanches dans leur lueur rose) : en partant de leur bord vers l'extérieur, on rencontre l'autre extrême en quelques pixels, dans toutes les directions.
                    // Le contour, lui, ne l'est qu'à moitié : son bord extérieur donne sur le fond. Part des départs (bord, direction vers l'extérieur) qui atteignent l'autre extrême.
                    val n = w * h
                    val reach = (lineH / 6).coerceIn(4, 12)
                    fun surround(x: BooleanArray, y: BooleanArray): Float {
                        var rays = 0; var hits = 0
                        for (i in 0 until n) {
                            if (!x[i]) continue
                            val x0 = i % w; val y0 = i / w
                            for (k in 0..3) {
                                val dx = if (k == 0) 1 else if (k == 1) -1 else 0; val dy = if (k == 2) 1 else if (k == 3) -1 else 0
                                var cx = x0 + dx; var cy = y0 + dy
                                if (cx < 0 || cy < 0 || cx >= w || cy >= h) continue
                                if (x[cy * w + cx] || !allowed[cy * w + cx]) continue
                                rays++
                                var st = 1
                                while (st <= reach && cx >= 0 && cy >= 0 && cx < w && cy < h) {
                                    val q = cy * w + cx
                                    if (!allowed[q] || x[q]) break
                                    if (y[q]) { hits++; break }
                                    cx += dx; cy += dy; st++
                                }
                            }
                        }
                        return if (rays < 40) -1f else hits.toFloat() / rays
                    }
                    // netteté du bord : une lettre a un bord net de tous les côtés ; une lueur n'en a un que du côté des lettres, elle s'estompe de l'autre (lettres blanches à lueur rose
                    // sur un mur clair : la lueur est l'extrême sombre, et entre deux lettres elle les entoure autant qu'elle en est entourée)
                    fun sharpness(x: BooleanArray): Float {
                        val step = 3; val minJump = 0.5f * (brightRef - darkRef)
                        var rays = 0; var sharp = 0
                        for (i in 0 until n) {
                            if (!x[i]) continue
                            val x0 = i % w; val y0 = i / w
                            for (k in 0..3) {
                                val dx = if (k == 0) 1 else if (k == 1) -1 else 0; val dy = if (k == 2) 1 else if (k == 3) -1 else 0
                                val nx = x0 + dx; val ny = y0 + dy
                                if (nx < 0 || ny < 0 || nx >= w || ny >= h || x[ny * w + nx]) continue
                                val fx = x0 + step * dx; val fy = y0 + step * dy
                                if (fx < 0 || fy < 0 || fx >= w || fy >= h || !allowed[fy * w + fx]) continue
                                rays++
                                if (kotlin.math.abs(lum(pixels[fy * w + fx]) - lum(pixels[i])) >= minJump) sharp++
                            }
                        }
                        return if (rays < 40) -1f else sharp.toFloat() / rays
                    }
                    // formes fermées entières (une lueur coupée par les boîtes de lecture paraîtrait entourée de lettres)
                    val sd = surround(dIn, bAll); val sb = surround(bIn, dAll)
                    val kd = sharpness(dIn); val kb = sharpness(bIn)
                    Erasure.trace?.invoke("couleur du texte : entouré par l'autre extrême : sombre ${"%.2f".format(sd)} / clair ${"%.2f".format(sb)} ; bord net : sombre ${"%.2f".format(kd)} / clair ${"%.2f".format(kb)}")
                    val xD = BooleanArray(n) { dIn[it] && tight[it] }; val xB = BooleanArray(n) { bIn[it] && tight[it] }
                    // les deux mesures s'additionnent (chacune seule hésite sur une lueur large qui remplit l'espace entre les lettres)
                    val scD = sd + kd; val scB = sb + kb
                    if (sd >= 0f && kd >= 0f && scD >= 1.2f && scD - scB >= 0.3f) { pickBright = false; decided = true }
                    else if (sb >= 0f && kb >= 0f && scB >= 1.2f && scB - scD >= 0.3f) { pickBright = true; decided = true }
                    else if (lineH > 0 && letterLike(xD, w, h, lineH).second.let { ld -> letterLike(xB, w, h, lineH).second.let { lb ->
                            Erasure.trace?.invoke("couleur du texte : formes de la taille d'une lettre : sombre ${"%.2f".format(ld)} / clair ${"%.2f".format(lb)}")
                            if (ld >= 0.4f && ld - lb >= 0.25f) { pickBright = false; true } else if (lb >= 0.4f && lb - ld >= 0.25f) { pickBright = true; true } else false } }) decided = true
                    else {
                        // sinon le trait des lettres est plus épais que le liseré
                        val pd = depthP90(xD, w, h); val pb = depthP90(xB, w, h)
                        Erasure.trace?.invoke("couleur du texte : épaisseur des formes fermées : sombre $pd px / claire $pb px")
                        if (pd >= 3 && pd * 2 >= pb * 3) { pickBright = false; decided = true }
                        else if (pb >= 3 && pb * 2 >= pd * 3) { pickBright = true; decided = true }
                    }
                }
            }
        }
        // les deux extrêmes doivent être nettement distincts de la médiane pour qu'il y ait ambiguïté (un texte blanc sur fond sombre a un extrême sombre à peine plus sombre que le fond).
        // Cas à départager : lettres noires cernées d'une lueur blanche sur un ciel (la lueur est l'extrême le plus éloigné) ; texte blanc fin sur un panneau holographique bleu aux plages
        // sombres épaisses (ces plages passaient pour les lettres : le texte blanc n'était pas effacé du tout).
        if (!decided && tight != null && w > 0 && h > 0 && pixels.size == w * h && dd >= 0.10f && db >= 0.25f) {
            // surfaces pleines de chaque extrême : tous les pixels des boîtes de lecture qui ont (à 0,12 près) la luminosité de cet extrême, pas seulement les 10 % les plus extrêmes
            val darkRef = (med - dd); val brightRef = (med + db)
            val dMask = BooleanArray(w * h); val bMask = BooleanArray(w * h)
            for (i in idx) if (tight[i]) { val l = lum(pixels[i]); if (l <= darkRef + 0.12f) dMask[i] = true; if (l >= brightRef - 0.12f) bMask[i] = true }
            // 1) les lettres forment des composantes de la taille d'une lettre ; une lueur soudée ou un fond (plages sombres d'un panneau, ciel) n'en forme pas. Quand l'une des deux classes en forme
            //    nettement plus que l'autre (au moins 15 points de la surface du masque), ce sont ses pixels les lettres.
            if (lineH > 0) {
                val sd = letterLike(dMask, w, h, lineH).second; val sb = letterLike(bMask, w, h, lineH).second
                Erasure.trace?.invoke("couleur du texte : part de composantes de la taille d'une lettre : sombre ${"%.2f".format(sd)} / clair ${"%.2f".format(sb)}")
                if (kotlin.math.abs(sd - sb) >= 0.15f) pickBright = sb > sd
                else if (dd >= 0.24f) pickBright = depthPick(dMask, bMask, w, h, dd, db, pickBright, allowed)
            } else if (dd >= 0.24f) pickBright = depthPick(dMask, bMask, w, h, dd, db, pickBright, allowed)
        }
        val pick = if (pickBright) bright else dark
        return pack(pick.sumOf { r(it) } / pick.size, pick.sumOf { g(it) } / pick.size, pick.sumOf { b(it) } / pick.size)
    }

    /**
     * Lettrage coloré (ni très clair ni très foncé) cerné d'un contour sombre, posé sur une lueur claire à hachures : l'extrême de luminosité retenu par [extremeTextColor] est alors la lueur
     * elle-même (blanche), pas les lettres. Toute la lueur passait pour du texte : effacée en bloc, puis remplacée par un brouillard gris rosé sans hachures, avec des bords crénelés.
     *
     * Une couleur de texte est bien plus fréquente dans les boîtes de lecture ([inBoxes]) qu'autour d'elles ([outside] : le voisinage immédiat des boîtes, sans les boîtes des textes voisins).
     * Quand la couleur retenue ([picked]) est au contraire deux fois plus rare dans les boîtes que dehors, c'est le fond ; on cherche alors, parmi les couleurs les plus fréquentes des
     * boîtes, une couleur franche (saturée, ni claire ni sombre) qui y est au moins 5 fois plus fréquente que dehors et y couvre au moins 18 % des pixels : c'est le remplissage des lettres.
     * Rend null (rien à corriger) dans tous les autres cas : un texte blanc sur fond sombre ou noir sur fond clair a sa couleur bien plus présente dans les boîtes.
     */
    fun enrichedFill(pixels: IntArray, w: Int, h: Int, inBoxes: BooleanArray, outside: BooleanArray, picked: Int): Int? {
        val n = w * h
        if (pixels.size != n) return null
        var nIn = 0; var nOut = 0
        for (i in 0 until n) { if (inBoxes[i]) nIn++ else if (outside[i]) nOut++ }
        if (nIn < 400 || nOut < 400) return null
        // parts de [c] (à [tol] près) dans les boîtes et hors des boîtes
        fun shares(c: Int, tol: Int): FloatArray {
            var a = 0; var o = 0
            for (i in 0 until n) { if (dist(pixels[i], c) > tol) continue; if (inBoxes[i]) a++ else if (outside[i]) o++ }
            return floatArrayOf(a.toFloat() / nIn, o.toFloat() / nOut)
        }
        val sp = shares(picked, 70)
        if (sp[0] >= 0.5f * sp[1]) return null
        val hist = IntArray(4096)
        for (i in 0 until n) if (inBoxes[i]) { val c = pixels[i]; hist[((r(c) shr 4) shl 8) or ((g(c) shr 4) shl 4) or (b(c) shr 4)]++ }
        val order = hist.indices.sortedByDescending { hist[it] }.take(12)
        for (k in order) {
            if (hist[k] < 0.05f * nIn) break
            val cen = pack(((k shr 8) and 15) * 16 + 8, ((k shr 4) and 15) * 16 + 8, (k and 15) * 16 + 8)
            val mx = max(r(cen), max(g(cen), b(cen))); val mn = min(r(cen), min(g(cen), b(cen)))
            val sat = (mx - mn).toFloat() / max(1, mx); val lu = lum(cen)
            if (sat < 0.35f || lu < 0.25f || lu > 0.85f) continue
            val sc = shares(cen, 45)
            if (sc[0] < 0.18f || sc[0] < 5f * (sc[1] + 0.01f)) continue
            var cnt = 0; var sr = 0L; var sg = 0L; var sb = 0L
            for (i in 0 until n) if (inBoxes[i] && dist(pixels[i], cen) <= 45) { cnt++; sr += r(pixels[i]); sg += g(pixels[i]); sb += b(pixels[i]) }
            if (cnt == 0) continue
            val mean = pack((sr / cnt).toInt(), (sg / cnt).toInt(), (sb / cnt).toInt())
            if (dist(mean, picked) <= 100) continue
            return mean
        }
        return null
    }

    /**
     * Retire de [src] les composantes qui ressemblent à des hachures : très fines (épaisseur moyenne sous [maxThickness] px, mesurée par 2 × aire / périmètre) et sans aucun pixel dans
     * [near] (le voisinage des lignes lues par l'OCR). Les pointes d'un contour « étoile » et les traits de vitesse passent dans les coins de la zone de texte détectée, plus large que
     * les lettres : chacune est une petite composante isolée qui passait pour une lettre, était effacée, et laissait une tache aux quatre coins de la zone. Une lettre est plus épaisse
     * (trait de 3 px et plus) ; ponctuation et accents sont près des lignes lues, donc jamais retirés.
     */
    internal fun withoutHatch(src: BooleanArray, w: Int, h: Int, near: BooleanArray, maxThickness: Float = 2.4f): BooleanArray {
        val out = src.copyOf(); val seen = BooleanArray(w * h); val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!src[start] || seen[start]) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
            var touches = false; var edges = 0
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (near[i]) touches = true
                if (x == 0 || !src[i - 1]) edges++
                if (x == w - 1 || !src[i + 1]) edges++
                if (y == 0 || !src[i - w]) edges++
                if (y == h - 1 || !src[i + w]) edges++
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (src[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (!touches && edges > 0 && 2f * tail / edges < maxThickness) for (k in 0 until tail) out[queue[k]] = false
        }
        return out
    }

    /**
     * Retire de [src] les traits fins (moins de 5 px de large) qui s'écartent de plus de 3 px des lignes lues [inner]. Ce qui est fin au-delà n'est pas
     * du texte : c'est du décor soudé aux lettres (les hachures d'un contour de bulle qui finissent au bout d'une ligne, le peigne de traits d'un panneau voisin), que l'effacement prenait
     * pour des lettres, et dont le voisinage clair s'effaçait avec lui : un bloc blanc taillé dans le dessin et une tache grise au bout des hachures. Les traits épais (lettrage gras,
     * bordure) gardent leur forme : érosion de 2 px puis reprise du contour (3 px, dans [src]).
     */
    internal fun withoutThinOutside(src: BooleanArray, w: Int, h: Int, inner: BooleanArray): BooleanArray {
        val keep = dilate(inner, w, h, 3)
        val thick = dilate(erode(src, w, h, 2), w, h, 3, src)
        return BooleanArray(w * h) { src[it] && (keep[it] || thick[it]) }
    }

    /**
     * Pixels de texte sur un dessin : remplissage des lettres (couleur proche de [textColor]) et leur contour (couleur dominante des
     * pixels voisins du remplissage), légèrement dilatés. Contrairement à un rectangle, on n'efface que les lettres : le dessin
     * autour est conservé, et une éventuelle lueur diffuse reste (la traduction est redessinée par-dessus avec son propre contour).
     */
    fun artMask(pixels: IntArray, w: Int, h: Int, allowed: BooleanArray, textColor: Int, grow: Int, maxDim: Int = Int.MAX_VALUE, tight: BooleanArray? = null, maxReach: Int = 6, lines: List<IntArray>? = null, rescueAvoid: ((BooleanArray) -> BooleanArray?)? = null, nearLines: BooleanArray? = null, inner: BooleanArray? = null): BooleanArray =
        artMaskInfo(pixels, w, h, allowed, textColor, grow, maxDim, tight, maxReach, lines, rescueAvoid, nearLines, inner).mask

    /** Masque du texte sur un dessin et résultat de la mesure du contour (le texte est-il cerné d'un trait contrasté ?). */
    class ArtMask(val mask: BooleanArray, val outlined: Boolean)

    /**
     * [lines] : boîtes de lecture des lignes (gauche, haut, droite, bas, dans la région) ; elles servent à rattraper les lettres soudées à une grande surface (voir [rescueInLines]).
     * [rescueAvoid] : reçoit les lettres déjà retenues et rend les pixels que le rattrapage ne doit jamais prendre (contour de bulle, trait du décor : les coins d'une boîte de lecture
     * débordent sur eux), ou null.
     * [nearLines] : voisinage des lignes lues ; une composante très fine qui n'y touche pas (pointe de contour, trait de vitesse dans un coin de la zone détectée) n'est pas une lettre (voir [withoutHatch]).
     * [inner] : pixels des lignes lues elles-mêmes ; les traits fins soudés aux lettres hors de ces lignes ne sont pas du texte (voir [withoutThinOutside]).
     */
    fun artMaskInfo(pixels: IntArray, w: Int, h: Int, allowed: BooleanArray, textColor: Int, grow: Int, maxDim: Int = Int.MAX_VALUE, tight: BooleanArray? = null, maxReach: Int = 6, lines: List<IntArray>? = null, rescueAvoid: ((BooleanArray) -> BooleanArray?)? = null, nearLines: BooleanArray? = null, inner: BooleanArray? = null): ArtMask {
        // lettres = composantes isolées : une grande surface de couleur proche (fond d'un encadré voisin, bas d'une bulle, cheveux blancs) n'est pas une lettre.
        // L'isolement se juge sur toute la région (et non sur la seule zone autorisée, dont le bord coupe ces grandes surfaces et les ferait paraître isolées),
        // puis on écarte toute composante plus grande qu'une lettre ([maxDim] : quelques hauteurs de ligne).
        val color = BooleanArray(w * h) { dist(pixels[it], textColor) <= 70 }
        val fill0 = withoutThickBlobs(enclosedMostlyIn(color, allowed, w, h, 0.7f), pixels, textColor, w, h)
        val fill1 = if (nearLines != null) withoutHatch(fill0, w, h, nearLines) else fill0
        val fill = if (inner != null) withoutThinOutside(fill1, w, h, inner) else fill1
        // lettres dont le remplissage touche une grande surface de même couleur (texte posé sur le bord d'une bulle blanche) : écartées ci-dessus avec la bulle. On les
        // récupère par leur contour : un pixel clair entouré de pixels sombres (contour noir de la lettre) est un pixel de lettre, un pixel de bulle ne l'est pas.
        rescueOutlined(color, fill, tight ?: allowed, pixels, w, h, textColor)
        if (lines != null) rescueInLines(fill, pixels, w, h, textColor, lines, rescueAvoid?.invoke(fill))
        // épaisseur du contour mesurée : couleur médiane à chaque distance du remplissage, comparée à celle du fond lointain. Un contour (blanc autour de lettres noires,
        // noir autour de lettres blanches) a une couleur stable sur quelques pixels puis rejoint le fond ; sans contour, on n'efface que le lissage des bords
        val dmax = 12
        val dm = IntArray(w * h) { if (fill[it]) 0 else Int.MAX_VALUE }
        run {
            val queue = IntArray(w * h); var head = 0; var tail = 0
            for (i in dm.indices) if (dm[i] == 0) queue[tail++] = i
            while (head < tail) {
                val i = queue[head++]; val d = dm[i]; if (d >= dmax) continue
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (dm[j] == Int.MAX_VALUE) { dm[j] = d + 1; queue[tail++] = j }
                }
            }
        }
        val byD = Array(dmax + 1) { ArrayList<Int>() }
        for (i in pixels.indices) { val d = dm[i]; if (d in 1..dmax && allowed[i] && !color[i]) byD[d].add(pixels[i]) }
        // profil de luminance du contour : pour un texte sombre le contour est clair (et inversement). On compare le percentile « côté contour » de chaque couronne à la
        // luminance médiane du fond lointain ; le contour est l'ensemble des couronnes qui gardent au moins la moitié du contraste maximal
        val lightOutline = lum(textColor) < 0.5f
        fun prof(d: Int): Float? {
            val l = byD[d]; if (l.size < 12) return null
            val v = FloatArray(l.size) { lum(l[it]) }; v.sort()
            return v[((if (lightOutline) 0.85f else 0.15f) * (v.size - 1)).toInt()]
        }
        val farL = run {
            val v = ArrayList<Float>(); for (d in dmax - 2..dmax) for (c in byD[d]) v.add(lum(c))
            if (v.size < 12) null else { v.sort(); v[v.size / 2] }
        }
        val peak = if (farL == null) null else (2..4).mapNotNull { prof(it) }.let { if (lightOutline) it.maxOrNull() else it.minOrNull() }
        val contrast = if (peak == null || farL == null) 0f else abs(peak - farL)
        val outlined = contrast > 0.11f
        var thick = 1
        if (outlined && farL != null) { for (d in 2..dmax - 3) { val v = prof(d) ?: break; if (abs(v - farL) >= 0.5f * contrast) thick = d else break } }
        val reach = if (outlined) min(thick + 1, maxReach) else 2
        // pixels d'une couronne retenus : le lissage des bords (distance 1) toujours, puis ceux dont la luminance est du côté du contour (pas le fond du dessin)
        val thr = if (outlined && farL != null && peak != null) farL + 0.4f * (peak - farL) else 0f
        val mask = BooleanArray(w * h) { i ->
            fill[i] || (allowed[i] && dm[i] <= reach && (dm[i] <= 2 || (outlined && (if (lightOutline) lum(pixels[i]) >= thr else lum(pixels[i]) <= thr))))
        }
        // halo de lissage (bords adoucis par le redimensionnement ou la compression) : les pixels voisins du masque qui s'écartent nettement du fond lointain sont absorbés,
        // sur 3 pixels au plus. Sans cela, la reconstruction reprend ces gris clairs en bordure et laisse un fantôme clair des lettres.
        val farRgb: Int? = run {
            val l = ArrayList<Int>(); for (d in dmax - 2..dmax) l.addAll(byD[d])
            if (l.size < 12) null else ColorMath.median(l.toIntArray())
        }
        var grown = mask
        if (farRgb != null) {
            repeat(3) {
                val nxt = grown.copyOf()
                for (y in 1 until h - 1) for (x in 1 until w - 1) {
                    val i = y * w + x
                    if (grown[i] || !allowed[i] || dist(pixels[i], farRgb) <= 28) continue
                    if (grown[i - 1] || grown[i + 1] || grown[i - w] || grown[i + w] || grown[i - w - 1] || grown[i - w + 1] || grown[i + w - 1] || grown[i + w + 1]) nxt[i] = true
                }
                grown = nxt
            }
        }
        return ArtMask(dilate(grown, w, h, grow, allowed), outlined)
    }

    /**
     * Sans les formes pleines bien plus épaisses que le trait des lettres : carreaux sombres d'une fenêtre sous un texte noir penché (journal du 23/11), tache d'encre, aplat. Ils ont la
     * couleur du texte (à la tolérance près) et tiennent dans la zone du texte, mais leur épaisseur (profondeur maximale) dépasse 2,5 fois celle de la composante médiane, qui est une lettre,
     * et leur teinte s'écarte nettement de celle du texte (un lettrage très gras, lui, en a la couleur exacte).
     * Rien n'est retiré quand il y a moins de trois composantes (pas de référence fiable).
     */
    internal fun withoutThickBlobs(fill: BooleanArray, pixels: IntArray, textColor: Int, w: Int, h: Int): BooleanArray {
        val n = w * h
        val d = IntArray(n) { if (fill[it]) 1 shl 20 else 0 }
        for (y in 0 until h) for (x in 0 until w) { val i = y * w + x; if (!fill[i]) continue; d[i] = minOf(d[i], if (x == 0) 1 else d[i - 1] + 1, if (y == 0) 1 else d[i - w] + 1) }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) { val i = y * w + x; if (!fill[i]) continue; d[i] = minOf(d[i], if (x == w - 1) 1 else d[i + 1] + 1, if (y == h - 1) 1 else d[i + w] + 1) }
        val label = IntArray(n) { -1 }; val stack = IntArray(n)
        val depth = ArrayList<Int>(); val size = ArrayList<Int>(); val far = ArrayList<Int>()
        val hist = IntArray(256)
        for (st in 0 until n) {
            if (!fill[st] || label[st] >= 0) continue
            val id = depth.size; var sp = 0; var cnt = 0; var md = 0
            java.util.Arrays.fill(hist, 0)
            stack[sp++] = st; label[st] = id
            while (sp > 0) {
                val i = stack[--sp]; cnt++; if (d[i] > md) md = d[i]
                hist[min(255, dist(pixels[i], textColor))]++
                val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (fill[j] && label[j] < 0) { label[j] = id; stack[sp++] = j }
                }
            }
            // écart médian à la couleur du texte : des lettres en ont la couleur exacte (aux bords près), un carreau brun sombre n'en est que proche
            var acc = 0; var medDist = 255; for (k in 0 until 256) { acc += hist[k]; if (acc * 2 >= cnt) { medDist = k; break } }
            depth.add(md); size.add(cnt); far.add(medDist)
        }
        val ref = depth.indices.filter { size[it] >= 20 }.map { depth[it] }.sorted()
        if (ref.size < 3) return fill
        val typical = ref[ref.size / 2]
        val maxOk = max(4f, 2.5f * typical)
        // épaisse ET d'une autre teinte que le texte (à 30 niveaux près) : un lettrage très gras (« WHAT THE HELL » soudé par ses traits) a la couleur exacte du texte, il reste
        fun drop(k: Int) = depth[k] > maxOk && far[k] > 30
        if (depth.indices.none { drop(it) }) return fill
        Erasure.trace?.invoke("formes pleines écartées de l'encre : ${depth.indices.count { drop(it) }} (épaisseur > ${"%.1f".format(maxOk)} px ; trait des lettres $typical px ; " +
            depth.indices.filter { drop(it) }.joinToString(", ") { k -> var x0 = w; var y0 = h; var x1 = 0; var y1 = 0; for (i in 0 until n) if (fill[i] && label[i] == k) { val x = i % w; val y = i / w; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }; "${size[k]} px [$x0,$y0 – $x1,$y1], épaisseur ${depth[k]}, écart de teinte ${far[k]}" } + ")")
        return BooleanArray(n) { fill[it] && !drop(label[it]) }
    }

    /**
     * Composantes connexes de [src] (calculées sur toute la région) qui ne touchent pas le bord, n'en occupent pas plus de la moitié et sont pour au moins [ratio]
     * de leurs pixels dans [zone]. Une lettre est entièrement dans la zone du texte ; une grande surface (bas d'une bulle, cheveux blancs) la dépasse largement.
     */
    private fun enclosedMostlyIn(src: BooleanArray, zone: BooleanArray, w: Int, h: Int, ratio: Float): BooleanArray {
        val seen = BooleanArray(w * h); val out = BooleanArray(w * h); val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!src[start] || seen[start]) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true; var border = false; var inside = 0
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (zone[i]) inside++
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border = true
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (src[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (!border && tail < w * h / 2 && inside >= ratio * tail) for (k in 0 until tail) if (zone[queue[k]]) out[queue[k]] = true
        }
        return out
    }

    /**
     * Lettres écartées par la règle des composantes isolées : des lettres soudées, par des pixels de la couleur du texte (dégradé violet sous la dernière ligne, lueur, fond voisin), à une grande
     * surface qui touche le bord de la région ; la surface et les lettres forment alors une seule grosse composante, rejetée en entier, et ces lettres restaient visibles sous la traduction
     * (la fin d'une phrase en rouge sombre sur un fond qui s'éclaircit vers le bas). Dans la boîte de lecture d'une ligne, avec une tolérance de couleur plus serrée, la surface se détache des
     * lettres : chaque composante restante qui a la hauteur d'une lettre sans remplir la boîte est du texte. Les lettres déjà retenues ne changent pas.
     */
    internal fun rescueInLines(fill: BooleanArray, pixels: IntArray, w: Int, h: Int, textColor: Int, lines: List<IntArray>, avoid: BooleanArray? = null) {
        val tol = 40
        for (lr in lines) {
            val bx0 = max(0, lr[0]); val by0 = max(0, lr[1]); val bx1 = min(w, lr[2]); val by1 = min(h, lr[3])
            val bw = bx1 - bx0; val bh = by1 - by0
            if (bw < 8 || bh < 8) continue
            val cand = BooleanArray(bw * bh) { k -> val i = (by0 + k / bw) * w + bx0 + k % bw; !fill[i] && !(avoid != null && avoid[i]) && dist(pixels[i], textColor) <= tol }
            val seen = BooleanArray(bw * bh); val queue = IntArray(bw * bh)
            for (start in 0 until bw * bh) {
                if (!cand[start] || seen[start]) continue
                var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
                var cx0 = bw; var cx1 = -1; var cy0 = bh; var cy1 = -1
                while (head < tail) {
                    val k = queue[head++]; val x = k % bw; val y = k / bw
                    if (x < cx0) cx0 = x; if (x > cx1) cx1 = x; if (y < cy0) cy0 = y; if (y > cy1) cy1 = y
                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx; val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= bw || ny >= bh) continue
                        val j = ny * bw + nx
                        if (cand[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                    }
                }
                val ch = cy1 - cy0 + 1; val cw = cx1 - cx0 + 1
                // une lettre (ou un mot) : au moins 40 % de la hauteur de la ligne, assez de pixels, et qui laisse du vide dans sa boîte (une plage pleine, un trait du décor, n'en laisse pas)
                if (tail < 10 || ch < 0.4f * bh || tail > 0.85f * cw * ch) continue
                for (q in 0 until tail) { val k = queue[q]; fill[(by0 + k / bw) * w + bx0 + k % bw] = true }
            }
        }
        rescueWeakLines(fill, pixels, w, h, lines, avoid)
    }

    /**
     * Ligne dont les lettres sont d'une autre couleur que celles des lignes voisines (dernière ligne d'un texte qui s'estompe vers le bas) : la couleur du texte mesurée sur tout le bloc ne
     * les retrouve pas, et il en reste un fantôme sous la traduction. On la reconnaît à son encre : moins de la moitié de celle des autres lignes. Elle est alors segmentée sans connaître
     * sa couleur : le fond sous la ligne est interpolé en hauteur entre les pixels juste au-dessus et juste au-dessous de sa boîte, et les lettres sont les pixels qui s'en écartent
     * (seuil d'Otsu sur l'écart). Le résultat n'est retenu que s'il redonne à la ligne une encre comparable à celle des autres.
     */
    private fun rescueWeakLines(fill: BooleanArray, pixels: IntArray, w: Int, h: Int, lines: List<IntArray>, avoid: BooleanArray?) {
        if (lines.size < 3) return
        fun cover(lr: IntArray): Float {
            val x0 = max(0, lr[0]); val y0 = max(0, lr[1]); val x1 = min(w, lr[2]); val y1 = min(h, lr[3])
            if (x1 <= x0 || y1 <= y0) return 0f
            var n = 0; for (y in y0 until y1) for (x in x0 until x1) if (fill[y * w + x]) n++
            return n.toFloat() / ((x1 - x0) * (y1 - y0))
        }
        val cov = lines.map { cover(it) }
        for ((k, lr) in lines.withIndex()) {
            val others = cov.filterIndexed { j, _ -> j != k }.sorted(); val med = others[others.size / 2]
            if (med < 0.3f || cov[k] >= 0.5f * med) continue
            val bx0 = max(0, lr[0]); val by0 = max(0, lr[1]); val bx1 = min(w, lr[2]); val by1 = min(h, lr[3])
            val bw = bx1 - bx0; val bh = by1 - by0
            if (bw < 16 || bh < 12) continue
            // fond sous la ligne : pour chaque colonne, médiane des pixels juste au-dessus et juste au-dessous de la boîte (bande de 4 pixels, fenêtre de 9 colonnes)
            fun band(x: Int, yA: Int, yB: Int): IntArray? {
                val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
                for (yy in max(0, yA)..min(h - 1, yB)) for (xx in max(0, x - 4)..min(w - 1, x + 4)) { val c = pixels[yy * w + xx]; rs.add(r(c)); gs.add(g(c)); bs.add(b(c)) }
                if (rs.size < 12) return null
                rs.sort(); gs.sort(); bs.sort()
                return intArrayOf(rs[rs.size / 2], gs[gs.size / 2], bs[bs.size / 2])
            }
            val diff = IntArray(bw * bh); var ok = true
            for (xx in 0 until bw) {
                val up = band(bx0 + xx, by0 - 5, by0 - 2); val dn = band(bx0 + xx, by1 + 2, by1 + 5)
                if (up == null && dn == null) { ok = false; break }
                for (yy in 0 until bh) {
                    val t = (yy + 0.5f) / bh
                    val bgc = IntArray(3) { c -> if (up != null && dn != null) (up[c] * (1f - t) + dn[c] * t).toInt() else (up ?: dn!!)[c] }
                    val p = pixels[(by0 + yy) * w + bx0 + xx]
                    diff[yy * bw + xx] = maxOf(abs(r(p) - bgc[0]), abs(g(p) - bgc[1]), abs(b(p) - bgc[2]))
                }
            }
            if (!ok) continue
            // seuil d'Otsu sur l'écart au fond
            val hist = IntArray(256); for (d in diff) hist[min(255, d)]++
            val total = diff.size; var sumAll = 0L; for (v in 0..255) sumAll += v.toLong() * hist[v]
            var wB = 0; var sumB = 0L; var best = -1.0; var thr = 0
            for (v in 0..255) {
                wB += hist[v]; if (wB == 0) continue
                val wF = total - wB; if (wF == 0) break
                sumB += v.toLong() * hist[v]
                val mB = sumB.toDouble() / wB; val mF = (sumAll - sumB).toDouble() / wF
                val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
                if (between > best) { best = between; thr = v }
            }
            if (thr < 18) continue
            val text = BooleanArray(bw * bh) { diff[it] > thr && !(avoid != null && avoid[(by0 + it / bw) * w + bx0 + it % bw]) }
            val share = text.count { it }.toFloat() / text.size
            if (share < 0.12f || share > 0.8f) continue
            // retenu seulement si la ligne retrouve une encre comparable à celle des autres
            var added = 0; var existing = 0
            for (q in text.indices) if (text[q]) { val i = (by0 + q / bw) * w + bx0 + q % bw; if (fill[i]) existing++ else added++ }
            val newCover = (existing + added).toFloat() / text.size
            if (newCover < 0.6f * med || newCover > 1.4f * med) continue
            for (q in text.indices) if (text[q]) fill[(by0 + q / bw) * w + bx0 + q % bw] = true
        }
    }

    private fun rescueOutlined(color: BooleanArray, fill: BooleanArray, allowed: BooleanArray, pixels: IntArray, w: Int, h: Int, textColor: Int) {
        // contour sombre autour du remplissage : on ne cherche que si le texte est clair (cerné d'un trait plus sombre)
        val tl = lum(textColor)
        if (tl < 0.6f) return
        val dark = BooleanArray(w * h) { lum(pixels[it]) < 0.30f }
        // image intégrale des pixels sombres : part de sombre dans une fenêtre 9×9
        val integ = IntArray((w + 1) * (h + 1))
        for (y in 0 until h) { var row = 0; for (x in 0 until w) { row += if (dark[y * w + x]) 1 else 0; integ[(y + 1) * (w + 1) + x + 1] = integ[y * (w + 1) + x + 1] + row } }
        val extra = BooleanArray(w * h)
        for (y in 4 until h - 4) for (x in 4 until w - 4) {
            val i = y * w + x
            if (!allowed[i] || fill[i] || !color[i]) continue
            val sum = integ[(y + 5) * (w + 1) + x + 5] - integ[(y - 4) * (w + 1) + x + 5] - integ[(y + 5) * (w + 1) + x - 4] + integ[(y - 4) * (w + 1) + x - 4]
            if (sum >= 22) extra[i] = true           // plus de 27 % de sombre autour (contour de lettre)
        }
        // le cœur d'un trait épais est à plus de 4 px du contour : on prolonge de 5 px, dans la couleur du texte et la zone seulement
        val grown = dilate(extra, w, h, 5, BooleanArray(w * h) { color[it] && allowed[it] })
        for (i in fill.indices) if (grown[i]) fill[i] = true
    }


    /**
     * Reconstruction par plus proche voisin : chaque pixel masqué prend la couleur du pixel non masqué le plus proche, puis un léger lissage (2 passes de 3×3) adoucit les raccords.
     * Contrairement à la diffusion, qui mélange le blanc d'une bulle et le fond sombre d'un dessin en un gris voilé, un bord net reste net (à l'épaisseur d'un trait près).
     */
    fun inpaintNearest(pixels: IntArray, w: Int, h: Int, mask: BooleanArray): IntArray {
        val out = pixels.copyOf()
        val n = w * h
        val src = IntArray(n) { -1 }
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in 0 until n) if (!mask[i]) { src[i] = i; queue[tail++] = i }
        if (tail == 0 || tail == n) return out
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (src[j] < 0) { src[j] = src[i]; queue[tail++] = j }
            }
        }
        for (i in 0 until n) if (mask[i] && src[i] >= 0) out[i] = pixels[src[i]]
        repeat(2) {
            val cur = out.copyOf()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                if (!mask[i]) continue
                var sr = 0; var sg = 0; var sb = 0
                for (dy in -1..1) for (dx in -1..1) { val c = cur[(y + dy) * w + x + dx]; sr += r(c); sg += g(c); sb += b(c) }
                out[i] = pack(sr / 9, sg / 9, sb / 9)
            }
        }
        return out
    }

    /**
     * Surface lisse : polynôme du second degré par canal, ajusté aux moindres carrés sur les pixels [usable] (échantillon régulier), en écartant en quelques itérations ceux qui s'en
     * éloignent (lettres restantes, dessin). Elle suit un dégradé ou un ombrage régulier que ni la couleur médiane ni un aplat ne représentent. null si trop peu de pixels ou si
     * l'ajustement est dégénéré.
     */
    fun smoothSurface(pixels: IntArray, w: Int, h: Int, usable: BooleanArray): IntArray? {
        val n = w * h
        var cnt = 0; for (i in 0 until n) if (usable[i]) cnt++
        if (cnt < 300 || w < 4 || h < 4) return null
        val step = maxOf(1, cnt / 6000)
        val idx = IntArray(cnt / step + 2); var m = 0; var seen = 0
        for (i in 0 until n) if (usable[i]) { if (seen % step == 0 && m < idx.size) idx[m++] = i; seen++ }
        val bx = DoubleArray(m * 6)
        for (k in 0 until m) {
            val i = idx[k]; val x = (i % w) / (w - 1.0) * 2 - 1; val y = (i / w) / (h - 1.0) * 2 - 1
            bx[k * 6] = 1.0; bx[k * 6 + 1] = x; bx[k * 6 + 2] = y; bx[k * 6 + 3] = x * x; bx[k * 6 + 4] = x * y; bx[k * 6 + 5] = y * y
        }
        val keep = BooleanArray(m) { true }
        var coef = Array(3) { DoubleArray(6) }
        fun solve(): Array<DoubleArray>? {
            val out = Array(3) { DoubleArray(6) }
            for (ch in 0 until 3) {
                val a = Array(6) { DoubleArray(7) }
                for (k in 0 until m) {
                    if (!keep[k]) continue
                    val c = when (ch) { 0 -> r(pixels[idx[k]]); 1 -> g(pixels[idx[k]]); else -> b(pixels[idx[k]]) }.toDouble()
                    for (p in 0 until 6) { val bp = bx[k * 6 + p]; for (q in 0 until 6) a[p][q] += bp * bx[k * 6 + q]; a[p][6] += bp * c }
                }
                for (p in 0 until 6) a[p][p] += 1e-6
                for (col in 0 until 6) {
                    var piv = col; for (rw in col + 1 until 6) if (abs(a[rw][col]) > abs(a[piv][col])) piv = rw
                    if (abs(a[piv][col]) < 1e-9) return null
                    val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
                    for (rw in 0 until 6) if (rw != col) { val f = a[rw][col] / a[col][col]; if (f != 0.0) for (q in col until 7) a[rw][q] -= f * a[col][q] }
                }
                for (p in 0 until 6) out[ch][p] = a[p][6] / a[p][p]
            }
            return out
        }
        for (thr in intArrayOf(255, 70, 48, 36)) {
            if (thr < 255) {
                val cf = coef; var kept = 0
                for (k in 0 until m) {
                    var worst = 0
                    for (ch in 0 until 3) {
                        var v = 0.0; for (p in 0 until 6) v += cf[ch][p] * bx[k * 6 + p]
                        val c = when (ch) { 0 -> r(pixels[idx[k]]); 1 -> g(pixels[idx[k]]); else -> b(pixels[idx[k]]) }
                        worst = maxOf(worst, abs(c - v.toInt()))
                    }
                    keep[k] = worst <= thr; if (keep[k]) kept++
                }
                if (kept < 200) break
            }
            coef = solve() ?: return null
        }
        val out = IntArray(n)
        for (i in 0 until n) {
            val x = (i % w) / (w - 1.0) * 2 - 1; val y = (i / w) / (h - 1.0) * 2 - 1
            val b6 = doubleArrayOf(1.0, x, y, x * x, x * y, y * y)
            val ch = IntArray(3) { c -> var v = 0.0; for (p in 0 until 6) v += coef[c][p] * b6[p]; v.toInt().coerceIn(0, 255) }
            out[i] = pack(ch[0], ch[1], ch[2])
        }
        return out
    }

    /**
     * Reconstruction lisse d'une zone : initialisation par la couleur du plus proche pixel valide, puis relaxation (moyenne des 4 voisins) qui lisse les raccords. Contrairement à
     * [inpaint], dont l'initialisation est la moyenne de toute la région et qui ne converge pas sur une grande zone (d'où une teinte de « carré » différente du fond), le résultat
     * suit le dégradé du fond autour de la zone. Seuls les pixels [valid] (non masqués) servent de sources : un contour ou un dessin voisin ne salit pas le remplissage.
     */
    fun inpaintSmooth(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, valid: BooleanArray? = null, iters: Int = 24): IntArray {
        val n = w * h
        // états : 0 exclu (ni source ni inconnue : contour, autre texte), 1 connu, 2 inconnu (à reconstruire)
        val st0 = ByteArray(n) { if (mask[it]) 2 else if (valid == null || valid[it]) 1 else 0 }
        var anyKnown = false; for (v in st0) if (v.toInt() == 1) { anyKnown = true; break }
        if (!anyKnown) return pixels.copyOf()
        class Level(val w: Int, val h: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray, val st: ByteArray)
        val levels = ArrayList<Level>()
        levels.add(Level(w, h, FloatArray(n) { r(pixels[it]).toFloat() }, FloatArray(n) { g(pixels[it]).toFloat() }, FloatArray(n) { b(pixels[it]).toFloat() }, st0))
        // pyramide : un pixel grossier est connu s'il a un enfant connu (moyenne des enfants connus), inconnu s'il n'a que des enfants inconnus, exclu sinon
        while (levels.last().w > 12 && levels.last().h > 12) {
            val f = levels.last(); val cw = (f.w + 1) / 2; val ch = (f.h + 1) / 2
            val cr = FloatArray(cw * ch); val cg = FloatArray(cw * ch); val cb = FloatArray(cw * ch); val cs = ByteArray(cw * ch)
            for (y in 0 until ch) for (x in 0 until cw) {
                var kn = 0; var un = 0; var sr = 0f; var sg = 0f; var sb = 0f
                for (dy in 0..1) for (dx in 0..1) {
                    val fx = 2 * x + dx; val fy = 2 * y + dy; if (fx >= f.w || fy >= f.h) continue
                    val i = fy * f.w + fx
                    when (f.st[i].toInt()) { 1 -> { kn++; sr += f.r[i]; sg += f.g[i]; sb += f.b[i] }; 2 -> un++ }
                }
                val ci = y * cw + x
                if (kn > 0) { cs[ci] = 1; cr[ci] = sr / kn; cg[ci] = sg / kn; cb[ci] = sb / kn } else if (un > 0) cs[ci] = 2
            }
            levels.add(Level(cw, ch, cr, cg, cb, cs))
        }
        fun relax(l: Level, passes: Int) {
            var cnt0 = 0; for (i in 0 until l.w * l.h) if (l.st[i].toInt() == 2) cnt0++
            val arr = IntArray(cnt0); var fill0 = 0; for (i in 0 until l.w * l.h) if (l.st[i].toInt() == 2) arr[fill0++] = i
            repeat(passes) { pass ->
                val range = if (pass % 2 == 0) arr.indices else arr.indices.reversed()
                for (k in range) {
                    val i = arr[k]; val x = i % l.w; val y = i / l.w
                    var cnt = 0; var ar = 0f; var ag = 0f; var ab = 0f
                    if (x > 0 && l.st[i - 1].toInt() != 0) { ar += l.r[i - 1]; ag += l.g[i - 1]; ab += l.b[i - 1]; cnt++ }
                    if (x < l.w - 1 && l.st[i + 1].toInt() != 0) { ar += l.r[i + 1]; ag += l.g[i + 1]; ab += l.b[i + 1]; cnt++ }
                    if (y > 0 && l.st[i - l.w].toInt() != 0) { ar += l.r[i - l.w]; ag += l.g[i - l.w]; ab += l.b[i - l.w]; cnt++ }
                    if (y < l.h - 1 && l.st[i + l.w].toInt() != 0) { ar += l.r[i + l.w]; ag += l.g[i + l.w]; ab += l.b[i + l.w]; cnt++ }
                    if (cnt > 0) { l.r[i] = ar / cnt; l.g[i] = ag / cnt; l.b[i] = ab / cnt }
                }
            }
        }
        // résolution du plus grossier au plus fin : l'inconnue de chaque niveau est initialisée par le niveau grossier, puis relaxée
        val top = levels.last()
        run { var sr = 0f; var sg = 0f; var sb = 0f; var c = 0; for (i in 0 until top.w * top.h) if (top.st[i].toInt() == 1) { sr += top.r[i]; sg += top.g[i]; sb += top.b[i]; c++ }
            if (c > 0) for (i in 0 until top.w * top.h) if (top.st[i].toInt() == 2) { top.r[i] = sr / c; top.g[i] = sg / c; top.b[i] = sb / c } }
        relax(top, 120)
        for (li in levels.size - 2 downTo 0) {
            val fine = levels[li]; val coarse = levels[li + 1]
            for (i in 0 until fine.w * fine.h) if (fine.st[i].toInt() == 2) {
                val x = i % fine.w; val y = i / fine.w
                val cx = (x / 2).coerceAtMost(coarse.w - 1); val cy = (y / 2).coerceAtMost(coarse.h - 1); val ci = cy * coarse.w + cx
                fine.r[i] = coarse.r[ci]; fine.g[i] = coarse.g[ci]; fine.b[i] = coarse.b[ci]
            }
            relax(fine, if (li == 0) iters else 14)
        }
        val out = pixels.copyOf(); val f0 = levels[0]
        for (i in 0 until n) if (f0.st[i].toInt() == 2) out[i] = pack(f0.r[i].toInt().coerceIn(0, 255), f0.g[i].toInt().coerceIn(0, 255), f0.b[i].toInt().coerceIn(0, 255))
        return out
    }

    /**
     * Comme [inpaintSmooth], mais sans mélanger deux régions très différentes. Quand le trou est à cheval sur un bord net (légende posée sur le bord d'une bulle claire devant un fond sombre,
     * cadre, ombre portée), la moyenne lisse des pixels voisins fait un dégradé gris entre les deux : une tache. Ici, chaque côté du bord est prolongé avec sa propre couleur, et la frontière
     * est la courbe où l'interpolation lisse d'une carte « clair / sombre » des pixels voisins vaut un demi : le bord traverse le trou sans flou.
     * Si le contour du trou n'est pas fait d'un petit nombre de grandes régions nettes (texture, dessin fin, trame, simple dégradé), le résultat est celui de [inpaintSmooth].
     */
    fun inpaintEdge(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, valid: BooleanArray? = null, iters: Int = 24): IntArray {
        val n = w * h
        val ringW = 6; val bandW = 12
        fun lum(c: Int) = (r(c) * 77 + g(c) * 150 + b(c) * 29) shr 8
        // distance (4 voisins) au trou, jusqu'à la largeur de la bande de couleurs
        val dm = IntArray(n) { if (mask[it]) 0 else -1 }
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in 0 until n) if (mask[i]) queue[tail++] = i
        if (tail == 0) return pixels.copyOf()
        while (head < tail) {
            val i = queue[head++]; val d = dm[i]; if (d >= bandW) continue
            val x = i % w; val y = i / w
            if (x > 0 && dm[i - 1] < 0) { dm[i - 1] = d + 1; queue[tail++] = i - 1 }
            if (x < w - 1 && dm[i + 1] < 0) { dm[i + 1] = d + 1; queue[tail++] = i + 1 }
            if (y > 0 && dm[i - w] < 0) { dm[i - w] = d + 1; queue[tail++] = i - w }
            if (y < h - 1 && dm[i + w] < 0) { dm[i + w] = d + 1; queue[tail++] = i + w }
        }
        fun fallback(why: String = ""): IntArray { if (why.isNotEmpty()) Erasure.trace?.invoke("remplissage lissé (pas de bord net : $why)"); return inpaintSmooth(pixels, w, h, mask, valid, iters) }
        // anneau : pixels connus à 1..ringW px du trou ; seuil de séparation clair / sombre (Otsu)
        val hist = IntArray(256); var ringN = 0
        for (i in 0 until n) { val d = dm[i]; if (d in 1..ringW && (valid == null || valid[i])) { hist[lum(pixels[i])]++; ringN++ } }
        if (ringN < 60) return fallback()
        var sumAll = 0L; for (v in 0..255) sumAll += v.toLong() * hist[v]
        var cnt0 = 0L; var sum0 = 0L; var best = -1.0; var t = -1
        for (v in 0..254) {
            cnt0 += hist[v]; sum0 += v.toLong() * hist[v]
            val cnt1 = ringN - cnt0
            if (cnt0 == 0L) continue
            if (cnt1 == 0L) break
            val m0 = sum0.toDouble() / cnt0; val m1 = (sumAll - sum0).toDouble() / cnt1
            val between = cnt0.toDouble() * cnt1 * (m0 - m1) * (m0 - m1)
            if (between > best) { best = between; t = v }
        }
        if (t < 0) return fallback()
        var n0 = 0; var n1 = 0; var s0 = 0L; var s1 = 0L
        for (v in 0..255) { if (v <= t) { n0 += hist[v]; s0 += v.toLong() * hist[v] } else { n1 += hist[v]; s1 += v.toLong() * hist[v] } }
        if (n0 == 0 || n1 == 0) return fallback()
        val m0 = s0.toDouble() / n0; val m1 = s1.toDouble() / n1; val gap = m1 - m0
        var var0 = 0.0; var var1 = 0.0
        for (v in 0..255) { if (v <= t) var0 += hist[v] * (v - m0) * (v - m0) else var1 += hist[v] * (v - m1) * (v - m1) }
        val sd0 = sqrt(var0 / n0); val sd1 = sqrt(var1 / n1)
        val lo = m0 + 0.25 * gap; val hi = m1 - 0.25 * gap
        var mid = 0; for (v in 0..255) if (v > lo && v < hi) mid += hist[v]
        // deux grandes régions nettes : écart net, deux groupes serrés, peu de pixels intermédiaires
        if (gap < 45 || min(n0, n1) < max(25, (0.08 * ringN).toInt()) || sd0 > 0.4 * gap || sd1 > 0.4 * gap || mid > 0.2 * ringN) return fallback("écart ${gap.toInt()}, parts $n0/$n1 sur $ringN, dispersion ${sd0.toInt()}/${sd1.toInt()}, intermédiaires $mid")
        // cohérence : peu de composantes de taille notable (une trame de points ou un dessin fin en ont beaucoup, de petites)
        val cls = ByteArray(n)   // 0 : hors anneau, 1 : sombre, 2 : clair
        for (i in 0 until n) {
            val d = dm[i]; if (d !in 1..ringW || (valid != null && !valid[i])) continue
            val y = lum(pixels[i]).toDouble()
            cls[i] = if (y <= lo) 1 else if (y >= hi) 2 else 0
        }
        val seen = BooleanArray(n); var comps = 0; var keptN = 0; var have1 = false; var have2 = false
        val keep = BooleanArray(n)
        for (s in 0 until n) {
            if (cls[s].toInt() == 0 || seen[s]) continue
            val c = cls[s]; var qh = 0; var qt = 0; queue[qt++] = s; seen[s] = true
            while (qh < qt) {
                val i = queue[qh++]; val x = i % w; val y = i / w
                if (x > 0 && !seen[i - 1] && cls[i - 1] == c) { seen[i - 1] = true; queue[qt++] = i - 1 }
                if (x < w - 1 && !seen[i + 1] && cls[i + 1] == c) { seen[i + 1] = true; queue[qt++] = i + 1 }
                if (y > 0 && !seen[i - w] && cls[i - w] == c) { seen[i - w] = true; queue[qt++] = i - w }
                if (y < h - 1 && !seen[i + w] && cls[i + w] == c) { seen[i + w] = true; queue[qt++] = i + w }
            }
            if (qt >= 14) { comps++; keptN += qt; if (c.toInt() == 1) have1 = true else have2 = true; for (k in 0 until qt) keep[queue[k]] = true }
        }
        var classified = 0; for (i in 0 until n) if (cls[i].toInt() != 0) classified++
        // les pixels de l'anneau appartiennent en grande majorité à ces grandes régions (pas à des points et des traits épars)
        if (!have1 || !have2 || comps > 6 || keptN < 0.7 * classified) return fallback("$comps régions notables, ${keptN * 100 / max(1, classified)} % des pixels")
        // carte clair / sombre des voisins (seules les grandes composantes votent), prolongée en lissant ; couleurs de chaque côté prolongées séparément
        val ind = IntArray(n); val v0 = BooleanArray(n); val v1 = BooleanArray(n)
        for (i in 0 until n) {
            val d = dm[i]; if (d < 1 || (valid != null && !valid[i])) continue
            val y = lum(pixels[i]).toDouble()
            if (keep[i]) ind[i] = if (cls[i].toInt() == 2) -1 else (0xFF shl 24)
            if (y <= lo) v0[i] = true else if (y >= hi) v1[i] = true
        }
        val field = inpaintSmooth(ind, w, h, mask, keep, iters)
        // le bord vu dans l'anneau (pixels dont le voisinage 5×5 contient les deux côtés) doit être un trait droit, et sa prolongation dans le trou doit rester sur ce trait : un seul côté
        // qui touche le trou, un contour courbe, des traits épars donneraient une « bosse » ou des cellules (la frontière de l'interpolation n'est alors plus le prolongement du bord)
        var ifN = 0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in 0 until n) {
            val d = dm[i]; if (d < 1 || d > ringW) continue
            val x = i % w; val y = i / w
            var c1 = 0; var c2 = 0
            for (dy in -2..2) { val yy = y + dy; if (yy < 0 || yy >= h) continue
                for (dx in -2..2) { val xx = x + dx; if (xx < 0 || xx >= w) continue
                    val j = yy * w + xx; if (!keep[j]) continue
                    if (cls[j].toInt() == 1) c1++ else c2++ } }
            if (c1 >= 2 && c2 >= 2) { ifN++; sx += x; sy += y; sxx += x.toDouble() * x; syy += y.toDouble() * y; sxy += x.toDouble() * y }
        }
        if (ifN < 20) return fallback("bord à peine visible autour du trou ($ifN px)")
        val mx = sx / ifN; val my = sy / ifN
        val cxx = sxx / ifN - mx * mx; val cyy = syy / ifN - my * my; val cxy = sxy / ifN - mx * my
        val half = (cxx + cyy) / 2; val rad = sqrt(max(0.0, half * half - (cxx * cyy - cxy * cxy)))
        val sdAlong = sqrt(max(0.0, half + rad)); val sdAcross = sqrt(max(0.0, half - rad))
        if (sdAlong < 4.0 || sdAcross > max(2.5, 0.15 * sdAlong)) return fallback("le bord vu autour du trou n'est pas un trait droit (longueur ${sdAlong.toInt()}, épaisseur ${sdAcross.toInt()})")
        val theta = 0.5 * kotlin.math.atan2(2 * cxy, cxx - cyy)
        val nx = -kotlin.math.sin(theta); val ny = kotlin.math.cos(theta)
        var hx0 = w; var hx1 = -1; var hy0 = h; var hy1 = -1
        for (i in 0 until n) if (mask[i]) { val x = i % w; val y = i / w; if (x < hx0) hx0 = x; if (x > hx1) hx1 = x; if (y < hy0) hy0 = y; if (y > hy1) hy1 = y }
        val ext = max(hx1 - hx0 + 1, hy1 - hy0 + 1).toDouble()
        // le bord doit au moins toucher le trou : si le trou est tout entier d'un seul côté, prolonger l'autre couleur y ferait une bosse, le remplissage lisse est meilleur
        var holeN = 0; var posN = 0; var negN = 0
        for (i in 0 until n) if (mask[i]) { holeN++; val sd = (i % w - mx) * nx + (i / w - my) * ny; if (sd > 1.5) posN++ else if (sd < -1.5) negN++ }
        if (min(posN, negN) < 0.02 * holeN) return fallback("le bord ne touche pas le trou (${posN * 100 / max(1, holeN)} % / ${negN * 100 / max(1, holeN)} % de chaque côté)")
        val alpha = FloatArray(n)
        var midN = 0; var devSum = 0.0; var devMax = 0.0
        for (i in 0 until n) if (mask[i]) {
            val a = ((r(field[i]) - 127.5f) / 30f + 0.5f).coerceIn(0f, 1f); alpha[i] = a
            if (a > 0.25f && a < 0.75f) {
                val dev = kotlin.math.abs((i % w - mx) * nx + (i / w - my) * ny)
                midN++; devSum += dev; if (dev > devMax) devMax = dev
            }
        }
        if (midN > 0 && (devSum / midN > max(4.0, 0.15 * ext) || devMax > max(10.0, 0.3 * ext))) return fallback("la frontière dans le trou s'écarte du bord (moyenne ${(devSum / midN).toInt()} px, maximum ${devMax.toInt()} px, trou $ext px)")
        val f0 = inpaintSmooth(pixels, w, h, mask, v0, iters)
        val f1 = inpaintSmooth(pixels, w, h, mask, v1, iters)
        val out = pixels.copyOf()
        for (i in 0 until n) if (mask[i]) {
            val a = alpha[i]
            val c0 = f0[i]; val c1 = f1[i]
            out[i] = pack((r(c0) + (r(c1) - r(c0)) * a).toInt(), (g(c0) + (g(c1) - g(c0)) * a).toInt(), (b(c0) + (b(c1) - b(c0)) * a).toInt())
        }
        Erasure.trace?.invoke("remplissage à bord net : sombre ${m0.toInt()} / clair ${m1.toInt()} (écart ${gap.toInt()}, anneau $ringN px, $comps régions, bord $ifN px longueur ${sdAlong.toInt()} épaisseur ${sdAcross.toInt()}, trou ${posN * 100 / max(1, holeN)} % / ${negN * 100 / max(1, holeN)} %, frontière $midN px écart ${if (midN > 0) (devSum / midN).toInt() else 0})")
        return out
    }

    /**
     * Structure du fond autour du texte : part des pixels de référence (marge de la zone hors texte, et bande de 10 px autour de la zone) qui portent un bord franc
     * (gradient de luminance > 60, puis > 30). Un dégradé, un ombrage, un fond flou ou bruité ont une part quasi nulle ; un dessin (contours, cheveux, motifs) en a une nette.
     * [text] : le texte lu (boîtes des lignes un peu élargies), dont les propres bords ne comptent pas. [limit] : restreint les pixels de référence (intérieur d'une bulle).
     */
    fun structure(pixels: IntArray, w: Int, h: Int, zone: BooleanArray, text: BooleanArray, limit: BooleanArray? = null, surface: IntArray? = null): FloatArray {
        val n = w * h
        // boîte de la zone élargie de 11 px : la bande de référence n'en sort pas, inutile de parcourir toute la région
        var bx0 = w; var bx1 = -1; var by0 = h; var by1 = -1
        for (i in 0 until n) if (zone[i]) { val x = i % w; val y = i / w; if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y }
        if (bx1 < 0) return floatArrayOf(0f, 0f, 0f)
        val rx0 = maxOf(1, bx0 - 11); val rx1 = minOf(w - 2, bx1 + 11); val ry0 = maxOf(1, by0 - 11); val ry1 = minOf(h - 2, by1 + 11)
        val lum = IntArray(n)
        for (y in maxOf(0, ry0 - 1)..minOf(h - 1, ry1 + 1)) for (x in maxOf(0, rx0 - 1)..minOf(w - 1, rx1 + 1)) { val c = pixels[y * w + x]; lum[y * w + x] = (r(c) * 77 + g(c) * 150 + b(c) * 29) shr 8 }
        val dm = IntArray(n) { if (zone[it]) 0 else Int.MAX_VALUE }
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in 0 until n) if (zone[i]) queue[tail++] = i
        while (head < tail) {
            val i = queue[head++]; val d = dm[i]; if (d >= 10) continue
            val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (dm[j] == Int.MAX_VALUE) { dm[j] = d + 1; queue[tail++] = j }
            }
        }
        var tot = 0; var e60 = 0; var e30 = 0
        var refN = 0; val refColsArr = IntArray(n); val refIdxArr = IntArray(n)
        // dans une bulle, la frange de 3 px au bord de l'intérieur est écartée : son voisin est le contour, dont le bord n'est pas une structure du fond
        val lim = if (limit != null) erode(limit, w, h, 3) else null
        for (y in ry0..ry1) for (x in rx0..rx1) {
            val i = y * w + x
            val inRef = (zone[i] && !text[i]) || (!zone[i] && dm[i] in 1..10)
            if (!inRef || (lim != null && !lim[i])) continue
            refIdxArr[refN] = i; refColsArr[refN++] = pixels[i]
            // gradient de Sobel ; un voisin appartenant au texte fausserait le bord : on l'ignore
            if (text[i - 1] || text[i + 1] || text[i - w] || text[i + w]) continue
            val gx = -lum[i - w - 1] + lum[i - w + 1] - 2 * lum[i - 1] + 2 * lum[i + 1] - lum[i + w - 1] + lum[i + w + 1]
            val gy = -lum[i - w - 1] - 2 * lum[i - w] - lum[i - w + 1] + lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1]
            val mag = kotlin.math.sqrt((gx * gx + gy * gy).toFloat()) / 4f
            tot++; if (mag > 60f) e60++; if (mag > 30f) e30++
        }
        if (tot < 80 || refN < 80) return floatArrayOf(0f, 0f, 0f)
        // masse de pixels très différents de la couleur médiane de référence : un aplat de zone les effacerait (panneau sombre dans une zone claire)
        fun med(sh: Int): Int { val a = IntArray(refN) { (refColsArr[it] shr sh) and 0xFF }; a.sort(); return a[a.size / 2] }
        val mr = med(16); val mg = med(8); val mb = med(0)
        var far = 0
        // avec une surface lisse (dégradé ajusté), l'écart se mesure à la surface au même endroit, pas à la couleur médiane : un dégradé n'est pas de la « masse »
        for (k in 0 until refN) {
            val c = refColsArr[k]
            val sf = if (surface != null) surface[refIdxArr[k]] else -1
            val rr = if (surface != null) r(sf) else mr; val rg = if (surface != null) g(sf) else mg; val rb = if (surface != null) b(sf) else mb
            if (maxOf(abs(((c shr 16) and 0xFF) - rr), abs(((c shr 8) and 0xFF) - rg), abs((c and 0xFF) - rb)) > 50) far++
        }
        return floatArrayOf(e60.toFloat() / tot, e30.toFloat() / tot, far.toFloat() / refN)
    }

    /**
     * Remplissage d'une zone effacée. Si le fond autour est vraiment uni (couleur médiane des pixels voisins, écart faible et pas de dégradé d'un côté à l'autre), renvoie null :
     * l'aplat de couleur unie est exact et reste le comportement d'origine. Sinon (dégradé, ombrage, léger bruit, fond coloré non uni) renvoie une reconstruction qui suit le
     * fond autour de la zone, avec le grain du fond : l'aplat, lui, se verrait comme un carré d'une autre teinte.
     */
    fun zoneFill(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, bg: Int): IntArray? {
        val n = w * h
        // bande de pixels voisins hors zone (jusqu'à 10 px) et proches du fond
        val dm = IntArray(n) { if (mask[it]) 0 else Int.MAX_VALUE }
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in 0 until n) if (mask[i]) queue[tail++] = i
        if (tail == 0 || tail == n) return null
        while (head < tail) {
            val i = queue[head++]; val d = dm[i]; if (d >= 10) continue
            val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (dm[j] == Int.MAX_VALUE) { dm[j] = d + 1; queue[tail++] = j }
            }
        }
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        for (i in 0 until n) if (mask[i]) { val x = i % w; val y = i / w; if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        val bandArr = IntArray(n); var bandN = 0; val valid = BooleanArray(n)
        for (i in 0 until n) if (dm[i] in 1..10 && dist(pixels[i], bg) <= 70) { bandArr[bandN++] = i; valid[i] = true }
        if (bandN < 60) return null
        // fond blanc : seules les références vraiment blanches comptent. Les bords roses du texte, l'ombre ou le dessin sombre qui déborde d'une étiquette (jusqu'à 70 niveaux du blanc)
        // salissaient le remplissage : une étiquette blanche était repeinte en gris moucheté
        val whiteBg = ((bg shr 16) and 0xFF) >= 235 && ((bg shr 8) and 0xFF) >= 235 && (bg and 0xFF) >= 235
        if (whiteBg) {
            var tight = 0; for (k in 0 until bandN) if (dist(pixels[bandArr[k]], bg) <= 24) tight++
            if (tight >= 80 && tight >= 0.5f * bandN) {
                var kept = 0
                for (k in 0 until bandN) { val i = bandArr[k]; if (dist(pixels[i], bg) <= 24) bandArr[kept++] = i else valid[i] = false }
                bandN = kept
            }
        }
        fun medOf(a: IntArray, cnt: Int, sh: Int): Int { val t = IntArray(cnt) { (a[it] shr sh) and 0xFF }; t.sort(); return t[cnt / 2] }
        val cols = IntArray(bandN) { pixels[bandArr[it]] }
        val mr = medOf(cols, bandN, 16); val mg = medOf(cols, bandN, 8); val mb = medOf(cols, bandN, 0)
        // fond blanc dont la bande voisine n'est pas blanche : c'est un bord (contour flou d'une bulle que l'effacement atteint, ombre), pas le fond. Remplir avec cette couleur
        // repeignait une bulle blanche en gris (200) : seul l'aplat blanc est juste
        if (whiteBg && maxOf(abs(mr - ((bg shr 16) and 0xFF)), abs(mg - ((bg shr 8) and 0xFF)), abs(mb - (bg and 0xFF))) > 24) {
            Erasure.trace?.invoke("zoneFill : bande ni blanche ni du fond (médiane=${"%02X%02X%02X".format(mr, mg, mb)}) → aplat du fond")
            return null
        }
        val madR = IntArray(bandN) { abs(((cols[it] shr 16) and 0xFF) - mr) }; madR.sort()
        val madG = IntArray(bandN) { abs(((cols[it] shr 8) and 0xFF) - mg) }; madG.sort()
        val madB = IntArray(bandN) { abs((cols[it] and 0xFF) - mb) }; madB.sort()
        val mad = maxOf(madR[bandN / 2], madG[bandN / 2], madB[bandN / 2])
        // dégradé : médianes des quatre côtés de la zone
        val quad = Array(4) { IntArray(bandN) }; val quadN = IntArray(4)
        for (k in 0 until bandN) {
            val i = bandArr[k]; val x = i % w; val y = i / w
            val q = if (y < minY) 0 else if (y > maxY) 1 else if (x < minX) 2 else if (x > maxX) 3 else -1
            if (q >= 0) quad[q][quadN[q]++] = pixels[i]
        }
        var spread = 0
        val meds = (0..3).filter { quadN[it] >= 12 }.map { q -> intArrayOf(medOf(quad[q], quadN[q], 16), medOf(quad[q], quadN[q], 8), medOf(quad[q], quadN[q], 0)) }
        for (c in 0..2) { if (meds.size >= 2) spread = maxOf(spread, meds.maxOf { it[c] } - meds.minOf { it[c] }) }
        val flat = mad <= 3 && spread <= 6
        Erasure.trace?.invoke("zoneFill : mad=$mad écart des côtés=$spread bande=$bandN médiane=${"%02X%02X%02X".format(mr, mg, mb)} → ${if (flat) "aplat exact" else "fond lissé + grain"}")
        if (flat) return null
        val smooth = inpaintSmooth(pixels, w, h, mask, valid)
        return addGrain(smooth, pixels, w, h, mask)
    }

    /**
     * Le fond que le masque va recouvrir est-il uni ? Les pixels du masque qui ne sont ni des lettres ni leur frange (`ink`) sont presque tous de la couleur de fond (à 10 niveaux près) :
     * le remplissage est alors cette couleur, exactement, quoi que borde le masque. Quand l'effacement atteint le contour d'une bulle (boîte de lecture bien plus haute que le gros
     * lettrage), la bande voisine est le bord flou du contour, pas le fond : la reconstruction « qui suit le fond » repeignait l'intérieur de la bulle en gris (blanche) ou en plus clair (sombre).
     */
    fun uniformBackground(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, ink: BooleanArray, bg: Int): Boolean {
        var n = 0; var near = 0
        for (i in 0 until w * h) if (mask[i] && !ink[i]) { n++; if (dist(pixels[i], bg) <= 10) near++ }
        return n >= 400 && near >= 0.97f * n
    }

    /** Remplace les pixels masqués par une reconstruction lisse du fond (diffusion multi-échelle depuis les pixels voisins non masqués). */
    fun inpaint(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, flatColor: Int? = null): IntArray {
        val out = pixels.copyOf()
        if (flatColor != null) { for (i in out.indices) if (mask[i]) out[i] = flatColor; return out }
        val unknown = mask.count { it }
        if (unknown == 0) return out
        if (unknown == w * h) return IntArray(w * h) { 0xFF808080.toInt() }
        // initialisation : moyenne des pixels connus
        var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
        for (i in pixels.indices) if (!mask[i]) { sr += r(pixels[i]); sg += g(pixels[i]); sb += b(pixels[i]); n++ }
        val fr = FloatArray(w * h); val fg = FloatArray(w * h); val fb = FloatArray(w * h)
        for (i in pixels.indices) { fr[i] = if (mask[i]) sr.toFloat() / n else r(pixels[i]).toFloat(); fg[i] = if (mask[i]) sg.toFloat() / n else g(pixels[i]).toFloat(); fb[i] = if (mask[i]) sb.toFloat() / n else b(pixels[i]).toFloat() }
        // relaxation de Gauss-Seidel : propage les couleurs du bord vers l'intérieur
        val iters = min(400, 40 + 6 * max(w, h))
        repeat(iters) {
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (!mask[i]) continue
                var cnt = 0; var ar = 0f; var ag = 0f; var ab = 0f
                if (x > 0) { ar += fr[i - 1]; ag += fg[i - 1]; ab += fb[i - 1]; cnt++ }
                if (x < w - 1) { ar += fr[i + 1]; ag += fg[i + 1]; ab += fb[i + 1]; cnt++ }
                if (y > 0) { ar += fr[i - w]; ag += fg[i - w]; ab += fb[i - w]; cnt++ }
                if (y < h - 1) { ar += fr[i + w]; ag += fg[i + w]; ab += fb[i + w]; cnt++ }
                fr[i] = ar / cnt; fg[i] = ag / cnt; fb[i] = ab / cnt
            }
        }
        for (i in out.indices) if (mask[i]) out[i] = pack(fr[i].toInt(), fg[i].toInt(), fb[i].toInt())
        return out
    }

    /** Alpha adouci pour les bords d'une zone reconstruite sur un dessin (255 au centre, dégradé sur 3 px). */
    /**
     * Trame de points (ou tout motif périodique) : cherche le décalage (sx, sy) pour lequel l'image se ressemble le mieux à elle-même hors du masque,
     * puis comble chaque pixel masqué en recopiant le pixel situé un ou plusieurs décalages plus loin. Renvoie le remplissage (les pixels sans source
     * valable gardent la valeur de [fallback]) ou null si l'image n'a pas de motif périodique net autour du trou.
     */
    fun periodicFill(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, fallback: IntArray, maxShift: Int = 24): IntArray? {
        val n = w * h
        val gray = IntArray(n) { val c = pixels[it]; (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8 }
        // échantillon de pixels hors masque (au plus ~5000)
        val free = ArrayList<Int>(); for (i in 0 until n) if (!mask[i]) free.add(i)
        if (free.size < 600) return null
        val stride = max(1, free.size / 5000)
        val sample = ArrayList<Int>(); var k = 0; while (k < free.size) { sample.add(free[k]); k += stride }
        var mean = 0.0; for (i in sample) mean += gray[i]; mean /= sample.size
        var dev = 0.0; for (i in sample) dev += abs(gray[i] - mean); dev /= sample.size
        if (dev < 3.0) return null                                   // fond quasi uni : le lissage suffit
        // écart moyen entre l'image et elle-même décalée de (sx, sy), hors masque
        val cache = HashMap<Int, Double>()
        fun mad(sx: Int, sy: Int): Double = cache.getOrPut((sy + 64) * 257 + (sx + 128)) {
            var sum = 0L; var cnt = 0
            for (i in sample) {
                val x = i % w + sx; val y = i / w + sy
                if (x < 0 || y < 0 || x >= w || y >= h) continue
                val j = y * w + x
                if (mask[j]) continue
                sum += abs(gray[i] - gray[j]); cnt++
            }
            if (cnt < 300) Double.MAX_VALUE else sum.toDouble() / cnt
        }
        var bestMad = Double.MAX_VALUE; var bsx = 0; var bsy = 0; var bestRatio = 0.0
        for (sy in 0..maxShift) for (sx in -maxShift..maxShift) {
            if (sy == 0 && sx <= 0) continue
            if (abs(sx) + sy < 4) continue
            val m = mad(sx, sy); if (m == Double.MAX_VALUE) continue
            // un vrai motif périodique colle bien à un décalage d'une période et mal à une demi-période ; une image simplement lisse colle de moins en moins avec la distance
            val half = mad((sx / 2.0).let { Math.round(it).toInt() }, (sy / 2.0).let { Math.round(it).toInt() })
            if (half == Double.MAX_VALUE) continue
            val ratio = half / max(m, 0.5)
            if (ratio > bestRatio || (ratio >= bestRatio * 0.98 && m < bestMad)) { bestRatio = ratio; bestMad = m; bsx = sx; bsy = sy }
        }
        if (bestMad == Double.MAX_VALUE || bestRatio < 1.7 || bestMad > 0.35 * dev || bestMad > 14.0) return null     // pas de motif périodique net
        val out = fallback.copyOf(); var found = 0; var total = 0
        for (i in 0 until n) {
            if (!mask[i]) continue
            total++
            val x = i % w; val y = i / w
            var done = false
            for (kk in 1..10) {
                for (sgn in intArrayOf(1, -1)) {
                    val qx = x + sgn * kk * bsx; val qy = y + sgn * kk * bsy
                    if (qx < 0 || qy < 0 || qx >= w || qy >= h) continue
                    val q = qy * w + qx
                    if (!mask[q]) { out[i] = pixels[q]; found++; done = true; break }
                }
                if (done) break
            }
        }
        return if (total == 0 || found < 0.85f * total) null else out
    }


    /**
     * Grain : sur un fond à trame irrégulière ou à texture, un remplissage lissé se voit comme un voile gris. On y ajoute un bruit de même intensité que
     * le grain des abords (écart entre l'image et sa version floutée, mesuré hors masque), sans effet sur un fond lisse.
     */
    fun addGrain(fill: IntArray, pixels: IntArray, w: Int, h: Int, mask: BooleanArray, seed: Long = 7L): IntArray {
        val n = w * h
        val gray = IntArray(n) { val c = pixels[it]; (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8 }
        // flou de boîte 5×5 (images intégrales) pour isoler le grain
        val integ = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) { var row = 0L; for (x in 0 until w) { row += gray[y * w + x]; integ[(y + 1) * (w + 1) + x + 1] = integ[y * (w + 1) + x + 1] + row } }
        fun blur(x: Int, y: Int): Double {
            val xa = max(0, x - 2); val xb = min(w, x + 3); val ya = max(0, y - 2); val yb = min(h, y + 3)
            val sum = integ[yb * (w + 1) + xb] - integ[ya * (w + 1) + xb] - integ[yb * (w + 1) + xa] + integ[ya * (w + 1) + xa]
            return sum.toDouble() / ((xb - xa) * (yb - ya))
        }
        // abords : pixels hors masque à moins de ~40 px du masque et sans gros contour (le grain, pas les bords de dessin)
        val near = dilate(mask, w, h, 40)
        val devs = ArrayList<Float>()
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            val i = y * w + x
            if (mask[i] || !near[i]) continue
            val d = abs(gray[i] - blur(x, y))
            if (d < 40) devs.add(d.toFloat())
        }
        if (devs.size < 300) return fill
        // écart absolu médian (robuste) : les bords de dessin, rares, ne gonflent plus le grain mesuré ; un fond lisse (dégradé sombre) donne un grain nul
        devs.sort()
        val sigma = devs[devs.size / 2] * 1.4826 / 0.67      // l'écart au flou 5×5 sous-estime le bruit : correction empirique
        if (sigma < 3.0) return fill
        val rnd = java.util.Random(seed); val out = fill.copyOf()
        val amp = sigma * 0.9
        for (i in 0 until n) if (mask[i]) {
            val d = (rnd.nextGaussian() * amp).toInt(); val c = fill[i]
            val r = (((c shr 16) and 0xFF) + d).coerceIn(0, 255); val g = (((c shr 8) and 0xFF) + d).coerceIn(0, 255); val b = ((c and 0xFF) + d).coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    fun featherAlpha(mask: BooleanArray, w: Int, h: Int): IntArray {
        fun erode(src: BooleanArray): BooleanArray {
            val o = BooleanArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                o[i] = src[i] && x > 0 && y > 0 && x < w - 1 && y < h - 1 && src[i - 1] && src[i + 1] && src[i - w] && src[i + w]
            }
            return o
        }
        val e1 = erode(mask); val e2 = erode(e1); val e3 = erode(e2)
        return IntArray(w * h) { i -> if (e3[i]) 255 else if (e2[i]) 210 else if (e1[i]) 140 else if (mask[i]) 70 else 0 }
    }

    /** Groupes de rangées contiguës contenant des pixels masqués : (première rangée, dernière rangée). */
    private fun rowGroups(mask: BooleanArray, w: Int, h: Int): List<IntArray> {
        val out = ArrayList<IntArray>(); var start = -1; var gap = 0
        for (y in 0 until h) {
            val has = (0 until w).any { mask[y * w + it] }
            if (has) { if (start < 0) start = y; gap = 0 } else if (start >= 0) { gap++; if (gap >= 3) { out.add(intArrayOf(start, y - gap)); start = -1; gap = 0 } }
        }
        if (start >= 0) out.add(intArrayOf(start, h - 1 - gap.coerceAtLeast(0)))
        return out
    }

    /**
     * Reconstruction « tampon de duplication » : chaque groupe de lignes effacé est rempli par de la vraie texture copiée d'à côté
     * (au-dessus, au-dessous, à gauche ou à droite), en choisissant le décalage dont les abords ressemblent le plus à ceux du trou,
     * avec raccord de luminosité. Retombe sur la diffusion [diffused] si aucun décalage ne convient.
     */
    fun inpaintClone(pixels: IntArray, w: Int, h: Int, mask: BooleanArray, diffused: IntArray): IntArray {
        val out = diffused.copyOf()
        val remaining = mask.copyOf()
        val cur = pixels.copyOf() // pixels courants : les groupes déjà remplis servent de source aux suivants
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
        for (g in rowGroups(mask, w, h)) {
            val gm = BooleanArray(w * h)
            var minX = w; var maxX = -1
            for (y in g[0]..g[1]) for (x in 0 until w) if (mask[y * w + x]) { gm[y * w + x] = true; if (x < minX) minX = x; if (x > maxX) maxX = x }
            if (maxX < 0) continue
            val gw = maxX - minX + 1; val gh = g[1] - g[0] + 1
            val m = max(4, gh / 10)
            val band = dilate(gm, w, h, 3)
            for (i in band.indices) if (gm[i] || remaining[i]) band[i] = false
            val bandIdx = band.indices.filter { band[it] }
            if (bandIdx.size < 20) { for (i in gm.indices) if (gm[i]) remaining[i] = false; continue }
            var bestCost = Double.MAX_VALUE; var bdx = 0; var bdy = 0
            for ((dx, dy) in listOf(0 to -(gh + m), 0 to (gh + m), -(gw + m) to 0, (gw + m) to 0)) {
                var cost = 0L; var cnt = 0
                for (q in bandIdx) {
                    val sx = q % w + dx; val sy = q / w + dy
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h || remaining[sy * w + sx] || gm[sy * w + sx]) continue
                    val a = cur[q]; val b2 = cur[sy * w + sx]
                    cost += abs(ch(a, 16) - ch(b2, 16)) + abs(ch(a, 8) - ch(b2, 8)) + abs(ch(a, 0) - ch(b2, 0)); cnt++
                }
                if (cnt < bandIdx.size * 0.4) continue
                val avg = cost.toDouble() / (cnt * 3)
                if (avg < bestCost) { bestCost = avg; bdx = dx; bdy = dy }
            }
            if (bestCost <= 38.0) {
                // raccord de luminosité : écart MÉDIAN entre l'anneau du trou et l'anneau correspondant de la source. La moyenne était tirée par un trait sombre voisin du trou
                // et assombrissait toute la copie (une page blanche reconstruite en gris)
                val dR = ArrayList<Int>(); val dG = ArrayList<Int>(); val dB = ArrayList<Int>()
                for (q in bandIdx) {
                    val sx = q % w + bdx; val sy = q / w + bdy
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h || remaining[sy * w + sx] || gm[sy * w + sx]) continue
                    val a = cur[q]; val b2 = cur[sy * w + sx]
                    dR.add(ch(a, 16) - ch(b2, 16)); dG.add(ch(a, 8) - ch(b2, 8)); dB.add(ch(a, 0) - ch(b2, 0))
                }
                fun med(l: ArrayList<Int>): Int { if (l.isEmpty()) return 0; l.sort(); return l[l.size / 2] }
                val orr = med(dR); val og = med(dG); val ob = med(dB)
                for (i in gm.indices) {
                    if (!gm[i]) continue
                    val sx = i % w + bdx; val sy = i / w + bdy
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h || remaining[sy * w + sx] || gm[sy * w + sx]) continue
                    val c = cur[sy * w + sx]
                    val cand = pack(ch(c, 16) + orr, ch(c, 8) + og, ch(c, 0) + ob)
                    // une copie très différente du remplissage de base (un bout de trait sombre dans un fond clair, par exemple) n'est pas de la texture : on garde le remplissage de base
                    val dd = diffused[i]
                    if (abs(ch(cand, 16) - ch(dd, 16)) > 70 || abs(ch(cand, 8) - ch(dd, 8)) > 70 || abs(ch(cand, 0) - ch(dd, 0)) > 70) continue
                    out[i] = cand
                }
            }
            for (i in gm.indices) if (gm[i]) { remaining[i] = false; cur[i] = out[i] }
        }
        return out
    }

    enum class FontStyle { SPEECH, SHOUT, SANS, NARRATION }

    /**
     * Choisit une famille de polices proche du texte d'origine :
     * - SHOUT : cris et onomatopées (majuscules, grande taille, ou texte libre cerné d'un contour) ;
     * - SANS : écritures CJK d'origine (gothique net) ;
     * - NARRATION : encadré rectangulaire ;
     * - SPEECH : parole classique de bulle.
     */
    fun chooseFont(source: String, lineHeight: Float, pageMedianLineHeight: Float, uniform: Boolean, outlined: Boolean, rectangular: Boolean): FontStyle {
        val letters = source.filter { it.isLetter() }
        val cjk = ScriptDetector.detect(source) != "en"
        val upper = if (letters.isEmpty() || cjk) 0f else letters.count { it.isUpperCase() }.toFloat() / letters.length
        val big = pageMedianLineHeight > 0 && lineHeight >= 1.5f * pageMedianLineHeight
        return when {
            // grand lettrage d'effet (cri, onomatopée) : police de cri ; un texte hors bulle de taille normale, même cerné, passe par l'assortiment de police
            big && (upper > 0.6f || source.trimEnd().endsWith("!")) -> FontStyle.SHOUT
            !uniform && outlined && !cjk && big -> FontStyle.SHOUT
            uniform && rectangular && !big -> FontStyle.NARRATION
            cjk -> FontStyle.SANS
            else -> FontStyle.SPEECH
        }
    }

    @Suppress("unused") private fun sqrtSafe(v: Double) = sqrt(v)
}
