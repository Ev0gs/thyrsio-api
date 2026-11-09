package com.pierr.thyrsio_api.referentiel;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientApiIntegrationTest extends ApiIntegrationTest {

    // ==================== Clients ====================

    @Test
    void create_client_normalizes_code_country_and_vat() throws Exception {
        String code = unique("imp");

        postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Import Test", "countryCode": "fr", "vatNumber": "fr 12 345.678-901"}
                """.formatted(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code.toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("$.countryCode").value("FR"))
                .andExpect(jsonPath("$.vatNumber").value("FR12345678901"))
                .andExpect(jsonPath("$.language").value("fr"))          // langue par défaut
                .andExpect(jsonPath("$.gs1LabelingRequired").value(false));
    }

    @Test
    void duplicate_client_code_returns_409() throws Exception {
        String json = """
                {"code": "%s", "name": "Doublon", "countryCode": "FR"}
                """.formatted(unique("DUP"));

        postJson("/api/referentiel/clients", json).andExpect(status().isCreated());
        postJson("/api/referentiel/clients", json).andExpect(status().isConflict());
    }

    @Test
    void unknown_country_returns_400() throws Exception {
        postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Nulle part", "countryCode": "XX"}
                """.formatted(unique("CLI")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Code pays inconnu")));
    }

    @Test
    void malformed_vat_number_returns_400() throws Exception {
        postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "TVA fausse", "countryCode": "FR", "vatNumber": "12345"}
                """.formatted(unique("CLI")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknown_incoterm_returns_400() throws Exception {
        postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Incoterm faux", "countryCode": "FR", "defaultIncoterm": "XYZ"}
                """.formatted(unique("CLI")))
                .andExpect(status().isBadRequest());
    }

    // ==================== Adresses de livraison ====================

    @Test
    void first_address_becomes_default_and_a_new_default_replaces_it() throws Exception {
        String clientId = createClient();
        String addresses = "/api/referentiel/clients/" + clientId + "/delivery-addresses";

        // La première adresse devient l'adresse par défaut, même sans le demander
        String first = JsonPath.read(body(postJson(addresses, address("Entrepôt A", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true))), "$.id");

        // Une nouvelle adresse par défaut retire le statut de la précédente
        String second = JsonPath.read(body(postJson(addresses, address("Entrepôt B", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true))), "$.id");

        getAsUser(addresses)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))       // la par défaut en premier
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].defaultAddress").value(false));
    }

    @Test
    void address_of_another_client_returns_404() throws Exception {
        String owner = createClient();
        String other = createClient();
        String addressId = JsonPath.read(body(postJson(
                "/api/referentiel/clients/" + owner + "/delivery-addresses", address("Entrepôt", false))
                .andExpect(status().isCreated())), "$.id");

        // Bonne adresse, mauvais client dans l'URL
        getAsUser("/api/referentiel/clients/{clientId}/delivery-addresses/{addressId}", other, addressId)
                .andExpect(status().isNotFound());
    }

    @Test
    void archived_address_loses_default_status() throws Exception {
        String clientId = createClient();
        String addresses = "/api/referentiel/clients/" + clientId + "/delivery-addresses";
        String addressId = JsonPath.read(body(postJson(addresses, address("Entrepôt", true))
                .andExpect(status().isCreated())), "$.id");

        postEmpty(addresses + "/" + addressId + "/archive").andExpect(status().isNoContent());

        getAsUser(addresses + "/" + addressId)
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.defaultAddress").value(false));
    }

    @Test
    void cannot_add_address_to_archived_client() throws Exception {
        String clientId = createClient();
        postEmpty("/api/referentiel/clients/" + clientId + "/archive").andExpect(status().isNoContent());

        postJson("/api/referentiel/clients/" + clientId + "/delivery-addresses", address("Entrepôt", false))
                .andExpect(status().isConflict());
    }

    @Test
    void addresses_of_unknown_client_return_404() throws Exception {
        getAsUser("/api/referentiel/clients/{id}/delivery-addresses", UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    // ==================== Méthodes utilitaires ====================

    private String createClient() throws Exception {
        return JsonPath.read(body(postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Client de test", "countryCode": "JP", "language": "en"}
                """.formatted(unique("CLI")))
                .andExpect(status().isCreated())), "$.id");
    }

    private static String address(String label, boolean isDefault) {
        return """
                {"label": "%s", "addressLine1": "1-2-3 Minato", "city": "Yokohama",
                 "countryCode": "JP", "defaultAddress": %s}
                """.formatted(label, isDefault);
    }
}