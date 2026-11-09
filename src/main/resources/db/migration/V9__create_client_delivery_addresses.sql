CREATE TABLE client_delivery_addresses (
                                           id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                           client_id              uuid        NOT NULL REFERENCES clients (id),
                                           label                  text        NOT NULL,          -- "Entrepôt Yokohama"
                                           recipient_name         text,                          -- destinataire, si différent du client
                                           address_line1          text        NOT NULL,
                                           address_line2          text,
                                           postal_code            text,                          -- facultatif : certains pays n'en ont pas
                                           city                   text        NOT NULL,
                                           country_code           text        NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    phone                  text,
    delivery_instructions  text,                          -- horaires, quai, prise de rendez-vous...
    default_address        boolean     NOT NULL DEFAULT false,
    active                 boolean     NOT NULL DEFAULT true,
    created_at             timestamptz NOT NULL DEFAULT now(),
    -- Une adresse archivée ne peut pas rester l'adresse par défaut
    CHECK (active OR NOT default_address)
);

CREATE INDEX client_delivery_addresses_client_id_idx ON client_delivery_addresses (client_id);

-- Index unique PARTIEL : l'unicité ne porte que sur les lignes où default_address est vrai.
-- Un client peut donc avoir 10 adresses, mais une seule par défaut.
CREATE UNIQUE INDEX client_delivery_addresses_one_default_idx
    ON client_delivery_addresses (client_id) WHERE default_address;