#!/bin/sh
# Exporte la description OpenAPI GENEREE par le backend (springdoc) dans
# contrat-api/openapi-implemente.json : exacte par construction, a comparer
# avec contrat-api/openapi.yaml (contrat de reference, ecrit a la main) lors
# de la revue du lead Backend.
#
# Prerequis : backend demarre en profil dev (Swagger actif), par exemple
# avec le compose de demonstration (deploiement/demo, port 8090 par defaut).
#   sh scripts/contrat/exporter-openapi.sh [http://localhost:8090]
set -eu
BASE="${1:-http://localhost:8090}"
CIBLE="$(dirname "$0")/../../contrat-api/openapi-implemente.json"
curl -fsS "$BASE/api/v1/v3/api-docs" -o "$CIBLE"
echo "Exporte : $CIBLE"
