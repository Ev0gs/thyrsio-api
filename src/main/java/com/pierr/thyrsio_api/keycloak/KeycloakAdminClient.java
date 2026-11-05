package com.pierr.thyrsio_api.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appelle l'API REST d'administration de Keycloak au nom de l'application
 * (client "thyrsio-api-admin", flux OAuth2 "client credentials").
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final RestClient restClient;
    private final KeycloakAdminProperties props;

    // Token de l'application, réutilisé jusqu'à son expiration
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(KeycloakAdminProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    /** Supprime un compte Keycloak. Si le compte n'existe déjà plus, il n'y a rien à faire. */
    public void deleteUser(UUID keycloakId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", props.realm(), keycloakId)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyDeleted) {
            // Le résultat souhaité est déjà atteint
        }
    }

    /** Attribue un rôle du realm à un utilisateur. */
    public void addRealmRole(UUID keycloakId, String roleName) {
        Map<String, Object> role = getRealmRole(roleName);
        log.debug("Ajout du rôle {} pour l'utilisateur Keycloak {} : {}", roleName, keycloakId, role);

        restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), keycloakId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    /** Retire un rôle du realm à un utilisateur. */
    public void removeRealmRole(UUID keycloakId, String roleName) {
        Map<String, Object> role = getRealmRole(roleName);
        log.debug("Retrait du rôle {} pour l'utilisateur Keycloak {} : {}", roleName, keycloakId, role);

        // DELETE avec un corps de requête : RestClient passe par method(...)
        restClient.method(HttpMethod.DELETE)
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), keycloakId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    // --- Méthodes internes ---

    /**
     * Keycloak exige la représentation exacte du rôle (id + nom) pour l'attribuer.
     * On renvoie tel quel le JSON qu'il nous donne : aucune conversion, aucun risque d'écart.
     */
    private Map<String, Object> getRealmRole(String roleName) {
        return restClient.get()
                .uri("/admin/realms/{realm}/roles/{name}", props.realm(), roleName)
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /**
     * Renvoie un token valide pour l'application. On en redemande un à Keycloak
     * seulement s'il n'y en a pas encore, ou s'il expire dans moins de 30 secondes.
     * "synchronized" : deux requêtes simultanées ne demandent pas chacune un token.
     */
    private synchronized String token() {
        if (accessToken == null || Instant.now().isAfter(accessTokenExpiresAt.minusSeconds(30))) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", props.clientId());
            form.add("client_secret", props.clientSecret());

            TokenResponse response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            accessToken = response.accessToken();
            accessTokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());
        }
        return accessToken;
    }

    /** La réponse de Keycloak à une demande de token (on ne lit que ce qui nous sert). */
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}