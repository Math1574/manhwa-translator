# État du projet (24 octobre 2026)

## Vérifié automatiquement (émulateur Android sur GitHub Actions)
- L'application démarre, charge un site, collecte les images (y compris chargement progressif et images protégées par Referer + cookie).
- OCR ML Kit : texte propre lu à 100 % en anglais, coréen, chinois sur pages synthétiques ; japonais vertical non lu.
- Traduction Gemma 4 E2B et E4B (LiteRT-LM) : fonctionne, contexte de chapitre, glossaire ; ~22 s/page (E2B) et ~34 s/page (E4B) sur CPU d'émulateur.
- Rendu : effacement du texte (aplat dans les bulles, reconstruction sur les dessins), polices proches du lettrage, aucun débordement ni chevauchement mesuré.
- Plein écran : barres masquées, bouton discret qui réapparaît après trois appuis rapides.

## Non vérifié
- Vitesse et mémoire de Gemma sur un vrai téléphone, GPU.
- Sites de lecture réels (chargement, protections, structure de page propre à chaque site).
- Qualité de lecture sur lettrages stylisés réels, texte vertical.


## Mise à jour de novembre 2026
- Lecture : PP-OCRv5 (anglais en concurrent de l'ancien lecteur, coréen, japonais horizontal), détection PP-OCRv4 conservée.
- Traduction : Gemma 4 (E2B/E4B) avec décodage spéculatif et test CPU/GPU ; Hy-MT2 évalué et écarté (moins bon que Gemma sur le corpus maison).
- Navigateur : blocage des publicités (liste AdAway, CC BY 3.0), bibliothèque (favoris, historique, reprise de lecture par œuvre).
- Écarté après mesure : MI-GAN (gain négligeable), détection PP-OCRv5 mobile (moins bon rappel).
- Reste à faire : japonais vertical (manga-ocr), détecteur de bulles, onglets multiples, gestion des chapitres, NPU.

## Mise à jour intégrée et APK allégé
- Clé de signature fixe (brouillon privé du dépôt, créée par le workflow « Cle de signature ») : plus de désinstallation entre deux versions.
- APK du téléphone en arm64 seul ; lecteur de japonais vertical en module facultatif (Release `models-ja-v1`). La compilation pour l'émulateur reste en x86_64 avec ce lecteur intégré, pour que les tests le couvrent.
- Réglages : carte de mise à jour (vérification, nouveautés, téléchargement reprenable et vérifié, installation) ; les nouveautés sont le message de la modification publiée.
- Non vérifié par le développeur : l'installation sur un vrai téléphone (autorisation « Installer des applications inconnues », écran d'installation du constructeur) et l'APK arm64 allégé (les tests en ligne tournent en x86_64).
