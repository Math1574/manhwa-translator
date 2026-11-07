import sys, random; sys.path.insert(0, '/tmp/lofi')
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from cat import layer, BODY, PAGES, PAWS
from room import room_layer
from light import relight, radial_rgba
S = 1728
def cat_layers(scale, cx, cy):
    size = int(S * scale); off = (int(S * cx - size / 2), int(S * cy - size / 2)); outs = []
    for inner in (BODY, PAGES, PAWS):
        L = Image.new("RGBA", (S, S), (0, 0, 0, 0)); L.alpha_composite(layer(inner, size), off); outs.append(L)
    return outs
def book_pos(scale, cx, cy):
    return (cx + ((50 + 8) / 116 - 0.5) * scale, cy + ((66 + 8) / 116 - 0.5) * scale)
def build(scale=0.70, cx=0.5, cy=0.545, with_room=True, glow=True, particles=True, ground_shadow=True, seed=11):
    random.seed(seed); np.random.seed(seed)
    bx, by = book_pos(scale, cx, cy)
    body, pages, paws = cat_layers(scale, cx, cy)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if with_room:
        room = room_layer(S)
        room = relight(room, bx, by, 0.75, ambient=(0.58, 0.55, 0.88), warm=(1.22, 0.92, 0.62), up=1.0, down=0.8, gain=0.95, power=1.25)
        room.alpha_composite(radial_rgba(S, 0.18, 0.28, 0.30, (120, 140, 255), 0.22, power=1.6))
        img.alpha_composite(room)
    if ground_shadow:
        sh = Image.new("RGBA", (S, S), (0, 0, 0, 0)); ImageDraw.Draw(sh).ellipse((S*0.26, S*0.835, S*0.74, S*0.895), fill=(10, 4, 30, 150))
        img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(S * 0.014)))
    amb = (0.52, 0.49, 0.86)
    img.alpha_composite(relight(body, bx, by, 0.62, ambient=amb)); img.alpha_composite(pages); img.alpha_composite(relight(paws, bx, by, 0.62, ambient=amb))
    if glow:
        img.alpha_composite(radial_rgba(S, bx, by - 0.03, 0.46, (255, 206, 130), 0.55, power=1.7))
        img.alpha_composite(radial_rgba(S, bx, by - 0.005, 0.22, (255, 243, 205), 0.75, power=1.3))
    if particles:
        P = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(P)
        for k in range(15):
            ang = random.uniform(-2.5, -0.65); r = random.uniform(0.12, 0.40)
            x = bx + np.cos(ang) * r * 0.9; y = by - 0.02 + np.sin(ang) * r
            rad = random.choice([2.2, 3.0, 4.2]) * S / 432
            if abs(x - cx) < 0.215 and 0.19 < y < 0.53: continue
            d.ellipse((x*S-rad*2.4, y*S-rad*2.4, x*S+rad*2.4, y*S+rad*2.4), fill=(255, 226, 150, 60))
            d.ellipse((x*S-rad, y*S-rad, x*S+rad, y*S+rad), fill=(255, 248, 214, 235))
        img.alpha_composite(P.filter(ImageFilter.GaussianBlur(0.8)))
    return img
def finish(img, grain=6.0, vignette=0.40):
    a = np.asarray(img).astype(np.float32); h, w = a.shape[:2]; yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.hypot((xx / w - 0.5) / 0.72, (yy / h - 0.52) / 0.76)
    a[..., :3] *= (1 - np.clip(d - 0.55, 0, 1) * vignette * 1.6)[..., None]
    a[..., :3] += np.random.randn(h, w, 1) * grain
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
def mask(im, kind):
    s = im.size[0]; m = Image.new("L", (s, s), 0); dd = ImageDraw.Draw(m)
    if kind == "circle": dd.ellipse((0, 0, s-1, s-1), fill=255)
    elif kind == "small": dd.ellipse((int(s*0.17), int(s*0.17), int(s*0.83), int(s*0.83)), fill=255)
    else: dd.rounded_rectangle((0, 0, s-1, s-1), radius=int(s*0.30), fill=255)
    o = Image.new("RGBA", im.size, (0, 0, 0, 0)); o.paste(im, (0, 0), m); return o
if __name__ == "__main__":
    full = finish(build()); f = full.resize((432, 432), Image.LANCZOS)
    sheet = Image.new("RGBA", (432*3+40, 452), (20, 16, 34, 255))
    sheet.alpha_composite(mask(f, "sq"), (10, 10)); sheet.alpha_composite(mask(f, "circle"), (452, 10)); sheet.alpha_composite(mask(f, "small"), (894, 10))
    sheet.convert("RGB").save("/tmp/lofi/icon_cat_preview.png"); full.resize((864, 864), Image.LANCZOS).convert("RGB").save("/tmp/lofi/icon_cat_full.png"); print("ok")
