package com.pierr.thyrsio_api.sales;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID>,
        JpaSpecificationExecutor<CustomerOrder> {

    @EntityGraph(attributePaths = "lines")
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findWithLinesById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "lines")
    @Query("select o from CustomerOrder o where o.id in :ids")
    List<CustomerOrder> findWithLinesByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("select o.code from CustomerOrder o where o.code like :pattern")
    List<String> findCodesLike(@Param("pattern") String pattern);
}
