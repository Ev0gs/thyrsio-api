-- Produits finis : des articles comme les autres, suivis par lot (le n° de lot imprimé sur la bouteille).
-- Leur stock monte à la clôture d'une mise (mouvement PRODUCTION).
INSERT INTO article_categories (code, label, default_unit_id, lot_tracked, regulated)
SELECT 'PRODUIT_FINI', 'Produit fini', u.id, true, false
FROM units u
WHERE u.code = 'U'
ON CONFLICT (code) DO NOTHING;

-- Mises en bouteille : planifiées, puis clôturées avec le réel (ou annulées).
CREATE TABLE bottlings (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 text          NOT NULL UNIQUE,  -- généré : MB-26-001
    status               text          NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'CLOSED', 'CANCELLED')),
    -- Chez nous (le vin sort d'une de nos cuves) ou chez le producteur (le vin n'est pas dans nos cuves)
    location             text          NOT NULL CHECK (location IN ('ON_SITE', 'AT_PRODUCER')),
    contractor           text,          -- le prestataire d'embouteillage
    -- Une mise peut durer plusieurs jours
    start_date           date          NOT NULL,
    end_date             date          NOT NULL,
    finished_article_id  uuid          NOT NULL REFERENCES articles (id),
    planned_bottles      integer       NOT NULL CHECK (planned_bottles > 0),
    bottle_volume_cl     integer       NOT NULL CHECK (bottle_volume_cl > 0),  -- 75, 150...
    source_tank_id       uuid          REFERENCES tanks (id),
    wine_description     text,          -- chez le producteur : le vin mis en bouteille
    notes                text,
    -- Le réel, saisi à la clôture
    produced_bottles     integer       CHECK (produced_bottles > 0),
    broken_bottles       integer       CHECK (broken_bottles >= 0),
    wine_used_hl         numeric(10, 2) CHECK (wine_used_hl > 0),
    lot_number           text          UNIQUE,          -- généré à la clôture : L26314-1
    closed_at            timestamptz,
    closed_by            uuid,
    created_by           uuid          NOT NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    version              bigint        NOT NULL DEFAULT 0,
    CONSTRAINT bottlings_dates_check CHECK (end_date >= start_date),
    CONSTRAINT bottlings_tank_on_site CHECK (location <> 'ON_SITE' OR source_tank_id IS NOT NULL),
    CONSTRAINT bottlings_closed_check CHECK (status <> 'CLOSED'
        OR (produced_bottles IS NOT NULL AND lot_number IS NOT NULL AND closed_at IS NOT NULL))
);

CREATE INDEX bottlings_start_date_idx ON bottlings (start_date);
CREATE INDEX bottlings_status_idx ON bottlings (status);
CREATE INDEX bottlings_finished_article_id_idx ON bottlings (finished_article_id);
CREATE INDEX bottlings_source_tank_id_idx ON bottlings (source_tank_id);

-- Les matières sèches prévues pour une mise : la liste propre à chaque mise (pas de nomenclature figée)
CREATE TABLE bottling_materials (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bottling_id       uuid           NOT NULL REFERENCES bottlings (id),
    article_id        uuid           NOT NULL REFERENCES articles (id),
    planned_quantity  numeric(14, 4) NOT NULL CHECK (planned_quantity > 0),
    CONSTRAINT bottling_materials_unique_key UNIQUE (bottling_id, article_id)
);

CREATE INDEX bottling_materials_article_id_idx ON bottling_materials (article_id);

-- Ce qui a réellement été consommé, par lot : figé à la clôture (les mouvements de stock en sont la trace)
CREATE TABLE bottling_consumptions (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bottling_id  uuid           NOT NULL REFERENCES bottlings (id),
    article_id   uuid           NOT NULL REFERENCES articles (id),
    lot_id       uuid           REFERENCES lots (id),
    quantity     numeric(14, 4) NOT NULL CHECK (quantity > 0)
);

CREATE INDEX bottling_consumptions_bottling_id_idx ON bottling_consumptions (bottling_id);
CREATE INDEX bottling_consumptions_article_id_idx ON bottling_consumptions (article_id);
CREATE INDEX bottling_consumptions_lot_id_idx ON bottling_consumptions (lot_id);
