package com.pierr.thyrsio_api.cuverie;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TankRepository extends JpaRepository<Tank, UUID> {

    // SELECT ... FOR UPDATE : comme les soldes du stock, une cuve qu'on remplit ou qu'on vide
    // est verrouillée jusqu'à la fin de la transaction
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tank> findForUpdateById(UUID id);

    boolean existsByCode(String code);

    // Le lot est chargé dans la même requête : la vue des cuves l'affiche pour chaque ligne
    @EntityGraph(attributePaths = "wineLot")
    List<Tank> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "wineLot")
    List<Tank> findByActiveTrueOrderByCodeAsc();

    @EntityGraph(attributePaths = "wineLot")
    @Query("select t from Tank t where t.id = :id")
    Optional<Tank> findWithLotById(@Param("id") UUID id);

    /** Les cuves qui contiennent ce lot. */
    List<Tank> findByWineLotIdOrderByCodeAsc(UUID wineLotId);

    /** Le volume actuel de chaque lot, calculé par la base : une ligne par lot en cuve. */
    @Query("""
            select new com.pierr.thyrsio_api.cuverie.WineLotVolume(t.wineLot.id, sum(t.volumeHl), count(t))
              from Tank t
             where t.wineLot.id in :wineLotIds
             group by t.wineLot.id
            """)
    List<WineLotVolume> volumesByLot(@Param("wineLotIds") Collection<UUID> wineLotIds);
}
