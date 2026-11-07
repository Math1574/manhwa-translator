#!/usr/bin/env python3
"""Génère les pages HTML du faux site de lecture dans /tmp/site."""
import glob, os, sys
S = sys.argv[1] if len(sys.argv) > 1 else "/tmp/site"
HEAD = '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title></head><body style="margin:0;background:#1c1c1c;color:#ddd;font-family:sans-serif">'
def page(name, title, imgs, lazy=False, prefix=""):
    body = ""
    for i in imgs:
        if lazy:
            body += f'<img data-src="{prefix}{i}" src="data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==" style="display:block;width:100%;min-height:900px;margin:0">'
        else:
            body += f'<img src="{prefix}{i}" style="display:block;width:100%;margin:0">'
    js = ""
    if lazy:
        js = """<script>var io=new IntersectionObserver(function(es){es.forEach(function(e){if(e.isIntersecting){var im=e.target;im.src=im.dataset.src;io.unobserve(im);im.style.minHeight=0;}})},{rootMargin:'200px'});document.querySelectorAll('img[data-src]').forEach(function(i){io.observe(i)});</script>"""
    open(f"{S}/{name}.html", "w").write(HEAD % title + f"<h3 style='padding:8px'>{title}</h3>" + body + js + "</body></html>")

# page piégée : régies publicitaires + redirection automatique vers un autre site (sans geste de l'utilisateur)
if os.path.exists(f"{S}/synth/en_caps.png"):
    open(f"{S}/adtest.html", "w").write(HEAD % "Pubs" + "<h3 style='padding:8px'>Test du blocage des publicités</h3>"
        + '<img src="https://pagead2.googlesyndication.com/pagead/test.png" width="1" height="1">'
        + '<script src="https://exoclick.com/ad.js"></script><iframe src="https://googleads.g.doubleclick.net/x" width="1" height="1"></iframe>'
        + '<img src="synth/en_caps.png" style="display:block;width:100%;margin:0">'
        + "<script>setTimeout(function(){location.href='http://evil-redirect.example.net/landing'},2500)</script></body></html>")
for lang in ("en", "fr", "ja", "kr", "cn"):
    imgs = sorted(os.path.basename(p) for p in glob.glob(f"{S}/pc/{lang}/E01P*.jpg"))
    if imgs:
        page(f"pc_{lang}", f"Pepper&Carrot ép.1 ({lang})", [f"pc/{lang}/{i}" for i in imgs])
imgs = sorted(os.path.basename(p) for p in glob.glob(f"{S}/pc/en/E01P*.jpg"))
if imgs:
    page("pc_en_lazy", "Pepper&Carrot ép.1 (en, chargement progressif)", [f"pc/en/{i}" for i in imgs], lazy=True)
    page("pc_en_ref", "Pepper&Carrot ép.1 (en, Referer + cookie requis)", [f"ref/en/{i}" for i in imgs])
    page("pc_en_strict", "Pepper&Carrot ép.1 (en, en-têtes de navigateur exigés)", [f"strict/en/{i}" for i in imgs[:4]])
    # adresses à usage unique : la page les charge avec un jeton aléatoire, le serveur refuse toute seconde requête (403)
    open(f"{S}/pc_en_once.html", "w").write(HEAD % "Pepper&Carrot ép.1 (en, adresses à usage unique)" + "<h3 style='padding:8px'>Adresses à usage unique</h3><script>"
        + "".join(f"document.write('<img src=\"/once/en/{i}?n='+Date.now()+'_'+Math.random()+'\" style=\"display:block;width:100%;margin:0\">');" for i in imgs[:4]) + "</script></body></html>")
    page("pc_en_ep6", "Pepper&Carrot ép.6 (en)", [f"pc/en/{os.path.basename(p)}" for p in sorted(glob.glob(f"{S}/pc/en/E06P*.jpg"))])
if os.path.exists(f"{S}/synth/atypical_0.png"): page("synth_atypical", "Textes atypiques", [f"synth/atypical_{i}.png" for i in range(6)])
if os.path.exists(f"{S}/synth/layouts_0.png"): page("synth_layouts", "Mises en page difficiles", [f"synth/layouts_{i}.png" for i in range(6)])
if os.path.exists(f"{S}/synth/en_variety.jpg"): page("synth_en_variety", "Synthétique en_variety (JPEG)", ["synth/en_variety.jpg"])
for n in ("en_caps", "en_mixed", "en_fancy", "ko", "zh", "ja_vertical"):
    if os.path.exists(f"{S}/synth/{n}.png"): page(f"synth_{n}", f"Synthétique {n}", [f"synth/{n}.png"])
# œuvre fictive (adresse de type /comics/<titre>-<identifiant>/chapter/<n>.html) : sert à vérifier la bibliothèque par œuvre, la reprise et le plein écran
if os.path.exists(f"{S}/synth/en_mixed.png"):
    d = f"{S}/comics/test-series-1a2b3c/chapter"
    os.makedirs(d, exist_ok=True)
    for n in (1, 2, 3):
        open(f"{d}/{n}.html", "w").write(HEAD % f"Test Series - Chapter {n}" + f"<h3 style='padding:8px'>Test Series - Chapter {n}</h3>"
            + '<img src="/synth/en_mixed.png" style="display:block;width:100%;margin:0">' * 2 + "</body></html>")
# banc du bloqueur : page piégée + faux sites tiers (noms en « .test », servis par l'application depuis /__sites/<nom>/ en mode test)
if os.path.exists(f"{S}/synth/en_caps.png"):
    import shutil
    def site_file(host, path, data=None, src=None):
        full = f"{S}/__sites/{host}/{path}"; os.makedirs(os.path.dirname(full), exist_ok=True)
        if src: shutil.copy(src, full)
        else: open(full, "w").write(data)
    img = f"{S}/synth/en_caps.png"
    site_file("adnet.test", "ad.js", "window.__adLoaded = true;")
    site_file("adnet.test", "frame.html", "<html><body><script>try{parent.__frameLoaded=true}catch(e){}</script>annonce</body></html>")
    site_file("adnet.test", "banner.gif", src=img)
    site_file("popunder.test", "landing", "<html><body>Pop-under</body></html>")
    site_file("cdn.test", "ads/ad.png", src=img)
    site_file("cdn.test", "bannieres-generiques/ok.png", src=img)
    site_file("cdn.test", "images/p1.png", src=img)
    site_file("cdn.test", "images/p2.png", src=img)
    site_file("friend.test", "page.html", "<html><head><title>Friend</title></head><body style='background:#123;color:#fff'><h2>Site ami</h2></body></html>")
    box = "position:fixed;left:20px;width:220px;height:56px;z-index:50;display:block;background:#446;color:#fff;font:20px sans-serif;line-height:56px;text-align:center;text-decoration:none"
    open(f"{S}/adtest2.html", "w").write(HEAD % "adtest2" + "<h3 style='padding:8px'>Test du bloqueur</h3>"
        + '<script src="http://adnet.test/ad.js"></script>'
        + '<iframe id="fr" src="http://adnet.test/frame.html" width="300" height="100"></iframe>'
        + ''.join(f'<img id="{i}" src="{u}" style="width:100%;display:block">' for i, u in (("banner", "http://adnet.test/banner.gif"), ("adimg", "http://cdn.test/ads/ad.png"),
            ("generic", "http://cdn.test/bannieres-generiques/ok.png"), ("cdn1", "http://cdn.test/images/p1.png"), ("cdn2", "http://cdn.test/images/p2.png"), ("same", "/synth/en_caps.png")))
        + '<a id="ov" href="http://popunder.test/overlay" style="position:fixed;left:0;top:0;width:100%;height:100%;opacity:0;z-index:99999"></a>'
        + f'<button id="btnPop" onclick="window.open(\'http://popunder.test/btn\')" style="position:fixed;left:20px;top:100px;width:220px;height:56px;z-index:50">Pop-up</button>'
        + f'<a id="next" target="_blank" href="/adtest2_ch2.html" style="top:180px;{box}">Suivant</a>'
        + f'<a id="ext" href="http://friend.test/page.html" style="top:260px;{box}">Autre site</a>'
        + f'<a id="adlink" href="http://popunder.test/clicked" style="top:340px;{box}">Lien regie</a>'
        + "<script>window.__popupFake=false;"
        + "setTimeout(function(){var w=window.open('http://popunder.test/p1');window.__popupFake=!!w&&w.closed===false},800);"
        + "setTimeout(function(){location.href='http://popunder.test/landing'},1500);"
        + "function real(id){var i=document.getElementById(id);return !!(i&&i.complete&&i.naturalWidth>20)}"
        + "setTimeout(function(){window.__state={adLoaded:!!window.__adLoaded,frameLoaded:!!window.__frameLoaded,bannerReal:real('banner'),adImgReal:real('adimg'),genericReal:real('generic'),cdn1Real:real('cdn1'),cdn2Real:real('cdn2'),sameReal:real('same'),popupFake:window.__popupFake}},4000);"
        + "</script></body></html>")
    open(f"{S}/adtest2_ch2.html", "w").write(HEAD % "adtest2 ch2" + "<h3 style='padding:8px'>Chapitre 2</h3></body></html>")
open(f"{S}/index.html", "w").write(HEAD % "Tests" + "".join(f'<p><a style="color:#8af" href="{os.path.basename(p)}">{os.path.basename(p)}</a></p>' for p in sorted(glob.glob(f"{S}/*.html")) if not p.endswith("index.html")) + "</body></html>")
print(sorted(os.listdir(S)))
