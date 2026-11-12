package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    // Query methods : "WHERE active = true", "WHERE kind = ?", avec pagination
    Page<Supplier> findByActiveTrue(Pageable pageable);

    Page<Supplier> findByKind(SupplierKind kind, Pageable pageable);

    Page<Supplier> findByActiveTrueAndKind(SupplierKind kind, Pageable pageable);

    /** Les adresses à placer sur la carte : jamais localisées, ou modifiées depuis. */
    List<Supplier> findTop20ByGeocodingGeocodedAtIsNullOrderByCreatedAtDesc();

    /**
     * Le résultat du géocodage, enregistré seulement si l'adresse n'a pas changé pendant la recherche.
     * Une ligne jamais enregistrée par l'application (données de démo, reprise) n'a pas encore de requête : null.
     * Un UPDATE ciblé : il ne touche à aucun autre champ, même si l'utilisateur modifie la fiche en même temps.
     */
    @Modifying
    @Query("""
            update Supplier s
               set s.geocoding.latitude = :latitude,
                   s.geocoding.longitude = :longitude,
                   s.geocoding.locationApproximate = :approximate,
                   s.geocoding.geocodingQuery = :query,
                   s.geocoding.geocodedAt = :at
             where s.id = :id
               and s.geocoding.geocodedAt is null
               and (s.geocoding.geocodingQuery is null or s.geocoding.geocodingQuery = :query)
            """)
    int recordLocation(@Param("id") UUID id, @Param("query") String query, @Param("latitude") Double latitude,
                       @Param("longitude") Double longitude, @Param("approximate") Boolean approximate,
                       @Param("at") Instant at);
}
