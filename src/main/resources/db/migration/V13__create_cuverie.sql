-- Lots de vin : un vin acheté en vrac, tel qu'il est arrivé.
-- Le millésime est ici, pas sur le produit fini : pas de nouvelles fiches produits chaque année.
CREATE TABLE wine_lots (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 text           NOT NULL UNIQUE,  -- n° interne généré : V26-001
    supplier_id          uuid           NOT NULL REFERENCES suppliers (id),
    appellation          text           NOT NULL,
    color                text           NOT NULL CHECK (color IN ('RED', 'WHITE', 'ROSE')),
    vintage              smallint       CHECK (vintage BETWEEN 1900 AND 2100),  -- NULL = non millésimé
    alcohol              numeric(4, 2)  CHECK (alcohol BETWEEN 0 AND 25),       -- % vol. annoncé
    supplier_lot_number  text,           -- n° de lot du vendeur
    dae_number           text,           -- document d'accompagnement électronique
    price_per_hl         numeric(10, 2) CHECK (price_per_hl >= 0),  -- facultatif : servira au coût de revient
    received_at          timestamptz    NOT NULL,
    notes                text,
    created_by           uuid           NOT NULL,
    created_at           timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX wine_lots_supplier_id_idx ON wine_lots (supplier_id);

-- Cuves : chacune porte son contenu actuel (un seul lot à la fois) et son volume.
CREATE TABLE tanks (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code         text           NOT NULL UNIQUE,  -- C01, INOX-3...
    label        text,                            -- "Inox 3 · chai nord"
    capacity_hl  numeric(10, 2) NOT NULL CHECK (capacity_hl > 0),
    -- Seul l'état "humain" est stocké. Vide / occupée se déduit du volume.
    status       text           NOT NULL DEFAULT 'IN_SERVICE'
                                CHECK (status IN ('IN_SERVICE', 'CLEANING', 'OUT_OF_SERVICE')),
    wine_lot_id  uuid           REFERENCES wine_lots (id),
    volume_hl    numeric(10, 2) NOT NULL DEFAULT 0 CHECK (volume_hl >= 0),
    active       boolean        NOT NULL DEFAULT true,
    updated_at   timestamptz    NOT NULL DEFAULT now(),
    -- Filets de sécurité : le service vérifie avant, la base refuse quoi qu'il arrive.
    CONSTRAINT tanks_volume_within_capacity CHECK (volume_hl <= capacity_hl),
    -- Une cuve vide n'a pas de lot, une cuve avec un lot n'est pas vide.
    CONSTRAINT tanks_lot_iff_volume CHECK ((wine_lot_id IS NULL) = (volume_hl = 0)),
    -- On ne nettoie pas et on ne met pas hors service une cuve pleine.
    CONSTRAINT tanks_status_requires_empty CHECK (status = 'IN_SERVICE' OR volume_hl = 0)
);

CREATE INDEX tanks_wine_lot_id_idx ON tanks (wine_lot_id);

-- Journal des cuves : jamais modifié, jamais supprimé. Volume signé.
CREATE TABLE tank_movements (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tank_id      uuid           NOT NULL REFERENCES tanks (id),
    wine_lot_id  uuid           NOT NULL REFERENCES wine_lots (id),
    type         text           NOT NULL CHECK (type IN
                                ('RECEPTION', 'TRANSFER_OUT', 'TRANSFER_IN', 'LOSS', 'ADJUSTMENT', 'BOTTLING')),
    volume_hl    numeric(10, 2) NOT NULL CHECK (volume_hl <> 0),
    -- Relie les lignes d'un même transfert : sortie, entrée, et perte éventuelle
    transfer_id  uuid,
    occurred_at  timestamptz    NOT NULL,
    reference    text,           -- n° de DAE, de bon, de mise...
    notes        text,
    created_by   uuid           NOT NULL,
    created_at   timestamptz    NOT NULL DEFAULT now(),
    -- Le sens est imposé par le type ; seul l'ajustement peut aller dans les deux sens
    CONSTRAINT tank_movements_sign_check CHECK (CASE
        WHEN type IN ('RECEPTION', 'TRANSFER_IN') THEN volume_hl > 0
        WHEN type = 'ADJUSTMENT' THEN true
        ELSE volume_hl < 0
    END),
    -- Un transfert a toujours son identifiant ; seule une perte peut aussi en avoir un
    CONSTRAINT tank_movements_transfer_check CHECK (
        (type NOT IN ('TRANSFER_OUT', 'TRANSFER_IN') OR transfer_id IS NOT NULL)
        AND (transfer_id IS NULL OR type IN ('TRANSFER_OUT', 'TRANSFER_IN', 'LOSS'))
    )
);

CREATE INDEX tank_movements_tank_occurred_idx ON tank_movements (tank_id, occurred_at DESC);
CREATE INDEX tank_movements_wine_lot_id_idx ON tank_movements (wine_lot_id);
CREATE INDEX tank_movements_transfer_id_idx ON tank_movements (transfer_id);

-- Analyses : rattachées au lot (le vin), avec la cuve prélevée si on la connaît.
CREATE TABLE wine_analyses (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    wine_lot_id       uuid          NOT NULL REFERENCES wine_lots (id),
    tank_id           uuid          REFERENCES tanks (id),
    analyzed_on       date          NOT NULL,
    alcohol           numeric(4, 2) CHECK (alcohol BETWEEN 0 AND 25),  -- % vol.
    free_so2          numeric(6, 1) CHECK (free_so2 >= 0),            -- mg/L
    total_so2         numeric(6, 1) CHECK (total_so2 >= 0),           -- mg/L
    total_acidity     numeric(5, 2) CHECK (total_acidity >= 0),       -- g/L H2SO4
    volatile_acidity  numeric(5, 2) CHECK (volatile_acidity >= 0),    -- g/L H2SO4
    laboratory        text,
    notes             text,         -- conclusions : filtration, collage...
    created_by        uuid          NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX wine_analyses_wine_lot_idx ON wine_analyses (wine_lot_id, analyzed_on DESC);
CREATE INDEX wine_analyses_tank_id_idx ON wine_analyses (tank_id);
