-- Clients du négociant : importateurs, distributeurs, cavistes...
CREATE TABLE clients (
                         id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                         code                   text        NOT NULL UNIQUE,
                         name                   text        NOT NULL,
    -- ISO 3166-1 alpha-2 : FR, BE, JP...
                         country_code           text        NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    -- ISO 639-1 : langue des documents envoyés au client
    language               text        NOT NULL DEFAULT 'fr' CHECK (language ~ '^[a-z]{2}$'),
    -- Adresse du siège (facturation). Les adresses de livraison auront leur propre table.
    address_line1          text,
    address_line2          text,
    postal_code            text,
    city                   text,
    email                  text,
    phone                  text,
    vat_number             text,
    excise_number          text,
    default_incoterm       text CHECK (default_incoterm IN
                               ('EXW', 'FCA', 'CPT', 'CIP', 'DAP', 'DPU', 'DDP', 'FAS', 'FOB', 'CFR', 'CIF')),
    gs1_labeling_required  boolean     NOT NULL DEFAULT false,
    notes                  text,
    active                 boolean     NOT NULL DEFAULT true,
    created_at             timestamptz NOT NULL DEFAULT now()
);