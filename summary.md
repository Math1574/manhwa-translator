# Rapport de test automatique

## en_gemma_ctrl
- durée 69s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · EN→FR
- note : gemma prêt : E2B=true E4B=false
- temps moyen par image : 48.8s (max 53.8s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 636 ms
- durées moyennes par image (3) : obtention 636 ms · lecture du texte 10700 ms · traduction 22775 ms · affichage 2715 ms
- détail de la lecture (dernière image) : ML Kit 3376 ms · Paddle 7685 ms · détecteur 6321 ms · total 7687 ms (en parallèle)
- mémoire de l'application (PSS) : 3032 Mo
- lecture Paddle :  [y=0 langue=en boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=3 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=4]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | Et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm, probablement pas assez fort. |
| ha... perfect. | Ah... parfait. |
| NO! Don't even think about it. | Non ! N'y pense même pas. |
| LASH | FOUET |
| Happy?! | Content ?! |

## ja_gemma_1
- durée 106s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · JA→FR
- note : gemma prêt : E2B=true E4B=false
- temps moyen par image : 85.9s (max 89.6s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 3 · durée moyenne d'obtention 410 ms
- durées moyennes par image (3) : obtention 410 ms · lecture du texte 17570 ms · traduction 46445 ms · affichage 2803 ms
- détail de la lecture (dernière image) : ML Kit 3419 ms · Paddle 18531 ms · détecteur 7290 ms · total 18543 ms (en parallèle)
- mémoire de l'application (PSS) : 3190 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=ja boîtes=3 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=1 texte_bulle=2 texte_libre=4]
- OCR : 20 lignes (14 utilisées, 6 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| …これで出来上がり | Voilà le résultat |
| は5は5 | Le cinq est cinq |
| .う-ん足りなかったかも | Hmm, il me manque peut-être quelque chose |
| どはどは | Do, do |
| さん | Trois |
| うん… カンペキ | Hmm... parfait |
| ダメ! そんなことしちゃだめなんだから | Non ! Tu ne peux pas faire ça |
| これで満足?! | C'est ça te satisfait ?! |

## ja_gemma_2
- durée 95s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · JA→FR
- note : gemma prêt : E2B=true E4B=false
- temps moyen par image : 76.8s (max 80.1s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 3 · durée moyenne d'obtention 222 ms
- durées moyennes par image (3) : obtention 222 ms · lecture du texte 15089 ms · traduction 45027 ms · affichage 3014 ms
- détail de la lecture (dernière image) : ML Kit 3573 ms · Paddle 17273 ms · détecteur 8305 ms · total 17275 ms (en parallèle)
- mémoire de l'application (PSS) : 3057 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=ja boîtes=3 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=1 texte_bulle=2 texte_libre=4]
- OCR : 20 lignes (14 utilisées, 6 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| …これで出来上がり | Voilà le résultat |
| は5は5 | Le cinq est cinq |
| .う-ん足りなかったかも | Hmm, il me manque peut-être quelque chose |
| どはどは | Do, do |
| さん | Trois |
| うん… カンペキ | Hmm... parfait |
| ダメ! そんなことしちゃだめなんだから | Non ! Tu ne peux pas faire ça |
| これで満足?! | C'est ça te satisfait ?! |

## ja_gemma_nopaddle
- durée 47s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · JA→FR
- note : gemma prêt : E2B=true E4B=false
- temps moyen par image : 27.9s (max 31.7s)
- rendu : 1/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 3 · durée moyenne d'obtention 619 ms
- durées moyennes par image (3) : obtention 619 ms · lecture du texte 1055 ms · traduction 22361 ms · affichage 2481 ms
- mémoire de l'application (PSS) : 2373 Mo
- OCR : 14 lignes (12 utilisées, 2 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| これで。出来上がり | Voilà. C'est fait. |
| う一ん足りなかったかも | Peut-être qu'il manquait un peu. |
| さん | Monsieur. |
| うん. 、カンペキ | Ouais. Parfait. |
| ダメ! そんなことしちゃだめなんだから | Non ! Tu ne peux pas faire ça ! |
| これで満足?! | Ça te satisfait ?! |

## ja_mlkit_2
- durée 22s · images 1 · traitées 0 · échecs 1 · sans texte 0 · moteur MLKIT · JA→FR
- dernière erreur : `translation : Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles`
- note : gemma prêt : E2B=true E4B=false
- origine des octets des images : page × 1 · durée moyenne d'obtention 364 ms
- mémoire de l'application (PSS) : 925 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=9 colonnes=0 manga(dispo=-, lues=0) ]
- OCR : 9 lignes (8 utilisées, 1 écartées) · bulles affichées 0 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|

## ja_mlkit_3
- durée 25s · images 1 · traitées 0 · échecs 1 · sans texte 0 · moteur MLKIT · JA→FR
- dernière erreur : `translation : Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles`
- note : gemma prêt : E2B=true E4B=false
- origine des octets des images : page × 1 · durée moyenne d'obtention 222 ms
- mémoire de l'application (PSS) : 976 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=9 colonnes=0 manga(dispo=-, lues=0) ]
- OCR : 9 lignes (8 utilisées, 1 écartées) · bulles affichées 0 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|

## ja_mlkit_nomodel
- durée 23s · images 1 · traitées 0 · échecs 1 · sans texte 0 · moteur MLKIT · JA→FR
- dernière erreur : `translation : Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles`
- note : gemma prêt : E2B=true E4B=false
- origine des octets des images : page × 1 · durée moyenne d'obtention 609 ms
- mémoire de l'application (PSS) : 924 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=9 colonnes=0 manga(dispo=-, lues=0) ]
- OCR : 9 lignes (8 utilisées, 1 écartées) · bulles affichées 0 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|


## Bloqueur de publicités (émulateur)

(scénario non exécuté ou sans rapport)

## Interface (émulateur)

(scénario non exécuté)

# Problèmes détectés
- ja_mlkit_2: translation : Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles
- ja_mlkit_2: image en échec (translation: Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles)
- ja_mlkit_3: translation : Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles
- ja_mlkit_3: image en échec (translation: Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles)
- ja_mlkit_nomodel: translation : Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles
- ja_mlkit_nomodel: image en échec (translation: Modèle de traduction « ja » absent : téléchargez-le dans l'onglet Modèles)
- bloqueur : pas de rapport