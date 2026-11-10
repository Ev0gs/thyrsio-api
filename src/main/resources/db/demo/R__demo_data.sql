-- =====================================================================
-- Données de démonstration : un négoce bordelais fictif.
-- Chargé UNIQUEMENT avec le profil Spring "dev" (voir application-dev.properties).
-- Idempotent : peut être rejoué sans créer de doublons.
-- =====================================================================

-- ---------- Fournisseurs ----------
INSERT INTO suppliers (name, email, phone, address, average_lead_time_days)
SELECT v.name, v.email, v.phone, v.address, v.lead_time
FROM (VALUES
          ('Verrerie du Sud-Ouest',         'commandes@verrerie-so.example',   '05 00 00 00 01', 'ZI des Verriers, 33000 Bordeaux',          21),
          ('Bouchonnerie Landaise',         'ventes@bouchons-landes.example',  '05 00 00 00 02', '8 route des Pins, 40000 Mont-de-Marsan',  14),
          ('Capsules d''Aquitaine',         'adv@capsules-aquitaine.example',  '05 00 00 00 03', '15 rue de l''Étain, 33100 Bordeaux',       10),
          ('Imprimerie Étiquettes Gironde', 'prepresse@ieg.example',           '05 00 00 00 04', '4 allée des Presses, 33700 Mérignac',       7),
          ('Cartonnages du Libournais',     'commandes@cartons-lib.example',   '05 00 00 00 05', 'ZA de la Ballastière, 33500 Libourne',      5)
     ) AS v(name, email, phone, address, lead_time)
WHERE NOT EXISTS (SELECT 1 FROM suppliers s WHERE s.name = v.name);

-- ---------- Articles (unité = unité par défaut de la catégorie) ----------
INSERT INTO articles (code, label, category_id, unit_id, main_supplier_id, supplier_reference, alert_threshold)
SELECT v.code, v.label, c.id, c.default_unit_id, s.id, v.supplier_ref, v.alert_threshold
FROM (VALUES
          ('BTL-BDX-75',    'Bouteille bordelaise 75 cl',        'BOUTEILLE',        'Verrerie du Sud-Ouest',         'BX75-ANTIQUE', 10000),
          ('BTL-BDX-150',   'Magnum bordelais 150 cl',           'BOUTEILLE',        'Verrerie du Sud-Ouest',         'BX150-ANTIQUE', 1000),
          ('BTL-BOURG-75',  'Bouteille bourguignonne 75 cl',     'BOUTEILLE',        'Verrerie du Sud-Ouest',         'BG75-FEUILLE',  3000),
          ('BCH-LIE-4924',  'Bouchon liège naturel 49×24',       'BOUCHON',          'Bouchonnerie Landaise',         'NAT-49-24-A',   5000),
          ('BCH-TECH-4424', 'Bouchon technique 44×24',           'BOUCHON',          'Bouchonnerie Landaise',         'TEC-44-24',     5000),
          ('CAP-ETN-NR',    'Capsule étain noire',               'CAPSULE',          'Capsules d''Aquitaine',         'ETN-NOIR-31',   3000),
          ('CAP-CRD-R75',   'Capsule CRD vin rouge 75 cl',       'CAPSULE_CRD',      'Capsules d''Aquitaine',         'CRD-R-75',      2000),
          ('CAP-CRD-R150',  'Capsule CRD vin rouge 150 cl',      'CAPSULE_CRD',      'Capsules d''Aquitaine',         'CRD-R-150',      300),
          ('ETQ-BDXS-23',   'Étiquette Bordeaux Supérieur 2023', 'ETIQUETTE',        'Imprimerie Étiquettes Gironde', 'E-BDXS-2023',   2000),
          ('ETQ-CDB-22',    'Étiquette Côtes de Bourg 2022',     'ETIQUETTE',        'Imprimerie Étiquettes Gironde', 'E-CDB-2022',    1000),
          ('ETQ-PREST-21',  'Étiquette Cuvée Prestige 2021',     'ETIQUETTE',        'Imprimerie Étiquettes Gironde', 'E-PREST-2021',   500),
          ('CET-FR',        'Contre-étiquette France',           'CONTRE_ETIQUETTE', 'Imprimerie Étiquettes Gironde', 'CE-FR',         2000),
          ('CET-JP',        'Contre-étiquette Japon',            'CONTRE_ETIQUETTE', 'Imprimerie Étiquettes Gironde', 'CE-JP',         1000),
          ('COL-MILL-23',   'Collerette millésime 2023',         'COLLERETTE',       'Imprimerie Étiquettes Gironde', 'COL-2023',      1000),
          ('CRT-6-CCH',     'Carton 6 bouteilles couchées',      'CARTON',           'Cartonnages du Libournais',     'C6-COUCHE',     2000),
          ('CRT-6-MAG',     'Carton 6 magnums couchés',          'CARTON',           'Cartonnages du Libournais',     'C6-MAGNUM',      300),
          ('CRX-6',         'Croisillon pour carton de 6',       'CROISILLON',       'Cartonnages du Libournais',     'X6',            2000),
          ('PAL-EUR',       'Palette Europe 80×120',             'PALETTE',          NULL,                            NULL,              50),
          ('FLM-ETR',       'Film étirable 500 mm (rouleau)',    'FILM',             NULL,                            NULL,              10)
     ) AS v(code, label, category, supplier, supplier_ref, alert_threshold)
         JOIN article_categories c ON c.code = v.category
         LEFT JOIN suppliers s ON s.name = v.supplier
    ON CONFLICT (code) DO NOTHING;

-- ---------- Lots (articles suivis par lot : bouchons et capsules) ----------
INSERT INTO lots (article_id, lot_number, notes)
SELECT a.id, v.lot_number, v.notes
FROM (VALUES
          ('BCH-LIE-4924',  '24-117',    'Livraison partielle, reliquat attendu'),
          ('BCH-LIE-4924',  '24-131',    NULL),
          ('BCH-TECH-4424', 'T-8812',    NULL),
          ('CAP-ETN-NR',    'E-5521',    NULL),
          ('CAP-CRD-R75',   'C-2026-08', 'Capsules CRD : à déclarer en DRM'),
          ('CAP-CRD-R150',  'C-2026-09', 'Capsules CRD : à déclarer en DRM')
     ) AS v(article, lot_number, notes)
         JOIN articles a ON a.code = v.article
    ON CONFLICT (article_id, lot_number) DO NOTHING;

-- ---------- Clients ----------
INSERT INTO clients (code, name, country_code, language, address_line1, postal_code, city,
                     email, phone, vat_number, default_incoterm, gs1_labeling_required, notes)
VALUES
    ('TOKYO-WINE',  'Tokyo Wine Import KK',  'JP', 'en', '3-5-8 Kaigan, Minato-ku',    '105-0022', 'Tokyo',
     'orders@tokyo-wine-import.example', '+81 3 0000 0000', NULL, 'FOB', true,
     'Contre-étiquette japonaise obligatoire'),
    ('VINS-SAVEURS', 'Vins & Saveurs SPRL',  'BE', 'fr', 'Rue des Vignerons 12',       '1000',     'Bruxelles',
     'achats@vins-saveurs.example', '+32 2 000 00 00', 'BE0123456789', 'DAP', false, NULL),
    ('CAVE-PENTES',  'Cave des Pentes',      'FR', 'fr', '12 montée des Tisserands',   '69001',    'Lyon',
     'contact@cave-des-pentes.example', '04 00 00 00 00', 'FR12345678901', 'EXW', false,
     'Enlèvement par leur transporteur'),
    ('HUDSON-FW',    'Hudson Fine Wines LLC', 'US', 'en', '100 River Street',          '10013',    'New York',
     'purchasing@hudson-fw.example', '+1 212 000 0000', NULL, 'FCA', true, NULL)
    ON CONFLICT (code) DO NOTHING;

-- ---------- Adresses de livraison ----------
-- "Par défaut" seulement si le client n'en a pas déjà une (l'index unique partiel l'interdirait)
INSERT INTO client_delivery_addresses (client_id, label, address_line1, postal_code, city, country_code,
                                       delivery_instructions, default_address)
SELECT c.id, v.label, v.address_line1, v.postal_code, v.city, v.country_code, v.instructions,
       v.is_default AND NOT EXISTS (SELECT 1 FROM client_delivery_addresses d
                                    WHERE d.client_id = c.id AND d.default_address)
FROM (VALUES
          ('TOKYO-WINE',   'Entrepôt sous douane Yokohama', '1-2 Daikoku-futo, Tsurumi-ku', '230-0054', 'Yokohama',  'JP',
           'Rendez-vous 48 h à l''avance, quai 3', true),
          ('TOKYO-WINE',   'Entrepôt Osaka',                '2-1 Nanko-kita, Suminoe-ku',   '559-0034', 'Osaka',     'JP',
           NULL, false),
          ('VINS-SAVEURS', 'Magasin Bruxelles',             'Rue des Vignerons 12',         '1000',     'Bruxelles', 'BE',
           'Livraison du mardi au vendredi, 8 h - 12 h', true),
          ('CAVE-PENTES',  'Cave',                          '12 montée des Tisserands',     '69001',    'Lyon',      'FR',
           'Rue étroite : porteur 12 t maximum', true),
          ('HUDSON-FW',    'Entrepôt sous douane Newark',   '500 Port Street',              '07114',    'Newark',    'US',
           'Livraison conteneur, dock 4', true)
     ) AS v(client, label, address_line1, postal_code, city, country_code, instructions, is_default)
         JOIN clients c ON c.code = v.client
WHERE NOT EXISTS (SELECT 1 FROM client_delivery_addresses d WHERE d.client_id = c.id AND d.label = v.label);

-- ---------- Mouvements de stock : 3 semaines d'activité ----------
-- days_ago / at : il y a N jours, à telle heure (LEAST : jamais dans le futur).
-- created_by : un identifiant fixe "démo", qui sert aussi de garde-fou contre les doublons.
-- Mises simulées : J-9 magnums Prestige 2021 (1 200), J-6 Bordeaux Sup. France (4 800),
--                  J-1 Bordeaux Sup. Japon (6 000).
INSERT INTO stock_movements (article_id, lot_id, type, quantity, occurred_at, reference, notes, created_by)
SELECT a.id, l.id, v.type, v.quantity,
       LEAST(date_trunc('day', now()) - v.days_ago * interval '1 day' + v.at::interval, now()),
       v.reference, v.notes, '00000000-0000-4000-8000-000000000001'::uuid
FROM (VALUES
          -- Bouteilles
          ('BTL-BDX-75',    NULL,        'RECEPTION',   24000, 20, '08:30', 'BL VSO-26-1187', NULL),
          ('BTL-BDX-75',    NULL,        'CONSUMPTION', -4800,  6, '17:30', 'Mise MB-0411',   NULL),
          ('BTL-BDX-75',    NULL,        'CONSUMPTION', -6000,  1, '17:40', 'Mise MB-0412',   NULL),
          ('BTL-BDX-75',    NULL,        'LOSS',         -600,  1, '17:45', 'Mise MB-0412',   'Casse sur la chaîne d''embouteillage'),
          ('BTL-BDX-75',    NULL,        'RECEPTION',   12000,  0, '09:12', 'BL VSO-26-1290', NULL),
          ('BTL-BDX-150',   NULL,        'RECEPTION',    4400, 18, '10:00', 'BL VSO-26-1201', NULL),
          ('BTL-BDX-150',   NULL,        'CONSUMPTION', -1200,  9, '16:50', 'Mise MB-0410',   NULL),
          ('BTL-BOURG-75',  NULL,        'RECEPTION',    1800, 30, '09:00', 'BL VSO-26-1102', NULL),
          -- Bouchons (suivis par lot)
          ('BCH-LIE-4924',  '24-117',    'RECEPTION',    7200, 15, '11:20', 'BL BL-77812',    NULL),
          ('BCH-LIE-4924',  '24-117',    'CONSUMPTION', -6000,  1, '17:40', 'Mise MB-0412',   NULL),
          ('BCH-LIE-4924',  '24-131',    'RECEPTION',    3800, 19, '14:05', 'BL BL-77650',    NULL),
          ('BCH-LIE-4924',  '24-131',    'CONSUMPTION', -1200,  9, '16:50', 'Mise MB-0410',   NULL),
          ('BCH-TECH-4424', 'T-8812',    'RECEPTION',   18800, 12, '09:45', 'BL BL-77901',    NULL),
          ('BCH-TECH-4424', 'T-8812',    'CONSUMPTION', -4800,  6, '17:30', 'Mise MB-0411',   NULL),
          -- Capsules (suivies par lot)
          ('CAP-ETN-NR',    'E-5521',    'RECEPTION',   14200, 13, '10:30', 'BL CA-26-0455',  NULL),
          ('CAP-ETN-NR',    'E-5521',    'CONSUMPTION', -4800,  6, '17:30', 'Mise MB-0411',   NULL),
          ('CAP-CRD-R75',   'C-2026-08', 'RECEPTION',    6800, 10, '15:10', 'BL CA-26-0472',  NULL),
          ('CAP-CRD-R75',   'C-2026-08', 'CONSUMPTION', -6000,  1, '17:40', 'Mise MB-0412',   NULL),
          ('CAP-CRD-R150',  'C-2026-09', 'RECEPTION',    1200, 25, '11:00', 'BL CA-26-0398',  NULL),
          ('CAP-CRD-R150',  'C-2026-09', 'CONSUMPTION', -1200,  9, '16:50', 'Mise MB-0410',   NULL),
          -- Étiquettes, contre-étiquettes, collerettes (ETQ-CDB-22 : jamais reçue)
          ('ETQ-BDXS-23',   NULL,        'RECEPTION',   22800, 16, '09:30', 'BL IEG-5532',    NULL),
          ('ETQ-BDXS-23',   NULL,        'CONSUMPTION', -4800,  6, '17:30', 'Mise MB-0411',   NULL),
          ('ETQ-BDXS-23',   NULL,        'CONSUMPTION', -6000,  1, '17:40', 'Mise MB-0412',   NULL),
          ('ETQ-BDXS-23',   NULL,        'LOSS',          -40,  1, '16:05', NULL,             'Rouleau abîmé à la pose'),
          ('ETQ-PREST-21',  NULL,        'RECEPTION',    5400, 22, '10:15', 'BL IEG-5490',    NULL),
          ('ETQ-PREST-21',  NULL,        'CONSUMPTION', -1200,  9, '16:50', 'Mise MB-0410',   NULL),
          ('CET-FR',        NULL,        'RECEPTION',   12800, 16, '09:30', 'BL IEG-5532',    NULL),
          ('CET-FR',        NULL,        'CONSUMPTION', -4800,  6, '17:30', 'Mise MB-0411',   NULL),
          ('CET-JP',        NULL,        'RECEPTION',   13500,  7, '14:20', 'BL IEG-5601',    NULL),
          ('CET-JP',        NULL,        'CONSUMPTION', -6000,  1, '17:40', 'Mise MB-0412',   NULL),
          ('COL-MILL-23',   NULL,        'RECEPTION',    2500, 16, '09:30', 'BL IEG-5532',    NULL),
          -- Emballages
          ('CRT-6-CCH',     NULL,        'RECEPTION',    4000, 14, '08:15', 'BL CL-26-0912',  NULL),
          ('CRT-6-CCH',     NULL,        'CONSUMPTION',  -800,  6, '17:30', 'Mise MB-0411',   NULL),
          ('CRT-6-CCH',     NULL,        'INVENTORY',    -100,  3, '18:00', 'Inventaire tournant cartons', 'Cartons humides mis au rebut'),
          ('CRT-6-CCH',     NULL,        'CONSUMPTION', -1000,  1, '17:40', 'Mise MB-0412',   NULL),
          ('CRT-6-MAG',     NULL,        'RECEPTION',     600, 18, '10:00', 'BL CL-26-0880',  NULL),
          ('CRT-6-MAG',     NULL,        'CONSUMPTION',  -200,  9, '16:50', 'Mise MB-0410',   NULL),
          ('CRX-6',         NULL,        'RECEPTION',    4200, 14, '08:15', 'BL CL-26-0912',  NULL),
          ('CRX-6',         NULL,        'CONSUMPTION',  -800,  6, '17:30', 'Mise MB-0411',   NULL),
          ('CRX-6',         NULL,        'CONSUMPTION', -1000,  1, '17:40', 'Mise MB-0412',   NULL),
          ('PAL-EUR',       NULL,        'RECEPTION',      60, 20, '08:30', 'BL VSO-26-1187', 'Palettes consignées'),
          ('PAL-EUR',       NULL,        'CONSUMPTION',    -2,  9, '16:50', 'Mise MB-0410',   NULL),
          ('PAL-EUR',       NULL,        'CONSUMPTION',    -9,  6, '17:30', 'Mise MB-0411',   NULL),
          ('PAL-EUR',       NULL,        'CONSUMPTION',   -11,  1, '17:40', 'Mise MB-0412',   NULL),
          ('FLM-ETR',       NULL,        'RECEPTION',      16, 20, '08:30', 'BL CL-26-0850',  NULL),
          ('FLM-ETR',       NULL,        'CONSUMPTION',    -2,  6, '17:30', 'Mise MB-0411',   NULL),
          ('FLM-ETR',       NULL,        'CONSUMPTION',    -2,  1, '17:40', 'Mise MB-0412',   NULL)
     ) AS v(article, lot_number, type, quantity, days_ago, at, reference, notes)
         JOIN articles a ON a.code = v.article
         LEFT JOIN lots l ON l.article_id = a.id AND l.lot_number = v.lot_number
-- Garde-fou : si les mouvements de démo existent déjà, on n'insère rien
WHERE NOT EXISTS (SELECT 1 FROM stock_movements m
                  WHERE m.created_by = '00000000-0000-4000-8000-000000000001'::uuid);

-- ---------- Soldes : calculés à partir des mouvements, donc cohérents par construction ----------
INSERT INTO stock_balances (article_id, lot_id, quantity, updated_at)
SELECT m.article_id, m.lot_id, sum(m.quantity), max(m.occurred_at)
FROM stock_movements m
WHERE m.created_by = '00000000-0000-4000-8000-000000000001'::uuid
GROUP BY m.article_id, m.lot_id
ON CONFLICT ON CONSTRAINT stock_balances_article_lot_key DO NOTHING;