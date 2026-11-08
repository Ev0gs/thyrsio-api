package com.pierr.thyrsio_api.referentiel;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String language = "fr";

    private String addressLine1;
    private String addressLine2;
    private String postalCode;
    private String city;
    private String email;
    private String phone;
    private String vatNumber;
    private String exciseNumber;

    @Enumerated(EnumType.STRING)
    private Incoterm defaultIncoterm;

    @Column(nullable = false)
    private boolean gs1LabelingRequired = false;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Client() {
    }

    /** Le service remplit les autres champs avant l'enregistrement ; la base refuse un client sans nom ni pays. */
    public Client(String code) {
        this.code = code;
    }

    public UUID getId() { return id; }

    public String getCode() { return code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }

    public String getExciseNumber() { return exciseNumber; }
    public void setExciseNumber(String exciseNumber) { this.exciseNumber = exciseNumber; }

    public Incoterm getDefaultIncoterm() { return defaultIncoterm; }
    public void setDefaultIncoterm(Incoterm defaultIncoterm) { this.defaultIncoterm = defaultIncoterm; }

    public boolean isGs1LabelingRequired() { return gs1LabelingRequired; }
    public void setGs1LabelingRequired(boolean gs1LabelingRequired) { this.gs1LabelingRequired = gs1LabelingRequired; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }

    public void archive() { this.active = false; }

    public void reactivate() { this.active = true; }

    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}