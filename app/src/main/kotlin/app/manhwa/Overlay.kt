package app.manhwa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import app.manhwa.core.AppSettings
import app.manhwa.core.ColorMath
import app.manhwa.core.DebugInfo
import app.manhwa.core.OverlayLayout
import app.manhwa.core.PageResult
import app.manhwa.core.RectF
import app.manhwa.core.TextMeasurer
import org.json.JSONArray
import org.json.JSONObject

/** Construit la description JSON de l'overlay (positions, taille de texte, couleurs de bulle) envoyée au script de la page. */
object OverlayBuilder {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val measurer = TextMeasurer { text, size -> paint.textSize = size; paint.measureText(text) * 1.08f }

    fun build(imageBytes: ByteArray, result: PageResult, settings: AppSettings, debug: DebugInfo? = null): JSONObject {
        val placed = OverlayLayout.place(result.blocks, result.imageWidth, result.imageHeight, settings.textScale, measurer)
        val bmp = decodeSampled(imageBytes, result.imageWidth, result.imageHeight)
        val sampleScale = if (bmp == null) 1f else bmp.width.toFloat() / result.imageWidth
        val blocks = JSONArray()
        try {
            for (p in placed) {
                val src = result.blocks.firstOrNull { it.id == p.id }?.box ?: p.box
                val bg = if (bmp != null) bubbleColor(bmp, src, sampleScale) else 0xFFFFFFFF.toInt()
                blocks.put(
                    JSONObject()
                        .put("x", p.box.left.toDouble()).put("y", p.box.top.toDouble())
                        .put("w", p.box.width.toDouble()).put("h", p.box.height.toDouble())
                        .put("t", p.lines.joinToString("\n")).put("fs", p.fontSizePx.toDouble())
                        .put("bg", ColorMath.css(bg)).put("fg", ColorMath.css(ColorMath.textColorFor(bg))),
                )
            }
        } finally {
            bmp?.recycle()
        }
        val json = JSONObject().put("w", result.imageWidth).put("h", result.imageHeight).put("blocks", blocks)
        if (debug != null && settings.diagnostic) json.put("dbg", debugJson(debug))
        return json
    }

    /** Page sans traduction affichable : on montre quand même ce que l'OCR a détecté. */
    fun debugOnly(debug: DebugInfo): JSONObject =
        JSONObject().put("w", debug.imageWidth).put("h", debug.imageHeight).put("blocks", JSONArray()).put("dbg", debugJson(debug))

    private fun debugJson(d: DebugInfo): JSONArray {
        val arr = JSONArray()
        for (l in d.lines) arr.put(
            JSONObject().put("x", l.box.left.toDouble()).put("y", l.box.top.toDouble())
                .put("w", l.box.width.toDouble()).put("h", l.box.height.toDouble())
                .put("t", l.text.take(40) + " · " + String.format(java.util.Locale.ROOT, "%.2f", l.confidence) + " · " + l.status)
                .put("ok", l.status == "gardée"),
        )
        return arr
    }

    /** Image réduite à environ 4 millions de pixels pour l'échantillonnage des couleurs ; null si la mémoire manque. */
    private fun decodeSampled(bytes: ByteArray, w: Int, h: Int): Bitmap? = try {
        var s = 1
        while ((w.toLong() / s) * (h.toLong() / s) > 4_000_000L) s *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) {
        null
    }

    /** Couleur médiane d'un anneau autour du bloc de texte d'origine (le fond de la bulle). */
    private fun bubbleColor(bmp: Bitmap, box: RectF, scale: Float): Int {
        val m = 4f
        val l = ((box.left - m) * scale).toInt().coerceIn(0, bmp.width - 1)
        val r = ((box.right + m) * scale).toInt().coerceIn(0, bmp.width - 1)
        val t = ((box.top - m) * scale).toInt().coerceIn(0, bmp.height - 1)
        val b = ((box.bottom + m) * scale).toInt().coerceIn(0, bmp.height - 1)
        val samples = ArrayList<Int>()
        val step = maxOf(1, (maxOf(r - l, b - t)) / 40)
        var x = l
        while (x <= r) { samples.add(bmp.getPixel(x, t)); samples.add(bmp.getPixel(x, b)); x += step }
        var y = t
        while (y <= b) { samples.add(bmp.getPixel(l, y)); samples.add(bmp.getPixel(r, y)); y += step }
        return ColorMath.median(samples.toIntArray())
    }
}

/** Script injecté dans les pages : repère les images, dessine l'overlay dans le document (suit défilement et zoom). */
object PageScript {
    val INJECT = """
(function(){
  if (window.__mt) return;
  var S = {entries:{}, seen:{}, seq:0, visible:true, timer:null};
  var MIN = 150;
  function idOf(im){ if(!im.__mtid){ im.__mtid = 'i' + (++S.seq); } return im.__mtid; }
  function findImg(id){ var l = document.images; for (var i=0;i<l.length;i++){ if (l[i].__mtid === id) return l[i]; } return null; }
  function srcOf(im){ var s = im.currentSrc || im.src || ''; if (s.indexOf('data:')===0 || s.indexOf('blob:')===0) return 'js:'; return s; }
  function collect(force){
    if (force) { S.seen = {}; }
    var out = [], l = document.images;
    for (var i=0;i<l.length;i++){
      var im = l[i], r = im.getBoundingClientRect();
      if (!im.complete || im.naturalWidth < MIN || im.naturalHeight < MIN) continue;
      if (r.width < MIN*0.8 || r.height < MIN*0.8) continue;
      var s = srcOf(im); if (!s) continue;
      var id = idOf(im); if (S.seen[id] === s) continue; S.seen[id] = s;
      out.push({id:id, src:s, w:im.naturalWidth, h:im.naturalHeight, top:r.top + window.scrollY, sy:window.scrollY});
    }
    return out;
  }
  function place(e){
    var r = e.img.getBoundingClientRect(), d = e.div;
    if (!document.contains(e.img) || r.width <= 0) { d.style.display = 'none'; e.key = ''; return; }
    var key = (r.left+window.scrollX)+'|'+(r.top+window.scrollY)+'|'+r.width+'|'+r.height+'|'+S.visible;
    if (e.key === key) return; e.key = key;
    d.style.display = S.visible ? 'block' : 'none';
    d.style.left = (r.left + window.scrollX) + 'px'; d.style.top = (r.top + window.scrollY) + 'px';
    d.style.width = r.width + 'px'; d.style.height = r.height + 'px';
    var s = r.width / e.w, k = d.children;
    for (var i=0;i<k.length;i++){ k[i].style.fontSize = (parseFloat(k[i].getAttribute('data-fs')) * s) + 'px'; }
  }
  function apply(id, data){
    var im = findImg(id); if (!im) return false;
    var old = S.entries[id]; if (old && old.div) old.div.remove();
    var d = document.createElement('div');
    d.setAttribute('data-mt-overlay', '1');
    d.style.cssText = 'position:absolute;pointer-events:none;z-index:2147483000;overflow:hidden;margin:0;padding:0;';
    for (var i=0;i<data.blocks.length;i++){
      var b = data.blocks[i], x = document.createElement('div');
      x.style.cssText = 'position:absolute;box-sizing:border-box;display:flex;align-items:center;justify-content:center;text-align:center;white-space:pre;word-break:normal;overflow-wrap:normal;line-height:1.15;overflow:hidden;border-radius:6px;font-family:sans-serif;font-weight:700;padding:1px;';
      x.style.left = (b.x / data.w * 100) + '%'; x.style.top = (b.y / data.h * 100) + '%';
      x.style.width = (b.w / data.w * 100) + '%'; x.style.height = (b.h / data.h * 100) + '%';
      x.style.background = b.bg; x.style.color = b.fg;
      x.setAttribute('data-fs', b.fs); x.textContent = b.t;
      d.appendChild(x);
    }
    if (data.dbg) {
      for (var j=0;j<data.dbg.length;j++){
        var q = data.dbg[j], bx = document.createElement('div'), lb = document.createElement('div');
        var col = q.ok ? '#19e36b' : '#ff3b3b';
        bx.style.cssText = 'position:absolute;box-sizing:border-box;border:2px dashed ' + col + ';';
        bx.style.left = (q.x / data.w * 100) + '%'; bx.style.top = (q.y / data.h * 100) + '%';
        bx.style.width = (q.w / data.w * 100) + '%'; bx.style.height = (q.h / data.h * 100) + '%';
        lb.style.cssText = 'position:absolute;white-space:nowrap;font-family:monospace;background:rgba(0,0,0,0.75);color:' + col + ';padding:0 2px;';
        lb.style.left = (q.x / data.w * 100) + '%'; lb.style.top = (q.y / data.h * 100) + '%';
        lb.setAttribute('data-fs', Math.max(12, data.w / 45)); lb.textContent = q.t;
        d.appendChild(bx); d.appendChild(lb);
      }
    }
    document.documentElement.appendChild(d);
    var e = {img:im, div:d, w:data.w, h:data.h, key:''}; S.entries[id] = e; place(e); return true;
  }
  function setVisible(v){ S.visible = v; for (var id in S.entries){ S.entries[id].key = ''; place(S.entries[id]); } }
  function clear(){ for (var id in S.entries){ if (S.entries[id].div) S.entries[id].div.remove(); } S.entries = {}; S.seen = {}; }
  function getB64(id){
    var im = findImg(id);
    if (!im) { AndroidMT.onB64(id, ''); return; }
    fetch(im.currentSrc || im.src).then(function(r){ return r.blob(); }).then(function(b){
      var fr = new FileReader();
      fr.onload = function(){ AndroidMT.onB64(id, String(fr.result).split(',')[1] || ''); };
      fr.onerror = function(){ AndroidMT.onB64(id, ''); };
      fr.readAsDataURL(b);
    }).catch(function(){ AndroidMT.onB64(id, ''); });
  }
  function notify(){
    if (S.timer) return;
    S.timer = setTimeout(function(){ S.timer = null; try { AndroidMT.onImagesChanged(); } catch(e) {} }, 500);
  }
  document.addEventListener('load', function(e){ if (e.target && e.target.tagName === 'IMG') notify(); }, true);
  try { new MutationObserver(notify).observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['src','srcset','data-src']}); } catch(e) {}
  setInterval(function(){ for (var id in S.entries){ place(S.entries[id]); } }, 300);
  window.addEventListener('resize', function(){ for (var id in S.entries){ S.entries[id].key = ''; place(S.entries[id]); } });
  window.__mt = {collect:collect, apply:apply, setVisible:setVisible, clear:clear, getB64:getB64};
})();
"""
}
