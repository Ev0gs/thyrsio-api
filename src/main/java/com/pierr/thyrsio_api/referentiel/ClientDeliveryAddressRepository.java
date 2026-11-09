package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientDeliveryAddressRepository extends JpaRepository<ClientDeliveryAddress, UUID> {

    /** L'adresse, seulement si elle appartient bien à ce client. */
    Optional<ClientDeliveryAddress> findByIdAndClientId(UUID id, UUID clientId);

    // L'adresse par défaut en premier, puis par libellé
    List<ClientDeliveryAddress> findByClientIdOrderByDefaultAddressDescLabelAsc(UUID clientId);

    List<ClientDeliveryAddress> findByClientIdAndActiveTrueOrderByDefaultAddressDescLabelAsc(UUID clientId);

    boolean existsByClientIdAndDefaultAddressTrue(UUID clientId);

    /**
     * Un seul UPDATE en base pour retirer l'adresse par défaut actuelle.
     * flushAutomatically : envoie d'abord les modifications en attente.
     * clearAutomatically : vide ensuite le cache JPA, qui ne sait rien de cet UPDATE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ClientDeliveryAddress a
               set a.defaultAddress = false
             where a.client.id = :clientId and a.defaultAddress = true
            """)
    int clearDefault(@Param("clientId") UUID clientId);
}