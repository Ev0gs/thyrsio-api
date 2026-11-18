-- Les matières sèches (bouteilles vides, bouchons, capsules, étiquettes, cartons...) ne sont plus suivies par lot.
-- Seuls les produits finis en ont un : le numéro imprimé sur les bouteilles, donné par la mise.
-- Le suivi par lot n'est donc plus une option de la catégorie : il découle d'elle (PRODUIT_FINI).
-- Les données existantes des matières sèches sont regroupées sans lot, puis leurs lots disparaissent.

CREATE TEMP TABLE dry_articles ON COMMIT DROP AS
SELECT a.id
FROM articles a
         JOIN article_categories c ON c.id = a.category_id
WHERE c.code <> 'PRODUIT_FINI';

-- ---------- Soldes : un seul par matière sèche, la somme de ses lots ----------
INSERT INTO stock_balances (article_id, lot_id, quantity, updated_at)
SELECT b.article_id, NULL, sum(b.quantity), max(b.updated_at)
FROM stock_balances b
WHERE b.lot_id IS NOT NULL
  AND b.article_id IN (SELECT id FROM dry_articles)
GROUP BY b.article_id
ON CONFLICT ON CONSTRAINT stock_balances_article_lot_key
    DO UPDATE SET quantity   = stock_balances.quantity + EXCLUDED.quantity,
                  updated_at = greatest(stock_balances.updated_at, EXCLUDED.updated_at);

DELETE FROM stock_balances
WHERE lot_id IS NOT NULL
  AND article_id IN (SELECT id FROM dry_articles);

-- ---------- Mouvements : les quantités restent, le lot disparaît ----------
UPDATE stock_movements
SET lot_id = NULL
WHERE lot_id IS NOT NULL
  AND article_id IN (SELECT id FROM dry_articles);

-- ---------- Inventaires : les lignes d'un même article (une par lot) n'en font plus qu'une ----------
-- Dans un brouillon, si un des lots n'était pas encore compté, la ligne regroupée est à recompter.
CREATE TEMP TABLE merged_inventory_lines ON COMMIT DROP AS
SELECT l.inventory_id,
       l.article_id,
       (array_agg(l.id ORDER BY l.lot_id NULLS FIRST, l.id))[1]                          AS keep_id,
       CASE WHEN bool_or(l.counted_quantity IS NULL) THEN NULL ELSE sum(l.counted_quantity) END AS counted,
       sum(l.expected_quantity)                                                          AS expected,
       sum(l.adjustment)                                                                 AS adjustment,
       string_agg(l.notes, ' · ')                                                        AS notes
FROM inventory_lines l
WHERE l.article_id IN (SELECT id FROM dry_articles)
  AND EXISTS (SELECT 1 FROM inventory_lines o
              WHERE o.inventory_id = l.inventory_id AND o.article_id = l.article_id AND o.lot_id IS NOT NULL)
GROUP BY l.inventory_id, l.article_id;

DELETE FROM inventory_lines l
    USING merged_inventory_lines m
WHERE l.inventory_id = m.inventory_id
  AND l.article_id = m.article_id
  AND l.id <> m.keep_id;

UPDATE inventory_lines l
SET lot_id            = NULL,
    counted_quantity  = m.counted,
    expected_quantity = m.expected,
    adjustment        = m.adjustment,
    notes             = m.notes
FROM merged_inventory_lines m
WHERE l.id = m.keep_id;

-- ---------- Mises en bouteille : ce qui a été consommé, par article seulement ----------
CREATE TEMP TABLE merged_consumptions ON COMMIT DROP AS
SELECT bottling_id, article_id, (array_agg(id ORDER BY id))[1] AS keep_id, sum(quantity) AS quantity
FROM bottling_consumptions
GROUP BY bottling_id, article_id
HAVING count(*) > 1;

DELETE FROM bottling_consumptions c
    USING merged_consumptions m
WHERE c.bottling_id = m.bottling_id
  AND c.article_id = m.article_id
  AND c.id <> m.keep_id;

UPDATE bottling_consumptions c
SET quantity = m.quantity
FROM merged_consumptions m
WHERE c.id = m.keep_id;

ALTER TABLE bottling_consumptions DROP COLUMN lot_id;

-- ---------- Les lots des matières sèches, puis l'option de catégorie ----------
DELETE FROM lots
WHERE article_id IN (SELECT id FROM dry_articles);

ALTER TABLE article_categories DROP COLUMN lot_tracked;
