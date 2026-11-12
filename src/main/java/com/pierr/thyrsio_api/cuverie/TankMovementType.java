package com.pierr.thyrsio_api.cuverie;

import java.math.BigDecimal;

/**
 * Les mouvements de vin. Contrairement au stock, aucun n'est saisi "en brut" :
 * chacun est créé par une opération du service (réception, transfert, perte, mesure).
 */
public enum TankMovementType {

    RECEPTION(Direction.IN),
    TRANSFER_OUT(Direction.OUT),
    TRANSFER_IN(Direction.IN),
    LOSS(Direction.OUT),
    ADJUSTMENT(Direction.EITHER),  // écart constaté par une mesure (jaugeage)
    BOTTLING(Direction.OUT);       // phase 2b : mise en bouteille

    public enum Direction { IN, OUT, EITHER }

    private final Direction direction;

    TankMovementType(Direction direction) {
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    /** Même règle que la contrainte tank_movements_sign_check de la base, vérifiée avant d'y arriver. */
    void checkSign(BigDecimal signedVolume) {
        boolean valid = switch (direction) {
            case IN -> signedVolume.signum() > 0;
            case OUT -> signedVolume.signum() < 0;
            case EITHER -> signedVolume.signum() != 0;
        };
        if (!valid) {
            throw new IllegalStateException("Volume " + signedVolume + " incohérent pour un mouvement " + name());
        }
    }
}
