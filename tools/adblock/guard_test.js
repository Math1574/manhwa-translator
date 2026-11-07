const { JSDOM } = require('jsdom');
const fs = require('fs');
const path = require('path');
const src = fs.readFileSync(path.join(__dirname, '..', '..', 'app', 'src', 'main', 'kotlin', 'app', 'manhwa', 'AdGuardScript.kt'), 'utf8');
const js = (src.match(/val JS: String = """([\s\S]*?)"""/) || [])[1];
if (!js) { console.log('script de garde introuvable dans AdGuardScript.kt'); process.exit(1); }
new Function(js);   // erreur de syntaxe : le test s'arrête ici
let fails = 0;
function ok(c, m) { console.log((c ? 'ok    ' : 'ÉCHEC ') + m); if (!c) fails++; }

function page(opts) {
  const log = [];
  const dom = new JSDOM('<!doctype html><html><head></head><body></body></html>', { url: 'http://scan-site.test/chapitre-1', runScripts: 'outside-only', pretendToBeVisual: true });
  const w = dom.window;
  w.AndroidMT = {
    adsOn: () => opts.on !== false,
    adBlocked: (k, u) => log.push(k + '|' + u),
    genuineLink: (h) => log.push('lien|' + h),
    cosmeticCss: (h) => opts.css || '',
  };
  return { dom, w, log };
}

// 1. window.open neutralisé, faux objet fenêtre, signalement à l'application
{
  const { w, log } = page({ css: '.pub{display:none!important}' });
  w.eval(js);
  const r = w.open('http://popunder.test/x');
  ok(r && r.closed === false && typeof r.close === 'function', 'window.open renvoie un faux objet fenêtre');
  ok(log.some(x => x.startsWith('pop-up|http://popunder.test/x')), 'pop-up signalé à l\'application : ' + log.join(','));
  const st = w.document.querySelector('style[data-mt]');
  ok(!!st && st.textContent.includes('.pub'), 'feuille de masquage injectée');
  w.eval(js);
  ok(w.document.querySelectorAll('style[data-mt]').length === 1, 'script idempotent (une seule injection)');
}
// 2. liens : nouvel onglet supprimé, vrai lien externe signalé
{
  const { w, log } = page({});
  w.eval(js);
  const d = w.document;
  d.body.innerHTML = '<a id="a1" target="_blank" href="/suite">suite</a><a id="a2" href="http://friend.test/page">ami</a>';
  const ev = (el, type) => el.dispatchEvent(new w.MouseEvent(type, { bubbles: true, cancelable: true }));
  ev(d.getElementById('a1'), 'mousedown');
  ok(!d.getElementById('a1').hasAttribute('target'), 'target=_blank retiré d\'un lien du site');
  ev(d.getElementById('a2'), 'mousedown');
  ok(log.some(x => x === 'lien|http://friend.test/page'), 'vrai lien externe signalé comme voulu par l\'utilisateur');
}
// 3. superposition invisible plein écran au-dessus d'un lien externe : retirée, clic annulé
{
  const { w, log } = page({});
  w.innerWidth = 400; w.innerHeight = 800;
  w.Element.prototype.getBoundingClientRect = function () { return this.id === 'ov' ? { left: 0, top: 0, width: 400, height: 800, right: 400, bottom: 800 } : { left: 0, top: 0, width: 50, height: 20, right: 50, bottom: 20 }; };
  w.eval(js);
  const d = w.document;
  d.body.innerHTML = '<a id="ov" href="http://popunder.test/overlay" style="position:fixed;left:0;top:0;width:100%;height:100%;opacity:0"></a><p>texte</p>';
  const a = d.getElementById('ov');
  const e = new w.MouseEvent('pointerdown', { bubbles: true, cancelable: true });
  a.dispatchEvent(e);
  ok(d.getElementById('ov') === null, 'superposition invisible retirée au premier appui');
  ok(e.defaultPrevented, 'le premier appui est annulé');
  ok(log.some(x => x.startsWith('superposition|')), 'superposition signalée');
}
// 4. une superposition qui contient une image n'est pas retirée
{
  const { w, log } = page({});
  w.innerWidth = 400; w.innerHeight = 800;
  w.Element.prototype.getBoundingClientRect = function () { return this.id === 'ov' ? { left: 0, top: 0, width: 400, height: 800, right: 400, bottom: 800 } : { left: 0, top: 0, width: 50, height: 20, right: 50, bottom: 20 }; };
  w.eval(js);
  const d = w.document;
  d.body.innerHTML = '<a id="ov" href="http://friend.test/x" style="position:fixed;left:0;top:0;width:100%;height:100%;opacity:1"><img src="x.png"></a>';
  d.getElementById('ov').dispatchEvent(new w.MouseEvent('pointerdown', { bubbles: true, cancelable: true }));
  ok(d.getElementById('ov') !== null, 'un lien visible avec une image n\'est jamais retiré');
}
// 5. bloqueur désactivé pour le site : le script ne touche à rien
{
  const { w, log } = page({ on: false });
  const nativeOpen = w.open;
  w.eval(js);
  ok(w.open === nativeOpen && w.document.querySelector('style[data-mt]') === null, 'bloqueur éteint : window.open et page intacts');
}
// 6. cadre publicitaire transparent plein écran ajouté après coup
{
  const { w, log } = page({});
  w.innerWidth = 400; w.innerHeight = 800;
  w.Element.prototype.getBoundingClientRect = function () { return this.tagName === 'IFRAME' ? { left: 0, top: 0, width: 400, height: 800, right: 400, bottom: 800 } : { left: 0, top: 0, width: 10, height: 10, right: 10, bottom: 10 }; };
  w.eval(js);
  const f = w.document.createElement('iframe'); f.src = 'http://adnet.test/f.html'; f.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;opacity:0';
  w.document.body.appendChild(f);
  return new Promise(r => setTimeout(r, 100)).then(() => {
    ok(w.document.querySelector('iframe') === null, 'cadre transparent plein écran tiers retiré');
    process.exit(fails ? 1 : 0);
  });
}
