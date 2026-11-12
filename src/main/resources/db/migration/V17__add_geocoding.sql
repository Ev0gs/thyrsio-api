-- Les coordonnées des adresses, pour les afficher sur une carte (OpenStreetMap).
-- Calculées en tâche de fond à partir de l'adresse (géocodage) :
--   geocoding_query : l'adresse telle qu'elle a été (ou sera) cherchée ; si l'adresse change, tout est remis à zéro
--   geocoded_at     : null = à chercher ; renseigné sans latitude = adresse introuvable
--   location_approximate : seule la ville (ou le code postal) a été trouvée, pas la rue

ALTER TABLE clients
    ADD COLUMN latitude             double precision CHECK (latitude BETWEEN -90 AND 90),
    ADD COLUMN longitude            double precision CHECK (longitude BETWEEN -180 AND 180),
    ADD COLUMN location_approximate boolean,
    ADD COLUMN geocoding_query      text,
    ADD COLUMN geocoded_at          timestamptz;

ALTER TABLE client_delivery_addresses
    ADD COLUMN latitude             double precision CHECK (latitude BETWEEN -90 AND 90),
    ADD COLUMN longitude            double precision CHECK (longitude BETWEEN -180 AND 180),
    ADD COLUMN location_approximate boolean,
    ADD COLUMN geocoding_query      text,
    ADD COLUMN geocoded_at          timestamptz;

ALTER TABLE suppliers
    ADD COLUMN latitude             double precision CHECK (latitude BETWEEN -90 AND 90),
    ADD COLUMN longitude            double precision CHECK (longitude BETWEEN -180 AND 180),
    ADD COLUMN location_approximate boolean,
    ADD COLUMN geocoding_query      text,
    ADD COLUMN geocoded_at          timestamptz;
