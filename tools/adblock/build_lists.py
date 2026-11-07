#!/usr/bin/env python3
"""
Assemble les listes de filtres du bloqueur de publicités de Manhwa Translator en un seul fichier compact :
app/src/main/assets/adblock/filters.txt (embarqué dans l'application, et téléchargé par l'application pour se mettre à jour).

Les listes sources sont téléchargées depuis GitHub (voir NOTICE.md pour les licences). On ne garde que ce que le moteur de l'application sait utiliser :
règles réseau (domaines, motifs, options gérées, exceptions) et masquage d'éléments propre à un site. Les règles génériques de masquage, les scriptlets,
les filtres procéduraux et les options non gérées (csp, removeparam, replace, cookie…) sont écartés pour garder un fichier léger.
"""
import datetime, os, re, sys, urllib.request

RAW = "https://raw.githubusercontent.com"
UBO = RAW + "/uBlockOrigin/uAssets/master/filters/%s.txt"
EL = RAW + "/easylist/easylist/master/easylist/%s.txt"
SOURCES = [
    ("uBlock Origin — filtres", [UBO % n for n in ("filters-general", "filters-2020", "filters-2021", "filters-2022", "filters-2023", "filters-2024", "filters-2025", "filters-2026",
                                                    "filters-mobile", "badware", "privacy", "unbreak", "quick-fixes", "resource-abuse")]),
    ("EasyList", [EL % n for n in ("easylist_adservers", "easylist_adservers_popup", "easylist_allowlist", "easylist_allowlist_popup", "easylist_general_block", "easylist_general_block_popup",
                                    "easylist_specific_block", "easylist_specific_block_popup", "easylist_thirdparty", "easylist_thirdparty_popup", "easylist_specific_hide")]),
    ("HaGeZi — Pop-Up Ads", [RAW + "/hagezi/dns-blocklists/main/adblock/popupads.txt"]),
]
LOCAL = os.path.join(os.path.dirname(__file__), "local.txt")
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "adblock", "filters.txt")

SUPPORTED = {"script", "image", "stylesheet", "css", "subdocument", "frame", "xmlhttprequest", "xhr", "media", "font", "object", "ping", "websocket", "other",
             "document", "doc", "popup", "popunder", "all", "third-party", "3p", "strict3p", "first-party", "1p", "strict1p", "important", "match-case", "badfilter",
             "redirect", "empty", "mp4", "domain", "from", "denyallow", "to"}
PROCEDURAL = (":has-text(", ":-abp-", ":xpath(", ":matches-css", ":matches-path", ":min-text-length", ":upward(", ":nth-ancestor(", ":others(", ":remove(", ":style(",
              ":watch-attr(", ":contains(", ":matches-media(", ":matches-attr(", ":matches-property(", ":is-visible", ":shadow(", "+js(")
OPT_TOKEN = re.compile(r"^[~a-z0-9_\-]+(=.*)?$", re.I)

def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "manhwa-translator-lists"}), timeout=60) as r:
                return r.read().decode("utf-8", "replace").splitlines()
        except Exception as e:
            print("  échec (%d/3) %s : %s" % (attempt + 1, url, e), file=sys.stderr)
    raise SystemExit("téléchargement impossible : " + url)

def split_options(s):
    out, start, i, in_re = [], 0, 0, False
    while i < len(s):
        c = s[i]
        if c == "=" and i + 1 < len(s) and s[i + 1] == "/": in_re = True
        if c == "," and not in_re: out.append(s[start:i]); start = i + 1
        if c == "," and in_re and i > 0 and s[i - 1] == "/": in_re = False; out.append(s[start:i]); start = i + 1
        i += 1
    out.append(s[start:]); return out

def keep(line):
    l = line.strip()
    if not l or l[0] in "![": return None
    if "$$" in l: return None
    h = l.find("#")
    if h >= 0 and any(l.startswith(m, h) for m in ("##", "#@#", "#?#", "#@?#", "#$#", "#@$#", "#%#", "#@%#")):
        if not (l.startswith("##", h) or l.startswith("#@#", h)): return None
        sel = l[h + (3 if l.startswith("#@#", h) else 2):].strip()
        if not l[:h] or not sel or "{" in sel or "}" in sel or any(p in sel for p in PROCEDURAL): return None
        doms = [d for d in l[:h].split(",") if d.strip()]
        if not any(not d.startswith("~") for d in doms) or any("*" in d or "/" in d for d in doms): return None
        return l
    body = l[2:] if l.startswith("@@") else l
    d = body.rfind("$")
    pattern = body
    if d >= 0:
        opts = body[d + 1:]
        toks = split_options(opts)
        if toks and all(OPT_TOKEN.match(t) for t in toks) and (opts[:1].isalnum() or opts[:1] == "~"):
            pattern = body[:d]
            has_domain = False
            for t in toks:
                name, _, val = t.partition("=")
                name = name.lstrip("~").lower()
                if name not in SUPPORTED: return None
                if name == "redirect" and l.startswith("@@"): return None
                if name == "badfilter": return l          # conservé : désactive la règle identique d'une autre liste
                if name in ("domain", "from", "denyallow", "to"):
                    if not val: return None
                    for x in val.split("|"):
                        x = x.strip()
                        if "*" in x or x.startswith("/") or x.startswith("~/"): return None
                if name in ("domain", "from") and val: has_domain = True
            if pattern in ("", "*") and not has_domain and not l.startswith("@@"): return None
    if pattern in ("", "*") and d < 0: return None
    if pattern.startswith("/") and pattern.endswith("/") and len(pattern) > 252: return None
    return l

def main():
    out_lines, seen, stats = [], set(), []
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    for title, urls in SOURCES:
        kept = total = 0
        out_lines.append("! ----- %s -----" % title)
        for url in urls:
            print("téléchargement", url)
            for line in fetch(url):
                total += 1
                k = keep(line)
                if k is None or k in seen: continue
                seen.add(k); out_lines.append(k); kept += 1
        stats.append("%s : %d règles retenues sur %d lignes" % (title, kept, total))
    if os.path.exists(LOCAL):
        out_lines.append("! ----- règles locales du projet -----")
        for line in open(LOCAL, encoding="utf-8").read().splitlines():
            if line.strip() and line.strip() not in seen: out_lines.append(line.strip()); seen.add(line.strip())
    header = ["! Manhwa Translator — listes de filtres assemblées le %s (tools/adblock/build_lists.py)" % now,
              "! Sources : uBlock Origin / uAssets (GPL-3.0), EasyList (GPL-3.0 ou CC BY-SA 3.0), HaGeZi (GPL-3.0). Voir NOTICE.md."] + ["! " + s for s in stats]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    text = "\n".join(header + out_lines) + "\n"
    # on ne réécrit pas le fichier si seul l'en-tête daté a changé (évite des commits inutiles)
    old = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
    if old.split("\n", 1)[-1].split("\n", 3)[-1] == text.split("\n", 1)[-1].split("\n", 3)[-1] and old:
        print("aucun changement dans les listes"); return
    with open(OUT, "w", encoding="utf-8", newline="\n") as f: f.write(text)
    print("écrit %s : %d lignes, %.1f Mo" % (os.path.abspath(OUT), len(out_lines), len(text.encode()) / 1e6)); print("\n".join(stats))

if __name__ == "__main__":
    main()
