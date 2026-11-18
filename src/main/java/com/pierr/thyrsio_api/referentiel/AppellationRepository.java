package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppellationRepository extends JpaRepository<Appellation, UUID> {

    List<Appellation> findAllByOrderByNameAsc();

    List<Appellation> findByActiveTrueOrderByNameAsc();

    List<Appellation> findByIdIn(Collection<UUID> ids);

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);
}
