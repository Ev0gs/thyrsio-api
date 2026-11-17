package com.pierr.thyrsio_api.registre;

/**
 * Les registres du cahier de chai, sur le modèle du registre unique de manipulations de la FGVB.
 * Une écriture appartient à un seul registre.
 */
public enum RegisterBook {
    /** Entrées de vin en vrac : DAE, fournisseur, appellation, cuves, volumes. */
    ENTREES_VRAC("Entrées de vin en vrac"),
    /** Mouvements de vin : transferts, pertes, jaugeages, tirages pour une mise. */
    MOUVEMENTS("Mouvements de vin"),
    /** Produits œnologiques ajoutés (SO2, stabilisants, tanins...). */
    PRODUITS_OENOLOGIQUES("Manipulations : produits œnologiques"),
    /** Coupages (règle 85/15), replis et déclassements. */
    COUPAGES("Coupages, replis et déclassements"),
    /** Conditionnement et identification des lots : les mises en bouteille. */
    CONDITIONNEMENT("Conditionnement et identification des lots");

    private final String label;

    RegisterBook(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
