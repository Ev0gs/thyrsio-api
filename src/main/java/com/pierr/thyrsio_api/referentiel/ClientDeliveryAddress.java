package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_delivery_addresses")
public class ClientDeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Même module → vraie relation. Une adresse ne change jamais de client.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @Column(nullable = false)
    private String label;

    private String recipientName;

    @Column(nullable = false)
    private String addressLine1;

    private String addressLine2;

    private String postalCode;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String countryCode;

    private String phone;

    private String deliveryInstructions;

    @Column(nullable = false)
    private boolean defaultAddress = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // La position sur la carte, calculée en tâche de fond à partir de l'adresse (GeocodingJob)
    @Embedded
    private Geocoding geocoding;

    protected ClientDeliveryAddress() {
    }

    public ClientDeliveryAddress(Client client) {
        this.client = client;
    }

    public UUID getId() { return id; }

    public Client getClient() { return client; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDeliveryInstructions() { return deliveryInstructions; }
    public void setDeliveryInstructions(String deliveryInstructions) { this.deliveryInstructions = deliveryInstructions; }

    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }

    public boolean isActive() { return active; }

    /** Une adresse archivée perd son statut d'adresse par défaut (la base l'exige aussi). */
    public void archive() {
        this.active = false;
        this.defaultAddress = false;
    }

    public void reactivate() { this.active = true; }

    public Instant getCreatedAt() { return createdAt; }

    /** null tant que l'adresse n'est pas localisée, ou si elle est introuvable. */
    public GeoLocation getLocation() { return Geocoding.location(geocoding, geocodingRequest()); }

    public LocationStatus getLocationStatus() { return Geocoding.status(geocoding, geocodingRequest()); }

    GeocodingRequest geocodingRequest() {
        return GeocodingRequest.structured(addressLine1, postalCode, city, countryCode);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        geocoding = Geocoding.track(geocoding, geocodingRequest());
    }

    @PreUpdate
    protected void onUpdate() {
        geocoding = Geocoding.track(geocoding, geocodingRequest());
    }
}