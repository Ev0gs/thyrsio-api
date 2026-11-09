CREATE TABLE inventories (
                             id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                             label         text        NOT NULL,           -- "Inventaire annuel 2026"
                             status        text        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'VALIDATED', 'CANCELLED')),
                             counted_at    timestamptz NOT NULL,           -- date du comptage = date des mouvements créés
                             notes         text,
                             created_by    uuid        NOT NULL,
                             created_at    timestamptz NOT NULL DEFAULT now(),
                             updated_at    timestamptz NOT NULL DEFAULT now(),
                             validated_by  uuid,
                             validated_at  timestamptz,
    -- Verrouillage optimiste : incrémenté à chaque modification
                             version       bigint      NOT NULL DEFAULT 0
);

CREATE TABLE inventory_lines (
                                 id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 inventory_id       uuid           NOT NULL REFERENCES inventories (id),
                                 article_id         uuid           NOT NULL REFERENCES articles (id),
                                 lot_id             uuid           REFERENCES lots (id),
                                 counted_quantity   numeric(14, 4) CHECK (counted_quantity >= 0),  -- NULL = pas encore compté
                                 expected_quantity  numeric(14, 4),   -- stock théorique, figé à la validation
                                 adjustment         numeric(14, 4),   -- écart appliqué, figé à la validation
                                 notes              text,
    -- Un article (et un lot) n'apparaît qu'une fois par inventaire
                                 CONSTRAINT inventory_lines_unique_key UNIQUE NULLS NOT DISTINCT (inventory_id, article_id, lot_id)
);

CREATE INDEX inventory_lines_article_id_idx ON inventory_lines (article_id);
CREATE INDEX inventory_lines_lot_id_idx ON inventory_lines (lot_id);