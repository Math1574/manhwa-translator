#!/usr/bin/env python3
"""Faux site de lecture pour les tests : sert /tmp/site, avec une zone /ref/ exigeant Referer + cookie (comme certains sites)."""
import http.server, json, os, sys

import threading
LOCK = threading.Lock()
ONCE_SEEN = set()
ROOT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/site"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8000

class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=ROOT, **k)

    def end_headers(self):
        if self.path.endswith(".html"):
            self.send_header("Set-Cookie", "sid=1; Path=/")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_GET(self):
        # /once/… : l'image n'est servie qu'à la PREMIÈRE requête (adresse à usage unique) : un second téléchargement par l'application reçoit 403
        # /strict/… : refusée (403) sans les en-têtes qu'un vrai navigateur envoie pour une image (Accept d'image, Accept-Language, Referer)
        if self.path.startswith("/once/") or self.path.startswith("/strict/"):
            once = self.path.startswith("/once/")
            if once:
                with LOCK:
                    seen = self.path in ONCE_SEEN
                    ONCE_SEEN.add(self.path)
                ok = not seen
            else:
                h = self.headers
                # (Chrome n'envoie les en-têtes Sec-Fetch-* qu'en HTTPS : on exige ceux que la WebView envoie aussi en HTTP simple)
                ok = bool(h.get("Accept-Language")) and bool(h.get("Referer")) and "image/" in h.get("Accept", "")
            if not ok:
                self.send_response(403); self.end_headers(); self.wfile.write(b"forbidden"); return
            self.path = "/pc/" + self.path.split("/", 2)[2].split("?")[0]
        if self.path.startswith("/ref/"):
            ok = (self.headers.get("Referer", "").startswith("http://10.0.2.2:") and "sid=1" in self.headers.get("Cookie", ""))
            if not ok:
                self.send_response(403); self.end_headers(); self.wfile.write(b"forbidden"); return
        super().do_GET()

    def do_POST(self):
        # /anilist : faux AniList pour les suggestions de noms (les contrôles n'appellent jamais le vrai service) ; même forme de réponse que graphql.anilist.co
        if self.path.split("?")[0] != "/anilist":
            self.send_response(404); self.end_headers(); return
        try:
            req = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0") or 0)).decode("utf-8"))
        except Exception:
            self.send_response(400); self.end_headers(); return
        q = str(req.get("query", "")); v = req.get("variables") or {}
        def name(first, last, native="", alt=None):
            full = (first + " " + last).strip()
            return {"first": first or None, "middle": None, "last": last or None, "full": full, "native": native or None, "alternative": alt}
        if "Page(" in q:
            media = []
            if "alpha" in str(v.get("s", "")).lower():
                media = [
                    {"id": 4242, "title": {"romaji": "Alpha Saga", "english": "Alpha Saga", "native": None}, "format": "MANGA", "countryOfOrigin": "KR", "startDate": {"year": 2019}, "isAdult": False},
                    {"id": 4243, "title": {"romaji": "Alpha Saga Gaiden", "english": None, "native": None}, "format": "NOVEL", "countryOfOrigin": "KR", "startDate": {"year": 2021}, "isAdult": False},
                ]
            body = {"data": {"Page": {"media": media}}}
        elif "Media(" in q:
            edges = [
                {"role": "MAIN", "node": {"name": name("Zarvokin", "Mirelka", "자르보킨", ["Zar"])}},
                {"role": "MAIN", "node": {"name": name("Brannic", "Doverell")}},
                {"role": "SUPPORTING", "node": {"name": name("Eren", "Stone")}},
                {"role": "BACKGROUND", "node": {"name": name("Kai", "")}},
            ]
            body = {"data": {"Media": {"characters": {"pageInfo": {"hasNextPage": False}, "edges": edges}}}}
        else:
            self.send_response(400); self.end_headers(); return
        data = json.dumps(body).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        sys.stderr.write("[site] " + (fmt % args) + "\n")

http.server.ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
