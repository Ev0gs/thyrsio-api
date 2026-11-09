package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.referentiel.dto.DeliveryAddressRequest;
import com.pierr.thyrsio_api.referentiel.dto.DeliveryAddressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Référentiel — Adresses de livraison")
@RestController
@RequestMapping("/api/referentiel/clients/{clientId}/delivery-addresses")
public class ClientDeliveryAddressController {

    private final ClientDeliveryAddressService addressService;

    public ClientDeliveryAddressController(ClientDeliveryAddressService addressService) {
        this.addressService = addressService;
    }

    @Operation(summary = "Lister les adresses de livraison d'un client",
            description = "L'adresse par défaut en premier. Les archivées sont exclues, sauf avec includeArchived=true.")
    @ApiResponse(responseCode = "404", description = "Client introuvable")
    @GetMapping
    public List<DeliveryAddressResponse> list(@PathVariable UUID clientId,
                                              @RequestParam(defaultValue = "false") boolean includeArchived) {
        return addressService.list(clientId, includeArchived);
    }

    @Operation(summary = "Récupérer une adresse de livraison")
    @ApiResponse(responseCode = "200", description = "Adresse trouvée")
    @ApiResponse(responseCode = "404", description = "Adresse introuvable pour ce client")
    @GetMapping("/{addressId}")
    public DeliveryAddressResponse get(@PathVariable UUID clientId, @PathVariable UUID addressId) {
        return addressService.get(clientId, addressId);
    }

    @Operation(summary = "Ajouter une adresse de livraison",
            description = "La première adresse d'un client devient automatiquement son adresse par défaut.")
    @ApiResponse(responseCode = "201", description = "Adresse créée")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "404", description = "Client introuvable")
    @ApiResponse(responseCode = "409", description = "Client archivé")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryAddressResponse create(@PathVariable UUID clientId,
                                          @Valid @RequestBody DeliveryAddressRequest request) {
        return addressService.create(clientId, request);
    }

    @Operation(summary = "Modifier une adresse de livraison",
            description = "Remplace l'adresse entière. defaultAddress=true en fait la nouvelle adresse par défaut.")
    @ApiResponse(responseCode = "200", description = "Adresse modifiée")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "404", description = "Adresse introuvable pour ce client")
    @PutMapping("/{addressId}")
    public DeliveryAddressResponse update(@PathVariable UUID clientId, @PathVariable UUID addressId,
                                          @Valid @RequestBody DeliveryAddressRequest request) {
        return addressService.update(clientId, addressId, request);
    }

    @Operation(summary = "Archiver une adresse de livraison", description = "Elle perd son statut d'adresse par défaut.")
    @ApiResponse(responseCode = "204", description = "Adresse archivée")
    @ApiResponse(responseCode = "404", description = "Adresse introuvable pour ce client")
    @PostMapping("/{addressId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID clientId, @PathVariable UUID addressId) {
        addressService.archive(clientId, addressId);
    }

    @Operation(summary = "Réactiver une adresse de livraison")
    @ApiResponse(responseCode = "204", description = "Adresse réactivée")
    @ApiResponse(responseCode = "404", description = "Adresse introuvable pour ce client")
    @PostMapping("/{addressId}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID clientId, @PathVariable UUID addressId) {
        addressService.reactivate(clientId, addressId);
    }
}