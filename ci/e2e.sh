#!/usr/bin/env bash
# Scénarios de bout en bout sur émulateur : installe l'APK, charge des pages de test, déclenche la traduction,
# récupère le rapport JSON de l'appli, des captures d'écran et les journaux.
PKG=app.manhwa.translator
OUT=/tmp/e2e; mkdir -p $OUT/shots $OUT/reports
SUITE=${SUITE:-quick}
EXTRA=()
say() { echo "[e2e $(date +%H:%M:%S)] $*" | tee -a $OUT/run.log; }

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null || true
adb shell settings put secure immersive_mode_confirmations confirmed; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0
adb shell settings put global hide_error_dialogs 1 2>/dev/null || true
adb install -r apk/app-debug.apk 2>&1 | tee -a $OUT/run.log
adb shell wm size | tee -a $OUT/run.log

push_model() { # $1 fichier, $2 nom de fichier dans l'appli, $3 somme sha256
  say "envoi du modèle $2 sur l'émulateur"
  adb push "$1" /data/local/tmp/model.bin >/dev/null
  adb shell "run-as $PKG mkdir -p files/models"
  adb shell "cat /data/local/tmp/model.bin | run-as $PKG sh -c 'cat > files/models/$2'"
  adb shell "echo -n $3 | run-as $PKG sh -c 'cat > files/models/$2.ok'"
  adb shell rm -f /data/local/tmp/model.bin
  adb shell "run-as $PKG ls -la files/models" | tee -a $OUT/run.log
}

shot() { # $1 nom, $2 index
  adb exec-out screencap -p | convert - -resize 540x -quality 70 "$OUT/shots/$1-$2.jpg" 2>/dev/null
}

run() { # nom page moteur source cible modèles timeout [lazy]
  local name=$1 page=$2 engine=$3 src=$4 tgt=$5 models=$6 timeout=$7 mode=$8
  say "=== $name ($page, $engine, $src→$tgt)"
  adb shell am force-stop $PKG
  adb shell "run-as $PKG rm -f files/report.json"
  adb logcat -c
  local mdl=(); [ -n "$models" ] && mdl=(--es mt_models "$models")
  local ext=("${EXTRA[@]}")
  adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_url "http://10.0.2.2:8000/$page" --es mt_engine $engine \
     --es mt_source $src --es mt_target $tgt "${mdl[@]}" "${ext[@]}" --es mt_timeout $timeout >/dev/null
  local start=$(date +%s)
  while :; do
    sleep 6
    if adb shell "run-as $PKG ls files/report.json" 2>/dev/null | grep -q report.json; then break; fi
    local extra=180; [ "$name" = prep ] && extra=1500
    if [ $(( $(date +%s) - start )) -gt $((timeout + extra)) ]; then say "délai dépassé sans rapport"; break; fi
    if [ "$mode" = lazy ]; then adb shell input swipe 540 1900 540 800 500; fi
  done
  adb exec-out "run-as $PKG cat files/report.json" > $OUT/reports/$name.json
  adb shell dumpsys meminfo $PKG 2>/dev/null | grep -m3 -E "TOTAL" > $OUT/reports/$name.mem
  adb logcat -d > $OUT/reports/$name.full.log
  grep -E "MTTEST|FATAL|AndroidRuntime|litert|LiteRT|tflite|OutOfMemory|ANR| MT +: " $OUT/reports/$name.full.log | head -80 > $OUT/reports/$name.log
  rm -f $OUT/reports/$name.full.log
  for i in 1 2 3 4 5 6; do adb shell input swipe 540 700 540 2000 250; done; sleep 1
  shot $name 0
  for i in 1 2 3; do adb shell input swipe 540 1900 540 700 450; sleep 2; shot $name $i; done
  say "terminé : $name ($(( $(date +%s) - start )) s)"
  if grep -q '"total":0' $OUT/reports/$name.json 2>/dev/null && [ "$TRIES" != 1 ]; then say "aucune image traitée : nouvel essai de $name"; TRIES=1 run "$@"; fi
}

lab() { # nom moteur variantes
  say "=== banc d'essai de traduction : $1 ($2, variantes $3)"
  adb push ci/lab/lab.json /data/local/tmp/lab.json >/dev/null
  adb shell "cat /data/local/tmp/lab.json | run-as $PKG sh -c 'cat > files/lab.json'"
  adb shell am force-stop $PKG; adb shell "run-as $PKG rm -f files/lab_$1.json"
  adb logcat -c
  adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_lab $1 --es mt_engine $2 --es mt_variants $3 --es mt_series "The Heavenly Demon Wants A Quiet Life" >/dev/null
  local start=$(date +%s) dead=0
  while :; do
    sleep 10
    adb shell "run-as $PKG ls files/lab_$1.json" 2>/dev/null | grep -q "lab_$1.json" && break
    if [ -z "$(adb shell pidof $PKG | tr -d '\r')" ]; then dead=$((dead+1)); else dead=0; fi
    [ $dead -ge 3 ] && { say "banc d'essai : l'application s'est arrêtée"; break; }
    [ $(( $(date +%s) - start )) -gt 2400 ] && { say "banc d'essai : délai dépassé"; break; }
  done
  adb exec-out "run-as $PKG cat files/lab_$1.json" > $OUT/reports/lab_$1.json 2>/dev/null
  [ -s $OUT/reports/lab_$1.json ] && grep -q '"variants"' $OUT/reports/lab_$1.json || adb exec-out "run-as $PKG cat files/lab_$1.partial.json" > $OUT/reports/lab_$1.json 2>/dev/null
  adb logcat -d | grep -E "MTTEST|FATAL|AndroidRuntime|litert|LiteRT|OutOfMemory|lowmemorykiller|ANR|F DEBUG" | head -80 > $OUT/reports/lab_$1.log
  say "banc d'essai terminé : $1 ($(( $(date +%s) - start )) s)"
}

if [ "$SUITE" != lab ]; then
# --- préparation : téléchargement des modèles de traduction ML Kit (première utilisation lente) ---
run prep index.html MLKIT EN FR "fr,ja,ko,zh" 30

# --- suite rapide : ML Kit (OCR + traduction légère) ---
run pc_en_mlkit          pc_en.html          MLKIT EN FR "fr"      300
run synth_en_caps_mlkit  synth_en_caps.html  MLKIT EN FR "fr"      200
run synth_en_mixed_mlkit synth_en_mixed.html MLKIT EN FR "fr"      200
run synth_en_variety_combined synth_en_variety.html MLKIT EN FR "fr" 300
run adblock_mlkit adtest.html MLKIT EN FR "fr" 200
run synth_layouts_combined synth_layouts.html MLKIT EN FR "fr" 400
run synth_atypical_combined synth_atypical.html MLKIT EN FR "fr" 400
EXTRA=(--es mt_enhanced false)
run synth_en_fancy_mlkitonly synth_en_fancy.html MLKIT EN FR "fr" 250
EXTRA=()
run synth_en_fancy_combined  synth_en_fancy.html MLKIT EN FR "fr" 300
run pc_en_ref_mlkit      pc_en_ref.html      MLKIT EN FR "fr"      300
# textes coupés entre deux images collées : avec la jonction (lecture entière de la bulle, posée sur les deux images), puis sans (témoin : fragments)
run seam_mlkit           seam_cut.html       MLKIT EN FR "fr"      300
EXTRA=(--es mt_noseam true)
run seam_off_mlkit       seam_cut.html       MLKIT EN FR "fr"      300
EXTRA=()
# file à deux traitements + regroupement des traductions (ML Kit passe aussi par le regroupeur) : trois pages, puis un long chapitre de 36 images
EXTRA=(--es mt_batch true)
run pc_en_batch_mlkit    pc_en.html          MLKIT EN FR "fr"      300
run pc_en_long_mlkit     pc_en_long.html     MLKIT EN FR "fr"      900
# lecteur qui bascule sur « Original » puis sur « Traduit » en cours de traduction : aucune image ne doit rester sans traduction (la barre de progression allait jusqu'au bout)
EXTRA=(--es mt_toggle 5000)
run pc_en_toggle_mlkit   pc_en_toggle.html   MLKIT EN FR "fr"      500
EXTRA=()
# images lues dans la WebView : adresses à usage unique (un second téléchargement recevrait 403)
run pc_en_once_mlkit     pc_en_once.html     MLKIT EN FR "fr"      300
# repli : téléchargement séparé seul (lecture par la WebView coupée), avec les en-têtes que la WebView envoie
OLDX=("${EXTRA[@]}"); EXTRA=("${OLDX[@]}" --es mt_nocdp true)
run pc_en_strict_mlkit   pc_en_strict.html   MLKIT EN FR "fr"      300
EXTRA=("${OLDX[@]}")
run pc_en_lazy_mlkit     pc_en_lazy.html     MLKIT EN FR "fr"      300 lazy
run synth_ko_mlkit       synth_ko.html       MLKIT KO FR "ko,fr"   300
run synth_ja_vert_mlkit  synth_ja_vertical.html MLKIT JA FR "ja,fr" 300
run synth_zh_mlkit       synth_zh.html       MLKIT ZH_HANS FR "zh,fr" 300
run pc_ja_mlkit          pc_ja.html          MLKIT JA FR "ja,fr"   300

# --- bloqueur de publicités : page piégée (régies, pop-ups, redirection, superposition invisible, vrais liens), appuis réels dans la WebView ---
say "=== bloqueur de publicités"
adb shell am force-stop $PKG
adb shell "run-as $PKG rm -f files/adtest.json"
adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_adtest 1 --es mt_report adtest.json >/dev/null
for i in $(seq 1 100); do
  sleep 3
  if adb shell "run-as $PKG ls files/adtest.json" 2>/dev/null | grep -q adtest.json; then break; fi
done
adb shell "run-as $PKG cat files/adtest.json" > $OUT/reports/adtest.json 2>/dev/null || true
adb exec-out screencap -p | convert - -resize 540x -quality 70 "$OUT/shots/adtest-0.jpg" 2>/dev/null
say "bloqueur terminé ($(wc -c < $OUT/reports/adtest.json) octets de rapport)"

# --- interface : plein écran d'un appui, onglets, bouton Original | Traduit, bibliothèque par œuvre, réglages en sections ---
say "=== interface"
UI=$OUT/reports/ui.tsv; : > $UI
uidump() { adb shell uiautomator dump /sdcard/ui_$1.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui_$1.xml > "$OUT/reports/ui_$1.xml" 2>/dev/null; }
has() { if grep -qi -- "$2" "$OUT/reports/ui_$1.xml" 2>/dev/null; then echo 1; else echo 0; fi; }
hasnot() { if grep -qi -- "$2" "$OUT/reports/ui_$1.xml" 2>/dev/null; then echo 0; else echo 1; fi; }
chk() { printf '%s\t%s\n' "$1" "$2" >> $UI; }
dismiss_anr() { for k in 1 2 3 4; do if adb shell dumpsys window | grep -q "Application Not Responding"; then say "fenêtre « ne répond plus » du système fermée"; adb shell input keyevent KEYCODE_BACK; sleep 1.5; else break; fi; done; }
tap() { dismiss_anr; adb shell input tap "$@"; }
tapt() { local xy; dismiss_anr; xy=$(python3 ci/ui_tap.py "$OUT/reports/ui_$1.xml" "$2" ${3:-1}) && adb shell input tap $xy; }
# appui long : un « balayage » immobile d'un peu plus d'une seconde au centre du nœud
holdt() { local xy; dismiss_anr; xy=$(python3 ci/ui_tap.py "$OUT/reports/ui_$1.xml" "$2" ${3:-1}) && adb shell input swipe $xy $xy 1100; }
# l'émulateur affiche parfois « Pixel Launcher ne répond plus » : cette fenêtre absorbe le premier appui, on la ferme avant de commencer
for k in 1 2 3; do
  uidump anr; if grep -q "isn't responding\|not responding" "$OUT/reports/ui_anr.xml" 2>/dev/null; then tapt anr "Wait" || adb shell input keyevent KEYCODE_BACK; sleep 1; else break; fi
done
adb shell am force-stop $PKG
adb shell "run-as $PKG rm -f files/ui.json"
adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_url "http://10.0.2.2:8000/comics/test-series-1a2b3c/chapter/1.html" --es mt_engine MLKIT --es mt_source EN \
   --es mt_target FR --es mt_models "fr" --es mt_timeout 200 --es mt_report ui.json --es mt_fullscreen true --es mt_watch true --es mt_undo_ms 20000 >/dev/null
for i in $(seq 1 40); do sleep 6; adb shell "run-as $PKG ls files/ui.json" 2>/dev/null | grep -q ui.json && break; done
adb exec-out "run-as $PKG cat files/ui.json" > $OUT/reports/fs.json
sleep 5
for k in 1 2 3; do
  uidump anr; if grep -q "isn't responding\|not responding" "$OUT/reports/ui_anr.xml" 2>/dev/null; then tapt anr "Wait" || adb shell input keyevent KEYCODE_BACK; sleep 1; else break; fi
done
shot ui 0
uidump fs0; chk "plein écran : rien d'affiché au repos" $(hasnot fs0 'Quitter le plein')
# diagnostic : quelle fenêtre a le focus, et quelle activité est au premier plan, avant le premier appui
adb shell dumpsys window | grep -E "mCurrentFocus" | head -2 | sed 's/^/  [avant appui] /' | tee -a $OUT/run.log
adb shell dumpsys activity activities | grep -E "ResumedActivity" | head -2 | sed 's/^/  [avant appui] /' | tee -a $OUT/run.log
tap 540 1200; sleep 2; adb shell dumpsys window | grep -E "mCurrentFocus" | head -1 | sed 's/^/  [après appui] /' | tee -a $OUT/run.log; uidump fs1; chk "plein écran : un appui ouvre le menu de lecture" $(has fs1 'Quitter le plein'); shot ui 1
chk "plein écran : le menu propose Original | Traduit et les chapitres" $([ "$(has fs1 'Original')" = 1 ] && [ "$(has fs1 'Traduit')" = 1 ] && [ "$(has fs1 'Suivant')" = 1 ] && echo 1 || echo 0)
tap 540 1200; sleep 2; uidump fs2; chk "plein écran : un second appui le referme" $(hasnot fs2 'Quitter le plein')
# réglages rapides depuis le plein écran : appui long sur « Traduit » (ou « Original ») du menu de lecture ; le retour referme le panneau sans quitter le plein écran
tap 540 1200; sleep 1.5; uidump fsq0
{ holdt fsq0 "=Traduit" || holdt fsq0 "=Original"; }; sleep 1.5; uidump fsq1; shot ui 13
chk "réglages rapides : un appui long sur Original | Traduit les ouvre aussi en plein écran" $(has fsq1 'glages rapides')
if [ "$(has fsq1 'glages rapides')" = 1 ]; then
  adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump fsq2
  chk "réglages rapides : le retour referme le panneau et reste en plein écran" $([ "$(hasnot fsq2 'glages rapides')" = 1 ] && [ "$(hasnot fsq2 'Biblioth')" = 1 ] && echo 1 || echo 0)
else
  chk "réglages rapides : le retour referme le panneau et reste en plein écran" 0
  sleep 4   # le menu de lecture se range seul au bout de 5 s : le prochain appui doit le rouvrir
fi
tap 540 1200; sleep 2; uidump fs3; tapt fs3 "Quitter le plein"; sleep 2; uidump nav
chk "plein écran : le bouton du menu le quitte (onglets de retour)" $(has nav 'Biblioth')
chk "onglets : Lecture, Bibliothèque, Réglages" $([ "$(has nav 'Lecture')" = 1 ] && [ "$(has nav 'Biblioth')" = 1 ] && [ "$(has nav 'glages')" = 1 ] && echo 1 || echo 0)
chk "bouton rond Original | Traduit présent hors plein écran" $([ "$(has nav 'Traduction activ')" = 1 ] || [ "$(has nav 'Traduire')" = 1 ] && echo 1 || echo 0)
shot ui 5
adb shell dumpsys window | grep -E "mCurrentFocus|isStatusBarVisible|statusBar.*visible" | head -3 | tee -a $OUT/run.log
# suppression annulable : l'appui long sur une traduction est rejoué par une commande (même proposition « Corriger / supprimer »), puis vrais appuis sur la corbeille et sur « Annuler » ;
# l'état de l'appli (bloc supprimé ou non, corrections) est relevé dans files/state.json. Fait avant les réglages rapides : la langue est encore anglais → français (résultat en cache repris)
appcmd() { adb shell "echo '$*' | run-as $PKG sh -c 'cat > files/cmd.txt'"; sleep 1.5; }
appstate() { adb shell "run-as $PKG rm -f files/state.json"; adb shell "echo state | run-as $PKG sh -c 'cat > files/cmd.txt'"; local i; for i in 1 2 3 4 5 6 7 8; do sleep 1; adb exec-out "run-as $PKG cat files/state.json" > $OUT/reports/state.json 2>/dev/null; grep -q '"works"' $OUT/reports/state.json 2>/dev/null && break; done; }
stv() { python3 -c "import json;print(json.load(open('$OUT/reports/state.json')).get('$1',''))" 2>/dev/null; }
waitst() { local i; for i in $(seq 1 ${3:-15}); do appstate; [ "$(stv $1)" = "$2" ] && return 0; done; return 1; }
okst() { if "$@"; then echo 1; else echo 0; fi; }
appcmd window 20000
appcmd peek; uidump pk; shot ui 14
chk "suppression : un appui long sur une traduction propose de la corriger ou de la supprimer" $([ "$(has pk 'Corriger cette traduction')" = 1 ] && [ "$(has pk 'Supprimer cette traduction')" = 1 ] && echo 1 || echo 0)
chk "suppression : l'état de départ est sain (aucune traduction supprimée, au moins un bloc)" $(okst waitst hidden 0 5)
blocks0=$(stv blocks)
tapt pk "Supprimer cette traduction" 1; sleep 1.5; uidump pk2; shot ui 15
chk "suppression : le bandeau « Annuler » apparaît après la suppression" $(has pk2 'Traduction supprim')
chk "suppression : la proposition « Corriger » disparaît avec la corbeille" $(hasnot pk2 'Corriger cette traduction')
chk "suppression : la traduction est supprimée (original rétabli, correction enregistrée)" $(okst waitst hidden 1 25)
chk "suppression : le bloc reste repérable (même nombre de blocs que avant)" $([ "$(stv blocks)" = "$blocks0" ] && echo 1 || echo 0)
tapt pk2 "Annuler" 1; sleep 1
chk "annulation : « Annuler » rétablit la traduction (plus de bloc supprimé, aucune correction)" $([ "$(okst waitst hidden 0 25)" = 1 ] && [ "$(stv edits)" = 0 ] && echo 1 || echo 0)
uidump pk3; chk "annulation : le bandeau se referme" $(hasnot pk3 'Traduction supprim')
# une correction faite à la main avant la suppression doit revenir telle quelle après « Annuler »
appcmd setedit ANNULERTEST
chk "annulation : une traduction corrigée à la main est enregistrée (départ)" $(okst waitst editTr ANNULERTEST 25)
appcmd peek; uidump pk4; tapt pk4 "Supprimer cette traduction" 1
chk "annulation : la traduction corrigée peut elle aussi être supprimée" $(okst waitst hidden 1 25)
uidump pk5; tapt pk5 "Annuler" 1; sleep 1
chk "annulation : la correction faite à la main avant la suppression est rétablie telle quelle" $([ "$(okst waitst hidden 0 25)" = 1 ] && [ "$(stv editTr)" = ANNULERTEST ] && echo 1 || echo 0)
appcmd clearedits
chk "annulation : les corrections de test sont retirées" $(okst waitst edits 0 25)
# sans annulation : le bandeau se referme seul au bout de 3 s (durée réelle) et la suppression reste
appcmd window 3000; appcmd peek; uidump pk6; tapt pk6 "Supprimer cette traduction" 1; sleep 6; uidump pk7; shot ui 16
chk "suppression : le bandeau se referme seul au bout de 3 s" $(hasnot pk7 'Traduction supprim')
chk "suppression : sans annulation, la suppression est définitive" $([ "$(okst waitst hidden 1 25)" = 1 ] && [ "$(stv undo)" = False ] && echo 1 || echo 0)
appcmd clearedits; waitst hidden 0 25 >/dev/null
# ignorer un texte ou une image : l'appui long est rejoué par une commande, puis vrais appuis sur « Ignorer ce texte… », sur le choix de la fenêtre, sur « Annuler » et « Rétablir »
waitge() { local i; for i in $(seq 1 ${3:-15}); do appstate; [ "$(stv $1)" -ge "$2" ] 2>/dev/null && return 0; done; return 1; }
appcmd window 20000
appcmd peek; uidump ig1; shot ui 17
chk "ignorer : un appui long propose aussi « Ignorer ce texte… » (avec Corriger)" $([ "$(has ig1 'Ignorer ce texte')" = 1 ] && [ "$(has ig1 'Corriger cette traduction')" = 1 ] && echo 1 || echo 0)
tapt ig1 "Ignorer ce texte" 1; sleep 1.5; uidump ig2; shot ui 18
chk "ignorer : la fenêtre propose cette œuvre, toutes les œuvres ou toute l'image" $([ "$(has ig2 'dans cette')" = 1 ] && [ "$(has ig2 'dans toutes')" = 1 ] && [ "$(has ig2 'Toute cette image')" = 1 ] && echo 1 || echo 0)
tapt ig2 "dans cette" 1; sleep 1.5; uidump ig3; shot ui 19
chk "ignorer : le bandeau « Texte ignoré · Annuler » apparaît" $(has ig3 'Texte ignor')
chk "ignorer : le texte reste tel quel (règle enregistrée, bloc masqué, aucune correction)" $([ "$(okst waitge hidden 1 25)" = 1 ] && [ "$(stv ign)" = 1 ] && [ "$(stv edits)" = 0 ] && echo 1 || echo 0)
tapt ig3 "Annuler" 1; sleep 1
chk "ignorer : « Annuler » retire la règle et rétablit la traduction" $([ "$(okst waitst hidden 0 25)" = 1 ] && [ "$(stv ign)" = 0 ] && echo 1 || echo 0)
appcmd peek; uidump ig4; tapt ig4 "Ignorer ce texte" 1; sleep 1.5; uidump ig5; tapt ig5 "dans toutes" 1; sleep 1.5
chk "ignorer partout : la règle est enregistrée et le texte reste tel quel" $([ "$(okst waitge hidden 1 25)" = 1 ] && [ "$(stv ign)" = 1 ] && echo 1 || echo 0)
appcmd peekign; uidump ig6; shot ui 20
chk "ignorer : un appui long sur un texte ignoré propose de le rétablir (pas de « Corriger »)" $([ "$(has ig6 'tablir')" = 1 ] && [ "$(hasnot ig6 'Corriger cette traduction')" = 1 ] && echo 1 || echo 0)
tapt ig6 "tablir" 1; sleep 1.5; uidump ig7; shot ui 24
chk "ignorer : « Rétablir » retire la règle et rend la traduction" $([ "$(okst waitst hidden 0 25)" = 1 ] && [ "$(stv ign)" = 0 ] && [ "$(has ig7 'Texte r[^ ]*tabli')" = 1 ] && echo 1 || echo 0)
# image entière (bandeau) : la règle (empreinte) est enregistrée, la page n'est plus traduite ; « Annuler » la rétablit
appcmd peek; uidump ig8; tapt ig8 "Ignorer ce texte" 1; sleep 1.5; uidump ig9; tapt ig9 "Toute cette image" 1; sleep 2; uidump ig10; shot ui 21
chk "image ignorée : le bandeau « Image ignorée · Annuler » apparaît" $(has ig10 'Image ignor')
chk "image ignorée : la règle est enregistrée et l'image n'est plus traduite" $([ "$(okst waitge ignImg 1 20)" = 1 ] && [ "$(okst waitge skipped 1 40)" = 1 ] && echo 1 || echo 0)
tapt ig10 "Annuler" 1; sleep 1
chk "image ignorée : « Annuler » rétablit l'image (règle retirée, page de nouveau traduite)" $([ "$(okst waitst ignImg 0 20)" = 1 ] && [ "$(okst waitst skipped 0 60)" = 1 ] && echo 1 || echo 0)
# une règle de texte (toutes les œuvres) est laissée en place : la page des réglages doit la lister, puis la retirer
appcmd peek; uidump ig11; tapt ig11 "Ignorer ce texte" 1; sleep 1.5; uidump ig12; tapt ig12 "dans toutes" 1; sleep 1.5
waitge hidden 1 25 >/dev/null
# réglages rapides : un appui long sur le bouton rond ouvre le panneau sans basculer la traduction ; un choix de langue y est enregistré ; le retour referme le panneau
orb_on=$(has nav 'Traduction activ')
{ holdt nav "Traduction activ" || holdt nav "Traduire"; }; sleep 1.5; uidump qs; shot ui 12
chk "réglages rapides : un appui long sur le bouton rond ouvre le panneau" $(has qs 'glages rapides')
chk "réglages rapides : langue, taille du texte, onomatopées et retraduction y sont" $([ "$(has qs 'Langue du texte')" = 1 ] && [ "$(has qs 'Traduire vers')" = 1 ] && [ "$(has qs 'Taille du texte')" = 1 ] && [ "$(has qs 'onomatop')" = 1 ] && [ "$(has qs 'Retraduire la page')" = 1 ] && echo 1 || echo 0)
if [ "$(has qs 'glages rapides')" = 1 ]; then
  tapt qs "=Coréen" 1; sleep 1
  adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump qs2
  # le bouton rond est caché sous le panneau tant qu'il est ouvert : son état se compare une fois le panneau refermé
  chk "réglages rapides : l'appui long ne bascule pas la traduction (même état du bouton avant et après)" $([ "$(has qs2 'Traduction activ')" = "$orb_on" ] && echo 1 || echo 0)
  chk "réglages rapides : le retour referme le panneau, la page reste affichée" $([ "$(hasnot qs2 'glages rapides')" = 1 ] && [ "$(has qs2 'Biblioth')" = 1 ] && echo 1 || echo 0)
else
  chk "réglages rapides : l'appui long ne bascule pas la traduction (même état du bouton avant et après)" 0
  chk "réglages rapides : le retour referme le panneau, la page reste affichée" 0
fi
tapt nav "=Bibliothèque" 1; sleep 2; uidump lib; shot ui 2
chk "bibliothèque : l'œuvre est listée avec « Continuer la lecture »" $([ "$(has lib 'Test Series')" = 1 ] && [ "$(has lib 'Continuer la lecture')" = 1 ] && echo 1 || echo 0)
tapt lib "=Test Series" 2 top; sleep 2; uidump series; shot ui 3
chk "fiche d'une œuvre : liste des chapitres" $(has series 'Chapitres')
chk "fiche d'une œuvre : page de l'œuvre et reprise proposées" $([ "$(has series 'Page de l')" = 1 ] && [ "$(has series 'Continuer')$(has series 'Reprendre')" != "00" ] && echo 1 || echo 0)
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump lib2
chk "retour de la fiche vers la bibliothèque" $(has lib2 'Historique')
tapt lib2 "=Réglages" 1; sleep 2; uidump set; shot ui 4
# l'accueil des réglages est plus long qu'un écran : on le fait défiler pour voir les derniers groupes, puis on remonte et on relit l'écran du haut
adb shell input swipe 540 1900 540 500 400; sleep 1; uidump setb; shot ui 41
adb shell input swipe 540 500 540 1900 400; sleep 1; adb shell input swipe 540 500 540 1900 400; sleep 1; uidump set
hs() { if [ "$(has set "$1")" = 1 ] || [ "$(has setb "$1")" = 1 ]; then echo 1; else echo 0; fi; }
chk "réglages : la page « Textes et images ignorés » est proposée à l'accueil" $(hs 'Textes et images ignor')
chk "réglages : une carte par thème" $([ "$(hs 'Reconnaissance du texte')" = 1 ] && [ "$(hs 'Navigation')" = 1 ] && [ "$(hs 'Donn')" = 1 ] && [ "$(hs 'Glossaire')" = 1 ] && [ "$(hs 'Mod.*les de traduction')" = 1 ] && [ "$(hs 'Avanc')" = 1 ] && echo 1 || echo 0)
chk "réglages rapides : la langue choisie dans le panneau est enregistrée (résumé de Réglages)" $(has set 'Cor[^"]*Fran[^"]*ML Kit')
chk "réglages : champ de recherche, carte de mise à jour et numéro de version sur l'accueil" $([ "$(has set 'widget.EditText')" = 1 ] && [ "$(has set 'Mise .*jour de')" = 1 ] && [ "$(has set 'build [0-9]')" = 1 ] && echo 1 || echo 0)
tapt set "=Traduction" 1 top; sleep 2; uidump settr
chk "réglages : la carte Traduction ouvre ses options (langues)" $(has settr 'Langue du texte')
tapt settr "=Anglais" 1 top; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump set2
chk "réglages : choisir une langue dans la carte Traduction l'enregistre (résumé de Réglages)" $(has set2 'Anglais[^"]*Fran[^"]*ML Kit')
tapt set2 "=Lecture" 1 top; sleep 2; uidump setrd; shot ui 6
chk "réglages : la carte Lecture ouvre ses options (bouton, barres)" $([ "$(has setrd 'Masquer les barres')" = 1 ] && echo 1 || echo 0)
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump set3
tapt set3 "=Apparence" 1 top; sleep 2; uidump setap; shot ui 7
chk "réglages : la carte Apparence propose mascotte, accent et fond" $([ "$(has setap 'Mascotte')" = 1 ] && [ "$(has setap 'Couleur d')" = 1 ] && echo 1 || echo 0)
tapt setap "=Hibou savant" 1; sleep 1.5; uidump setap2; shot ui 8
chk "réglages : choisir une autre mascotte met à jour sa présentation" $(has setap2 'Hibou savant : il relit')
# recherche dans les réglages : un résultat ouvre la page Avancé ; un second mène à un groupe du bas de la page, vers lequel elle doit défiler
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump set4
tapt set4 "@EditText" 1; sleep 1; adb shell input text gpu; sleep 2; uidump srch; shot ui 9
chk "réglages : la recherche « gpu » propose l'accélération GPU, rangée dans Avancé" $([ "$(has srch 'GPU')" = 1 ] && [ "$(has srch 'Avanc')" = 1 ] && echo 1 || echo 0)
tapt srch "=Accélération GPU (expérimental)" 1; sleep 3; uidump adv; shot ui 10
chk "réglages : un résultat de recherche ouvre la page Avancé" $([ "$(has adv 'Tester CPU')" = 1 ] && echo 1 || echo 0)
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump set5
tapt set5 "Effacer la recherche" 1; sleep 1; uidump set6
tapt set6 "@EditText" 1; sleep 1; adb shell input text filtres; sleep 2; uidump srch2
tapt srch2 "=Listes de filtres" 1; sleep 3; uidump adv2; shot ui 11
chk "réglages : la recherche fait défiler la page jusqu'au groupe trouvé (bas de la page Avancé)" $([ "$(has adv2 'Voir le journal')" = 1 ] && echo 1 || echo 0)
# page « Textes et images ignorés » : atteinte par la recherche, elle liste la règle posée en lisant (texte, toutes les œuvres) et permet de la retirer
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump set7
tapt set7 "Effacer la recherche" 1; sleep 1; uidump set8
tapt set8 "@EditText" 1; sleep 1; adb shell input text ignor; sleep 2; uidump srch3
tapt srch3 "=Textes et images ignorés" 1; sleep 2.5; uidump ign1; shot ui 22
chk "ignorés : la recherche « ignor » ouvre la page « Textes et images ignorés »" $([ "$(has ign1 'Aucune image ignor')" = 1 ] && echo 1 || echo 0)
chk "ignorés : la règle posée en lisant (toutes les œuvres) y est listée avec « Retirer »" $([ "$(has ign1 'Toutes les [^ ]*uvres')" = 1 ] && [ "$(has ign1 'Retirer')" = 1 ] && echo 1 || echo 0)
tapt ign1 "Retirer" 1; sleep 1.5; uidump ign2; shot ui 23
chk "ignorés : « Retirer » supprime la règle de la liste et de l'application" $([ "$(has ign2 'Aucun texte ignor')" = 1 ] && [ "$(okst waitst ign 0 8)" = 1 ] && echo 1 || echo 0)
# diagnostic : plantages ou blocages système pendant le scénario
adb logcat -d -v brief 2>/dev/null | grep -iE "ANR in|Application Not Responding|FATAL EXCEPTION|AndroidRuntime|Process .* has died|am_crash|am_anr" | tail -30 > $OUT/reports/ui_logcat.txt
echo "  [journal système] $(wc -l < $OUT/reports/ui_logcat.txt) ligne(s) d'ANR ou de plantage" | tee -a $OUT/run.log
# raccourci « Reprendre la lecture » de l'icône : publié d'après la dernière œuvre lue
adb shell dumpsys shortcut > $OUT/reports/shortcut.txt 2>/dev/null
chk "raccourci : « Reprendre la lecture » publié pour l'œuvre lue" $([ "$(grep -c 'id=resume' $OUT/reports/shortcut.txt)" -gt 0 ] && grep -q 'Test Series' $OUT/reports/shortcut.txt && echo 1 || echo 0)

fi # fin des scénarios web (absents de la suite lab)

# --- suite complète / lab : Gemma 4 E2B ---
if [ "$SUITE" != quick ] && [ -f /tmp/gemma/gemma-4-E2B-it.litertlm ]; then
  push_model /tmp/gemma/gemma-4-E2B-it.litertlm gemma-4-E2B-it.litertlm 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
  lab e2b GEMMA_E2B v2,v3
  if [ "$SUITE" = full ]; then
  run pc_en_gemma          pc_en.html          GEMMA_E2B EN FR "" 1500
  run synth_en_caps_gemma  synth_en_caps.html  GEMMA_E2B EN FR "" 900
  run synth_ko_gemma       synth_ko.html       GEMMA_E2B KO FR "" 900
  run pc_ja_gemma          pc_ja.html          GEMMA_E2B JA FR "" 1500
  fi
fi
if [ "$SUITE" != quick ] && [ -f /tmp/gemma/gemma-4-E4B-it.litertlm ]; then
  push_model /tmp/gemma/gemma-4-E4B-it.litertlm gemma-4-E4B-it.litertlm 0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0
  lab e4b GEMMA_E4B v2,v3
  if [ "$SUITE" = full ]; then
  run pc_en_gemma4b          pc_en.html          GEMMA_E4B EN FR "" 1800
  run synth_en_caps_gemma4b  synth_en_caps.html  GEMMA_E4B EN FR "" 1200
  fi
fi
say "toutes les scènes sont terminées"
