package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.dto.DeliveryAddressRequest;
import com.pierr.thyrsio_api.referentiel.dto.DeliveryAddressResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientDeliveryAddressService {

    private final ClientDeliveryAddressRepository addressRepository;
    // Même module : on peut utiliser le repository des clients directement
    private final ClientRepository clientRepository;

    public ClientDeliveryAddressService(ClientDeliveryAddressRepository addressRepository,
                                        ClientRepository clientRepository) {
        this.addressRepository = addressRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<DeliveryAddressResponse> list(UUID clientId, boolean includeArchived) {
        findClient(clientId); // 404 si le client n'existe pas, plutôt qu'une liste vide trompeuse
        List<ClientDeliveryAddress> addresses = includeArchived
                ? addressRepository.findByClientIdOrderByDefaultAddressDescLabelAsc(clientId)
                : addressRepository.findByClientIdAndActiveTrueOrderByDefaultAddressDescLabelAsc(clientId);
        return addresses.stream().map(DeliveryAddressResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DeliveryAddressResponse get(UUID clientId, UUID addressId) {
        return DeliveryAddressResponse.from(findAddress(clientId, addressId));
    }

    @Transactional
    public DeliveryAddressResponse create(UUID clientId, DeliveryAddressRequest request) {
        // D'abord l'UPDATE groupé : il vide le cache JPA, donc on charge le client après
        if (request.wantsDefault()) {
            addressRepository.clearDefault(clientId);
        }
        Client client = findClient(clientId);
        if (!client.isActive()) {
            throw new BusinessRuleException("Le client " + client.getCode() + " est archivé");
        }

        ClientDeliveryAddress address = new ClientDeliveryAddress(client);
        applyFields(address, request);
        // La première adresse d'un client devient son adresse par défaut
        address.setDefaultAddress(request.wantsDefault()
                || !addressRepository.existsByClientIdAndDefaultAddressTrue(clientId));
        return DeliveryAddressResponse.from(addressRepository.save(address));
    }

    /** Remplacement complet (PUT). defaultAddress=true retire le statut de l'ancienne adresse par défaut. */
    @Transactional
    public DeliveryAddressResponse update(UUID clientId, UUID addressId, DeliveryAddressRequest request) {
        if (request.wantsDefault()) {
            addressRepository.clearDefault(clientId);
        }
        // Si l'adresse est introuvable, l'exception annule toute la transaction, clearDefault compris
        ClientDeliveryAddress address = findAddress(clientId, addressId);
        if (request.wantsDefault() && !address.isActive()) {
            throw new IllegalArgumentException("Une adresse archivée ne peut pas être l'adresse par défaut");
        }
        applyFields(address, request);
        address.setDefaultAddress(request.wantsDefault());
        return DeliveryAddressResponse.from(address);
    }

    @Transactional
    public void archive(UUID clientId, UUID addressId) {
        findAddress(clientId, addressId).archive();
    }

    @Transactional
    public void reactivate(UUID clientId, UUID addressId) {
        findAddress(clientId, addressId).reactivate();
    }

    // --- Méthodes utilitaires ---

    private Client findClient(UUID clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
    }

    /** 404 si l'adresse n'existe pas OU si elle appartient à un autre client. */
    private ClientDeliveryAddress findAddress(UUID clientId, UUID addressId) {
        return addressRepository.findByIdAndClientId(addressId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Adresse de livraison", addressId));
    }

    private static void applyFields(ClientDeliveryAddress address, DeliveryAddressRequest request) {
        address.setLabel(request.label().trim());
        address.setRecipientName(blankToNull(request.recipientName()));
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(blankToNull(request.addressLine2()));
        address.setPostalCode(blankToNull(request.postalCode()));
        address.setCity(request.city().trim());
        address.setCountryCode(IsoCodes.country(request.countryCode()));
        address.setPhone(blankToNull(request.phone()));
        address.setDeliveryInstructions(blankToNull(request.deliveryInstructions()));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}