-- Une mise en bouteille devient un événement (un lieu, une durée) qui réalise une ou plusieurs productions :
-- un produit fini le matin, un autre l'après-midi, un troisième le lendemain...
-- Chaque production reçoit son numéro de lot dès la planification, avant le début de la mise :
-- L + sigle de l'appellation + millésime + numéro d'ordre (LBX2401). Un numéro ne sert jamais deux fois.

-- ---------- Les appellations : leur sigle entre dans le numéro de lot ----------
CREATE TABLE appellations (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Des lettres seulement : le millésime qui suit dans le numéro de lot ne peut pas se confondre avec le sigle
    code        text        NOT NULL UNIQUE CHECK (code ~ '^[A-Z]{1,4}$'),
    name        text        NOT NULL,
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX appellations_name_key ON appellations (lower(name));

INSERT INTO appellations (code, name) VALUES ('BX', 'Bordeaux');

-- ---------- Les compteurs des numéros de lot ----------
-- Un par préfixe (LBX24) ; il ne recule jamais : une production supprimée ou annulée ne rend pas son numéro.
CREATE TABLE lot_number_sequences (
    prefix      text    PRIMARY KEY,
    last_value  integer NOT NULL CHECK (last_value > 0)
);

-- ---------- Les productions d'une mise ----------
CREATE TABLE bottling_productions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bottling_id          uuid           NOT NULL REFERENCES bottlings (id),
    status               text           NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'CLOSED', 'CANCELLED')),
    -- Sur un jour (le matin, l'après-midi ou la journée) ou sur plusieurs jours
    start_date           date           NOT NULL,
    end_date             date           NOT NULL,
    slot                 text           NOT NULL DEFAULT 'FULL_DAY' CHECK (slot IN ('MORNING', 'AFTERNOON', 'FULL_DAY')),
    finished_article_id  uuid           NOT NULL REFERENCES articles (id),
    planned_bottles      integer        NOT NULL CHECK (planned_bottles > 0),
    bottle_volume_cl     integer        NOT NULL CHECK (bottle_volume_cl > 0),
    source_tank_id       uuid           REFERENCES tanks (id),
    wine_description     text,
    -- Ce qui fait le numéro de lot
    appellation_id       uuid           REFERENCES appellations (id),
    vintage              integer        CHECK (vintage BETWEEN 1900 AND 2100),  -- NULL : non millésimé
    lot_number           text           UNIQUE,
    -- Le réel, saisi à la clôture de la production
    produced_bottles     integer        CHECK (produced_bottles > 0),
    broken_bottles       integer        CHECK (broken_bottles >= 0),
    wine_used_hl         numeric(10, 2) CHECK (wine_used_hl > 0),
    closed_at            timestamptz,
    closed_by            uuid,
    created_at           timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT bottling_productions_dates_check CHECK (end_date >= start_date),
    CONSTRAINT bottling_productions_slot_one_day CHECK (slot = 'FULL_DAY' OR end_date = start_date),
    CONSTRAINT bottling_productions_closed_check CHECK (status <> 'CLOSED'
        OR (produced_bottles IS NOT NULL AND lot_number IS NOT NULL AND closed_at IS NOT NULL))
);

CREATE INDEX bottling_productions_bottling_id_idx ON bottling_productions (bottling_id);
CREATE INDEX bottling_productions_start_date_idx ON bottling_productions (start_date);
CREATE INDEX bottling_productions_finished_article_id_idx ON bottling_productions (finished_article_id);
CREATE INDEX bottling_productions_source_tank_id_idx ON bottling_productions (source_tank_id);
CREATE INDEX bottling_productions_appellation_id_idx ON bottling_productions (appellation_id);

-- Chaque mise existante devient une mise à une seule production, qui garde son numéro de lot.
-- Une mise encore planifiée n'en a pas : il sera attribué dès qu'on choisira son appellation.
INSERT INTO bottling_productions (bottling_id, status, start_date, end_date, slot, finished_article_id,
                                  planned_bottles, bottle_volume_cl, source_tank_id, wine_description,
                                  lot_number, produced_bottles, broken_bottles, wine_used_hl,
                                  closed_at, closed_by, created_at)
SELECT b.id, b.status, b.start_date, b.end_date, 'FULL_DAY', b.finished_article_id,
       b.planned_bottles, b.bottle_volume_cl, b.source_tank_id, b.wine_description,
       b.lot_number, b.produced_bottles, b.broken_bottles, b.wine_used_hl,
       b.closed_at, b.closed_by, b.created_at
FROM bottlings b;

-- ---------- Matières, consommations, commandes servies : rattachées à la production ----------
ALTER TABLE bottling_materials ADD COLUMN production_id uuid REFERENCES bottling_productions (id);
UPDATE bottling_materials m SET production_id = p.id FROM bottling_productions p WHERE p.bottling_id = m.bottling_id;
ALTER TABLE bottling_materials ALTER COLUMN production_id SET NOT NULL;
ALTER TABLE bottling_materials DROP COLUMN bottling_id;  -- emporte l'ancienne contrainte unique
ALTER TABLE bottling_materials ADD CONSTRAINT bottling_materials_unique_key UNIQUE (production_id, article_id);

ALTER TABLE bottling_consumptions ADD COLUMN production_id uuid REFERENCES bottling_productions (id);
UPDATE bottling_consumptions c SET production_id = p.id FROM bottling_productions p WHERE p.bottling_id = c.bottling_id;
ALTER TABLE bottling_consumptions ALTER COLUMN production_id SET NOT NULL;
ALTER TABLE bottling_consumptions DROP COLUMN bottling_id;
CREATE INDEX bottling_consumptions_production_id_idx ON bottling_consumptions (production_id);

ALTER TABLE bottling_allocations ADD COLUMN production_id uuid REFERENCES bottling_productions (id);
UPDATE bottling_allocations a SET production_id = p.id FROM bottling_productions p WHERE p.bottling_id = a.bottling_id;
ALTER TABLE bottling_allocations ALTER COLUMN production_id SET NOT NULL;
ALTER TABLE bottling_allocations DROP COLUMN bottling_id;
ALTER TABLE bottling_allocations ADD CONSTRAINT bottling_allocations_unique_key UNIQUE (production_id, order_line_id);

-- ---------- La mise : un lieu, une période, un état ----------
-- Ses dates sont celles de ses productions ; elle est « en cours » quand une partie est clôturée.
ALTER TABLE bottlings
    DROP COLUMN finished_article_id,
    DROP COLUMN planned_bottles,
    DROP COLUMN bottle_volume_cl,
    DROP COLUMN source_tank_id,
    DROP COLUMN wine_description,
    DROP COLUMN produced_bottles,
    DROP COLUMN broken_bottles,
    DROP COLUMN wine_used_hl,
    DROP COLUMN lot_number,
    DROP COLUMN closed_at,
    DROP COLUMN closed_by;

ALTER TABLE bottlings DROP CONSTRAINT bottlings_status_check;
ALTER TABLE bottlings ADD CONSTRAINT bottlings_status_check
    CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED'));
