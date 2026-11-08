CREATE TABLE articles (
                          id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                          code                text           NOT NULL UNIQUE,
                          label               text           NOT NULL,
                          category_id         uuid           NOT NULL REFERENCES article_categories (id),
                          unit_id             uuid           NOT NULL REFERENCES units (id),
                          main_supplier_id    uuid           REFERENCES suppliers (id),
                          supplier_reference  text,
    -- numeric : un nombre décimal exact (jamais d'arrondi binaire comme avec un double)
                          alert_threshold     numeric(14, 4) CHECK (alert_threshold >= 0),
                          active              boolean        NOT NULL DEFAULT true,
                          created_at          timestamptz    NOT NULL DEFAULT now()
);

-- PostgreSQL ne crée PAS d'index automatiquement sur les clés étrangères :
-- sans eux, filtrer les articles par catégorie ou par fournisseur parcourt toute la table
CREATE INDEX articles_category_id_idx      ON articles (category_id);
CREATE INDEX articles_main_supplier_id_idx ON articles (main_supplier_id);