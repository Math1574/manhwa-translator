"""Essai de retouche par IA (MI-GAN, LaMa en référence) sur les paires exportées du banc d'effacement.

Pour chaque paire : <nom>_img.jpg (région d'origine), <nom>_mask.png (255 = garder, 0 = trou), <nom>_ours.jpg (notre effacement actuel).
Sortie : <nom>__<modèle>.jpg, une planche « origine | actuel | modèles » et summary.md (modèles trouvés, entrées/sorties, temps).
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

api = HfApi()
REPOS = [
    "andraniksargsyan/migan",
    "lxfater/inpaint-web",
    "FreeHugsForRobots/ps-inpaint-migan",
    "Carve/LaMa-ONNX",
]
cands = []
for repo in REPOS:
    try:
        files = api.list_repo_files(repo)
        say(f"- dépôt {repo} : {files}")
        for f in files:
            lf = f.lower()
            if lf.endswith(".onnx") and ("migan" in lf or "lama" in lf or "inpaint" in lf or repo.endswith("ps-inpaint-migan")):
                cands.append((repo, f))
    except Exception as e:
        say(f"- dépôt {repo} : inaccessible ({e})")


class Model:
    def __init__(self, repo, f):
        self.tag = (repo.split("/")[0][:6] + "-" + os.path.basename(f).replace(".onnx", ""))[:40]
        path = hf_hub_download(repo, f)
        self.size = os.path.getsize(path)
        so = ort.SessionOptions()
        so.intra_op_num_threads = 4
        self.s = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
        self.ins = self.s.get_inputs()
        self.outs = self.s.get_outputs()
        sig = ", ".join(f"{i.name}{i.shape}:{i.type}" for i in self.ins) + " -> " + ", ".join(f"{o.name}{o.shape}:{o.type}" for o in self.outs)
        say(f"- modèle {self.tag} ({repo}/{f}, {self.size / 1e6:.1f} Mo) : {sig}")
        t = [i.type for i in self.ins]
        if len(self.ins) == 2 and all("uint8" in x for x in t):
            self.kind = "pipeline"   # image uint8 [1,3,H,W] + masque uint8 [1,1,H,W] (255 = garder) -> image uint8
        elif len(self.ins) == 1:
            self.kind = "migan"      # [1,4,512,512] = concat(masque - 0.5, rgb * masque), rgb dans [-1, 1]
        else:
            self.kind = "lama"       # image [1,3,512,512] 0..1 + masque [1,1,512,512] (1 = trou)
        say(f"  -> lu comme « {self.kind} »")

    def size_of(self, i):
        sh = self.ins[i].shape
        h, w = sh[2], sh[3]
        return (w if isinstance(w, int) else None, h if isinstance(h, int) else None)

    def run(self, img, keep):
        """img : HxWx3 uint8 ; keep : HxW bool (vrai = garder). Rend HxWx3 uint8 (trou rempli, reste inchangé)."""
        H, W = keep.shape
        if self.kind == "pipeline":
            x = img.transpose(2, 0, 1)[None].astype(np.uint8)
            m = (keep.astype(np.uint8) * 255)[None, None]
            out = self.s.run(None, {self.ins[0].name: x, self.ins[1].name: m})[0]
            res = out[0].transpose(1, 2, 0).astype(np.uint8)
            if res.shape[:2] != (H, W):
                res = np.asarray(Image.fromarray(res).resize((W, H), Image.BICUBIC))
        else:
            tw, th = self.size_of(0)
            tw = tw or 512
            th = th or 512
            im = np.asarray(Image.fromarray(img).resize((tw, th), Image.BICUBIC)).astype(np.float32)
            km = np.asarray(Image.fromarray((keep * 255).astype(np.uint8)).resize((tw, th), Image.NEAREST)) > 127
            km = km.astype(np.float32)
            if self.kind == "migan":
                x = im / 127.5 - 1.0
                inp = np.concatenate([km[None] - 0.5, (x * km[..., None]).transpose(2, 0, 1)], axis=0)[None].astype(np.float32)
                out = self.s.run(None, {self.ins[0].name: inp})[0][0].transpose(1, 2, 0)
                out = (out + 1.0) * 127.5
            else:
                a = (im / 255.0).transpose(2, 0, 1)[None].astype(np.float32)
                b = (1.0 - km)[None, None].astype(np.float32)
                out = self.s.run(None, {self.ins[0].name: a, self.ins[1].name: b})[0][0].transpose(1, 2, 0)
                if out.max() <= 1.5:
                    out = out * 255.0
            out = np.clip(out, 0, 255).astype(np.uint8)
            res = np.asarray(Image.fromarray(out).resize((W, H), Image.BICUBIC))
        k = keep[..., None]
        return np.where(k, img, res).astype(np.uint8)


models = []
for repo, f in cands:
    try:
        models.append(Model(repo, f))
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
            times[m.tag].append(dt)
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
    fh.write("# Essai de retouche par IA\n\nPlanches : origine | effacement actuel | " + " | ".join(m.tag for m in models) + "\n\n")
    for m in models:
        ts = sorted(times[m.tag])
        if ts:
            fh.write(f"- {m.tag} ({m.kind}, {m.size / 1e6:.1f} Mo) : médiane {ts[len(ts) // 2] * 1000:.0f} ms, max {ts[-1] * 1000:.0f} ms sur {len(ts)} régions (processeur du serveur de test)\n")
    fh.write("\n## Journal\n\n```\n" + "\n".join(log) + "\n```\n")
say("fini")
