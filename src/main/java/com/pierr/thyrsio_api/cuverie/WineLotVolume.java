package com.pierr.thyrsio_api.cuverie;

import java.math.BigDecimal;
import java.util.UUID;

/** Résultat d'un GROUP BY : volume total d'un lot et nombre de cuves qui le contiennent. */
public record WineLotVolume(UUID wineLotId, BigDecimal volumeHl, Long tankCount) {
}
