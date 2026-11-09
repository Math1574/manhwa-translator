# Bibliothèque de polices du texte traduit

Le texte traduit prend la police de la bibliothèque qui ressemble le plus au lettrage d'origine (`core/.../StyleMatcher.kt`).

- `build_library.py` : liste des candidats (source unique), copie des fichiers de police dans `app/src/main/assets/fonts/lib/`, génération de `core/.../FontLibrary.kt`
  et, avec `--static DIR`, une police statique par candidat (instances des polices variables) pour mesurer en JVM.
  Les polices viennent du dépôt Google Fonts (SIL OFL 1.1 ou Apache 2.0) ; classées par dossier de famille dans `--src`.
- `harness/` : générateurs d'échantillons (rendus dégradés propres, pages synthétiques passées par la vraie chaîne d'effacement de l'application) et banc de mesure.
- `train_style_model.py` : apprend les métriques de ressemblance (analyse discriminante linéaire) et génère `core/.../StyleModel.kt`.

Reconstruire le modèle : générer les échantillons avec `harness/` (voir les propriétés `-Dk`, `-Dseed`, `-Dout` de chaque programme), puis
`python3 train_style_model.py --pipe-train pipe_train_*.csv --pipe-test pipe_test_*.csv --clean-train samples_train.csv --out ../../core/src/main/kotlin/app/manhwa/core/StyleModel.kt`.
