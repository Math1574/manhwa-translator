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
    private fun dist(a: Int, c: Int) = maxOf(abs(r(a) - r(c)), abs(g(a) - g(c)), abs(b(a) - b(c)))
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
     * @param pixels région ARGB (w*h)
     * @param allowed zone où le texte peut se trouver (lignes OCR agrandies) : rien n'est effacé en dehors
     * Le fond est la couleur dominante de la zone ; l'uniformité est mesurée sur le cadre de 6 px autour de la zone.
     */
    fun analyze(pixels: IntArray, w: Int, h: Int, allowed: BooleanArray, grow: Int = 2): Analysis {
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
        for (i in pixels.indices) if (frame[i] && !allowed[i]) { fc++; if (dist(pixels[i], bg) <= 14) fn++ }
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
    ): BooleanArray {
        val raw = BooleanArray(w * h) { dist(pixels[it], bg) > thr }
        val seen = BooleanArray(w * h)
        val keep = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val maxArea = w * h / 2
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
                for (k in 0 until tail) { val q = queue[k]; if (opened[q]) keep[q] = true else thin?.set(q, true) }
                continue
            }
            when {
                // lettre isolée : effacée entièrement, même si elle dépasse la boîte de l'OCR (mot omis, lettre débordante)
                enclosed && (inAllowed || inInterior * 2 >= tail) -> for (k in 0 until tail) keep[queue[k]] = true
                // composante reliée au bord ou à un contour (lettre collée au contour) : seulement le cœur des lignes de texte
                inAllowed && core != null -> for (k in 0 until tail) if (core[queue[k]]) keep[queue[k]] = true
            }
        }
        return dilate(keep, w, h, grow)
    }

    /** Épaisseur moyenne du trait (pixels) d'un masque de texte : 2 × aire / périmètre (exact pour un trait fin de longueur ≫ épaisseur). */
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
    fun extremeTextColor(pixels: IntArray, allowed: BooleanArray): Int {
        val idx = pixels.indices.filter { allowed[it] }
        if (idx.isEmpty()) return 0xFF000000.toInt()
        val sorted = idx.sortedBy { lum(pixels[it]) }
        val n = max(1, sorted.size / 10)
        val dark = sorted.take(n).map { pixels[it] }; val bright = sorted.takeLast(n).map { pixels[it] }
        val med = lum(pixels[sorted[sorted.size / 2]])
        val dd = med - dark.map { lum(it) }.average().toFloat(); val db = bright.map { lum(it) }.average().toFloat() - med
        val pick = if (db >= dd) bright else dark
        return pack(pick.sumOf { r(it) } / pick.size, pick.sumOf { g(it) } / pick.size, pick.sumOf { b(it) } / pick.size)
    }

    /**
     * Pixels de texte sur un dessin : remplissage des lettres (couleur proche de [textColor]) et leur contour (couleur dominante des
     * pixels voisins du remplissage), légèrement dilatés. Contrairement à un rectangle, on n'efface que les lettres : le dessin
     * autour est conservé, et une éventuelle lueur diffuse reste (la traduction est redessinée par-dessus avec son propre contour).
     */
    fun artMask(pixels: IntArray, w: Int, h: Int, allowed: BooleanArray, textColor: Int, grow: Int, maxDim: Int = Int.MAX_VALUE, tight: BooleanArray? = null, maxReach: Int = 6): BooleanArray =
        artMaskInfo(pixels, w, h, allowed, textColor, grow, maxDim, tight, maxReach).mask

    /** Masque du texte sur un dessin et résultat de la mesure du contour (le texte est-il cerné d'un trait contrasté ?). */
    class ArtMask(val mask: BooleanArray, val outlined: Boolean)

    fun artMaskInfo(pixels: IntArray, w: Int, h: Int, allowed: BooleanArray, textColor: Int, grow: Int, maxDim: Int = Int.MAX_VALUE, tight: BooleanArray? = null, maxReach: Int = 6): ArtMask {
        // lettres = composantes isolées : une grande surface de couleur proche (fond d'un encadré voisin, bas d'une bulle, cheveux blancs) n'est pas une lettre.
        // L'isolement se juge sur toute la région (et non sur la seule zone autorisée, dont le bord coupe ces grandes surfaces et les ferait paraître isolées),
        // puis on écarte toute composante plus grande qu'une lettre ([maxDim] : quelques hauteurs de ligne).
        val color = BooleanArray(w * h) { dist(pixels[it], textColor) <= 70 }
        val fill = enclosedMostlyIn(color, allowed, w, h, 0.7f)
        // lettres dont le remplissage touche une grande surface de même couleur (texte posé sur le bord d'une bulle blanche) : écartées ci-dessus avec la bulle. On les
        // récupère par leur contour : un pixel clair entouré de pixels sombres (contour noir de la lettre) est un pixel de lettre, un pixel de bulle ne l'est pas.
        rescueOutlined(color, fill, tight ?: allowed, pixels, w, h, textColor)
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

    /** Retire de [m] les composantes connexes dont la boîte englobante dépasse [maxDim] pixels en largeur ou en hauteur. */
    private fun dropLarge(m: BooleanArray, w: Int, h: Int, maxDim: Int) {
        val seen = BooleanArray(w * h); val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!m[start] || seen[start]) continue
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
                    if (m[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (x1 - x0 + 1 > maxDim || y1 - y0 + 1 > maxDim) for (k in 0 until tail) m[queue[k]] = false
        }
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
                // raccord de luminosité : écart moyen entre l'anneau du trou et l'anneau correspondant de la source
                var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
                for (q in bandIdx) {
                    val sx = q % w + bdx; val sy = q / w + bdy
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h || remaining[sy * w + sx] || gm[sy * w + sx]) continue
                    val a = cur[q]; val b2 = cur[sy * w + sx]
                    sr += ch(a, 16) - ch(b2, 16); sg += ch(a, 8) - ch(b2, 8); sb += ch(a, 0) - ch(b2, 0); n++
                }
                val orr = if (n > 0) (sr / n).toInt() else 0; val og = if (n > 0) (sg / n).toInt() else 0; val ob = if (n > 0) (sb / n).toInt() else 0
                for (i in gm.indices) {
                    if (!gm[i]) continue
                    val sx = i % w + bdx; val sy = i / w + bdy
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h || remaining[sy * w + sx] || gm[sy * w + sx]) continue
                    val c = cur[sy * w + sx]
                    out[i] = pack(ch(c, 16) + orr, ch(c, 8) + og, ch(c, 0) + ob)
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
