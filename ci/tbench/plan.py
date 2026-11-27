#!/usr/bin/env python3
"""Matrice des tâches du banc : chaque variante (options du banc) × chaque lot (fichier .tsv). Lit ci/tbench/plan.json."""
import json
p = json.load(open("ci/tbench/plan.json"))
inc = [{"variant": v, "opts": o, "shard": s} for v, o in p["variants"].items() for s in p["shards"]]
print(json.dumps({"include": inc}))
