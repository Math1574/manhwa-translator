# Choix techniques (étude non vérifiée en ligne : accès à Hugging Face et aux dépôts bloqué ici)

Les points marqués « à vérifier » reposent sur mes connaissances et doivent être confirmés avant intégration.

## OCR — choix v1 : ML Kit Text Recognition v2 (modèles embarqués dans l'APK)
- Modules latin, chinois, japonais, coréen ; fonctionne hors ligne ; rend des coordonnées par ligne.
- Limites : SDK gratuit mais propriétaire (pas open source) ; texte vertical japonais et polices très stylisées mal gérés (à vérifier sur pages réelles) ; un module par écriture, donc la langue « automatique » exige d'essayer plusieurs modules.
- Alternatives étudiées : PaddleOCR via ONNX Runtime (open source, multilingue, plus lourd à intégrer) ; manga-ocr (japonais, très bon sur les bulles mais exige un détecteur de bulles dont la licence est à vérifier).

## Traduction — choix v1 : ML Kit Translate (hors ligne après téléchargement par langue)
- Gratuit, sans clé ni compte, modèles ~30 Mo par langue. Passe par l'anglais pour ja/ko/zh → fr (qualité moyenne, à évaluer).
- Chinois simplifié/traditionnel : probablement un seul modèle « zh » (à vérifier).
- Option qualité v2 : LLM local via LiteRT-LM (plusieurs Go, licence du modèle à vérifier, RAM élevée) — téléchargement explicite uniquement, derrière la même interface `TranslationEngine`.

## Affichage — overlay dans la page WebView
JavaScript injecté : liste les <img>, envoie leurs URL et rectangles ; l'application télécharge l'image avec les mêmes cookies/Referer que la page, lance OCR + traduction, puis le JS dessine les blocs en positionnement absolu dans le document (suit donc défilement et zoom). MutationObserver pour les images chargées progressivement. Aucune permission de superposition système.

## Nettoyage du texte
- Mode rapide : aplat de la couleur de fond échantillonnée sur le contour du bloc.
- Mode amélioré (à faire) : inpainting classique (Telea/OpenCV, Apache-2.0) ; les modèles d'inpainting neuronaux sont écartés en v1 (taille, mémoire, temps).
