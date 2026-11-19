package com.pierr.thyrsio_api.gs1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/gs1/prefixes ; en modification (PUT), le préfixe lui-même ne change pas. */
public record PrefixRequest(
        @NotBlank @Pattern(regexp = "^\\s*[0-9]{6,11}\\s*$", message = "de 6 à 11 chiffres, tel que donné par GS1")
        String prefix,
        @Size(max = 100) String label,
        Boolean active
) {
}
