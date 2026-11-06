package com.pierr.thyrsio_api.user;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.TestcontainersConfig;
import com.pierr.thyrsio_api.keycloak.KeycloakAdminClient;
import com.pierr.thyrsio_api.security.KeycloakRealmRoleConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public class UserApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // Remplace le vrai client Keycloak : aucun appel réseau, et on peut vérifier les appels reçus
    @MockitoBean
    KeycloakAdminClient keycloakAdminClient;

    // Remplace le décodeur de tokens : les tests n'ont jamais besoin de joindre Keycloak
    @MockitoBean
    JwtDecoder jwtDecoder;

    // ==================== /api/users/me ====================

    @Test
    void me_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_with_invalid_token_returns_401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("Token invalide"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer faux-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_first_call_creates_profile_from_token() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(get("/api/users/me")
                .with(token(UUID.randomUUID(), email, "Pierre Test", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void me_reuses_profile_and_syncs_changes_from_keycloak() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        String profileId = provision(token(keycloakId, uniqueEmail(), "Ancien Nom", "USER"));

        // Même utilisateur Keycloak, mais son nom et son email ont changé dans Keycloak
        String newEmail = uniqueEmail();
        mockMvc.perform(get("/api/users/me")
                        .with(token(keycloakId, newEmail, "Nouveau Nom", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId))   // même profil, pas de doublon
                .andExpect(jsonPath("$.name").value("Nouveau Nom"))
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void delete_me_deletes_keycloak_account_and_profile() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        RequestPostProcessor me = token(keycloakId, uniqueEmail(), "Pierre Test", "USER");
        String profileId = provision(me);

        mockMvc.perform(delete("/api/users/me")
                    .with(me))
                .andExpect(status().isNoContent());

        verify(keycloakAdminClient).deleteUser(keycloakId);
        mockMvc.perform(get("/api/users/{id}", profileId)
                    .with(admin()))
                .andExpect(status().isNotFound());
    }

    // ==================== /api/users (admin) ====================

    @Test
    void user_cannot_list_users() throws Exception {
        mockMvc.perform(get("/api/users")
                    .with(token(UUID.randomUUID(), uniqueEmail(), "Pierre Test", "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_list_users() throws Exception {
        provision(token(UUID.randomUUID(), uniqueEmail(), "Pierre Test", "USER"));

        mockMvc.perform(get("/api/users").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void admin_grants_admin_role_through_keycloak() throws Exception {
        UUID targetKeycloakId = UUID.randomUUID();
        String targetId = provision(token(targetKeycloakId, uniqueEmail(), "Pierre Test", "USER"));

        mockMvc.perform(put("/api/users/{id}/roles/admin", targetId)
                    .with(admin()))
                .andExpect(status().isNoContent());

        verify(keycloakAdminClient).addRealmRole(targetKeycloakId, "ADMIN");
    }

    @Test
    void admin_revokes_admin_role_through_keycloak() throws Exception {
        UUID targetKeycloakId = UUID.randomUUID();
        String targetId = provision(token(targetKeycloakId, uniqueEmail(), "Pierre Test", "ADMIN"));

        mockMvc.perform(delete("/api/users/{id}/roles/admin", targetId)
                        .with(admin()))
                .andExpect(status().isNoContent());

        verify(keycloakAdminClient).removeRealmRole(targetKeycloakId, "ADMIN");
    }

    @Test void admin_cannot_revoke_own_admin_role() throws Exception {
        RequestPostProcessor me = token(UUID.randomUUID(), uniqueEmail(), "Pierre Test", "ADMIN");
        String myId = provision(me);

        mockMvc.perform(delete("/api/users/{id}/roles/admin", myId)
                        .with(me))
                .andExpect(status().isBadRequest());

        verify(keycloakAdminClient, never()).removeRealmRole(any(), any());
    }

    @Test
    void admin_deletes_user_in_keycloak_and_database() throws Exception {
        UUID targetKeycloakId = UUID.randomUUID();
        String targetId = provision(token(targetKeycloakId, uniqueEmail(), "Pierre Test", "USER"));

        mockMvc.perform(delete("/api/users/{id}", targetId).with(admin()))
                .andExpect(status().isNoContent());

        verify(keycloakAdminClient).deleteUser(targetKeycloakId);
        mockMvc.perform(get("/api/users/{id}", targetId).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test void admin_cannot_delete_own_account_through_admin_route() throws Exception {
        RequestPostProcessor me = token(UUID.randomUUID(), uniqueEmail(), "Pierre Test", "ADMIN");
        String myId = provision(me);

        mockMvc.perform(delete("/api/users/{id}", myId).with(me))
                .andExpect(status().isBadRequest());

        verify(keycloakAdminClient, never()).deleteUser(any());
    }

    @Test
    void grant_admin_on_unknown_user_returns_404() throws Exception {
        mockMvc.perform(put("/api/users/{id}/roles/admin", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void keycloak_failure_returns_502() throws Exception {
        String targetId = provision(token(UUID.randomUUID(), uniqueEmail(), "Cible", "USER"));

        doThrow(new RestClientException("Keycloak indisponible"))
                .when(keycloakAdminClient).addRealmRole(any(), any());

        mockMvc.perform(put("/api/users/{id}/roles/admin", targetId).with(admin()))
                .andExpect(status().isBadGateway());
    }

    // ==================== Méthodes utilitaires ====================

    /**
     * Un faux token, tel que Keycloak l'aurait délivré.
     * Les rôles passent par le vrai convertisseur de l'appli.
     */
    private static RequestPostProcessor token(UUID keycloakId, String email, String name, String... roles) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(keycloakId.toString())
                        .claim("email", email)
                        .claim("name", name)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    /** Un administrateur quelconque. */
    private static RequestPostProcessor admin() {
        return token(UUID.randomUUID(), uniqueEmail(), "Admin Test", "ADMIN");
    }

    /** Appelle /me pour créer le profil Thyrsio, et renvoie son id. */
    private String provision(RequestPostProcessor token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").with(token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Un email différent à chaque appel : les tests ne se gênent pas entre eux. */
    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
