#!/usr/bin/env python3
"""Pages de test synthétiques dont on connaît le texte exact : bulles, texte libre stylisé, onomatopées, texte vertical."""
import json, os, random, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/site/synth"
os.makedirs(OUT, exist_ok=True)
W, H = 800, 1400
COMIC = "/usr/share/fonts/truetype/comic-neue/ComicNeue-Bold.ttf"
DEJAVU = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
CJK = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
def F(path, size, idx=0):
    for p in (path, DEJAVU):
        try:
            return ImageFont.truetype(p, size, index=idx) if p.endswith(".ttc") else ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()

def background(seed):
    r = random.Random(seed)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    c1 = (r.randint(30, 120), r.randint(30, 120), r.randint(80, 200)); c2 = (r.randint(120, 230), r.randint(60, 160), r.randint(40, 120))
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(c1[i] * (1 - t) + c2[i] * t) for i in range(3)))
    for _ in range(14):
        x, y = r.randint(0, W), r.randint(0, H); s = r.randint(60, 260)
        d.polygon([(x, y), (x + s, y + r.randint(-40, 40)), (x + r.randint(0, s), y + s)], fill=(r.randint(0, 255), r.randint(0, 255), r.randint(0, 255)))
    return img.filter(ImageFilter.GaussianBlur(2))

def wrap(d, text, font, maxw):
    lines, cur = [], ""
    tokens = text.split(" ") if " " in text else list(text)
    sep = " " if " " in text else ""
    for t in tokens:
        cand = (cur + sep + t) if cur else t
        if d.textlength(cand, font=font) <= maxw: cur = cand
        else:
            if cur: lines.append(cur)
            cur = t
    if cur: lines.append(cur)
    return lines

def bubble(img, d, box, text, font, gt, kind="bubble", lang="en", dark=False):
    x0, y0, x1, y1 = box
    d.ellipse(box, fill=(20, 20, 30) if dark else "white", outline="black" if not dark else "white", width=4)
    inner = (x1 - x0) * 0.64
    lines = wrap(d, text, font, inner)
    lh = font.size * 1.2; total = lh * len(lines); y = (y0 + y1) / 2 - total / 2
    tb = [W, H, 0, 0]; lb = []
    for ln in lines:
        w = d.textlength(ln, font=font); x = (x0 + x1) / 2 - w / 2
        d.text((x, y), ln, font=font, fill="white" if dark else "black")
        bb = d.textbbox((x, y), ln, font=font); lb.append([int(bb[0]), int(bb[1]), int(bb[2]), int(bb[3])])
        tb = [min(tb[0], x), min(tb[1], y), max(tb[2], x + w), max(tb[3], y + lh)]
        y += lh
    gt.append({"kind": kind, "lang": lang, "text": text, "box": [int(v) for v in tb], "lines": lb})

def vertical_bubble(img, d, box, text, font, gt, lang="ja"):
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, radius=40, fill="white", outline="black", width=4)
    cols, cur = [], ""
    per = max(3, int((y1 - y0 - 40) / (font.size * 1.05)))
    for ch in text:
        cur += ch
        if len(cur) >= per: cols.append(cur); cur = ""
    if cur: cols.append(cur)
    cw = font.size * 1.3
    x = x1 - 20 - cw
    tb = [W, H, 0, 0]
    for col in cols:
        y = y0 + 20
        for ch in col:
            d.text((x, y), ch, font=font, fill="black"); y += font.size * 1.05
        tb = [min(tb[0], x), min(tb[1], y0 + 20), max(tb[2], x + font.size), max(tb[3], y)]
        x -= cw
    gt.append({"kind": "vertical", "lang": lang, "text": text, "box": [int(v) for v in tb]})

def free_text(img, text, font, xy, gt, lang="en", glow=True):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(layer)
    if glow:
        ld.text(xy, text, font=font, fill=(255, 40, 40, 255), stroke_width=7, stroke_fill=(255, 40, 40, 255))
        layer = layer.filter(ImageFilter.GaussianBlur(6)); ld = ImageDraw.Draw(layer)
    ld.text(xy, text, font=font, fill=(255, 255, 255, 255), stroke_width=3, stroke_fill=(90, 0, 0, 255))
    img.paste(layer, (0, 0), layer)
    d = ImageDraw.Draw(img); l, t, r, b = d.textbbox(xy, text, font=font)
    gt.append({"kind": "free", "lang": lang, "text": text, "box": [int(l), int(t), int(r), int(b)], "lines": [[int(l), int(t), int(r), int(b)]]})

PAGES = {
  "en_caps": ("en", True, COMIC, 0, [
      "PLEASE... TAKE ME AS YOUR DISCIPLE!", "I CAN'T BELIEVE YOU CAME ALL THE WAY HERE.", "WHO ARE YOU? WHAT DO YOU WANT FROM ME?",
      "THE MASTER WILL NOT SEE ANYONE TODAY.", "DO I HAVE NO CHOICE BUT TO KILL HIM?"], "NO CHOICE BUT TO KILL HIM?", "THUD!"),
  "en_mixed": ("en", False, DEJAVU, 0, [
      "Please... take me as your disciple!", "I can't believe you came all the way here.", "Who are you? What do you want from me?",
      "The master will not see anyone today.", "Do I have no choice but to kill him?"], "You will regret this.", "Boom!"),
  "ko": ("ko", False, CJK, 1, [
      "제발... 저를 제자로 받아주세요!", "여기까지 오시다니 믿을 수 없군요.", "당신은 누구십니까? 저에게 무엇을 원하시나요?",
      "스승님은 오늘 아무도 만나지 않으실 겁니다.", "그를 죽일 수밖에 없는 건가?"], "후회하게 될 거다.", "털썩"),
  "zh": ("zh", False, CJK, 2, [
      "求求你……收我为徒吧！", "没想到你竟然大老远跑到这里来。", "你是谁？你想从我这里得到什么？",
      "师父今天不见任何人。", "难道我别无选择，只能杀了他吗？"], "你会后悔的。", "咚！"),
}
JA_VERT = ["お願いします…弟子にしてください！", "ここまで来るとは信じられない。", "お前は誰だ？何が望みだ？", "師匠は今日は誰とも会わない。"]

for name, (lang, caps, fpath, idx, texts, free, sfx) in PAGES.items():
    img = background(hash(name) & 0xffff); d = ImageDraw.Draw(img); gt = []
    font = F(fpath, 30 if lang == "en" else 28, idx)
    pos = [(70, 60, 470, 250), (330, 300, 740, 500), (60, 560, 460, 760), (360, 800, 750, 990), (80, 1030, 440, 1200)]
    for box, t in zip(pos, texts): bubble(img, d, box, t, font, gt, lang=lang)
    ff = F(fpath, 46, idx); size = 46
    while ImageDraw.Draw(img).textlength(free, font=ff) > 700 and size > 20:
        size -= 2; ff = F(fpath, size, idx)
    free_text(img, free, ff, (50, 1250), gt, lang)
    free_text(img, sfx, F(fpath, 64, idx), (520, 600), gt, lang, glow=False)
    img.save(f"{OUT}/{name}.png"); json.dump(gt, open(f"{OUT}/{name}.json", "w"), ensure_ascii=False, indent=1)

img = background(7); d = ImageDraw.Draw(img); gt = []
jf = F(CJK, 34, 0)
for i, t in enumerate(JA_VERT):
    vertical_bubble(img, d, (560 - (i % 2) * 280, 60 + i * 300, 760 - (i % 2) * 280, 330 + i * 300), t, jf, gt)
img.save(f"{OUT}/ja_vertical.png"); json.dump(gt, open(f"{OUT}/ja_vertical.json", "w"), ensure_ascii=False, indent=1)
print(sorted(os.listdir(OUT)))


# ---------- page « lettrages difficiles » : titre tremblant à lueur, SFX penché, encadré de narration, texte murmuré hors bulle ----------
def wobbly(img, text, font_path, size, xy, gt, rot=10, glow=(255, 30, 30), fill=(255, 255, 255), stroke=(110, 0, 0), lang="en", kind="free"):
    r = random.Random(sum(map(ord, text)))
    x, y = xy
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
    box = [W, H, 0, 0]
    for ch in text:
        f = F(font_path, int(size * r.uniform(0.9, 1.15)))
        w = d.textlength(ch, font=f)
        tile = Image.new("RGBA", (int(w) + 40, int(size * 1.6)), (0, 0, 0, 0)); td = ImageDraw.Draw(tile)
        if glow: td.text((20, 10), ch, font=f, fill=glow + (255,), stroke_width=9, stroke_fill=glow + (255,))
        tile = tile.filter(ImageFilter.GaussianBlur(5)) if glow else tile; td = ImageDraw.Draw(tile)
        td.text((20, 10), ch, font=f, fill=fill + (255,), stroke_width=3, stroke_fill=stroke + (255,))
        tile = tile.rotate(r.uniform(-rot, rot), expand=True, resample=Image.BICUBIC)
        layer.paste(tile, (int(x - 20), int(y + r.randint(-6, 6) - 10)), tile)
        box = [min(box[0], x), min(box[1], y), max(box[2], x + w), max(box[3], y + size)]
        x += w * 0.95
    img.paste(layer, (0, 0), layer)
    gt.append({"kind": kind, "lang": lang, "text": text, "box": [int(v) for v in box], "lines": [[int(v) for v in box]]})

img = background(21); d = ImageDraw.Draw(img); gt = []
wobbly(img, "NO CHOICE BUT", COMIC, 66, (60, 90), gt)
wobbly(img, "TO KILL HIM?", COMIC, 66, (90, 190), gt)
# SFX penché très stylisé
sfx = Image.new("RGBA", (420, 200), (0, 0, 0, 0)); sd = ImageDraw.Draw(sfx)
sd.text((10, 10), "CRACK!", font=F("/usr/share/fonts/truetype/comic-neue/ComicNeue-Bold.ttf", 120), fill=(255, 240, 0, 255), stroke_width=6, stroke_fill=(0, 0, 0, 255))
sfx = sfx.rotate(18, expand=True, resample=Image.BICUBIC); img.paste(sfx, (400, 330), sfx)
gt.append({"kind": "sfx", "lang": "en", "text": "CRACK!", "box": [410, 340, 800, 520], "lines": [[410, 340, 800, 520]]})
# encadré de narration rectangulaire
d.rectangle((40, 520, 460, 640), fill=(250, 235, 160), outline="black", width=3)
nf = F(DEJAVU, 26)
for i, ln in enumerate(["THREE DAYS LATER,", "AT THE MOUNTAIN GATE."]): d.text((60, 545 + i * 36), ln, font=nf, fill="black")
gt.append({"kind": "narration", "lang": "en", "text": "THREE DAYS LATER, AT THE MOUNTAIN GATE.", "box": [60, 545, 400, 615], "lines": [[60, 545, 330, 575], [60, 581, 400, 611]]})
# bulle avec police manuscrite
hand = "/usr/share/fonts/truetype/comic-neue/ComicNeue-Bold.ttf"
bubble(img, d, (300, 690, 790, 940), "I HEARD MANY PEOPLE WITH SEVERED MERIDIANS WERE GENIUSES.", F(hand, 28), gt)
# texte murmuré hors bulle, petit, gris clair sur dessin
wf = F(DEJAVU, 30)
d.text((70, 980), "...he is still alive.", font=wf, fill=(235, 235, 235), stroke_width=2, stroke_fill=(30, 30, 30))
gt.append({"kind": "whisper", "lang": "en", "text": "...he is still alive.", "box": [70, 980, 330, 1015], "lines": [[70, 980, 330, 1015]]})
# titre en lettres blanches à contour épais, au bas
wobbly(img, "HEAVENLY DEMON", COMIC, 70, (40, 1120), gt, rot=6, glow=None, fill=(255, 255, 255), stroke=(20, 0, 60))
img.save(f"{OUT}/en_fancy.png"); json.dump(gt, open(f"{OUT}/en_fancy.json", "w"), ensure_ascii=False, indent=1)
