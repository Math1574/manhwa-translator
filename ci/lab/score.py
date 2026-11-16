#!/usr/bin/env python3
"""Banc d'essai de traduction : chrF (β=2) contre des références, contrôles de sens (expressions, faux amis, titres, bégaiements) et comparaison côte à côte des variantes.

Usage : score.py <dossier de résultats> <corpus.json> [--selftest]
  --selftest : vérifie que chaque référence du corpus réussit son propre contrôle (sinon le contrôle est faux, pas la traduction).
"""
import json, sys, collections, re, glob, os

args = [a for a in sys.argv[1:] if not a.startswith("--")]
OUT = args[0] if len(args) > 0 else "/tmp/e2e"
corpus = json.load(open(args[1] if len(args) > 1 else "ci/lab/corpus.json"))
refs = [r for p in corpus["refs"] for r in p]; srcs = [s for p in corpus["pages"] for s in p]
checks = [c for p in corpus.get("checks", []) for c in p] + [{}] * len(srcs)
new_from = corpus.get("new_from")
n_old = sum(len(p) for p in corpus["pages"][:new_from]) if new_from else len(srcs)

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

def passes(h, c):
    """None : pas de contrôle sur cette ligne. Sinon vrai si tous les motifs « ok » se trouvent et aucun motif « bad »."""
    if not c or ("ok" not in c and "bad" not in c): return None
    h = h.replace("’", "'")
    oks = c.get("ok", []); oks = [oks] if isinstance(oks, str) else oks
    for p in oks:
        if not re.search(p, h, re.I): return False
    return not (c.get("bad") and re.search(c["bad"], h, re.I))

if "--selftest" in sys.argv:
    bad = [(i + 1, r) for i, (r, c) in enumerate(zip(refs, checks)) if passes(r, c) is False]
    for i, r in bad: print(f"ligne {i} : la référence échoue à son propre contrôle : {r} / {checks[i-1]}")
    print("contrôles :", sum(1 for c in checks if passes("", c) is not None), "; références en échec :", len(bad)); sys.exit(1 if bad else 0)

KIND = {"idiom": "expressions toutes faites", "falsefriend": "faux amis", "title": "titres (maître, aîné…)", "stutter": "bégaiements", "noise": "lecture bruitée", "natural": "langue courante", "narration": "narration"}
lines = ["# Banc d'essai de traduction\n"]
res = {}
for path in sorted(glob.glob(f"{OUT}/reports/lab_*.json")):
    d = json.load(open(path)); tag = os.path.basename(path)[4:-5]
    for var, pages in d.get("variants", {}).items():
        hyp = [t for p in pages for t in p["tr"]]
        sc = [chrf(h.casefold(), r.casefold()) for h, r in zip(hyp, refs)]
        ms = sum(p["ms"] for p in pages) / max(1, len(pages))
        res[(tag, var)] = (hyp, sc, ms)

def mean(x): return sum(x) / len(x) if x else 0.0
def rate(hyp, pick):
    got = [passes(h, c) for h, c in zip(hyp, checks) if pick(c)]; got = [g for g in got if g is not None]
    return (sum(1 for g in got if g), len(got))
def pc(t): return f"{t[0]}/{t[1]}" if t[1] else "–"

lines.append(f"chrF : « anciennes » = les {n_old} premières lignes (13 pages, mêmes que les essais précédents) ; « nouvelles » = le reste (expressions, faux amis, titres, bégaiements, lecture bruitée).\n")
lines.append("| modèle / variante | chrF anciennes | chrF nouvelles | contrôles réussis | expressions de la table | expressions hors table | s / page |\n|---|---|---|---|---|---|---|")
for (tag, var), (hyp, sc, ms) in res.items():
    lines.append(f"| {tag} {var} | {mean(sc[:n_old])*100:.1f} | {mean(sc[n_old:])*100:.1f} | {pc(rate(hyp, lambda c: True))} | "
                 f"{pc(rate(hyp, lambda c: c.get('table') is True))} | {pc(rate(hyp, lambda c: c.get('table') is False))} | {ms/1000:.1f} |")
lines.append("")
lines.append("| modèle / variante | " + " | ".join(KIND.values()) + " |\n|---|" + "---|" * len(KIND))
for (tag, var), (hyp, sc, ms) in res.items():
    lines.append(f"| {tag} {var} | " + " | ".join(pc(rate(hyp, lambda c, k=k: c.get("kind") == k)) for k in KIND) + " |")
lines.append("")
summary_len = len(lines)
keys = list(res.keys())
for i, (s, r) in enumerate(zip(srcs, refs)):
    c = checks[i]
    lines.append(f"**{i+1}.** `{s}`" + (f"  _({KIND.get(c.get('kind'), c.get('kind'))}{', table' if c.get('table') else (', hors table' if c.get('table') is False else '')})_" if c.get("kind") else "") + f"\n- réf : {r}")
    for k in keys:
        h = res[k][0][i] if i < len(res[k][0]) else "(absent)"
        p = passes(h, c) if i < len(res[k][0]) else None
        lines.append(f"- {k[0]} {k[1]} {'' if p is None else ('✓' if p else '✗')} : {h}")
    lines.append("")
open(f"{OUT}/lab_summary.md", "w").write("\n".join(lines)); print("\n".join(lines[:summary_len]))
