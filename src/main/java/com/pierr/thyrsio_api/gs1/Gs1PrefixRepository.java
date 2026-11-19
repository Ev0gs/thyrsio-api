package com.pierr.thyrsio_api.gs1;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Gs1PrefixRepository extends JpaRepository<Gs1Prefix, UUID> {

    List<Gs1Prefix> findAllByOrderByCreatedAtAsc();

    /** Le préfixe verrouillé jusqu'à la fin de la transaction : deux générations simultanées s'attendent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Gs1Prefix p where p.id = :id")
    Optional<Gs1Prefix> lockById(@Param("id") UUID id);
}
