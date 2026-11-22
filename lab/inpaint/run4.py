"""Quatrième essai : LaMa « manga » (ONNX Runtime 1.20.0) avec la préparation de l'application (AiFill) : une seule passe par texte,
rectangle du trou élargi de 48 px, réduit à 640 px sur le grand côté au-delà, moyenne des pixels connus par case, trou dès qu'une case touche le trou,
complété au multiple de 8 par réflexion, relu en bilinéaire. Temps par région et planche « origine | actuel | LaMa (appli) ».
"""
import os, sys, glob, time, resource, math
import numpy as np
from PIL import Image

PAIRS, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
import onnxruntime as ort
from huggingface_hub import hf_hub_download

SIDE, CONTEXT = 640, 48
path = hf_hub_download("ogkalu/lama-manga-onnx-dynamic", "lama-manga-dynamic.onnx")
so = ort.SessionOptions()
so.intra_op_num_threads = 4
so.enable_cpu_mem_arena = False
so.enable_mem_pattern = False
s = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
ins = [i.name for i in s.get_inputs()]


def mirror(i, n):
    if n <= 1:
        return 0
    p = 2 * n - 2
    k = i % p
    return k if k < n else p - k


def fill(img, hole):
    h, w = hole.shape
    ys, xs = np.nonzero(hole)
    hx0, hx1, hy0, hy1 = xs.min(), xs.max(), ys.min(), ys.max()
    cx0, cy0 = max(0, hx0 - CONTEXT), max(0, hy0 - CONTEXT)
    cw, ch = min(w - 1, hx1 + CONTEXT) - cx0 + 1, min(h - 1, hy1 + CONTEXT) - cy0 + 1
    scale = min(1.0, SIDE / max(cw, ch))
    sw, sh = max(2, min(cw, math.ceil(cw * scale - 1e-9))), max(2, min(ch, math.ceil(ch * scale - 1e-9)))
    crop = img[cy0:cy0 + ch, cx0:cx0 + cw].astype(np.float64)
    hc = hole[cy0:cy0 + ch, cx0:cx0 + cw]
    small = np.zeros((sh, sw, 3)); sh_hole = np.zeros((sh, sw), bool)
    for y in range(sh):
        ya = y * ch // sh; yb = max(ya + 1, (y + 1) * ch // sh)
        for x in range(sw):
            xa = x * cw // sw; xb = max(xa + 1, (x + 1) * cw // sw)
            hb = hc[ya:yb, xa:xb]
            if hb.any():
                sh_hole[y, x] = True
            else:
                small[y, x] = np.floor(crop[ya:yb, xa:xb].reshape(-1, 3).sum(0) / hb.size)
    pw, ph = (sw + 7) // 8 * 8, (sh + 7) // 8 * 8
    iy = [mirror(y, sh) for y in range(ph)]; ix = [mirror(x, sw) for x in range(pw)]
    im = small[iy][:, ix] / 255.0
    mk = sh_hole[iy][:, ix].astype(np.float32)
    im[mk > 0] = 0
    out = s.run(None, {ins[0]: im.transpose(2, 0, 1)[None].astype(np.float32), ins[1]: mk[None, None]})[0][0].transpose(1, 2, 0)
    res = img.copy()
    fx, fy = sw / cw, sh / ch
    for y, x in zip(ys, xs):
        u = (x - cx0 + 0.5) * fx - 0.5; v = (y - cy0 + 0.5) * fy - 0.5
        x0, y0 = math.floor(u), math.floor(v); ax, ay = u - x0, v - y0
        def at(xx, yy):
            return out[min(max(yy, 0), sh - 1), min(max(xx, 0), sw - 1)]
        val = (at(x0, y0) * (1 - ax) + at(x0 + 1, y0) * ax) * (1 - ay) + (at(x0, y0 + 1) * (1 - ax) + at(x0 + 1, y0 + 1) * ax) * ay
        res[y, x] = np.clip(np.floor(val * 255 + 0.5), 0, 255)
    return res, (pw, ph)


pairs = sorted(glob.glob(os.path.join(PAIRS, "*_img.jpg")))
rows = []
for p in pairs:
    base = os.path.basename(p)[:-8]
    img = np.asarray(Image.open(p).convert("RGB"))
    keep = np.asarray(Image.open(os.path.join(PAIRS, base + "_mask.png")).convert("L")) > 127
    ours = np.asarray(Image.open(os.path.join(PAIRS, base + "_ours.jpg")).convert("RGB"))
    t0 = time.time()
    r, size = fill(img, ~keep)
    dt = time.time() - t0
    rows.append((dt, base, img.shape[1], img.shape[0], size))
    Image.fromarray(r).save(os.path.join(OUT, f"{base}__appli.jpg"), quality=92)
    gap = np.full((img.shape[0], 8, 3), 255, np.uint8)
    Image.fromarray(np.concatenate([img, gap, ours, gap, r], axis=1)).save(os.path.join(OUT, f"{base}__planche.jpg"), quality=88)
rows.sort()
lines = [f"{b} {w}x{h} -> {sz[0]}x{sz[1]} : {dt * 1000:.0f} ms" for dt, b, w, h, sz in rows]
summ = f"{len(rows)} régions : médiane {rows[len(rows) // 2][0] * 1000:.0f} ms, 90e centile {rows[int(len(rows) * 0.9)][0] * 1000:.0f} ms, max {rows[-1][0] * 1000:.0f} ms ; mémoire max {resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024:.0f} Mo"
print(summ)
with open(os.path.join(OUT, "summary.md"), "w") as fh:
    fh.write("# LaMa manga, préparation de l'application (une passe, 640 px au plus)\n\n" + summ + "\n\n```\n" + "\n".join(lines) + "\n```\n")
