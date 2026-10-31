# Rapport de test automatique

## fs
- durée 29s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 5.6s (max 6.5s)
- rendu : 1/8 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 18 lignes (15 utilisées, 3 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | .et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| WWW.PEPPERCARROT.COM OS/2014 | WWW.PEPPERCARROT.COM OS / 2014 |

## pc_en_lazy_mlkit
- durée 33s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 6.5s (max 7.7s)
- rendu : 1/8 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 18 lignes (15 utilisées, 3 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | .et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| WWW.PEPPERCARROT.COM OS/2014 | WWW.PEPPERCARROT.COM OS / 2014 |

## pc_en_mlkit
- durée 29s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 5.8s (max 6.6s)
- rendu : 1/8 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 18 lignes (15 utilisées, 3 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | .et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| WWW.PEPPERCARROT.COM OS/2014 | WWW.PEPPERCARROT.COM OS / 2014 |

## pc_en_ref_mlkit
- durée 31s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 5.8s (max 6.7s)
- rendu : 1/8 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 18 lignes (15 utilisées, 3 écartées) · bulles affichées 8 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| .and the last touch. | .et la dernière touche. |
| ...mmm probably not strong enough. | ... mmm probablement pas assez fort. |
| PLOP | PLOP |
| PLOP | PLOP |
| ha... perfect. | ha ... parfait. |
| NO! Don't even think about it. | Non! Ne pense même pas à ça. |
| Happy?! | Heureux ?! |
| WWW.PEPPERCARROT.COM OS/2014 | WWW.PEPPERCARROT.COM OS / 2014 |

## pc_ja_mlkit
- durée 33s · images 3 · traitées 3 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (1501 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 5.9s (max 6.8s)
- rendu : 1/7 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 15 lignes (13 utilisées, 2 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| original | traduction |
|---|---|
| `これで出来上がり | `Ceci est fait |
| 持`う一ん足りなかったかも | Holding `je n'étais pas assez |
| さん | San |
| うん。カンペキ | Ouais. Campeki |
| ダメ！そんなことしちゃだめなんだから | Pas bien! Parce que ce n'est pas une telle chose |
| シャ | Chaise |
| これで満足？! | Est-ce satisfait? ! |

## préparation
- modèle fr : ready (1501 ms)
- modèle ja : ready (1500 ms)
- modèle ko : ready (1500 ms)
- modèle zh : ready (1500 ms)
- gemma prêt : E2B=false E4B=false

## synth_en_caps_mlkit
- durée 19s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 7.9s (max 7.9s)
- rendu : 5/7 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 22 lignes (22 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: PLEASE... TAKE ME AS YOUR DISCIPLE! | 100 % | oui | PLAÎT ... PRENDS-MOI COMME DISCIPLE! |
| bubble: I CAN'T BELIEVE YOU CAME ALL THE WAY HERE. | 100 % | oui | JE NE PEUX PAS CROIRE QUE TU VIENNES TOUT LE CHEMIN ICI. |
| bubble: WHO ARE YOU? WHAT DO YOU WANT FROM ME? | 100 % | oui | Qui es-tu? Qu'attends-tu de moi? |
| bubble: THE MASTER WILL NOT SEE ANYONE TODAY. | 100 % | oui | LE MAÎTRE NE VERRA PERSONNE AUJOURD'HUI. |
| bubble: DO I HAVE NO CHOICE BUT TO KILL HIM? | 100 % | oui | EST-CE QUE JE N'AI PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: NO CHOICE BUT TO KILL HIM? | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: THUD! | 100 % | oui | BRUIT SOURD! |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**

## synth_en_fancy_combined
- durée 23s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 11.2s (max 11.2s)
- rendu : 2/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 15 lignes (15 utilisées, 0 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| free: NO CHOICE BUT | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| free: TO KILL HIM? | 100 % | oui | PAS D'AUTRE CHOIX QUE DE LE TUER? |
| sfx: CRACK! | 80 % | oui | CRAC |
| narration: THREE DAYS LATER, AT THE MOUNTAIN GATE. | 100 % | oui | CRAC / TROIS JOURS PLUS TARD, À LA PORTE DE MONTAGNE. |
| bubble: I HEARD MANY PEOPLE WITH SEVERED MERIDIANS WERE GENIUSES. | 100 % | oui | J'AI ENTENDU DE NOMBREUSES PERSONNES SOUFFRANT DE MÉRIDIENS COUPÉS ÉTAIENT DES GÉNIES. |
| whisper: ...he is still alive. | 100 % | oui | ... il est toujours en vie. |
| free: HEAVENLY DEMON | 100 % | oui | DÉMON CÉLESTE |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**

## synth_en_fancy_mlkitonly
- durée 19s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1501 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 7.1s (max 7.1s)
- rendu : 2/6 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 12 lignes (12 utilisées, 0 écartées) · bulles affichées 6 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| free: NO CHOICE BUT | 82 % | oui | PAS DE CHOE MAIS DE LE TUER? |
| free: TO KILL HIM? | 100 % | oui | PAS DE CHOE MAIS DE LE TUER? |
| sfx: CRACK! | 80 % | oui | CRAC |
| narration: THREE DAYS LATER, AT THE MOUNTAIN GATE. | 100 % | oui | TROIS JOURS PLUS TARD, À LA PORTE DE MONTAGNE. |
| bubble: I HEARD MANY PEOPLE WITH SEVERED MERIDIANS WERE GENIUSES. | 100 % | oui | J'AI BEAUCOUP DE GENS ATTEINTS DE MÉRIDIENS COUPÉS ÉTAIENT DES GÉNIES. |
| whisper: ...he is still alive. | 100 % | oui | Oodhe est toujours en vie. |
| free: HEAVENLY DEMON | 100 % | oui | DÉMON CÉLESTE |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**

## synth_en_mixed_mlkit
- durée 19s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · EN→FR
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 7.3s (max 7.3s)
- rendu : 5/7 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 18 lignes (18 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

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
- durée 19s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · JA→FR
- note : modèle ja : ready (1500 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 4.9s (max 4.9s)
- rendu : 5/5 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 12 lignes (11 utilisées, 1 écartées) · bulles affichées 5 · hors image 0 · chevauchements 1 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| vertical: お願いします…弟子にしてください！ | 53 % | oui | S'il vous plaît: s'il vous plaît donnez-moi! |
| vertical: ここまで来るとは信じられない。 | 86 % | oui | Je ne peux pas croire que ça viendra ici |
| vertical: お前は誰だ？何が望みだ？ | 50 % | oui | Êtes-vous? Quel est l'espoir? |
| vertical: 師匠は今日は誰とも会わない。 | 46 % | oui | Le maître rencontre également n'importe qui aujourd'hui / Saké |

**Lecture OCR correcte : 1/4 · éléments traduits : 4/4**

## synth_ko_mlkit
- durée 21s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · KO→FR
- note : modèle ko : ready (1500 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 6.6s (max 6.6s)
- rendu : 5/7 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 14 lignes (14 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 제발... 저를 제자로 받아주세요! | 100 % | oui | S'il vous plaît, acceptez-moi comme disciple! |
| bubble: 여기까지 오시다니 믿을 수 없군요. | 100 % | oui | Je ne peux pas y croire. |
| bubble: 당신은 누구십니까? 저에게 무엇을 원하시나요? | 100 % | oui | Qui es-tu? Que veux-tu que je suis? |
| bubble: 스승님은 오늘 아무도 만나지 않으실 겁니다. | 100 % | oui | Maître ne rencontrera personne aujourd'hui. |
| bubble: 그를 죽일 수밖에 없는 건가? | 100 % | oui | Devez-vous le tuer? |
| free: 후회하게 될 거다. | 100 % | oui | Le regret |
| free: 털썩 | 100 % | oui | Flumping |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**

## synth_zh_mlkit
- durée 19s · images 1 · traitées 1 · échecs 0 · sans texte 0 · moteur MLKIT · ZH_HANS→FR
- note : modèle zh : ready (1501 ms)
- note : modèle fr : ready (1500 ms)
- note : gemma prêt : E2B=false E4B=false
- temps moyen par image : 6.9s (max 6.9s)
- rendu : 5/7 bulles à forme détectée · texte ne tenant pas dans sa zone : 0
- OCR : 12 lignes (12 utilisées, 0 écartées) · bulles affichées 7 · hors image 0 · chevauchements 0 · texte < 10px 0

| texte attendu | lu par l'OCR | traduit ? | traduction |
|---|---|---|---|
| bubble: 求求你……收我为徒吧！ | 100 % | oui | Je vous prie de vous ... je vais me recevoir! |
| bubble: 没想到你竟然大老远跑到这里来。 | 100 % | oui | Je ne m'attendais pas à ce que vous veniez ici. |
| bubble: 你是谁？你想从我这里得到什么？ | 100 % | oui | Qui es-tu? Que veux-tu de me tirer de moi? |
| bubble: 师父今天不见任何人。 | 100 % | oui | Le maître ne verra personne aujourd'hui. |
| bubble: 难道我别无选择，只能杀了他吗？ | 100 % | oui | Je ne choisis pas, puis-je seulement le tuer? |
| free: 你会后悔的。 | 100 % | oui | Vous le regretterez. |
| free: 咚！ | 100 % | oui | 咚! |

**Lecture OCR correcte : 7/7 · éléments traduits : 7/7**


# Problèmes détectés
- synth_ja_vert_mlkit: mise en page (hors image 0, chevauchements 1)
- synth_ja_vert_mlkit: OCR 1/4