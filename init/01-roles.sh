#!/bin/bash
# Exécuté une seule fois, au premier démarrage du conteneur (volume vide).
# On ne crée ici que ce que Flyway ne sait pas faire : le rôle applicatif
# et ses droits. Les tables sont gérées par les migrations Flyway de l'appli.

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v pwd="$THYRSIO_APP_PASSWORD" <<'SQL'

-- Rôle de l'appli : pas superuser, mais peut créer des bases et des rôles
-- (nécessaire pour provisionner un tenant)
CREATE ROLE thyrsio_app LOGIN CREATEDB CREATEROLE PASSWORD :'pwd';

-- Quand thyrsio_app crée un rôle de tenant, il obtient le droit de SET ROLE
-- dessus (nécessaire pour CREATE DATABASE ... OWNER), mais n'hérite pas
-- de ses privilèges : il ne peut donc pas se connecter aux bases des tenants.
ALTER ROLE thyrsio_app SET createrole_self_grant = 'set';

-- La base "thyrsio" (registre des tenants) appartient à l'appli,
-- pour que Flyway puisse y créer ses tables.
ALTER DATABASE thyrsio OWNER TO thyrsio_app;
REVOKE CONNECT ON DATABASE thyrsio FROM PUBLIC;
GRANT CONNECT ON DATABASE thyrsio TO thyrsio_app;
SQL
