package com.pierr.thyrsio_api.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CustomerOrderLineRepository extends JpaRepository<CustomerOrderLine, UUID> {

    /**
     * Les lignes qui attendent encore des bouteilles, avec leur commande, dans l'ordre où elles seront servies :
     * la date de chargement souhaitée d'abord (sans date : en dernier), puis la date de commande.
     */
    @Query("""
            select l from CustomerOrderLine l join fetch l.order o
             where o.status in :statuses and l.shippedQuantity < l.quantity
             order by o.requestedLoadingDate asc nulls last, o.orderDate asc, o.code asc
            """)
    List<CustomerOrderLine> findOpenLines(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("select l from CustomerOrderLine l join fetch l.order where l.id in :ids")
    List<CustomerOrderLine> findWithOrderByIdIn(@Param("ids") Collection<UUID> ids);
}
