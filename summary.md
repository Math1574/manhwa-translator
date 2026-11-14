# Rapport de test automatique

## adblock_mlkit
- durée 35s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 20.6s (max 20.6s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w900-d62×6 ; classes : condensed×6
- origine des octets des images : page × 1 · durée moyenne d'obtention 381 ms
- durées moyennes par image (1) : obtention 381 ms · lecture du texte 13408 ms · traduction 377 ms · affichage 6051 ms
- détail de la lecture (dernière image) : ML Kit 5361 ms · Paddle 13364 ms · détecteur 8695 ms · total 13365 ms (en parallèle)
- blocage des publicités : 5 requêtes ou redirections bloquées · adresse finale http://10.0.2.2:8000/adtest.html
- mémoire de l'application (PSS) : 616 Mo
- lecture Paddle :  [y=0 langue=en boîtes=25 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=2]
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
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 29.2s (max 38.1s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 290 ms
- durées moyennes par image (3) : obtention 290 ms · lecture du texte 11327 ms · traduction 422 ms · affichage 2722 ms
- détail de la lecture (dernière image) : ML Kit 1418 ms · Paddle 6199 ms · détecteur 4036 ms · total 6200 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 968 Mo
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
- durée 59s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3008 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 33.6s (max 41.7s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 165 ms
- durées moyennes par image (3) : obtention 165 ms · lecture du texte 12590 ms · traduction 452 ms · affichage 3323 ms
- détail de la lecture (dernière image) : ML Kit 3805 ms · Paddle 5232 ms · détecteur 5274 ms · total 5275 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 977 Mo
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

## pc_en_long_mlkit
- durée 237s · images 31 · traitées 31 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3002 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 27.0s (max 44.1s)
- rendu : 62/97 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : comicneue-bold×65, boogaloo×20, caveatbrush×10, chewy×2 ; classes : comic×87, brush×10
- origine des octets des images : page × 31 · durée moyenne d'obtention 193 ms
- durées moyennes par image (31) : obtention 193 ms · lecture du texte 6977 ms · traduction 764 ms · affichage 3818 ms
- détail de la lecture (dernière image) : ML Kit 6274 ms · Paddle 6867 ms · détecteur 6060 ms · total 6868 ms (en parallèle)
- long chapitre : durée moyenne des 12 premières 28.7s, des 12 dernières 25.7s
- débit : 7.8 images par minute
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
- durée 55s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3003 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 29.6s (max 38.6s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 227 ms
- durées moyennes par image (3) : obtention 227 ms · lecture du texte 11545 ms · traduction 477 ms · affichage 2920 ms
- détail de la lecture (dernière image) : ML Kit 2692 ms · Paddle 6799 ms · détecteur 4739 ms · total 6799 ms (en parallèle)
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
- note : modèle fr : ready (3002 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 29.0s (max 37.5s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 269 ms
- durées moyennes par image (3) : obtention 269 ms · lecture du texte 11110 ms · traduction 467 ms · affichage 2942 ms
- détail de la lecture (dernière image) : ML Kit 3769 ms · Paddle 5842 ms · détecteur 5485 ms · total 5845 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 978 Mo
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
- durée 49s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3010 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 26.4s (max 33.1s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : page × 3 · durée moyenne d'obtention 503 ms
- durées moyennes par image (3) : obtention 503 ms · lecture du texte 9662 ms · traduction 451 ms · affichage 2716 ms
- détail de la lecture (dernière image) : ML Kit 3011 ms · Paddle 4633 ms · détecteur 4459 ms · total 4634 ms (en parallèle)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 978 Mo
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
- durée 48s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4531 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 28.6s (max 33.2s)
- rendu : 4/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : chewy×2, caveatbrush×2, comicneue-bold×1 ; classes : comic×3, brush×2
- origine des octets des images : réseau × 3 · durée moyenne d'obtention 419 ms
- durées moyennes par image (3) : obtention 419 ms · lecture du texte 10373 ms · traduction 505 ms · affichage 3629 ms
- détail de la lecture (dernière image) : ML Kit 1152 ms · Paddle 6575 ms · détecteur 4982 ms · total 6576 ms (en parallèle)
- lecture dans la WebView impossible, motif : lecture par la WebView coupée (test)
- traductions regroupées avec d'autres pages : 0/3 images
- mémoire de l'application (PSS) : 924 Mo
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
- durée 114s · images 12 · traitées 12 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3004 ms)
- note : gemma prêt : E2B=false E4B=false
- note : bascule Original puis Traduit à 7 s (total/traitées/échecs avant : 12/0/0)
- temps moyen par image : 28.1s (max 43.4s)
- rendu : 24/38 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : comicneue-bold×26, boogaloo×6, caveatbrush×4, chewy×2 ; classes : comic×34, brush×4
- origine des octets des images : page × 12 · durée moyenne d'obtention 241 ms
- durées moyennes par image (12) : obtention 241 ms · lecture du texte 7601 ms · traduction 796 ms · affichage 4328 ms
- détail de la lecture (dernière image) : ML Kit 7391 ms · Paddle 8514 ms · détecteur 7086 ms · total 8514 ms (en parallèle)
- bascule Original/Traduit : 12 images traitées sur 12 repérées ; ['bascule Original puis Traduit à 7 s (total/traitées/échecs avant : 12/0/0)']
- traductions regroupées avec d'autres pages : 0/12 images
- mémoire de l'application (PSS) : 983 Mo
- lecture Paddle :  [y=0 langue=en boîtes=8 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=12 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=1]
- OCR : 157 lignes (98 utilisées, 59 écartées) · bulles affichées 38 · hors image 0 · chevauchements 0 · texte < 10px 0

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

## pc_ja_mlkit
- durée 60s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (4502 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 32.6s (max 42.4s)
- rendu : 5/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 3 · durée moyenne d'obtention 426 ms
- durées moyennes par image (3) : obtention 426 ms · lecture du texte 13238 ms · traduction 862 ms · affichage 3533 ms
- détail de la lecture (dernière image) : ML Kit 1547 ms · Paddle 11044 ms · détecteur 6424 ms · total 11048 ms (en parallèle)
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
- modèle fr : ready (3009 ms)
- modèle ja : ready (1500 ms)
- modèle ko : ready (1500 ms)
- modèle zh : ready (1501 ms)
- gemma prêt : E2B=false E4B=false

## seam_mlkit
- durée 54s · images 4 · traitées 4 · échecs 0 · sans texte 2 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4506 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 28.8s (max 38.8s)
- rendu : 2/2 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×1, baloo2-w700×1 ; classes : sans×1, comic×1
- origine des octets des images : page × 4 · durée moyenne d'obtention 224 ms
- durées moyennes par image (2) : obtention 219 ms · lecture du texte 8090 ms · traduction 544 ms · affichage 3252 ms
- détail de la lecture (dernière image) : ML Kit 1125 ms · Paddle 5469 ms · détecteur 4747 ms · total 5469 ms (en parallèle)
- jonction A : 1 bloc(s), dont 1 lu(s) en entier ; image du dessus recouverte : oui
- jonction B : 1 bloc(s), dont 1 lu(s) en entier ; image du dessus recouverte : oui
- textes coupés lus en entier : 2/2 (avec la jonction)
- mémoire de l'application (PSS) : 868 Mo
- lecture Paddle :  [y=0 langue=en boîtes=4 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 : aucune boîte] [zones : bulles=1 texte_bulle=1 texte_libre=0]
- OCR : 14 lignes (8 utilisées, 6 écartées) · bulles affichées 2 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| I WILL NEVER FORGIVE YOU FOR WHAT YOU DID TO HER. | JE NE TE PARDONNERAI JAMAIS DE CE QUE TU L'AS FAIT. |
| YOU HAVE COME SO FAR ALREADY, BUT THE JOURNEY HAS JUST BEGUN. | VOUS ÊTES DÉJÀ VENU DÉJÀ, MAIS LE VOYAGE VIENT DE COMMENCER. |

## seam_off_mlkit
- durée 55s · images 4 · traitées 4 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3000 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 28.4s (max 40.9s)
- rendu : 4/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×2, baloo2-w700×2 ; classes : sans×2, comic×2
- origine des octets des images : page × 4 · durée moyenne d'obtention 138 ms
- durées moyennes par image (4) : obtention 138 ms · lecture du texte 9860 ms · traduction 506 ms · affichage 3002 ms
- détail de la lecture (dernière image) : ML Kit 3064 ms · Paddle 10565 ms · détecteur 7998 ms · total 10565 ms (en parallèle)
- jonction A : 2 bloc(s), dont 0 lu(s) en entier ; image du dessus recouverte : non
- jonction B : 2 bloc(s), dont 0 lu(s) en entier ; image du dessus recouverte : non
- textes coupés lus en entier : 0/2 (sans la jonction)
- mémoire de l'application (PSS) : 902 Mo
- lecture Paddle :  [y=0 : aucune boîte] [y=1184 langue=en boîtes=6 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=1 texte_libre=0]
- OCR : 11 lignes (11 utilisées, 0 écartées) · bulles affichées 4 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| I WILL NEVER DGIIE | JE NE VAIS JAMAIS DGIIE |
| BUT THE JOURNEY HAS JUST BEGUN. | MAIS LE VOYAGE VIENT DE COMMENCER. |
| NGIvL iUU i WHAT YOU DID TO HER. | NGIVL IUU I CE QUE TU AS FAIT À ELLE. |
| YOU HAVE COME SO FAR ALREADY, | VOUS ÊTES DÉJÀ VENU DÉJÀ, |

## synth_atypical_combined
- durée 59s · images 6 · traitées 6 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3032 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 27.8s (max 42.0s)
- rendu : 3/10 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, baloo2-w700×3, archivo-w900-d62×3, chewy×1 ; classes : comic×4, sans×3, condensed×3
- filigrane contre une bulle : 1 bloc(s) traduit(s)
- bulle ouverte : texte dans l ellipse
- origine des octets des images : page × 6 · durée moyenne d'obtention 188 ms
- durées moyennes par image (6) : obtention 188 ms · lecture du texte 6495 ms · traduction 840 ms · affichage 6920 ms
- détail de la lecture (dernière image) : ML Kit 2179 ms · Paddle 4331 ms · détecteur 3159 ms · total 4339 ms (en parallèle)
- mémoire de l'application (PSS) : 736 Mo
- lecture Paddle :  [y=0 langue=en boîtes=8 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=2 texte_bulle=4 texte_libre=1]
- OCR : 69 lignes (40 utilisées, 29 écartées) · bulles affichées 10 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| WHATEVER THE CASE IS, I MUST GATHER MORE SOULS. | QUOI QU'IL EN SOIT, JE DOIS RASSEMBLER PLUS D'ÂMES. |
| IF I KEEP DOING THAT, THEN ONE DAY... | SI JE CONTINUE À FAIRE ÇA, ALORS UN JOUR ... |
| SHUT UP, THAT SOUNDS GROSS. | TAIS-TOI, ÇA SONNE BRUT. |
| A RESTRICTION ON YOUR WANT ME TO PUT MOUTH? | UNE RESTRICTION SUR VOTRE VEUVE-TOI DE ME METTRE LA BOUCHE? |
| IN THE WAKE OF THEIR DECEITFUL TEMPTATIONS | À LA SUITE DE LEURS TENTATIONS TROMPEUSES |
| THE WORLD'S BALANCE WAS TEETERING PRECARIOUSLY | LE SOLDE DU MONDE EN TRAIN DE SE FAIRE VACRIR PRÉCISÉMENT |
| YES, SIR! I'LL SHUT MY MOUTH BY PUTTING A BOULDER | OUI, MONSIEUR! JE VAIS FERMER LA BOUCHE EN METTANT UN ROCHER |
| SHUT UP ALREADY, YOU PLEASE JUST CRAZY BASTARD... | TAIS-TOI DÉJÀ, TU VEUX JUSTE JUSTE FOU BÂTARD ... |
| ALL I DID WAS WATCH I OVER THE STORAGE. HAHA. | TOUT CE QUE J'AI FAIT ÉTAIT REGARDER I SUR LE STOCKAGE. HAHA. |
| ALL I DID WAS WATCH OVER THE STORAGE. HAHA. | TOUT CE QUE J'AI FAIT ÉTAIT SURVEILLE LE STOCKAGE. HAHA. |
| THAT DOESN'T SEEM TO BE THE CASE. | CELA NE SEMBLE PAS ÊTRE LE CAS. |

## synth_en_caps_mlkit
- durée 35s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 19.3s (max 19.3s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w900-d62×6 ; classes : condensed×6
- origine des octets des images : page × 1 · durée moyenne d'obtention 387 ms
- durées moyennes par image (1) : obtention 387 ms · lecture du texte 12361 ms · traduction 377 ms · affichage 5696 ms
- détail de la lecture (dernière image) : ML Kit 3528 ms · Paddle 12317 ms · détecteur 7014 ms · total 12318 ms (en parallèle)
- mémoire de l'application (PSS) : 639 Mo
- lecture Paddle :  [y=0 langue=en boîtes=25 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=8 texte_bulle=8 texte_libre=2]
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
- durée 45s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 29.5s (max 29.5s)
- rendu : 2/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, chewy×2 ; classes : sans×3, comic×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 161 ms
- durées moyennes par image (1) : obtention 161 ms · lecture du texte 12960 ms · traduction 422 ms · affichage 15303 ms
- détail de la lecture (dernière image) : ML Kit 3358 ms · Paddle 12920 ms · détecteur 8682 ms · total 12921 ms (en parallèle)
- mémoire de l'application (PSS) : 659 Mo
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
- durée 25s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 10.3s (max 10.3s)
- rendu : 0/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×3, fredoka-w600-d100×2 ; classes : sans×3, comic×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 229 ms
- durées moyennes par image (1) : obtention 229 ms · lecture du texte 992 ms · traduction 346 ms · affichage 8189 ms
- mémoire de l'application (PSS) : 217 Mo
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
- durée 34s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4505 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 18.7s (max 18.7s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : baloo2-w700×5, archivo-w900-d62×1 ; classes : comic×5, condensed×1
- origine des octets des images : page × 1 · durée moyenne d'obtention 861 ms
- durées moyennes par image (1) : obtention 861 ms · lecture du texte 11986 ms · traduction 367 ms · affichage 5308 ms
- détail de la lecture (dernière image) : ML Kit 3135 ms · Paddle 11946 ms · détecteur 7339 ms · total 11947 ms (en parallèle)
- mémoire de l'application (PSS) : 632 Mo
- lecture Paddle :  [y=0 langue=en boîtes=20 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=en boîtes=2 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=5 texte_bulle=6 texte_libre=2]
- OCR : 18 lignes (17 utilisées, 1 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: Please... take me as your disciple! | 100 % | oui | Plaît ... Prends-moi comme disciple! |
| bubble: I can't believe you came all the way here. | 100 % | oui | Je ne peux pas croire que tu viennes tout le chemin ici. |
| bubble: Who are you? What do you want from me? | 100 % | oui | Qui es-tu? Qu'attends-tu de moi? |
| bubble: The master will not see anyone today. | 100 % | oui | Le maître ne verra personne aujourd'hui. |
| bubble: Do I have no choice but to kill him? | 100 % | oui | Est-ce que je n'ai pas d'autre choix que de le tuer? |
| free: You will regret this. | 100 % | oui | Vous le regretterez. |
| sfx: Boom! | 100 % | laissée telle quelle |  |

**Lecture OCR correcte : 7/7 · éléments traités comme attendu (traduits, onomatopées laissées) : 7/7**

## synth_en_variety_combined
- durée 38s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (4513 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 20.8s (max 20.8s)
- rendu : 3/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×2, archivo-w900-d62×2 ; classes : sans×2, condensed×2
- origine des octets des images : page × 1 · durée moyenne d'obtention 400 ms
- durées moyennes par image (1) : obtention 400 ms · lecture du texte 13561 ms · traduction 401 ms · affichage 6007 ms
- détail de la lecture (dernière image) : ML Kit 5218 ms · Paddle 13524 ms · détecteur 8329 ms · total 13525 ms (en parallèle)
- mémoire de l'application (PSS) : 639 Mo
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
- temps moyen par image : 21.9s (max 21.9s)
- rendu : 4/4 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 640 ms
- durées moyennes par image (1) : obtention 640 ms · lecture du texte 17626 ms · traduction 576 ms · affichage 2481 ms
- détail de la lecture (dernière image) : ML Kit 3831 ms · Paddle 17623 ms · détecteur 8053 ms · total 17624 ms (en parallèle)
- mémoire de l'application (PSS) : 748 Mo
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
- temps moyen par image : 17.8s (max 17.8s)
- rendu : 5/7 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 476 ms
- durées moyennes par image (1) : obtention 476 ms · lecture du texte 9552 ms · traduction 641 ms · affichage 6254 ms
- détail de la lecture (dernière image) : ML Kit 2044 ms · Paddle 9547 ms · détecteur 6500 ms · total 9549 ms (en parallèle)
- mémoire de l'application (PSS) : 641 Mo
- lecture Paddle :  [y=0 langue=ko boîtes=14 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=ko boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=6 texte_bulle=6 texte_libre=2]
- OCR : 14 lignes (14 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

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
- durée 75s · images 6 · traitées 6 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (3001 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 34.5s (max 47.4s)
- rendu : 33/39 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- police : archivo-w700-d100×21, archivo-w900-d62×18 ; classes : sans×21, condensed×18
- regroupement : 39 blocs lus pour 42 blocs réels · fusions à tort 0 · scissions 0
- origine des octets des images : page × 6 · durée moyenne d'obtention 214 ms
- durées moyennes par image (6) : obtention 214 ms · lecture du texte 9339 ms · traduction 993 ms · affichage 5782 ms
- détail de la lecture (dernière image) : ML Kit 5363 ms · Paddle 7583 ms · détecteur 5964 ms · total 7584 ms (en parallèle)
- mémoire de l'application (PSS) : 851 Mo
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
| SOMEONE IS WATCHING U FROM THE ROOF | QUELQU'UN TE REGARDE DU TOIT |
| THEY WILL PAY FOR WHAT THEY DID | ILS PAIERONT POUR CE QU'ILS ONT FAIT |
| THEY WILL PAY FOR WHAT THEY DID | ILS PAIERONT POUR CE QU'ILS ONT FAIT |
| DON'T NDERESTIMATE THE OLD MAN | NE SOUS-ESTIMEZ PAS LE VIEIL HOMME |
| SOMEONE IS WATCHING US FROM THE ROOF | QUELQU'UN NOUS REGARDE DU TOIT |
| WE MUST LEAVE BEFORE DAWN | NOUS DEVONS PARTIR AVANT L'AUBE |

## synth_zh_mlkit
- durée 32s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · ZH_HANS→FR
- note : modèle zh : ready (4521 ms)
- note : modèle fr : ready (1501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 14.8s (max 14.8s)
- rendu : 5/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- origine des octets des images : page × 1 · durée moyenne d'obtention 243 ms
- durées moyennes par image (1) : obtention 243 ms · lecture du texte 9298 ms · traduction 727 ms · affichage 3531 ms
- détail de la lecture (dernière image) : ML Kit 2927 ms · Paddle 9295 ms · détecteur 8245 ms · total 9296 ms (en parallèle)
- mémoire de l'application (PSS) : 604 Mo
- lecture Paddle :  [y=0 langue=zh boîtes=12 colonnes=0 manga(dispo=-, lues=0) ] [y=1184 langue=zh boîtes=1 colonnes=0 manga(dispo=-, lues=0) ] [zones : bulles=5 texte_bulle=6 texte_libre=1]
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

Requêtes tierces servies par le faux site (donc non bloquées) : http://cdn.test/images/p1.png, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://friend.test/page.html, http://friend.test/favicon.ico, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png, http://cdn.test/bannieres-generiques/ok.png, http://adnet.test/banner.gif, http://cdn.test/ads/ad.png, http://adnet.test/ad.js, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p2.png, http://cdn.test/images/p1.png, http://adnet.test/frame.html, http://popunder.test/landing, http://cdn.test/images/p2.png, http://cdn.test/bannieres-generiques/ok.png, http://cdn.test/images/p1.png

<details><summary>journal du bloqueur</summary>

```
Journal du bloqueur — 29 action(s)
  cadre : 4
  image : 8
  pop-up : 5
  redirection (liste) : 5
  requête : 1
  script : 4
  superposition : 2

07:28:13  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
07:28:13  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
07:28:13  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
07:28:12  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
07:28:12  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
07:28:06  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
07:28:05  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
07:28:05  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
07:28:05  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
07:28:05  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
07:28:02  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
07:28:02  superposition  http://popunder.test/overlay  (page : 10.0.2.2)
07:28:00  redirection (liste)  http://popunder.test/landing  ←  ||popunder.test^  (page : 10.0.2.2)
07:27:59  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
07:27:58  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
07:27:58  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
07:27:58  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
07:27:58  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)
07:27:55  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
07:27:54  pop-up  http://popunder.test/btn  (page : 10.0.2.2)
07:27:53  redirection (liste)  http://popunder.test/clicked  ←  ||popunder.test^  (page : 10.0.2.2)
07:27:53  superposition  http://popunder.test/overlay  (page : 10.0.2.2)
07:27:48  requête  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
07:27:48  redirection (liste)  http://popunder.test/landing  ←  ||popunder.test^  (page : 10.0.2.2)
07:27:47  pop-up  http://popunder.test/p1  (page : 10.0.2.2)
07:27:46  cadre  http://adnet.test/frame.html  ←  ||adnet.test^  (page : 10.0.2.2)
07:27:46  image  http://cdn.test/ads/ad.png  ←  ||cdn.test/ads/  (page : 10.0.2.2)
07:27:46  image  http://adnet.test/banner.gif  ←  ||adnet.test^  (page : 10.0.2.2)
07:27:46  script  http://adnet.test/ad.js  ←  ||adnet.test^  (page : 10.0.2.2)

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
| raccourci : « Reprendre la lecture » publié pour l'œuvre lue | oui |

**Interface : 93/93 vérifications réussies**

# Problèmes détectés
- synth_ja_vert_mlkit: OCR 0/4