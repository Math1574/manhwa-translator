import sys, io, os; sys.path.insert(0, '/tmp/lofi')
import numpy as np, cairosvg
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import compose_cat as C
from compose_cat import S, build, finish, cat_layers, book_pos, mask
from cat import _svg, layer, BODY, PAGES, PAWS
from room import room_layer
from light import relight, radial_rgba
OUT = "/tmp/lofi/out"; os.makedirs(OUT, exist_ok=True)
SCALE, CX, CY = 0.70, 0.5, 0.545
# fond : la chambre seule, éclairée par le livre
import random; random.seed(11); np.random.seed(11)
bx, by = book_pos(SCALE, CX, CY)
room = room_layer(S)
room = relight(room, bx, by, 0.75, ambient=(0.58, 0.55, 0.88), warm=(1.22, 0.92, 0.62), up=1.0, down=0.8, gain=0.95, power=1.25)
room.alpha_composite(radial_rgba(S, 0.18, 0.28, 0.30, (120, 140, 255), 0.22, power=1.6))
bg = finish(room).resize((432, 432), Image.LANCZOS).convert("RGB"); bg.save(f"{OUT}/ic_launcher_art_bg.png", optimize=True)
# premier plan : le chat, son livre, la lueur, les particules, l'ombre au sol
fg = build(SCALE, CX, CY, with_room=False, glow=True, particles=True, ground_shadow=True)
fg = fg.filter(ImageFilter.GaussianBlur(0.9)).resize((432, 432), Image.LANCZOS); fg.save(f"{OUT}/ic_launcher_art_fg.png", optimize=True)
# silhouette pour l'icône thématique : le chat, son casque et son livre ; yeux, museau, dos du livre et bandeau évidés
body, pages, paws = cat_layers(SCALE, CX, CY)
sil = Image.new("RGBA", (S, S), (0, 0, 0, 0)); [sil.alpha_composite(l) for l in (body, pages, paws)]
a = sil.split()[3]
size = int(S * SCALE); off = (int(S * CX - size / 2), int(S * CY - size / 2))
holes_inner = '''<rect x="-8" y="-8" width="116" height="116" fill="black"/>
<g fill="white"><ellipse cx="38.5" cy="40" rx="3.6" ry="3.8"/><ellipse cx="61.5" cy="40" rx="3.6" ry="3.8"/><path d="M47.4,43.4 C49,42.2 51,42.2 52.6,43.4 C51.6,46 48.4,46 47.4,43.4Z"/></g>
<g stroke="white" fill="none" stroke-linecap="round"><path d="M50,46 L50,48.2 M50,48.2 C48.2,50.2 45.6,49.8 45,48.6 M50,48.2 C51.8,50.2 54.4,49.8 55,48.6" stroke-width="1.2"/>
<path d="M50,69.4 L50,90.6" stroke-width="1.5"/><path d="M26,77 C35,73.6 44,73.6 50,76" stroke-width="1.1"/><path d="M74,77 C65,73.6 56,73.6 50,76" stroke-width="1.1"/>
<path d="M27,40 C26,17 74,17 73,40" stroke-width="1.5"/></g>'''
h = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=_svg(holes_inner, size).encode()))).convert("L")
hole = Image.new("L", (S, S), 0); hole.paste(h, off)
alpha = ImageChops.multiply(a, ImageChops.invert(hole))
mono = Image.new("RGBA", (S, S), (255, 255, 255, 0)); mono.putalpha(alpha)
mono.resize((432, 432), Image.LANCZOS).save(f"{OUT}/ic_launcher_art_mono.png", optimize=True)
# mascotte pour l'application : le chat et sa lueur, sans la chambre, rognée serré
m = build(0.80, 0.5, 0.5, with_room=False, glow=True, particles=True, ground_shadow=False)
bb = m.split()[3].filter(ImageFilter.MaxFilter(9)).getbbox(); m = m.crop(bb); side = max(m.size)
sq = Image.new("RGBA", (side + 40, side + 40), (0, 0, 0, 0)); sq.alpha_composite(m, ((sq.width - m.width) // 2, (sq.height - m.height) // 2))
sq.resize((512, 512), Image.LANCZOS).save(f"{OUT}/mascot_chat.png", optimize=True)
for f in sorted(os.listdir(OUT)): print(f, os.path.getsize(f"{OUT}/{f}") // 1024, "Ko")
full = bg.convert("RGBA"); full.alpha_composite(fg)
mono_prev = Image.new("RGBA", (432, 432), (60, 54, 90, 255)); mt = Image.new("RGBA", (432, 432), (232, 222, 255, 0)); mt.putalpha(Image.open(f"{OUT}/ic_launcher_art_mono.png").split()[3]); mono_prev.alpha_composite(mt)
mp = Image.open(f"{OUT}/mascot_chat.png").resize((432, 432)); bgm = Image.new("RGBA", (432, 432), (15, 10, 31, 255)); bgm.alpha_composite(mp)
sheet = Image.new("RGBA", (432*4+50, 452), (20, 16, 34, 255))
sheet.alpha_composite(mask(full, "circle"), (10, 10)); sheet.alpha_composite(mask(full, "small"), (452, 10)); sheet.alpha_composite(mask(mono_prev, "circle"), (894, 10)); sheet.alpha_composite(bgm, (1336, 10))
sheet.convert("RGB").save("/tmp/lofi/final_cat_preview.png")
