#!/usr/bin/env python3
"""Analyse des rapports de test : lecture OCR (vérité terrain des pages synthétiques), couverture, mise en page, erreurs, durées."""
import json, os, re, sys, glob, difflib

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/e2e"
SYN = sys.argv[2] if len(sys.argv) > 2 else "/tmp/site/synth"
lines = []
def P(*a): lines.append(" ".join(str(x) for x in a))

def norm(s): return "".join(ch for ch in s.casefold() if ch.isalnum())
def inter(a, b, pad=0):
    return not (a[2] + pad < b[0] or b[2] + pad < a[0] or a[3] + pad < b[1] or b[3] + pad < a[1])
def box(d): return (d["x0"], d["y0"], d["x1"], d["y1"])
def recall(gt_text, got):
    g, o = norm(gt_text), norm(got)
    if not g: return 1.0
    m = difflib.SequenceMatcher(None, g, o, autojunk=False)
    return sum(b.size for b in m.get_matching_blocks()) / len(g)

problems = []
P("# Rapport de test automatique\n")
for path in sorted(glob.glob(f"{OUT}/reports/*.json")):
    name = os.path.basename(path)[:-5]
    if name == 'prep':
        try: P('## préparation\n- ' + '\n- '.join(json.load(open(path)).get('notes', [])) + '\n')
        except Exception as e: P(f'## préparation illisible : {e}')
        continue
    try:
        r = json.load(open(path))
    except Exception as e:
        P(f"## {name}\n**RAPPORT ILLISIBLE** : {e}\n"); problems.append(f"{name}: rapport illisible"); continue
    c = r.get("counters", {}); imgs = r.get("images", [])
    P(f"## {name}")
    P(f"- durée {r.get('elapsedMs',0)//1000}s{' — **DÉLAI DÉPASSÉ**' if r.get('timeout') else ''} · images {c.get('total')} · traitées {c.get('done')} · échecs {c.get('failed')} · sans texte {c.get('noText')} · moteur {r.get('settings',{}).get('engine')} · {r.get('settings',{}).get('source')}→{r.get('settings',{}).get('target')}")
    if c.get("lastError"): P(f"- dernière erreur : `{c['lastError']}`"); problems.append(f"{name}: {c['lastError']}")
    for n in r.get("notes", []): P(f"- note : {n}")
    if r.get("timeout"): problems.append(f"{name}: délai dépassé")
    if not imgs: problems.append(f"{name}: aucune image traitée")
    ms = [i.get("ms", 0) for i in imgs if i.get("outcome", "").startswith("done")]
    if ms: P(f"- temps moyen par image : {sum(ms)/len(ms)/1000:.1f}s (max {max(ms)/1000:.1f}s)")
    tot_ocr = rej = kept = 0; overlap = outside = tiny = 0; nblocks = 0
    for im in imgs:
        if im.get("outcome") == "failed": problems.append(f"{name}: image en échec ({im.get('stage')}: {im.get('message')})")
        for o in im.get("ocr", []):
            tot_ocr += 1
            if o["s"] == "gardée": kept += 1
            else: rej += 1
        ov = im.get("overlay", {}).get("blocks", []) if im.get("overlay") else []
        W, H = im.get("w", 0), im.get("h", 0)
        nblocks += len(ov)
        rects = [(b["x"], b["y"], b["x"] + b["w"], b["y"] + b["h"]) for b in ov]
        for b, rc in zip(ov, rects):
            if rc[0] < -1 or rc[1] < -1 or rc[2] > W + 1 or rc[3] > H + 1: outside += 1
            if b["fs"] < 10: tiny += 1
        for i in range(len(rects)):
            for j in range(i + 1, len(rects)):
                a, b2 = rects[i], rects[j]
                ix = max(0, min(a[2], b2[2]) - max(a[0], b2[0])); iy = max(0, min(a[3], b2[3]) - max(a[1], b2[1]))
                small = min((a[2]-a[0])*(a[3]-a[1]), (b2[2]-b2[0])*(b2[3]-b2[1]))
                if small > 0 and ix * iy / small > 0.1: overlap += 1
    P(f"- OCR : {tot_ocr} lignes ({kept} utilisées, {rej} écartées) · bulles affichées {nblocks} · hors image {outside} · chevauchements {overlap} · texte < 10px {tiny}")
    if outside or overlap: problems.append(f"{name}: mise en page (hors image {outside}, chevauchements {overlap})")

    # vérité terrain (pages synthétiques)
    m = re.match(r"synth_(.+?)_(mlkit|gemma|gemma4b)$", name)
    gtname = {"ja_vert": "ja_vertical"}.get(m.group(1), m.group(1)) if m else None
    if gtname and os.path.exists(f"{SYN}/{gtname}.json") and imgs:
        gt = json.load(open(f"{SYN}/{gtname}.json")); im = imgs[0]
        P("\n| texte attendu | lu par l'OCR | traduit ? | traduction |\n|---|---|---|---|")
        okc = trc = 0
        for g in gt:
            gb = g["box"]
            got = " ".join(o["t"] for o in im.get("ocr", []) if inter(box(o), gb, 8))
            rc = recall(g["text"], got)
            tb = [b for b in im.get("blocks", []) if inter(box(b), gb, 8)]
            tr = " / ".join(b["tr"] for b in tb)
            okc += rc >= 0.8; trc += bool(tb)
            P(f"| {g['kind']}: {g['text']} | {rc*100:.0f} % | {'oui' if tb else '**NON**'} | {tr} |")
        P(f"\n**Lecture OCR correcte : {okc}/{len(gt)} · éléments traduits : {trc}/{len(gt)}**")
        if okc < len(gt): problems.append(f"{name}: OCR {okc}/{len(gt)}")
        if trc < len(gt): problems.append(f"{name}: traduits {trc}/{len(gt)}")
    else:
        # pages réelles : échantillon de traductions à lire
        P("\n| original | traduction |\n|---|---|")
        shown = 0
        for im in imgs:
            for b in im.get("blocks", []):
                if shown < 14: P(f"| {b['src'][:110]} | {b['tr'][:140]} |"); shown += 1
    P("")

P("\n# Problèmes détectés\n" + ("\n".join(f"- {p}" for p in problems) if problems else "aucun"))
open(f"{OUT}/summary.md", "w").write("\n".join(lines))
print("\n".join(lines[-40:]))
