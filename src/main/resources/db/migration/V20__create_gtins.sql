-- Les gencodes (GTIN, codes-barres EAN-13) des produits finis.
-- GS1 attribue au client un préfixe entreprise ; chaque gencode = préfixe + référence article + clé de contrôle.
-- Un gencode désigne une unité vendue : une bouteille, ou un groupé (carton de 6, caisse bois de 6...).
-- Il ne sert qu'une fois : un gencode retiré n'est jamais redonné à un autre produit.

-- ---------- Les préfixes entreprise GS1 ----------
-- Un préfixe de 9 chiffres donne 1 000 gencodes, de 10 chiffres 100... ; un client peut en acheter plusieurs.
CREATE TABLE gs1_prefixes (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    prefix      text        NOT NULL UNIQUE CHECK (prefix ~ '^[0-9]{6,11}$'),
    label       text,
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- ---------- Les gencodes ----------
CREATE TABLE gtins (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    gtin                 text        NOT NULL UNIQUE CHECK (gtin ~ '^[0-9]{13}$'),
    -- Le préfixe dont il est issu (NULL : un gencode repris d'un historique, hors de nos préfixes)
    prefix_id            uuid        REFERENCES gs1_prefixes (id),
    -- Le produit fini qu'il désigne (NULL : réservé, pas encore attribué)
    finished_article_id  uuid        REFERENCES articles (id),
    packaging            text        NOT NULL CHECK (packaging IN ('BOTTLE', 'CARTON', 'WOODEN_CASE', 'OTHER')),
    units_per_pack       integer     NOT NULL DEFAULT 1 CHECK (units_per_pack > 0),
    description          text,
    status               text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    notes                text,
    created_by           uuid        NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    assigned_at          timestamptz,
    retired_at           timestamptz,
    CONSTRAINT gtins_bottle_is_one CHECK (packaging <> 'BOTTLE' OR units_per_pack = 1)
);

CREATE INDEX gtins_finished_article_id_idx ON gtins (finished_article_id);
CREATE INDEX gtins_prefix_id_idx ON gtins (prefix_id);
-- Un seul gencode actif par produit et par conditionnement (la bouteille, le carton de 6...) ; libre pour « autre »
CREATE UNIQUE INDEX gtins_active_packaging_key ON gtins (finished_article_id, packaging, units_per_pack)
    WHERE status = 'ACTIVE' AND finished_article_id IS NOT NULL AND packaging <> 'OTHER';
