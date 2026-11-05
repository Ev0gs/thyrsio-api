#!/bin/bash
# Exécuté une seule fois, au premier démarrage du conteneur (volume vide).
# Crée le rôle de l'appli et lui donne la base. Les tables sont gérées par Flyway.

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v pwd="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE keycloak LOGIN PASSWORD :'pwd';
CREATE DATABASE keycloak OWNER keycloak;
SQL