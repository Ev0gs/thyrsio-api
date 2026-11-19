#!/bin/bash
# Crée le premier administrateur de Thyrsio (les suivants se créent depuis l'application, page Utilisateurs).
#   ./create-admin.sh prenom.nom@exemple.fr "Prénom" "Nom"
# Un mot de passe provisoire est affiché : il devra être changé à la première connexion.
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
EMAIL=${1:?"Usage : $0 email prénom nom"}; FIRST=${2:?prénom}; LAST=${3:?nom}

kc() { docker compose -f docker-compose.prod.yml exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080 --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

PASSWORD=$(openssl rand -base64 18)
kc create users -r thyrsio -s "username=$EMAIL" -s "email=$EMAIL" -s emailVerified=true \
  -s "firstName=$FIRST" -s "lastName=$LAST" -s enabled=true -s 'requiredActions=["UPDATE_PASSWORD"]' >/dev/null
kc set-password -r thyrsio --username "$EMAIL" --new-password "$PASSWORD" --temporary
kc add-roles -r thyrsio --uusername "$EMAIL" --rolename ADMIN --rolename USER

echo
echo "Administrateur créé : $EMAIL"
echo "Mot de passe provisoire : $PASSWORD"
echo "Connexion : https://$APP_DOMAIN (le mot de passe sera changé à la première connexion)"
