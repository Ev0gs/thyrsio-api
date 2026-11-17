package com.pierr.thyrsio_api.company;

import com.pierr.thyrsio_api.company.dto.CompanySettingsRequest;
import com.pierr.thyrsio_api.company.dto.CompanySettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Entreprise", description = "La fiche de l'entreprise : page de garde des registres, en-tête des documents")
@RestController
@RequestMapping("/api/company")
public class CompanySettingsController {

    private final CompanySettingsService service;

    public CompanySettingsController(CompanySettingsService service) {
        this.service = service;
    }

    @Operation(summary = "La fiche de l'entreprise")
    @GetMapping
    public CompanySettingsResponse get() {
        return service.get();
    }

    @Operation(summary = "Modifier la fiche de l'entreprise")
    @PutMapping
    public CompanySettingsResponse update(@Valid @RequestBody CompanySettingsRequest request) {
        return service.update(request);
    }
}
