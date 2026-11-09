package com.pierr.thyrsio_api.stock;

import java.math.BigDecimal;

/** Un enum : chaque type a un sens et dit s'il peut être saisi à la main. */
public enum MovementType {

    RECEPTION(Direction.IN, true),     // manuel en attendant le module Achats
    CONSUMPTION(Direction.OUT, true),  // manuel en attendant le module Production
    LOSS(Direction.OUT, true),
    ADJUSTMENT(Direction.EITHER, true),
    PRODUCTION(Direction.IN, false),
    SHIPMENT(Direction.OUT, false),
    INVENTORY(Direction.EITHER, false);

    public enum Direction { IN, OUT, EITHER }

    private final Direction direction;
    private final boolean manualEntry;

    MovementType(Direction direction, boolean manualEntry) {
        this.direction = direction;
        this.manualEntry = manualEntry;
    }

    public Direction getDirection() { return direction; }

    public boolean isManualEntry() { return manualEntry; }

    /**
     * Transforme la quantité saisie en quantité signée.
     * IN et OUT : on saisit une quantité positive, le type donne le sens.
     * EITHER : la quantité saisie porte elle-même son signe (+5 ou -5).
     */
    public BigDecimal signedQuantity(BigDecimal quantity) {
        return switch (direction) {
            case IN -> requirePositive(quantity);
            case OUT -> requirePositive(quantity).negate();
            case EITHER -> {
                if (quantity.signum() == 0) {
                    throw new IllegalArgumentException("La quantité d'un mouvement " + name() + " ne peut pas être nulle");
                }
                yield quantity;
            }
        };
    }

    private BigDecimal requirePositive(BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "La quantité doit être positive pour un mouvement " + name() + " : le sens est donné par le type");
        }
        return quantity;
    }
}