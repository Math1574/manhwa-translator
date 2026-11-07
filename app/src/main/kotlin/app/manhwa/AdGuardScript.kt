package app.manhwa

/**
 * Script de garde injecté dans toutes les pages (et leurs cadres) avant les scripts du site. Il fait ce que le filtrage réseau ne peut pas faire :
 *  - `window.open` ne crée jamais de fenêtre ; il renvoie un faux objet fenêtre pour que les scripts de pop-under n'aient pas de repli ;
 *    un lien du site lui-même ouvert par script après un geste de l'utilisateur est suivi normalement ;
 *  - les liens s'ouvrent dans la page (pas de nouvel onglet), et une superposition invisible plein écran qui cache un lien vers un autre site est retirée au premier appui ;
 *  - un vrai lien visible vers un autre site est signalé à l'application, qui l'autorise ;
 *  - les cadres publicitaires transparents plein écran ajoutés après coup sont retirés ;
 *  - masquage d'éléments propre au site (règles des listes), demandé à l'application de façon synchrone dès le début du document.
 * Le bloqueur peut être désactivé site par site : le script ne fait alors rien.
 */
object AdGuardScript {
    val JS: String = """
(function () {
  if (window.__mtGuard) return;
  try { Object.defineProperty(window, '__mtGuard', { value: 1 }); } catch (e) { window.__mtGuard = 1; }
  var B = null;
  function bridge() { if (!B) { try { B = window.AndroidMT || null; } catch (e) {} } return B; }
  function note(kind, url) { try { var b = bridge(); if (b && b.adBlocked) b.adBlocked(kind, String(url || '')); } catch (e) {} }
  try { var b0 = bridge(); if (b0 && b0.adsOn && !b0.adsOn(String(location.hostname))) return; } catch (e) {}

  function siteOf(h) {
    h = String(h || '').toLowerCase(); var p = h.split('.'); if (p.length <= 2) return h;
    var n = (p[p.length - 1].length === 2 && p[p.length - 2].length <= 3) ? 3 : 2; return p.slice(-n).join('.');
  }
  var mySite = siteOf(location.hostname);
  function absUrl(u) { try { return new URL(String(u || ''), location.href); } catch (e) { return null; } }

  // 1. window.open : jamais de fenêtre
  var fakeWin = function () {
    return { closed: false, close: function () {}, focus: function () {}, blur: function () {}, postMessage: function () {},
      document: { write: function () {}, writeln: function () {}, open: function () {}, close: function () {} },
      location: { href: '', assign: function () {}, replace: function () {} } };
  };
  var open2 = function open(u) {
    var a = absUrl(u);
    if (a && siteOf(a.hostname) === mySite && navigator.userActivation && navigator.userActivation.isActive) { try { location.href = a.href; } catch (e) {} }
    else note('pop-up', a ? a.href : u);
    return fakeWin();
  };
  try { Object.defineProperty(window, 'open', { value: open2, writable: false, configurable: false }); } catch (e) { try { window.open = open2; } catch (e2) {} }

  // 2. liens et superpositions
  function linkOf(n) { for (var i = 0; n && n !== document && i < 8; i++, n = n.parentNode) { if (n.tagName === 'A' && n.href) return n; } return null; }
  function bigAndInvisible(el) {
    var cs = getComputedStyle(el), r = el.getBoundingClientRect();
    if (r.width * r.height < innerWidth * innerHeight * 0.45) return false;
    var pos = cs.position; if (pos !== 'fixed' && pos !== 'absolute' && pos !== 'sticky') return false;
    var invisible = parseFloat(cs.opacity) < 0.1 || cs.visibility === 'hidden';
    var empty = !(el.textContent || '').trim() && !el.querySelector('img,video,canvas,svg,picture,button,input,iframe');
    return invisible || empty;
  }
  function overlayish(el) { for (var i = 0, n = el; n && n.nodeType === 1 && i < 4; i++, n = n.parentElement) { if (bigAndInvisible(n)) return n; } return null; }
  function onDown(ev) {
    try {
      var t = ev.target; if (!t || t.nodeType !== 1) return;
      var a = linkOf(t); if (!a) return;
      if (a.target && a.target !== '_self') a.removeAttribute('target');
      if (siteOf(a.hostname) === mySite) return;
      var ov = overlayish(a);
      if (ov) { ev.preventDefault(); ev.stopImmediatePropagation(); note('superposition', a.href); try { ov.remove(); } catch (e) {} return; }
      var b = bridge(); if (b && b.genuineLink) b.genuineLink(a.href);
    } catch (e) {}
  }
  ['pointerdown', 'touchstart', 'mousedown', 'click'].forEach(function (n) { try { document.addEventListener(n, onDown, true); } catch (e) {} });

  // 3. cadres publicitaires transparents plein écran ajoutés après coup
  function sweep(root) {
    var fr = root.tagName === 'IFRAME' ? [root] : (root.querySelectorAll ? root.querySelectorAll('iframe') : []);
    for (var i = 0; i < fr.length; i++) {
      var f = fr[i];
      try {
        var u = absUrl(f.src); if (!u || siteOf(u.hostname) === mySite) continue;
        var cs = getComputedStyle(f), r = f.getBoundingClientRect();
        if ((cs.position === 'fixed' || cs.position === 'absolute') && r.width * r.height >= innerWidth * innerHeight * 0.45 && parseFloat(cs.opacity) < 0.1) { note('superposition', u.href); f.remove(); }
      } catch (e) {}
    }
  }
  try {
    new MutationObserver(function (ms) {
      for (var i = 0; i < ms.length; i++) { var an = ms[i].addedNodes; for (var j = 0; j < an.length; j++) if (an[j].nodeType === 1) sweep(an[j]); }
    }).observe(document, { childList: true, subtree: true });
  } catch (e) {}

  // 4. masquage d'éléments propre au site
  var cssDone = false;
  function injectCss() {
    if (cssDone) return;
    var css = ''; try { var b = bridge(); if (b && b.cosmeticCss) css = b.cosmeticCss(String(location.hostname)) || ''; else return; } catch (e) { return; }
    cssDone = true; if (!css) return;
    var s = document.createElement('style'); s.setAttribute('data-mt', '1'); s.textContent = css;
    var add = function () { var p = document.head || document.documentElement; if (p) { p.appendChild(s); return true; } return false; };
    if (!add()) { try { var mo = new MutationObserver(function () { if (add()) mo.disconnect(); }); mo.observe(document, { childList: true }); } catch (e) {} }
  }
  injectCss();
  try { document.addEventListener('DOMContentLoaded', injectCss); } catch (e) {}
})();
"""
}
