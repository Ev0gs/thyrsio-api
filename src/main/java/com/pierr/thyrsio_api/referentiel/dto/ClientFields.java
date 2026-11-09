package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.Incoterm;

/** Les champs modifiables d'un client, communs à la création et à la modification. */
public interface ClientFields {
    String name();
    String countryCode();
    String language();
    String addressLine1();
    String addressLine2();
    String postalCode();
    String city();
    String email();
    String phone();
    String vatNumber();
    String exciseNumber();
    Incoterm defaultIncoterm();
    Boolean gs1LabelingRequired();
    String notes();
}