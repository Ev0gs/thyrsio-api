-- Un fournisseur fournit soit des matières sèches, soit du vin en vrac.
-- Les fournisseurs existants sont des fournisseurs de matières sèches.
ALTER TABLE suppliers
    ADD COLUMN kind text NOT NULL DEFAULT 'DRY_GOODS' CHECK (kind IN ('DRY_GOODS', 'WINE'));

-- Le vin en vrac est géré par le module Cuverie, plus comme un article.
-- Désactivée plutôt que supprimée : on ne casse rien de ce qui pourrait la référencer.
UPDATE article_categories SET active = false WHERE code = 'VIN_VRAC';
