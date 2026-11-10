package app.manhwa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import app.manhwa.core.AppSettings
import app.manhwa.core.BarrierShape
import app.manhwa.core.BubbleVerifier
import app.manhwa.core.LayoutArea
import app.manhwa.core.Erasure
import app.manhwa.core.PixelSource
import app.manhwa.core.PlacedLine
import app.manhwa.core.ColorMath
import app.manhwa.core.DebugInfo
import app.manhwa.core.FontMatcher
import app.manhwa.core.FontLibrary
import app.manhwa.core.RenderEdit
import app.manhwa.core.StyleMatcher
import app.manhwa.core.EditApplier
import app.manhwa.core.GlyphRender
import app.manhwa.core.GlyphRenderer
import app.manhwa.core.PageResult
import app.manhwa.core.ReadingOrder
import app.manhwa.core.RectF
import app.manhwa.core.RenderGate
import app.manhwa.core.RowShape
import app.manhwa.core.ShapeFit
import app.manhwa.core.ScriptDetector
import app.manhwa.core.ShapeLayout
import app.manhwa.core.TextMeasurer
import app.manhwa.core.TextNormalizer
import app.manhwa.core.Typography
import app.manhwa.core.TextErase
import app.manhwa.core.TextErase.FontStyle
import app.manhwa.core.TranslatedBlock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Rendu des traductions : pour chaque zone de texte, effacement des pixels du texte d'origine (aplat dans les bulles,
 * reconstruction du fond sur les dessins), puis dessin de la traduction avec une police proche du lettrage d'origine.
 * Le résultat est un petit patch PNG par zone, posé exactement sur l'image dans la page.
 */
class Typefaces(private val ctx: Context) {
    private val cache = HashMap<String, Typeface>()

    /** Familles candidates pour la correspondance avec le lettrage d'origine (polices libres SIL OFL). */
    val comicFamilies = listOf(
        "comicneue", "comicneue-italic", "patrickhandsc", "kalam", "londrina", "lilita", "caveat", "anton",
        "noto-500-100", "noto-700-100", "noto-800-100", "noto-700-80", "noto-800-70", // sans-serif propre : lettrage numérique ou texte libre
    )

    fun family(id: String): Typeface {
        FontLibrary.spec(id)?.let { return library(it) }
        if (id.startsWith("noto-")) { val p = id.split('-'); return noto(p[1].toInt(), p[2].toFloat()) }
        return familyStatic(id)
    }

    /** Police de la bibliothèque : fichier statique, ou fichier variable avec les valeurs de ses axes (graisse, largeur, inclinaison…). */
    private fun library(sp: FontLibrary.Spec): Typeface = synchronized(cache) {
        cache.getOrPut("lib/${sp.id}") {
            try {
                if (sp.axes.isEmpty()) Typeface.createFromAsset(ctx.assets, "fonts/${sp.file}")
                else Typeface.Builder(ctx.assets, "fonts/${sp.file}").setFontVariationSettings(sp.axes).build() ?: Typeface.DEFAULT_BOLD
            } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
        }
    }

    private fun familyStatic(id: String): Typeface = synchronized(cache) {
        cache.getOrPut(id) {
            val file = when (id) {
                "comicneue" -> "ComicNeue-Bold.ttf"; "comicneue-italic" -> "ComicNeue-BoldItalic.ttf"; "patrickhandsc" -> "PatrickHandSC-Regular.ttf"
                "kalam" -> "Kalam-Bold.ttf"; "bangers" -> "Bangers-Regular.ttf"; "londrina" -> "LondrinaSolid-Black.ttf"
                "lilita" -> "LilitaOne-Regular.ttf"; "caveat" -> "CaveatBrush-Regular.ttf"; "anton" -> "Anton-Regular.ttf"
                else -> "ComicNeue-Bold.ttf"
            }
            try { Typeface.createFromAsset(ctx.assets, "fonts/$file") } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
        }
    }

    fun shout(): Typeface = family("bangers")

    /** Noto Sans variable : épaisseur (wght 100-900) et largeur (wdth 62,5-100) ; pour les sources CJK, sans lettrage latin à imiter. */
    fun noto(wght: Int, wdth: Float): Typeface {
        val w = ((wght + 25) / 50) * 50
        val d = (kotlin.math.round(wdth / 2.5f) * 2.5f).coerceIn(62.5f, 100f)
        return synchronized(cache) {
            cache.getOrPut("noto/$w/$d") {
                try {
                    Typeface.Builder(ctx.assets, "fonts/NotoSans-Variable.ttf")
                        .setFontVariationSettings("'wght' $w, 'wdth' $d").build() ?: Typeface.DEFAULT_BOLD
                } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
            }
        }
    }
}

/**
 * Aperçu de l'éditeur de correction : couche d'effacement (fond reconstruit sous le texte d'origine) et couche de texte seul, toutes deux à [ss] pixels par pixel de la fenêtre et posées en
 * [rect], mise en page automatique avant décalage ([baseRect]) et centre vers lequel ramener le texte ([target]). L'appelant recycle les bitmaps.
 */
class LayerSink {
    var erase: Bitmap? = null
    var text: Bitmap? = null
    var rect: RectF? = null
    var ss: Float = 1f
    var baseRect: RectF? = null
    var target: Pair<Float, Float>? = null
    var report: JSONObject? = null
    fun recycle() { erase?.recycle(); text?.recycle(); erase = null; text = null }
}

/** Dessine un texte avec une famille candidate, mis à l'échelle pour remplir w × h : sert à comparer à l'encre d'origine. */
private class AndroidGlyphRenderer(private val fonts: Typefaces) : GlyphRenderer {
    override fun render(text: String, family: String, embolden: Float, skew: Float, w: Int, h: Int): GlyphRender? {
        val size = 120f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.family(family); textSize = size; textSkewX = skew
            if (embolden > 0f) { style = Paint.Style.FILL_AND_STROKE; strokeWidth = embolden * size; strokeJoin = Paint.Join.ROUND }
        }
        val path = android.graphics.Path(); paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        val b = android.graphics.RectF(); path.computeBounds(b, true)
        val stroke = embolden * size
        val bw = b.width() + stroke; val bh = b.height() + stroke
        if (bw < 2f || bh < 2f || w < 4 || h < 4) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            c.scale(w / bw, h / bh); c.translate(-b.left + stroke / 2f, -b.top + stroke / 2f)
            paint.color = 0xFFFFFFFF.toInt(); c.drawText(text, 0f, 0f, paint)
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            return GlyphRender(BooleanArray(w * h) { (px[it] ushr 24) > 127 }, (w / bw) / (h / bh))
        } finally { bmp.recycle() }
    }
}

/** Adaptateur Bitmap → accès aux pixels pour le moteur d'effacement du cœur. */
private class BitmapSource(private val bmp: Bitmap) : PixelSource {
    override val width: Int get() = bmp.width
    override val height: Int get() = bmp.height
    override fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray = IntArray(w * h).also { bmp.getPixels(it, 0, w, x, y, w, h) }
}

/** Police retenue pour un bloc : famille, réglages variables et espacement des lettres. */
private class FontChoice(
    val typeface: Typeface, val letterSpacing: Float, val wght: Int, val wdth: Float,
    val scaleX: Float = 1f, val skew: Float = 0f, val embolden: Float = 0f, val family: String = "noto",
)

object PageRenderer {
    /** Double résolution pour un texte net quand la page est agrandie à l'écran. */
    private const val SUPERSAMPLE = 2f

    private class Prep(
        val block: TranslatedBlock,
        val plan: Erasure.Plan?,       // null si l'image n'a pas pu être décodée
        val style: FontStyle,
        val text: String,              // traduction avec typographie
        val bubble: RectF? = null,     // bulle détectée ET confirmée par son contour ; null = texte hors bulle
        val obstacles: List<RectF> = emptyList(),   // emprises des textes voisins (le texte hors bulle ne doit pas les recouvrir)
        val neighborBubbles: List<RectF> = emptyList(),   // bulles voisines confirmées : un texte hors bulle ne doit pas recouvrir leur contour
        val hardObstacles: List<RectF> = emptyList(),     // texte déjà placé d'autres blocs : toujours évité
        val bubbleRect: Boolean = false,                  // la bulle (confirmée, sinon la boîte du détecteur) est un cartouche rectangulaire ; sinon bulle ronde, bornée par son ellipse
        val container: RectF? = null,                     // boîte de bulle du détecteur NON confirmée par un contour : elle borne quand même la mise en page (jamais de texte libre dans une bulle)
        val font: String? = null,                         // police de la bibliothèque qui copie le lettrage d'origine (voir StyleMatcher) ; null : police par défaut
        val fontSlant: Float = 0f,                        // inclinaison mesurée du lettrage d'origine (0 = droit, environ −0,2 = italique)
        val fontInfo: String = "",                        // résumé de la décision de police, pour le journal
        val sizeHint: Float = 0f,                         // hauteur de lettre de référence (pixels de l'image d'origine) harmonisée avec les blocs de même taille d'origine ; 0 : mesure propre au bloc
    ) {
        fun withHard(h: List<RectF>) = Prep(block, plan, style, text, bubble, obstacles, neighborBubbles, h, bubbleRect, container, font, fontSlant, fontInfo, sizeHint)
        fun withFont(f: String?, slant: Float, info: String, size: Float) = Prep(block, plan, style, text, bubble, obstacles, neighborBubbles, hardObstacles, bubbleRect, container, f, slant, info, size)
    }

    fun render(fonts: Typefaces, imageBytes: ByteArray, result: PageResult, settings: AppSettings, debug: DebugInfo?, fontMemory: StyleMatcher.Memory? = null, series: String? = null,
               edits: Map<Int, RenderEdit> = emptyMap(), only: Int? = null, sink: LayerSink? = null): JSONObject {
        val bmp = decodeSampled(imageBytes, result.imageWidth, result.imageHeight)
        val s = if (bmp == null) 1f else bmp.width.toFloat() / result.imageWidth
        val iw = result.imageWidth.toFloat(); val ih = result.imageHeight.toFloat()
        val blocks = result.blocks.filter { it.translated.isNotBlank() }
        val lineHs = blocks.map { lineHeightOf(it) }.sorted()
        val medianLine = if (lineHs.isEmpty()) 0f else lineHs[lineHs.size / 2]
        val patches = JSONArray(); val report = JSONArray()
        try {
            val src = bmp?.let { BitmapSource(it) }
            // bulles annoncées par le détecteur et confirmées par un contour visible (le texte libre sur page blanche ou noire n'en a pas)
            val verified = HashMap<Int, RectF?>()
            for (b in blocks) {
                val bb = b.bubble ?: continue
                val u = b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { x, y -> x.union(y) }
                verified[b.id] = if (src == null) bb else try { if (BubbleVerifier.verify(src, s, bb, u, iw, ih)) bb else null } catch (e: Throwable) { null }
            }
            // cartouche rectangulaire ou bulle ronde : décide si la forme est bornée par l'ellipse de la bulle et comment sont protégées les bulles voisines
            val rectCache = HashMap<RectF, Boolean>()
            val isRect: (RectF) -> Boolean = { bb -> rectCache.getOrPut(bb) { if (src == null) false else try { BubbleVerifier.isRectangular(src, s, bb, iw, ih) } catch (e: Throwable) { false } } }
            // 1) préparation (effacement) et mise en page de chaque bloc
            val preps = ArrayList<Prep>(); val laids = ArrayList<Laid?>()
            var skipped = 0
            for ((bi, b) in blocks.withIndex()) {
                if (only != null && b.id != only) continue            // aperçu de l'éditeur : seul le bloc corrigé est mis en page
                val edit = edits[b.id]
                // traduction identique au texte lu (nom propre, interjection, décor lu comme du texte) : l'image n'est pas touchée (sauf dans l'aperçu de l'éditeur, qui montre toujours le texte)
                if (sink == null && RenderGate.unchanged(b.source, b.translated)) { skipped++; continue }
                // lignes des autres blocs : ce bloc ne doit pas les effacer
                val others = blocks.withIndex().filter { it.index != bi }.flatMap { it.value.lineBoxes.ifEmpty { listOf(it.value.box) } }
                val prep = prepare(b, src, s, iw, ih, medianLine, settings.target.code, others, blocks, verified, isRect, edit)
                val manual = edit?.manual == true            // zone dessinée par l'utilisateur : elle fait foi, aucune des précautions suivantes ne s'applique
                // texte hors bulle, sans zone du détecteur, sur un dessin : trop risqué (faux texte dans le décor), l'image n'est pas touchée
                if (!manual && RenderGate.tooUncertain(b.kind, b.zone != null, b.bubble != null, prep.plan?.mode == Erasure.Mode.ART, b.source)) { skipped++; continue }
                // filet de sécurité, quelle que soit la méthode d'effacement : une zone effacée très supérieure au texte lu est du dessin qu'on abîmerait, on n'y touche pas
                val pl = prep.plan
                if (!manual && pl != null && pl.mode != Erasure.Mode.SMOOTH) {
                    val textArea = b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { x, y -> x.union(y) }.let { u -> max(1f, u.area * s * s) }
                    var n = 0; for (m in pl.mask) if (m) n++
                    if (n > 10f * textArea) { skipped++; continue }
                }
                preps.add(prep)
            }
            // police : celle de la bibliothèque qui copie le mieux le lettrage d'origine de chaque bloc, de façon cohérente d'un bloc à l'autre (voir StyleMatcher), puis mise en page
            val picks: List<FontPick?> = try { chooseFonts(fonts, preps, s, fontMemory, series) } catch (e: Throwable) { emptyList() }
            // taille : les blocs dont le texte d'origine a la même hauteur de lettre (à 18 % près) reçoivent la même hauteur de référence : la mesure de chacun est bruitée, la taille du texte
            // d'origine ne l'est pas ; deux bulles de même taille d'origine ne doivent pas recevoir des tailles de traduction différentes à cause d'une mesure
            val sizes: FloatArray = try { harmonizedHeights(preps, s) } catch (e: Throwable) { FloatArray(preps.size) }
            for (i in preps.indices) {
                val pk = picks.getOrNull(i)
                val ed = edits[preps[i].block.id]
                preps[i] = if (ed?.fontId != null) preps[i].withFont(ed.fontId, 0f, "police choisie", sizes[i]) else preps[i].withFont(pk?.id, pk?.slant ?: 0f, pk?.info ?: "", sizes[i])
                var laid = try { layoutBlock(fonts, s, preps[i], iw, ih, settings.textScale * (ed?.scale ?: 1f), src) } catch (e: Throwable) { null }
                // position choisie : le texte est décalé (la mise en page automatique reste la référence du « centrer » et de l'aperçu)
                if (sink != null && laid != null) { sink.baseRect = laid.placed(0f); sink.target = EditApplier.centerTarget(preps[i].bubble ?: preps[i].container, preps[i].block.zone, preps[i].block.lineBoxes.ifEmpty { listOf(preps[i].block.box) }.reduce { a, c -> a.union(c) }) }
                if (laid != null && ed != null && (ed.dx != 0f || ed.dy != 0f)) laid = laid.shifted(ed.dx, ed.dy)
                laids.add(laid)
            }
            // 2) collisions : un texte hors bulle (forme libre) dont le texte placé recouvre celui d'un autre bloc est replacé en évitant le texte réellement placé
            //    des autres (leur traduction est plus longue que l'original, que chaque bloc évitait seul)
            for (round in 0 until 2) {
                var changed = false
                for (i in preps.indices) {
                    val li = laids[i] ?: continue
                    val pi = preps[i]
                    if (pi.bubble != null || pi.container != null || pi.plan?.shape != null) continue            // les bulles gardent leur place : ce sont les textes libres qui s'adaptent
                    if (edits[pi.block.id]?.pinned == true) continue                                              // position choisie par l'utilisateur : on n'y touche pas
                    val mine = li.placed(2f)
                    val others = preps.indices.filter { it != i }.mapNotNull { j -> laids[j]?.placed(4f) }
                    if (others.none { o -> (mine.intersection(o)?.area ?: 0f) > 0.04f * mine.area }) continue
                    var relaid = try { layoutBlock(fonts, s, pi.withHard(others), iw, ih, settings.textScale, src) } catch (e: Throwable) { null } ?: continue
                    // ordre de lecture : un bloc replacé de l'autre côté d'un voisin inverserait les phrases (« …COMPRENDRE LE BONHEUR » avant « J'AVAIS UNE MAISON… »).
                    // On réduit alors la taille du texte jusqu'à ce qu'il ne chevauche plus rien, plutôt que de le déplacer par-dessus ou par-dessous le voisin.
                    val crosses = { r: Laid -> val b0 = li.placed(0f); val a0 = r.placed(0f); preps.indices.any { j -> j != i && laids[j]?.let { o -> ReadingOrder.crossesNeighbour(b0, a0, o.placed(0f)) } == true } }
                    if (crosses(relaid)) {
                        var alt: Laid? = null
                        for (k in listOf(0.9f, 0.8f, 0.7f)) {
                            val cand = try { layoutBlock(fonts, s, pi, iw, ih, settings.textScale * k, src) } catch (e: Throwable) { null } ?: continue
                            val m = cand.placed(2f)
                            if (others.none { o -> (m.intersection(o)?.area ?: 0f) > 0.04f * m.area } && !crosses(cand)) { alt = cand; break }
                        }
                        relaid = alt ?: continue   // aucune taille ne convient sans inverser l'ordre : on garde la première mise en page
                    }
                    laids[i] = relaid; changed = true
                }
                if (!changed) break
            }
            // 3) dessin
            for (i in preps.indices) {
                val laid = laids[i] ?: continue
                val othersPlaced = laids.indices.filter { it != i }.flatMap { j -> laids[j]?.lineRects(3f) ?: emptyList() }
                val rendered = try { paintBlock(s, preps[i], laid, iw, ih, othersPlaced, sink) } catch (e: Throwable) { null } ?: continue
                patches.put(rendered.first); report.put(rendered.second)
            }
            val json = JSONObject().put("w", result.imageWidth).put("h", result.imageHeight).put("patches", patches).put("blocks", report).put("skipped", skipped)
            if (debug != null && settings.diagnostic) json.put("dbg", OverlayBuilder.debugJson(debug))
            return json
        } finally {
            bmp?.recycle()
        }
    }

    private fun lineHeightOf(b: TranslatedBlock): Float =
        if (b.lineHeight > 0f) b.lineHeight else if (b.vertical) b.box.width else b.box.height

    private fun prepare(b: TranslatedBlock, src: PixelSource?, s: Float, iw: Float, ih: Float, medianLine: Float, target: String, others: List<RectF>, blocksAll: List<TranslatedBlock>, verified: Map<Int, RectF?>, isRect: (RectF) -> Boolean, edit: RenderEdit? = null): Prep {
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val bubble = verified[b.id]
        // zone de texte détectée : tout ce qu'elle contient est effacé, y compris une ligne que la lecture n'a pas trouvée (sinon il en reste un morceau)
        // zone dessinée par l'utilisateur : elle remplace la zone du détecteur, sans les garde-fous de taille
        val zone = edit?.region?.bounds ?: b.zone?.takeIf { z -> z.area <= 3.5f * union.area && z.area >= 0.8f * union.area && z.contains(union.centerX, union.centerY) }
        // bulles voisines (autres que celle qui contient ce bloc) : le texte libre qui les chevauche ne doit entamer ni leur intérieur ni leur contour.
        // « Contient ce bloc » se mesure sur l'ellipse pour une bulle ronde : le coin de sa boîte n'est pas dans la bulle.
        fun containsMe(nb: RectF): Boolean = if (isRect(nb)) nb.contains(union.centerX, union.centerY) else {
            val ex = (union.centerX - nb.centerX) / (nb.width / 2f * 1.06f); val ey = (union.centerY - nb.centerY) / (nb.height / 2f * 1.06f); ex * ex + ey * ey <= 1f
        }
        val neighborAll = blocksAll.filter { it !== b }.mapNotNull { verified[it.id] ?: it.bubble }.distinct()
        val neighborBubbles = neighborAll.filter { nb -> !containsMe(nb) || bubble == null }
        val outside = neighborAll.filter { nb -> !containsMe(nb) }
        val plan0 = if (src != null) try { Erasure.plan(src, s, lineBoxes, lineH, iw, ih, others, bubble, outside.filter { isRect(it) }, free = bubble == null, outlines = if (bubble == null) neighborBubbles else emptyList(), extra = listOfNotNull(zone), protectRound = outside.filter { !isRect(it) }, confine = if (bubble == null) b.bubble?.takeIf { it.area >= 1.6f * lineBoxes.reduce { x, y -> x.union(y) }.area } else null, confineRect = b.bubble?.let { isRect(it) } ?: false) } catch (e: Throwable) { null } else null
        // zone dessinée par l'utilisateur : rien n'est effacé en dehors
        val plan = if (plan0 != null && edit?.region != null) try { Erasure.restrict(plan0, edit.region.mask(plan0.x0, plan0.y0, plan0.w, plan0.h, s)) } catch (e: Throwable) { plan0 } else plan0
        val flat = plan?.mode == Erasure.Mode.FLAT
        val rectangular = flat && plan?.shape == null && union.width > 2.2f * union.height && lineBoxes.size <= 2
        // hauteur de ligne pour le choix du style : mesurée sur les lettres (les boîtes d'une ligne inclinée sont gonflées)
        val inkH = measuredInkHeight(plan, b, s)
        val lineHStyle = if (inkH > 0f && !b.vertical && inkH >= 0.49f * lineH) min(lineH, inkH / 0.8f) else lineH
        val style = TextErase.chooseFont(b.source, lineHStyle, medianLine, flat, plan?.outlined ?: false, rectangular)
        val text = if (target == "fr") Typography.french(b.translated) else b.translated
        val obstacles = blocksAll.filter { it !== b }.map { o -> o.lineBoxes.ifEmpty { listOf(o.box) }.reduce { x, y -> x.union(y) } }
        // boîte de bulle non confirmée : retenue seulement si elle laisse de la marge autour du texte. Un texte libre que le détecteur a aussi marqué « bulle » a une boîte qui
        // épouse son texte : l'ellipse inscrite le rognerait et le réduirait
        val container = if (bubble == null) b.bubble?.takeIf { it.area >= 1.6f * union.area } else null
        return Prep(b, plan, style, text, bubble, obstacles, neighborBubbles, emptyList(), (bubble ?: container)?.let { isRect(it) } ?: false, container)
    }

    /**
     * Encre d'une ligne d'origine : le cœur des lettres (sans leur halo ni leur contour) quand il est cohérent avec l'encre complète, c'est-à-dire d'au moins 60 % de sa hauteur ;
     * sinon l'encre complète. Un cœur réduit à des fragments (couleur du texte mal estimée) donnerait des lettres de 3 ou 4 pixels, donc un texte minuscule.
     */
    private fun lineInkOf(plan: Erasure.Plan, s: Float, b: RectF, text: String): app.manhwa.core.LineInk? = StyleMatcher.lineInk(plan, s, b, text)

    /** Hauteur médiane des lettres d'origine (pixels de l'image d'origine), 0 si inconnue. */
    private fun measuredInkHeight(plan: Erasure.Plan?, b: TranslatedBlock, s: Float): Float {
        if (plan == null) return 0f
        val boxes = b.lineBoxes; val texts = b.lineTexts
        if (boxes.isEmpty() || texts.size != boxes.size) return 0f
        val hs = boxes.indices.mapNotNull { i -> lineInkOf(plan, s, boxes[i], texts[i])?.letterHeight }.sorted()
        return if (hs.isEmpty()) 0f else min(hs[hs.size / 2] / s, if (!b.vertical && b.lineHeight > 0f) b.lineHeight / 1.12f else Float.MAX_VALUE)
    }


    /** Hauteur des capitales / corps de police, mesurée sur la police réelle. */
    private fun capRatio(tf: Typeface): Float {
        val p = Paint().apply { typeface = tf; textSize = 100f }
        val r = Rect(); p.getTextBounds("H", 0, 1, r)
        return (r.height() / 100f).coerceIn(0.4f, 0.95f)
    }

    /** Encre de chaque ligne d'origine (recadrée au plus juste) à partir du plan d'effacement. */
    private fun lineInks(p: Prep, s: Float): List<app.manhwa.core.LineInk> {
        // cœur des lettres (sans leur halo ni leur contour) quand il est cohérent : la graisse et la taille ne sont pas faussées par la lueur
        val plan = p.plan ?: return emptyList()
        val boxes = p.block.lineBoxes; val texts = p.block.lineTexts
        if (boxes.isEmpty() || texts.size != boxes.size) return emptyList()
        return boxes.indices.mapNotNull { i -> lineInkOf(plan, s, boxes[i], texts[i]) }
    }

    /**
     * Hauteur de lettre de référence d'un bloc (pixels de l'image d'origine) : celle de l'encre, bornée par la hauteur de ligne. Une encre beaucoup plus basse que la ligne
     * (moins de 55 % de la hauteur de capitale permise) est une mesure ratée (fragments, contour, lueur) : on se fie alors à la hauteur de ligne. Sans cela, des lettres de 22 px
     * étaient traduites en 11 px.
     */
    private fun baseHeightOf(inkH: Float, capLimit: Float, lineH: Float): Float = when {
        inkH <= 0f -> lineH * 0.8f
        capLimit == Float.MAX_VALUE -> inkH
        inkH < 0.55f * capLimit -> 0.9f * capLimit
        else -> min(inkH, capLimit)
    }

    private fun inkHeightOf(p: Prep, s: Float): Float {
        val inks = lineInks(p, s); val b = p.block
        return if (inks.isEmpty()) 0f else (if (b.vertical) inks.map { it.w } else inks.map { it.letterHeight }).sorted().let { it[it.size / 2] } / s
    }

    /** Hauteurs de référence de tous les blocs, harmonisées : les blocs de même taille d'origine (écart de 18 % au plus) prennent la hauteur médiane de leur groupe. Texte vertical : 0 (mesure propre). */
    private fun harmonizedHeights(preps: List<Prep>, s: Float): FloatArray {
        val raw = FloatArray(preps.size) { i ->
            val b = preps[i].block
            if (b.vertical) 0f else { val lineH = lineHeightOf(b); baseHeightOf(inkHeightOf(preps[i], s), if (lineH > 0f) lineH / 1.12f else Float.MAX_VALUE, lineH) }
        }
        val out = raw.copyOf()
        val order = raw.indices.filter { raw[it] > 0f }.sortedBy { raw[it] }
        var k = 0
        while (k < order.size) {
            val first = raw[order[k]]; var e = k
            while (e + 1 < order.size && raw[order[e + 1]] <= first * 1.18f) e++
            if (e > k) { val med = (k..e).map { raw[order[it]] }.sorted().let { it[it.size / 2] }; for (q in k..e) out[order[q]] = med }
            k = e + 1
        }
        return out
    }

    /** Police retenue pour un bloc par l'appariement de style : identifiant dans la bibliothèque, inclinaison mesurée, résumé pour le journal. */
    private class FontPick(val id: String, val slant: Float, val info: String)

    /** Police de la bibliothèque utilisée quand rien ne peut être mesuré. */
    private const val DEFAULT_FONT = StyleMatcher.DEFAULT

    /**
     * Police de chaque bloc : celle de la bibliothèque (143 candidates libres) dont le style ressemble le plus au lettrage d'origine, mesuré sur son encre (graisse, montants, barres,
     * contraste, orientation des contours), départagée sur le texte réel (forme, largeur), jamais plus grasse que l'original (texte lisible). Les blocs de style semblable reçoivent la même
     * police, ceux d'un autre style (autre personnage, texte hors bulle, lettrage particulier) peuvent en recevoir une autre ; la mémoire de la série garde la police d'un style déjà vu.
     */
    private fun chooseFonts(fonts: Typefaces, preps: List<Prep>, s: Float, memory: StyleMatcher.Memory?, series: String?): List<FontPick?> {
        val blocks = preps.map { p ->
            if (ScriptDetector.dominant(p.block.source) != "en") null            // source CJK : pas de lettrage latin à imiter
            else {
                val lines = lineInks(p, s)
                if (lines.isEmpty()) null
                else StyleMatcher.describe(lines, if (p.plan?.mode == Erasure.Mode.ART) StyleMatcher.Regime.ART else StyleMatcher.Regime.FLAT)
            }
        }
        val dec = StyleMatcher.decide(blocks, AndroidGlyphRenderer(fonts), memory, series)
        return dec.mapIndexed { i, d ->
            if (d == null || ScriptDetector.dominant(preps[i].block.source) != "en") null
            else {
                val b = blocks[i]; val top = d.ranking.firstOrNull()
                val info = if (b == null) "non mesuré (voisin) · famille ${d.family}" else "famille %s · poids %.3f · dist %.1f · forme %.2f · pén %.2f%s · alt %s".format(
                    d.family, kotlin.math.exp(b.raw[0]), top?.dist ?: 0f, top?.shape ?: 0f, top?.penalty ?: 0f, if (d.fromMemory) " · série" else "",
                    d.ranking.drop(1).take(2).joinToString(",") { it.id })
                FontPick(d.id, b?.slant ?: 0f, info)
            }
        }
    }

    /** Police d'un bloc : sources CJK en Noto calibrée, tout le reste dans la police de la bibliothèque choisie pour lui (sans épaississement ni étirement), droite ou inclinée comme l'original. */
    private fun matchFont(fonts: Typefaces, p: Prep): FontChoice {
        val cjk = ScriptDetector.dominant(p.block.source) != "en"
        if (cjk) {
            val wght = if (p.plan?.outlined ?: false) 800 else 650
            return FontChoice(fonts.noto(wght, 100f), 0f, wght, 100f)
        }
        val fam = p.font ?: if (p.style == FontStyle.SHOUT) "bangers" else DEFAULT_FONT
        val native = FontLibrary.spec(fam)?.italic == true
        val skew = if (!native && p.fontSlant <= -0.10f) p.fontSlant.coerceIn(-0.25f, 0f) else 0f
        return FontChoice(fonts.family(fam), 0f, 700, 100f, skew = skew, family = fam)
    }

    /** Résultat de la mise en page d'un bloc : police, pinceau de mesure, lignes placées. */
    private class Laid(val font: FontChoice, val paint: Paint, val fit: ShapeFit, val art: Boolean, val allCaps: Boolean, val em: Float, val stepRatio: Float, val lay: String = "") {
        /** Même mise en page, texte décalé de ([dx], [dy]) pixels de l'image. */
        fun shifted(dx: Float, dy: Float): Laid = Laid(font, paint, ShapeFit(fit.size, fit.lineStep, fit.lines.map { PlacedLine(it.text, it.cx + dx, it.top + dy, it.width) }, fit.fits), art, allCaps, em, stepRatio, lay)

        /** Rectangle de chaque ligne placée : sert à ne pas recouvrir le texte d'un autre bloc avec un aplat d'effacement. */
        fun lineRects(margin: Float): List<RectF> = fit.lines.map { ln -> RectF(ln.cx - ln.width / 2f - margin, ln.top - margin, ln.cx + ln.width / 2f + margin, ln.top + fit.lineStep + margin) }

        /** Rectangle occupé par le texte placé (lignes) : sert à détecter les collisions entre blocs. */
        fun placed(margin: Float): RectF {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (ln in fit.lines) { l = min(l, ln.cx - ln.width / 2f); r = max(r, ln.cx + ln.width / 2f); t = min(t, ln.top); b = max(b, ln.top + fit.lineStep) }
            return RectF(l - margin, t - margin, r + margin, b + margin)
        }
    }

    private fun layoutBlock(fonts: Typefaces, s: Float, p: Prep, iw: Float, ih: Float, userScale: Float, src: PixelSource? = null): Laid? {
        val b = p.block; val plan = p.plan
        val lineBoxes = b.lineBoxes.ifEmpty { listOf(b.box) }
        val union = lineBoxes.reduce { a, c -> a.union(c) }
        val lineH = lineHeightOf(b)
        val allCaps = TextNormalizer.isAllCaps(b.source)

        // police assortie au lettrage d'origine ; corps cible : même hauteur de capitales que l'encre d'origine
        val inks = lineInks(p, s)
        val font = matchFont(fonts, p)
        // texte vertical : la taille d'un caractère est la LARGEUR de la colonne (et non sa hauteur, qui est la longueur du texte)
        val inkH = if (inks.isEmpty()) 0f else (if (b.vertical) inks.map { it.w } else inks.map { it.letterHeight }).sorted().let { it[it.size / 2] } / s       // pixels de l'image d'origine
        val cap = capRatio(font.typeface)
        // la hauteur de ligne mesurée dans l'axe du texte borne la hauteur d'encre (lettres collées à un contour ou à une autre ligne)
        val capLimit = if (!b.vertical && lineH > 0f) lineH / 1.12f else Float.MAX_VALUE
        val baseH = if (p.sizeHint > 0f) p.sizeHint else baseHeightOf(inkH, capLimit, lineH)
        // mesure de l'encre fiable (pas ratée) : la traduction ne dépasse pas nettement la taille d'origine, même si elle est courte et tiendrait en plus grand
        val inkTrusted = inkH > 0f && !b.vertical && (capLimit == Float.MAX_VALUE || inkH >= 0.55f * capLimit)
        val emInk = (if (allCaps && !b.vertical) baseH / cap else if (b.vertical) baseH / 0.95f else baseH / 0.85f) * userScale.coerceIn(0.5f, 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = font.typeface; letterSpacing = font.letterSpacing; textScaleX = font.scaleX; textSkewX = font.skew
        }
        val measurer = TextMeasurer { text, size -> paint.textSize = size; paint.measureText(text) * 1.02f }

        // interligne d'origine : pas entre deux lignes / corps
        val tops = lineBoxes.map { it.top }.sorted()
        val pitch = if (tops.size >= 2) tops.zipWithNext { a, c -> c - a }.sorted().let { it[it.size / 2] } else 0f
        val stepRatio = if (pitch > 0f && !b.vertical) (pitch / (if (allCaps) baseH / cap else baseH / 0.85f)).coerceIn(1.0f, 1.6f) else 1.18f

        // forme disponible : intérieur de la bulle relevé sur l'image ; sinon ellipse inscrite dans la bulle confirmée ; sinon (texte hors bulle) l'emprise
        // d'origine progressivement agrandie, sans sortir de l'image ni recouvrir un voisin, jusqu'à ce que la traduction y tienne à une taille proche de l'original
        val art = plan?.mode == Erasure.Mode.ART
        // une bulle confirmée par son contour borne toujours le texte : ellipse inscrite (cartouche : rectangle), marge réduite si le texte d'origine la remplissait déjà.
        // Sans cela, une bulle presque pleine retombait sur la mise en page d'un texte libre, qui n'a aucune limite de contour.
        // boîte limite : bulle confirmée, sinon boîte du détecteur (fond non dessiné). Un texte placé dans une bulle y reste, que son contour ait été retrouvé ou non.
        val limitBox: RectF? = p.bubble ?: p.container?.takeIf { plan != null && plan.mode != Erasure.Mode.ART }
        val bubbleShape = limitBox?.let { c ->
            val k = if (c.width * 0.88f < union.width * 1.05f || c.height * 0.88f < union.height * 1.05f) 0.025f else 0.06f
            val inset = RectF(c.left + c.width * k, c.top + c.height * k, c.right - c.width * k, c.bottom - c.height * k)
            if (p.bubbleRect) RowShape.rect(inset.left, inset.top, inset.right, inset.bottom) else RowShape.ellipse(inset.left, inset.top, inset.right, inset.bottom)
        }
        // majuscules accentuées : l'accent d'une ligne ne doit pas toucher la ligne du dessus
        val accents = TextNormalizer.isAllCaps(p.text) && p.text.any { it in "ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇŒŸ" }
        val step = if (accents) max(stepRatio, 1.14f) else stepRatio
        val centerY = (union.top + union.bottom) / 2f
        // Taille : la traduction doit ressembler au texte d'origine par la taille et l'emplacement. Deux estimations indépendantes : la hauteur des lettres mesurée sur l'encre
        // (fragile : contours, texte incliné, graisse) et la taille à laquelle la traduction TIENT dans l'emprise d'origine (un peu agrandie). On retient la première, bornée
        // entre 85 % et 110 % de la seconde, et jamais plus haute que la ligne d'origine : plus d'agrandissement de la zone pour garder des lettres hautes, plus de texte minuscule.
        val origPadX = union.width * 0.05f + 4f; val origPadY = union.height * 0.08f + 4f
        val origRect = RowShape.rect(union.left - origPadX, union.top - origPadY, union.right + origPadX, union.bottom + origPadY)
        val marginInk = max(4f, emInk * 0.14f)
        val fpFit = if (b.vertical) null else ShapeLayout.fit(origRect, centerY, p.text, measurer, emInk * 3f, max(5f, emInk * 0.3f), step, marginInk)
        val sFp = if (fpFit != null && fpFit.fits && fpFit.lines.isNotEmpty()) fpFit.size else 0f
        val lineCap = 1.05f * max(max(lineH, pitch), 1f)
        val emRaw = if (sFp > 0f) min(min(max(emInk, 0.85f * sFp), 1.1f * sFp), lineCap).coerceAtLeast(5f) else emInk
        val em = if (inkTrusted) min(emRaw, 1.15f * emInk).coerceAtLeast(5f) else emRaw
        val margin = max(4f, em * 0.14f)
        val fixedShape = plan?.shape ?: bubbleShape
        // garde-fou indépendant de la détection : forme relevée sur les pixels (premier trait sombre de chaque côté du texte), pour les fonds unis. Le texte n'est jamais
        // posé sur un contour ou un dessin, même si la bulle n'a pas été reconnue ou si le remplissage de la bulle a fui par un trou du contour.
        val guard: RowShape? = if (src != null && plan != null && plan.mode == Erasure.Mode.FLAT) try {
            val c = limitBox
            val own = lineBoxes + listOfNotNull(b.zone?.takeIf { z -> z.area <= 3.5f * union.area })
            val padX = union.width * 1.0f + 20f; val padY = union.height * 1.0f + 20f
            BarrierShape.build(
                src, s, plan.bg, plan.layer, plan.x0, plan.y0, plan.w, plan.h, own, union.centerX,
                if (c != null) min(c.top, union.top - 8f) else union.top - padY, if (c != null) max(c.bottom, union.bottom + 8f) else union.bottom + padY,
                if (c != null) min(c.left, union.left - 8f) else union.left - padX, if (c != null) max(c.right, union.right + 8f) else union.right + padX, iw, ih,
            )
        } catch (e: Throwable) { null } else null
        // bulle ronde confirmée : jamais au-delà de son ellipse (un contour ouvert laisse la forme relevée déborder sur la page)
        val ellipseLimit: RowShape? = limitBox?.takeIf { !p.bubbleRect }?.let { c -> RowShape.ellipse(c.left + c.width * 0.015f, c.top + c.height * 0.015f, c.right - c.width * 0.015f, c.bottom - c.height * 0.015f) }
        val fit: ShapeFit = if (fixedShape != null) {
            // l'emprise d'origine reste toujours disponible dans la bulle (le texte d'origine y tenait), bornée par l'ellipse un peu élargie : la forme relevée, parfois trop
            // étroite (bords en dégradé, lueur), ne doit pas imposer un texte plus petit que l'original
            val origInBubble = limitBox?.takeIf { !p.bubbleRect }?.let { c -> origRect.intersect(RowShape.ellipse(c.left - c.width * 0.06f, c.top - c.height * 0.06f, c.right + c.width * 0.06f, c.bottom + c.height * 0.06f)) } ?: origRect
            val widened = fixedShape.union(origInBubble)
            val base = ShapeLayout.fit(widened, centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin)
            var constrained: RowShape? = widened
            if (ellipseLimit != null) constrained = constrained?.intersect(ellipseLimit)
            if (guard != null) constrained = constrained?.intersect(guard)
            val alt = if (constrained != null && constrained !== widened) ShapeLayout.fit(constrained, centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin) else null
            // on garde la mise en page contrainte si elle tient (ou si l'autre ne tenait pas) sans rétrécir le texte de plus de moitié
            if (alt != null && alt.lines.isNotEmpty() && (alt.fits || !base.fits) && alt.size >= 0.55f * base.size) alt else base
        } else {
            var best: ShapeFit? = null
            for (g in floatArrayOf(if (art) 0.08f else 0.05f, 0.16f, 0.3f, 0.55f)) {
                val area = LayoutArea.expand(union, g, p.obstacles, iw, ih, p.hardObstacles)
                val near = p.neighborBubbles.filter { nb -> nb.left < area.right && nb.right > area.left && nb.top < area.bottom && nb.bottom > area.top }
                // contre une bulle voisine : lignes réparties autour de l'ellipse (forme par rangées) OU bloc compact au-dessous / à côté de la bulle
                val shapes = ArrayList<RowShape>()
                if (near.isEmpty()) shapes.add(RowShape.rect(area.left, area.top, area.right, area.bottom))
                else {
                    val gap = max(6f, em * 0.2f)
                    shapes.add(RowShape.rectAvoiding(area.left, area.top, area.right, area.bottom, near, gap))
                    for (nb in near) for (r in LayoutArea.candidatesAround(area, nb, gap, union.height * 1.4f, iw, ih)) if (near.none { o -> o !== nb && o.left < r.right && o.right > r.left && o.top < r.bottom && o.bottom > r.top }) shapes.add(RowShape.rect(r.left, r.top, r.right, r.bottom))
                }
                for (areaShape0 in shapes) {
                    val areaShape = if (guard != null) (areaShape0.intersect(guard) ?: continue) else areaShape0
                    val sy = if (centerY in areaShape.top..areaShape.bottom) centerY else (areaShape.top + areaShape.bottom) / 2f
                    val cand = ShapeLayout.fit(areaShape, sy, p.text, measurer, em, max(5f, em * 0.5f), step, margin)
                    if (cand.lines.isEmpty()) continue
                    val cur = best
                    if (cur == null || (cand.fits && !cur.fits) || (cand.fits == cur.fits && cand.size > cur.size * 1.02f)) best = cand
                }
                val bestNow = best
                if (bestNow != null && bestNow.fits && bestNow.size >= em * 0.72f) break
            }
            best ?: ShapeLayout.fit(RowShape.rect(union.left, union.top, union.right, union.bottom), centerY, p.text, measurer, em, max(5f, em * 0.4f), step, margin)
        }
        if (fit.lines.isEmpty()) return null
        val lay = "encre=%.0f emprise=%.0f ligne=%.0f pas=%.0f cible=%.0f taille=%.0f forme=%s".format(emInk, sFp, lineH, pitch, em, fit.size, if (fixedShape == null) "libre" else if (plan?.shape != null) "relevée" else "ellipse")
        return Laid(font, paint, fit, art, allCaps, em, stepRatio, lay)
    }

    private fun paintBlock(s: Float, p: Prep, laid: Laid, iw: Float, ih: Float, othersPlaced: List<RectF> = emptyList(), sink: LayerSink? = null): Pair<JSONObject, JSONObject>? {
        val b = p.block; val plan = p.plan
        val font = laid.font; val paint = laid.paint; val fit = laid.fit; val art = laid.art; val allCaps = laid.allCaps; val em = laid.em; val stepRatio = laid.stepRatio; val lay = laid.lay

        // zone du patch : région effacée + toutes les lignes placées
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        for (ln in fit.lines) { l = min(l, ln.cx - ln.width / 2f); r = max(r, ln.cx + ln.width / 2f); t = min(t, ln.top); bt = max(bt, ln.top + fit.lineStep) }
        var rect = RectF(l - fit.size * 0.3f, t - fit.size * 0.3f, r + fit.size * 0.3f, bt + fit.size * 0.3f)
        if (plan != null) rect = rect.union(RectF(plan.x0 / s, plan.y0 / s, (plan.x0 + plan.w) / s, (plan.y0 + plan.h) / s))
        rect = RectF(max(0f, rect.left), max(0f, rect.top), min(iw, rect.right), min(ih, rect.bottom))
        // double résolution pour les patches de taille normale ; réduite pour les très grands blocs afin de limiter mémoire et poids de l'envoi
        val areaImg = (rect.right - rect.left) * (rect.bottom - rect.top)
        val ss = min(SUPERSAMPLE * s, max(s, kotlin.math.sqrt(1_600_000f / max(1f, areaImg))))
        val pw = max(1, ((rect.right - rect.left) * ss).toInt()); val ph = max(1, ((rect.bottom - rect.top) * ss).toInt())
        if (pw.toLong() * ph > 12_000_000L) return null
        val patch = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        // aperçu de l'éditeur : le texte est dessiné seul, sur une couche transparente à part, pour pouvoir le déplacer sans refaire l'effacement
        val textBmp = if (sink != null) Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888) else null
        var keep = false
        try {
            val canvas = Canvas(patch)
            val tc = if (textBmp != null) Canvas(textBmp) else canvas
            // 1) effacement du texte d'origine (couche à la résolution échantillonnée, agrandie à la double résolution)
            if (plan != null) {
                // l'aplat d'effacement ne recouvre jamais le texte placé d'un autre bloc (sinon il en coupe le bas, comme une lettre rognée)
                val layerPx = if (othersPlaced.isEmpty()) plan.layer else plan.layer.copyOf().also { arr ->
                    for (o in othersPlaced) {
                        val x0 = ((o.left * s).toInt() - plan.x0).coerceIn(0, plan.w); val x1 = ((o.right * s).toInt() - plan.x0 + 1).coerceIn(0, plan.w)
                        val y0 = ((o.top * s).toInt() - plan.y0).coerceIn(0, plan.h); val y1 = ((o.bottom * s).toInt() - plan.y0 + 1).coerceIn(0, plan.h)
                        for (yy in y0 until y1) for (xx in x0 until x1) arr[yy * plan.w + xx] = 0
                    }
                }
                val layerBmp = Bitmap.createBitmap(layerPx, plan.w, plan.h, Bitmap.Config.ARGB_8888)
                try {
                    val dst = android.graphics.RectF((plan.x0 / s - rect.left) * ss, (plan.y0 / s - rect.top) * ss, ((plan.x0 + plan.w) / s - rect.left) * ss, ((plan.y0 + plan.h) / s - rect.top) * ss)
                    canvas.drawBitmap(layerBmp, null, dst, Paint(if (plan.mode == Erasure.Mode.ART) Paint.FILTER_BITMAP_FLAG else 0))
                } finally { layerBmp.recycle() }
            } else {
                canvas.drawColor(0xE6FFFFFF.toInt()) // image non décodée : aplat clair derrière le texte (repli)
            }
            // 2) texte traduit
            // fond réellement obtenu après effacement (et non une estimation) : le texte doit toujours y être lisible
            val bgForText = plan?.let { if (it.fillMean != 0) it.fillMean else it.bg } ?: 0xFFFFFFFF.toInt()
            var fg = plan?.textColor ?: ColorMath.textColorFor(bgForText)
            if (plan?.mode != Erasure.Mode.ART && kotlin.math.abs(ColorMath.luminance(fg) - ColorMath.luminance(bgForText)) < 0.45f) fg = ColorMath.textColorFor(bgForText)
            val px = fit.size * ss
            paint.textSize = px
            val capPx = Rect().also { paint.getTextBounds("H", 0, 1, it) }.height().toFloat()
            val allCapsOut = TextNormalizer.isAllCaps(p.text)
            for (ln in fit.lines) {
                val cx = (ln.cx - rect.left) * ss
                val bandCenter = (ln.top + fit.lineStep / 2f - rect.top) * ss
                val base = bandCenter + (if (allCapsOut || allCaps) capPx / 2f else capPx * 0.5f)
                paint.textAlign = Paint.Align.CENTER
                if (art) { // texte sur dessin : contour de couleur opposée pour rester lisible
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = px * 0.11f; paint.strokeJoin = Paint.Join.ROUND
                    paint.color = if (ColorMath.luminance(fg) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    tc.drawText(ln.text, cx, base, paint)
                }
                if (font.embolden > 0f) { paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = font.embolden * px; paint.strokeJoin = Paint.Join.ROUND }
                else { paint.style = Paint.Style.FILL; paint.strokeWidth = 0f }
                // lettrage à lueur (halo coloré sur fond uni) : la traduction reçoit le même halo
                val glow = plan?.glow ?: 0
                if (glow != 0) {
                    paint.color = fg
                    paint.setShadowLayer(px * 0.32f, 0f, 0f, glow)
                    tc.drawText(ln.text, cx, base, paint)
                    tc.drawText(ln.text, cx, base, paint)
                    paint.clearShadowLayer()
                }
                paint.color = fg
                tc.drawText(ln.text, cx, base, paint)
            }
            val out = ByteArrayOutputStream()
            if (sink == null) patch.compress(Bitmap.CompressFormat.PNG, 100, out)
            val patchJson = JSONObject().put("x", rect.left.toDouble()).put("y", rect.top.toDouble()).put("w", (rect.right - rect.left).toDouble()).put("h", (rect.bottom - rect.top).toDouble())
                .put("bid", b.id).put("src", b.source).put("tr", b.translated)
                .put("png", "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            val rep = JSONObject().put("x", l.toDouble()).put("y", t.toDouble()).put("w", (r - l).toDouble()).put("h", (bt - t).toDouble())
                .put("t", fit.lines.joinToString("\n") { it.text }).put("fs", fit.size.toDouble()).put("style", p.style.name)
                .put("fg", "#%06x".format(fg and 0xFFFFFF)).put("fillMean", "#%06x".format((plan?.fillMean ?: 0) and 0xFFFFFF)).put("planBg", "#%06x".format((plan?.bg ?: 0) and 0xFFFFFF))
                .put("mode", plan?.mode?.name ?: "NONE").put("outlined", plan?.outlined ?: false)
                .put("fits", fit.fits).put("lines", fit.lines.size).put("shape", plan?.shape != null)
                .put("kind", b.kind).put("bubbleOk", p.bubble != null).put("rectBubble", p.bubbleRect).put("lay", lay)
                .put("erase", plan?.let { pl -> var n = 0; for (m in pl.mask) if (m) n++; Math.round(n / (s * s)) } ?: 0)
                .put("orig", JSONArray(b.lineBoxes.ifEmpty { listOf(b.box) }.reduce { a, c -> a.union(c) }.let { u -> listOf(u.left, u.top, u.right, u.bottom).map { v -> Math.round(v) } }))
                .put("cont", b.bubble?.let { c -> JSONArray(listOf(c.left, c.top, c.right, c.bottom).map { v -> Math.round(v) }) } ?: JSONObject.NULL)
                .put("zoneBox", b.zone?.let { c -> JSONArray(listOf(c.left, c.top, c.right, c.bottom).map { v -> Math.round(v) }) } ?: JSONObject.NULL)
                .put("family", font.family).put("fontInfo", p.fontInfo).put("skew", font.skew.toDouble()).put("embolden", font.embolden.toDouble()).put("scaleX", font.scaleX.toDouble()).put("wght", font.wght).put("wdth", font.wdth.toDouble()).put("em", em.toDouble()).put("stepRatio", stepRatio.toDouble())
            if (sink != null) { sink.erase = patch; sink.text = textBmp; sink.rect = rect; sink.ss = ss; sink.report = rep; keep = true; return JSONObject() to rep }
            return patchJson to rep
        } finally {
            if (!keep) { patch.recycle(); textBmp?.recycle() }
        }
    }

    /** Image réduite à environ 4 millions de pixels ; null si la mémoire manque. */
    private fun decodeSampled(bytes: ByteArray, w: Int, h: Int): Bitmap? = try {
        var sample = 1
        while ((w.toLong() / sample) * (h.toLong() / sample) > 4_000_000L) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }
}
