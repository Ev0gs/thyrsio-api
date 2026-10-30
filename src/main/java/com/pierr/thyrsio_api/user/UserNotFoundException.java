package com.pierr.thyrsio_api.user;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("Utilisateur introuvable : " + id);
    }

    public UserNotFoundException(String email) {
        super("Utilisateur introuvable : " + email);
    }
}