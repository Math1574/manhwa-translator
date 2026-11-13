# Manhwa Translator

Application Android : navigateur intégré qui traduit automatiquement les mangas, manhwas et manhuas **directement sur le téléphone**
(OCR et traduction locaux, aucun compte, aucune clé d'API, aucun envoi de vos pages à un serveur de traduction).

## Installer
1. Ouvrez la page **Releases** du dépôt et téléchargez le dernier `ManhwaTranslator-debug.apk`.
2. Installez-le (autorisez l'installation depuis cette source si Android le demande).

## Mises à jour
- Toutes les versions publiées sont signées avec **la même clé fixe**, rangée dans un brouillon privé du dépôt (workflow « Cle de signature », à lancer une seule fois) :
  une nouvelle version s'installe **par-dessus la précédente, sans désinstaller**, et garde réglages, bibliothèque, glossaire, corrections et modèles.
- Réglages → carte **Mise à jour de l'application** : vérification au lancement (au plus toutes les six heures), nouveautés, téléchargement (reprise possible,
  taille et somme de contrôle vérifiées), puis installation par Android. Rien ne se télécharge sans appui sur le bouton. Une pastille signale une version disponible.
- L'APK du téléphone ne contient que l'architecture arm64. Le lecteur de japonais vertical (manga-ocr, environ 100 Mo) est un **module facultatif**, téléchargé à la demande
  (Réglages → Modèles de traduction) depuis la Release `models-ja-v1`.
- Sur le téléphone, la première installation d'une version signée avec la clé fixe demande de désinstaller l'ancienne une seule fois ; les suivantes non.

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
- **Navigateur** : barre d'adresse, précédent/suivant, actualisation, plein écran, **favoris** (cœur), **historique** et **reprise de la lecture** par œuvre (onglet Bibliothèque ; la page d'accueil du navigateur les affiche aussi).
  **Blocage des publicités** (régies connues, fenêtres intempestives, redirections vers un autre site), réglable dans Réglages.
- **Lecture du texte** (OCR) local, deux moteurs fusionnés : ML Kit et **PaddleOCR PP-OCRv5** (modèles ONNX, Apache-2.0) : anglais (deux lecteurs en concurrence), coréen, japonais, chinois ;
  **japonais vertical** par manga-ocr. Un filtre écarte filigranes, logos et débris de lecture ; un dictionnaire anglais répare mots collés et chiffres parasites avant traduction.
- **Traduction** locale avec contexte : Gemma 4 traduit toute la page d'un coup, avec les derniers dialogues du chapitre, le glossaire, les noms propres du chapitre et des consignes de traducteur de bande dessinée.
  Chaque ligne passe des contrôles automatiques (mots anglais restants, contenu perdu, noms modifiés…) et est retraduite si besoin. Décodage spéculatif et **test de vitesse CPU / GPU** dans Réglages.
  ML Kit reste disponible comme option légère (traduction bulle par bulle, qualité inférieure).
- **Correction manuelle** : un appui long sur une traduction l'ouvre pour correction ; on peut aussi fixer un nom ou un terme pour tout le chapitre. Les corrections sont mémorisées.
- **Journal de traduction** (Réglages → Exporter) : texte lu, traduction et problèmes détectés, pour signaler des erreurs. Il reste sur le téléphone.
- **Rendu** : le texte d'origine est effacé (aplat dans les bulles, fond lissé pour les dégradés, reconstruction sur les dessins) puis la traduction est mise en page **dans la forme réelle de la bulle**
  (retours à la ligne équilibrés), avec une police choisie parmi **143 variantes de polices libres** par **comparaison de style avec l'encre du lettrage d'origine** (graisse, épaisseur des traits, contraste, forme des contours, largeur ; métrique apprise sur des pages synthétiques), cohérente d'un texte à l'autre et mémorisée par série,
  l'interligne d'origine, une double résolution pour la netteté et la typographie française. Polices libres (SIL OFL).
- **Glossaire** local, cache des pages traduites, traitement page par page en file d'attente.

## Limites connues
- Lettrages très stylisés : l'OCR peut se tromper ; Réglages → **Diagnostic** montre ce qui est détecté (vert) ou rejeté (rouge) ; le journal permet de me signaler les erreurs.
- Sur un dessin chargé, le fond reconstruit derrière un texte effacé peut paraître flou ou taché (aucune méthode testée n'a fait nettement mieux, voir docs/STATUS.md).
- Vitesse de Gemma et accélération GPU : mesurables sur votre téléphone avec « Tester CPU / GPU » ; non vérifiées sur un téléphone réel par le développeur.
- Pas encore : onglets multiples, gestion des chapitres, détecteur de bulles dédié, NPU.

## Développement et tests
- `core/` : logique pure Kotlin (regroupement des bulles, mise en page, effacement, cache, file d'attente, glossaire, téléchargement de modèles) : plus de 110 tests, `gradle :core:test`.
- `app/` : application Android (Compose, WebView, ML Kit, LiteRT-LM).
- GitHub Actions (`.github/workflows/build.yml`) : tests du cœur, compilation de l'APK, publication en Release, puis **test de bout en bout sur émulateur**
  (faux site de lecture avec pages Pepper&Carrot, licence CC-BY 4.0, et pages synthétiques ; captures d'écran, rapport JSON, mesures).
  Lancement manuel avec `suite=full` pour inclure Gemma.
- Les résultats des tests sont publiés dans les branches `e2e-quick` et `e2e-full`.

## Crédits
Pages de test : *Pepper&Carrot* par David Revoy (CC-BY 4.0), https://www.peppercarrot.com. Polices : SIL Open Font License 1.1.


## Licences et crédits
Voir [docs/CREDITS.md](docs/CREDITS.md) (modèles, polices, dictionnaire, liste de blocage des publicités).
