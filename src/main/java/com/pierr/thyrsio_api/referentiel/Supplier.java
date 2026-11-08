package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    // Facultatifs : on ne connaît pas toujours toutes les coordonnées d'un fournisseur
    private String email;

    private String phone;

    private String address;

    private Integer averageLeadTimeDays;

    // Un fournisseur créé est toujours actif
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Supplier() {
    }

    public Supplier(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getAverageLeadTimeDays() {
        return averageLeadTimeDays;
    }

    public void setAverageLeadTimeDays(Integer averageLeadTimeDays) {
        this.averageLeadTimeDays = averageLeadTimeDays;
    }

    public boolean isActive() {
        return active;
    }

    /** Archive le fournisseur : il n'est plus proposé, mais l'historique qui le référence reste intact. */
    public void archive() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
