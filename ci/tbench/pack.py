#!/usr/bin/env python3
"""Emballe les entrées du banc (fichiers .tsv et .premises d'un dossier) dans data.bin : texte compressé et brouillé (pas de dialogues lisibles ni indexables dans le dépôt).
Usage : pack.py <dossier> <data.bin>"""
import gzip, json, os, sys
KEY = b"manhwa-translator-tbench"
d = sys.argv[1]
files = {f: open(os.path.join(d, f), encoding="utf-8").read() for f in sorted(os.listdir(d)) if f.endswith((".tsv", ".premises"))}
raw = gzip.compress(json.dumps(files, ensure_ascii=False).encode("utf-8"))
open(sys.argv[2], "wb").write(bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(raw)))
print(len(files), "fichiers,", len(raw), "octets")
