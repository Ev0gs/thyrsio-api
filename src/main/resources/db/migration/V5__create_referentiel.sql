-- Unités de mesure
CREATE TABLE units (
                       id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                       code      text     NOT NULL UNIQUE,
                       label     text     NOT NULL,
    -- Nombre de décimales autorisées : 0 pour des capsules, 2 pour des litres
                       decimals  smallint NOT NULL CHECK (decimals BETWEEN 0 AND 6)
);

-- Catégories d'articles : en table, pas en enum, pour que chaque client puisse les adapter
CREATE TABLE article_categories (
                                    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                    code             text    NOT NULL UNIQUE,
                                    label            text    NOT NULL,
                                    default_unit_id  uuid    NOT NULL REFERENCES units (id),
                                    lot_tracked      boolean NOT NULL DEFAULT false,  -- les articles de cette catégorie sont suivis par lot
                                    regulated        boolean NOT NULL DEFAULT false,  -- soumis à la réglementation (capsules CRD)
                                    active           boolean NOT NULL DEFAULT true
);

-- Fournisseurs
CREATE TABLE suppliers (
                           id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                           name                    text        NOT NULL,
                           email                   text,
                           phone                   text,
                           address                 text,
                           average_lead_time_days  integer     CHECK (average_lead_time_days >= 0),
                           active                  boolean     NOT NULL DEFAULT true,
                           created_at              timestamptz NOT NULL DEFAULT now()
);

-- Valeurs initiales, modifiables ensuite par chaque client
INSERT INTO units (code, label, decimals) VALUES
                                              ('U',  'Unité',       0),
                                              ('L',  'Litre',       2),
                                              ('HL', 'Hectolitre',  4),
                                              ('KG', 'Kilogramme',  3);

INSERT INTO article_categories (code, label, default_unit_id, lot_tracked, regulated)
SELECT c.code, c.label, u.id, c.lot_tracked, c.regulated
FROM (VALUES
          ('BOUTEILLE',        'Bouteille',        'U', false, false),
          ('BOUCHON',          'Bouchon',          'U', true,  false),
          ('CAPSULE',          'Capsule',          'U', true,  false),
          ('CAPSULE_CRD',      'Capsule CRD',      'U', true,  true),
          ('ETIQUETTE',        'Étiquette',        'U', false, false),
          ('CONTRE_ETIQUETTE', 'Contre-étiquette', 'U', false, false),
          ('COLLERETTE',       'Collerette',       'U', false, false),
          ('CARTON',           'Carton',           'U', false, false),
          ('CROISILLON',       'Croisillon',       'U', false, false),
          ('PALETTE',          'Palette',          'U', false, false),
          ('FILM',             'Film',             'U', false, false),
          ('VIN_VRAC',         'Vin en vrac',      'L', true,  false)
     ) AS c (code, label, unit_code, lot_tracked, regulated)
         JOIN units u ON u.code = c.unit_code;