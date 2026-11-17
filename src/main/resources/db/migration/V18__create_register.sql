-- Le cahier de chai numérique (registre de manipulations) et la fiche de l'entreprise qui le tient.

-- ---------- L'entreprise : la page de garde du registre ----------
-- Une seule ligne (id = 1). Raison sociale, n° d'accise et adresse figurent en tête de chaque registre.
CREATE TABLE company_settings (
    id                  smallint    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    legal_name          text,
    excise_number       text,
    siret               text,
    vat_number          text,
    address_line1       text,
    address_line2       text,
    postal_code         text,
    city                text,
    country_code        text        NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
    phone               text,
    email               text,
    -- La date à partir de laquelle le registre numérique fait foi (avant : le cahier papier)
    register_start_date date,
    updated_at          timestamptz NOT NULL DEFAULT now()
);
INSERT INTO company_settings (id) VALUES (1);

-- ---------- Les écritures du registre ----------
-- Chaque opération enregistrée dans l'application y écrit une ligne, dans la même transaction.
-- Une écriture ne se modifie ni ne s'efface jamais : une erreur se corrige par une contre-écriture
-- (corrects_entry_id). Chaque écriture porte l'empreinte (SHA-256) de la précédente : toute retouche
-- en base, même hors de l'application, se voit à la vérification.
CREATE TABLE register_entries (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_no       bigint      NOT NULL UNIQUE CHECK (sequence_no > 0),
    book              text        NOT NULL CHECK (book IN
                          ('ENTREES_VRAC', 'MOUVEMENTS', 'PRODUITS_OENOLOGIQUES', 'COUPAGES', 'CONDITIONNEMENT')),
    kind              text        NOT NULL,
    operation_at      timestamptz NOT NULL,
    recorded_at       timestamptz NOT NULL,
    recorded_by       uuid        NOT NULL,
    recorded_by_name  text,
    -- Le vin concerné, figé au moment de l'écriture (le lot peut être renommé ensuite)
    wine_lot_id       uuid,
    wine_lot_code     text,
    denomination      text,
    tanks             text,
    volume_hl         numeric(12, 4),
    reference         text,
    summary           text        NOT NULL,
    -- Les colonnes propres à chaque registre, en JSON canonique (clés triées) : c'est ce texte qui est scellé
    data              text        NOT NULL,
    corrects_entry_id uuid        REFERENCES register_entries (id),
    -- Saisie au-delà du délai réglementaire (jour ouvrable suivant, 3e pour une sortie)
    late              boolean     NOT NULL DEFAULT false,
    previous_hash     text        NOT NULL,
    hash              text        NOT NULL UNIQUE
);

CREATE INDEX register_entries_book_idx      ON register_entries (book, operation_at);
CREATE INDEX register_entries_wine_lot_idx  ON register_entries (wine_lot_id, operation_at);
CREATE INDEX register_entries_operation_idx ON register_entries (operation_at);

-- Le registre est inaltérable : la base elle-même refuse toute modification ou suppression
CREATE FUNCTION register_entries_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Le registre est inaltérable : une écriture ne se modifie ni ne se supprime, elle se corrige par une contre-écriture';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER register_entries_no_update
    BEFORE UPDATE OR DELETE ON register_entries
    FOR EACH ROW EXECUTE FUNCTION register_entries_immutable();

CREATE TRIGGER register_entries_no_truncate
    BEFORE TRUNCATE ON register_entries
    FOR EACH STATEMENT EXECUTE FUNCTION register_entries_immutable();

-- ---------- La tête du registre ----------
-- Une seule ligne, verrouillée à chaque écriture : les numéros se suivent sans trou ni doublon,
-- et chaque écriture connaît l'empreinte de la précédente.
CREATE TABLE register_head (
    id            smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    last_sequence bigint   NOT NULL DEFAULT 0,
    last_hash     text     NOT NULL
);
-- L'empreinte de départ : 64 zéros
INSERT INTO register_head (id, last_sequence, last_hash) VALUES (1, 0, repeat('0', 64));
