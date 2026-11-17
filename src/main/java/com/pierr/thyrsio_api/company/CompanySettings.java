package com.pierr.thyrsio_api.company;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** L'entreprise qui utilise Thyrsio : la page de garde des registres, l'en-tête des documents. Une seule ligne. */
@Entity
@Table(name = "company_settings")
public class CompanySettings {

    @Id
    private short id;

    private String legalName;
    private String exciseNumber;
    private String siret;
    private String vatNumber;
    private String addressLine1;
    private String addressLine2;
    private String postalCode;
    private String city;

    @Column(nullable = false)
    private String countryCode = "FR";

    private String phone;
    private String email;
    private LocalDate registerStartDate;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CompanySettings() {
    }

    public String getLegalName() { return legalName; }
    public String getExciseNumber() { return exciseNumber; }
    public String getSiret() { return siret; }
    public String getVatNumber() { return vatNumber; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getPostalCode() { return postalCode; }
    public String getCity() { return city; }
    public String getCountryCode() { return countryCode; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public LocalDate getRegisterStartDate() { return registerStartDate; }
    public Instant getUpdatedAt() { return updatedAt; }

    void update(String legalName, String exciseNumber, String siret, String vatNumber, String addressLine1,
                String addressLine2, String postalCode, String city, String countryCode, String phone, String email,
                LocalDate registerStartDate) {
        this.legalName = legalName;
        this.exciseNumber = exciseNumber;
        this.siret = siret;
        this.vatNumber = vatNumber;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.postalCode = postalCode;
        this.city = city;
        this.countryCode = countryCode;
        this.phone = phone;
        this.email = email;
        this.registerStartDate = registerStartDate;
        this.updatedAt = Instant.now();
    }
}
