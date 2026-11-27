#!/usr/bin/env python3
"""Inverse de pack.py. Usage : unpack.py <data.bin> <dossier>"""
import gzip, json, os, sys
KEY = b"manhwa-translator-tbench"
raw = open(sys.argv[1], "rb").read()
files = json.loads(gzip.decompress(bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(raw))).decode("utf-8"))
os.makedirs(sys.argv[2], exist_ok=True)
for f, t in files.items(): open(os.path.join(sys.argv[2], f), "w", encoding="utf-8").write(t)
print(len(files), "fichiers")
