#!/usr/bin/env python3
"""Sondage Hugging Face : liste (avec tailles) les fichiers des dépôts de modèles utiles et lit quelques fiches."""
import json, sys, urllib.request, urllib.parse
REPOS = [
    "ogkalu/comic-text-and-bubble-detector", "PaddlePaddle/PP-OCRv6_small_det_onnx", "PaddlePaddle/PP-OCRv6_tiny_det_onnx", "Sundowner123/comic-text-and-bubble-detector",
    "a-b-c-x-y-z/Manga-Text-Segmentation-2025", "mayocream/manga-text-segmentation-2025", "iamokish/manga-text-segmentation-safetensors",
    "mloet/comic_text_bubble_detector", "hon-lu/comic-text-and-bubble-detector-onnx-fp16", "depreciation84/comic-text-and-bubble-detector-int8",
    "hhuggg/comictextdetector", "rikimaruwebsite/comic-text-detector-web", "petersunde/anima-manga-text-removal-flow", "mayocream/manga109-segmentation-bubble",
    "mednasserallah/manga109-segmentation-bubble-onnx", "ShadowB/Manga109-panel-balloon-text-yolov26-segmentation",
]
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "probe"})
    with urllib.request.urlopen(req, timeout=60) as r: return r.read()
out = []
for repo in REPOS:
    out.append(f"===== {repo}")
    try:
        meta = json.loads(get(f"https://huggingface.co/api/models/{repo}"))
        out.append(f"licence={meta.get('cardData', {}).get('license')} tags={','.join((meta.get('tags') or [])[:12])} mis_à_jour={meta.get('lastModified')}")
        tree = json.loads(get(f"https://huggingface.co/api/models/{repo}/tree/main?recursive=1"))
        files = [(t.get('path'), t.get('size') or (t.get('lfs') or {}).get('size') or 0) for t in tree if t.get('type') == 'file']
        for p, s in files[:400]:
            if repo == "SWHL/RapidOCR" and not any(k in p for k in ("v5", "v6", "PP-OCRv4", "en_", "dict", "korean", "japan", "chinese_cht", "latin")): continue
            out.append(f"  {s/1e6:9.2f} Mo  {p}")
        if len(files) > 400: out.append(f"  ... {len(files)} fichiers au total")
    except Exception as e:
        out.append(f"  ERREUR {e}")
out.append("===== recherche Hy-MT2")
try:
    for m in json.loads(get("https://huggingface.co/api/models?search=Hy-MT2&limit=20")): out.append("  " + m["id"])
except Exception as e: out.append(f"  ERREUR {e}")
open(sys.argv[1], "w", encoding="utf8").write("\n".join(out))

# ---- fiches techniques et téléchargements ----
import os, re
docs = [("ogkalu/comic-text-and-bubble-detector", "README.md", 7000), ("ogkalu/comic-text-and-bubble-detector", "config.json", 2500),
        ("ogkalu/comic-text-and-bubble-detector", "preprocessor_config.json", 1200), ("mayocream/comic-text-detector-onnx", "README.md", 5000),
        ("a-b-c-x-y-z/Manga-Text-Segmentation-2025", "README.md", 5000), ("mayocream/manga-text-segmentation-2025", "README.md", 4000)]
more = []
for repo, path, n in docs:
    more.append(f"\n===== DOC {repo}/{path}")
    try: more.append(get(f"https://huggingface.co/{repo}/resolve/main/{path}").decode("utf8", "replace")[:n])
    except Exception as e: more.append(f"  ERREUR {e}")
open(sys.argv[1], "a", encoding="utf8").write("\n".join(more))
os.makedirs("/tmp/models", exist_ok=True)
JOBS = [
    ("onnx-community/manga-ocr-base-ONNX", r"onnx/(encoder|decoder)_model_(uint8|quantized)\.onnx$"),
]
for repo, pattern in JOBS:
    try:
        tree = json.loads(get(f"https://huggingface.co/api/models/{repo}/tree/main?recursive=1"))
        for t in tree:
            if t.get("type") != "file": continue
            pth = t["path"]; size = t.get("size") or (t.get("lfs") or {}).get("size") or 0
            if not re.search(pattern, pth) or size > 95_500_000: continue
            open("/tmp/models/" + repo.replace("/", "__") + "__" + pth.replace("/", "__"), "wb").write(get(f"https://huggingface.co/{repo}/resolve/main/{pth}"))
    except Exception as e:
        open(sys.argv[1], "a", encoding="utf8").write(f"\nERREUR téléchargement {repo}: {e}")
