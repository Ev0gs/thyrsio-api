-- Commandes clients : ce qu'un client commande, et pour quand.
CREATE TABLE customer_orders (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    text        NOT NULL UNIQUE,  -- généré : CC-26-001
    client_id               uuid        NOT NULL REFERENCES clients (id),
    delivery_address_id     uuid        REFERENCES client_delivery_addresses (id),
    customer_reference      text,        -- le n° de commande du client (son bon de commande)
    order_date              date        NOT NULL,
    requested_loading_date  date,        -- la date de chargement souhaitée
    incoterm                text        CHECK (incoterm IN
                                ('EXW', 'FCA', 'CPT', 'CIP', 'DAP', 'DPU', 'DDP', 'FAS', 'FOB', 'CFR', 'CIF')),
    status                  text        NOT NULL DEFAULT 'DRAFT' CHECK (status IN
                                ('DRAFT', 'CONFIRMED', 'PARTIALLY_SHIPPED', 'SHIPPED', 'CANCELLED')),
    notes                   text,
    confirmed_at            timestamptz,
    created_by              uuid        NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint      NOT NULL DEFAULT 0
);

CREATE INDEX customer_orders_client_id_idx ON customer_orders (client_id);
CREATE INDEX customer_orders_status_idx ON customer_orders (status);
CREATE INDEX customer_orders_loading_date_idx ON customer_orders (requested_loading_date);
CREATE INDEX customer_orders_delivery_address_id_idx ON customer_orders (delivery_address_id);

-- Une ligne par produit fini commandé. shipped_quantity monte à chaque expédition.
CREATE TABLE customer_order_lines (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          uuid           NOT NULL REFERENCES customer_orders (id),
    article_id        uuid           NOT NULL REFERENCES articles (id),
    quantity          integer        NOT NULL CHECK (quantity > 0),          -- en bouteilles
    unit_price        numeric(12, 2) CHECK (unit_price >= 0),               -- facultatif : prix par bouteille
    shipped_quantity  integer        NOT NULL DEFAULT 0,
    CONSTRAINT customer_order_lines_shipped_check CHECK (shipped_quantity BETWEEN 0 AND quantity),
    CONSTRAINT customer_order_lines_unique_key UNIQUE (order_id, article_id)
);

CREATE INDEX customer_order_lines_article_id_idx ON customer_order_lines (article_id);

-- Le lien mise ↔ commande : une mise produit (une partie de) ce qu'une ligne de commande demande.
-- Une mise peut servir plusieurs commandes, une ligne peut être couverte par plusieurs mises.
CREATE TABLE bottling_allocations (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bottling_id    uuid    NOT NULL REFERENCES bottlings (id),
    order_line_id  uuid    NOT NULL REFERENCES customer_order_lines (id),
    quantity       integer NOT NULL CHECK (quantity > 0),
    CONSTRAINT bottling_allocations_unique_key UNIQUE (bottling_id, order_line_id)
);

CREATE INDEX bottling_allocations_order_line_id_idx ON bottling_allocations (order_line_id);
