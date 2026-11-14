# Rapport de test automatique

## adblock_mlkit
- durée 36s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 20.9s (max 20.9s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w900-d62×6 ; classes : condensed×6
- origine des octets des images : page × 1 · durée moyenne d'obtention 215 ms
- durées moyennes par image (1) : obtention 215 ms · lecture du texte 13488 ms · traduction 405 ms · affichage 5512 ms
- détail de la lecture (dernière image) : ML Kit 2907 ms · Paddle 13416 ms · détecteur 7399 ms · total 13443 ms (en parallèle)
- blocage des publicités : 5 requêtes ou redirections bloquées · adresse finale http://10.0.2.2:8000/adtest.html
- mémoire de l'application (PSS) : 634 Mo
- lecture Paddle :  [y=0 langue=en boîtes=24 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=2]
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
- durée 53s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3003 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 30.0s (max 38.3s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 572 ms
- durées moyennes par image (3) : obtention 572 ms · lecture du texte 11442 ms · traduction 560 ms · affichage 3076 ms
- détail de la lecture (dernière image) : ML Kit 2847 ms · Paddle 5347 ms · détecteur 4562 ms · total 5347 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 966 Mo
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
- durée 51s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3029 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 31.2s (max 36.0s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 865 ms
- durées moyennes par image (3) : obtention 865 ms · lecture du texte 11060 ms · traduction 654 ms · affichage 4049 ms
- détail de la lecture (dernière image) : ML Kit 3648 ms · Paddle 5631 ms · détecteur 4948 ms · total 5631 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 941 Mo
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
- durée 233s · images 31 · traitées 31 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 27.0s (max 45.7s)
- rendu : 62/97 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : comicneue-bold×65, boogaloo×20, caveatbrush×10, chewy×2 ; classes : comic×87, brush×10
- origine des octets des images : page × 31 · durée moyenne d'obtention 198 ms
- durées moyennes par image (31) : obtention 198 ms · lecture du texte 6975 ms · traduction 762 ms · affichage 3705 ms
- détail de la lecture (dernière image) : ML Kit 4297 ms · Paddle 6298 ms · détecteur 5302 ms · total 6299 ms (en parallèle)
- long chapitre : durée moyenne des 12 premières 28.8s, des 12 dernières 25.5s
- débit : 8.0 images par minute
- traductions regroupées avec d'autres pages : 0/31 images
- mémoire de l'application (PSS) : 999 Mo
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
- durée 53s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 29.5s (max 38.3s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 524 ms
- durées moyennes par image (3) : obtention 524 ms · lecture du texte 11551 ms · traduction 490 ms · affichage 2847 ms
- détail de la lecture (dernière image) : ML Kit 3371 ms · Paddle 5835 ms · détecteur 5145 ms · total 5836 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 961 Mo
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
- durée 53s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 29.5s (max 38.3s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 337 ms
- durées moyennes par image (3) : obtention 337 ms · lecture du texte 11389 ms · traduction 337 ms · affichage 2917 ms
- détail de la lecture (dernière image) : ML Kit 3309 ms · Paddle 6141 ms · détecteur 4959 ms · total 6146 ms (en parallèle)
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

## pc_en_ref_mlkit
- durée 53s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3002 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 29.1s (max 38.4s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 588 ms
- durées moyennes par image (3) : obtention 588 ms · lecture du texte 11607 ms · traduction 477 ms · affichage 2701 ms
- détail de la lecture (dernière image) : ML Kit 3632 ms · Paddle 7000 ms · détecteur 5510 ms · total 7001 ms (en parallèle)
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

## pc_en_strict_mlkit
- durée 50s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 30.2s (max 35.1s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : réseau × 3 · durée moyenne d'obtention 220 ms
- durées moyennes par image (3) : obtention 220 ms · lecture du texte 10707 ms · traduction 651 ms · affichage 4368 ms
- détail de la lecture (dernière image) : ML Kit 3403 ms · Paddle 6147 ms · détecteur 5467 ms · total 6147 ms (en parallèle)
- lecture dans la WebView impossible, motif : lecture par la WebView coupée (test)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 985 Mo
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

## pc_en_toggle_mlkit
- durée 132s · images 12 · traitées 12 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- note : bascule Original puis Traduit à 7 s (total/traitées/échecs avant : 12/0/0)
- temps moyen par image : 24.7s (max 34.8s)
- rendu : 24/38 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : comicneue-bold×26, boogaloo×6, caveatbrush×4, chewy×2 ; classes : comic×34, brush×4
- origine des octets des images : page × 16 · durée moyenne d'obtention 339 ms
- durées moyennes par image (16) : obtention 339 ms · lecture du texte 7086 ms · traduction 812 ms · affichage 3440 ms
- détail de la lecture (dernière image) : ML Kit 7694 ms · Paddle 8709 ms · détecteur 7101 ms · total 8710 ms (en parallèle)
- bascule Original/Traduit : 16 images traitées sur 12 repérées ; ['bascule Original puis Traduit à 7 s (total/traitées/échecs avant : 12/0/0)']
- traductions regroupées avec d'autres pages : 0/16 images
- mémoire de l'application (PSS) : 983 Mo
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
- durée 60s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (4549 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 33.8s (max 44.2s)
- rendu : 5/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 3 · durée moyenne d'obtention 372 ms
- durées moyennes par image (3) : obtention 372 ms · lecture du texte 13629 ms · traduction 828 ms · affichage 3745 ms
- détail de la lecture (dernière image) : ML Kit 3605 ms · Paddle 12214 ms · détecteur 7301 ms · total 12222 ms (en parallèle)
- mémoire de l'application (PSS) : 969 Mo
- lecture Paddle :  [y=0 langue=ja boîtes=9 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=ja boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=2 texte_libre=0]
- OCR : 20 lignes (14 utilisées, 6 écartées) · bulles affichées 5 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| …これで出来上がり | ... c'est fait |
| は5は5 | 5 est 5 |
| .う-ん足りなかったかも | Hmmm, ce n'était pas assez |
| どはどは | Comment |
| さん | San |
| これで満足?! | Est-ce satisfait ?! |
| うん… カンペキ | Ouais ... Campeki |
| ダメ! そんなことしちゃだめなんだから | Pas bien! Parce que ce n'est pas une telle chose |

## préparation
- modèle fr : ready (4504 ms)
- modèle ja : ready (1501 ms)
- modèle ko : ready (1500 ms)
- modèle zh : ready (1500 ms)
- gemma prêt : E2B=false E4B=false

## seam_mlkit
- durée 61s · images 4 · traitées 4 · échecs 0 · sans texte 2 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 25.2s (max 30.7s)
- rendu : 2/2 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×1, baloo2-w700×1 ; classes : sans×1, comic×1
- origine des octets des images : page × 4 · durée moyenne d'obtention 505 ms
- durées moyennes par image (2) : obtention 506 ms · lecture du texte 12862 ms · traduction 691 ms · affichage 4464 ms
- détail de la lecture (dernière image) : ML Kit 4744 ms · Paddle 13874 ms · détecteur 9564 ms · total 13875 ms (en parallèle)
- jonction A : 1 bloc(s), dont 1 lu(s) en entier ; image du dessus recouverte : oui
- jonction B : 1 bloc(s), dont 1 lu(s) en entier ; image du dessus recouverte : oui
- textes coupés lus en entier : 2/2 (avec la jonction)
- mémoire de l'application (PSS) : 919 Mo
- lecture Paddle :  [y=0 : aucune boîte] [y=1184 langue=en boîtes=6 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=1 texte_libre=0]
- OCR : 14 lignes (8 utilisées, 6 écartées) · bulles affichées 2 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| I WILL NEVER FORGIVE YOU FOR WHAT YOU DID TO HER. | JE NE TE PARDONNERAI JAMAIS DE CE QUE TU L'AS FAIT. |
| YOU HAVE COME SO FAR ALREADY, BUT THE JOURNEY HAS JUST BEGUN. | VOUS ÊTES DÉJÀ VENU DÉJÀ, MAIS LE VOYAGE VIENT DE COMMENCER. |

## seam_off_mlkit
- durée 58s · images 4 · traitées 4 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4598 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 31.0s (max 44.7s)
- rendu : 4/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : baloo2-w700×2, archivo-w700-d100×2 ; classes : comic×2, sans×2
- origine des octets des images : page × 4 · durée moyenne d'obtention 184 ms
- durées moyennes par image (4) : obtention 184 ms · lecture du texte 10235 ms · traduction 534 ms · affichage 2739 ms
- détail de la lecture (dernière image) : ML Kit 1354 ms · Paddle 9249 ms · détecteur 6353 ms · total 9256 ms (en parallèle)
- jonction A : 2 bloc(s), dont 0 lu(s) en entier ; image du dessus recouverte : non
- jonction B : 2 bloc(s), dont 0 lu(s) en entier ; image du dessus recouverte : non
- textes coupés lus en entier : 0/2 (sans la jonction)
- mémoire de l'application (PSS) : 870 Mo
- lecture Paddle :  [y=0 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 : aucune boîte] [zones : bulles=1 texte_bulle=1 texte_libre=0]
- OCR : 11 lignes (11 utilisées, 0 écartées) · bulles affichées 4 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| YOU HAVE COME SO FAR ALREADY, | VOUS ÊTES DÉJÀ VENU DÉJÀ, |
| I WILL NEVER DGIIE | JE NE VAIS JAMAIS DGIIE |
| BUT THE JOURNEY HAS JUST BEGUN. | MAIS LE VOYAGE VIENT DE COMMENCER. |
| NGIvL iUU i WHAT YOU DID TO HER. | NGIVL IUU I CE QUE TU AS FAIT À ELLE. |

## synth_atypical_combined
- durée 59s · images 6 · traitées 6 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3065 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 28.1s (max 42.7s)
- rendu : 3/10 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, archivo-w900-d62×3, baloo2-w700×3, chewy×1 ; classes : comic×4, sans×3, condensed×3
- filigrane contre une bulle : 1 bloc(s) traduit(s)
- bulle ouverte : texte dans l ellipse
- origine des octets des images : page × 6 · durée moyenne d'obtention 355 ms
- durées moyennes par image (6) : obtention 355 ms · lecture du texte 6696 ms · traduction 778 ms · affichage 9358 ms
- détail de la lecture (dernière image) : ML Kit 4986 ms · Paddle 4390 ms · détecteur 3508 ms · total 4986 ms (en parallèle)
- mémoire de l'application (PSS) : 728 Mo
- lecture Paddle :  [y=0 langue=en boîtes=8 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=4 texte_libre=1]
- OCR : 69 lignes (40 utilisées, 29 écartées) · bulles affichées 10 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| WHATEVER THE CASE IS, I MUST GATHER MORE SOULS. | QUOI QU'IL EN SOIT, JE DOIS RASSEMBLER PLUS D'ÂMES. |
| IF I KEEP DOING THAT, THEN ONE DAY... | SI JE CONTINUE À FAIRE ÇA, ALORS UN JOUR ... |
| IN THE WAKE OF THEIR DECEITFUL TEMPTATIONS | À LA SUITE DE LEURS TENTATIONS TROMPEUSES |
| THE WORLD'S BALANCE WAS TEETERING PRECARIOUSLY | LE SOLDE DU MONDE EN TRAIN DE SE FAIRE VACRIR PRÉCISÉMENT |
| SHUT UP, THAT SOUNDS GROSS. | TAIS-TOI, ÇA SONNE BRUT. |
| A RESTRICTION ON YOUR WANT ME TO PUT MOUTH? | UNE RESTRICTION SUR VOTRE VEUVE-TOI DE ME METTRE LA BOUCHE? |
| YES, SIR! I'LL SHUT MY MOUTH BY PUTTING A BOULDER | OUI, MONSIEUR! JE VAIS FERMER LA BOUCHE EN METTANT UN ROCHER |
| SHUT UP ALREADY, YOU PLEASE JUST CRAZY BASTARD... | TAIS-TOI DÉJÀ, TU VEUX JUSTE JUSTE FOU BÂTARD ... |
| ALL I DID WAS WATCH I OVER THE STORAGE. HAHA. | TOUT CE QUE J'AI FAIT ÉTAIT REGARDER I SUR LE STOCKAGE. HAHA. |
| ALL I DID WAS WATCH OVER THE STORAGE. HAHA. | TOUT CE QUE J'AI FAIT ÉTAIT SURVEILLE LE STOCKAGE. HAHA. |
| THAT DOESN'T SEEM TO BE THE CASE. | CELA NE SEMBLE PAS ÊTRE LE CAS. |

## synth_en_caps_mlkit
- durée 35s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 19.5s (max 19.5s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w900-d62×6 ; classes : condensed×6
- origine des octets des images : page × 1 · durée moyenne d'obtention 436 ms
- durées moyennes par image (1) : obtention 436 ms · lecture du texte 13155 ms · traduction 387 ms · affichage 5133 ms
- détail de la lecture (dernière image) : ML Kit 3517 ms · Paddle 13109 ms · détecteur 7688 ms · total 13110 ms (en parallèle)
- mémoire de l'application (PSS) : 613 Mo
- lecture Paddle :  [y=0 langue=en boîtes=24 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=2]
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
- durée 39s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3025 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 24.5s (max 24.5s)
- rendu : 2/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, chewy×2 ; classes : sans×3, comic×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 479 ms
- durées moyennes par image (1) : obtention 479 ms · lecture du texte 13034 ms · traduction 372 ms · affichage 10272 ms
- détail de la lecture (dernière image) : ML Kit 4560 ms · Paddle 12997 ms · détecteur 8772 ms · total 12998 ms (en parallèle)
- mémoire de l'application (PSS) : 629 Mo
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
- durée 26s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 10.8s (max 10.8s)
- rendu : 0/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, fredoka-w600-d100×2 ; classes : sans×3, comic×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 680 ms
- durées moyennes par image (1) : obtention 680 ms · lecture du texte 995 ms · traduction 367 ms · affichage 8410 ms
- mémoire de l'application (PSS) : 253 Mo
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
- durée 32s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 18.4s (max 18.4s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : baloo2-w700×5, notosans-w700-d100×1 ; classes : comic×5, sans×1
- origine des octets des images : page × 1 · durée moyenne d'obtention 295 ms
- durées moyennes par image (1) : obtention 295 ms · lecture du texte 11278 ms · traduction 401 ms · affichage 5452 ms
- détail de la lecture (dernière image) : ML Kit 2684 ms · Paddle 11230 ms · détecteur 7455 ms · total 11237 ms (en parallèle)
- mémoire de l'application (PSS) : 627 Mo
- lecture Paddle :  [y=0 langue=en boîtes=19 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=6 texte_bulle=6 texte_libre=2]
- OCR : 18 lignes (17 utilisées, 1 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: Please... take me as your disciple! | 100 % | oui | Plaît ... Prends-moi comme disciple! |
| bubble: I can't believe you came all the way here. | 100 % | oui | Je ne peux pas croire que tu viennes tout le chemin ici. |
| bubble: Who are you? What do you want from me? | 100 % | oui | Qui es-tu? Qu'attends-tu de moi? |
| bubble: The master will not see anyone today. | 100 % | oui | Le maître ne verra personne aujourd'hui. |
| bubble: Do I have no choice but to kill him? | 100 % | oui | Est-ce que je n'ai pas d'autre choix que de le tuer? |
| free: You will regret this. | 100 % | oui | Vous allez le regretter. |
| sfx: Boom! | 100 % | laissée telle quelle |  |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_en_variety_combined
- durée 38s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 22.4s (max 22.4s)
- rendu : 3/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×2, archivo-w900-d62×2 ; classes : sans×2, condensed×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 823 ms
- durées moyennes par image (1) : obtention 823 ms · lecture du texte 13727 ms · traduction 431 ms · affichage 7051 ms
- détail de la lecture (dernière image) : ML Kit 4830 ms · Paddle 13687 ms · détecteur 8957 ms · total 13689 ms (en parallèle)
- mémoire de l'application (PSS) : 645 Mo
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
- durée 38s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (3001 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 22.7s (max 22.7s)
- rendu : 4/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 249 ms
- durées moyennes par image (1) : obtention 249 ms · lecture du texte 18115 ms · traduction 577 ms · affichage 2497 ms
- détail de la lecture (dernière image) : ML Kit 2857 ms · Paddle 18111 ms · détecteur 6965 ms · total 18112 ms (en parallèle)
- mémoire de l'application (PSS) : 750 Mo
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
- durée 34s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · KO→FR
- note : modèle ko : ready (4501 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 17.7s (max 17.7s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 198 ms
- durées moyennes par image (1) : obtention 198 ms · lecture du texte 10350 ms · traduction 695 ms · affichage 5125 ms
- détail de la lecture (dernière image) : ML Kit 3073 ms · Paddle 10337 ms · détecteur 7709 ms · total 10346 ms (en parallèle)
- mémoire de l'application (PSS) : 619 Mo
- lecture Paddle :  [y=0 langue=ko boîtes=14 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=ko boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=6 texte_bulle=6 texte_libre=1]
- OCR : 14 lignes (14 utilisées, 0 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 제발... 저를 제자로 받아주세요! | 100 % | oui | S'il vous plaît, acceptez-moi comme disciple! |
| bubble: 여기까지 오시다니 믿을 수 없군요. | 100 % | oui | Je ne peux pas y croire. |
| bubble: 당신은 누구십니까? 저에게 무엇을 원하시나요? | 100 % | oui | Qui es-tu? Que veux-tu que je suis? |
| bubble: 스승님은 오늘 아무도 만나지 않으실 겁니다. | 100 % | oui | Maître ne rencontrera personne aujourd'hui. |
| bubble: 그를 죽일 수밖에 없는 건가? | 100 % | oui | Avez-vous de le tuer? |
| free: 후회하게 될 거다. | 100 % | oui | Je vais le regretter. |
| free: 털썩 | 100 % | oui | Flumping |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_layouts_combined
- durée 74s · images 6 · traitées 6 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4503 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 34.5s (max 48.2s)
- rendu : 33/39 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×21, archivo-w900-d62×18 ; classes : sans×21, condensed×18
- regroupement : 39 blocs lus pour 42 blocs réels · fusions à tort 0 · scissions 0
- origine des octets des images : page × 6 · durée moyenne d'obtention 352 ms
- durées moyennes par image (6) : obtention 352 ms · lecture du texte 9171 ms · traduction 1221 ms · affichage 8232 ms
- détail de la lecture (dernière image) : ML Kit 5073 ms · Paddle 7739 ms · détecteur 3925 ms · total 7739 ms (en parallèle)
- mémoire de l'application (PSS) : 848 Mo
- lecture Paddle :  [y=0 langue=en boîtes=15 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=10 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=0]
- OCR : 140 lignes (107 utilisées, 33 écartées) · bulles affichées 39 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| IT HAS BEEN THREE YEARS SINCE THE WAR | CELA FAIT TROIS ANS DEPUIS LA GUERRE |
| THIS IS THE END OF THE ROAD | C'EST LA FIN DE LA ROUTE |
| WE MUST LEAVE BEFORE DAWN | NOUS DEVONS PARTIR AVANT L'AUBE |
| HOW DID YOU FIND THIS PLACE | COMMENT AVEZ-VOUS TROUVÉ CET ENDROIT |
| HOW DID YOU FIND THIS PLACE | COMMENT AVEZ-VOUS TROUVÉ CET ENDROIT |
| I TOLD YOU TO STAY AWAY FROM HERE | JE T'AI DIT DE RESTER À L'ÉCART D'ICI |
| THE MASTER WILL NOT SEE ANYONE TODAY | LE MAÎTRE NE VERRA PERSONNE AUJOURD'HUI |
| YOU CALL THAT A TECHNIQUE | VOUS APPELEZ CELA UNE TECHNIQUE |
| YOU CALL THAT A TECHNIQUE | VOUS APPELEZ CELA UNE TECHNIQUE |
| THIS IS THE END OF THE ROAD | C'EST LA FIN DE LA ROUTE |
| THEY WILL PAY-FOR WHAT THEY DID | ILS PAIERONT CE QU'ILS ONT FAIT |
| THE GATE WILL OPEN AT SUNSET | LA PORTE S'OUVRIRA AU COUCHER DU SOLEIL |
| THE MASTER WILL NOT SEE ANYONE TODAY | LE MAÎTRE NE VERRA PERSONNE AUJOURD'HUI |
| I NEVER THOUGHT I WOULD SEE YOU AGAIN | JE N'AI JAMAIS PENSÉ TE REVOIR |

## synth_zh_mlkit
- durée 30s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · ZH_HANS→FR
- note : modèle zh : ready (3003 ms)
- note : modèle fr : ready (1501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 15.5s (max 15.5s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 417 ms
- durées moyennes par image (1) : obtention 417 ms · lecture du texte 9234 ms · traduction 750 ms · affichage 3986 ms
- détail de la lecture (dernière image) : ML Kit 1824 ms · Paddle 9229 ms · détecteur 6660 ms · total 9230 ms (en parallèle)
- mémoire de l'application (PSS) : 618 Mo
- lecture Paddle :  [y=0 langue=zh boîtes=13 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=zh boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=5 texte_bulle=6 texte_libre=1]
- OCR : 12 lignes (12 utilisées, 0 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 求求你……收我为徒吧！ | 100 % | oui | Je vous prie de vous ... je vais me recevoir! |
| bubble: 没想到你竟然大老远跑到这里来。 | 100 % | oui | Je ne m'attendais pas à ce que vous veniez ici. |
| bubble: 你是谁？你想从我这里得到什么？ | 100 % | oui | Qui es-tu? Que veux-tu de me tirer de moi? |
| bubble: 师父今天不见任何人。 | 100 % | oui | Le maître ne verra personne aujourd'hui. |
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

Requêtes tierces servies par le faux site (donc non bloquées) : http://cdn.test/images/p1.png, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://friend.test/page.html, http://friend.test/favicon.ico, http://cdn.test/images/p1.png, http://cdn.test/images/p2.png, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p1.png, http://cdn.test/images/p2.png, http://cdn.test/bannieres-generiques/ok.png, http://adnet.test/banner.gif, http://adnet.test/ad.js, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png, http://cdn.test/ads/ad.png, http://cdn.test/bannieres-generiques/ok.png, http://adnet.test/frame.html, http://popunder.test/landing, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png

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

11:23:58  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
11:23:57  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:57  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
11:23:57  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:57  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:55  requête  http://adnet.test/frame.html  ←  ||adnet.test^  (page : popunder.test)
11:23:50  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
11:23:49  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:49  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
11:23:49  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:49  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:47  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
11:23:47  superposition  http://popunder.test/overlay  (page : 10.0.2.2)
11:23:45  redirection (liste)  http://popunder.test/landing  ←  ||popunder.test^  (page : 10.0.2.2)
11:23:44  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
11:23:43  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:43  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
11:23:43  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:43  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:41  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
11:23:39  pop-up  http://popunder.test/btn  (page : 10.0.2.2)
11:23:38  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
11:23:38  superposition  http://popunder.test/overlay  (page : 10.0.2.2)
11:23:33  redirection (liste)  http://popunder.test/landing  ←  ||popunder.test^  (page : 10.0.2.2)
11:23:33  requête  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:32  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
11:23:31  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:31  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
11:23:31  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
11:23:31  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)

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
| réglages rapides : un appui long sur le bouton rond ouvre le panneau | oui |
| réglages rapides : langue, taille du texte, onomatopées et retraduction y sont | oui |
| réglages rapides : la gomme à traductions y est proposée | oui |
| réglages rapides : l'appui long ne bascule pas la traduction (même état du bouton avant et après) | oui |
| réglages rapides : le retour referme le panneau, la page reste affichée | oui |
| bibliothèque : l'œuvre est listée avec « Continuer la lecture » | oui |
| fiche d'une œuvre : liste des chapitres | oui |
| fiche d'une œuvre : page de l'œuvre et reprise proposées | oui |
| retour de la fiche vers la bibliothèque | oui |
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
| raccourci : « Reprendre la lecture » publié pour l'œuvre lue | oui |

**Interface : 104/104 vérifications réussies**

# Problèmes détectés
- synth_ja_vert_mlkit: OCR 0/4