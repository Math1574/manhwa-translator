# Rapport de test automatique

## fs
- durée 15s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 0.7s (max 1.1s)
- OCR : 16 lignes (15 utilisées, 1 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| ...and the last touch. | ... et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| VWW.7ETTERCAROLCON 05/2019 | VWW.7ETTERCAROLCON 05/2019 |

## pc_en_lazy_mlkit
- durée 17s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 0.7s (max 1.0s)
- OCR : 16 lignes (15 utilisées, 1 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| ...and the last touch. | ... et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| VWW.7ETTERCAROLCON 05/2019 | VWW.7ETTERCAROLCON 05/2019 |

## pc_en_mlkit
- durée 15s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 0.8s (max 1.0s)
- OCR : 16 lignes (15 utilisées, 1 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| ...and the last touch. | ... et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| VWW.7ETTERCAROLCON 05/2019 | VWW.7ETTERCAROLCON 05/2019 |

## pc_en_ref_mlkit
- durée 15s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 0.7s (max 1.0s)
- OCR : 16 lignes (15 utilisées, 1 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| ...and the last touch. | ... et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| VWW.7ETTERCAROLCON 05/2019 | VWW.7ETTERCAROLCON 05/2019 |

## pc_ja_mlkit
- durée 17s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (1500 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 0.9s (max 1.2s)
- OCR : 15 lignes (12 utilisées, 3 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| これで。出来上がり | Avec cela. Terminer |
| う一ん足りなかったかも | Il peut ne pas suffire |
| さん | San |
| うん.... カンペキ | Ouais .... Campeki |
| ダメ! そんなことしちゃだめなんだから」 | Pas bien! Parce que ce n'est pas une telle chose " |
| これで満足?! | Est-ce satisfait ?! |

## préparation
- modèle fr : ready (1535 ms)
- modèle ja : ready (1500 ms)
- modèle ko : ready (1501 ms)
- modèle zh : ready (1500 ms)
- gemma prêt : E2B=false E4B=false

## synth_en_caps_mlkit
- durée 15s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 2.8s (max 2.8s)
- OCR : 22 lignes (22 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: PLEASE... TAKE ME AS YOUR DISCIPLE! | 100 % | oui | PLAÎT ... PRENDS-MOI COMME DISCIPLE! |
| bubble: I CAN'T BELIEVE YOU CAME ALL THE WAY HERE. | 100 % | oui | JE NE PEUX PAS CROIRE QUE TU ES VENU TOUT LE CHEMIN ICI. |
| bubble: WHO ARE YOU? WHAT DO YOU WANT FROM ME? | 100 % | oui | QUI ES-TU? QU'ATTENDS-TU DE MOI? |
| bubble: THE MASTER WILL NOT SEE ANYONE TODAY. | 100 % | oui | LE MAÎTRE NE VERRA PERSONNE AUJOURD'HUI. |
| bubble: DO I HAVE NO CHOICE BUT TO KILL HIM? | 100 % | oui | EST-CE QUE JE N'AI PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: NO CHOICE BUT TO KILL HIM? | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: THUD! | 100 % | oui | BRUIT SOURD! |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**

## synth_en_mixed_mlkit
- durée 15s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 2.8s (max 2.8s)
- OCR : 17 lignes (17 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: Please... take me as your disciple! | 100 % | oui | Plaît ... Prends-moi comme disciple! |
| bubble: I can't believe you came all the way here. | 100 % | oui | Je ne peux pas croire que tu viennes tout le chemin ici. |
| bubble: Who are you? What do you want from me? | 100 % | oui | Qui es-tu? Qu'attends-tu de moi? |
| bubble: The master will not see anyone today. | 100 % | oui | Le maître ne verra personne aujourd'hui. |
| bubble: Do I have no choice but to kill him? | 100 % | oui | Est-ce que je n'ai pas d'autre choix que de le tuer? |
| free: You will regret this. | 100 % | oui | Vous le regretterez. |
| free: Boom! | 100 % | oui | Boom! |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**

## synth_ja_vert_mlkit
- durée 15s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (1504 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 1.9s (max 1.9s)
- OCR : 10 lignes (10 utilisées, 0 écartées) · bulles affichées 4 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| vertical: お願いします…弟子にしてください！ | 40 % | oui | S'il vous plaît: s'il vous plaît donnez-moi! |
| vertical: ここまで来るとは信じられない。 | 43 % | oui | Je ne peux pas croire que ça viendra ici |
| vertical: お前は誰だ？何が望みだ？ | 50 % | oui | Êtes-vous? Quel est l'espoir? |
| vertical: 師匠は今日は誰とも会わない。 | 46 % | oui | Le maître rencontre également n'importe qui aujourd'hui |

**Lecture OCR correcte : 0/4 · éléments traduits : 4/4**

## synth_ko_mlkit
- durée 15s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · KO→FR
- note : modèle ko : ready (1501 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 2.3s (max 2.3s)
- OCR : 14 lignes (13 utilisées, 1 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 제발... 저를 제자로 받아주세요! | 100 % | oui | S'il vous plaît ... S'il vous plaît, prenez-moi comme disciple! |
| bubble: 여기까지 오시다니 믿을 수 없군요. | 100 % | oui | Je ne peux pas y croire. |
| bubble: 당신은 누구십니까? 저에게 무엇을 원하시나요? | 100 % | oui | Qui es-tu? Que veux-tu que je suis? |
| bubble: 스승님은 오늘 아무도 만나지 않으실 겁니다. | 100 % | oui | Maître ne rencontrera personne aujourd'hui. |
| bubble: 그를 죽일 수밖에 없는 건가? | 100 % | oui | Avez-vous de le tuer? |
| free: 후회하게 될 거다. | 100 % | oui | Je vais le regretter. |
| free: 털썩 | 100 % | **NON** |  |

**Lecture OCR correcte : 7/7 · éléments traduits : 6/7**

## synth_zh_mlkit
- durée 15s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · ZH_HANS→FR
- note : modèle zh : ready (1500 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 2.4s (max 2.4s)
- OCR : 12 lignes (12 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 求求你……收我为徒吧！ | 100 % | oui | Je vous prie de vous ... je me suis reçu! |
| bubble: 没想到你竟然大老远跑到这里来。 | 93 % | oui | Je ne m'attendais pas à ce que tu sois un grand vieux | |
| bubble: 你是谁？你想从我这里得到什么？ | 100 % | oui | Qui es-tu? Que veux-tu de me tirer de moi? |
| bubble: 师父今天不见任何人。 | 100 % | oui | Le maître ne verra personne aujourd'hui. |
| bubble: 难道我别无选择，只能杀了他吗？ | 100 % | oui | Je ne choisis pas, puis-je seulement le tuer? |
| free: 你会后悔的。 | 100 % | oui | Vous le regretterez. |
| free: 咚！ | 100 % | oui | 咚! |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**


# Problèmes détectés
- synth_ja_vert_mlkit: OCR 0/4
- synth_ko_mlkit: traduits 6/7