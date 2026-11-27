#!/usr/bin/env python3
"""Prépare les entrées du banc d'essai de traduction à partir d'un journal de traduction exporté (une ligne JSON par page).

Usage : prep.py <journal.jsonl> <sélection.json> <dossier de sortie>
Sélection : {"<œuvre (segment d'adresse, sans l'identifiant)>": [[<chapitre>, <pages au plus>], …], …}
Sortie : un fichier <œuvre>.tsv par œuvre (chapitre \t œuvre \t page \t texte lu réparé \t texte brut), pages dans l'ordre de lecture.
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

for slug, chapters in sel.items():
    rows = []
    for ch, maxp in chapters:
        imgs = sorted(pages[slug].get(str(ch), {}).keys(), key=num)[:maxp]
        for img in imgs:
            for b in pages[slug][str(ch)][img]:
                clean = (b.get("rep") or b["src"]).replace("\t", " ").replace("\n", " ")
                src = b["src"].replace("\t", " ").replace("\n", " ")
                rows.append("\t".join([f"{slug}/{ch}", titles[slug], img, clean, src]))
    open(os.path.join(out_dir, f"{slug}.tsv"), "w", encoding="utf-8").write("\n".join(rows) + "\n")
    print(slug, len(rows), "bulles")
