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
    # long chapitre : 36 images (les pages du faux site, répétées avec un paramètre distinct) pour détecter un arrêt ou un ralentissement sur la durée
    pool = [f"pc/en/{i}" for i in imgs] + [f"pc/en/{os.path.basename(p)}" for p in sorted(glob.glob(f"{S}/pc/en/E06P*.jpg"))]
    page("pc_en_long", "Long chapitre (36 images, en)", [f"{pool[k % len(pool)]}?k={k}" for k in range(36)])
    page("pc_en_toggle", "Bascule Original/Traduit (14 images, en)", [f"{pool[k % len(pool)]}?k={k}" for k in range(14)])
# textes coupés entre deux images collées (deux paires, séparées par un espace) : la traduction doit lire chaque bulle en entier et la poser sur les deux images
if os.path.exists(f"{S}/synth/seam_a1.png"):
    im = lambda n: f'<img src="synth/seam_{n}.png" style="display:block;width:100%;margin:0">'
    open(f"{S}/seam_cut.html", "w").write(HEAD % "Textes coupés" + "<h3 style='padding:8px'>Textes coupés entre deux images</h3>"
        + im("a1") + im("a2") + '<div style="height:80px"></div>' + im("b1") + im("b2") + "</body></html>")
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
    # chapitre de deux pages suivies du « bas de page » d'un site (séries proposées : vignettes plus étroites que les pages) : seules les deux pages sont à traduire, jamais ce qui est dessous
    open(f"{d}/4.html", "w").write(HEAD % "Test Series - Chapter 4" + "<h3 style='padding:8px'>Test Series - Chapter 4</h3>"
        + '<img src="/synth/en_mixed.png" style="display:block;width:100%;margin:0">' * 2
        + '<div style="height:60px;padding:16px">Chapitre suivant</div><h3 style="padding:8px">Séries populaires</h3>'
        + '<div style="display:flex;flex-wrap:wrap;gap:8px;padding:8px">' + '<img src="/synth/en_caps.png" style="width:170px;display:block">' * 4 + '</div><div style="height:300px"></div>'
        # commentaires : une image d'un autre dossier, presque aussi large que les pages (mise en page mobile) ; le filtre de la colonne l'écarte. Une image du même dossier et de la même largeur que les pages
        # arrive plus tard (__lateAdd, appelée par le test une fois en bas de page) : le filtre ne peut pas la distinguer d'une page, c'est l'arrêt de fin de chapitre qui la retient
        + '<h4 style="padding:8px">Commentaires</h4><img src="/comments/c1.png" style="display:block;width:80%;margin:12px auto"><div id="late"></div>'
        + '<script>window.__lateAdd=function(){var i=new Image();i.src="/synth/en_mixed.png";i.style.cssText="display:block;width:80%;margin:12px auto";document.getElementById("late").appendChild(i)}'
        # dernière page du chapitre chargée en retard (chargement différé) : elle s'empile juste sous la page 2, après l'arrêt de fin de chapitre ; elle doit être traduite toute seule
        + ';window.__stackAdd=function(){var p=document.querySelectorAll("img")[1],i=new Image();i.src="/synth/en_mixed.png";i.style.cssText="display:block;width:100%;margin:0";p.parentNode.insertBefore(i,p.nextSibling)}</script></body></html>')
    os.makedirs(f"{S}/comments", exist_ok=True)
    import shutil as _sh
    if os.path.exists(f"{S}/synth/en_caps.png"): _sh.copy(f"{S}/synth/en_caps.png", f"{S}/comments/c1.png")
    # chapitre trop court pour défiler (page d'attente, chapitre à venir) : rien n'y est lu, il ne doit jamais être marqué comme lu
    os.makedirs(f"{S}/comics/short-series-9z9z9z/chapter", exist_ok=True)
    open(f"{S}/comics/short-series-9z9z9z/chapter/1.html", "w").write(HEAD % "Short Series - Chapter 1" + "<h3 style='padding:8px'>Chapitre à venir</h3></body></html>")
    # page de l'œuvre : annonce son illustration (og:image, adresse relative) ; une vignette d'une autre œuvre la précède pour vérifier qu'on ne la confond pas avec l'illustration
    import struct, zlib
    def png(w, h, px):
        raw = b"".join(b"\x00" + b"".join(bytes(px(x, y)) for x in range(w)) for y in range(h))
        def chunk(t, d): c = struct.pack(">I", len(d)) + t + d; return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
        return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    sd = f"{S}/comics/test-series-1a2b3c"
    open(f"{sd}/cover.png", "wb").write(png(240, 360, lambda x, y: (40 + y * 160 // 360, 90 + x * 100 // 240, 200 - y * 120 // 360) if 24 <= x < 216 and 24 <= y < 336 else (230, 200, 60)))
    open(f"{sd}/other.png", "wb").write(png(120, 180, lambda x, y: (200, 60, 60)))
    open(f"{sd}/index.html", "w").write('<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Test Series - Read Free Online</title>'
        '<meta property="og:image" content="/comics/test-series-1a2b3c/cover.png">'
        '<meta property="og:description" content="Read Test Series manhwa online for free at Test Scans. Updated daily."></head><body style="margin:0;background:#1c1c1c;color:#ddd;font-family:sans-serif">'
        "<h3 style='padding:8px'>Test Series</h3>"
        # synopsis : la balise de partage est une phrase du site (refusée) ; le vrai résumé est sous le titre « Synopsis »
        "<h4 style='padding:0 8px'>Synopsis</h4><p style='padding:0 8px'>An ordinary office clerk finds a hidden door in the basement of her building. Behind it lies a library where every book tells the story of someone who disappeared.</p>"
        '<img src="/comics/test-series-1a2b3c/other.png" alt="Another Story" class="thumb" style="width:80px">'
        '<img src="/comics/test-series-1a2b3c/cover.png" alt="Test Series" style="width:160px"></body></html>')
    # autres œuvres, pour les illustrations (page de chapitre : pour qu'elles entrent dans la bibliothèque)
    def work(slug, title):
        os.makedirs(f"{S}/comics/{slug}/chapter", exist_ok=True)
        open(f"{S}/comics/{slug}/chapter/1.html", "w").write(HEAD % f"{title} - Chapter 1" + f"<h3 style='padding:8px'>{title} - Chapter 1</h3></body></html>")
    # image de partage « du site » (la même pour toutes les pages), rangée hors des dossiers d'œuvres comme sur un vrai site : une image rangée sous le nom de l'œuvre est, elle, celle de l'œuvre
    os.makedirs(f"{S}/site-og", exist_ok=True)
    open(f"{S}/site-og/og-image.png", "wb").write(png(600, 315, lambda x, y: (60, 60, 180)))
    # (a) page construite par son propre script : l'image de partage est celle du site (« og-image »), l'illustration au titre de l'œuvre n'apparaît que 1,8 s après le chargement
    work("spa-series-7a7a7a", "Spa Series")
    sp = f"{S}/comics/spa-series-7a7a7a"
    open(f"{sp}/cover.png", "wb").write(png(240, 360, lambda x, y: (200 - x * 100 // 240, 60 + y * 120 // 360, 90)))
    open(f"{sp}/index.html", "w").write('<!doctype html><html><head><meta charset="utf-8"><title>Spa Series</title><meta property="og:image" content="/site-og/og-image.png"></head>'
        '<body><h3>Spa Series</h3><script>setTimeout(function(){var i=new Image();i.alt="Spa Series";i.src="/comics/spa-series-7a7a7a/cover.png";i.width=160;document.body.appendChild(i)},1800)</script></body></html>')
    # (b) le site n'a aucune image pour l'œuvre : seul l'annuaire (faux serveur, /anilist) en a une
    work("anilist-only-5e5e5e", "Anilist Only")
    open(f"{S}/comics/anilist-only-5e5e5e/cover.png", "wb").write(png(240, 360, lambda x, y: (60, 160 + y * 60 // 360, 90 + x * 100 // 240)))
    # (c) la page de l'œuvre n'annonce que l'image du site : aucune illustration (la pastille reste)
    work("generic-only-8c8c8c", "Generic Only")
    gd = f"{S}/comics/generic-only-8c8c8c"
    open(f"{gd}/index.html", "w").write('<!doctype html><html><head><meta charset="utf-8"><title>Generic Only</title><meta property="og:image" content="/site-og/og-image.png"><meta property="og:description" content="Read Generic Only manhwa online for free at Test Scans. Updated daily."></head><body><h3>Generic Only</h3></body></html>')
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
