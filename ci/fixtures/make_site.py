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
    page("pc_en_ep6", "Pepper&Carrot ép.6 (en)", [f"pc/en/{os.path.basename(p)}" for p in sorted(glob.glob(f"{S}/pc/en/E06P*.jpg"))])
if os.path.exists(f"{S}/synth/atypical_0.png"): page("synth_atypical", "Textes atypiques", [f"synth/atypical_{i}.png" for i in range(6)])
if os.path.exists(f"{S}/synth/layouts_0.png"): page("synth_layouts", "Mises en page difficiles", [f"synth/layouts_{i}.png" for i in range(6)])
if os.path.exists(f"{S}/synth/en_variety.jpg"): page("synth_en_variety", "Synthétique en_variety (JPEG)", ["synth/en_variety.jpg"])
for n in ("en_caps", "en_mixed", "en_fancy", "ko", "zh", "ja_vertical"):
    if os.path.exists(f"{S}/synth/{n}.png"): page(f"synth_{n}", f"Synthétique {n}", [f"synth/{n}.png"])
open(f"{S}/index.html", "w").write(HEAD % "Tests" + "".join(f'<p><a style="color:#8af" href="{os.path.basename(p)}">{os.path.basename(p)}</a></p>' for p in sorted(glob.glob(f"{S}/*.html")) if not p.endswith("index.html")) + "</body></html>")
print(sorted(os.listdir(S)))
