#!/usr/bin/env python3
"""Prépare les entrées du banc d'essai de traduction à partir d'un journal de traduction exporté (une ligne JSON par page).

Usage : prep.py <journal.jsonl> <sélection.json> <dossier de sortie>
Sélection : {"<œuvre (segment d'adresse, sans l'identifiant)>": {"run": [[<chapitre>, <pages au plus>], …], "warm": [<chapitre déjà lu>, …]}, …}
  (forme courte : [[<chapitre>, <pages au plus>], …] sans préchauffage)
Sortie : un fichier <œuvre>.tsv par œuvre (chapitre \t œuvre \t page \t texte lu réparé \t texte brut), pages dans l'ordre de lecture ;
  <œuvre>.warm.tsv : chapitres « déjà lus » (mêmes colonnes + traduction du journal), pour préchauffer la mémoire de l'œuvre sans appeler le modèle.
"""
import json, re, sys, os, collections

journal, sel_path, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
sel = json.load(open(sel_path))
os.makedirs(out_dir, exist_ok=True)
pages = collections.defaultdict(lambda: collections.defaultdict(dict))
titles = {}
for line in open(journal, encoding="utf-8"):
    o = json.loads(line)
    if o.get("type") != "page": continue
    parts = o["url"].rstrip("/").split("/")
    slug = re.sub(r"-[0-9a-f]{6,}$", "", parts[-3]); ch = parts[-1].split("#")[0]
    if slug not in sel: continue
    # titre de l'œuvre déduit de l'adresse, comme dans l'application (le champ « series » du journal peut être celui de la page suivante)
    titles[slug] = " ".join(w.capitalize() for w in slug.split("-"))
    pages[slug][ch][o["img"]] = o["blocks"]

def num(img):
    m = re.search(r"\d+", img); return int(m.group()) if m else 0

def one(s):
    return (s or "").replace("\t", " ").replace("\n", " ")

def rows_of(slug, ch, maxp, with_tr):
    out = []
    imgs = sorted(pages[slug].get(str(ch), {}).keys(), key=num)[:maxp]
    for img in imgs:
        for b in pages[slug][str(ch)][img]:
            r = [f"{slug}/{ch}", titles[slug], img, one(b.get("rep") or b["src"]), one(b["src"])]
            if with_tr: r.append(one(b.get("tr")))
            out.append("\t".join(r))
    return out

for slug, spec in sel.items():
    if isinstance(spec, list): spec = {"run": spec, "warm": []}
    rows = [r for ch, maxp in spec["run"] for r in rows_of(slug, ch, maxp, False)]
    open(os.path.join(out_dir, f"{slug}.tsv"), "w", encoding="utf-8").write("\n".join(rows) + "\n")
    warm = [r for ch in spec.get("warm", []) for r in rows_of(slug, ch, 10_000, True)]
    if warm: open(os.path.join(out_dir, f"{slug}.warm.tsv"), "w", encoding="utf-8").write("\n".join(warm) + "\n")
    print(slug, len(rows), "bulles à traduire,", len(warm), "de préchauffage")
