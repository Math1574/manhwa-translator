package app.manhwa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Superposition d'une image traitée avec une fenêtre (bas de l'image du dessus collé au-dessus d'elle) : elle couvre la fenêtre entière, donc aussi le bas de l'image du dessus.
 * Ce découpage la sépare à la jonction : la partie sous la jonction se pose sur cette image, celle au-dessus se pose sur l'image du dessus
 * (un texte coupé par le découpage du chapitre est ainsi effacé et redessiné d'un seul tenant sur les deux images).
 */
object SeamOverlay {
    class Split(val own: JSONObject, val up: JSONObject?)

    /**
     * @param window  superposition rendue sur la fenêtre (coordonnées de la fenêtre) ; @param ctxH hauteur de contexte (haut de la fenêtre) ; @param prevH hauteur de l'image du dessus ;
     * @param imageW @param imageH taille de cette image ; @param prevId identifiant de l'image du dessus.
     */
    fun split(window: JSONObject, ctxH: Int, imageW: Int, imageH: Int, prevH: Int, prevId: String): Split {
        val patches = window.optJSONArray("patches") ?: JSONArray()
        val lower = JSONArray(); val upper = JSONArray()
        val seam = ctxH.toDouble(); val upShift = (prevH - ctxH).toDouble()
        for (i in 0 until patches.length()) {
            val p = patches.getJSONObject(i)
            val y = p.getDouble("y"); val h = p.getDouble("h")
            when {
                y >= seam - 0.01 -> lower.put(p.put("y", y - seam))
                y + h <= seam + 0.01 -> upper.put(p.put("y", y + upShift))
                else -> {
                    // à cheval sur la jonction : le PNG est coupé en deux à la jonction
                    val parts = cut(p, y, h, seam)
                    if (parts == null) { lower.put(p.put("y", y - seam).put("h", h)) }
                    else { upper.put(parts.first.put("y", y + upShift)); lower.put(parts.second) }
                }
            }
        }
        val rep = window.optJSONArray("blocks") ?: JSONArray()
        val repOut = JSONArray()
        for (i in 0 until rep.length()) {
            val b = rep.getJSONObject(i)
            var ny = b.getDouble("y") - seam; var nh = b.getDouble("h")
            if (ny < 0) { nh += ny; ny = 0.0; b.put("seamTop", true) }       // bloc à cheval : sa partie sous la jonction
            if (nh <= 0) continue
            b.put("y", ny).put("h", nh)
            for (k in listOf("orig", "cont", "zoneBox")) shiftBox(b, k, ctxH)
            repOut.put(b)
        }
        val own = JSONObject().put("w", imageW).put("h", imageH).put("patches", lower).put("blocks", repOut).put("skipped", window.optInt("skipped", 0))
        val up = if (upper.length() > 0) JSONObject().put("w", imageW).put("h", prevH).put("patches", upper).put("target", prevId) else null
        return Split(own, up)
    }

    private fun shiftBox(b: JSONObject, key: String, ctxH: Int) {
        val a = b.optJSONArray(key) ?: return
        if (a.length() == 4) { a.put(1, a.getInt(1) - ctxH); a.put(3, a.getInt(3) - ctxH) }
    }

    /** Coupe un morceau d'image (PNG en base 64) à la ligne [seam] : (partie haute, partie basse) ; la partie basse commence juste sous la jonction. */
    private fun cut(p: JSONObject, y: Double, h: Double, seam: Double): Pair<JSONObject, JSONObject>? {
        var bmp: Bitmap? = null; var top: Bitmap? = null; var bottom: Bitmap? = null
        try {
            val raw = Base64.decode(p.getString("png").substringAfter("base64,"), Base64.DEFAULT)
            bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            if (bmp.height < 2 || h <= 0) return null
            val row = Math.round((seam - y) / h * bmp.height).toInt().coerceIn(1, bmp.height - 1)
            val topH = row * h / bmp.height
            top = Bitmap.createBitmap(bmp, 0, 0, bmp.width, row)
            bottom = Bitmap.createBitmap(bmp, 0, row, bmp.width, bmp.height - row)
            val upper = JSONObject(p.toString()).put("h", topH).put("png", png(top))
            val lower = JSONObject(p.toString()).put("y", y + topH - seam).put("h", h - topH).put("png", png(bottom))
            return upper to lower
        } catch (_: Throwable) {
            return null
        } finally {
            bmp?.recycle(); top?.recycle(); bottom?.recycle()
        }
    }

    private fun png(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
