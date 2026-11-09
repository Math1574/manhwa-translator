#!/usr/bin/env python3
"""Analyse des rapports de test : lecture OCR (vérité terrain des pages synthétiques), couverture, mise en page, erreurs, durées."""
import re, os
import json, os, re, sys, glob, difflib

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/e2e"
SYN = sys.argv[2] if len(sys.argv) > 2 else "/tmp/site/synth"
lines = []
try:
    FONT_CLS = dict(re.findall(r'Spec\("([^"]+)", "[^"]*", "[^"]*", "([^"]+)"', open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "core", "src", "main", "kotlin", "app", "manhwa", "core", "FontLibrary.kt"), encoding="utf8").read()))
except Exception:
    FONT_CLS = {}

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
    if name in ('adtest', 'fs'): continue      # rapports d'autres scénarios (bloqueur, plein écran), lus plus bas
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
    tot_ocr = rej = kept = 0; overlap = outside = tiny = excess = 0; nblocks = 0
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
            # effacement disproportionné : la zone effacée ne doit pas dépasser de beaucoup le texte (sinon du dessin est abîmé)
            if b.get("mode") != "SMOOTH" and b.get("erase", 0) > 12 * max(1, b["w"] * b["h"]): excess += 1
        for i in range(len(rects)):
            for j in range(i + 1, len(rects)):
                a, b2 = rects[i], rects[j]
                ix = max(0, min(a[2], b2[2]) - max(a[0], b2[0])); iy = max(0, min(a[3], b2[3]) - max(a[1], b2[1]))
                small = min((a[2]-a[0])*(a[3]-a[1]), (b2[2]-b2[0])*(b2[3]-b2[1]))
                if small > 0 and ix * iy / small > 0.1:
                    # un recouvrement déjà présent dans l'image d'origine (bruitage dessiné sur une bulle) n'est pas un défaut de la traduction
                    oa, ob = ov[i].get("orig"), ov[j].get("orig")
                    if oa and ob:
                        oix = max(0, min(oa[2], ob[2]) - max(oa[0], ob[0])); oiy = max(0, min(oa[3], ob[3]) - max(oa[1], ob[1]))
                        osmall = min((oa[2]-oa[0])*(oa[3]-oa[1]), (ob[2]-ob[0])*(ob[3]-ob[1]))
                        if osmall > 0 and oix * oiy / osmall > 0.1: continue
                    overlap += 1
    nofit = sum(1 for im in imgs for b in (im.get('overlay') or {}).get('blocks', []) if b.get('fits') is False)
    shaped = sum(1 for im in imgs for b in (im.get('overlay') or {}).get('blocks', []) if b.get('shape'))
    if nblocks: P(f"- rendu : {shaped}/{nblocks} bulles à forme détectée · texte ne tenant pas dans sa zone : {nofit}")
    if nofit: problems.append(f"{name}: texte ne tient pas dans sa zone ({nofit})")
    # police : chaque bloc reçoit la police de la bibliothèque (143 candidates libres) qui copie le mieux son lettrage d'origine ; jamais d'épaississement ni de déformation en largeur.
    # Les pages synthétiques sont dessinées avec des polices sans empattement ou comiques : une police à empattement ou de machine à écrire serait un mauvais appariement, et les blocs
    # d'un même lettrage (toutes les bulles de la page « caps ») doivent recevoir la même police ou presque.
    fam_count = {}; cls_count = {}; fam_bad = 0; odd = 0; scattered = 0
    synth_latin = name.startswith(("synth_en_caps", "synth_en_mixed", "synth_en_variety", "synth_en_fancy"))
    for im in imgs:
        fs = []
        for b in (im.get('overlay') or {}).get('blocks', []):
            f = b.get('family')
            if not f or f == 'noto': continue                      # source CJK : police calibrée
            c = FONT_CLS.get(f, '?'); fam_count[f] = fam_count.get(f, 0) + 1; cls_count[c] = cls_count.get(c, 0) + 1; fs.append(f)
            if (b.get('embolden') or 0) > 0 or abs((b.get('scaleX') or 1) - 1) > 0.01: fam_bad += 1
            if synth_latin and c in ('serif', 'mono'): odd += 1
        if name.startswith(("synth_en_caps", "synth_en_mixed")) and len(set(fs)) > 2: scattered += 1
    if fam_count:
        P(f"- police : {', '.join(f'{k}×{v}' for k, v in sorted(fam_count.items(), key=lambda kv: -kv[1]))} ; classes : {', '.join(f'{k}×{v}' for k, v in sorted(cls_count.items(), key=lambda kv: -kv[1]))}")
        if fam_bad: problems.append(f"{name}: {fam_bad} bloc(s) avec épaississement ou déformation de la police")
        if odd: problems.append(f"{name}: {odd} bloc(s) de page synthétique avec une police à empattement ou machine à écrire (mauvais appariement)")
        if scattered: problems.append(f"{name}: plus de deux polices différentes sur une page au lettrage unique ({scattered} page(s))")
    if name.startswith("synth_layouts"):
        # regroupement : un bloc lu qui recouvre deux blocs de la vérité est une fusion à tort ; un bloc de la vérité réparti sur deux blocs lus est une scission
        merges = splits = 0; gt_total = 0; read_total = 0
        for k, im in enumerate(r.get("images", [])):
            try: gtb = json.load(open(f"{SYN}/layouts_{k}.json"))
            except Exception: continue
            gboxes = []
            for b in gtb:
                xs0 = min(l["box"][0] for l in b["lines"]); ys0 = min(l["box"][1] for l in b["lines"]); xs1 = max(l["box"][2] for l in b["lines"]); ys1 = max(l["box"][3] for l in b["lines"])
                gboxes.append((xs0, ys0, xs1, ys1))
            def cov(g, d):
                ix = max(0, min(g[2], d[2]) - max(g[0], d[0])); iy = max(0, min(g[3], d[3]) - max(g[1], d[1])); return ix * iy / max(1, (g[2] - g[0]) * (g[3] - g[1]))
            rboxes = [(b["x0"], b["y0"], b["x1"], b["y1"]) for b in im.get("blocks", [])]
            gt_total += len(gboxes); read_total += len(rboxes)
            for d in rboxes:
                if sum(1 for g in gboxes if cov(g, d) >= 0.35) >= 2: merges += 1
            for g in gboxes:
                if sum(1 for d in rboxes if cov(g, d) >= 0.35) >= 2: splits += 1
        P(f"- regroupement : {read_total} blocs lus pour {gt_total} blocs réels · fusions à tort {merges} · scissions {splits}")
        if merges > 2: problems.append(f"{name}: {merges} blocs fusionnés à tort")
    if name.startswith("synth_atypical"):
        # bulle ouverte (page 5) : toutes les lignes de la traduction doivent rester dans l'ellipse de la bulle (centre 430,330 ; rayons 285 × 225)
        try:
            im = r.get("images", [])[4]
            out = 0
            for b in im.get("overlay", {}).get("blocks", []):
                import math
                bx, by, bw, bh = b["x"], b["y"], b["w"], b["h"]
                for cxp, cyp in ((bx, by), (bx + bw, by), (bx, by + bh), (bx + bw, by + bh)):
                    if ((cxp - 430) / 285.0) ** 2 + ((cyp - 330) / 225.0) ** 2 > 1.7 and cyp < 600: out += 1   # coins du rectangle des lignes : un texte dans l'ellipse en a naturellement un peu au-delà (1,3), un texte qui déborde bien plus (3)
            if out: problems.append(f"{name}: texte de la bulle ouverte hors de l'ellipse ({out} coins)")
            # page 6 : filigrane collé à une bulle ; la bulle doit être traduite (un bloc affiché), le filigrane jamais
            im6 = r.get("images", [])[5]
            n6 = len(im6.get("overlay", {}).get("blocks", [])) if im6.get("overlay") else 0
            P(f"- filigrane contre une bulle : {n6} bloc(s) traduit(s)")
            if n6 < 1: problems.append(f"{name}: bulle supprimée avec le filigrane voisin")
            P(f"- bulle ouverte : {'texte hors ellipse' if out else 'texte dans l ellipse'}")
        except Exception: pass
    srcs = {}
    for im in imgs:
        k = im.get("fetch") or "?"
        srcs[k] = srcs.get(k, 0) + 1
    if srcs and any(k != "?" for k in srcs):
        P("- origine des octets des images : " + ", ".join(f"{k} × {v}" for k, v in sorted(srcs.items())) + " · durée moyenne d'obtention " + str(round(sum(im.get("fetchMs", 0) for im in imgs) / max(1, len(imgs)))) + " ms")
    timed = [im for im in imgs if im.get("ocrMs") is not None and im.get("outcome", "").startswith("done")]
    if timed:
        avg = lambda k: round(sum(im.get(k, 0) for im in timed) / len(timed))
        P(f"- durées moyennes par image ({len(timed)}) : obtention {avg('fetchMs')} ms · lecture du texte {avg('ocrMs')} ms · traduction {avg('trMs')} ms · affichage {avg('renderMs')} ms")
        ot = [im.get("ocrT") for im in timed if im.get("ocrT")]
        if ot: P(f"- détail de la lecture (dernière image) : {ot[-1]}")
        why = sorted({im.get("fetchWhy") for im in imgs if im.get("fetchWhy")})
        if why: P("- lecture dans la WebView impossible, motif : " + " ; ".join(why)[:300])
    if name.startswith("seam_"):
        gt = [{"pair": "a", "needles": ["NEVER", "FORGIVE", "DID", "HER"]}, {"pair": "b", "needles": ["COME", "JOURNEY", "BEGUN"]}]     # mots lus dans la bulle entière (voir gen_seams.py)
        on = not name.startswith("seam_off")
        whole = 0
        for g in gt:
            ims = [im for im in imgs if f"seam_{g['pair']}" in im.get("src", "")]
            blks = [b for im in ims for b in im.get("blocks", [])]
            full = [b for b in blks if all(n in b.get("src", "").upper() for n in g["needles"])]
            up = [im for im in ims if im.get("overlayUp") and im["overlayUp"].get("patches")]
            P(f"- jonction {g['pair'].upper()} : {len(blks)} bloc(s), dont {len(full)} lu(s) en entier ; image du dessus recouverte : {'oui' if up else 'non'}")
            whole += 1 if full else 0
            if on:
                if len(full) != 1: problems.append(f"{name}: paire {g['pair']} : le texte coupé n'est pas lu en un seul bloc ({len(full)})")
                elif len(blks) != 1: problems.append(f"{name}: paire {g['pair']} : {len(blks)} blocs au lieu d'un (texte traduit deux fois ou en morceaux)")
                if not up: problems.append(f"{name}: paire {g['pair']} : rien n'est posé sur l'image du dessus")
        P(f"- textes coupés lus en entier : {whole}/{len(gt)} ({'avec' if on else 'sans'} la jonction)")
    if name.startswith("pc_en_toggle"):
        done_n = sum(1 for im in imgs if im.get("outcome", "").startswith("done"))
        tot_n = c.get("total") or 0
        P(f"- bascule Original/Traduit : {done_n} images traitées sur {tot_n} repérées ; {[x for x in r.get('notes', []) if 'bascule' in x]}")
        if r.get("timeout") or tot_n < 8 or c.get("done", 0) + c.get("failed", 0) < tot_n:
            problems.append(f"{name}: après une bascule Original/Traduit, des images restent sans traduction ({c.get('done', 0)}+{c.get('failed', 0)} sur {tot_n}, délai dépassé : {r.get('timeout')})")
    if name.startswith("pc_en_long"):
        done_n = sum(1 for im in imgs if im.get("outcome", "").startswith("done"))
        tot_n = c.get("total") or 0
        # la page compte 36 images, mais l'une des sept images du fonds de test est trop petite pour être une page : 31 candidates attendues
        if tot_n < 28 or done_n < tot_n: problems.append(f"{name}: long chapitre : {done_n} images traitées sur {tot_n} repérées (36 dans la page) : arrêt ou échecs")
        seq = [im.get("ms", 0) for im in imgs if im.get("outcome", "").startswith("done")]
        if len(seq) >= 24:
            a = sum(seq[:12]) / 12; b = sum(seq[-12:]) / 12
            P(f"- long chapitre : durée moyenne des 12 premières {a/1000:.1f}s, des 12 dernières {b/1000:.1f}s")
            if b > 2.5 * a: problems.append(f"{name}: ralentissement sur la durée ({a/1000:.1f}s → {b/1000:.1f}s par image)")
        P(f"- débit : {done_n / max(1, r.get('elapsedMs', 1) / 60000):.1f} images par minute")
    shared = sum(1 for im in imgs if im.get("batch", 0) > 1)
    if any("batch" in im for im in imgs) and name.startswith("pc_en_"): P(f"- traductions regroupées avec d'autres pages : {shared}/{len(imgs)} images")
    if name.startswith("pc_en_once"):
        if c.get("done", 0) < c.get("total", 1) or c.get("total", 0) == 0: problems.append(f"{name}: images à usage unique non traduites ({c.get('done')}/{c.get('total')}) : la lecture par la WebView ne fonctionne pas")
        if srcs.get("page", 0) < 1: problems.append(f"{name}: aucune image lue dans la page ({srcs})")
    if name.startswith("pc_en_strict"):
        if c.get("done", 0) < c.get("total", 1) or c.get("total", 0) == 0: problems.append(f"{name}: téléchargement séparé refusé malgré les en-têtes de navigateur ({c.get('done')}/{c.get('total')})")
    if name.startswith("adblock"):
        blocked = r.get("blocked", 0); final = r.get("finalUrl", "")
        P(f"- blocage des publicités : {blocked} requêtes ou redirections bloquées · adresse finale {final}")
        if blocked < 3: problems.append(f"{name}: blocage insuffisant ({blocked})")
        if "evil-redirect" in final: problems.append(f"{name}: la redirection automatique a abouti")
    try:
        import re as _re
        mem = open(path.replace(".json", ".mem")).read()
        m = _re.search(r"TOTAL PSS:\s+(\d+)", mem) or _re.search(r"TOTAL\s+(\d+)", mem)
        if m: P(f"- mémoire de l'application (PSS) : {int(m.group(1)) // 1024} Mo")
    except Exception: pass
    if r.get("ocrNote"): P(f"- lecture Paddle : {r['ocrNote']}")
    P(f"- OCR : {tot_ocr} lignes ({kept} utilisées, {rej} écartées) · bulles affichées {nblocks} · hors image {outside} · chevauchements {overlap} · texte < 10px {tiny}")
    if outside or overlap: problems.append(f"{name}: mise en page (hors image {outside}, chevauchements {overlap})")
    if excess: problems.append(f"{name}: effacement disproportionné sur {excess} bloc(s)")

    # vérité terrain (pages synthétiques)
    m = re.match(r"synth_(.+?)_(mlkitonly|combined|mlkit|gemma4b|gemma)$", name)
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
            # les onomatopées (kind « sfx ») doivent rester telles quelles : « laissée » est le bon résultat, une traduction est l'erreur
            is_sfx = g["kind"] == "sfx"
            okc += rc >= 0.8; trc += (not tb) if is_sfx else bool(tb)
            verdict = ("laissée telle quelle" if not tb else "**traduite à tort**") if is_sfx else ("oui" if tb else "**NON**")
            P(f"| {g['kind']}: {g['text']} | {rc*100:.0f} % | {verdict} | {tr} |")
        P(f"\n**Lecture OCR correcte : {okc}/{len(gt)} · éléments traités comme attendu (traduits, onomatopées laissées) : {trc}/{len(gt)}**")
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

# interface (scénario ui) : une ligne « vérification<TAB>0 ou 1 » par contrôle fait sur l'export de l'écran de l'émulateur
adt = f"{OUT}/reports/adtest.json"
P("\n## Bloqueur de publicités (émulateur)\n")
if os.path.exists(adt) and os.path.getsize(adt) > 0:
    try:
        a = json.load(open(adt, encoding="utf8"))
        P("| vérification | résultat |\n|---|---|")
        for ck in a["checks"]: P(f"| {ck['name']} | {'oui' if ck['ok'] else '**NON**'} |")
        badc = [ck for ck in a["checks"] if not ck["ok"]]
        P(f"\n**Bloqueur : {len(a['checks']) - len(badc)}/{len(a['checks'])} vérifications réussies**")
        for ck in badc: problems.append(f"bloqueur : {ck['name']} ({str(ck.get('detail', ''))[:140]})")
        P("\nRequêtes tierces servies par le faux site (donc non bloquées) : " + (", ".join(a.get("served", [])) or "aucune"))
        P("\n<details><summary>journal du bloqueur</summary>\n\n```\n" + a.get("log", "")[:3500] + "\n```\n</details>")
        for n in a.get("notes", []): P("- " + str(n)[:300])
    except Exception as e:
        P(f"(rapport illisible : {e})"); problems.append("bloqueur : rapport illisible")
else:
    P("(scénario non exécuté ou sans rapport)"); problems.append("bloqueur : pas de rapport")

uit = f"{OUT}/reports/ui.tsv"
if os.path.exists(uit):
    rows = [l.rstrip("\n").split("\t") for l in open(uit, encoding="utf8") if "\t" in l]
    P("\n## Interface (émulateur)\n\n| vérification | résultat |\n|---|---|")
    for k, v in rows: P(f"| {k} | {'oui' if v.strip() == '1' else '**NON**'} |")
    bad = [k for k, v in rows if v.strip() != "1"]
    P(f"\n**Interface : {len(rows) - len(bad)}/{len(rows)} vérifications réussies**")
    for k in bad: problems.append(f"interface : {k}")
else:
    P("\n## Interface (émulateur)\n\n(scénario non exécuté)")

P("\n# Problèmes détectés\n" + ("\n".join(f"- {p}" for p in problems) if problems else "aucun"))
open(f"{OUT}/summary.md", "w").write("\n".join(lines))
print("\n".join(lines[-40:]))
