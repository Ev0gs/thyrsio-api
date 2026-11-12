package com.pierr.thyrsio_api.referentiel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** La porte d'entrée des autres modules vers les clients et leurs adresses de livraison. */
@Service
public class ClientLookup {

    private final ClientRepository clientRepository;
    private final ClientDeliveryAddressRepository addressRepository;

    public ClientLookup(ClientRepository clientRepository, ClientDeliveryAddressRepository addressRepository) {
        this.clientRepository = clientRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ClientInfo> findById(UUID id) {
        return clientRepository.findById(id).map(ClientInfo::from);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ClientInfo> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return clientRepository.findAllById(ids).stream()
                .map(ClientInfo::from)
                .collect(Collectors.toMap(ClientInfo::id, Function.identity()));
    }

    /** L'adresse, seulement si elle appartient bien à ce client. */
    @Transactional(readOnly = true)
    public Optional<DeliveryAddressInfo> findAddress(UUID clientId, UUID addressId) {
        return addressRepository.findByIdAndClientId(addressId, clientId).map(DeliveryAddressInfo::from);
    }

    /** L'adresse par défaut du client, s'il en a une active. */
    @Transactional(readOnly = true)
    public Optional<DeliveryAddressInfo> findDefaultAddress(UUID clientId) {
        return addressRepository.findByClientIdAndActiveTrueOrderByDefaultAddressDescLabelAsc(clientId).stream()
                .findFirst()
                .map(DeliveryAddressInfo::from);
    }
}
