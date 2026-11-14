#!/usr/bin/env python3
"""Vérifie, d'après un export uiautomator, que les textes donnés apparaissent du haut vers le bas dans l'ordre indiqué.
Usage : ui_order.py export.xml texte1 texte2 … — chaque texte est comparé en entier (sans tenir compte des majuscules) au texte ou à la description d'un nœud ;
code de sortie 0 si tous sont trouvés et rangés du plus haut au plus bas, sinon 1."""
import re, sys
import xml.etree.ElementTree as ET
xml, names = sys.argv[1], [n.strip().lower() for n in sys.argv[2:]]
try:
    root = ET.parse(xml).getroot()
except Exception:
    sys.exit(1)
tops = {}
for n in root.iter("node"):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
    if not m: continue
    top = int(m.group(2))
    for t in ((n.get("text") or "").strip().lower(), (n.get("content-desc") or "").strip().lower()):
        if t in names and (t not in tops or top < tops[t]): tops[t] = top
if any(n not in tops for n in names):
    print("introuvable :", [n for n in names if n not in tops]); sys.exit(1)
ys = [tops[n] for n in names]
if all(a < b for a, b in zip(ys, ys[1:])): sys.exit(0)
print("ordre constaté :", sorted(names, key=lambda n: tops[n])); sys.exit(1)
