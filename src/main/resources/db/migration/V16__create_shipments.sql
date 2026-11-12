-- Expéditions : le chargement de tout ou partie d'une commande.
-- Préparée (brouillon), puis expédiée : le stock de produits finis sort alors, lot par lot.
CREATE TABLE shipments (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                   text        NOT NULL UNIQUE,  -- généré : EX-26-001, c'est aussi le n° du bon de livraison
    order_id               uuid        NOT NULL REFERENCES customer_orders (id),
    status                 text        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SHIPPED', 'CANCELLED')),
    planned_date           date        NOT NULL,
    carrier                text,        -- transporteur
    transport_reference    text,        -- n° de lettre de voiture, de conteneur, de plomb...
    notes                  text,
    -- L'adresse de livraison telle qu'elle était au départ : le bon de livraison ne doit plus changer
    delivery_address_text  text,
    shipped_at             timestamptz,
    shipped_by             uuid,
    created_by             uuid        NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT shipments_shipped_check CHECK (status <> 'SHIPPED' OR shipped_at IS NOT NULL)
);

CREATE INDEX shipments_order_id_idx ON shipments (order_id);
CREATE INDEX shipments_status_idx ON shipments (status);
CREATE INDEX shipments_planned_date_idx ON shipments (planned_date);

-- Ce qui est chargé : une ligne de commande, un lot, une quantité (un même produit peut venir de deux lots)
CREATE TABLE shipment_lines (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id    uuid    NOT NULL REFERENCES shipments (id),
    order_line_id  uuid    NOT NULL REFERENCES customer_order_lines (id),
    article_id     uuid    NOT NULL REFERENCES articles (id),
    lot_id         uuid    REFERENCES lots (id),
    quantity       integer NOT NULL CHECK (quantity > 0),
    CONSTRAINT shipment_lines_unique_key UNIQUE NULLS NOT DISTINCT (shipment_id, order_line_id, lot_id)
);

CREATE INDEX shipment_lines_order_line_id_idx ON shipment_lines (order_line_id);
CREATE INDEX shipment_lines_article_id_idx ON shipment_lines (article_id);
CREATE INDEX shipment_lines_lot_id_idx ON shipment_lines (lot_id);
