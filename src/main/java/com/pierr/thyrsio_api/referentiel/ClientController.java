package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.referentiel.dto.ClientResponse;
import com.pierr.thyrsio_api.referentiel.dto.CreateClientRequest;
import com.pierr.thyrsio_api.referentiel.dto.UpdateClientRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Référentiel — Clients")
@RestController
@RequestMapping("/api/referentiel/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @Operation(summary = "Lister les clients (paginé, filtrable)",
            description = "Triés par raison sociale par défaut. Les clients archivés sont exclus, sauf avec includeArchived=true.")
    @GetMapping
    public Page<ClientResponse> list(
            @Parameter(description = "Texte recherché dans le code ou la raison sociale") @RequestParam(required = false) String search,
            @Parameter(description = "Code pays ISO, ex. JP") @RequestParam(required = false) String countryCode,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return clientService.list(search, countryCode, includeArchived, pageable);
    }

    @Operation(summary = "Récupérer un client")
    @ApiResponse(responseCode = "200", description = "Client trouvé")
    @ApiResponse(responseCode = "404", description = "Client introuvable")
    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable UUID id) {
        return clientService.get(id);
    }

    @Operation(summary = "Créer un client")
    @ApiResponse(responseCode = "201", description = "Client créé")
    @ApiResponse(responseCode = "400", description = "Données invalides (pays, langue, n° de TVA...)")
    @ApiResponse(responseCode = "409", description = "Code déjà utilisé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody CreateClientRequest request) {
        return clientService.create(request);
    }

    @Operation(summary = "Modifier un client", description = "Remplace la fiche entière, sauf le code qui est définitif.")
    @ApiResponse(responseCode = "200", description = "Client modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "404", description = "Client introuvable")
    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateClientRequest request) {
        return clientService.update(id, request);
    }

    @Operation(summary = "Archiver un client")
    @ApiResponse(responseCode = "204", description = "Client archivé")
    @ApiResponse(responseCode = "404", description = "Client introuvable")
    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        clientService.archive(id);
    }

    @Operation(summary = "Réactiver un client")
    @ApiResponse(responseCode = "204", description = "Client réactivé")
    @ApiResponse(responseCode = "404", description = "Client introuvable")
    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID id) {
        clientService.reactivate(id);
    }
}