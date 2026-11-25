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
  var S = {entries:{}, seen:{}, first:{}, seq:0, visible:true, timer:null, gen:{}};
  var ER = {on:false, t:0, x:0, y:0};      // gomme : active, instant et position du dernier toucher qui a supprimé une traduction
  function mark(el, on){ el.style.outline = on ? '2px dashed rgba(255,70,70,0.95)' : ''; el.style.outlineOffset = '-2px'; }
  var MIN = 150;
  function idOf(im){ if(!im.__mtid){ im.__mtid = 'i' + (++S.seq); } return im.__mtid; }
  function findImg(id){ var l = document.images; for (var i=0;i<l.length;i++){ if (l[i].__mtid === id) return l[i]; } return null; }
  function srcOf(im){ var s = im.currentSrc || im.src || ''; if (s.indexOf('data:')===0 || s.indexOf('blob:')===0) return 'js:'; return s; }
  // images que l'application n'a pas pu traiter (travail interrompu) : la page les oublie, elles seront redonnées à la prochaine collecte
  function forget(ids){ for (var i=0;i<ids.length;i++){ delete S.seen[ids[i]]; delete S.first[ids[i]]; } }
  // adresse d'une image sans son nom de fichier ni sa requête : les pages d'un chapitre sont rangées dans un même dossier
  function dirOf(s){ var q = s.indexOf('?'); if (q >= 0) s = s.slice(0, q); q = s.indexOf('#'); if (q >= 0) s = s.slice(0, q); var i = s.lastIndexOf('/'); return i > 8 ? s.slice(0, i) : s; }
  // position et taille affichées de chaque image assez grande (images des calques de l'application exclues)
  function geoList(){
    var geo = [], l = document.images;
    for (var g0=0; g0<l.length; g0++){
      var g = l[g0]; if (g.getAttribute('data-mt-img')) continue;
      var gr = g.getBoundingClientRect(); if (gr.width < MIN*0.8 || gr.height < MIN*0.8) continue;
      geo.push({im:g, top:gr.top + window.scrollY, bottom:gr.bottom + window.scrollY, left:gr.left + window.scrollX, width:gr.width});
    }
    return geo;
  }
  // Colonne du chapitre : ses pages se suivent, empilées à (peu près) la même largeur, et ce sont elles qui occupent le plus de place dans la page. Ce qui est nettement plus étroit et
  // n'est pas dans cette colonne (avatars et images des commentaires, vignettes d'autres œuvres, boutons du site sous le dernier chapitre) n'est pas l'œuvre : on ne le donne pas à
  // la traduction. Une image aussi large que les pages, mais sous la colonne, n'est une page que si elle a la même largeur d'origine et vient du même dossier qu'elles (une page
  // d'un chapitre coupé en deux par un grand vide l'est donc toujours ; une image de commentaire, une bannière ou une publicité ne l'est pas). Rend null s'il n'y a pas de colonne nette.
  function column(geo){
    var n = geo.length, i; if (n < 2) return null;
    var byW = geo.slice().sort(function(a, b){ return a.width - b.width; }), tot = 0;
    for (i = 0; i < n; i++) tot += byW[i].width * (byW[i].bottom - byW[i].top);
    var acc = 0, ref = byW[n - 1].width;
    for (i = 0; i < n; i++) { acc += byW[i].width * (byW[i].bottom - byW[i].top); if (acc * 2 >= tot) { ref = byW[i].width; break; } }   // largeur « médiane » pondérée par la surface
    var lim = ref * 0.55, gap = Math.max(300, window.innerHeight * 0.6);
    var pages = geo.filter(function(g){ return g.width >= lim; }).sort(function(a, b){ return a.top - b.top; });
    if (!pages.length) return null;
    // chaînes de pages (au plus « gap » entre deux voisines) : la plus étendue est le chapitre
    var best = null, cur = null;
    for (i = 0; i < pages.length; i++) {
      var p = pages[i], pa = p.width * (p.bottom - p.top);
      if (cur && p.top - cur.bottom <= gap) { cur.bottom = Math.max(cur.bottom, p.bottom); cur.left = Math.min(cur.left, p.left); cur.right = Math.max(cur.right, p.left + p.width); cur.area += pa; }
      else { cur = {top:p.top, bottom:p.bottom, left:p.left, right:p.left + p.width, area:pa}; }
      if (!best || cur.area > best.area || cur === best) best = cur;
    }
    best.lim = lim; best.fullBottom = best.bottom;
    // portrait des pages de la colonne : les largeurs d'origine qui reviennent et les dossiers d'où elles viennent (au moins une page sur cinq chacun)
    var mem = [], cw = {}, cd = {}, tot2 = 0, k;
    for (i = 0; i < n; i++) {
      var g2 = geo[i], w2 = g2.im.naturalWidth;
      if (g2.width < lim || g2.top < best.top - 4 || g2.bottom > best.bottom + 4 || w2 < MIN) continue;
      mem.push(g2); tot2++;
      cw[w2] = (cw[w2] || 0) + 1;
      var d2 = dirOf(srcOf(g2.im)); cd[d2] = (cd[d2] || 0) + 1;
    }
    best.nw = []; best.dirs = {}; best.nd = 0;
    for (k in cw) { if (cw[k] >= 2 && cw[k] * 5 >= tot2) best.nw.push(+k); }
    for (k in cd) { if (cd[k] >= 2 && cd[k] * 5 >= tot2) { best.dirs[k] = 1; best.nd++; } }
    // bas de la colonne : une image qui la prolonge sans être de la même sorte que les pages (bannière ou image de commentaire collée à la dernière page) n'en fait pas partie
    mem.sort(function(a, b){ return b.top - a.top; });
    for (i = 0; i < mem.length; i++) { if (sameKind(mem[i].im, srcOf(mem[i].im), best)) { best.bottom = mem[i].bottom; break; } }
    return best;
  }
  // l'image est de la même sorte que les pages de la colonne : même largeur d'origine (à 2 % près) et même dossier (au moins deux pages, et une sur cinq, en partagent un ;
  // quand les pages ne partagent ni largeur ni dossier, rien ne permet de dire que l'image n'en est pas une : oui)
  function sameKind(im, s, col){
    var w = im.naturalWidth, ok = !col.nw.length;
    for (var i = 0; i < col.nw.length; i++) { if (Math.abs(w - col.nw[i]) <= col.nw[i] * 0.02) { ok = true; break; } }
    return ok && (!col.nd || !!col.dirs[dirOf(s)]);
  }
  // « strict » : on ne donne que les images de la colonne du chapitre (pages de lecture) ; sinon (page où l'on a demandé la traduction à la main) toutes les images assez grandes ;
  // « all » : tout ce qui est assez grand, colonne ou non (l'utilisateur demande les images de dessous le chapitre)
  // « only » : ne donner que ces images (ensemble d'identifiants, voir tail)
  function collect(force, strict, all, only){
    if (force) { S.seen = {}; }
    var out = [], l = document.images, now = Date.now();
    // images empilées : l'image collée juste au-dessus (son bord bas touche le bord haut de celle-ci, même colonne, même largeur) apporte le contexte
    // d'un texte que le découpage du chapitre a coupé en deux ; l'image collée dessous prendra en charge les textes coupés au bas de celle-ci
    var geo = geoList();
    function near(a, b){ return Math.abs(a - b) <= 3; }
    var col = strict ? column(geo) : null;
    for (var i=0;i<l.length;i++){
      var im = l[i], r = im.getBoundingClientRect();
      if (im.getAttribute('data-mt-img')) continue;
      if (!im.complete || im.naturalWidth < MIN || im.naturalHeight < MIN) continue;
      if (r.width < MIN*0.8 || r.height < MIN*0.8) continue;
      var s = srcOf(im); if (!s) continue;
      var id = idOf(im); if (S.seen[id] === s) continue;
      if (only && !only[id]) continue;
      var top = r.top + window.scrollY, left = r.left + window.scrollX, prev = null, next = null;
      // hors du chapitre : ailleurs que dans la colonne des pages (une image plus étroite au milieu des pages, elle, reste), et soit nettement plus étroite qu'elles,
      // soit sous la colonne sans être de la même sorte (largeur d'origine et dossier) : commentaire, bannière, vignette
      if (col && !all) {
        var inCol = top >= col.top - 4 && top + r.height <= col.bottom + 4 && left >= col.left - 4 && left + r.width <= col.right + 4;
        if (!inCol) {
          if (r.width < col.lim) continue;
          if (top >= col.bottom - 4 && !sameKind(im, s, col)) continue;
        }
      }
      for (var k=0;k<geo.length;k++){
        var q = geo[k]; if (q.im === im || q.im.naturalWidth !== im.naturalWidth) continue;
        if (near(q.bottom, top) && near(q.left, left) && near(q.width, r.width)) prev = q.im;
        if (near(q.top, top + r.height) && near(q.left, left) && near(q.width, r.width)) next = q.im;
      }
      // l'image du dessus n'est pas encore chargée : on l'attend (6 s au plus) pour la joindre à celle-ci ; un minuteur relance la collecte si rien d'autre ne le fait
      if (prev && !(prev.complete && prev.naturalHeight > 0)) {
        if (!S.first[id]) { S.first[id] = now; setTimeout(notify, 6200); }
        if (now - S.first[id] < 6000) continue;
        prev = null;
      }
      S.seen[id] = s;
      out.push({id:id, src:s, w:im.naturalWidth, h:im.naturalHeight, top:top, sy:window.scrollY, dh:r.height, vh:window.innerHeight, n:i,
                prev: prev ? {id:idOf(prev), src:srcOf(prev), h:prev.naturalHeight} : null, next: next ? {h:next.naturalHeight} : null});
    }
    return out;
  }
  // images qui attendent : chargées, assez grandes, pas encore données à la traduction, et pas une vignette (nettement plus étroite que les pages) — ce que la collecte laisse de côté
  // sous le chapitre (commentaires, bannières) ou ce qui est apparu depuis ; l'application le propose d'un bouton discret
  function waiting(strict){
    var col = strict ? column(geoList()) : null, n = 0, l = document.images;
    for (var i=0;i<l.length;i++){
      var im = l[i]; if (im.getAttribute('data-mt-img')) continue;
      if (!im.complete || im.naturalWidth < MIN || im.naturalHeight < MIN) continue;
      var r = im.getBoundingClientRect(); if (r.width < MIN*0.8 || r.height < MIN*0.8) continue;
      if (col && r.width < col.lim) continue;
      var s = srcOf(im); if (!s) continue;
      if (im.__mtid && S.seen[im.__mtid] === s) continue;
      n++;
    }
    return n;
  }
  // Fin de chapitre déjà déclarée : une page arrivée depuis qui prolonge le chapitre — empilée juste sous une page déjà donnée (48 px au plus, même bord gauche, même largeur
  // affichée) et de la même sorte que les pages — est une page du chapitre chargée en retard (chargement différé : vide et sans hauteur au moment de l'arrêt), pas un commentaire :
  // elle est donnée tout de suite. Les pages prises s'enchaînent (la suivante peut s'empiler sous une page prise au même passage). Le reste attend le bouton « Traduire ».
  function tail(strict){
    var geo = geoList(), col = strict ? column(geo) : null, ends = [], cand = [], took = {}, i, k;
    for (i=0;i<geo.length;i++){ var g = geo[i]; if (g.im.__mtid && S.seen[g.im.__mtid]) ends.push(g); else cand.push(g); }
    if (!ends.length) return took;
    cand.sort(function(a, b){ return a.top - b.top; });
    var more = true;
    while (more) {
      more = false;
      for (i=0;i<cand.length;i++){
        var c = cand[i], im = c.im; if (took[idOf(im)]) continue;
        if (!im.complete || im.naturalWidth < MIN || im.naturalHeight < MIN) continue;
        var s = srcOf(im); if (!s) continue;
        if (col && !sameKind(im, s, col)) continue;
        for (k=0;k<ends.length;k++){
          var e = ends[k];
          if (c.top >= e.bottom - 4 && c.top - e.bottom <= 48 && Math.abs(c.left - e.left) <= 8 && Math.abs(c.width - e.width) <= Math.max(8, e.width * 0.03)) { took[idOf(im)] = 1; ends.push(c); more = true; break; }
        }
      }
    }
    return took;
  }
  function collectTail(strict){ var t = tail(strict); for (var x in t) return collect(false, strict, true, t); return []; }
  // images du chapitre pas encore chargées (affichées en grand mais encore vides : chargement différé) : tant qu'il en reste, le chapitre n'est pas complet
  function chainPending(strict){
    var geo = geoList(), col = strict ? column(geo) : null, n = 0;
    for (var i=0;i<geo.length;i++){
      var g = geo[i], im = g.im;
      if (im.complete && im.naturalWidth >= MIN && im.naturalHeight >= MIN) continue;
      if (!col || (g.top >= col.top - 4 && g.bottom <= col.fullBottom + 4)) n++;
    }
    return n;
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
    var im = findImg(data.target || id); if (!im) return false;     // « target » : superposition rattachée à l'image du dessus (partie d'un texte coupé par la jonction)
    // numéro d'envoi : un morceau d'un envoi plus ancien, arrivé après un envoi plus récent, ne se pose pas par-dessus (il remettrait l'ancien texte)
    if (data.gen !== undefined) { var g0 = S.gen[id] || 0; if (data.append ? data.gen !== g0 : data.gen < g0) return false; S.gen[id] = data.gen; }
    var old = S.entries[id];
    if (data.append && old && old.div) {
      var d = old.div;
    } else {
      if (old && old.div) old.div.remove();
      var d = document.createElement('div');
      d.setAttribute('data-mt-overlay', '1');
      d.style.cssText = 'position:absolute;pointer-events:none;z-index:2147483000;overflow:hidden;margin:0;padding:0;';
      if (data.target) d.style.zIndex = 2147483001;     // la partie posée sur l'image du dessus recouvre toujours une éventuelle traduction partielle de cette image
      document.documentElement.appendChild(d);
    }
    var ps = data.patches || [];
    for (var i=0;i<ps.length;i++){
      var q0 = ps[i], im2 = document.createElement('img');
      im2.src = q0.png; im2.setAttribute('data-mt-img', '1'); im2.draggable = false;
      if (q0.bid !== undefined) { im2.setAttribute('data-bid', q0.bid); im2.setAttribute('data-src', q0.src || ''); im2.setAttribute('data-tr', q0.tr || ''); }
      if (q0.inert) im2.setAttribute('data-inert', '1');       // emplacement d'une traduction supprimée ou ignorée : on peut la retrouver d'un appui long, mais il n'y a rien à effacer
      im2.style.cssText = 'position:absolute;margin:0;padding:0;border:0;max-width:none;pointer-events:none;';
      if (ER.on && q0.bid !== undefined && !q0.inert) mark(im2, true);
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
  // fin de chapitre : prévient l'application quand on approche du bas de la page
  var nbState = false, nbTick = false;
  function nbCheck(){ nbTick = false; var nb = (window.innerHeight + window.scrollY) >= (document.documentElement.scrollHeight - 900); if (nb !== nbState) { nbState = nb; try { AndroidMT.onBottom(nb); } catch(e) {} } }
  window.addEventListener('scroll', function(){ if (!nbTick) { nbTick = true; setTimeout(nbCheck, 300); } }, {passive:true});
  // position de lecture : l'application traduit d'abord ce que l'on regarde à cet instant
  var vpTick = false;
  function vpReport(){ vpTick = false; try { AndroidMT.onViewport(window.scrollY, window.innerHeight); } catch(e) {} }
  window.addEventListener('scroll', function(){ if (!vpTick) { vpTick = true; setTimeout(vpReport, 250); } }, {passive:true});
  setTimeout(nbCheck, 800);

  // appui long (0,75 s : les 0,45 s d'avant + 0,3 s) sur une traduction : tant que le doigt reste posé, la traduction s'efface et l'original réapparaît dessous (aperçu) ; l'application propose alors de la corriger
  var LP_MS = 750, LP_SLOP = 14;
  var LP = {x:0, y:0, timer:null};
  var PK = {el:null, open:false, sy:0};
  // les propositions ouvertes par l'appui long disparaissent dès que l'on fait défiler la page
  function peekGone(){ if (!PK.open) return; PK.open = false; try { AndroidMT.onPeekGone(); } catch(e) {} }
  function peek(x, y){
    var best = null, bestArea = 1e18, bestId = null;
    for (var id in S.entries){
      var kids = S.entries[id].div.children;
      for (var i=0;i<kids.length;i++){
        var k = kids[i]; if (!k.getAttribute || !k.getAttribute('data-bid')) continue;
        var r = k.getBoundingClientRect();
        if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom && r.width * r.height < bestArea) { best = k; bestArea = r.width * r.height; bestId = id; }
      }
    }
    if (best) {
      best.style.visibility = 'hidden'; PK.el = best; PK.open = true; PK.sy = window.scrollY;
      try { AndroidMT.onPeek(bestId, best.getAttribute('data-bid'), best.getAttribute('data-src'), best.getAttribute('data-tr')); } catch(e) {}
      return;
    }
    // aucune traduction sous le doigt : si c'est une image de la page déjà traitée, l'application propose d'y ajouter un texte (texte non détecté, zone ignorée)
    if (!S.visible) return;
    var imgs = document.images;
    for (var j=0;j<imgs.length;j++){
      var im = imgs[j]; if (im.getAttribute('data-mt-img') || !im.__mtid) continue;
      var ir = im.getBoundingClientRect();
      if (ir.width > 0 && ir.height > 0 && x >= ir.left && x <= ir.right && y >= ir.top && y <= ir.bottom) {
        PK.open = true; PK.sy = window.scrollY;
        try { AndroidMT.onPeekEmpty(im.__mtid, (x - ir.left) / ir.width * im.naturalWidth, (y - ir.top) / ir.height * im.naturalHeight); } catch(e) {}
        return;
      }
    }
  }
  function unpeek(){ if (PK.el) { PK.el.style.visibility = ''; PK.el = null; } }
  // suppression demandée par l'application : le patch disparaît tout de suite (la page est ensuite redessinée sans lui)
  function dropPatch(id, bid){
    var e = S.entries[id]; if (!e) return;
    var kids = e.div.children;
    for (var i=0;i<kids.length;i++){ var k = kids[i]; if (k.getAttribute && k.getAttribute('data-bid') === String(bid)) k.style.display = 'none'; }
  }
  document.addEventListener('touchstart', function(e){
    if (e.touches.length !== 1) { clearTimeout(LP.timer); unpeek(); peekGone(); return; }
    LP.x = e.touches[0].clientX; LP.y = e.touches[0].clientY; clearTimeout(LP.timer);
    LP.timer = setTimeout(function(){ peek(LP.x, LP.y); }, LP_MS);
  }, {passive:true, capture:true});
  document.addEventListener('touchmove', function(e){
    if (!e.touches.length) return;
    if (Math.abs(e.touches[0].clientX - LP.x) > LP_SLOP || Math.abs(e.touches[0].clientY - LP.y) > LP_SLOP) { clearTimeout(LP.timer); unpeek(); peekGone(); }
  }, {passive:true, capture:true});
  ['touchend','touchcancel'].forEach(function(n){ document.addEventListener(n, function(){ clearTimeout(LP.timer); unpeek(); }, {passive:true, capture:true}); });
  window.addEventListener('scroll', function(){ if (PK.open && Math.abs(window.scrollY - PK.sy) > 8) { unpeek(); peekGone(); } }, {passive:true});
  // ce long appui est géré ici : le moteur de la page ne doit pas y mêler le sien (menu contextuel, glisser-déposer de l'image), qui interrompait parfois le geste
  try { var stl = document.createElement('style'); stl.textContent = 'img{-webkit-user-drag:none!important;-webkit-touch-callout:none!important}'; (document.head || document.documentElement).appendChild(stl); } catch(e) {}
  document.addEventListener('contextmenu', function(e){ if (e.target && e.target.tagName === 'IMG') e.preventDefault(); }, true);

  // sens du défilement (barre d'adresse qui se masque) et avancement de lecture
  var sc = {y: window.scrollY, acc: 0, dir: 0, t: null, sent: -1};
  function onMove(){
    var y = window.scrollY, dy = y - sc.y; sc.y = y;
    if (dy !== 0) {
      if ((dy > 0) !== (sc.acc > 0)) sc.acc = 0;
      sc.acc += dy;
      if (Math.abs(sc.acc) >= 28) { var d = sc.acc > 0 ? 1 : -1; if (d !== sc.dir) { sc.dir = d; try { AndroidMT.onScrollDir(d > 0); } catch(e) {} } sc.acc = 0; }
    }
    if (!sc.t) sc.t = setTimeout(function(){
      sc.t = null;
      var h = document.documentElement.scrollHeight - window.innerHeight;
      // page pas encore (ou plus) défilable : images pas encore chargées, page en cours de remplacement… rien n'a été lu, on ne signale rien (« 100 % » marquait le chapitre comme lu dès l'ouverture)
      if (h <= 0) return;
      var p = Math.max(0, Math.min(100, Math.round(100 * window.scrollY / h)));
      if (p !== sc.sent) { sc.sent = p; try { AndroidMT.onProgress(p); } catch(e) {} }
    }, 1000);
  }
  window.addEventListener('scroll', onMove, {passive:true});
  setTimeout(onMove, 1500);

  // le zoom par double appui du navigateur est coupé (le pincement reste) : le double appui sert au plein écran, détecté côté application (voir obtainWebView)
  try { var zs = document.createElement('style'); zs.textContent = 'html,body{touch-action:manipulation}'; (document.head || document.documentElement).appendChild(zs); } catch(e) {}
  // appui simple sur la page (ni défilement, ni lien, ni double appui) : ouvre le menu de lecture en plein écran, montre ou masque la barre d'adresse sinon
  var tp = {x:0, y:0, t:0, moved:false, timer:null, last:0};
  document.addEventListener('touchstart', function(e){
    if (e.touches.length !== 1) { tp.moved = true; return; }
    tp.x = e.touches[0].clientX; tp.y = e.touches[0].clientY; tp.t = Date.now(); tp.moved = false;
  }, {passive:true, capture:true});
  document.addEventListener('touchmove', function(e){
    if (!e.touches.length) return;
    if (Math.abs(e.touches[0].clientX - tp.x) > 10 || Math.abs(e.touches[0].clientY - tp.y) > 10) tp.moved = true;
  }, {passive:true, capture:true});
  document.addEventListener('touchend', function(e){
    if (tp.moved || Date.now() - tp.t > 300 || e.changedTouches.length !== 1) return;
    var el = e.target;
    while (el && el !== document.body && el !== document.documentElement) {
      var tn = el.tagName;
      if (tn === 'A' || tn === 'BUTTON' || tn === 'INPUT' || tn === 'TEXTAREA' || tn === 'SELECT' || tn === 'VIDEO' || tn === 'LABEL' || el.onclick) return;
      el = el.parentElement;
    }
    var now = Date.now();
    clearTimeout(tp.timer);
    if (now - tp.last < 320) { tp.last = 0; return; }
    tp.last = now;
    tp.timer = setTimeout(function(){ tp.last = 0; try { AndroidMT.onTap(); } catch(e) {} }, 330);
  }, {passive:true, capture:true});

  // gomme : tant qu'elle est active, un appui simple sur une traduction la supprime (l'original réapparaît dessous) ; ni les barres ni un lien éventuel ne réagissent à cet appui
  function patchAt(x, y){
    var best = null, bestArea = 1e18, bestId = null;
    for (var id in S.entries){
      var e = S.entries[id]; if (!e.div || e.div.style.display === 'none') continue;
      var kids = e.div.children;
      for (var i=0;i<kids.length;i++){
        var k = kids[i]; if (!k.getAttribute || !k.getAttribute('data-bid') || k.getAttribute('data-inert') || k.style.display === 'none') continue;
        var r = k.getBoundingClientRect();
        if (r.width > 0 && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom && r.width * r.height < bestArea) { best = k; bestArea = r.width * r.height; bestId = id; }
      }
    }
    return best ? {el:best, id:bestId} : null;
  }
  function setErase(on){
    ER.on = !!on;
    for (var id in S.entries){
      var e = S.entries[id]; if (!e.div) continue;
      var kids = e.div.children;
      for (var i=0;i<kids.length;i++){ var k = kids[i]; if (k.getAttribute && k.getAttribute('data-bid') && !k.getAttribute('data-inert')) mark(k, ER.on); }
    }
  }
  var EP = {x:0, y:0, t:0, moved:false};
  document.addEventListener('touchstart', function(e){
    if (e.touches.length !== 1) { EP.moved = true; return; }
    EP.x = e.touches[0].clientX; EP.y = e.touches[0].clientY; EP.t = Date.now(); EP.moved = false;
  }, {passive:true, capture:true});
  document.addEventListener('touchmove', function(e){
    if (!e.touches.length) return;
    if (Math.abs(e.touches[0].clientX - EP.x) > 10 || Math.abs(e.touches[0].clientY - EP.y) > 10) EP.moved = true;
  }, {passive:true, capture:true});
  document.addEventListener('touchend', function(e){
    if (!ER.on || EP.moved || Date.now() - EP.t > 300 || e.changedTouches.length !== 1) return;
    var h = patchAt(EP.x, EP.y); if (!h) return;
    ER.t = Date.now(); ER.x = EP.x; ER.y = EP.y;
    clearTimeout(tp.timer); tp.last = 0;                         // ce toucher ne sert qu'à effacer : il n'ouvre ni ne ferme les barres
    var el = h.el;
    el.style.display = 'none';                                   // la traduction disparaît tout de suite ; l'application enregistre la suppression et redessine la page
    try { AndroidMT.onErase(h.id, el.getAttribute('data-bid'), el.getAttribute('data-src'), el.getAttribute('data-tr')); } catch(err) {}
  }, {passive:true, capture:true});
  document.addEventListener('click', function(e){
    if (!ER.on) return;
    if ((Date.now() - ER.t < 700 && Math.abs(e.clientX - ER.x) < 30 && Math.abs(e.clientY - ER.y) < 30) || patchAt(e.clientX, e.clientY)) { e.preventDefault(); e.stopImmediatePropagation(); }
  }, true);
  // tests automatiques : rejoue un appui simple au centre de la première traduction visible (faux s'il n'y en a pas)
  function testErase(){
    var pick = null;
    for (var id in S.entries){
      var e = S.entries[id]; if (!e.div) continue;
      var kids = e.div.children;
      for (var i=0;i<kids.length && !pick;i++){
        var k = kids[i]; if (!k.getAttribute || !k.getAttribute('data-bid') || k.getAttribute('data-inert') || k.style.display === 'none') continue;
        var r = k.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        if (r.width > 0 && cx >= 0 && cx <= window.innerWidth && cy >= 0 && cy <= window.innerHeight) pick = {x:cx, y:cy};
      }
      if (pick) break;
    }
    if (!pick) return false;
    var tgt = document.elementFromPoint(pick.x, pick.y) || document.body;
    function fire(type){
      var t = new Touch({identifier: 7, target: tgt, clientX: pick.x, clientY: pick.y});
      var list = (type === 'touchend') ? [] : [t];
      tgt.dispatchEvent(new TouchEvent(type, {bubbles:true, cancelable:true, touches:list, targetTouches:list, changedTouches:[t]}));
    }
    fire('touchstart'); setTimeout(function(){ fire('touchend'); }, 80);
    return true;
  }

  // textes écartés : chaque texte lu mais laissé de côté est entouré (cadres posés dans le calque de l'image, sans effet sur les appuis) ; tant que le mode est actif, un appui simple sur un cadre
  // demande sa traduction (le cadre s'assombrit en attendant) ; ni les barres ni un lien éventuel ne réagissent à cet appui
  var DS = {on:false, t:0, x:0, y:0};
  function setDiscs(id, list, w, h){
    var e = S.entries[id];
    if (!e) { if (!list.length) return true; if (!apply(id, {w:w, h:h, patches:[]})) return false; e = S.entries[id]; }      // page sans traduction : un calque vide porte les cadres (aucun s'il n'y a rien à entourer)
    if (!e || !e.div) return false;
    var kids = e.div.children;
    for (var i=kids.length-1;i>=0;i--){ var k0 = kids[i]; if (k0.getAttribute && k0.getAttribute('data-did') !== null) k0.remove(); }
    for (var j=0;j<list.length;j++){
      var q = list[j], b = document.createElement('div');
      b.setAttribute('data-did', q.id);
      b.style.cssText = 'position:absolute;box-sizing:border-box;pointer-events:none;border:2px dashed rgba(255,150,0,0.95);background:rgba(255,150,0,0.10);';
      b.style.left = (q.x / e.w * 100) + '%'; b.style.top = (q.y / e.h * 100) + '%';
      b.style.width = (q.w / e.w * 100) + '%'; b.style.height = (q.h / e.h * 100) + '%';
      b.style.display = DS.on ? 'block' : 'none';
      e.div.appendChild(b);
    }
    return true;
  }
  function setDisc(on){
    DS.on = !!on;
    for (var id in S.entries){
      var e = S.entries[id]; if (!e.div) continue;
      var kids = e.div.children;
      for (var i=0;i<kids.length;i++){ var k = kids[i]; if (k.getAttribute && k.getAttribute('data-did') !== null) k.style.display = DS.on ? 'block' : 'none'; }
    }
  }
  function discAt(x, y){
    var best = null, bestArea = 1e18, bestId = null;
    for (var id in S.entries){
      var e = S.entries[id]; if (!e.div || e.div.style.display === 'none') continue;
      var kids = e.div.children;
      for (var i=0;i<kids.length;i++){
        var k = kids[i]; if (!k.getAttribute || k.getAttribute('data-did') === null || k.style.display === 'none' || k.getAttribute('data-busy')) continue;
        var r = k.getBoundingClientRect();
        if (r.width > 0 && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom && r.width * r.height < bestArea) { best = k; bestArea = r.width * r.height; bestId = id; }
      }
    }
    return best ? {el:best, id:bestId} : null;
  }
  document.addEventListener('touchend', function(e){
    if (!DS.on || EP.moved || Date.now() - EP.t > 300 || e.changedTouches.length !== 1) return;
    var h = discAt(EP.x, EP.y); if (!h) return;
    DS.t = Date.now(); DS.x = EP.x; DS.y = EP.y;
    clearTimeout(tp.timer); tp.last = 0;                         // ce toucher ne sert qu'à traduire : il n'ouvre ni ne ferme les barres
    h.el.setAttribute('data-busy', '1'); h.el.style.background = 'rgba(255,150,0,0.45)';
    try { AndroidMT.onDiscard(h.id, h.el.getAttribute('data-did')); } catch(err) {}
  }, {passive:true, capture:true});
  document.addEventListener('click', function(e){
    if (!DS.on) return;
    if ((Date.now() - DS.t < 700 && Math.abs(e.clientX - DS.x) < 30 && Math.abs(e.clientY - DS.y) < 30) || discAt(e.clientX, e.clientY)) { e.preventDefault(); e.stopImmediatePropagation(); }
  }, true);
  // tests automatiques : rejoue un appui simple au centre du premier cadre visible (faux s'il n'y en a pas)
  function testDisc(){
    var pick = null;
    for (var id in S.entries){
      var e = S.entries[id]; if (!e.div || e.div.style.display === 'none') continue;
      var kids = e.div.children;
      for (var i=0;i<kids.length && !pick;i++){
        var k = kids[i]; if (!k.getAttribute || k.getAttribute('data-did') === null || k.style.display === 'none' || k.getAttribute('data-busy')) continue;
        var r = k.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        if (r.width > 0 && cx >= 0 && cx <= window.innerWidth && cy >= 0 && cy <= window.innerHeight) pick = {x:cx, y:cy};
      }
      if (pick) break;
    }
    if (!pick) return false;
    var tgt = document.elementFromPoint(pick.x, pick.y) || document.body;
    function fire(type){
      var t = new Touch({identifier: 8, target: tgt, clientX: pick.x, clientY: pick.y});
      var list = (type === 'touchend') ? [] : [t];
      tgt.dispatchEvent(new TouchEvent(type, {bubbles:true, cancelable:true, touches:list, targetTouches:list, changedTouches:[t]}));
    }
    fire('touchstart'); setTimeout(function(){ fire('touchend'); }, 80);
    return true;
  }

  // tests automatiques : où poser un vrai doigt (pixels de la vue) — au centre de la première traduction affichée, et sur une zone d'image sans traduction
  // bad : textes que l'application tient pour supprimés ou ignorés ({image: [numéros]}) ; la page peut les montrer encore un instant, le temps d'être redessinée
  function probe(bad){
    var dpr = window.devicePixelRatio || 1, out = {}, patches = [], first = null;
    for (var id in S.entries){
      var kids = S.entries[id].div.children, skip = bad ? (bad[id] || bad[String(id).replace('~up', '')] || []) : [];
      for (var i=0;i<kids.length;i++){
        var k = kids[i]; if (!k.getAttribute || !k.getAttribute('data-bid')) continue;
        patches.push(k.getBoundingClientRect());
        if (!first && !k.getAttribute('data-inert') && k.style.display !== 'none' && skip.indexOf(+k.getAttribute('data-bid')) < 0) first = k;
      }
    }
    if (first) {
      var r = first.getBoundingClientRect();
      if (r.top < 0 || r.bottom > window.innerHeight) { window.scrollBy(0, r.top + r.height / 2 - window.innerHeight / 2); r = first.getBoundingClientRect(); patches = []; for (var id3 in S.entries){ var kk = S.entries[id3].div.children; for (var i3=0;i3<kk.length;i3++){ if (kk[i3].getAttribute && kk[i3].getAttribute('data-bid')) patches.push(kk[i3].getBoundingClientRect()); } } }
      out.tx = Math.round((r.left + r.width / 2) * dpr); out.ty = Math.round((r.top + r.height / 2) * dpr);
    }
    var imgs = document.images;
    for (var a=0; a<imgs.length && out.ex === undefined; a++){
      var im = imgs[a]; if (im.getAttribute('data-mt-img') || !im.__mtid) continue;
      var ir = im.getBoundingClientRect(); if (ir.width <= 0) continue;
      for (var gy=0.05; gy<0.96 && out.ex === undefined; gy+=0.05){
        for (var gx=0.05; gx<0.96; gx+=0.05){
          var px = ir.left + ir.width * gx, py = ir.top + ir.height * gy;
          if (px < 10 || px > window.innerWidth - 10 || py < 60 || py > window.innerHeight - 60) continue;
          var free = true;
          for (var q=0; q<patches.length; q++){ var pr = patches[q]; if (px > pr.left - 30 && px < pr.right + 30 && py > pr.top - 30 && py < pr.bottom + 30) { free = false; break; } }
          if (free) { out.ex = Math.round(px * dpr); out.ey = Math.round(py * dpr); break; }
        }
      }
    }
    return JSON.stringify(out);
  }

  // reprise de lecture : on attend que la hauteur de la page se stabilise (images qui se chargent), puis on se place ; un toucher de l'utilisateur annule
  function resumeTo(pct){
    var tries = 0, lastH = -1, stable = 0, stopped = false;
    document.addEventListener('touchstart', function(){ stopped = true; }, {once:true, passive:true, capture:true});
    (function tick(){
      if (stopped) return;
      var h = document.documentElement.scrollHeight;
      if (h === lastH) stable++; else { stable = 0; lastH = h; }
      tries++;
      if (stable >= 3 || tries > 25) { window.scrollTo(0, Math.round((h - window.innerHeight) * pct / 100)); return; }
      setTimeout(tick, 400);
    })();
  }
  window.__mt = {probe:probe, collect:collect, collectTail:collectTail, waiting:waiting, chainPending:chainPending, apply:apply, setVisible:setVisible, clear:clear, getB64:getB64, resumeTo:resumeTo, forget:forget, dropPatch:dropPatch, setErase:setErase, testErase:testErase, setDisc:setDisc, setDiscs:setDiscs, testDisc:testDisc};
})();
"""
}
