#!/usr/bin/env python3
"""Serveur de génération du banc d'essai de traduction : le même modèle Gemma (fichier .litertlm) que l'application, sur le processeur de la machine de test.

POST / avec un objet JSON {system, user, top_k, top_p, temperature, max_tokens, seed?} : réponse brute en texte.
GET /health : « ok » quand le modèle est chargé.
Usage : server.py <modèle.litertlm> <port> [threads]
Mode (variable TBENCH_SERVER) :
  conv    : une conversation neuve par demande (comme l'application) ;
  session : la consigne (système) lue une fois puis recopiée (clone de session, cache de préfixe), seul le message de la demande est lu à chaque fois.
            −37 % sur ce processeur, mais sur le GPU du téléphone (build 266, journal du 28/11 13:37) chaque recopie coûtait 6 à 10 s de plus et gardait
            environ 60 Mo jamais rendus (mémoire native de 1,3 à 4,7 Go en 30 pages, appli tuée par Android) : retiré de l'application au build 267 ;
  ab      : les deux sur chaque demande, réponse de « conv » renvoyée, comparaison (textes, durées) dans /tmp/ab.jsonl.
"""
import http.server, json, os, sys, time, threading
import litert_lm as lm

model, port = sys.argv[1], int(sys.argv[2])
threads = int(sys.argv[3]) if len(sys.argv) > 3 else None
t0 = time.time()
backend = lm.Backend.CPU(thread_count=threads) if threads else lm.Backend.CPU()
os.makedirs("/tmp/litert_cache", exist_ok=True)
engine = lm.Engine(model, backend=backend, max_num_tokens=8192, cache_dir="/tmp/litert_cache")
print(f"modèle chargé en {time.time() - t0:.1f} s", flush=True)
lock = threading.Lock()
mode = os.environ.get("TBENCH_SERVER", "conv")
print("mode :", mode, flush=True)
bases = {}   # (consigne, échantillonnage) -> session dont la consigne est déjà lue


def gen_conv(body, sampler):
    conv = engine.create_conversation(system_message=body["system"], sampler_config=sampler, max_output_tokens=int(body.get("max_tokens", 512)))
    try:
        return str(conv.send_message(body["user"]))
    finally:
        conv.close()


def gen_session(body, sampler):
    key = (body["system"], sampler.top_k, sampler.top_p, sampler.temperature)
    base = bases.get(key)
    if base is None:
        while len(bases) >= 2:
            bases.pop(next(iter(bases))).close()
        base = engine.create_session(apply_prompt_template=True, sampler_config=sampler)
        # Gemma 4 : pas de gabarit simple dans les métadonnées, le gabarit Jinja des conversations est reproduit à la main (sonde du 28/11 : réponse identique)
        base.run_prefill(["<|turn>system\n" + body["system"].strip() + "<turn|>\n<|turn>user\n"])
        bases[key] = base
    s = base.clone()
    try:
        s.run_prefill([body["user"].strip() + "<turn|>\n<|turn>model\n"])
        r = s.run_decode()
        return r.texts[0] if r.texts else ""
    finally:
        s.close()


class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, text):
        b = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        self._send(200, "ok")

    def do_POST(self):
        try:
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])).decode("utf-8"))
            sampler = lm.SamplerConfig(top_k=int(body.get("top_k", 20)), top_p=float(body.get("top_p", 0.9)),
                                       temperature=float(body.get("temperature", 0.15)), seed=int(body.get("seed", 7)))
            with lock:
                t = time.time()
                if mode == "session":
                    reply = gen_session(body, sampler)
                else:
                    reply = gen_conv(body, sampler)
                dt = time.time() - t
                if mode == "ab":
                    t2 = time.time()
                    try:
                        other = gen_session(body, sampler); err = None
                    except Exception as e:
                        other = ""; err = repr(e)
                    dt2 = time.time() - t2
                    with open("/tmp/ab.jsonl", "a", encoding="utf-8") as f:
                        f.write(json.dumps({"conv": reply, "session": other, "conv_s": round(dt, 2), "session_s": round(dt2, 2), "in_sys": len(body["system"]),
                                            "in_user": len(body["user"]), "same": reply == other, "err": err, "user": body["user"][-300:]}, ensure_ascii=False) + "\n")
            print(f"génération : {dt:.1f} s, {len(body['system']) + len(body['user'])} car. -> {len(reply)} car.", flush=True)
            self._send(200, reply)
        except Exception as e:  # le banc note l'échec de la page et continue
            print("erreur :", repr(e), flush=True)
            self._send(500, repr(e))


http.server.ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
