package com.pierr.thyrsio_api.gs1;

/** Ce que désigne un gencode : une bouteille, ou un groupé de plusieurs bouteilles. */
public enum GtinPackaging {
    BOTTLE("Bouteille"),
    CARTON("Carton"),
    WOODEN_CASE("Caisse bois"),
    OTHER("Autre");

    private final String label;

    GtinPackaging(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
