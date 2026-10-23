#!/usr/bin/env python3
"""Faux site de lecture pour les tests : sert /tmp/site, avec une zone /ref/ exigeant Referer + cookie (comme certains sites)."""
import http.server, os, sys

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
        if self.path.startswith("/ref/"):
            ok = (self.headers.get("Referer", "").startswith("http://10.0.2.2:") and "sid=1" in self.headers.get("Cookie", ""))
            if not ok:
                self.send_response(403); self.end_headers(); self.wfile.write(b"forbidden"); return
        super().do_GET()

    def log_message(self, fmt, *args):
        sys.stderr.write("[site] " + (fmt % args) + "\n")

http.server.ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
