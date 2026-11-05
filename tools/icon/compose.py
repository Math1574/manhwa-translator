import io, math, random
import numpy as np
import cairosvg
from PIL import Image, ImageDraw, ImageFilter, ImageChops
from PIL.ImageFilter import MinFilter
from creature import creature_svg
random.seed(7); np.random.seed(7)
S = 1728                      # 4 × 432 : on dessine en grand puis on réduit
def vgrad(c0, c1, stops=None):
    a = np.zeros((S, S, 3), np.float32)
    ys = np.linspace(0, 1, S)[:, None]
    if stops is None: stops = [(0, c0), (1, c1)]
    for (p0, col0), (p1, col1) in zip(stops[:-1], stops[1:]):
        m = (ys >= p0) & (ys <= p1); t = np.clip((ys - p0) / (p1 - p0), 0, 1)
        for k in range(3): a[..., k] = np.where(m, col0[k] + (col1[k] - col0[k]) * t, a[..., k])
    return Image.fromarray(a.astype(np.uint8)).convert("RGBA")
def radial(cx, cy, r, color, alpha, power=1.6):
    y, x = np.mgrid[0:S, 0:S].astype(np.float32)
    d = np.clip(np.hypot(x - cx * S, y - cy * S) / (r * S), 0, 1)
    a = ((1 - d) ** power * alpha * 255).astype(np.uint8)
    im = Image.new("RGBA", (S, S), color + (0,)); im.putalpha(Image.fromarray(a)); return im
def blob(layer, cx, cy, rx, ry, color, alpha, blur):
    m = Image.new("L", (S, S), 0); ImageDraw.Draw(m).ellipse((cx*S-rx*S, cy*S-ry*S, cx*S+rx*S, cy*S+ry*S), fill=int(alpha*255))
    m = m.filter(ImageFilter.GaussianBlur(blur*S)); c = Image.new("RGBA", (S, S), color + (0,)); c.putalpha(m); layer.alpha_composite(c)
def hill(points, color0, color1, blur=0.0):
    m = Image.new("L", (S, S), 0); ImageDraw.Draw(m).polygon([(x*S, y*S) for x, y in points], fill=255)
    if blur: m = m.filter(ImageFilter.GaussianBlur(blur*S))
    g = vgrad(color0, color1); g.putalpha(m); return g
def bez(p0, p1, p2, p3, n=40):
    return [((1-t)**3*p0[0]+3*(1-t)**2*t*p1[0]+3*(1-t)*t*t*p2[0]+t**3*p3[0], (1-t)**3*p0[1]+3*(1-t)**2*t*p1[1]+3*(1-t)*t*t*p2[1]+t**3*p3[1]) for t in np.linspace(0, 1, n)]

def background():
    bg = vgrad(None, None, [(0, (22, 14, 78)), (0.45, (84, 48, 160)), (0.72, (206, 112, 156)), (0.86, (255, 176, 124)), (1, (60, 40, 110))])
    bg.alpha_composite(radial(0.5, 0.42, 0.62, (255, 214, 150), 0.85, 1.7))      # grande lune chaude derrière le personnage
    bg.alpha_composite(radial(0.5, 0.42, 0.28, (255, 246, 214), 0.9, 1.4))
    # nuages doux
    for cx, cy, rx, ry, a in [(0.18, 0.30, 0.20, 0.05, 0.30), (0.80, 0.22, 0.22, 0.045, 0.26), (0.32, 0.66, 0.26, 0.05, 0.22), (0.86, 0.60, 0.20, 0.04, 0.22), (0.55, 0.14, 0.18, 0.035, 0.18)]:
        blob(bg, cx, cy, rx, ry, (255, 232, 244), a, 0.018)
    # étoiles
    d = ImageDraw.Draw(bg)
    for _ in range(46):
        x, y = random.random(), random.random() * 0.45; r = random.choice([1.2, 1.6, 2.2]) * S / 432
        d.ellipse((x*S-r, y*S-r, x*S+r, y*S+r), fill=(255, 245, 225, random.randint(110, 230)))
    # collines en deux plans
    far = bez((0, 0.80), (0.2, 0.70), (0.38, 0.78), (0.55, 0.74)) + bez((0.55, 0.74), (0.72, 0.70), (0.86, 0.76), (1, 0.72))
    bg.alpha_composite(hill(far + [(1, 1), (0, 1)], (92, 70, 150), (46, 34, 104), blur=0.002))
    near = bez((0, 0.88), (0.22, 0.80), (0.42, 0.90), (0.60, 0.86)) + bez((0.60, 0.86), (0.78, 0.82), (0.9, 0.88), (1, 0.84))
    bg.alpha_composite(hill(near + [(1, 1), (0, 1)], (52, 40, 118), (22, 16, 66), blur=0.002))
    # lucioles
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for x, y, r in [(0.14, 0.52, 1.0), (0.84, 0.46, 1.2), (0.24, 0.80, 0.9), (0.78, 0.78, 1.1), (0.10, 0.70, 0.8), (0.92, 0.66, 0.8), (0.66, 0.30, 0.9), (0.30, 0.22, 0.9)]:
        glow.alpha_composite(radial(x, y, 0.035 * r, (255, 226, 130), 0.95, 1.2))
        ImageDraw.Draw(glow).ellipse((x*S-3.2*r*S/432, y*S-3.2*r*S/432, x*S+3.2*r*S/432, y*S+3.2*r*S/432), fill=(255, 250, 220, 255))
    bg.alpha_composite(glow)
    # grain de papier
    n = (np.random.randn(S, S) * 7).astype(np.float32)
    a = np.asarray(bg).astype(np.float32); a[..., :3] = np.clip(a[..., :3] + n[..., None], 0, 255)
    return Image.fromarray(a.astype(np.uint8))

def foreground(scale=0.62, dy=0.02, backlight=True, ground=True):
    png = cairosvg.svg2png(bytestring=creature_svg(int(S*scale), with_shadow=False).encode())
    c = Image.open(io.BytesIO(png)).convert("RGBA")
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0)); layer.alpha_composite(c, (int((S-c.width)/2), int((S-c.height)/2 + dy*S)))
    a = layer.split()[3]
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if backlight:
        # contre-jour chaud : halo diffus autour de la silhouette
        halo = a.filter(ImageFilter.MaxFilter(31)).filter(ImageFilter.GaussianBlur(S * 0.022))
        hc = Image.new("RGBA", (S, S), (255, 205, 140, 0)); hc.putalpha(halo.point(lambda v: int(v * 0.55))); out.alpha_composite(hc)
    # contour « encre » légère, de la couleur sombre du pelage
    edge = a.filter(ImageFilter.MaxFilter(7)); ring = ImageChops.subtract(edge, a).filter(ImageFilter.GaussianBlur(1.2))
    ink = Image.new("RGBA", (S, S), (28, 18, 92, 0)); ink.putalpha(ring.point(lambda v: min(255, int(v * 0.85)))); out.alpha_composite(ink)
    out.alpha_composite(layer)
    if backlight:
        # lumière de bord : crème chaude sur les contours du haut et de la gauche (source derrière-gauche)
        inner = ImageChops.subtract(a, a.filter(MinFilter(13)))
        yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
        dirm = np.clip(1.15 - (yy / S) * 1.5 - (xx / S) * 0.25, 0, 1)
        rim = Image.fromarray((np.asarray(inner).astype(np.float32) * dirm).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))
        rc = Image.new("RGBA", (S, S), (255, 238, 204, 0)); rc.putalpha(rim.point(lambda v: min(255, int(v * 0.95)))); out.alpha_composite(rc)
    if ground:
        sh = Image.new("RGBA", (S, S), (0, 0, 0, 0)); ImageDraw.Draw(sh).ellipse((S*0.30, S*0.835, S*0.70, S*0.875), fill=(8, 4, 30, 140))
        sh = sh.filter(ImageFilter.GaussianBlur(S*0.012)); res = Image.new("RGBA", (S, S), (0, 0, 0, 0)); res.alpha_composite(sh); res.alpha_composite(out); out = res
    return out.filter(ImageFilter.GaussianBlur(0.9))

if __name__ == "__main__":
    bg = background(); fg = foreground()
    full = bg.copy(); full.alpha_composite(fg)
    def mask(im, kind):
        s = im.size[0]; m = Image.new("L", (s, s), 0); d = ImageDraw.Draw(m)
        if kind == "circle": d.ellipse((0, 0, s-1, s-1), fill=255)
        elif kind == "small": d.ellipse((int(s*0.17), int(s*0.17), int(s*0.83), int(s*0.83)), fill=255)
        else: d.rounded_rectangle((0, 0, s-1, s-1), radius=int(s*0.30), fill=255)
        o = Image.new("RGBA", im.size, (0, 0, 0, 0)); o.paste(im, (0, 0), m); return o
    f = full.resize((432, 432), Image.LANCZOS)
    sheet = Image.new("RGBA", (432*3+40, 452), (20, 16, 34, 255))
    sheet.alpha_composite(mask(f, "sq"), (10, 10)); sheet.alpha_composite(mask(f, "circle"), (452, 10)); sheet.alpha_composite(mask(f, "small"), (894, 10))
    sheet.convert("RGB").save("/tmp/icon/icon_preview3.png"); full.resize((864, 864), Image.LANCZOS).convert("RGB").save("/tmp/icon/full864.png"); print("ok")
