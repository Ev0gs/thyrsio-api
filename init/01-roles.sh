#!/bin/bash
# Exécuté une seule fois, au premier démarrage du conteneur (volume vide).
# Crée le rôle de l'appli et lui donne la base. Les tables sont gérées par Flyway.

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v pwd="$THYRSIO_APP_PASSWORD" -v db="$POSTGRES_DB" <<'SQL'
CREATE ROLE thyrsio_app LOGIN PASSWORD :'pwd';
ALTER DATABASE :"db" OWNER TO thyrsio_app;
SQL