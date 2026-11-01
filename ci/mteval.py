#!/usr/bin/env python3
"""Évaluation hôte de la traduction : Hy-MT2 (llama.cpp) sur le corpus de 60 répliques, scoré en chrF contre les références.
Variantes : texte tel que lu (majuscules), phrase normale, phrase normale + contexte de la page (gabarit « Background Information » de Tencent)."""
import json, sys, time, subprocess, os, collections, urllib.request

gguf, server, tag, out_json = sys.argv[1:5]
variants = sys.argv[5].split(",") if len(sys.argv) > 5 else ["upper", "sentence", "context"]
corpus = json.load(open("ci/lab/corpus.json"))
pages, refs = corpus["pages"], [r for p in corpus["refs"] for r in p]

def sentence_case(s):
    t = s.lower(); out = []; cap = True
    for ch in t:
        out.append(ch.upper() if cap and ch.isalpha() else ch)
        if ch.isalpha(): cap = False
        if ch in ".!?": cap = True
    r = "".join(out)
    import re
    return re.sub(r"\bi\b", "I", r)

def ngr(s, n):
    s = "".join(s.split()); return collections.Counter(s[i:i + n] for i in range(len(s) - n + 1))
def chrf(h, r, beta=2.0):
    ps, rs = [], []
    for n in range(1, 7):
        H, R = ngr(h, n), ngr(r, n)
        if not H or not R: continue
        ov = sum((H & R).values()); ps.append(ov / sum(H.values())); rs.append(ov / sum(R.values()))
    if not ps: return 0.0
    P, Rr = sum(ps) / len(ps), sum(rs) / len(rs)
    return 0.0 if P + Rr == 0 else (1 + beta ** 2) * P * Rr / (beta ** 2 * P + Rr)

srv = subprocess.Popen([server, "-m", gguf, "-c", "4096", "-t", "4", "--port", "8081", "--host", "127.0.0.1"], stdout=open("/tmp/srv.log", "w"), stderr=subprocess.STDOUT)
for _ in range(240):
    try: urllib.request.urlopen("http://127.0.0.1:8081/health", timeout=2); break
    except Exception: time.sleep(2)
else:
    print("serveur indisponible"); open("/tmp/srv.log").read(); sys.exit(1)

def ask(prompt):
    body = json.dumps({"model": "x", "messages": [{"role": "user", "content": prompt}], "temperature": 0, "max_tokens": 200, "stream": False}).encode()
    req = urllib.request.Request("http://127.0.0.1:8081/v1/chat/completions", data=body, headers={"Content-Type": "application/json"})
    t = time.time(); r = json.loads(urllib.request.urlopen(req, timeout=600).read())
    return r["choices"][0]["message"]["content"].strip(), time.time() - t

res = {}
for v in variants:
    outs = []; tot = 0.0
    for pg in pages:
        for i, line in enumerate(pg):
            src = line if v == "upper" else sentence_case(line)
            if v == "context" and i > 0:
                bg = "\n".join(sentence_case(x) for x in pg[max(0, i - 3):i])
                prompt = f"[Background Information]\n{bg}\n\nPlease translate the following text into French, taking the provided background information into consideration.\n\n[Source Text]\n{src}"
            else:
                prompt = f"Translate the following text into French. Note that you should only output the translated result without any additional explanation:\n\n{src}"
            o, dt = ask(prompt); outs.append(o); tot += dt
    sc = [chrf(h.casefold(), r.casefold()) for h, r in zip(outs, refs)]
    res[v] = {"chrf": 100 * sum(sc) / len(sc), "s_par_ligne": tot / len(outs), "sorties": outs}
    print(tag, v, f"chrF {res[v]['chrf']:.1f}", f"{res[v]['s_par_ligne']:.1f} s/ligne", flush=True)
srv.terminate()
json.dump({"modele": tag, "variantes": res}, open(out_json, "w"), ensure_ascii=False, indent=1)
