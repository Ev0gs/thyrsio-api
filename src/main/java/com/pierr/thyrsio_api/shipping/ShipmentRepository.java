package com.pierr.thyrsio_api.shipping;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {

    @EntityGraph(attributePaths = "lines")
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findWithLinesById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "lines")
    @Query("select s from Shipment s where s.id in :ids")
    List<Shipment> findWithLinesByIdIn(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = "lines")
    List<Shipment> findByOrderIdOrderByPlannedDateAscCreatedAtAsc(UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, ShipmentStatus status);

    @Query("select s.code from Shipment s where s.code like :pattern")
    List<String> findCodesLike(@Param("pattern") String pattern);
}
