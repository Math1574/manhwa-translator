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
        interior: BooleanArray? = null, core: BooleanArray? = null,
    ): BooleanArray {
        val raw = BooleanArray(w * h) { dist(pixels[it], bg) > thr }
        val seen = BooleanArray(w * h)
        val keep = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val maxArea = w * h / 2
        for (start in 0 until w * h) {
            if (!raw[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var touchesBorder = false; var inAllowed = false; var inInterior = 0
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesBorder = true
                if (allowed[i]) inAllowed = true
                if (interior != null && interior[i]) inInterior++
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (raw[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            val enclosed = !touchesBorder && tail < maxArea
            when {
                // lettre isolée : effacée entièrement, même si elle dépasse la boîte de l'OCR (mot omis, lettre débordante)
                enclosed && (inAllowed || inInterior * 2 >= tail) -> for (k in 0 until tail) keep[queue[k]] = true
                // composante reliée au bord (contour de la bulle, lettre collée au contour) : seulement le cœur des lignes de texte
                touchesBorder && inAllowed && core != null -> for (k in 0 until tail) if (core[queue[k]]) keep[queue[k]] = true
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
    fun artMask(pixels: IntArray, w: Int, h: Int, allowed: BooleanArray, textColor: Int, grow: Int): BooleanArray {
        // lettres = composantes isolées : une grande surface de couleur proche (fond d'un encadré voisin) touche le bord et est ignorée
        val fill = enclosedOnly(BooleanArray(w * h) { allowed[it] && dist(pixels[it], textColor) <= 70 }, w, h)
        val near = dilate(fill, w, h, 4)
        val ring = ArrayList<Int>()
        for (i in pixels.indices) if (near[i] && !fill[i] && allowed[i]) ring.add(pixels[i])
        val stroke = if (ring.isEmpty()) textColor else ColorMath.median(ring.toIntArray())
        val strokeSame = dist(stroke, textColor) < 50
        val mask = BooleanArray(w * h) { fill[it] || (!strokeSame && near[it] && allowed[it] && dist(pixels[it], stroke) <= 45) }
        return dilate(mask, w, h, grow, allowed)
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
            (!uniform && outlined && !cjk && source.length <= 40) || (big && (upper > 0.6f || source.trimEnd().endsWith("!"))) -> FontStyle.SHOUT
            !uniform && outlined -> FontStyle.SHOUT
            uniform && rectangular && !big -> FontStyle.NARRATION
            cjk -> FontStyle.SANS
            else -> FontStyle.SPEECH
        }
    }

    @Suppress("unused") private fun sqrtSafe(v: Double) = sqrt(v)
}
