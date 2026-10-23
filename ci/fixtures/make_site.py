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

for lang in ("en", "fr", "ja", "kr", "cn"):
    imgs = sorted(os.path.basename(p) for p in glob.glob(f"{S}/pc/{lang}/E01P*.jpg"))
    if imgs:
        page(f"pc_{lang}", f"Pepper&Carrot ép.1 ({lang})", [f"pc/{lang}/{i}" for i in imgs])
imgs = sorted(os.path.basename(p) for p in glob.glob(f"{S}/pc/en/E01P*.jpg"))
if imgs:
    page("pc_en_lazy", "Pepper&Carrot ép.1 (en, chargement progressif)", [f"pc/en/{i}" for i in imgs], lazy=True)
    page("pc_en_ref", "Pepper&Carrot ép.1 (en, Referer + cookie requis)", [f"ref/en/{i}" for i in imgs])
    page("pc_en_ep6", "Pepper&Carrot ép.6 (en)", [f"pc/en/{os.path.basename(p)}" for p in sorted(glob.glob(f"{S}/pc/en/E06P*.jpg"))])
for n in ("en_caps", "en_mixed", "ko", "zh", "ja_vertical"):
    if os.path.exists(f"{S}/synth/{n}.png"): page(f"synth_{n}", f"Synthétique {n}", [f"synth/{n}.png"])
open(f"{S}/index.html", "w").write(HEAD % "Tests" + "".join(f'<p><a style="color:#8af" href="{os.path.basename(p)}">{os.path.basename(p)}</a></p>' for p in sorted(glob.glob(f"{S}/*.html")) if not p.endswith("index.html")) + "</body></html>")
print(sorted(os.listdir(S)))
