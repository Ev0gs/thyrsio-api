ALTER TABLE users
ADD password_hash text NOT NULL;

ALTER TABLE users
ADD role text NOT NULL DEFAULT 'USER' CHECK (role = 'USER' OR role = 'ADMIN');
