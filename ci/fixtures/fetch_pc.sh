#!/usr/bin/env bash
# Télécharge quelques pages de Pepper&Carrot (David Revoy, CC-BY 4.0, https://www.peppercarrot.com) en plusieurs langues.
OUT=${1:-/tmp/site/pc}
B=https://www.peppercarrot.com/0_sources
for lang in en fr ja kr cn; do
  mkdir -p $OUT/$lang
  for ep in "ep01_Potion-of-Flight E01 1 2 3 4 5" "ep06_The-Potion-Contest E06 1 2 3"; do
    set -- $ep; dir=$1; code=$2; shift 2
    for p in "$@"; do
      P=$(printf "P%02d" $p)
      curl -sSfL -m 60 -o $OUT/$lang/${code}${P}.jpg "$B/$dir/low-res/${lang}_Pepper-and-Carrot_by-David-Revoy_${code}${P}.jpg" || { echo "absent: $lang $code$P"; rm -f $OUT/$lang/${code}${P}.jpg; }
    done
  done
done
ls -la $OUT/*/ | head -60
