package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** Les adresses à placer sur la carte : jamais localisées, ou modifiées depuis. */
    List<ClientDeliveryAddress> findTop20ByGeocodingGeocodedAtIsNullOrderByCreatedAtDesc();

    /**
     * Le résultat du géocodage, enregistré seulement si l'adresse n'a pas changé pendant la recherche.
     * Une ligne jamais enregistrée par l'application (données de démo, reprise) n'a pas encore de requête : null.
     * Un UPDATE ciblé : il ne touche à aucun autre champ, même si l'utilisateur modifie la fiche en même temps.
     */
    @Modifying
    @Query("""
            update ClientDeliveryAddress a
               set a.geocoding.latitude = :latitude,
                   a.geocoding.longitude = :longitude,
                   a.geocoding.locationApproximate = :approximate,
                   a.geocoding.geocodingQuery = :query,
                   a.geocoding.geocodedAt = :at
             where a.id = :id
               and a.geocoding.geocodedAt is null
               and (a.geocoding.geocodingQuery is null or a.geocoding.geocodingQuery = :query)
            """)
    int recordLocation(@Param("id") UUID id, @Param("query") String query, @Param("latitude") Double latitude,
                       @Param("longitude") Double longitude, @Param("approximate") Boolean approximate,
                       @Param("at") Instant at);
}