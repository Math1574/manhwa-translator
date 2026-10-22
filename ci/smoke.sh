#!/usr/bin/env bash
# Test de fumée sur émulateur : installation, démarrage, navigation, captures, recherche de plantages.
PKG=app.manhwa.translator
OUT=/tmp/smoke; mkdir -p $OUT
log() { echo "$@" | tee -a $OUT/report.txt; }
adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard 2>/dev/null || true
adb install -r apk/app-debug.apk 2>&1 | tee -a $OUT/report.txt
adb logcat -c
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 12
PID=$(adb shell pidof $PKG | tr -d '\r'); log "PID après démarrage: '${PID}'"
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}; log "écran: $W x $H"
shot() { adb exec-out screencap -p > $OUT/$1.png; }
tab() { adb shell input tap $(( W*(2*$1+1)/8 )) $(( H-150 )); sleep 3; }
shot 01-navigateur
tab 1; shot 02-modeles
tab 2; shot 03-glossaire
tab 3; shot 04-reglages
tab 0; sleep 1
# saisie d'une adresse dans le navigateur
adb shell input tap $(( W/2 )) $(( 330 )); sleep 1
adb shell input text "example.org"; adb shell input keyevent KEYCODE_ENTER; sleep 10
shot 05-example-org
PID2=$(adb shell pidof $PKG | tr -d '\r'); log "PID en fin de test: '${PID2}'"
adb logcat -d > $OUT/logcat.txt
log "--- plantages détectés :"
grep -E "FATAL EXCEPTION|Process: $PKG|AndroidRuntime: " $OUT/logcat.txt | head -30 | tee -a $OUT/report.txt
grep -E "manhwa|MlKit|chromium.*ERROR" $OUT/logcat.txt | grep -E " E | W " | head -30 > $OUT/app-warnings.txt
if [ -z "$PID" ] || [ -z "$PID2" ] || grep -q "FATAL EXCEPTION" $OUT/logcat.txt; then log "RESULTAT: ECHEC"; else log "RESULTAT: OK (pas de plantage)"; fi
