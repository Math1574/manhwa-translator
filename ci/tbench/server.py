#!/usr/bin/env python3
"""Serveur de génération du banc d'essai de traduction : le même modèle Gemma (fichier .litertlm) que l'application, sur le processeur de la machine de test.

POST / avec un objet JSON {system, user, top_k, top_p, temperature, max_tokens, seed?} : une conversation neuve (comme dans l'application), réponse brute en texte.
GET /health : « ok » quand le modèle est chargé.
Usage : server.py <modèle.litertlm> <port> [threads]
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
                conv = engine.create_conversation(system_message=body["system"], sampler_config=sampler, max_output_tokens=int(body.get("max_tokens", 512)))
                try:
                    reply = str(conv.send_message(body["user"]))
                finally:
                    conv.close()
                dt = time.time() - t
            print(f"génération : {dt:.1f} s, {len(body['system']) + len(body['user'])} car. -> {len(reply)} car.", flush=True)
            self._send(200, reply)
        except Exception as e:  # le banc note l'échec de la page et continue
            print("erreur :", repr(e), flush=True)
            self._send(500, repr(e))


http.server.ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
