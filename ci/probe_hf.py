#!/usr/bin/env python3
"""Sondage Hugging Face : liste (avec tailles) les fichiers des dépôts de modèles utiles et lit quelques fiches."""
import json, sys, urllib.request, urllib.parse
REPOS = [
    "SWHL/RapidOCR", "monkt/paddleocr-onnx",
    "edgetools/migan", "CardAnvilModels/MI-GAN-ONNX", "edgetools/lama", "mayocream/lama-manga-onnx", "mayocream/lama-manga",
    "ogkalu/comic-speech-bubble-detector-yolov8m", "ogkalu/comic-text-segmenter-yolov8m",
    "litert-community/gemma-4-E4B-it-litert-lm", "litert-community/gemma-4-E2B-it-litert-lm",
    "tencent/Hy-MT2-1.8B", "tencent/Hy-MT2-1.8B-GGUF", "tencent/Hy-MT2-7B-GGUF",
    "onnx-community/manga-ocr-base-ONNX", "mlboydaisuke/manga-ocr-ExecuTorch",
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
