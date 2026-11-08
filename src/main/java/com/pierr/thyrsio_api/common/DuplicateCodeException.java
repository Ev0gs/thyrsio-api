package com.pierr.thyrsio_api.common;

public class DuplicateCodeException extends RuntimeException {
    public DuplicateCodeException(String resource, String code) {
        super(resource + " : le code " + code + " existe déjà");
    }
}
