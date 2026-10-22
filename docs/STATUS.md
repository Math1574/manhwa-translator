# État réel du projet (22 octobre 2026)

## Fait et vérifié (exécuté dans cet environnement)
Module `core` (Kotlin pur, sans Android) : 44 tests, 44 réussis, compilés avec kotlinc 2.0.21 / JDK 21.
- Regroupement des lignes OCR en bulles (horizontal, vertical CJK, dédoublonnage, filtrage du bruit)
- Mise en page de l'overlay (reste dans l'image, taille de texte réglable, anti-chevauchement)
- Glossaire local + corrections mémorisées
- Cache LRU mémoire + disque (corruption gérée, vidage, limite de taille)
- Pipeline OCR → traduction avec repli par bloc, cas « aucune bulle », erreurs par étape
- File d'attente séquentielle (priorité, doublons, annulation, erreur isolée)
- Téléchargement de modèles : confirmation des gros fichiers, espace disque, SHA-256, reprise (Range),
  coupure réseau, HTTP 404 — testé contre un vrai serveur HTTP local
- Paramètres : sérialisation et bornes

## NON fait
- Module `:app` (Android, Compose, WebView, bouton flottant, écrans, injection JS de l'overlay)
- Intégration OCR (ML Kit) et traduction (ML Kit Translate) : seules les interfaces `OcrEngine` / `TranslationEngine` existent
- Inpainting (mode amélioré)
- APK : impossible à compiler ici (accès à Gradle, Google Maven et au SDK Android bloqué) ; prévu via GitHub Actions
- Aucun test sur émulateur ou téléphone, aucun test linguistique réel, aucun test hors ligne réel

## Pourquoi pas d'APK ici
Le réseau de cet environnement n'autorise pas services.gradle.org, dl.google.com ni Maven Central.
Le code Android ne peut donc pas être compilé ici ; il le sera dans GitHub Actions.
