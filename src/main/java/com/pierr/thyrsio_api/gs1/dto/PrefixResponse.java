package com.pierr.thyrsio_api.gs1.dto;

import java.util.UUID;

/** Un préfixe GS1 et son remplissage : capacity gencodes possibles, used déjà pris (retirés compris). */
public record PrefixResponse(UUID id, String prefix, String label, boolean active,
                             long capacity, long used, long remaining) {
}
