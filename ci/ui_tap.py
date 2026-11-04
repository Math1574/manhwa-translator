#!/usr/bin/env python3
"""Centre du n-ième nœud (texte ou description) correspondant au motif, d'après un export uiautomator : affiche « x y », sinon code de sortie 1.
Usage : ui_tap.py export.xml motif [n] [top]   — motif préfixé de « = » : égalité exacte ; « top » : les nœuds sont classés du plus haut au plus bas de l'écran."""
import re, sys
import xml.etree.ElementTree as ET
xml, pat = sys.argv[1], sys.argv[2]
nth = int(sys.argv[3]) if len(sys.argv) > 3 else 1
top = len(sys.argv) > 4 and sys.argv[4] == "top"
exact = pat.startswith("=")
pat = pat[1:].lower() if exact else pat.lower()
try:
    root = ET.parse(xml).getroot()
except Exception:
    sys.exit(1)
found = []
for n in root.iter("node"):
    texts = [(n.get("text") or "").strip().lower(), (n.get("content-desc") or "").strip().lower()]
    ok = any(t == pat for t in texts) if exact else any(pat in t for t in texts)
    if not ok: continue
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
    if not m: continue
    x0, y0, x1, y1 = map(int, m.groups())
    found.append(((x0 + x1) // 2, (y0 + y1) // 2, y0))
if top: found.sort(key=lambda f: f[2])
if len(found) >= nth:
    print(found[nth - 1][0], found[nth - 1][1]); sys.exit(0)
sys.exit(1)
