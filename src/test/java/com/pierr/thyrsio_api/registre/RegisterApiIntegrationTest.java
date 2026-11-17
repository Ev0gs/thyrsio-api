package com.pierr.thyrsio_api.registre;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Le cahier de chai : chaque opération y écrit sa ligne, scellée et inaltérable. */
class RegisterApiIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void a_reception_writes_its_entry_in_the_bulk_wine_register() throws Exception {
        String supplier = wineSupplier();
        String tank = createTank("100");
        String lotId = JsonPath.read(body(postJson("/api/cuverie/receptions", """
                {"supplierId": "%s", "appellation": "Bordeaux Supérieur", "color": "RED", "vintage": 2023,
                 "daeNumber": "24FRG92622010", "fills": [{"tankId": "%s", "volumeHl": 75}]}
                """.formatted(supplier, tank)).andExpect(status().isCreated())), "$.id");

        getAsUser("/api/registre/entries?book=ENTREES_VRAC&wineLotId=" + lotId)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].kind").value("RECEPTION"))
                .andExpect(jsonPath("$.content[0].bookLabel").value("Entrées de vin en vrac"))
                .andExpect(jsonPath("$.content[0].denomination").value("Bordeaux Supérieur rouge 2023"))
                .andExpect(jsonPath("$.content[0].reference").value("24FRG92622010"))
                .andExpect(jsonPath("$.content[0].summary", containsString("Entrée de 75 hl")))
                .andExpect(jsonPath("$.content[0].data.dae").value("24FRG92622010"))
                .andExpect(jsonPath("$.content[0].data.fills[0].volumeHl").value(75))
                .andExpect(jsonPath("$.content[0].data.lotBalanceHl").value(75))
                .andExpect(jsonPath("$.content[0].late").value(false))
                .andExpect(jsonPath("$.content[0].hash").isString());
    }

    @Test
    void transfers_losses_and_measurements_keep_the_lot_balance() throws Exception {
        String supplier = wineSupplier();
        String source = createTank("100");
        String destination = createTank("100");
        String lotId = receive(supplier, source, "80");

        postJson("/api/cuverie/transfers", """
                {"sourceTankId": "%s", "destinationTankId": "%s", "volumeHl": 30, "lossHl": 0.5}
                """.formatted(source, destination)).andExpect(status().isCreated());
        postJson("/api/cuverie/tanks/" + destination + "/losses", """
                {"volumeHl": 1}
                """).andExpect(status().isCreated());
        postJson("/api/cuverie/tanks/" + source + "/measurements", """
                {"measuredHl": 49}
                """).andExpect(status().isCreated());

        // Les plus récentes d'abord : jaugeage, perte, transfert
        getAsUser("/api/registre/entries?book=MOUVEMENTS&wineLotId=" + lotId)
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].kind").value("MEASURE"))
                .andExpect(jsonPath("$.content[0].data.recordedHl").value(49.5))
                .andExpect(jsonPath("$.content[0].data.lotBalanceHl").value(78))
                .andExpect(jsonPath("$.content[1].kind").value("LOSS"))
                .andExpect(jsonPath("$.content[1].data.lotBalanceHl").value(78.5))
                .andExpect(jsonPath("$.content[2].kind").value("TRANSFER"))
                .andExpect(jsonPath("$.content[2].data.lossHl").value(0.5))
                .andExpect(jsonPath("$.content[2].data.lotBalanceHl").value(79.5));
    }

    @Test
    void the_register_is_numbered_without_gaps_and_its_chain_verifies() throws Exception {
        String supplier = wineSupplier();
        receive(supplier, createTank("50"), "10");
        receive(supplier, createTank("50"), "20");

        List<Long> sequences = jdbc.queryForList("select sequence_no from register_entries order by sequence_no", Long.class);
        for (int i = 0; i < sequences.size(); i++) {
            assertThat(sequences.get(i)).isEqualTo(i + 1L);
        }
        getAsUser("/api/registre/verify")
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.verifiedEntries").value(sequences.size()))
                .andExpect(jsonPath("$.lastHash").isString());
    }

    @Test
    void the_database_refuses_to_change_or_delete_an_entry() throws Exception {
        receive(wineSupplier(), createTank("50"), "10");

        assertThatThrownBy(() -> jdbc.update("update register_entries set summary = 'retouché'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inaltérable");
        assertThatThrownBy(() -> jdbc.update("delete from register_entries"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inaltérable");
    }

    @Test
    void correcting_a_lot_writes_a_counter_entry_linked_to_its_reception() throws Exception {
        String lotId = receive(wineSupplier(), createTank("50"), "10");
        String receptionEntry = JsonPath.read(body(getAsUser(
                "/api/registre/entries?book=ENTREES_VRAC&wineLotId=" + lotId)), "$.content[0].id");

        putJson("/api/cuverie/wine-lots/" + lotId, """
                {"appellation": "Bordeaux", "color": "RED", "vintage": 2023, "daeNumber": "24FRG92622010"}
                """).andExpect(status().isOk());

        getAsUser("/api/registre/entries?book=ENTREES_VRAC&wineLotId=" + lotId)
                .andExpect(jsonPath("$.content[0].kind").value("CORRECTION"))
                .andExpect(jsonPath("$.content[0].correctsEntryId").value(receptionEntry))
                .andExpect(jsonPath("$.content[0].data.changes.appellation.before").value("Bordeaux Supérieur"))
                .andExpect(jsonPath("$.content[0].data.changes.appellation.after").value("Bordeaux"))
                .andExpect(jsonPath("$.content[0].summary",
                        containsString("Bordeaux Supérieur rouge 2023 devient Bordeaux rouge 2023")))
                // La réception d'origine reste telle quelle
                .andExpect(jsonPath("$.content[1].kind").value("RECEPTION"))
                .andExpect(jsonPath("$.content[1].denomination").value("Bordeaux Supérieur rouge 2023"));
    }

    @Test
    void an_operation_recorded_too_late_is_flagged() throws Exception {
        String supplier = wineSupplier();
        String tank = createTank("50");
        String lotId = JsonPath.read(body(postJson("/api/cuverie/receptions", """
                {"supplierId": "%s", "appellation": "Bordeaux", "color": "RED", "vintage": 2024,
                 "receivedAt": "%s", "fills": [{"tankId": "%s", "volumeHl": 10}]}
                """.formatted(supplier, Instant.now().minus(10, ChronoUnit.DAYS), tank))
                .andExpect(status().isCreated())), "$.id");

        getAsUser("/api/registre/entries?late=true&wineLotId=" + lotId)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].late").value(true));
    }

    @Test
    void the_company_sheet_feeds_the_register_cover_page() throws Exception {
        putJson("/api/company", """
                {"legalName": "SARL Test", "exciseNumber": "fr 0109 26e2586", "addressLine1": "1 route des Vignes",
                 "postalCode": "33330", "city": "Saint-Émilion", "countryCode": "fr", "registerStartDate": "2027-01-01"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.exciseNumber").value("FR010926E2586"))
                .andExpect(jsonPath("$.countryCode").value("FR"));

        getAsUser("/api/company")
                .andExpect(jsonPath("$.legalName").value("SARL Test"))
                .andExpect(jsonPath("$.registerStartDate").value("2027-01-01"));
    }

    // ---------- Outils ----------

    private String wineSupplier() throws Exception {
        return JsonPath.read(body(postJson("/api/referentiel/suppliers", """
                {"name": "%s", "kind": "WINE"}
                """.formatted(unique("Château"))).andExpect(status().isCreated())), "$.id");
    }

    private String createTank(String capacityHl) throws Exception {
        return JsonPath.read(body(postJson("/api/cuverie/tanks", """
                {"code": "%s", "capacityHl": %s}
                """.formatted(unique("C"), capacityHl)).andExpect(status().isCreated())), "$.id");
    }

    /** Réceptionne un Bordeaux Supérieur rouge 2023 dans une cuve ; renvoie l'id du lot. */
    private String receive(String supplierId, String tankId, String volumeHl) throws Exception {
        return JsonPath.read(body(postJson("/api/cuverie/receptions", """
                {"supplierId": "%s", "appellation": "Bordeaux Supérieur", "color": "RED", "vintage": 2023,
                 "daeNumber": "24FRG92622010", "fills": [{"tankId": "%s", "volumeHl": %s}]}
                """.formatted(supplierId, tankId, volumeHl)).andExpect(status().isCreated())), "$.id");
    }
}
