# Manhwa Translator

Application Android : navigateur intégré qui traduit automatiquement les mangas, manhwas et manhuas **directement sur le téléphone**
(OCR et traduction locaux, aucun compte, aucune clé d'API, aucun envoi de vos pages à un serveur de traduction).

## Installer
1. Ouvrez la page **Releases** du dépôt et téléchargez le dernier `ManhwaTranslator-debug.apk`.
2. Installez-le (autorisez l'installation depuis cette source si Android le demande).

## Première utilisation
1. Onglet **Modèles** : téléchargez **Gemma 4 E2B** (2,6 Go, Wi-Fi recommandé, gardez l'application ouverte ; le téléchargement est vérifié
   par somme de contrôle et peut être repris). Il est choisi automatiquement. **Gemma 4 E4B** (3,7 Go) est un peu plus lent, de qualité comparable.
2. Ouvrez un site de lecture dans le navigateur, puis un chapitre : **la traduction démarre toute seule** et se poursuit pendant le défilement.
   Aucun bouton à presser. Un bouton discret « Original / Traduit » permet de comparer.
3. Une fois le modèle téléchargé, tout fonctionne **sans connexion** (hors chargement du site lui-même).

## Plein écran
- Bouton **Plein écran** dans la barre du haut. Les barres système et l'interface disparaissent.
- Un petit bouton ✕ discret s'efface seul ; **trois appuis rapides sur l'écran** le font réapparaître pour quitter le plein écran. Le bouton Retour d'Android quitte aussi.

## Ce que fait l'application
- **OCR** local, deux moteurs fusionnés : ML Kit et **PaddleOCR** (modèles ONNX, Apache-2.0) pour repérer le texte stylisé, hors des bulles ou en lettrage spécial. Japonais, coréen, chinois, anglais.
- **Traduction** locale avec contexte : Gemma 4 traduit toute la page d'un coup, avec les derniers dialogues du chapitre, le glossaire et des consignes de traducteur de bande dessinée.
  ML Kit reste disponible comme option légère (traduction bulle par bulle, qualité inférieure).
- **Rendu** : le texte d'origine est effacé (aplat dans les bulles, reconstruction du fond sur les dessins) puis la traduction est mise en page **dans la forme réelle de la bulle**
  (retours à la ligne équilibrés, jamais de débordement), avec une police dont l'épaisseur et la largeur sont **calibrées sur le lettrage d'origine** (Noto Sans variable ; Bangers pour les cris),
  l'interligne d'origine, une double résolution pour la netteté et la typographie française. Polices libres (SIL OFL).
- **Glossaire** local (noms de personnages), cache des pages traduites, traitement page par page en file d'attente.

## Limites connues
- Lettrages très stylisés, texte vertical japonais : l'OCR les lit mal ou pas. Activez Réglages → **Diagnostic** pour voir ce qui est détecté (vert) ou rejeté (rouge).
- Sur un dessin chargé, le fond reconstruit derrière un texte effacé peut paraître un peu flou.
- Vitesse de Gemma et accélération GPU (option expérimentale) non mesurées sur téléphone réel : seulement sur émulateur (≈ 20 s par page sur CPU d'émulateur).
- Mode « amélioré » d'effacement par modèle d'IA : non disponible.

## Développement et tests
- `core/` : logique pure Kotlin (regroupement des bulles, mise en page, effacement, cache, file d'attente, glossaire, téléchargement de modèles) : 73 tests, `gradle :core:test`.
- `app/` : application Android (Compose, WebView, ML Kit, LiteRT-LM).
- GitHub Actions (`.github/workflows/build.yml`) : tests du cœur, compilation de l'APK, publication en Release, puis **test de bout en bout sur émulateur**
  (faux site de lecture avec pages Pepper&Carrot, licence CC-BY 4.0, et pages synthétiques ; captures d'écran, rapport JSON, mesures).
  Lancement manuel avec `suite=full` pour inclure Gemma.
- Les résultats des tests sont publiés dans les branches `e2e-quick` et `e2e-full`.

## Crédits
Pages de test : *Pepper&Carrot* par David Revoy (CC-BY 4.0), https://www.peppercarrot.com. Polices : SIL Open Font License 1.1.
