#!/bin/bash
# Règle Keycloak pour la production, après le premier démarrage (le realm importé est celui du développement).
# Peut être relancé sans risque : il réécrit les mêmes réglages.
#   ./configure-keycloak.sh
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a

kc() { docker compose -f docker-compose.prod.yml exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }

echo "Connexion à Keycloak…"
kc config credentials --server http://localhost:8080 --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

echo "L'application web : seules ses adresses de production sont acceptées"
WEB_ID=$(kc get clients -r thyrsio -q clientId=thyrsio-web --fields id --format csv --noquotes)
kc update "clients/$WEB_ID" -r thyrsio \
  -s "redirectUris=[\"https://$APP_DOMAIN/*\"]" \
  -s "webOrigins=[\"https://$APP_DOMAIN\"]" \
  -s "attributes.\"post.logout.redirect.uris\"=https://$APP_DOMAIN/*"

echo "Les règles de sécurité du realm thyrsio"
kc update realms/thyrsio \
  -s sslRequired=external \
  -s bruteForceProtected=true \
  -s failureFactor=8 \
  -s 'passwordPolicy=length(12) and notUsername and notEmail' \
  -s resetPasswordAllowed=true

echo "Les e-mails (SMTP $SMTP_HOST:$SMTP_PORT)"
kc update realms/thyrsio \
  -s "smtpServer.host=$SMTP_HOST" \
  -s "smtpServer.port=$SMTP_PORT" \
  -s "smtpServer.from=$SMTP_FROM" \
  -s "smtpServer.fromDisplayName=Thyrsio" \
  -s "smtpServer.auth=true" \
  -s "smtpServer.user=$SMTP_USER" \
  -s "smtpServer.password=$SMTP_PASSWORD" \
  -s "smtpServer.starttls=true" \
  -s "smtpServer.ssl=false"

echo "La console d'administration (realm master) : protégée contre les essais de mots de passe"
kc update realms/master -s bruteForceProtected=true -s sslRequired=external

echo "Keycloak est prêt."
