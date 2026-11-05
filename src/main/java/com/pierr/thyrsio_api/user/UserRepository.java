package com.pierr.thyrsio_api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByKeycloakId(UUID keycloakId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
