#!/usr/bin/env bash
# Lance un scenario k6 via l'image Docker officielle (aucune installation).
#
#   ./lancer.sh smoke
#   PALIER=2m ./lancer.sh lecture-menu
#   BASE_URL=http://host.docker.internal:5173/api/v1 ./lancer.sh limitation-debit
#
# Les mots de passe staff (K6_MDP_*) sont lus dans l'environnement, ou a
# defaut dans deploiement/loadtest/.env (jamais affiches). Rapports :
# resultats/<scenario>-<date>.html (tableau de bord k6) et .json (resume).
set -euo pipefail
cd "$(dirname "$0")"

scenario="${1:?Usage : ./lancer.sh <scenario> [options k6]}"
shift
[ -f "$scenario.js" ] || { echo "Scenario inconnu : $scenario.js" >&2; exit 2; }

fichier_env="${ENV_FILE:-../deploiement/loadtest/.env}"
if [ -z "${K6_MDP_CUISINE:-}" ] && [ -f "$fichier_env" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$fichier_env"
  set +a
fi

export BASE_URL="${BASE_URL:-http://host.docker.internal:${PORT:-8380}/api/v1}"
if [ "$scenario" != "limitation-debit" ]; then
  export METRIQUES_URL="${METRIQUES_URL:-http://host.docker.internal:${METRIQUES_PORT:-9190}/actuator/prometheus}"
fi

horodatage="$(date +%Y%m%d-%H%M%S)"
mkdir -p resultats
chmod 777 resultats 2>/dev/null || true

MSYS_NO_PATHCONV=1 docker run --rm -i \
  --add-host host.docker.internal:host-gateway \
  -v "$PWD:/scripts" -w /scripts \
  -e BASE_URL -e METRIQUES_URL -e ALLOWED_HOSTS \
  -e PALIER -e PALIERS -e DUREE -e DEBIT -e ITERATIONS -e PRODUIT_ID -e LIMITE \
  -e K6_MDP_CUISINE -e K6_MDP_GERANT -e K6_MDP_MANAGER -e K6_MDP_LIVREUR -e K6_MDP_VENDEUR \
  -e K6_WEB_DASHBOARD=true \
  -e K6_WEB_DASHBOARD_EXPORT="resultats/$scenario-$horodatage.html" \
  grafana/k6:1.3.0 run \
  --summary-export "resultats/$scenario-$horodatage.json" \
  "$@" "$scenario.js"
