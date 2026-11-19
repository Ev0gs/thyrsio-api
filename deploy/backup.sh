#!/bin/bash
# Sauvegarde les deux bases (thyrsio et keycloak), garde BACKUP_KEEP_DAYS jours, et en envoie une copie
# hors du serveur si RCLONE_REMOTE est renseigné. À lancer chaque nuit par cron :
#   15 3 * * * /home/thyrsio/thyrsio/thyrsio-api/deploy/backup.sh >> /var/log/thyrsio-backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a

DIR=${BACKUP_DIR:-/var/backups/thyrsio}
STAMP=$(date +%Y-%m-%d_%H%M)
mkdir -p "$DIR"
echo "[$(date -Is)] Sauvegarde dans $DIR"

for DB in "$POSTGRES_DB" keycloak; do
  FILE="$DIR/$DB-$STAMP.dump"
  # Format personnalisé de pg_dump : compressé, restaurable table par table avec pg_restore
  docker compose -f docker-compose.prod.yml exec -T postgres \
    pg_dump -U "$POSTGRES_USER" -Fc "$DB" > "$FILE.part"
  mv "$FILE.part" "$FILE"
  echo "  $DB : $(du -h "$FILE" | cut -f1)"
done

find "$DIR" -name '*.dump' -mtime +"${BACKUP_KEEP_DAYS:-14}" -delete

if [ -n "${RCLONE_REMOTE:-}" ]; then
  rclone copy "$DIR" "$RCLONE_REMOTE" --include '*.dump'
  echo "  copié vers $RCLONE_REMOTE"
else
  echo "  ATTENTION : aucune copie hors du serveur (RCLONE_REMOTE vide)"
fi
echo "[$(date -Is)] Terminé"
