# Licences et crédits des composants embarqués ou téléchargés

| Composant | Rôle | Licence |
|---|---|---|
| ML Kit (reconnaissance de texte, traduction) | lecture et traduction de repli | conditions Google ML Kit |
| PaddleOCR PP-OCRv4 (détection), PP-OCRv5 (anglais, coréen, unifié zh/ja/en) en ONNX | lecture du texte | Apache-2.0 |
| ONNX Runtime | exécution des modèles de lecture | MIT |
| Gemma 4 E2B / E4B (LiteRT-LM) | traduction locale | Apache-2.0 |
| Bibliothèque de 143 variantes de polices (Comic Neue, Bangers, Boogaloo, Chewy, Fredoka, Baloo 2, Nunito, Recursive, Patrick Hand, Kalam, Caveat, Anton, Bebas Neue, Oswald, Antonio, League Gothic, Archivo, Roboto Condensed, Noto Sans et Serif, Lora, Playfair Display, Cinzel, Courier Prime…) | rendu des traductions : la police qui ressemble le plus au lettrage d'origine est retenue | SIL Open Font License 1.1, ou Apache 2.0 (Chewy, Luckiest Guy, Permanent Marker, Rock Salt, Special Elite) ; détail et droits d'auteur de chaque fichier dans `app/src/main/assets/fonts/lib/LICENSES.txt` ; liste et génération : `tools/fontlib/` |
| Dictionnaire anglais ENABLE | réparation du texte lu | domaine public |
| Liste des 10 000 mots anglais les plus fréquents (google-10000-english) | rangs de fréquence | données dérivées du corpus Google Trillion Word |
| Liste de domaines publicitaires AdAway (https://github.com/AdAway/adaway.github.io) | blocage des publicités | Creative Commons Attribution 3.0 (auteurs : Kicelo, Dominik Schuermann et contributeurs) ; domaines supplémentaires ajoutés par ce projet |

Évalués puis écartés (pas dans l'application) : MI-GAN (MIT) pour l'effacement sur dessin, détection PP-OCRv5 mobile.
