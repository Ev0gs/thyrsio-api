#!/bin/bash
# Restaure une base à partir d'une sauvegarde : ÉCRASE les données actuelles de cette base.
#   ./restore.sh /var/backups/thyrsio/thyrsio-2026-11-19_0315.dump thyrsio
#   ./restore.sh /var/backups/thyrsio/keycloak-2026-11-19_0315.dump keycloak
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
FILE=${1:?"Usage : $0 fichier.dump base"}; DB=${2:?base (thyrsio ou keycloak)}
[ -f "$FILE" ] || { echo "Fichier introuvable : $FILE"; exit 1; }

read -r -p "La base $DB va être remplacée par $FILE. Taper OUI pour continuer : " ANSWER
[ "$ANSWER" = "OUI" ] || { echo "Annulé."; exit 1; }

COMPOSE="docker compose -f docker-compose.prod.yml"
SERVICE=$([ "$DB" = keycloak ] && echo keycloak || echo api)
OWNER=$([ "$DB" = keycloak ] && echo keycloak || echo thyrsio_app)

echo "Arrêt de $SERVICE…"
$COMPOSE stop "$SERVICE"
$COMPOSE exec -T postgres psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$DB\" WITH (FORCE)" -c "CREATE DATABASE \"$DB\" OWNER $OWNER"
$COMPOSE exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$DB" --no-owner --role="$OWNER" < "$FILE"
echo "Redémarrage de $SERVICE…"
$COMPOSE start "$SERVICE"
echo "Restauration terminée."
