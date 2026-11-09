#!/usr/bin/env python3
"""Apprend les métriques de ressemblance des styles de lettrage et écrit core/.../StyleModel.kt.

Entrées : des CSV d'échantillons (un par ligne : id de police, classe, hauteur, puis les 15 mesures du descripteur ; les échantillons de la chaîne complète ajoutent le mode d'effacement
et le type de fond), produits par harness/StylePipeDump.kt (pages synthétiques passées par la vraie chaîne de l'application) et harness/StyleDump.kt (rendus dégradés propres).
Méthode : analyse discriminante linéaire régularisée sur les mesures standardisées ; chaque police est représentée par le centre de ses échantillons dans l'espace projeté.
Deux espaces, car la qualité de la mesure dépend du fond :
  - « flat » : texte sur fond uni (bulles) — mesure précise, entraîné sur les échantillons de la chaîne complète en mode FLAT uniquement ;
  - « all »  : texte sur dessin et regroupement des blocs — entraîné sur tout (chaîne complète + rendus propres).
"""
import argparse, csv, glob, numpy as np
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis as LDA

def load_pipe(paths):
    ids, X, M = [], [], []
    for p in paths:
        for r in csv.reader(open(p)): ids.append(r[0]); X.append([float(x) for x in r[3:18]]); M.append(r[18])
    return np.array(ids), np.array(X), np.array(M)

def load_clean(paths):
    ids, X = [], []
    for p in paths:
        for r in csv.reader(open(p)): ids.append(r[0]); X.append([float(x) for x in r[3:18]])
    return np.array(ids), np.array(X)

def fit(X, y, nlab, shrink, dims):
    mu = X.mean(0); sd = X.std(0) + 1e-6
    m = LDA(solver="eigen", shrinkage=shrink).fit((X - mu) / sd, y)
    W = m.scalings_[:, :dims]
    Z = ((X - mu) / sd) @ W
    protos = np.array([Z[y == c].mean(0) for c in range(nlab)])
    raw = np.array([X[y == c].mean(0) for c in range(nlab)])
    return mu, sd, W, protos, raw

def evaluate(model, X, y, name):
    mu, sd, W, protos, raw = model
    Z = ((X - mu) / sd) @ W
    d = ((Z[:, None, :] - protos[None]) ** 2).sum(2); o = np.argsort(d, 1)
    print(name, {k: round(100 * float(np.mean([y[i] in o[i, :k] for i in range(len(y))])), 1) for k in (1, 3, 5)})

ap = argparse.ArgumentParser()
ap.add_argument("--pipe-train", nargs="+", required=True); ap.add_argument("--pipe-test", nargs="*", default=[])
ap.add_argument("--clean-train", nargs="*", default=[]); ap.add_argument("--dims", type=int, default=12); ap.add_argument("--shrink", type=float, default=0.05)
ap.add_argument("--out", required=True)
a = ap.parse_args()
pi, pX, pM = load_pipe(a.pipe_train)
ci, cX = load_clean(a.clean_train) if a.clean_train else (np.array([]), np.zeros((0, 15)))
labels = sorted(set(pi)); l2i = {l: i for i, l in enumerate(labels)}
yv = lambda ids: np.array([l2i[i] for i in ids])
flat = fit(pX[pM == "FLAT"], yv(pi[pM == "FLAT"]), len(labels), a.shrink, a.dims)
allm = fit(np.vstack([pX, cX]) if len(cX) else pX, np.concatenate([yv(pi), yv(ci)]) if len(cX) else yv(pi), len(labels), a.shrink, a.dims)
if a.pipe_test:
    ti, tX, tM = load_pipe(a.pipe_test)
    evaluate(flat, tX[tM == "FLAT"], yv(ti[tM == "FLAT"]), "test fond uni  (métrique flat) :")
    evaluate(allm, tX[tM == "ART"], yv(ti[tM == "ART"]), "test dessin    (métrique all)  :")
    evaluate(allm, tX, yv(ti), "test ensemble  (métrique all)  :")

def arr(x): return " ".join("%.5g" % v for v in np.asarray(x).ravel())
def space(name, m):
    mu, sd, W, protos, raw = m
    return f'    val {name}: Space = Space("{arr(mu)}", "{arr(sd)}", "{arr(W)}", "{arr(protos)}", "{arr(raw)}")'
L = ["package app.manhwa.core", "", "// GÉNÉRÉ par tools/fontlib/train_style_model.py : ne pas modifier à la main.", "",
     "/**",
     " * Métriques apprises de ressemblance des styles : standardisation, projection discriminante, centre de chaque police candidate (projeté et brut).",
     " * [flat] : texte sur fond uni (mesure précise) ; [all] : texte sur dessin (mesure bruitée) et regroupement des blocs.",
     " */",
     "object StyleModel {",
     f"    const val DIM_IN = {pX.shape[1]}", f"    const val DIM_OUT = {a.dims}", "",
     "    class Space(mu: String, sd: String, w: String, protos: String, raw: String) {",
     "        val mu = parse(mu); val sd = parse(sd)",
     "        /** Projection : ligne = composante d'entrée (DIM_IN), colonne = composante de sortie (DIM_OUT). */",
     "        val w = parse(w); val protos = parse(protos)",
     "        /** Mesures brutes moyennes de chaque police, telles que la chaîne les mesure dans ce régime. */",
     "        val raw = parse(raw)",
     "        private fun parse(s: String): FloatArray = s.split(' ').map { it.toFloat() }.toFloatArray()",
     "    }", "",
     "    val ids: List<String> = \"" + " ".join(labels) + "\".split(\" \")",
     space("flat", flat), space("all", allm), "}", ""]
open(a.out, "w", encoding="utf8").write("\n".join(L))
print("écrit", a.out, "—", len(labels), "polices")
