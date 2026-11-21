"""Icône de l'application à partir du logo fourni (tools/icon/logo_source.jpg, 1024×1024, fond noir).

Icône adaptative (108 dp = 432 px) : seule la zone centrale (66 dp = 264 px, un peu plus ici) est sûre, le reste peut être rogné par le masque du lanceur. Le logo est donc réduit
pour tenir dans cette zone ; le fond est noir uni, le premier plan est le logo avec une transparence tirée de sa luminosité (le noir devient transparent, les lueurs restent douces) :
posé sur le fond noir, il redonne exactement l'image d'origine. Icône thématique (Android 13+) : silhouette monochrome tirée de la même luminosité.

Usage : python3 make_logo.py <dossier de sortie>   (puis copier les trois PNG dans app/src/main/res/drawable-nodpi/)
"""
import os, sys
import numpy as np
from PIL import Image

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logo_source.jpg")
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
SIZE = 432
os.makedirs(OUT, exist_ok=True)

im = Image.open(SRC).convert("RGB")
a = np.asarray(im).astype(np.float32)
mx = a.max(axis=2)
ys, xs = np.where(mx > 25)
x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
content = max(x1 - x0, y1 - y0)
TARGET = 258.0                      # diamètre du dessin dans l'icône (la zone sûre est de 264 px : les éclats les plus fins du bord peuvent frôler le masque)
k = TARGET / content
side = int(round(1024 * k))
big = im.resize((side, side), Image.LANCZOS)
ox = int(round(SIZE / 2 - cx * k)); oy = int(round(SIZE / 2 - cy * k))

canvas = Image.new("RGB", (SIZE, SIZE), (0, 0, 0))
canvas.paste(big, (ox, oy))
c = np.asarray(canvas).astype(np.float32)

# fond : noir uni
Image.new("RGB", (SIZE, SIZE), (0, 0, 0)).save(os.path.join(OUT, "ic_launcher_art_bg.png"), optimize=True)

# premier plan : transparence = luminosité (max des canaux), couleur divisée par la transparence (sur noir, le résultat est l'image d'origine)
alpha = c.max(axis=2) / 255.0
alpha = np.clip(alpha * 1.04, 0, 1)
col = np.where(alpha[..., None] > 1e-3, c / np.maximum(alpha[..., None], 1e-3), 0)
col = np.clip(col, 0, 255)
fg = np.dstack([col, alpha * 255.0]).astype(np.uint8)
Image.fromarray(fg, "RGBA").save(os.path.join(OUT, "ic_launcher_art_fg.png"), optimize=True)

# icône thématique : silhouette blanche, opacité tirée de la luminosité (les parties sombres du tourbillon restent lisibles)
lum = (0.299 * c[..., 0] + 0.587 * c[..., 1] + 0.114 * c[..., 2]) / 255.0
m_alpha = np.clip(np.power(np.clip(lum * 2.2, 0, 1), 0.7), 0, 1)
mono = np.dstack([np.full((SIZE, SIZE), 255, np.uint8), np.full((SIZE, SIZE), 255, np.uint8), np.full((SIZE, SIZE), 255, np.uint8), (m_alpha * 255).astype(np.uint8)])
Image.fromarray(mono, "RGBA").save(os.path.join(OUT, "ic_launcher_art_mono.png"), optimize=True)

for f in sorted(os.listdir(OUT)):
    print(f, os.path.getsize(os.path.join(OUT, f)) // 1024, "Ko")

# aperçu (masques rond et arrondi) pour contrôle
def mask(img, kind):
    from PIL import ImageDraw
    s = img.size[0]; mm = Image.new("L", (s, s), 0); d = ImageDraw.Draw(mm)
    if kind == "circle": d.ellipse((int(s * 0.1667), int(s * 0.1667), int(s * 0.8333), int(s * 0.8333)), fill=255)
    else: d.rounded_rectangle((int(s * 0.1667), int(s * 0.1667), int(s * 0.8333), int(s * 0.8333)), radius=int(s * 0.17), fill=255)
    o = Image.new("RGBA", img.size, (30, 30, 30, 255)); o.paste(img, (0, 0), mm); return o
full = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255)); full.alpha_composite(Image.open(os.path.join(OUT, "ic_launcher_art_fg.png")))
sheet = Image.new("RGBA", (SIZE * 3 + 40, SIZE + 20), (30, 30, 30, 255))
sheet.alpha_composite(mask(full, "circle"), (10, 10)); sheet.alpha_composite(mask(full, "round"), (SIZE + 20, 10))
mp = Image.new("RGBA", (SIZE, SIZE), (60, 54, 90, 255)); tint = Image.new("RGBA", (SIZE, SIZE), (232, 222, 255, 0)); tint.putalpha(Image.open(os.path.join(OUT, "ic_launcher_art_mono.png")).split()[3]); mp.alpha_composite(tint)
sheet.alpha_composite(mask(mp, "circle"), (SIZE * 2 + 30, 10))
sheet.convert("RGB").save(os.path.join(OUT, "preview.png"))
