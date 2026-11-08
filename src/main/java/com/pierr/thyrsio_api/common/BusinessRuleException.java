package com.pierr.thyrsio_api.common;

/** Une règle métier empêche l'opération dans l'état actuel des données → 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}