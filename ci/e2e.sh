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
run pc_en_lazy_mlkit     pc_en_lazy.html     MLKIT EN FR "fr"      300 lazy
run synth_ko_mlkit       synth_ko.html       MLKIT KO FR "ko,fr"   300
run synth_ja_vert_mlkit  synth_ja_vertical.html MLKIT JA FR "ja,fr" 300
run synth_zh_mlkit       synth_zh.html       MLKIT ZH_HANS FR "zh,fr" 300
run pc_ja_mlkit          pc_ja.html          MLKIT JA FR "ja,fr"   300

# --- interface : plein écran d'un appui, onglets, bouton Original | Traduit, bibliothèque par œuvre, réglages en sections ---
say "=== interface"
UI=$OUT/reports/ui.tsv; : > $UI
uidump() { adb shell uiautomator dump /sdcard/ui_$1.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui_$1.xml > "$OUT/reports/ui_$1.xml" 2>/dev/null; }
has() { if grep -qi -- "$2" "$OUT/reports/ui_$1.xml" 2>/dev/null; then echo 1; else echo 0; fi; }
hasnot() { if grep -qi -- "$2" "$OUT/reports/ui_$1.xml" 2>/dev/null; then echo 0; else echo 1; fi; }
chk() { printf '%s\t%s\n' "$1" "$2" >> $UI; }
tapt() { local xy; xy=$(python3 ci/ui_tap.py "$OUT/reports/ui_$1.xml" "$2" ${3:-1}) && adb shell input tap $xy; }
# l'émulateur affiche parfois « Pixel Launcher ne répond plus » : cette fenêtre absorbe le premier appui, on la ferme avant de commencer
for k in 1 2 3; do
  uidump anr; if grep -q "isn't responding\|not responding" "$OUT/reports/ui_anr.xml" 2>/dev/null; then tapt anr "Wait" || adb shell input keyevent KEYCODE_BACK; sleep 1; else break; fi
done
adb shell am force-stop $PKG
adb shell "run-as $PKG rm -f files/ui.json"
adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_url "http://10.0.2.2:8000/comics/test-series-1a2b3c/chapter/1.html" --es mt_engine MLKIT --es mt_source EN \
   --es mt_target FR --es mt_models "fr" --es mt_timeout 200 --es mt_report ui.json --es mt_fullscreen true >/dev/null
for i in $(seq 1 40); do sleep 6; adb shell "run-as $PKG ls files/ui.json" 2>/dev/null | grep -q ui.json && break; done
adb exec-out "run-as $PKG cat files/ui.json" > $OUT/reports/fs.json
sleep 5
for k in 1 2 3; do
  uidump anr; if grep -q "isn't responding\|not responding" "$OUT/reports/ui_anr.xml" 2>/dev/null; then tapt anr "Wait" || adb shell input keyevent KEYCODE_BACK; sleep 1; else break; fi
done
shot ui 0
uidump fs0; chk "plein écran : rien d'affiché au repos" $(hasnot fs0 'Quitter le plein')
adb shell input tap 540 1200; sleep 2; uidump fs1; chk "plein écran : un appui ouvre le menu de lecture" $(has fs1 'Quitter le plein'); shot ui 1
chk "plein écran : le menu propose Original | Traduit et les chapitres" $([ "$(has fs1 'Original')" = 1 ] && [ "$(has fs1 'Traduit')" = 1 ] && [ "$(has fs1 'Suivant')" = 1 ] && echo 1 || echo 0)
adb shell input tap 540 1200; sleep 2; uidump fs2; chk "plein écran : un second appui le referme" $(hasnot fs2 'Quitter le plein')
adb shell input tap 540 1200; sleep 2; uidump fs3; tapt fs3 "Quitter le plein"; sleep 2; uidump nav
chk "plein écran : le bouton du menu le quitte (onglets de retour)" $(has nav 'Biblioth')
chk "onglets : Lecture, Bibliothèque, Réglages" $([ "$(has nav 'Lecture')" = 1 ] && [ "$(has nav 'Biblioth')" = 1 ] && [ "$(has nav 'glages')" = 1 ] && echo 1 || echo 0)
chk "bouton rond Original | Traduit présent hors plein écran" $([ "$(has nav 'Traduction activ')" = 1 ] || [ "$(has nav 'Traduire')" = 1 ] && echo 1 || echo 0)
shot ui 5
adb shell dumpsys window | grep -E "mCurrentFocus|isStatusBarVisible|statusBar.*visible" | head -3 | tee -a $OUT/run.log
tapt nav "=Bibliothèque" 1; sleep 2; uidump lib; shot ui 2
chk "bibliothèque : l'œuvre est listée avec « Continuer la lecture »" $([ "$(has lib 'Test Series')" = 1 ] && [ "$(has lib 'Continuer la lecture')" = 1 ] && echo 1 || echo 0)
tapt lib "=Test Series" 2 top; sleep 2; uidump series; shot ui 3
chk "fiche d'une œuvre : liste des chapitres" $(has series 'Chapitres')
chk "fiche d'une œuvre : page de l'œuvre et reprise proposées" $([ "$(has series 'Page de l')" = 1 ] && [ "$(has series 'Continuer')$(has series 'Reprendre')" != "00" ] && echo 1 || echo 0)
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump lib2
chk "retour de la fiche vers la bibliothèque" $(has lib2 'Historique')
tapt lib2 "=Réglages" 1; sleep 2; uidump set; shot ui 4
chk "réglages : une carte par thème" $([ "$(has set 'Reconnaissance du texte')" = 1 ] && [ "$(has set 'Navigation')" = 1 ] && [ "$(has set 'Donn')" = 1 ] && [ "$(has set 'Glossaire')" = 1 ] && echo 1 || echo 0)
tapt set "=Traduction" 1 top; sleep 2; uidump settr
chk "réglages : la carte Traduction ouvre ses options (langues)" $(has settr 'Langue du texte')
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump set2
tapt set2 "=Lecture" 1 top; sleep 2; uidump setrd; shot ui 6
chk "réglages : la carte Lecture ouvre ses options (bouton, barres)" $([ "$(has setrd 'Masquer les barres')" = 1 ] && echo 1 || echo 0)
adb shell input keyevent KEYCODE_BACK; sleep 1.5; uidump set3
tapt set3 "=Apparence" 1 top; sleep 2; uidump setap; shot ui 7
chk "réglages : la carte Apparence propose mascotte, accent et fond" $([ "$(has setap 'Mascotte')" = 1 ] && [ "$(has setap 'Couleur d')" = 1 ] && echo 1 || echo 0)
tapt setap "=Hibou savant" 1; sleep 1.5; uidump setap2; shot ui 8
chk "réglages : choisir une autre mascotte met à jour sa présentation" $(has setap2 'Hibou savant : il relit')
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
