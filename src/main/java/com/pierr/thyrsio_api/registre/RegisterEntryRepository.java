package com.pierr.thyrsio_api.registre;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

interface RegisterEntryRepository extends JpaRepository<RegisterEntry, UUID>, JpaSpecificationExecutor<RegisterEntry> {

    /** Toutes les écritures, dans l'ordre : pour vérifier la chaîne des empreintes. */
    Stream<RegisterEntry> streamAllByOrderBySequenceNoAsc();

    /** La dernière écriture d'un type pour un lot (la réception d'un lot, pour la corriger). */
    Optional<RegisterEntry> findFirstByWineLotIdAndKindOrderBySequenceNoDesc(UUID wineLotId, String kind);
}
