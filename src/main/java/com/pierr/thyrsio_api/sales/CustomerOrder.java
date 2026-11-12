package com.pierr.thyrsio_api.sales;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.referentiel.Incoterm;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Une commande client : la racine de l'agrégat, ses lignes ne se modifient qu'à travers elle.
 * Brouillon ou confirmée sans rien d'expédié : modifiable. Ensuite, seules les expéditions la font avancer.
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    /** Une ligne voulue : quantité et prix unitaire (facultatif). */
    record LineValues(int quantity, BigDecimal unitPrice) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    // UUID : le client et son adresse appartiennent au module Référentiel
    @Column(nullable = false, updatable = false)
    private UUID clientId;

    private UUID deliveryAddressId;

    private String customerReference;

    @Column(nullable = false)
    private LocalDate orderDate;

    private LocalDate requestedLoadingDate;

    @Enumerated(EnumType.STRING)
    private Incoterm incoterm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.DRAFT;

    private String notes;

    private Instant confirmedAt;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CustomerOrderLine> lines = new ArrayList<>();

    protected CustomerOrder() {
    }

    CustomerOrder(String code, UUID clientId, UUID createdBy) {
        this.code = code;
        this.clientId = clientId;
        this.createdBy = createdBy;
    }

    // ========== Saisie ==========

    void describe(UUID deliveryAddressId, String customerReference, LocalDate orderDate,
                  LocalDate requestedLoadingDate, Incoterm incoterm, String notes) {
        requireEditable();
        this.deliveryAddressId = deliveryAddressId;
        this.customerReference = customerReference;
        this.orderDate = orderDate;
        this.requestedLoadingDate = requestedLoadingDate;
        this.incoterm = incoterm;
        this.notes = notes;
        touch();
    }

    /** Remplace les lignes, en mettant à jour sur place celles qui restent (même raison que pour les mises). */
    void replaceLines(Map<UUID, LineValues> valuesByArticle) {
        requireEditable();
        lines.removeIf(line -> !valuesByArticle.containsKey(line.getArticleId()));
        Map<UUID, CustomerOrderLine> existing = new HashMap<>();
        lines.forEach(line -> existing.put(line.getArticleId(), line));
        valuesByArticle.forEach((articleId, values) -> {
            CustomerOrderLine line = existing.get(articleId);
            if (line != null) {
                line.change(values.quantity(), values.unitPrice());
            } else {
                lines.add(new CustomerOrderLine(this, articleId, values.quantity(), values.unitPrice()));
            }
        });
        touch();
    }

    // ========== Cycle de vie ==========

    void confirm() {
        if (status != OrderStatus.DRAFT) {
            throw new BusinessRuleException("La commande " + code + " n'est pas un brouillon (" + status + ")");
        }
        if (lines.isEmpty()) {
            throw new BusinessRuleException("La commande " + code + " n'a aucune ligne");
        }
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
        touch();
    }

    void cancel() {
        if (status != OrderStatus.DRAFT && status != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException("La commande " + code + " a déjà été expédiée en partie ou en totalité : "
                    + "elle ne peut plus être annulée");
        }
        this.status = OrderStatus.CANCELLED;
        touch();
    }

    /** Une expédition est partie : les lignes avancent, et la commande avec. */
    void recordShipped(Map<UUID, Integer> bottlesByLine) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("La commande " + code + " n'est pas confirmée (" + status + ")");
        }
        bottlesByLine.forEach((lineId, bottles) -> findLine(lineId)
                .orElseThrow(() -> new IllegalArgumentException("Ligne " + lineId + " absente de la commande " + code))
                .ship(bottles));
        boolean allShipped = lines.stream().allMatch(line -> line.remaining() == 0);
        this.status = allShipped ? OrderStatus.SHIPPED : OrderStatus.PARTIALLY_SHIPPED;
        touch();
    }

    /** Modifiable : brouillon, ou confirmée sans rien d'expédié. */
    void requireEditable() {
        boolean nothingShipped = lines.stream().allMatch(line -> line.getShippedQuantity() == 0);
        if (!(status == OrderStatus.DRAFT || (status == OrderStatus.CONFIRMED && nothingShipped))) {
            throw new BusinessRuleException("La commande " + code + " ne peut plus être modifiée (" + status + ")");
        }
    }

    Optional<CustomerOrderLine> findLine(UUID lineId) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ========== Getters ==========

    public UUID getId() { return id; }

    public String getCode() { return code; }

    public UUID getClientId() { return clientId; }

    public UUID getDeliveryAddressId() { return deliveryAddressId; }

    public String getCustomerReference() { return customerReference; }

    public LocalDate getOrderDate() { return orderDate; }

    public LocalDate getRequestedLoadingDate() { return requestedLoadingDate; }

    public Incoterm getIncoterm() { return incoterm; }

    public OrderStatus getStatus() { return status; }

    public String getNotes() { return notes; }

    public Instant getConfirmedAt() { return confirmedAt; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public List<CustomerOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
}
