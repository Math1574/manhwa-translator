#!/usr/bin/env python3
"""Affiche l'URL du binaire llama.cpp Linux x64 (CPU) de la version publiée la plus récente."""
import json, os, re, urllib.request
req = urllib.request.Request("https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=10", headers={"Authorization": "Bearer " + os.environ.get("GH_TOKEN", ""), "User-Agent": "ci"})
for rel in json.loads(urllib.request.urlopen(req, timeout=60).read()):
    for a in rel.get("assets", []):
        if re.fullmatch(r"llama-b\d+-bin-ubuntu-x64\.tar\.gz", a["name"]):
            print(a["browser_download_url"]); raise SystemExit(0)
raise SystemExit("aucun binaire trouvé")
