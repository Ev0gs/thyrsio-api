-- Mouvements : le journal de tout ce qui entre et sort. Jamais modifié, jamais supprimé.
CREATE TABLE stock_movements (
                                 id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                 article_id   uuid           NOT NULL REFERENCES articles (id),
                                 lot_id       uuid           REFERENCES lots (id),
                                 type         text           NOT NULL CHECK (type IN
                                                                             ('RECEPTION', 'CONSUMPTION', 'LOSS', 'ADJUSTMENT', 'PRODUCTION', 'SHIPMENT', 'INVENTORY')),
    -- Signée : positive pour une entrée, négative pour une sortie
                                 quantity     numeric(14, 4) NOT NULL CHECK (quantity <> 0),
    -- Quand c'est arrivé physiquement (peut être antérieur à la saisie)
                                 occurred_at  timestamptz    NOT NULL,
                                 reference    text,          -- n° de bon de livraison, de mise...
                                 notes        text,
                                 created_by   uuid           NOT NULL,  -- id Keycloak de l'utilisateur
                                 created_at   timestamptz    NOT NULL DEFAULT now()
);

-- L'historique d'un article, du plus récent au plus ancien
CREATE INDEX stock_movements_article_occurred_idx ON stock_movements (article_id, occurred_at DESC);
CREATE INDEX stock_movements_lot_id_idx ON stock_movements (lot_id);

-- Soldes : le stock actuel par article et par lot, tenu à jour avec chaque mouvement
CREATE TABLE stock_balances (
                                id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                article_id  uuid           NOT NULL REFERENCES articles (id),
                                lot_id      uuid           REFERENCES lots (id),
                                quantity    numeric(14, 4) NOT NULL CHECK (quantity >= 0),
                                updated_at  timestamptz    NOT NULL DEFAULT now(),
    -- NULLS NOT DISTINCT (Postgres 15+) : deux soldes "sans lot" pour le même article
    -- sont considérés comme des doublons. Sans ça, NULL ≠ NULL et on pourrait en créer plusieurs.
                                CONSTRAINT stock_balances_article_lot_key UNIQUE NULLS NOT DISTINCT (article_id, lot_id)
);

-- article_id est déjà couvert par la contrainte unique (première colonne)
CREATE INDEX stock_balances_lot_id_idx ON stock_balances (lot_id);