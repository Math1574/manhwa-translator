"""Troisième essai : LaMa « manga » avec la version d'ONNX Runtime de l'application (1.20.0), par tuiles de 512 px au plus (mémoire bornée).

Vérifie : chargement et opérateurs (opset, DFT), temps par tuile et par région, mémoire maximale, licence (README des dépôts).
"""
import os, sys, glob, time, resource, collections
import numpy as np
from PIL import Image

PAIRS, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
log = []


def say(*a):
    s = " ".join(str(x) for x in a)
    print(s, flush=True)
    log.append(s)


def rss():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024.0


import onnxruntime as ort
import onnx
from huggingface_hub import hf_hub_download

say(f"onnxruntime {ort.__version__}, onnx {onnx.__version__}")
for repo in ["ogkalu/lama-manga-onnx-dynamic", "dreMaz/AnimeMangaInpainting", "ogkalu/aot-inpainting"]:
    try:
        p = hf_hub_download(repo, "README.md")
        say(f"--- README {repo} ---\n" + open(p, encoding="utf-8", errors="replace").read()[:1500])
    except Exception as e:
        say(f"README {repo} : {e}")

path = hf_hub_download("ogkalu/lama-manga-onnx-dynamic", "lama-manga-dynamic.onnx")
import hashlib
h = hashlib.sha256()
with open(path, "rb") as fh:
    for chunk in iter(lambda: fh.read(1 << 20), b""):
        h.update(chunk)
say(f"fichier {os.path.getsize(path)} octets, sha256 {h.hexdigest()}")
m = onnx.load(path, load_external_data=False)
say("opset : " + ", ".join(f"{o.domain or 'ai.onnx'}={o.version}" for o in m.opset_import) + f" ; ir {m.ir_version}")
ops = collections.Counter(n.op_type for n in m.graph.node)
say("opérateurs : " + ", ".join(f"{k}×{v}" for k, v in sorted(ops.items())))
del m

say(f"mémoire avant session : {rss():.0f} Mo")
sessions = {}
for th in (4, 2):
    so = ort.SessionOptions()
    so.intra_op_num_threads = th
    t0 = time.time()
    sessions[th] = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    say(f"session {th} fils : {time.time() - t0:.1f} s, mémoire max {rss():.0f} Mo")
s = sessions[4]
ins = [i.name for i in s.get_inputs()]


def infer(sess, im, hole):
    a = (im / 255.0).transpose(2, 0, 1)[None].astype(np.float32)
    b = hole[None, None].astype(np.float32)
    out = sess.run(None, {ins[0]: a, ins[1]: b})[0][0].transpose(1, 2, 0)
    return np.clip(out * 255.0, 0, 255)


for th, sess in sessions.items():
    for side in (256, 384, 512):
        im = np.random.RandomState(0).randint(0, 255, (side, side, 3)).astype(np.float32)
        hole = np.zeros((side, side), np.float32); hole[side // 4: 3 * side // 4, side // 4: 3 * side // 4] = 1
        infer(sess, im, hole)
        t0 = time.time(); infer(sess, im, hole); dt = time.time() - t0
        say(f"tuile {side}x{side}, {th} fils : {dt * 1000:.0f} ms, mémoire max {rss():.0f} Mo")

TILE, OV = 512, 128


def run_tiled(img, keep):
    H, W = keep.shape
    hole = (~keep).astype(np.float32)
    acc = np.zeros((H, W, 3), np.float64); wsum = np.zeros((H, W), np.float64)
    xs = [0] if W <= TILE else list(range(0, W - TILE, TILE - OV)) + [W - TILE]
    ys = [0] if H <= TILE else list(range(0, H - TILE, TILE - OV)) + [H - TILE]
    n = 0
    for y0 in ys:
        for x0 in xs:
            y1, x1 = min(H, y0 + TILE), min(W, x0 + TILE)
            hm = hole[y0:y1, x0:x1]
            if hm.sum() == 0:
                continue
            im = img[y0:y1, x0:x1].astype(np.float32)
            h, w = hm.shape
            ph, pw = (-h) % 8, (-w) % 8
            imp = np.pad(im, ((0, ph), (0, pw), (0, 0)), mode="reflect")
            hmp = np.pad(hm, ((0, ph), (0, pw)), mode="reflect")
            out = infer(s, imp, hmp)[:h, :w]
            n += 1
            # poids : cosinus surélevé sur la bande de recouvrement (1 au centre)
            wy = np.ones(h); wx = np.ones(w)
            for arr, a0, a1, full in ((wy, y0, y1, H), (wx, x0, x1, W)):
                L = len(arr)
                r = np.arange(L)
                if a0 > 0:
                    k = r < OV
                    arr[k] = np.minimum(arr[k], 0.5 - 0.5 * np.cos(np.pi * (r[k] + 0.5) / OV))
                if a1 < full:
                    k = r >= L - OV
                    arr[k] = np.minimum(arr[k], 0.5 - 0.5 * np.cos(np.pi * (L - r[k] - 0.5) / OV))
            wt = np.outer(wy, wx)
            acc[y0:y1, x0:x1] += out * wt[..., None]; wsum[y0:y1, x0:x1] += wt
    res = img.astype(np.float64).copy()
    ok = wsum > 0
    res[ok] = acc[ok] / wsum[ok][..., None]
    k = keep[..., None]
    return np.where(k, img, np.clip(res, 0, 255)).astype(np.uint8), n


pairs = sorted(glob.glob(os.path.join(PAIRS, "*_img.jpg")))
times = []
tiles = 0
for p in pairs:
    base = os.path.basename(p)[:-8]
    img = np.asarray(Image.open(p).convert("RGB"))
    keep = np.asarray(Image.open(os.path.join(PAIRS, base + "_mask.png")).convert("L")) > 127
    t0 = time.time()
    r, n = run_tiled(img, keep)
    dt = time.time() - t0
    tiles += n
    times.append((dt, img.shape[1], img.shape[0], n))
    Image.fromarray(r).save(os.path.join(OUT, f"{base}__lama-tuiles.jpg"), quality=92)
times.sort()
say(f"\n{len(pairs)} régions, {tiles} tuiles : médiane {times[len(times) // 2][0] * 1000:.0f} ms, max {times[-1][0] * 1000:.0f} ms ({times[-1][1]}x{times[-1][2]}, {times[-1][3]} tuiles), total {sum(t for t, *_ in times):.0f} s ; mémoire max {rss():.0f} Mo")
with open(os.path.join(OUT, "summary.md"), "w") as fh:
    fh.write("# LaMa manga avec ONNX Runtime 1.20.0, par tuiles\n\n```\n" + "\n".join(log) + "\n```\n")
say("fini")
