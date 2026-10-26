#!/usr/bin/env python3
"""Banc d'essai de traduction : chrF (β=2) contre des références, et comparaison côte à côte des variantes."""
import json, sys, collections
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/e2e"
corpus = json.load(open(sys.argv[2] if len(sys.argv) > 2 else "ci/lab/corpus.json"))
refs = [r for p in corpus["refs"] for r in p]; srcs = [s for p in corpus["pages"] for s in p]

def ngr(s, n):
    s = "".join(s.split()); return collections.Counter(s[i:i+n] for i in range(len(s) - n + 1))
def chrf(h, r, beta=2.0):
    ps, rs = [], []
    for n in range(1, 7):
        H, R = ngr(h, n), ngr(r, n)
        if not H or not R: continue
        ov = sum((H & R).values()); ps.append(ov / sum(H.values())); rs.append(ov / sum(R.values()))
    if not ps: return 0.0
    P, Rr = sum(ps) / len(ps), sum(rs) / len(rs)
    return 0.0 if P + Rr == 0 else (1 + beta**2) * P * Rr / (beta**2 * P + Rr)

lines = ["# Banc d'essai de traduction\n"]
res = {}
import glob, os
for path in sorted(glob.glob(f"{OUT}/reports/lab_*.json")):
    d = json.load(open(path)); tag = os.path.basename(path)[4:-5]
    for var, pages in d.get("variants", {}).items():
        hyp = [t for p in pages for t in p["tr"]]
        sc = [chrf(h.casefold(), r.casefold()) for h, r in zip(hyp, refs)]
        ms = sum(p["ms"] for p in pages) / max(1, len(pages))
        res[(tag, var)] = (hyp, sum(sc) / max(1, len(sc)), ms)
lines.append("| modèle / variante | chrF moyen | s / page |\n|---|---|---|")
for (tag, var), (hyp, sc, ms) in res.items(): lines.append(f"| {tag} {var} | {sc*100:.1f} | {ms/1000:.1f} |")
lines.append("")
keys = list(res.keys())
for i, (s, r) in enumerate(zip(srcs, refs)):
    lines.append(f"**{i+1}.** `{s}`\n- réf : {r}")
    for k in keys:
        h = res[k][0][i] if i < len(res[k][0]) else "(absent)"
        lines.append(f"- {k[0]} {k[1]} : {h}")
    lines.append("")
open(f"{OUT}/lab_summary.md", "w").write("\n".join(lines)); print("\n".join(lines[:12]))
