package com.pierr.thyrsio_api;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.keycloak.KeycloakAdminClient;
import com.pierr.thyrsio_api.security.KeycloakRealmRoleConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base des tests d'intégration : vrai Postgres (Testcontainers), vraie appli, faux tokens.
 * Toutes les classes qui en héritent partagent le même contexte Spring, donc un seul démarrage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class ApiIntegrationTest {

    /** L'utilisateur qui fait les appels dans les tests (sert de created_by). */
    protected static final UUID TEST_USER_ID = UUID.randomUUID();

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected KeycloakAdminClient keycloakAdminClient;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    // ==================== Tokens ====================

    protected static RequestPostProcessor token(UUID keycloakId, String... roles) {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(keycloakId.toString())
                        .claim("email", "test@example.com")
                        .claim("name", "Utilisateur Test")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    protected static RequestPostProcessor user() {
        return token(TEST_USER_ID, "USER");
    }

    // ==================== Requêtes ====================

    protected ResultActions getAsUser(String url, Object... uriVariables) throws Exception {
        return mockMvc.perform(get(url, uriVariables).with(user()));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).with(user()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).with(user()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions postEmpty(String url) throws Exception {
        return mockMvc.perform(post(url).with(user()));
    }

    /** Le corps de la réponse, en UTF-8 (sinon les accents des messages sont abîmés). */
    protected static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Lit un nombre dans le JSON en BigDecimal : 7000.0000 et 7000 sont alors comparables. */
    protected static BigDecimal decimal(String json, String path) {
        Object value = JsonPath.read(json, path);
        return new BigDecimal(value.toString());
    }

    // ==================== Données de test ====================

    /** "BOUCHON" devient "BOUCHON-1A2B3C4D" : un code différent à chaque appel. */
    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** Crée un article de la catégorie donnée (avec son unité par défaut) et renvoie son id. */
    protected String createArticle(String categoryCode) throws Exception {
        String json = """
                {"code": "%s", "label": "Article de test", "categoryCode": "%s"}
                """.formatted(unique(categoryCode), categoryCode);
        return JsonPath.read(body(postJson("/api/referentiel/articles", json)
                .andExpect(status().isCreated())), "$.id");
    }

    protected String createLot(String articleId, String lotNumber) throws Exception {
        String json = """
                {"articleId": "%s", "lotNumber": "%s"}
                """.formatted(articleId, lotNumber);
        return JsonPath.read(body(postJson("/api/stock/lots", json)
                .andExpect(status().isCreated())), "$.id");
    }

    /** Saisit un mouvement ; lotId peut être null. Le test vérifie lui-même le statut attendu. */
    protected ResultActions move(String articleId, String lotId, String type, String quantity) throws Exception {
        String lot = (lotId == null) ? "null" : "\"" + lotId + "\"";
        String json = """
                {"articleId": "%s", "lotId": %s, "type": "%s", "quantity": %s}
                """.formatted(articleId, lot, type, quantity);
        return postJson("/api/stock/movements", json);
    }

    /** Stock total d'un article, tous lots confondus. */
    protected BigDecimal stockOf(String articleId) throws Exception {
        String json = body(mockMvc.perform(get("/api/stock/balances")
                        .param("articleId", articleId)
                        .param("includeEmpty", "true")
                        .with(user()))
                .andExpect(status().isOk()));
        List<Object> quantities = JsonPath.read(json, "$.content[*].quantity");
        return quantities.stream()
                .map(quantity -> new BigDecimal(quantity.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}