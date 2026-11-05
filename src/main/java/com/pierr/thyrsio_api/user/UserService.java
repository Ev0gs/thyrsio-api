package com.pierr.thyrsio_api.user;

import com.pierr.thyrsio_api.keycloak.KeycloakAdminClient;
import com.pierr.thyrsio_api.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    public UserService(UserRepository userRepository, KeycloakAdminClient keycloakAdminClient) {
        this.userRepository = userRepository;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return UserResponse.from(findUserById(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    /**
     * Renvoie le profil Thyrsio de l'utilisateur connecté.
     * Le crée au premier appel, puis recopie à chaque appel l'email et le nom venant de Keycloak.
     */
    @Transactional
    public UserResponse syncCurrentUser(UUID keycloakId, String name, String email) {
        User user = userRepository.findByKeycloakId(keycloakId).orElse(null);

        if (user == null) {
            user = userRepository.save(new User(keycloakId, name, email));
        } else {
            // Dirty checking : Hibernate ne fait un UPDATE que si une valeur a réellement changé
            user.setName(name);
            user.setEmail(email);
        }
        return UserResponse.from(user);
    }

    /** L'utilisateur connecté supprime son propre compte. */
    @Transactional
    public void deleteAccount(UUID keycloakId) {
        keycloakAdminClient.deleteUser(keycloakId);
        // Le profil Thyrsio peut ne pas exister si l'utilisateur n'a jamais appelé /me
        userRepository.findByKeycloakId(keycloakId).ifPresent(userRepository::delete);
    }

    /** Un admin supprime le compte d'un utilisateur. */
    @Transactional
    public void delete(UUID id, UUID actingKeycloakId) {
        User user = findUserById(id);
        preventSelfAction(user, actingKeycloakId,
                "Pour supprimer votre propre compte, utilisez DELETE /api/users/me");

        // Keycloak d'abord : si cet appel échoue, la base n'est pas touchée
        keycloakAdminClient.deleteUser(user.getKeycloakId());
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public void grantAdmin(UUID id) {
        keycloakAdminClient.addRealmRole(findUserById(id).getKeycloakId(), ADMIN_ROLE);
    }

    @Transactional(readOnly = true)
    public void revokeAdmin(UUID id, UUID actingKeycloakId) {
        User user = findUserById(id);
        preventSelfAction(user, actingKeycloakId,
                "Un administrateur ne peut pas retirer son propre rôle ADMIN");
        keycloakAdminClient.removeRealmRole(user.getKeycloakId(), ADMIN_ROLE);
    }

    // --- Méthodes utilitaires ---

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private static void preventSelfAction(User user, UUID actingKeycloakId, String message) {
        if (user.getKeycloakId().equals(actingKeycloakId)) {
            throw new IllegalArgumentException(message);
        }
    }
}
