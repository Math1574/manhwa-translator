#!/usr/bin/env bash
# Scénarios de bout en bout sur émulateur : installe l'APK, charge des pages de test, déclenche la traduction,
# récupère le rapport JSON de l'appli, des captures d'écran et les journaux.
PKG=app.manhwa.translator
OUT=/tmp/e2e; mkdir -p $OUT/shots $OUT/reports
SUITE=${SUITE:-quick}
say() { echo "[e2e $(date +%H:%M:%S)] $*" | tee -a $OUT/run.log; }

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null || true
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0
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
  adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_url "http://10.0.2.2:8000/$page" --es mt_engine $engine \
     --es mt_source $src --es mt_target $tgt --es mt_models "$models" --es mt_timeout $timeout >/dev/null
  local start=$(date +%s)
  while :; do
    sleep 6
    if adb shell "run-as $PKG ls files/report.json" 2>/dev/null | grep -q report.json; then break; fi
    local extra=180; [ "$name" = prep ] && extra=1500
    if [ $(( $(date +%s) - start )) -gt $((timeout + extra)) ]; then say "délai dépassé sans rapport"; break; fi
    if [ "$mode" = lazy ]; then adb shell input swipe 540 1900 540 800 500; fi
  done
  adb exec-out "run-as $PKG cat files/report.json" > $OUT/reports/$name.json
  adb logcat -d > $OUT/reports/$name.full.log
  grep -E "MTTEST|FATAL|AndroidRuntime|litert|LiteRT|tflite|OutOfMemory|ANR" $OUT/reports/$name.full.log | head -60 > $OUT/reports/$name.log
  rm -f $OUT/reports/$name.full.log
  for i in 1 2 3 4 5 6; do adb shell input swipe 540 700 540 2000 250; done; sleep 1
  shot $name 0
  for i in 1 2 3; do adb shell input swipe 540 1900 540 700 450; sleep 2; shot $name $i; done
  say "terminé : $name ($(( $(date +%s) - start )) s)"
}

# --- préparation : téléchargement des modèles de traduction ML Kit (première utilisation lente) ---
run prep index.html MLKIT EN FR "fr,ja,ko,zh" 30

# --- suite rapide : ML Kit (OCR + traduction légère) ---
run pc_en_mlkit          pc_en.html          MLKIT EN FR "fr"      300
run synth_en_caps_mlkit  synth_en_caps.html  MLKIT EN FR "fr"      200
run synth_en_mixed_mlkit synth_en_mixed.html MLKIT EN FR "fr"      200
run pc_en_ref_mlkit      pc_en_ref.html      MLKIT EN FR "fr"      300
run pc_en_lazy_mlkit     pc_en_lazy.html     MLKIT EN FR "fr"      300 lazy
run synth_ko_mlkit       synth_ko.html       MLKIT KO FR "ko,fr"   300
run synth_ja_vert_mlkit  synth_ja_vertical.html MLKIT JA FR "ja,fr" 300
run synth_zh_mlkit       synth_zh.html       MLKIT ZH_HANS FR "zh,fr" 300
run pc_ja_mlkit          pc_ja.html          MLKIT JA FR "ja,fr"   300

# --- plein écran : bouton discret masqué, réapparition par triple appui ---
say "=== plein écran"
adb shell am force-stop $PKG
adb shell "run-as $PKG rm -f files/fs.json"
adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_url "http://10.0.2.2:8000/pc_en.html" --es mt_engine MLKIT --es mt_source EN \
   --es mt_target FR --es mt_models "fr" --es mt_timeout 200 --es mt_report fs.json --es mt_fullscreen true >/dev/null
for i in $(seq 1 40); do sleep 6; adb shell "run-as $PKG ls files/fs.json" 2>/dev/null | grep -q fs.json && break; done
adb exec-out "run-as $PKG cat files/fs.json" > $OUT/reports/fs.json
sleep 5; shot fullscreen 0
adb shell "input tap 540 1200; input tap 540 1200; input tap 540 1200"; shot fullscreen 1
sleep 5; shot fullscreen 2
adb shell dumpsys window | grep -E "mCurrentFocus|isStatusBarVisible|statusBar.*visible" | head -3 | tee -a $OUT/run.log

# --- suite complète : Gemma 4 E2B ---
if [ "$SUITE" = full ] && [ -f /tmp/gemma/gemma-4-E2B-it.litertlm ]; then
  push_model /tmp/gemma/gemma-4-E2B-it.litertlm gemma-4-E2B-it.litertlm 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
  run pc_en_gemma          pc_en.html          GEMMA_E2B EN FR "" 1500
  run synth_en_caps_gemma  synth_en_caps.html  GEMMA_E2B EN FR "" 900
  run synth_ko_gemma       synth_ko.html       GEMMA_E2B KO FR "" 900
  run pc_ja_gemma          pc_ja.html          GEMMA_E2B JA FR "" 1500
fi
say "toutes les scènes sont terminées"
