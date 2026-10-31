package app.manhwa

import app.manhwa.core.DebugInfo
import org.json.JSONArray
import org.json.JSONObject

/** Description JSON du mode diagnostic (cadres des lignes détectées par l'OCR). */
object OverlayBuilder {

    /** Page sans traduction affichable : on montre quand même ce que l'OCR a détecté. */
    fun debugOnly(debug: DebugInfo): JSONObject =
        JSONObject().put("w", debug.imageWidth).put("h", debug.imageHeight).put("blocks", JSONArray()).put("dbg", debugJson(debug))

    fun debugJson(d: DebugInfo): JSONArray {
        val arr = JSONArray()
        for (l in d.lines) arr.put(
            JSONObject().put("x", l.box.left.toDouble()).put("y", l.box.top.toDouble())
                .put("w", l.box.width.toDouble()).put("h", l.box.height.toDouble())
                .put("t", l.text.take(40) + " · " + String.format(java.util.Locale.ROOT, "%.2f", l.confidence) + " · " + l.status)
                .put("ok", l.status == "gardée"),
        )
        return arr
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
      if (im.getAttribute('data-mt-img')) continue;
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
    for (var i=0;i<k.length;i++){ var f = k[i].getAttribute('data-fs'); if (f) k[i].style.fontSize = (parseFloat(f) * s) + 'px'; }
  }
  function apply(id, data){
    var im = findImg(id); if (!im) return false;
    var old = S.entries[id];
    if (data.append && old && old.div) {
      var d = old.div;
    } else {
      if (old && old.div) old.div.remove();
      var d = document.createElement('div');
      d.setAttribute('data-mt-overlay', '1');
      d.style.cssText = 'position:absolute;pointer-events:none;z-index:2147483000;overflow:hidden;margin:0;padding:0;';
      document.documentElement.appendChild(d);
    }
    var ps = data.patches || [];
    for (var i=0;i<ps.length;i++){
      var q0 = ps[i], im2 = document.createElement('img');
      im2.src = q0.png; im2.setAttribute('data-mt-img', '1'); im2.draggable = false;
      im2.style.cssText = 'position:absolute;margin:0;padding:0;border:0;max-width:none;pointer-events:none;';
      im2.style.left = (q0.x / data.w * 100) + '%'; im2.style.top = (q0.y / data.h * 100) + '%';
      im2.style.width = (q0.w / data.w * 100) + '%'; im2.style.height = (q0.h / data.h * 100) + '%';
      d.appendChild(im2);
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
    var e = (data.append && old) ? old : {img:im, div:d, w:data.w, h:data.h, key:''}; e.key = ''; S.entries[id] = e; place(e); return true;
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
