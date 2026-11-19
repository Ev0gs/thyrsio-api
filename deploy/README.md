# Mettre Thyrsio en production (OVH)

Un seul serveur fait tout tourner avec Docker Compose :

| Service | Rôle | Joignable depuis Internet |
| --- | --- | --- |
| `web` (Caddy) | HTTPS automatique, l'application web, le passage vers l'API et Keycloak | oui, ports 80 et 443 |
| `api` | l'API Spring Boot (profil `prod`) | non, par Caddy (`/api`) |
| `keycloak` | les comptes et la connexion | non, par Caddy (`auth.…`) |
| `postgres` | les bases `thyrsio` et `keycloak` | jamais |

## 1. Le serveur et le domaine (une fois)

1. **Commander un VPS** dans l'espace client OVH (Bare Metal Cloud > VPS) : **au moins 4 Go de RAM**
   (Java et Keycloak sont gourmands), un datacenter en France, **Ubuntu 24.04**. Ajouter votre clé SSH à la commande.
2. **Le DNS**, dans Web Cloud > Noms de domaine > votre domaine > Zone DNS, ajouter deux entrées **A**
   vers l'adresse IPv4 du VPS (et **AAAA** vers l'IPv6 si vous l'utilisez) :
   - `app` → l'IP du VPS (l'application : `app.mondomaine.fr`)
   - `auth` → l'IP du VPS (la connexion : `auth.mondomaine.fr`)

   Vérifier depuis votre PC : `nslookup app.mondomaine.fr` doit répondre l'IP du VPS (quelques minutes à quelques heures).
3. **Les e-mails** (mot de passe oublié) : une adresse `no-reply@mondomaine.fr` dans votre offre e-mail OVH,
   ou un service SMTP comme Brevo. Il vous faut l'hôte, le port, l'identifiant et le mot de passe SMTP.

## 2. Préparer le serveur (une fois)

Se connecter : `ssh ubuntu@IP_DU_VPS`, puis :

```bash
# Mises à jour, et mises à jour de sécurité automatiques
sudo apt update && sudo apt upgrade -y
sudo apt install -y unattended-upgrades ufw git rclone
sudo dpkg-reconfigure -plow unattended-upgrades

# Pare-feu : SSH, HTTP, HTTPS seulement
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
sudo ufw enable

# Connexion SSH par clé uniquement
sudo sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo systemctl restart ssh

# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # puis se déconnecter et se reconnecter
```

> Attention : Docker publie ses ports en contournant `ufw`. Ici, seul Caddy publie des ports (80 et 443) :
> ne publiez jamais ceux de Postgres ou de Keycloak dans le compose de production.

## 3. Installer Thyrsio (une fois)

Les deux dépôts sont privés : créer sur le serveur une clé SSH (`ssh-keygen -t ed25519`) et l'ajouter
en **Deploy key** (lecture seule) dans les réglages GitHub de **chacun** des deux dépôts.

```bash
mkdir -p ~/thyrsio && cd ~/thyrsio
git clone -b production git@github.com:Ev0gs/thyrsio-api.git
git clone -b production git@github.com:Ev0gs/thyrsio-web.git
cd thyrsio-api/deploy
cp .env.example .env && chmod 600 .env
nano .env    # remplir : domaines, mots de passe (openssl rand -base64 32), SMTP, sauvegardes
```

Démarrer (le premier lancement compile l'API et l'application web : quelques minutes) :

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml logs -f    # Ctrl+C pour quitter
```

Quand Keycloak a démarré (`Keycloak … started` dans les journaux) :

```bash
./configure-keycloak.sh                                   # adresses de production, sécurité, e-mails
./create-admin.sh prenom@client.fr "Prénom" "Nom"         # le premier administrateur
```

Ouvrir `https://app.mondomaine.fr` : connexion avec le mot de passe provisoire, à changer aussitôt.
Les autres comptes se créent ensuite dans l'application (page Utilisateurs).

## 4. Les sauvegardes (indispensable)

Chaque nuit, les deux bases sont sauvegardées, gardées 14 jours, et copiées **hors du serveur** :

1. Créer un conteneur **Object Storage S3** dans l'espace OVH (Public Cloud > Object Storage),
   avec un utilisateur S3 et ses clés.
2. Sur le serveur : `rclone config` → nouveau remote `ovh-s3`, type `s3`, fournisseur `Other`,
   l'endpoint et la région donnés par OVH, les clés S3.
3. Dans `.env` : `RCLONE_REMOTE=ovh-s3:nom-du-conteneur`.
4. Préparer le dossier, essayer, puis planifier :

```bash
sudo mkdir -p /var/backups/thyrsio && sudo chown $USER /var/backups/thyrsio
./backup.sh
crontab -e
# ajouter la ligne :
15 3 * * * $HOME/thyrsio/thyrsio-api/deploy/backup.sh >> $HOME/thyrsio-backup.log 2>&1
```

**Tester une restauration** au moins une fois, avant d'en avoir besoin :
`./restore.sh /var/backups/thyrsio/thyrsio-AAAA-MM-JJ_HHMM.dump thyrsio`.

## 5. Mettre à jour

Après un `git push` sur la branche `production` :

```bash
cd ~/thyrsio/thyrsio-api/deploy && ./update.sh
```

Le script sauvegarde d'abord, récupère le code, reconstruit et redémarre. Les migrations de la base
(Flyway) s'appliquent au démarrage de l'API.

## 6. Surveiller

- Un service gratuit comme **UptimeRobot** : vérifier `https://app.mondomaine.fr` toutes les 5 minutes, alerte par e-mail.
- Les journaux : `docker compose -f docker-compose.prod.yml logs --tail 200 api`.
- L'espace disque : `df -h` (les sauvegardes et les images Docker en prennent).

## La console Keycloak

Elle est fermée depuis Internet (l'API, sur le réseau du serveur, y accède). Pour l'ouvrir depuis le bureau,
mettre son adresse IP dans `KEYCLOAK_ADMIN_ALLOWED_IPS` (`.env`), puis
`docker compose -f docker-compose.prod.yml up -d web`, et aller sur `https://auth.mondomaine.fr/admin`.
