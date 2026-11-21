# Bibliothèque de polices du texte traduit

Le texte traduit prend la police de la bibliothèque qui ressemble le plus au lettrage d'origine (`core/.../StyleMatcher.kt`).

- `build_library.py` : liste des candidats (source unique), copie des fichiers de police dans `app/src/main/assets/fonts/lib/`, génération de `core/.../FontLibrary.kt`
  et, avec `--static DIR`, une police statique par candidat (instances des polices variables) pour mesurer en JVM.
  Les polices viennent du dépôt Google Fonts (SIL OFL 1.1 ou Apache 2.0) ; classées par dossier de famille dans `--src`.
- `harness/` : générateurs d'échantillons (rendus dégradés propres, pages synthétiques passées par la vraie chaîne d'effacement de l'application) et banc de mesure.
- `train_style_model.py` : apprend les métriques de ressemblance (analyse discriminante linéaire) et génère `core/.../StyleModel.kt`.

Reconstruire le modèle : générer les échantillons avec `harness/` (voir les propriétés `-Dk`, `-Dseed`, `-Dout` de chaque programme), puis
`python3 train_style_model.py --pipe-train pipe_train_*.csv --pipe-test pipe_test_*.csv --clean-train samples_train.csv --out ../../core/src/main/kotlin/app/manhwa/core/StyleModel.kt`.

## Polices stylisées

`build_library.py` déclare aussi `styled` (technique, gothique, horreur, script, pochoir, titres percutants) : elles restent HORS du modèle de style appris (`StyleModel`) et ne sont jamais choisies par `StyleMatcher`.
`core/.../StyledFonts.kt` ne les choisit seul que dans deux cas : fenêtre système (texte entre crochets, clair sur fond sombre) et lettrage qu'aucune police ordinaire ne copie.
Leur choix manuel est toujours possible dans l'éditeur (onglet « Police »).

Seuils de `StyledFonts` : réglés avec `harness/StyledBench.kt` (dessin Java2D équivalent à celui de l'application, échantillons dégradés de la vraie chaîne d'effacement). Compiler avec le cœur :
`kotlinc core/src/main/kotlin harness/StyleBench.kt harness/PipeSamples.kt harness/StyledBench.kt`, puis
`java -Dfonts=<dossier des polices statiques> -Dwhich=regular|styled -Dleave=none|self|family -Dk=8 -Dout=bench.csv StyledBenchKt`.
`which=regular` (l'original est une police ordinaire, privée de toute sa famille avec `leave=family`) mesure les fausses décisions : il doit n'y en avoir aucune.

