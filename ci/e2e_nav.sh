# Suite « nav » (reprise par ci/e2e.sh) : changer de chapitre pendant que le VRAI modèle Gemma traduit (émulateur, processeur seul : lent, mais réel).
# L'application se figeait puis se fermait dans ce cas, parce que le fil principal attendait la fin de la génération. On vérifie qu'elle répond, reste ouverte, et traduit ensuite le nouveau chapitre.
# Variables et fonctions héritées de ci/e2e.sh : PKG, OUT, say, shot, push_model.

UI=$OUT/reports/ui.tsv; : > $UI
chk() { printf '%s\t%s\n' "$1" "$2" >> $UI; }
uidump() { adb shell uiautomator dump /sdcard/ui_$1.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui_$1.xml > "$OUT/reports/ui_$1.xml" 2>/dev/null; }
hasnot() { if grep -qi -- "$2" "$OUT/reports/ui_$1.xml" 2>/dev/null; then echo 0; else echo 1; fi; }
appcmd() { adb shell "echo '$*' | run-as $PKG sh -c 'cat > files/cmd.txt'"; sleep 1.5; }
appstate() { adb shell "run-as $PKG rm -f files/state.json"; adb shell "echo state | run-as $PKG sh -c 'cat > files/cmd.txt'"; local i; for i in 1 2 3 4 5 6 7 8; do sleep 1; adb exec-out "run-as $PKG cat files/state.json" > $OUT/reports/state.json 2>/dev/null; grep -q '"works"' $OUT/reports/state.json 2>/dev/null && break; done; }
stv() { python3 -c "import json;print(json.load(open('$OUT/reports/state.json')).get('$1',''))" 2>/dev/null; }
okst() { if "$@"; then echo 1; else echo 0; fi; }
alive() { adb shell pidof $PKG | tr -d '\r' | awk '{print $1}'; }

E2B=/tmp/gemma/gemma-4-E2B-it.litertlm
if [ ! -f $E2B ]; then
  say "nav : modèle Gemma E2B absent de l'émulateur de test"
  chk "chapitre suivant pendant une traduction de Gemma : modèle disponible pour le test" 0
else
  push_model $E2B gemma-4-E2B-it.litertlm 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
  CH1="http://10.0.2.2:8000/comics/test-series-1a2b3c/chapter/1.html"
  CH2="http://10.0.2.2:8000/comics/test-series-1a2b3c/chapter/2.html"
  say "=== chapitre suivant pendant une traduction par le vrai modèle (Gemma E2B)"
  adb shell am force-stop $PKG
  adb shell "run-as $PKG rm -f files/nav.json files/state.json"
  adb logcat -c
  adb shell am start -n $PKG/app.manhwa.MainActivity --es mt_url "$CH1" --es mt_engine GEMMA_E2B --es mt_source EN --es mt_target FR \
     --es mt_timeout 1500 --es mt_report nav.json --es mt_watch true >/dev/null
  sleep 4
  pid0=$(alive)
  # le chapitre suivant est demandé par l'application elle-même à l'instant où le modèle se met à traduire (une attente depuis ici, par adb, arrive trop tard : la traduction ne dure que quelques secondes),
  # puis elle relève pendant 20 s le plus long retard de réponse de son fil principal
  appcmd "gotogen $CH2"
  ok=0; start=$(date +%s)
  while [ $(( $(date +%s) - start )) -lt 900 ]; do
    appstate
    if [ "$(stv url)" = "$CH2" ]; then ok=1; break; fi
    [ -z "$(alive)" ] && { say "nav : l'application s'est arrêtée avant le changement de chapitre"; break; }
    sleep 2
  done
  say "nav : chapitre changé (ok=$ok) après $(( $(date +%s) - start )) s ; page $(stv epoch), pendant une traduction « $(stv navGen) »"
  sleep 22
  appstate; uidump nv1; shot nav 1
  say "nav : plus long retard du fil principal pendant les 20 s suivantes : « $(stv navMaxLag) » ms"
  chk "chapitre suivant pendant une traduction de Gemma : le modèle était bien en train de traduire quand le chapitre a changé" $([ "$(stv navGen)" = True ] && echo 1 || echo 0)
  chk "chapitre suivant pendant une traduction de Gemma : l'application répond sans attendre la fin de la génération (plus long retard du fil principal < 2,5 s)" $([ "$(stv navMaxLag)" -ge 0 ] 2>/dev/null && [ "$(stv navMaxLag)" -lt 2500 ] 2>/dev/null && echo 1 || echo 0)
  chk "chapitre suivant pendant une traduction de Gemma : l'application reste ouverte (même processus)" $([ -n "$pid0" ] && [ "$(alive)" = "$pid0" ] && echo 1 || echo 0)
  chk "chapitre suivant pendant une traduction de Gemma : le nouveau chapitre s'ouvre" $ok
  chk "chapitre suivant pendant une traduction de Gemma : aucune fenêtre « ne répond pas » du système" $(hasnot nv1 "isn't responding")
  # le nouveau chapitre finit par être traduit par le modèle (la génération de l'ancien se termine d'abord)
  ok=0; start=$(date +%s)
  while [ $(( $(date +%s) - start )) -lt 900 ]; do
    appstate
    if [ "$(stv url)" = "$CH2" ] && [ "$(stv prog)" = "2/2" ]; then ok=1; break; fi
    [ -z "$(alive)" ] && break
    sleep 5
  done
  say "nav : nouveau chapitre traduit (ok=$ok) après $(( $(date +%s) - start )) s, avancement « $(stv prog) »"
  chk "chapitre suivant pendant une traduction de Gemma : le nouveau chapitre est traduit ensuite" $ok
  chk "chapitre suivant pendant une traduction de Gemma : l'application est toujours ouverte à la fin" $([ -n "$pid0" ] && [ "$(alive)" = "$pid0" ] && echo 1 || echo 0)
  # plantages ou blocages de CETTE application (les autres processus de l'émulateur, comme les services Google, s'arrêtent parfois sans rapport)
  adb logcat -d -v brief 2>/dev/null | grep -iE "ANR in|Application Not Responding|FATAL EXCEPTION|Fatal signal|am_crash|am_anr|F DEBUG|Process .* has died" | grep -E "$PKG|\( *$pid0\)" | tail -30 > $OUT/reports/nav_logcat.txt
  adb logcat -d | grep -E "MTTEST|litert|LiteRT|lowmemorykiller|OutOfMemory" | tail -60 > $OUT/reports/nav.log
  chk "chapitre suivant pendant une traduction de Gemma : aucun plantage ni blocage de l'application dans le journal du système" $([ ! -s $OUT/reports/nav_logcat.txt ] && echo 1 || echo 0)
  echo "  [journal système] $(wc -l < $OUT/reports/nav_logcat.txt) ligne(s) d'ANR ou de plantage de l'application" | tee -a $OUT/run.log
fi
