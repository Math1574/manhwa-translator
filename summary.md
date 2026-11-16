# Rapport de test automatique

## en_gemma_ctrl
- durée 65s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · EN→FR
- note : gemma prêt : E2B=true E4B=false
- temps moyen par image : 46.1s (max 51.2s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 376 ms
- durées moyennes par image (3) : obtention 376 ms · lecture du texte 9762 ms · traduction 22500 ms · affichage 2374 ms
- détail de la lecture (dernière image) : ML Kit 4931 ms · Paddle 7011 ms · détecteur 4161 ms · total 7012 ms (en parallèle)
- mémoire de l'application (PSS) : 3049 Mo
- lecture Paddle :  [y=0 langue=en boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=3 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=4]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | Et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm, probablement pas assez fort. |
| ha... perfect. | Ha... parfait. |
| NO! Don't even think about it. | Non ! N'y pense même pas. |
| LASH | FOUET |
| Happy?! | Heureux ?! |

## ja_gemma_1
- durée 69s · images 0 · traitées 0 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · JA→FR
- note : gemma prêt : E2B=true E4B=false
- mémoire de l'application (PSS) : 792 Mo
- OCR : 0 lignes (0 utilisées, 0 écartées) · bulles affichées 0 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|

## ja_gemma_2
- durée 65s · images 0 · traitées 0 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · JA→FR
- note : gemma prêt : E2B=true E4B=false
- mémoire de l'application (PSS) : 698 Mo
- OCR : 0 lignes (0 utilisées, 0 écartées) · bulles affichées 0 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|

## ja_gemma_nopaddle
- durée 46s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur GEMMA_E2B · JA→FR
- note : gemma prêt : E2B=true E4B=false
- temps moyen par image : 27.7s (max 32.0s)
- rendu : 1/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 3 · durée moyenne d'obtention 540 ms
- durées moyennes par image (3) : obtention 540 ms · lecture du texte 1031 ms · traduction 22876 ms · affichage 2034 ms
- mémoire de l'application (PSS) : 2380 Mo
- OCR : 14 lignes (12 utilisées, 2 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| これで。出来上がり | Voilà. C'est fini. |
| う一ん足りなかったかも | Peut-être qu'il manquait un peu. |
| さん | Monsieur. |
| うん. 、カンペキ | Ouais. C'est parfait. |
| ダメ! そんなことしちゃだめなんだから | Non ! Tu ne dois pas faire ça ! |
| これで満足?! | Ça te satisfait ?! |

## ja_mlkit_nomodel
- durée 65s · images 0 · traitées 0 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : gemma prêt : E2B=true E4B=false
- mémoire de l'application (PSS) : 251 Mo
- OCR : 0 lignes (0 utilisées, 0 écartées) · bulles affichées 0 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|


## Bloqueur de publicités (émulateur)

(scénario non exécuté ou sans rapport)

## Interface (émulateur)

(scénario non exécuté)

# Problèmes détectés
- ja_gemma_1: aucune image traitée
- ja_gemma_2: aucune image traitée
- ja_mlkit_nomodel: aucune image traitée
- bloqueur : pas de rapport