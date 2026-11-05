import io, os
import numpy as np, cairosvg
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import compose
from compose import S, background, foreground
from creature import creature_svg

OUT = "/tmp/icon/out"; os.makedirs(OUT, exist_ok=True)
SCALE, DY = 0.67, 0.005
bg = background().resize((432, 432), Image.LANCZOS).convert("RGB")
bg.save(f"{OUT}/ic_launcher_art_bg.png", optimize=True)
fg = foreground(scale=SCALE, dy=DY, backlight=True, ground=True).resize((432, 432), Image.LANCZOS)
fg.save(f"{OUT}/ic_launcher_art_fg.png", optimize=True)

# silhouette pour l'icône thématique (Android 13+) : la forme du personnage, yeux, nez, sourire et croc évidés
sil = foreground(scale=SCALE, dy=DY, backlight=False, ground=False)
a = sil.split()[3]
holes_svg = creature_svg(int(S*SCALE), with_shadow=False)
import re
# on ne garde que les éléments de visage, tracés en blanc sur fond noir
face = '''<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="-8 -10 116 116"><rect x="-8" y="-10" width="116" height="116" fill="black"/>
<g fill="white"><ellipse cx="36" cy="50" rx="8" ry="8.6"/><ellipse cx="64" cy="50" rx="8" ry="8.6"/>
<path d="M47.7,58 C49,56.8 51,56.8 52.3,58 C51.6,60.2 48.4,60.2 47.7,58Z"/><path d="M44.8,67.2 L46.6,71.3 L48.6,68.4 Z"/></g>
<g stroke="white" fill="none" stroke-linecap="round"><path d="M40.5,64.2 C45,71 55,70.6 61.8,61.2" stroke-width="2.2"/><path d="M56,37 C62,33 70,33.5 74,37" stroke-width="2.2"/><path d="M27,39.5 C31,37.6 37,37.3 42,38.6" stroke-width="2.2"/></g></svg>''' % (int(S*SCALE), int(S*SCALE))
h = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=face.encode()))).convert("L")
hole = Image.new("L", (S, S), 0); hole.paste(h, (int((S-h.width)/2), int((S-h.height)/2 + DY*S)))
alpha = ImageChops.multiply(a, ImageChops.invert(hole))
mono = Image.new("RGBA", (S, S), (255, 255, 255, 0)); mono.putalpha(alpha)
mono.resize((432, 432), Image.LANCZOS).save(f"{OUT}/ic_launcher_art_mono.png", optimize=True)

# mascotte pour l'application : sans scène, contre-jour discret, rognée serré
m = foreground(scale=0.80, dy=0.0, backlight=True, ground=False)
bbox = m.split()[3].filter(ImageFilter.MaxFilter(9)).getbbox()
m = m.crop(bbox); side = max(m.size); sq = Image.new("RGBA", (side + 40, side + 40), (0, 0, 0, 0)); sq.alpha_composite(m, ((sq.width - m.width)//2, (sq.height - m.height)//2))
sq.resize((512, 512), Image.LANCZOS).save(f"{OUT}/mascot.png", optimize=True)

for f in sorted(os.listdir(OUT)): print(f, os.path.getsize(f"{OUT}/{f}")//1024, "Ko")
# aperçu : fond + premier plan aux masques
full = bg.convert("RGBA"); full.alpha_composite(fg)
def mask(im, kind):
    s = im.size[0]; mm = Image.new("L", (s, s), 0); d = ImageDraw.Draw(mm)
    if kind == "circle": d.ellipse((0, 0, s-1, s-1), fill=255)
    elif kind == "small": d.ellipse((int(s*0.17), int(s*0.17), int(s*0.83), int(s*0.83)), fill=255)
    else: d.rounded_rectangle((0, 0, s-1, s-1), radius=int(s*0.30), fill=255)
    o = Image.new("RGBA", im.size, (0, 0, 0, 0)); o.paste(im, (0, 0), mm); return o
mono_prev = Image.new("RGBA", (432, 432), (60, 54, 90, 255)); mt = Image.new("RGBA", (432, 432), (232, 222, 255, 0)); mt.putalpha(Image.open(f"{OUT}/ic_launcher_art_mono.png").split()[3]); mono_prev.alpha_composite(mt)
mp = Image.open(f"{OUT}/mascot.png").resize((432, 432))
sheet = Image.new("RGBA", (432*4+50, 452), (20, 16, 34, 255))
sheet.alpha_composite(mask(full, "circle"), (10, 10)); sheet.alpha_composite(mask(full, "small"), (452, 10)); sheet.alpha_composite(mask(mono_prev, "circle"), (894, 10))
bgm = Image.new("RGBA", (432, 432), (15, 10, 31, 255)); bgm.alpha_composite(mp); sheet.alpha_composite(bgm, (1336, 10))
sheet.convert("RGB").save("/tmp/icon/final_preview.png")
