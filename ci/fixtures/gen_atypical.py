"""Pages de test « textes atypiques » (équivalents synthétiques de cas rencontrés en lecture réelle, sans image protégée) :
0. page blanche, texte libre dont la couleur est un dégradé (beige → noir) ;
1. page noire, texte blanc à lueur rouge ;
2. fond à trame de points et texte blanc contouré incliné, avec une bulle à police manuscrite ;
3. bulle à grand texte et petit texte libre incliné qui chevauche le contour de la bulle."""
import math, os, random, sys
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/site/synth"
os.makedirs(OUT, exist_ok=True)
W = 800
DJ = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"
DJB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
CN = "/usr/share/fonts/truetype/comic-neue/ComicNeue-Bold.ttf"


def F(path, size):
    for p in (path, DJB):
        try: return ImageFont.truetype(p, size)
        except OSError: continue
    return ImageFont.load_default()


def text_mask(size, lines, font, spacing, angle=0.0, center=None):
    m = Image.new("L", size, 0); d = ImageDraw.Draw(m)
    y = center[1] - spacing * len(lines) / 2 if center else 20
    for l in lines:
        w = d.textlength(l, font=font); d.text(((center[0] if center else size[0] / 2) - w / 2, y), l, font=font, fill=255); y += spacing
    return m.rotate(angle, resample=Image.BICUBIC, center=center) if angle else m


def page0():
    img = Image.new("RGB", (W, 520), "white")
    f = F(DJ, 48)
    m1 = text_mask(img.size, ["WHATEVER THE CASE IS,", "I MUST GATHER MORE SOULS."], f, 64, center=(W / 2, 110))
    img.paste(Image.new("RGB", img.size, (20, 20, 20)), mask=m1)
    m2 = text_mask(img.size, ["IF I KEEP DOING THAT,", "THEN ONE DAY..."], f, 64, center=(W / 2, 380))
    grad = Image.new("RGB", img.size); gp = grad.load()
    for x in range(W):
        t = x / W; c = tuple(int(a * (1 - t) + b * t) for a, b in zip((165, 140, 95), (28, 24, 20)))
        for y in range(520): gp[x, y] = c
    img.paste(grad, mask=m2)
    return img, [{"kind": "free", "text": "WHATEVER THE CASE IS, I MUST GATHER MORE SOULS.", "box": [90, 70, 710, 190]}, {"kind": "free", "text": "IF I KEEP DOING THAT, THEN ONE DAY...", "box": [140, 340, 660, 450]}]


def page1():
    img = Image.new("RGB", (W, 560), (0, 0, 0))
    f = F(DJ, 52)
    blocks = []
    for lines, cy in ((["IN THE WAKE OF THEIR", "DECEITFUL TEMPTATIONS."], 110), (["THE WORLD'S", "BALANCE WAS TEETERING", "PRECARIOUSLY."], 400)):
        m = text_mask(img.size, lines, f, 66, center=(W / 2, cy))
        glow = m.filter(ImageFilter.GaussianBlur(16)).point(lambda v: min(255, v * 2))
        img = Image.composite(Image.new("RGB", img.size, (235, 20, 35)), img, glow)
        img.paste(Image.new("RGB", img.size, (255, 255, 255)), mask=m)
        bb = m.getbbox(); blocks.append({"kind": "free", "text": " ".join(lines), "box": list(bb)})
    return img, blocks


def page2():
    img = Image.new("RGB", (W, 560)); px = img.load()
    for y in range(560):
        for x in range(W):
            t = y / 560; base = (int(60 + 120 * t), int(80 + 110 * t), int(130 + 90 * t))
            dot = ((x // 8 + y // 8) % 2 == 0) and ((x % 8) < 4 and (y % 8) < 4)
            px[x, y] = tuple(min(255, c + (28 if dot else 0)) for c in base)
    d = ImageDraw.Draw(img)
    d.ellipse((520, 250, 960, 700), fill=(52, 66, 62), outline=(12, 14, 12), width=4)          # « chapeau » sombre en bord de page
    # bulle à police manuscrite
    d.ellipse((330, 14, 790, 200), fill="white", outline="black", width=3)
    f = F(CN, 30)
    for i, l in enumerate(["YES, SIR! I'LL", "SHUT MY MOUTH BY", "PUTTING A BOULDER"]):
        w = d.textlength(l, font=f); d.text((560 - w / 2, 48 + i * 40), l, font=f, fill="black")
    # texte libre incliné, blanc contouré de noir
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(layer); f2 = F(CN, 34)
    for i, l in enumerate(["PLEASE JUST", "SHUT UP ALREADY, YOU", "CRAZY BASTARD..."]):
        w = ld.textlength(l, font=f2); ld.text((230 - w / 2, 300 + i * 44), l, font=f2, fill=(255, 255, 255, 255), stroke_width=3, stroke_fill=(0, 0, 0, 255))
    layer = layer.rotate(7, resample=Image.BICUBIC, center=(230, 360))
    img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    return img, [{"kind": "bubble", "text": "YES, SIR! I'LL SHUT MY MOUTH BY PUTTING A BOULDER", "box": [380, 40, 740, 170]}, {"kind": "free", "text": "PLEASE JUST SHUT UP ALREADY, YOU CRAZY BASTARD...", "box": [80, 290, 400, 460]}]


def page3():
    img = Image.new("RGB", (W, 560), "white"); d = ImageDraw.Draw(img)
    d.rectangle((0, 0, W, 90), fill=(70, 80, 70))
    d.ellipse((60, 40, 640, 420), fill="white", outline="black", width=3)
    f = F(DJB, 54)
    for i, l in enumerate(["SHUT UP,", "THAT SOUNDS", "GROSS."]):
        w = d.textlength(l, font=f); d.text((350 - w / 2, 120 + i * 78), l, font=f, fill="black")
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(layer); f2 = F(CN, 28)
    for i, l in enumerate(["WANT ME TO PUT", "A RESTRICTION ON YOUR", "MOUTH?"]):
        w = ld.textlength(l, font=f2); ld.text((560 - w / 2, 365 + i * 34), l, font=f2, fill=(0, 0, 0, 255))
    layer = layer.rotate(7, resample=Image.BICUBIC, center=(560, 410))
    img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    return img, [{"kind": "bubble", "text": "SHUT UP, THAT SOUNDS GROSS.", "box": [200, 120, 500, 340]}, {"kind": "free", "text": "WANT ME TO PUT A RESTRICTION ON YOUR MOUTH?", "box": [400, 350, 720, 480]}]


def page4():
    """Bulle ronde dont le contour est ouvert en bas à droite (il se fond dans la page) : la traduction, plus longue, doit rester dans l'ellipse."""
    random.seed(3)
    img = Image.new("RGB", (W, 640), "white"); d = ImageDraw.Draw(img)
    for y in range(0, 360):
        t = y / 360; d.line((0, y, W, y), fill=(int(120 + 60 * t), int(130 + 50 * t), int(120 + 40 * t)))
    for _ in range(60):
        x = random.randint(0, W); y = random.randint(0, 330); r = random.randint(8, 40); c = random.randint(40, 150); d.ellipse((x, y, x + r, y + r), fill=(c, c + 10, c))
    d.line((60, 360, W, 360), fill=(0, 0, 0), width=3)
    cx, cy, rx, ry = 430, 330, 285, 225
    d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill="white")
    for a10 in range(0, 3600):
        a = a10 / 10.0
        if 0 <= a <= 100: continue
        for t in (-1.5, 0, 1.5):
            x = cx + (rx + t) * math.cos(math.radians(a)); y = cy + (ry + t) * math.sin(math.radians(a)); d.ellipse((x - 1.2, y - 1.2, x + 1.2, y + 1.2), fill="black")
    f = F(CN, 44)
    for i, l in enumerate(["ALL I DID WAS", "WATCH OVER THE", "STORAGE. HAHA."]):
        w = d.textlength(l, font=f); d.text((cx - w / 2 + 20, cy - 5 + i * 62), l, font=f, fill="black")
    return img, [{"kind": "bubble", "text": "ALL I DID WAS WATCH OVER THE STORAGE. HAHA.", "box": [290, 325, 640, 500]}]


def page5():
    """Bulle dont la boîte contient le logo du site (filigrane) : la ligne de logo ne doit pas faire supprimer toute la bulle."""
    img = Image.new("RGB", (W, 520), (40, 46, 60)); d = ImageDraw.Draw(img)
    for y in range(0, 520):
        t = y / 520; d.line((0, y, W, y), fill=(int(40 + 70 * t), int(46 + 60 * t), int(60 + 40 * t)))
    d.rectangle((40, 10, W - 40, 510), outline=(0, 0, 0), width=4)
    cx, cy, rx, ry = 440, 290, 300, 190
    d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill="white", outline="black", width=4)
    # logo du site discret (texte directement sur le décor, sans bandeau) au coin de la boîte de la bulle, hors de l'ellipse : le détecteur ne lui donne pas de zone,
    # la ligne est « libre » et se rattachait à la bulle voisine (puis la bulle entière était supprimée comme filigrane)
    d.text((165, 120), "ASURASCANS.COM", font=F(CN, 20), fill=(90, 85, 130))
    f = F(CN, 46)
    for i, l in enumerate(["THAT DOESN'T", "SEEM TO BE THE", "CASE."]):
        w = d.textlength(l, font=f); d.text((cx - w / 2, 215 + i * 62), l, font=f, fill="black")
    return img, [{"kind": "bubble", "text": "THAT DOESN'T SEEM TO BE THE CASE.", "box": [250, 215, 630, 400]}]


import json
for i, fn in enumerate((page0, page1, page2, page3, page4, page5)):
    im, gt = fn(); im.save(f"{OUT}/atypical_{i}.png"); json.dump(gt, open(f"{OUT}/atypical_{i}.json", "w"))
print("pages atypiques générées : 6")
