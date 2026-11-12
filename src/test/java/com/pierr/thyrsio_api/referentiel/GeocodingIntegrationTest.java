package com.pierr.thyrsio_api.referentiel;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La tâche de géocodage avec un faux géocodeur (les tests n'appellent pas Internet) :
 * Bordeaux est trouvée à la rue près, « Nulle-Part » nulle part, le reste à la ville près.
 */
class GeocodingIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ClientDeliveryAddressRepository addressRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<GeocodingRequest> searched = new ArrayList<>();
    private GeocodingJob job;

    @BeforeEach
    void setUp() {
        Geocoder fake = request -> {
            searched.add(request);
            String text = request.query();
            if (text.contains("Nulle-Part")) {
                return Optional.empty();
            }
            return Optional.of(text.contains("Bordeaux")
                    ? new GeoLocation(44.8378, -0.5792, false)
                    : new GeoLocation(45.764, 4.8357, true));
        };
        job = new GeocodingJob(clientRepository, addressRepository, supplierRepository, fake,
                new TransactionTemplate(transactionManager));
    }

    @Test
    void a_client_is_located_and_relocated_when_its_address_changes() throws Exception {
        String id = JsonPath.read(body(postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Cave Bordelaise", "countryCode": "fr",
                 "addressLine1": "10 quai des Chartrons", "postalCode": "33000", "city": "Bordeaux"}
                """.formatted(unique("GEO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.locationStatus").value("PENDING"))), "$.id");

        job.runOnce();

        getAsUser("/api/referentiel/clients/" + id)
                .andExpect(jsonPath("$.location.latitude").value(44.8378))
                .andExpect(jsonPath("$.location.longitude").value(-0.5792))
                .andExpect(jsonPath("$.location.approximate").value(false))
                .andExpect(jsonPath("$.locationStatus").value("LOCATED"));
        assertThat(searched).anySatisfy(request -> {
            assertThat(request.street()).isEqualTo("10 quai des Chartrons");
            assertThat(request.countryCode()).isEqualTo("FR");
        });

        // Un champ sans rapport avec l'adresse : la position reste
        putJson("/api/referentiel/clients/" + id, """
                {"name": "Cave Bordelaise", "countryCode": "FR", "notes": "Client fidèle",
                 "addressLine1": "10 quai des Chartrons", "postalCode": "33000", "city": "Bordeaux"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.location.latitude").value(44.8378));

        // Le client déménage : l'ancienne position disparaît, puis la nouvelle est trouvée
        putJson("/api/referentiel/clients/" + id, """
                {"name": "Cave Bordelaise", "countryCode": "FR",
                 "addressLine1": "12 montée des Tisserands", "postalCode": "69001", "city": "Lyon"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.location").doesNotExist());

        job.runOnce();

        getAsUser("/api/referentiel/clients/" + id)
                .andExpect(jsonPath("$.location.latitude").value(45.764))
                .andExpect(jsonPath("$.location.approximate").value(true));
    }

    /** Les données de démo et les reprises sont insérées en SQL : elles n'ont pas encore de requête enregistrée. */
    @Test
    void a_client_inserted_outside_the_application_is_located_too() throws Exception {
        String code = unique("SQL").toUpperCase();
        jdbc.update("INSERT INTO clients (code, name, country_code, address_line1, postal_code, city) "
                + "VALUES (?, 'Repris en SQL', 'FR', '1 cours de l''Intendance', '33000', 'Bordeaux')", code);
        String id = jdbc.queryForObject("SELECT id::text FROM clients WHERE code = ?", String.class, code);

        job.runOnce();

        getAsUser("/api/referentiel/clients/" + id)
                .andExpect(jsonPath("$.locationStatus").value("LOCATED"))
                .andExpect(jsonPath("$.location.latitude").value(44.8378));
        // Trouvé une fois pour toutes : le passage suivant ne le cherche plus
        long searches = searched.stream().filter(request -> "1 cours de l'Intendance".equals(request.street())).count();
        job.runOnce();
        assertThat(searched.stream().filter(request -> "1 cours de l'Intendance".equals(request.street())).count())
                .isEqualTo(searches).isEqualTo(1);
    }

    @Test
    void an_address_that_cannot_be_found_is_not_searched_again() throws Exception {
        String id = JsonPath.read(body(postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Introuvable", "countryCode": "FR", "city": "Nulle-Part"}
                """.formatted(unique("GEO")))
                .andExpect(status().isCreated())), "$.id");

        job.runOnce();
        long attempts = searched.stream().filter(request -> "Nulle-Part".equals(request.city())).count();
        job.runOnce();

        assertThat(searched.stream().filter(request -> "Nulle-Part".equals(request.city())).count())
                .isEqualTo(attempts).isEqualTo(1);
        getAsUser("/api/referentiel/clients/" + id)
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.locationStatus").value("NOT_FOUND"));
    }

    @Test
    void a_client_without_a_town_has_nothing_to_locate() throws Exception {
        postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Sans adresse", "countryCode": "FR"}
                """.formatted(unique("GEO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationStatus").value("NO_ADDRESS"));
    }

    @Test
    void delivery_addresses_and_suppliers_are_located_too() throws Exception {
        String clientId = JsonPath.read(body(postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Importateur", "countryCode": "BE"}
                """.formatted(unique("GEO")))
                .andExpect(status().isCreated())), "$.id");
        String addressId = JsonPath.read(body(postJson(
                "/api/referentiel/clients/" + clientId + "/delivery-addresses", """
                        {"label": "Entrepôt", "addressLine1": "5 rue du Port", "postalCode": "33000",
                         "city": "Bordeaux", "countryCode": "FR"}
                        """).andExpect(status().isCreated())), "$.id");
        String supplierId = JsonPath.read(body(postJson("/api/referentiel/suppliers", """
                {"name": "%s", "address": "2 chemin des Palus\\n33710 Bourg"}
                """.formatted(unique("Vignobles")))
                .andExpect(status().isCreated())), "$.id");

        job.runOnce();

        getAsUser("/api/referentiel/clients/" + clientId + "/delivery-addresses/" + addressId)
                .andExpect(jsonPath("$.location.latitude").value(44.8378));
        getAsUser("/api/referentiel/suppliers/" + supplierId)
                .andExpect(jsonPath("$.location.approximate").value(true));
        // L'adresse sur deux lignes est cherchée comme une seule
        assertThat(searched).anySatisfy(request ->
                assertThat(request.freeText()).isEqualTo("2 chemin des Palus, 33710 Bourg"));
    }
}
