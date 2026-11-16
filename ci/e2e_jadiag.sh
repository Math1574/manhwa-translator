# Suite « jadiag » (reprise par ci/e2e.sh) — vérification de la correction « fin de page annoncée sous about:blank » : la scène japonaise avec le vrai modèle Gemma n'a repéré aucune image dans une exécution complète (alors que la même page passe avec ML Kit
# et que les scènes anglaise et coréenne passent avec Gemma). On rejoue la scène seule, avec le journal complet de chaque passage (ci/e2e.sh : variable DIAG), pour voir ce qui se passe
# entre le chargement de la page et le repérage des images. Variables et fonctions héritées de ci/e2e.sh : PKG, OUT, say, shot, push_model, run.
E2B=/tmp/gemma/gemma-4-E2B-it.litertlm
if [ ! -f $E2B ]; then
  say "jadiag : modèle Gemma E2B absent de l'émulateur de test"
  exit 0
fi
push_model $E2B gemma-4-E2B-it.litertlm 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
DIAG=1; TRIES=1
run ja_gemma_1        pc_ja.html GEMMA_E2B JA FR "" 300
run en_gemma_ctrl     pc_en.html GEMMA_E2B EN FR "" 300
run ja_gemma_2        pc_ja.html GEMMA_E2B JA FR "" 300
EXTRA=(--es mt_enhanced false)
run ja_gemma_nopaddle pc_ja.html GEMMA_E2B JA FR "" 300
EXTRA=()
run ja_mlkit_nomodel  pc_ja.html MLKIT JA FR "" 120
run ja_mlkit_2        pc_ja.html MLKIT JA FR "" 120
run ja_mlkit_3        pc_ja.html MLKIT JA FR "" 120
say "jadiag terminé"
