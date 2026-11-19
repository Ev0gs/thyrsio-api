#!/bin/bash
# Met Thyrsio à jour : récupère le code des deux dépôts, reconstruit les images et redémarre ce qui a changé.
# Une sauvegarde est faite avant (les migrations de base s'appliquent au démarrage de l'API).
#   ./update.sh
set -euo pipefail
cd "$(dirname "$0")"

./backup.sh
git -C .. pull --ff-only
git -C ../../thyrsio-web pull --ff-only
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker image prune -f >/dev/null
echo "À jour. Journaux de l'API : docker compose -f docker-compose.prod.yml logs -f api"
