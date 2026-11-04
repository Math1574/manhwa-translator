#!/usr/bin/env python3
"""Centre du n-ième nœud (texte ou description) contenant le motif, d'après un export uiautomator : affiche « x y », sinon code de sortie 1."""
import re, sys
import xml.etree.ElementTree as ET
xml, pat = sys.argv[1], sys.argv[2].lower()
nth = int(sys.argv[3]) if len(sys.argv) > 3 else 1
found = 0
try:
    root = ET.parse(xml).getroot()
except Exception:
    sys.exit(1)
for n in root.iter("node"):
    label = (n.get("text") or "") + " " + (n.get("content-desc") or "")
    if pat in label.lower():
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if not m: continue
        found += 1
        if found == nth:
            x0, y0, x1, y1 = map(int, m.groups()); print((x0 + x1) // 2, (y0 + y1) // 2); sys.exit(0)
sys.exit(1)
