package com.pierr.thyrsio_api.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Lien avec le compte Keycloak (claim "sub" du token). Ne change jamais.
    @Column(nullable = false, updatable = false, unique = true)
    private UUID keycloakId;

    // Copies synchronisées depuis Keycloak, qui reste la source de vérité
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(UUID keycloakId, String name, String email) {
        this.keycloakId = keycloakId;
        this.name = name;
        this.email = email;
    }

    public UUID getId() { return id; }

    public UUID getKeycloakId() { return keycloakId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
