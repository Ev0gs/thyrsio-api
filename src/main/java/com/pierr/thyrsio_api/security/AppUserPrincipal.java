package com.pierr.thyrsio_api.security;

import com.pierr.thyrsio_api.user.Role;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AppUserPrincipal(
        UUID id,
        String email,
        String passwordHash,
        Role role
) implements UserDetails {

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String toString() {
        // Le toString généré par le record afficherait le hash du mot de passe :
        // on l'exclut, pour qu'il n'apparaisse jamais dans les logs
        return "AppUserPrincipal[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
