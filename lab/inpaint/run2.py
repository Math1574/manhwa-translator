"""Deuxième essai de retouche par IA : modèles entraînés sur des mangas (AOT de manga-image-translator, LaMa « manga »), à la résolution d'origine.

Pour chaque paire : <nom>_img.jpg (région d'origine), <nom>_mask.png (255 = garder, 0 = trou), <nom>_ours.jpg (notre effacement actuel).
Sortie : <nom>__<modèle>.jpg, une planche « origine | actuel | modèles » et summary.md (fichiers des dépôts, entrées/sorties, temps).
"""
import os, sys, glob, time, traceback
import numpy as np
from PIL import Image

PAIRS, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
log = []


def say(*a):
    s = " ".join(str(x) for x in a)
    print(s, flush=True)
    log.append(s)


from huggingface_hub import HfApi, hf_hub_download
import onnxruntime as ort

try:
    import torch
    torch.set_num_threads(4)
except Exception:
    torch = None

api = HfApi()
REPOS = [
    "ogkalu/aot-inpainting",
    "ogkalu/aot-inpainting-jit",
    "ogkalu/lama-manga-onnx-dynamic",
    "mayocream/lama-manga-onnx",
    "dreMaz/AnimeMangaInpainting",
    "Carve/LaMa-ONNX",
]
cands = []
for repo in REPOS:
    try:
        info = api.model_info(repo, files_metadata=True)
        files = [(s.rfilename, s.size or 0) for s in info.siblings]
        say(f"- dépôt {repo} : " + ", ".join(f"{f} ({sz / 1e6:.1f} Mo)" for f, sz in files))
        for f, sz in files:
            lf = f.lower()
            if lf.endswith(".onnx") and not (repo == "Carve/LaMa-ONNX" and lf == "lama.onnx"):
                cands.append((repo, f, "onnx"))
            elif lf.endswith((".pt", ".jit", ".pth", ".ckpt")) and "aot" in repo.lower() and sz < 400e6:
                cands.append((repo, f, "torch"))
    except Exception as e:
        say(f"- dépôt {repo} : inaccessible ({e})")


def to_range(out):
    """Sortie du modèle -> 0..255 (devine l'échelle : [-1, 1], [0, 1] ou [0, 255])."""
    lo, hi = float(out.min()), float(out.max())
    if lo < -0.2:
        return (out + 1.0) * 127.5, "[-1,1]"
    if hi <= 1.5:
        return out * 255.0, "[0,1]"
    return out, "[0,255]"


class Model:
    def __init__(self, repo, f, fmt):
        self.repo, self.f, self.fmt = repo, f, fmt
        self.tag = (repo.split("/")[1][:14] + "-" + os.path.basename(f).rsplit(".", 1)[0])[:40]
        path = hf_hub_download(repo, f)
        self.size = os.path.getsize(path)
        self.aot = "aot" in repo.lower()
        self.range = None
        if fmt == "onnx":
            so = ort.SessionOptions()
            so.intra_op_num_threads = 4
            self.s = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
            self.ins = self.s.get_inputs()
            self.outs = self.s.get_outputs()
            sig = ", ".join(f"{i.name}{i.shape}:{i.type}" for i in self.ins) + " -> " + ", ".join(f"{o.name}{o.shape}:{o.type}" for o in self.outs)
            sh = self.ins[0].shape
            self.fixed = sh[2] if isinstance(sh[2], int) and isinstance(sh[3], int) else None
        else:
            if torch is None:
                raise RuntimeError("torch absent")
            self.m = torch.jit.load(path, map_location="cpu").eval()
            sig = "torch.jit"
            self.fixed = None
        say(f"- modèle {self.tag} ({repo}/{f}, {self.size / 1e6:.1f} Mo) : {sig} ; {'AOT' if self.aot else 'LaMa'} ; {'taille fixe ' + str(self.fixed) if self.fixed else 'taille libre'}")

    def infer(self, im, hole):
        """im : HxWx3 float 0..255 ; hole : HxW float (1 = trou). Rend HxWx3 float 0..255."""
        if self.aot:
            x = (im / 127.5 - 1.0) * (1.0 - hole[..., None])
        else:
            x = im / 255.0
        a = x.transpose(2, 0, 1)[None].astype(np.float32)
        b = hole[None, None].astype(np.float32)
        if self.fmt == "onnx":
            names = [i.name for i in self.ins]
            feed = {}
            for n in names:
                feed[n] = b if "mask" in n.lower() else a
            if len(feed) == 2 and all("mask" not in n.lower() for n in names):
                feed = {names[0]: a, names[1]: b}
            out = self.s.run(None, feed)[0][0].transpose(1, 2, 0)
        else:
            with torch.no_grad():
                out = self.m(torch.from_numpy(a), torch.from_numpy(b))[0].numpy().transpose(1, 2, 0)
        out, r = to_range(out.astype(np.float32))
        if self.range is None:
            self.range = r
            say(f"  {self.tag} : sortie lue comme {r}")
        return out

    def run(self, img, keep):
        """img : HxWx3 uint8 ; keep : HxW bool (vrai = garder). Rend HxWx3 uint8 (trou rempli, reste inchangé)."""
        H, W = keep.shape
        hole = (~keep).astype(np.float32)
        if self.fixed:
            # carré par répétition des bords, puis taille du modèle
            side = max(H, W)
            oy, ox = (side - H) // 2, (side - W) // 2
            sq = np.pad(img, ((oy, side - H - oy), (ox, side - W - ox), (0, 0)), mode="edge")
            hq = np.pad(hole, ((oy, side - H - oy), (ox, side - W - ox)), mode="constant")
            t = self.fixed
            im = np.asarray(Image.fromarray(sq).resize((t, t), Image.BICUBIC)).astype(np.float32)
            hm = (np.asarray(Image.fromarray((hq * 255).astype(np.uint8)).resize((t, t), Image.BILINEAR)) > 0).astype(np.float32)
            out = self.infer(im, hm)
            out = np.clip(out, 0, 255).astype(np.uint8)
            out = np.asarray(Image.fromarray(out).resize((side, side), Image.BICUBIC))[oy:oy + H, ox:ox + W]
        else:
            # résolution d'origine (réduite seulement au-delà de 1024 px), complétée au multiple de 8 par réflexion
            s = min(1.0, 1024.0 / max(H, W))
            h2, w2 = max(8, int(round(H * s))), max(8, int(round(W * s)))
            im = img if s == 1.0 else np.asarray(Image.fromarray(img).resize((w2, h2), Image.BICUBIC))
            hm = hole if s == 1.0 else (np.asarray(Image.fromarray((hole * 255).astype(np.uint8)).resize((w2, h2), Image.BILINEAR)) > 0).astype(np.float32)
            ph, pw = (-h2) % 8, (-w2) % 8
            im = np.pad(im, ((0, ph), (0, pw), (0, 0)), mode="reflect").astype(np.float32)
            hm = np.pad(hm, ((0, ph), (0, pw)), mode="reflect")
            out = self.infer(im, hm)[:h2, :w2]
            out = np.clip(out, 0, 255).astype(np.uint8)
            if s != 1.0:
                out = np.asarray(Image.fromarray(out).resize((W, H), Image.BICUBIC))
        k = keep[..., None]
        return np.where(k, img, out).astype(np.uint8)


models = []
for repo, f, fmt in cands:
    try:
        models.append(Model(repo, f, fmt))
    except Exception as e:
        say(f"- modèle {repo}/{f} : échec ({e})")

pairs = sorted(glob.glob(os.path.join(PAIRS, "*_img.jpg")))
say(f"\n{len(pairs)} paires, {len(models)} modèles\n")
times = {m.tag: [] for m in models}
for p in pairs:
    base = os.path.basename(p)[:-8]
    img = np.asarray(Image.open(p).convert("RGB"))
    keep = np.asarray(Image.open(os.path.join(PAIRS, base + "_mask.png")).convert("L")) > 127
    ours = np.asarray(Image.open(os.path.join(PAIRS, base + "_ours.jpg")).convert("RGB"))
    row = [img, ours]
    for m in models:
        try:
            t0 = time.time()
            r = m.run(img, keep)
            dt = time.time() - t0
            times[m.tag].append((dt, img.shape[0] * img.shape[1]))
            Image.fromarray(r).save(os.path.join(OUT, f"{base}__{m.tag}.jpg"), quality=92)
            row.append(r)
        except Exception as e:
            say(f"{base} / {m.tag} : erreur {e}")
            traceback.print_exc()
            row.append(np.zeros_like(img))
    gap = np.full((img.shape[0], 8, 3), 255, np.uint8)
    strip = np.concatenate(sum(([r, gap] for r in row), [])[:-1], axis=1)
    sim = Image.fromarray(strip)
    if sim.width > 2400:
        sim = sim.resize((2400, max(1, int(sim.height * 2400 / sim.width))))
    sim.save(os.path.join(OUT, f"{base}__planche.jpg"), quality=88)

with open(os.path.join(OUT, "summary.md"), "w") as fh:
    fh.write("# Essai de retouche par IA (modèles manga)\n\nPlanches : origine | effacement actuel | " + " | ".join(m.tag for m in models) + "\n\n")
    for m in models:
        ts = sorted(times[m.tag])
        if ts:
            mp = sum(t for t, _ in ts) / max(1, sum(a for _, a in ts)) * 1e6
            fh.write(f"- {m.tag} ({'AOT' if m.aot else 'LaMa'}, {m.size / 1e6:.1f} Mo, sortie {m.range}) : médiane {ts[len(ts) // 2][0] * 1000:.0f} ms, max {ts[-1][0] * 1000:.0f} ms, {mp * 1000:.0f} ms par mégapixel, sur {len(ts)} régions (processeur du serveur de test, 4 fils)\n")
    fh.write("\n## Journal\n\n```\n" + "\n".join(log) + "\n```\n")
say("fini")
