package com.pierr.thyrsio_api.registre;

import com.pierr.thyrsio_api.registre.dto.RegisterEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Registre", description = "Le cahier de chai : les écritures inaltérables de chaque opération")
@RestController
@RequestMapping("/api/registre")
public class RegisterController {

    private final RegisterService registerService;
    private final RegisterJournal journal;

    public RegisterController(RegisterService registerService, RegisterJournal journal) {
        this.registerService = registerService;
        this.journal = journal;
    }

    @Operation(summary = "Les écritures du registre",
            description = "Filtrées par registre (book), période de l'opération (from inclus, to exclu), lot de vin, "
                    + "texte (q) ou saisies hors délai (late=true). Par défaut, les plus récentes d'abord.")
    @GetMapping("/entries")
    public Page<RegisterEntryResponse> list(
            @RequestParam(required = false) RegisterBook book,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID wineLotId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean late,
            @ParameterObject @PageableDefault(size = 50, sort = "sequenceNo", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return registerService.list(book, from, to, wineLotId, q, late, pageable);
    }

    @Operation(summary = "Vérifier l'intégrité du registre",
            description = "Recalcule l'empreinte de chaque écriture et vérifie la numérotation : "
                    + "toute modification faite après l'inscription est signalée.")
    @GetMapping("/verify")
    public RegisterVerification verify() {
        return journal.verify();
    }
}
