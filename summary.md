# Rapport de test automatique

## adblock_mlkit
- durée 31s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 16.6s (max 16.6s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w900-d62×6 ; classes : condensed×6
- origine des octets des images : page × 1 · durée moyenne d'obtention 209 ms
- durées moyennes par image (1) : obtention 209 ms · lecture du texte 9929 ms · traduction 308 ms · affichage 5312 ms
- détail de la lecture (dernière image) : ML Kit 3713 ms · Paddle 9895 ms · détecteur 7100 ms · total 9896 ms (en parallèle)
- blocage des publicités : 5 requêtes ou redirections bloquées · adresse finale http://10.0.2.2:8000/adtest.html
- mémoire de l'application (PSS) : 646 Mo
- lecture Paddle :  [y=0 langue=en boîtes=24 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=7 texte_bulle=6 texte_libre=2]
- OCR : 22 lignes (21 utilisées, 1 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| PLEASE... TAKE ME AS YOUR DISCIPLE! | PLAÎT ... PRENDS-MOI COMME DISCIPLE! |
| I CAN'T BELIEVE YOU CAME ALL THE WAY HERE. | JE NE PEUX PAS CROIRE QUE TU VIENNES TOUT LE CHEMIN ICI. |
| WHO ARE YOU? WHAT DO YOU WANT FROM ME? | QUI ES-TU? QU'ATTENDS-TU DE MOI? |
| THE MASTER WILL NOT SEE ANYONE TODAY. | LE MAÎTRE NE VERRA PERSONNE AUJOURD'HUI. |
| DO I HAVE NO CHOICE BUT TO KILL HIM? | EST-CE QUE JE N'AI PAS D'AUTRE CHOIX QUE DE LE TUER? |
| NO CHOICE BUT TO KILL HIM? | PAS D'AUTRE CHOIX QUE DE LE TUER? |

## pc_en_batch_mlkit
- durée 45s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 23.9s (max 30.1s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 435 ms
- durées moyennes par image (3) : obtention 435 ms · lecture du texte 8699 ms · traduction 370 ms · affichage 2587 ms
- détail de la lecture (dernière image) : ML Kit 2691 ms · Paddle 4221 ms · détecteur 3317 ms · total 4221 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 974 Mo
- lecture Paddle :  [y=0 langue=en boîtes=9 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=0]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| Happy?! | Heureux ?! |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |

## pc_en_lazy_mlkit
- durée 43s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 24.0s (max 27.5s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 159 ms
- durées moyennes par image (3) : obtention 159 ms · lecture du texte 8583 ms · traduction 395 ms · affichage 3635 ms
- détail de la lecture (dernière image) : ML Kit 1385 ms · Paddle 3976 ms · détecteur 3853 ms · total 4000 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 955 Mo
- lecture Paddle :  [y=0 langue=en boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=3 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=4]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |
| Happy?! | Heureux ?! |

## pc_en_long_mlkit
- durée 207s · images 31 · traitées 31 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 23.0s (max 34.0s)
- rendu : 62/97 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : comicneue-bold×65, boogaloo×20, caveatbrush×10, chewy×2 ; classes : comic×87, brush×10
- origine des octets des images : page × 31 · durée moyenne d'obtention 184 ms
- durées moyennes par image (31) : obtention 184 ms · lecture du texte 5922 ms · traduction 643 ms · affichage 3147 ms
- détail de la lecture (dernière image) : ML Kit 5380 ms · Paddle 5824 ms · détecteur 5377 ms · total 5824 ms (en parallèle)
- long chapitre : durée moyenne des 12 premières 24.0s, des 12 dernières 22.0s
- débit : 9.0 images par minute
- traductions regroupées avec d'autres pages : 0/31 images
- mémoire de l'application (PSS) : 994 Mo
- lecture Paddle :  [y=0 langue=en boîtes=14 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=6 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=4 texte_bulle=4 texte_libre=0]
- OCR : 416 lignes (250 utilisées, 166 écartées) · bulles affichées 97 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| Oh shoot, I fell asleep with the window open, again... | Oh tirer, je me suis endormi avec la fenêtre ouverte, encore une fois ... |
| ..it's so windy... | .. c'est tellement venteux ... |
| ...and why can I see Komona through the window? | ... Et pourquoi puis-je voir Komona à travers la fenêtre? |
| The Potion Contest! | Le concours de potion! |
| I must've... must've accidentally fallen asleep! | J'ai dû ... Doit être endormi accidentellement! |
| Where am I ?!? | Où suis-je?!? |
| Happy?! | Heureux ?! |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |
| Carrot! You're so cute to have thought of bringing me to the Contest! | Carotte! Tu es si mignon d'avoir pensé à m'apporter au concours! |
| Fan-tas-tic! | Fan-tas-TIC! |

## pc_en_mlkit
- durée 41s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3005 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 21.4s (max 27.2s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 378 ms
- durées moyennes par image (3) : obtention 378 ms · lecture du texte 7942 ms · traduction 399 ms · affichage 2233 ms
- détail de la lecture (dernière image) : ML Kit 1152 ms · Paddle 3735 ms · détecteur 3064 ms · total 3736 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 967 Mo
- lecture Paddle :  [y=0 langue=en boîtes=9 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=0]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| Happy?! | Heureux ?! |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |

## pc_en_once_mlkit
- durée 45s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 24.8s (max 31.5s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 550 ms
- durées moyennes par image (3) : obtention 550 ms · lecture du texte 9182 ms · traduction 345 ms · affichage 2776 ms
- détail de la lecture (dernière image) : ML Kit 3067 ms · Paddle 5154 ms · détecteur 4008 ms · total 5156 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 973 Mo
- lecture Paddle :  [y=0 langue=en boîtes=9 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=0]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| Happy?! | Heureux ?! |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |

## pc_en_ref_mlkit
- durée 45s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 24.2s (max 31.5s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 549 ms
- durées moyennes par image (3) : obtention 549 ms · lecture du texte 9200 ms · traduction 412 ms · affichage 2541 ms
- détail de la lecture (dernière image) : ML Kit 5258 ms · Paddle 5861 ms · détecteur 4421 ms · total 5861 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 972 Mo
- lecture Paddle :  [y=0 langue=en boîtes=9 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=0]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| Happy?! | Heureux ?! |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |

## pc_en_strict_mlkit
- durée 43s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 21.7s (max 28.5s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : réseau × 3 · durée moyenne d'obtention 269 ms
- durées moyennes par image (3) : obtention 269 ms · lecture du texte 8351 ms · traduction 364 ms · affichage 2265 ms
- détail de la lecture (dernière image) : ML Kit 3857 ms · Paddle 5191 ms · détecteur 4936 ms · total 5192 ms (en parallèle)
- lecture dans la WebView impossible, motif : lecture par la WebView coupée (test)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 983 Mo
- lecture Paddle :  [y=0 langue=en boîtes=9 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=0]
- OCR : 25 lignes (12 utilisées, 13 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| Happy?! | Heureux ?! |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |

## pc_en_toggle_mlkit
- durée 110s · images 12 · traitées 12 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- note : bascule Original puis Traduit à 7 s (total/traitées/échecs avant : 12/0/0)
- temps moyen par image : 19.4s (max 26.9s)
- rendu : 24/38 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : comicneue-bold×26, boogaloo×6, caveatbrush×4, chewy×2 ; classes : comic×34, brush×4
- origine des octets des images : page × 16 · durée moyenne d'obtention 268 ms
- durées moyennes par image (16) : obtention 268 ms · lecture du texte 5617 ms · traduction 606 ms · affichage 2705 ms
- détail de la lecture (dernière image) : ML Kit 4107 ms · Paddle 5918 ms · détecteur 4721 ms · total 5920 ms (en parallèle)
- bascule Original/Traduit : 16 images traitées sur 12 repérées ; ['bascule Original puis Traduit à 7 s (total/traitées/échecs avant : 12/0/0)']
- traductions regroupées avec d'autres pages : 0/16 images
- mémoire de l'application (PSS) : 988 Mo
- lecture Paddle :  [y=0 langue=en boîtes=8 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=12 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=1]
- OCR : 199 lignes (124 utilisées, 75 écartées) · bulles affichées 38 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| Oh shoot, I fell asleep with the window open, again... | Oh tirer, je me suis endormi avec la fenêtre ouverte, encore une fois ... |
| ..it's so windy... | .. c'est tellement venteux ... |
| ...and why can I see Komona through the window? | ... Et pourquoi puis-je voir Komona à travers la fenêtre? |
| The Potion Contest! | Le concours de potion! |
| I must've... must've accidentally fallen asleep! | J'ai dû ... Doit être endormi accidentellement! |
| Where am I ?!? | Où suis-je?!? |
| Happy?! | Heureux ?! |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| LASH | CILS |
| .and the last touch. | . et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |

## pc_ja_mlkit
- durée 48s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (3001 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 25.2s (max 32.8s)
- rendu : 5/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 3 · durée moyenne d'obtention 231 ms
- durées moyennes par image (3) : obtention 231 ms · lecture du texte 10219 ms · traduction 777 ms · affichage 2687 ms
- détail de la lecture (dernière image) : ML Kit 4737 ms · Paddle 9840 ms · détecteur 6661 ms · total 9842 ms (en parallèle)
- mémoire de l'application (PSS) : 972 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=ja boîtes=3 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=1 texte_bulle=2 texte_libre=4]
- OCR : 20 lignes (14 utilisées, 6 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| …これで出来上がり | ... c'est fait |
| は5は5 | 5 est 5 |
| .う-ん足りなかったかも | Hmmm, ce n'était pas assez |
| どはどは | Comment |
| さん | San |
| うん… カンペキ | Ouais ... Campeki |
| ダメ! そんなことしちゃだめなんだから | Pas bien! Parce que ce n'est pas une telle chose |
| これで満足?! | Est-ce satisfait ?! |

## préparation
- modèle fr : ready (3000 ms)
- modèle ja : ready (1500 ms)
- modèle ko : ready (1501 ms)
- modèle zh : ready (1500 ms)
- gemma prêt : E2B=false E4B=false

## seam_mlkit
- durée 43s · images 4 · traitées 4 · échecs 0 · sans texte 2 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 28.8s (max 29.4s)
- rendu : 2/2 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : baloo2-w700×1, archivo-w700-d100×1 ; classes : comic×1, sans×1
- origine des octets des images : page × 4 · durée moyenne d'obtention 173 ms
- durées moyennes par image (2) : obtention 174 ms · lecture du texte 6031 ms · traduction 484 ms · affichage 3091 ms
- détail de la lecture (dernière image) : ML Kit 2944 ms · Paddle 4643 ms · détecteur 3601 ms · total 4644 ms (en parallèle)
- jonction A : 1 bloc(s), dont 1 lu(s) en entier ; image du dessus recouverte : oui
- jonction B : 1 bloc(s), dont 1 lu(s) en entier ; image du dessus recouverte : oui
- textes coupés lus en entier : 2/2 (avec la jonction)
- mémoire de l'application (PSS) : 893 Mo
- lecture Paddle :  [y=0 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 : aucune boîte] [zones : bulles=1 texte_bulle=1 texte_libre=0]
- OCR : 14 lignes (8 utilisées, 6 écartées) · bulles affichées 2 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| YOU HAVE COME SO FAR ALREADY, BUT THE JOURNEY HAS JUST BEGUN. | VOUS ÊTES DÉJÀ VENU DÉJÀ, MAIS LE VOYAGE VIENT DE COMMENCER. |
| I WILL NEVER FORGIVE YOU FOR WHAT YOU DID TO HER. | JE NE TE PARDONNERAI JAMAIS DE CE QUE TU L'AS FAIT. |

## seam_off_mlkit
- durée 47s · images 4 · traitées 4 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 24.4s (max 32.8s)
- rendu : 4/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×2, baloo2-w700×2 ; classes : sans×2, comic×2
- origine des octets des images : page × 4 · durée moyenne d'obtention 171 ms
- durées moyennes par image (4) : obtention 171 ms · lecture du texte 7810 ms · traduction 435 ms · affichage 2821 ms
- détail de la lecture (dernière image) : ML Kit 1251 ms · Paddle 6603 ms · détecteur 4709 ms · total 6613 ms (en parallèle)
- jonction A : 2 bloc(s), dont 0 lu(s) en entier ; image du dessus recouverte : non
- jonction B : 2 bloc(s), dont 0 lu(s) en entier ; image du dessus recouverte : non
- textes coupés lus en entier : 0/2 (sans la jonction)
- mémoire de l'application (PSS) : 880 Mo
- lecture Paddle :  [y=0 : aucune boîte] [y=1184 langue=en boîtes=6 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=1 texte_libre=0]
- OCR : 11 lignes (11 utilisées, 0 écartées) · bulles affichées 4 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| NGIvL iUU i WHAT YOU DID TO HER. | NGIVL IUU I CE QUE TU AS FAIT À ELLE. |
| BUT THE JOURNEY HAS JUST BEGUN. | MAIS LE VOYAGE VIENT DE COMMENCER. |
| I WILL NEVER DGIIE | JE NE VAIS JAMAIS DGIIE |
| YOU HAVE COME SO FAR ALREADY, | VOUS ÊTES DÉJÀ VENU DÉJÀ, |

## synth_atypical_combined
- durée 49s · images 6 · traitées 6 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3003 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 22.9s (max 35.0s)
- rendu : 3/10 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : baloo2-w700×3, archivo-w700-d100×3, archivo-w900-d62×3, chewy×1 ; classes : comic×4, sans×3, condensed×3
- filigrane contre une bulle : 1 bloc(s) traduit(s)
- bulle ouverte : texte dans l ellipse
- origine des octets des images : page × 6 · durée moyenne d'obtention 192 ms
- durées moyennes par image (6) : obtention 192 ms · lecture du texte 5098 ms · traduction 696 ms · affichage 6652 ms
- détail de la lecture (dernière image) : ML Kit 1827 ms · Paddle 3184 ms · détecteur 2221 ms · total 3184 ms (en parallèle)
- mémoire de l'application (PSS) : 732 Mo
- lecture Paddle :  [y=0 langue=en boîtes=8 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=4 texte_libre=1]
- OCR : 69 lignes (40 utilisées, 29 écartées) · bulles affichées 10 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| SHUT UP, THAT SOUNDS GROSS. | TAIS-TOI, ÇA SONNE BRUT. |
| A RESTRICTION ON YOUR WANT ME TO PUT MOUTH? | UNE RESTRICTION SUR VOTRE VEUVE-TOI DE ME METTRE LA BOUCHE? |
| WHATEVER THE CASE IS, I MUST GATHER MORE SOULS. | QUOI QU'IL EN SOIT, JE DOIS RASSEMBLER PLUS D'ÂMES. |
| IF I KEEP DOING THAT, THEN ONE DAY... | SI JE CONTINUE À FAIRE ÇA, ALORS UN JOUR ... |
| YES, SIR! I'LL SHUT MY MOUTH BY PUTTING A BOULDER | OUI, MONSIEUR! JE VAIS FERMER LA BOUCHE EN METTANT UN ROCHER |
| SHUT UP ALREADY, YOU PLEASE JUST CRAZY BASTARD... | TAIS-TOI DÉJÀ, TU VEUX JUSTE JUSTE FOU BÂTARD ... |
| IN THE WAKE OF THEIR DECEITFUL TEMPTATIONS | À LA SUITE DE LEURS TENTATIONS TROMPEUSES |
| THE WORLD'S BALANCE WAS TEETERING PRECARIOUSLY | LE SOLDE DU MONDE EN TRAIN DE SE FAIRE VACRIR PRÉCISÉMENT |
| ALL I DID WAS WATCH I OVER THE STORAGE. HAHA. | TOUT CE QUE J'AI FAIT ÉTAIT REGARDER I SUR LE STOCKAGE. HAHA. |
| ALL I DID WAS WATCH OVER THE STORAGE. HAHA. | TOUT CE QUE J'AI FAIT ÉTAIT SURVEILLE LE STOCKAGE. HAHA. |
| THAT DOESN'T SEEM TO BE THE CASE. | CELA NE SEMBLE PAS ÊTRE LE CAS. |

## synth_en_caps_mlkit
- durée 31s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 16.4s (max 16.4s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w900-d62×6 ; classes : condensed×6
- origine des octets des images : page × 1 · durée moyenne d'obtention 219 ms
- durées moyennes par image (1) : obtention 219 ms · lecture du texte 9633 ms · traduction 312 ms · affichage 5437 ms
- détail de la lecture (dernière image) : ML Kit 2930 ms · Paddle 9600 ms · détecteur 6768 ms · total 9602 ms (en parallèle)
- mémoire de l'application (PSS) : 644 Mo
- lecture Paddle :  [y=0 langue=en boîtes=24 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=7 texte_bulle=6 texte_libre=2]
- OCR : 22 lignes (21 utilisées, 1 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: PLEASE... TAKE ME AS YOUR DISCIPLE! | 100 % | oui | PLAÎT ... PRENDS-MOI COMME DISCIPLE! |
| bubble: I CAN'T BELIEVE YOU CAME ALL THE WAY HERE. | 100 % | oui | JE NE PEUX PAS CROIRE QUE TU VIENNES TOUT LE CHEMIN ICI. |
| bubble: WHO ARE YOU? WHAT DO YOU WANT FROM ME? | 100 % | oui | QUI ES-TU? QU'ATTENDS-TU DE MOI? |
| bubble: THE MASTER WILL NOT SEE ANYONE TODAY. | 100 % | oui | LE MAÎTRE NE VERRA PERSONNE AUJOURD'HUI. |
| bubble: DO I HAVE NO CHOICE BUT TO KILL HIM? | 100 % | oui | EST-CE QUE JE N'AI PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: NO CHOICE BUT TO KILL HIM? | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| sfx: THUD! | 100 % | laissée telle quelle |  |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_en_fancy_combined
- durée 33s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 18.5s (max 18.5s)
- rendu : 2/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, chewy×2 ; classes : sans×3, comic×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 554 ms
- durées moyennes par image (1) : obtention 554 ms · lecture du texte 9224 ms · traduction 290 ms · affichage 8380 ms
- détail de la lecture (dernière image) : ML Kit 4338 ms · Paddle 9198 ms · détecteur 6965 ms · total 9199 ms (en parallèle)
- mémoire de l'application (PSS) : 654 Mo
- lecture Paddle :  [y=0 langue=en boîtes=16 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=3 texte_bulle=5 texte_libre=3]
- OCR : 15 lignes (14 utilisées, 1 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| free: NO CHOICE BUT | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: TO KILL HIM? | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| sfx: CRACK! | 80 % | laissée telle quelle |  |
| narration: THREE DAYS LATER, AT THE MOUNTAIN GATE. | 100 % | oui | TROIS JOURS PLUS TARD, À LA PORTE DE MONTAGNE. |
| bubble: I HEARD MANY PEOPLE WITH SEVERED MERIDIANS WERE GENIUSES. | 100 % | oui | J'AI ENTENDU DE NOMBREUSES PERSONNES SOUFFRANT DE MÉRIDIENS COUPÉS ÉTAIENT DES GÉNIES. |
| whisper: ...he is still alive. | 100 % | oui | ... il est toujours en vie. |
| free: HEAVENLY DEMON | 100 % | oui | DÉMON CÉLESTE |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_en_fancy_mlkitonly
- durée 23s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 9.5s (max 9.5s)
- rendu : 0/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, fredoka-w600-d100×2 ; classes : sans×3, comic×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 134 ms
- durées moyennes par image (1) : obtention 134 ms · lecture du texte 874 ms · traduction 294 ms · affichage 7345 ms
- mémoire de l'application (PSS) : 251 Mo
- OCR : 12 lignes (11 utilisées, 1 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| free: NO CHOICE BUT | 91 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: TO KILL HIM? | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| sfx: CRACK! | 80 % | laissée telle quelle |  |
| narration: THREE DAYS LATER, AT THE MOUNTAIN GATE. | 100 % | oui | TROIS JOURS PLUS TARD, À LA PORTE DE MONTAGNE. |
| bubble: I HEARD MANY PEOPLE WITH SEVERED MERIDIANS WERE GENIUSES. | 100 % | oui | J'AI ENTENDU DE NOMBREUSES PERSONNES SOUFFRANT DE MÉRIDIENS COUPÉS ÉTAIENT DES GÉNIES. |
| whisper: ...he is still alive. | 100 % | oui | Oodhe est toujours en vie. |
| free: HEAVENLY DEMON | 100 % | oui | DÉMON CÉLESTE |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_en_mixed_mlkit
- durée 29s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 15.0s (max 15.0s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : baloo2-w700×5, notosans-w700-d100×1 ; classes : comic×5, sans×1
- origine des octets des images : page × 1 · durée moyenne d'obtention 126 ms
- durées moyennes par image (1) : obtention 126 ms · lecture du texte 9302 ms · traduction 305 ms · affichage 4718 ms
- détail de la lecture (dernière image) : ML Kit 3599 ms · Paddle 9277 ms · détecteur 6161 ms · total 9277 ms (en parallèle)
- mémoire de l'application (PSS) : 627 Mo
- lecture Paddle :  [y=0 langue=en boîtes=21 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=6 texte_bulle=6 texte_libre=2]
- OCR : 18 lignes (17 utilisées, 1 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: Please... take me as your disciple! | 100 % | oui | Plaît ... Prends-moi comme disciple! |
| bubble: I can't believe you came all the way here. | 100 % | oui | Je ne peux pas croire que tu viennes tout le chemin ici. |
| bubble: Who are you? What do you want from me? | 100 % | oui | Qui es-tu? Qu'attends-tu de moi? |
| bubble: The master will not see anyone today. | 100 % | oui | Le maître ne verra personne aujourd'hui. |
| bubble: Do I have no choice but to kill him? | 100 % | oui | Est-ce que je n'ai pas d'autre choix que de le tuer? |
| free: You will regret this. | 59 % | oui | Vous le regretterez. |
| sfx: Boom! | 100 % | laissée telle quelle |  |

**Lecture OCR correcte : 6/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_en_variety_combined
- durée 31s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 16.4s (max 16.4s)
- rendu : 3/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×2, archivo-w900-d62×2 ; classes : sans×2, condensed×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 685 ms
- durées moyennes par image (1) : obtention 685 ms · lecture du texte 10262 ms · traduction 330 ms · affichage 4836 ms
- détail de la lecture (dernière image) : ML Kit 4108 ms · Paddle 10233 ms · détecteur 6301 ms · total 10237 ms (en parallèle)
- mémoire de l'application (PSS) : 653 Mo
- lecture Paddle :  [y=0 langue=en boîtes=14 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=5 texte_bulle=4 texte_libre=1]
- OCR : 14 lignes (14 utilisées, 0 écartées) · bulles affichées 4 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: I HEARD THAT MANY PEOPLE WITH SEVERED MERIDIANS WERE GENIUSES. | 100 % | oui | J'AI ENTENDU DIRE QUE BEAUCOUP DE GENS SOUFFRANT DE MÉRIDIENS COUPÉS ÉTAIENT DES GÉNIES. |
| dark: THERE ARE COUNTLESS PEOPLE WHO DESTROYED THEIR OWN SECTS. | 100 % | oui | IL Y A D'INNOMBRABLES PERSONNES QUI ONT DÉTRUIT LEURS PROPRES SECTES. |
| gradient: ALL I DID WAS WATCH OVER THE STORAGE. | 100 % | oui | TOUT CE QUE J'AI FAIT ÉTAIT SURVEILLE LE STOCKAGE. |
| free-white: HE THINKS I AM JUST A MARTIAL ARTIST HIDING MY ABILITIES... IS THAT SO? | 100 % | oui | IL PENSE QUE JE SUIS JUSTE UN ARTISTE MARTIAL CACHANT MES CAPACITÉS ... EST-CE QUE ÇA? |

**Lecture OCR correcte : 4/4 · éléments traités comme attendu (traduits, onomatopées laissées) : 4/4**

## synth_ja_vert_mlkit
- durée 32s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (3001 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 17.2s (max 17.2s)
- rendu : 4/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 185 ms
- durées moyennes par image (1) : obtention 185 ms · lecture du texte 13666 ms · traduction 471 ms · affichage 2057 ms
- détail de la lecture (dernière image) : ML Kit 2559 ms · Paddle 13661 ms · détecteur 5435 ms · total 13663 ms (en parallèle)
- mémoire de l'application (PSS) : 753 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=13 colonnes=9 manga(dispo=true, lues=9) ] [y=1184 langue=ja boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=4 texte_bulle=4 texte_libre=0]
- OCR : 10 lignes (10 utilisées, 0 écartées) · bulles affichées 4 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| vertical: お願いします…弟子にしてください！ | 73 % | oui | S'il vous plaît ... s'il vous plaît soyez un disciple! |
| vertical: ここまで来るとは信じられない。 | 57 % | oui | Je ne peux pas croire que ça viendra ici |
| vertical: お前は誰だ？何が望みだ？ | 50 % | oui | Qui es-tu? Quel est l'espoir? |
| vertical: 師匠は今日は誰とも会わない。 | 46 % | oui | Le maître rencontre également n'importe qui aujourd'hui |

**Lecture OCR correcte : 0/4 · éléments traités comme attendu (traduits, onomatopées laissées) : 4/4**

## synth_ko_mlkit
- durée 30s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · KO→FR
- note : modèle ko : ready (3000 ms)
- note : modèle fr : ready (1501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 14.3s (max 14.3s)
- rendu : 5/7 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 315 ms
- durées moyennes par image (1) : obtention 315 ms · lecture du texte 7594 ms · traduction 510 ms · affichage 5431 ms
- détail de la lecture (dernière image) : ML Kit 3619 ms · Paddle 7590 ms · détecteur 6074 ms · total 7591 ms (en parallèle)
- mémoire de l'application (PSS) : 646 Mo
- lecture Paddle :  [y=0 langue=ko boîtes=14 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=ko boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=6 texte_bulle=6 texte_libre=2]
- OCR : 14 lignes (14 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 제발... 저를 제자로 받아주세요! | 100 % | oui | S'il vous plaît, acceptez-moi comme disciple! |
| bubble: 여기까지 오시다니 믿을 수 없군요. | 100 % | oui | Je ne peux pas y croire. |
| bubble: 당신은 누구십니까? 저에게 무엇을 원하시나요? | 100 % | oui | Qui es-tu? Que veux-tu que je suis? |
| bubble: 스승님은 오늘 아무도 만나지 않으실 겁니다. | 100 % | oui | Maître ne rencontrera personne aujourd'hui. |
| bubble: 그를 죽일 수밖에 없는 건가? | 100 % | oui | Avez-vous de le tuer? |
| free: 후회하게 될 거다. | 100 % | oui | Le regret |
| free: 털썩 | 100 % | oui | Flumping |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_layouts_combined
- durée 65s · images 6 · traitées 6 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 28.3s (max 39.4s)
- rendu : 33/39 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×21, archivo-w900-d62×18 ; classes : sans×21, condensed×18
- regroupement : 39 blocs lus pour 42 blocs réels · fusions à tort 0 · scissions 0
- origine des octets des images : page × 6 · durée moyenne d'obtention 241 ms
- durées moyennes par image (6) : obtention 241 ms · lecture du texte 7692 ms · traduction 818 ms · affichage 4999 ms
- détail de la lecture (dernière image) : ML Kit 4569 ms · Paddle 6257 ms · détecteur 4945 ms · total 6259 ms (en parallèle)
- mémoire de l'application (PSS) : 859 Mo
- lecture Paddle :  [y=0 langue=en boîtes=15 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=10 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=0]
- OCR : 140 lignes (107 utilisées, 33 écartées) · bulles affichées 39 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| YOU CALL THAT A TECHNIQUE | VOUS APPELEZ CELA UNE TECHNIQUE |
| YOU CALL THAT A TECHNIQUE | VOUS APPELEZ CELA UNE TECHNIQUE |
| THIS IS THE END OF THE ROAD | C'EST LA FIN DE LA ROUTE |
| THEY WILL PAY-FOR WHAT THEY DID | ILS PAIERONT CE QU'ILS ONT FAIT |
| THE GATE WILL OPEN AT SUNSET | LA PORTE S'OUVRIRA AU COUCHER DU SOLEIL |
| THE MASTER WILL NOT SEE ANYONE TODAY | LE MAÎTRE NE VERRA PERSONNE AUJOURD'HUI |
| I NEVER THOUGHT I WOULD SEE YOU AGAIN | JE N'AI JAMAIS PENSÉ TE REVOIR |
| LET ME HANDLE THE REST | LAISSEZ-MOI GÉRER LE RESTE |
| HOW DID YOU FIND THIS PLACE | COMMENT AVEZ-VOUS TROUVÉ CET ENDROIT |
| WHAT DO YOU WANT FROM ME >WHAT DO YOU | QUE VEUX-TU DE MOI> QU'EST-CE QUE TU |
| WANT FROM ME | VEUX DE MOI |
| YOU CALL THAT A TECHNIQUE | VOUS APPELEZ CELA UNE TECHNIQUE |
| SOMEONE IS WATCHING US FROM THE ROOF | QUELQU'UN NOUS REGARDE DU TOIT |
| THEY WILL PAY FOR WHAT THEY DID | ILS PAIERONT POUR CE QU'ILS ONT FAIT |

## synth_zh_mlkit
- durée 26s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · ZH_HANS→FR
- note : modèle zh : ready (3004 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 11.8s (max 11.8s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 144 ms
- durées moyennes par image (1) : obtention 144 ms · lecture du texte 7227 ms · traduction 592 ms · affichage 2830 ms
- détail de la lecture (dernière image) : ML Kit 2489 ms · Paddle 7224 ms · détecteur 5843 ms · total 7225 ms (en parallèle)
- mémoire de l'application (PSS) : 630 Mo
- lecture Paddle :  [y=0 langue=zh boîtes=16 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=zh boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=5 texte_bulle=6 texte_libre=1]
- OCR : 12 lignes (12 utilisées, 0 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 求求你……收我为徒吧！ | 100 % | oui | Je vous prie de vous ... je vais me recevoir! |
| bubble: 没想到你竟然大老远跑到这里来。 | 100 % | oui | Je ne m'attendais pas à ce que vous veniez ici. |
| bubble: 你是谁？你想从我这里得到什么？ | 100 % | oui | Qui es-tu? Que veux-tu de me tirer de moi? |
| bubble: 师父今天不见任何人。 | 89 % | oui | Maître n'a rien vu aujourd'hui. |
| bubble: 难道我别无选择，只能杀了他吗？ | 100 % | oui | Je ne choisis pas, puis-je seulement le tuer? |
| free: 你会后悔的。 | 100 % | oui | Vous le regretterez. |
| free: 咚！ | 100 % | oui | 咚! |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**


## Bloqueur de publicités (émulateur)

| vérification | résultat |
|---|---|
| moteur : les règles de test sont chargées | oui |
| réseau : le script publicitaire n'est pas exécuté | oui |
| réseau : le cadre publicitaire est vide | oui |
| réseau : la bannière d'une régie tierce est remplacée par un pixel | oui |
| réseau : l'image d'une règle de domaine (cdn.test/ads/) est bloquée | oui |
| images : les images du chapitre servies par un CDN tiers s'affichent | oui |
| images : une règle générique ne bloque pas une image tierce | oui |
| images : l'image du site lui-même s'affiche | oui |
| pop-up : window.open par script est neutralisé | oui |
| redirection : le changement de site automatique est refusé | oui |
| superposition : un lien invisible plein écran est retiré au premier appui | oui |
| pop-up : window.open après un vrai geste est neutralisé | oui |
| liste : un lien vers une régie connue est refusé même touché | oui |
| lien : un vrai lien visible vers un autre site est suivi | oui |
| lien : un lien du site en nouvel onglet s'ouvre dans la page | oui |
| site exclu : bloqueur éteint, le script publicitaire s'exécute | oui |
| site exclu : on peut le réactiver | oui |

**Bloqueur : 17/17 vérifications réussies**

Requêtes tierces servies par le faux site (donc non bloquées) : http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png, http://friend.test/page.html, http://friend.test/favicon.ico, http://cdn.test/images/p1.png, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://adnet.test/banner.gif, http://cdn.test/ads/ad.png, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p1.png, http://adnet.test/ad.js, http://cdn.test/images/p2.png, http://adnet.test/frame.html, http://popunder.test/landing, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png, http://cdn.test/bannieres-generiques/ok.png

<details><summary>journal du bloqueur</summary>

```
Journal du bloqueur — 30 action(s)
  cadre : 4
  image : 8
  pop-up : 5
  redirection (liste) : 5
  requête : 2
  script : 4
  superposition : 2

06:29:49  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
06:29:48  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:48  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
06:29:48  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:48  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:41  requête  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:41  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
06:29:40  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:40  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
06:29:40  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:40  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:37  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
06:29:37  superposition  http://popunder.test/overlay  (page : 10.0.2.2)
06:29:35  redirection (liste)  http://popunder.test/landing  ←  ||popunder.test^  (page : 10.0.2.2)
06:29:35  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
06:29:34  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:34  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
06:29:34  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:34  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:31  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
06:29:29  pop-up  http://popunder.test/btn  (page : 10.0.2.2)
06:29:28  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
06:29:28  superposition  http://popunder.test/overlay  (page : 10.0.2.2)
06:29:23  redirection (liste)  http://popunder.test/landing  ←  ||popunder.test^  (page : 10.0.2.2)
06:29:23  requête  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:23  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
06:29:22  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:22  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
06:29:22  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
06:29:22  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)

```
</details>
- état de la page : "{\"adLoaded\":false,\"frameLoaded\":false,\"bannerReal\":false,\"adImgReal\":false,\"genericReal\":true,\"cdn1Real\":true,\"cdn2Real\":true,\"sameReal\":true,\"popupFake\":true}"

## Interface (émulateur)

| vérification | résultat |
|---|---|
| plein écran : rien d'affiché au repos | oui |
| plein écran : un appui ouvre le menu de lecture | oui |
| plein écran : le menu propose Original | Traduit et les chapitres | oui |
| plein écran : un second appui le referme | oui |
| réglages rapides : un appui long sur Original | Traduit les ouvre aussi en plein écran | oui |
| réglages rapides : le retour referme le panneau et reste en plein écran | oui |
| plein écran : le bouton du menu le quitte (onglets de retour) | oui |
| onglets : Lecture, Bibliothèque, Réglages | oui |
| bouton rond Original | Traduit présent hors plein écran | oui |
| suppression : un appui long sur une traduction propose de la corriger ou de la supprimer | oui |
| suppression : l'état de départ est sain (aucune traduction supprimée, au moins un bloc) | oui |
| suppression : le bandeau « Annuler » apparaît après la suppression | oui |
| suppression : la proposition « Corriger » disparaît avec la corbeille | oui |
| suppression : la traduction est supprimée (original rétabli, correction enregistrée) | oui |
| suppression : le bloc reste repérable (même nombre de blocs que avant) | oui |
| annulation : « Annuler » rétablit la traduction (plus de bloc supprimé, aucune correction) | oui |
| annulation : le bandeau se referme | oui |
| annulation : une traduction corrigée à la main est enregistrée (départ) | oui |
| annulation : la traduction corrigée peut elle aussi être supprimée | oui |
| annulation : la correction faite à la main avant la suppression est rétablie telle quelle | oui |
| annulation : les corrections de test sont retirées | oui |
| suppression : le bandeau se referme seul au bout de 3 s | oui |
| suppression : sans annulation, la suppression est définitive | oui |
| gomme : activée, un bouton « Gomme active · Terminer » est affiché | oui |
| gomme : un appui sur une traduction la supprime (bloc masqué, correction enregistrée) | oui |
| gomme : le bandeau « Traduction supprimée · Annuler » apparaît | oui |
| gomme : l'appui n'a pas masqué la barre d'adresse (il ne sert qu'à effacer) | oui |
| gomme : elle reste active après un appui | oui |
| gomme : un deuxième appui supprime une deuxième traduction (autre bloc) | oui |
| gomme : « Annuler » ne rétablit que la dernière suppression | oui |
| gomme : « Terminer » coupe la gomme | oui |
| gomme : le bouton « Gomme active » disparaît | oui |
| gomme : les corrections de test sont retirées | oui |
| retraduction ciblée : départ sain (aucun terme enregistré, des textes traduits) | oui |
| retraduction ciblée : une fenêtre montre le terme et, pour chaque texte concerné, la traduction avant et après | oui |
| retraduction ciblée : seuls les textes qui contiennent le terme sont retraduits (pas toute la page) | oui |
| retraduction ciblée : rien n'est enregistré avant la confirmation (aucun terme, traductions inchangées) | oui |
| retraduction ciblée : « Annuler » ferme la fenêtre sans rien enregistrer | oui |
| retraduction ciblée : « Enregistrer » ajoute le terme | oui |
| retraduction ciblée : le texte concerné est retraduit avec le terme, les autres textes ne changent pas | oui |
| retraduction ciblée : la fenêtre se referme après l'enregistrement | oui |
| retraduction ciblée : « Enregistrer » de l'éditeur avec un terme passe aussi par l'aperçu | oui |
| retraduction ciblée : depuis l'éditeur, le terme est ajouté et le texte concerné retraduit | oui |
| mise à jour à l'affichage : un terme modifié sans aperçu ne retraduit que le texte concerné (page reprise du cache) | oui |
| retraduction ciblée : les termes et corrections de test sont retirés | oui |
| textes écartés : activés, un bouton « Textes écartés · Terminer » est affiché | oui |
| textes écartés : au moins un texte laissé de côté est entouré (page reprise ou relue) | oui |
| textes écartés : un appui traduit le texte (correction enregistrée, cadre retiré) | oui |
| textes écartés : le bandeau « Texte traduit · Annuler » apparaît | oui |
| textes écartés : le mode reste actif après un appui | oui |
| textes écartés : « Annuler » retire la traduction et le cadre revient | oui |
| textes écartés : « Terminer » coupe le mode | oui |
| textes écartés : le bouton disparaît | oui |
| textes écartés : les corrections de test sont retirées | oui |
| ignorer : un appui long propose aussi « Ignorer ce texte… » (avec Corriger) | oui |
| ignorer : la fenêtre propose cette œuvre, toutes les œuvres ou toute l'image | oui |
| ignorer : le bandeau « Texte ignoré · Annuler » apparaît | oui |
| ignorer : le texte reste tel quel (règle enregistrée, bloc masqué, aucune correction) | oui |
| ignorer : « Annuler » retire la règle et rétablit la traduction | oui |
| ignorer partout : la règle est enregistrée et le texte reste tel quel | oui |
| ignorer : un appui long sur un texte ignoré propose de le rétablir (pas de « Corriger ») | oui |
| ignorer : « Rétablir » retire la règle et rend la traduction | oui |
| image ignorée : le bandeau « Image ignorée · Annuler » apparaît | oui |
| image ignorée : la règle est enregistrée et l'image n'est plus traduite | oui |
| image ignorée : « Annuler » rétablit l'image (règle retirée, page de nouveau traduite) | oui |
| image ignorée : la règle est de nouveau enregistrée et l'image n'est plus traduite | oui |
| image ignorée : un appui long sur l'image propose de la rétablir | oui |
| image ignorée : « Rétablir » retire la règle et la page est de nouveau traduite | oui |
| appui long réel : 0,45 s (l'ancienne durée) sur une traduction ne propose plus rien (il en faut 0,75) | oui |
| appui long réel : 1,4 s sur une traduction propose de la corriger ou de la supprimer | oui |
| appui long réel : faire défiler l'écran referme aussitôt la proposition | oui |
| appui long réel : 1,4 s sur une image sans traduction propose d'en ajouter une | oui |
| appui long réel : la proposition d'ajout vient aussi pour une page dont la mémoire a été vidée | oui |
| appui long réel : « Ajouter une traduction » ouvre l'éditeur même pour une page oubliée (préparée à la demande) | oui |
| réglages rapides : 0,45 s d'appui sur le bouton rond n'ouvre pas le panneau (il en faut un peu plus que l'appui long du système) | oui |
| réglages rapides : un appui long sur le bouton rond ouvre le panneau | oui |
| réglages rapides : langue, taille du texte, onomatopées et retraduction y sont | oui |
| réglages rapides : la gomme à traductions y est proposée | oui |
| réglages rapides : l'appui long ne bascule pas la traduction (même état du bouton avant et après) | oui |
| réglages rapides : le retour referme le panneau, la page reste affichée | oui |
| bibliothèque : l'œuvre est listée, sans carte « Continuer la lecture » (la reprise est sur l'accueil) | oui |
| bibliothèque : l'œuvre affiche l'illustration annoncée par la page de l'œuvre du site | oui |
| fiche d'une œuvre : l'illustration est aussi en tête de la fiche | oui |
| fiche d'une œuvre : liste des chapitres | oui |
| fiche d'une œuvre : page de l'œuvre et lecture proposées | oui |
| retour de la fiche vers la bibliothèque | oui |
| bibliothèque : trois œuvres de démonstration s'ajoutent (4 œuvres avec Test Series) | oui |
| bibliothèque : champ de recherche, pastilles de statut, tri et historique | oui |
| bibliothèque : par défaut, l'œuvre lue le plus récemment est en tête | oui |
| bibliothèque : une œuvre dont le site ne répond pas garde sa pastille (aucune illustration inventée) | oui |
| bibliothèque : le menu de tri propose trois façons de ranger | oui |
| bibliothèque : tri par titre (A → Z) | oui |
| bibliothèque : le tri choisi est retenu dans les réglages | oui |
| bibliothèque : tri par chapitres lus | oui |
| bibliothèque : retour au tri par date de lecture | oui |
| fiche d'une œuvre : choix du statut En cours / Terminée | oui |
| fiche d'une œuvre : « Terminée » est retenu et expliqué | oui |
| glossaire de l'œuvre : la page s'ouvre sur l'œuvre de la bibliothèque, pas sur celle de la page lue | oui |
| glossaire de l'œuvre : un terme ajouté ici est enregistré (une page de cette œuvre n'est pas ouverte : pas d'aperçu) | oui |
| glossaire de l'œuvre : le terme est listé comme propre à l'œuvre | oui |
| glossaire de l'œuvre : le terme se retire | oui |
| glossaire de l'œuvre : le retour ramène à la fiche de l'œuvre | oui |
| bibliothèque : l'œuvre terminée porte sa pastille, les pastilles se recomptent | oui |
| bibliothèque : le filtre « Terminées » ne montre que les œuvres terminées | oui |
| bibliothèque : le filtre « En cours » écarte les œuvres terminées | oui |
| historique : titres simples (œuvre et chapitre), ni adresse ni titre brut | oui |
| bibliothèque : la recherche ne garde que l'œuvre cherchée | oui |
| bibliothèque : une recherche sans résultat le dit | oui |
| réglages : la page « Textes et images ignorés » est proposée à l'accueil | oui |
| réglages : une carte par thème | oui |
| réglages rapides : la langue choisie dans le panneau est enregistrée (résumé de Réglages) | oui |
| réglages : champ de recherche, carte de mise à jour et numéro de version sur l'accueil | oui |
| réglages : la carte Traduction ouvre ses options (langues) | oui |
| réglages : choisir une langue dans la carte Traduction l'enregistre (résumé de Réglages) | oui |
| réglages : la carte Lecture ouvre ses options (bouton, barres) | oui |
| réglages : la carte Apparence propose mascotte, accent et fond | oui |
| réglages : choisir une autre mascotte met à jour sa présentation | oui |
| réglages : la recherche « gpu » propose l'accélération GPU, rangée dans Avancé | oui |
| réglages : un résultat de recherche ouvre la page Avancé | oui |
| réglages : la recherche fait défiler la page jusqu'au groupe trouvé (bas de la page Avancé) | oui |
| ignorés : la recherche « ignor » ouvre la page « Textes et images ignorés » | oui |
| ignorés : la règle posée en lisant (toutes les œuvres) y est listée avec « Retirer » | oui |
| ignorés : « Retirer » supprime la règle de la liste et de l'application | oui |
| centre de corrections : départ (une correction rangée sous la page ouverte, un terme, deux traductions retenues) | oui |
| centre de corrections : la recherche « corrections » ouvre la page | oui |
| centre de corrections : le terme du glossaire y est listé avec « Retirer » | oui |
| centre de corrections : « Retirer » supprime le terme du glossaire | oui |
| centre de corrections : les trois groupes sont là (ignorés, glossaire, corrections par chapitre) | oui |
| centre de corrections : la correction est rangée sous son chapitre (œuvre et numéro), avec ce qui a été fait | oui |
| centre de corrections : la traduction retenue sans correction est listée à part | oui |
| centre de corrections : « Défaire » retire la correction et la traduction retenue qui lui est liée (pas l'autre) | oui |
| centre de corrections : la page retrouve sa traduction automatique après « Défaire » | oui |
| centre de corrections : la liste des corrections se vide | oui |
| centre de corrections : « Retirer » oublie la traduction retenue sans correction | oui |
| accueil : l'écran d'accueil remplace la page (aucune page ouverte) | oui |
| accueil : « Dernière lecture » propose de reprendre l'œuvre lue en dernier | oui |
| accueil : les langues de la traduction sont rappelées | oui |
| accueil : les autres œuvres en cours, avec leur avancement, sans l'œuvre terminée | oui |
| accueil : ce n'est pas la bibliothèque (ni recherche, ni tri, ni pastilles de statut) | oui |
| accueil : tuiles des sites lus (nom, nombre d'œuvres) et tuile pour en ajouter | oui |
| accueil : « Ajouter » ouvre la saisie d'une adresse | oui |
| accueil : le site saisi depuis « Ajouter » est épinglé | oui |
| accueil : la tuile du site épinglé est affichée (épinglée, sans œuvre lue) | oui |
| accueil : un appui long sur une tuile propose de la retirer de l'accueil | oui |
| accueil : « Retirer de l'accueil » enlève la tuile et son épingle | oui |
| accueil : « Rétablir les sites retirés » est proposé | oui |
| accueil : « Rétablir les sites retirés » remet la liste | oui |
| accueil : « Reprendre » ouvre la page et l'accueil se retire | oui |
| éditeur : s'ouvre sur l'image avec les flèches défaire / refaire, la zone de dessin et les onglets | oui |
| éditeur : au départ rien à défaire ni à refaire, vue non zoomée, pas de « Vue d'origine » | oui |
| éditeur : deux doigts qui s'écartent zooment (vue resserrée, « Vue d'origine » proposé) | oui |
| éditeur : après le zoom, l'image est redécodée sur la vue (région couverte = vue, à un pixel près) | oui |
| éditeur : un doigt qui glisse dans l'onglet Texte déplace la vue | oui |
| éditeur : deux doigts qui glissent déplacent la vue, sans changer le zoom | oui |
| éditeur : deux doigts qui se rapprochent éloignent la vue | oui |
| éditeur : « Vue d'origine » rend la vue de départ | oui |
| éditeur : onglet Zone en pinceau libre : « Peindre » et « Gommer » sont proposés | oui |
| éditeur : le pinceau libre est actif, le pinceau (pas la gomme) est choisi | oui |
| éditeur : un trait de pinceau ajoute une zone peinte et un état à défaire | oui |
| éditeur : « Gommer » passe le pinceau en gomme | oui |
| éditeur : un coup de gomme ajoute un trait de gomme (la zone peinte reste, creusée) | oui |
| éditeur : « Défaire » retire le coup de gomme (et laisse le « Refaire ») | oui |
| éditeur : « Refaire » le remet | oui |
| éditeur : défaire deux fois efface tout le dessin | oui |
| éditeur : en mode rectangle un glissé change la zone et s'ajoute à l'historique | oui |
| éditeur : défaire rend la zone d'avant | oui |
| éditeur : onglet Position : un glissé déplace le texte | oui |
| éditeur : défaire remet le texte à sa place | oui |
| éditeur : « Annuler » referme l'éditeur sans rien enregistrer | oui |
| chapitre trop court pour défiler : n'est pas marqué comme lu | oui |
| chapitre suivant pendant une traduction : l'application répond sans attendre la fin de la traduction (retard du fil principal < 2,5 s) | oui |
| chapitre suivant pendant une traduction : le nouveau chapitre s'ouvre | oui |
| chapitre suivant pendant une traduction : le nouveau chapitre est traduit | oui |
| chapitre suivant pendant une traduction : aucune fenêtre « ne répond pas » du système | oui |
| page perdue : l'application reste ouverte (même processus) | oui |
| page perdue : l'incident est reconnu et la page est rouverte | oui |
| page perdue : la même adresse est rouverte et traduite de nouveau | oui |
| page perdue : aucune fenêtre d'erreur du système (ni « ne répond pas », ni « s'arrête ») | oui |
| raccourci : « Reprendre la lecture » publié pour l'œuvre lue | oui |
| illustrations : une page d'œuvre qui construit son illustration par script (image de partage générique) affiche l'image au titre de l'œuvre | oui |
| illustrations : l'illustration de cette œuvre vient du site (journal) | oui |
| illustrations : l'annuaire donne l'illustration d'une œuvre dont le site n'a aucune image | oui |
| illustrations : celle-ci vient de l'annuaire (journal) | oui |
| illustrations : une page qui n'annonce que l'image du site ne donne aucune illustration (la pastille reste) | oui |
| illustrations : l'échec est expliqué au journal | oui |
| illustrations : l'image du site (partage générique) n'est jamais téléchargée | oui |
| illustrations : l'image au titre de l'œuvre, apparue après le chargement, est celle qui est téléchargée | oui |
| illustrations : avec le réglage « annuaire » coupé, l'annuaire n'est plus interrogé | oui |

**Interface : 188/188 vérifications réussies**

# Problèmes détectés
- synth_en_mixed_mlkit: OCR 6/7
- synth_ja_vert_mlkit: OCR 0/4