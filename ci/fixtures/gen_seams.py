#!/usr/bin/env python3
"""Images de test « texte coupé entre deux images » : deux paires d'images de 900×1500 qui, empilées, donnent une bulle que le découpage coupe en deux.
Paire A : la coupure passe au milieu d'une ligne. Paire B : elle passe entre deux lignes de la même bulle."""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/site/synth"
os.makedirs(OUT, exist_ok=True)
DEJAVU = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
W, H = 900, 1500
try: F = ImageFont.truetype(DEJAVU, 48)
except Exception: F = ImageFont.load_default()


def scene():
    img = Image.new("RGB", (W, 2 * H)); d = ImageDraw.Draw(img)
    for y in range(2 * H):       # fond en dégradé sans texte : les deux images voisines se raccordent
        t = y / (2 * H)
        d.line([(0, y), (W, y)], fill=(int(40 + 90 * t), int(60 + 40 * (1 - t)), int(110 + 60 * t)))
    return img, d


def bubble(d, box, lines, y_first):
    d.ellipse(box, fill="white", outline="black", width=5)
    y = y_first
    for ln in lines:
        w = d.textlength(ln, font=F)
        d.text(((box[0] + box[2]) / 2 - w / 2, y), ln, font=F, fill="black")
        y += 62


gt = []
# paire A : la coupure (y = H) passe au milieu de la deuxième ligne
img, d = scene()
lines = ["I WILL NEVER", "FORGIVE YOU FOR", "WHAT YOU DID", "TO HER."]
bubble(d, (120, H - 215, 780, H + 215), lines, H - 93)
img.crop((0, 0, W, H)).save(f"{OUT}/seam_a1.png"); img.crop((0, H, W, 2 * H)).save(f"{OUT}/seam_a2.png")
gt.append({"pair": "a", "full": " ".join(lines), "needles": ["NEVER", "FORGIVE", "DID", "HER"]})
# paire B : la coupure passe entre la deuxième et la troisième ligne
img, d = scene()
lines = ["YOU HAVE COME", "SO FAR ALREADY,", "BUT THE JOURNEY", "HAS JUST BEGUN."]
bubble(d, (100, H - 225, 800, H + 225), lines, H - 130)
img.crop((0, 0, W, H)).save(f"{OUT}/seam_b1.png"); img.crop((0, H, W, 2 * H)).save(f"{OUT}/seam_b2.png")
gt.append({"pair": "b", "full": " ".join(lines), "needles": ["COME", "JOURNEY", "BEGUN"]})
json.dump(gt, open(f"{OUT}/seam_gt.json", "w"), ensure_ascii=False)
print("images de jonction écrites dans", OUT)
