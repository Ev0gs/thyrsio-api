-- Le mot de passe et le rôle sont désormais gérés par Keycloak
ALTER TABLE users DROP COLUMN password_hash;
ALTER TABLE users DROP COLUMN role;

-- L'unicité de l'email est garantie par Keycloak
ALTER TABLE users DROP CONSTRAINT users_email_key;

-- Lien avec le compte Keycloak : la valeur "sub" du token
ALTER TABLE users ADD COLUMN keycloak_id uuid NOT NULL UNIQUE;