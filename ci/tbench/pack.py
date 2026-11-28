#!/usr/bin/env python3
"""Emballe les fichiers d'un dossier (entrées du banc, ou ses résultats) dans un seul fichier : texte compressé et brouillé (pas de dialogues lisibles ni indexables dans le dépôt).
Usage : pack.py <dossier> <fichier.bin>"""
import gzip, json, os, sys
KEY = b"manhwa-translator-tbench"
d = sys.argv[1]
files = {f: open(os.path.join(d, f), encoding="utf-8", errors="replace").read() for f in sorted(os.listdir(d)) if os.path.isfile(os.path.join(d, f))}
raw = gzip.compress(json.dumps(files, ensure_ascii=False).encode("utf-8"))
open(sys.argv[2], "wb").write(bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(raw)))
print(len(files), "fichiers,", len(raw), "octets")
