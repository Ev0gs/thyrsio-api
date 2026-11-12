package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.WineColor;
import com.pierr.thyrsio_api.cuverie.WineLot;

import java.util.UUID;

/** Ce qu'on affiche d'un lot à côté d'une cuve ou d'un mouvement. */
public record WineLotSummary(
        UUID id,
        String code,
        String appellation,
        WineColor color,
        Short vintage
) {
    /** null si la cuve est vide. */
    public static WineLotSummary from(WineLot lot) {
        if (lot == null) {
            return null;
        }
        return new WineLotSummary(lot.getId(), lot.getCode(), lot.getAppellation(), lot.getColor(), lot.getVintage());
    }
}
