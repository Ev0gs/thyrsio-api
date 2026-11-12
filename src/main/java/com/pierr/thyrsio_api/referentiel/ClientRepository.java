package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    boolean existsByCode(String code);

    /** Les adresses à placer sur la carte : jamais localisées, ou modifiées depuis. */
    List<Client> findTop20ByGeocodingGeocodedAtIsNullOrderByCreatedAtDesc();

    /**
     * Le résultat du géocodage, enregistré seulement si l'adresse n'a pas changé pendant la recherche.
     * Une ligne jamais enregistrée par l'application (données de démo, reprise) n'a pas encore de requête : null.
     * Un UPDATE ciblé : il ne touche à aucun autre champ, même si l'utilisateur modifie la fiche en même temps.
     */
    @Modifying
    @Query("""
            update Client c
               set c.geocoding.latitude = :latitude,
                   c.geocoding.longitude = :longitude,
                   c.geocoding.locationApproximate = :approximate,
                   c.geocoding.geocodingQuery = :query,
                   c.geocoding.geocodedAt = :at
             where c.id = :id
               and c.geocoding.geocodedAt is null
               and (c.geocoding.geocodingQuery is null or c.geocoding.geocodingQuery = :query)
            """)
    int recordLocation(@Param("id") UUID id, @Param("query") String query, @Param("latitude") Double latitude,
                       @Param("longitude") Double longitude, @Param("approximate") Boolean approximate,
                       @Param("at") Instant at);
}