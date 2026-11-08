-- Lots : une quantité homogène d'un article, identifiée par un numéro
-- (une livraison de bouchons, un achat de vin en vrac...)
CREATE TABLE lots (
                      id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                      article_id   uuid        NOT NULL REFERENCES articles (id),
                      lot_number   text        NOT NULL,
                      expiry_date  date,
                      notes        text,
                      created_at   timestamptz NOT NULL DEFAULT now(),
    -- Unique par article : deux fournisseurs peuvent livrer chacun un "lot 2024-01".
    -- Cette contrainte crée aussi un index qui sert aux recherches par article.
                      UNIQUE (article_id, lot_number)
);