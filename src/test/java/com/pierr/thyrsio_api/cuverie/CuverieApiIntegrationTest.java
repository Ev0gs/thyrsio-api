package com.pierr.thyrsio_api.cuverie;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CuverieApiIntegrationTest extends ApiIntegrationTest {

    // ==================== Cuves ====================

    @Test
    void cuverie_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/cuverie/tanks")).andExpect(status().isUnauthorized());
    }

    @Test
    void tank_is_created_empty_with_an_uppercase_code() throws Exception {
        String code = unique("cuve").toLowerCase();
        postJson("/api/cuverie/tanks", """
                {"code": "%s", "label": "Inox chai nord", "capacityHl": 120.5}
                """.formatted(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))
                .andExpect(jsonPath("$.state").value("EMPTY"))
                .andExpect(jsonPath("$.status").value("IN_SERVICE"))
                .andExpect(jsonPath("$.wineLot").value(nullValue()));

        postJson("/api/cuverie/tanks", """
                {"code": "%s", "capacityHl": 50}
                """.formatted(code.toUpperCase()))
                .andExpect(status().isConflict());
    }

    @Test
    void capacity_with_more_than_two_decimals_returns_400() throws Exception {
        postJson("/api/cuverie/tanks", """
                {"code": "%s", "capacityHl": 10.123}
                """.formatted(unique("C")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacityHl").exists());
    }

    // ==================== Réception ====================

    @Test
    void reception_creates_a_lot_and_fills_several_tanks() throws Exception {
        String supplier = wineSupplier();
        String tankA = createTank("100");
        String tankB = createTank("80");

        String json = body(receive(supplier, "Bordeaux Supérieur", fill(tankA, "100"), fill(tankB, "42.5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("V\\d{2}-\\d{3}")))
                .andExpect(jsonPath("$.tanks", hasSize(2))));
        assertThat(decimal(json, "$.volumeHl")).isEqualByComparingTo("142.5");

        String lotId = JsonPath.read(json, "$.id");
        getAsUser("/api/cuverie/tanks/" + tankA)
                .andExpect(jsonPath("$.state").value("IN_USE"))
                .andExpect(jsonPath("$.wineLot.id").value(lotId));
        assertThat(volumeOf(tankB)).isEqualByComparingTo("42.5");

        getAsUser("/api/cuverie/movements?wineLotId=" + lotId)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].type", everyItem(is("RECEPTION"))));
    }

    @Test
    void reception_beyond_capacity_is_refused_and_nothing_is_saved() throws Exception {
        String supplier = wineSupplier();
        String tankA = createTank("100");
        String tankB = createTank("50");

        receive(supplier, "Côtes de Bourg", fill(tankA, "60"), fill(tankB, "60"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Capacité dépassée")));

        // Tout ou rien : la cuve A, pourtant assez grande, n'a pas été remplie
        assertThat(volumeOf(tankA)).isEqualByComparingTo("0");
        getAsUser("/api/cuverie/wine-lots?supplierId=" + supplier)
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void a_tank_holds_only_one_wine_at_a_time() throws Exception {
        String supplier = wineSupplier();
        String tank = createTank("100");
        receive(supplier, "Bordeaux", fill(tank, "30")).andExpect(status().isCreated());

        receive(supplier, "Graves", fill(tank, "30"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("un seul vin")));
    }

    @Test
    void reception_requires_a_wine_supplier() throws Exception {
        String dryGoods = JsonPath.read(body(postJson("/api/referentiel/suppliers", """
                {"name": "%s"}
                """.formatted(unique("Verrerie"))).andExpect(status().isCreated())), "$.id");

        receive(dryGoods, "Bordeaux", fill(createTank("100"), "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("pas un fournisseur de vin")));
    }

    @Test
    void lot_codes_follow_each_other_within_a_year() throws Exception {
        String supplier = wineSupplier();
        String first = JsonPath.read(body(receive(supplier, "Bordeaux", fill(createTank("10"), "5"))), "$.code");
        String second = JsonPath.read(body(receive(supplier, "Bordeaux", fill(createTank("10"), "5"))), "$.code");

        int firstNumber = Integer.parseInt(first.substring(4));
        assertThat(second).isEqualTo(first.substring(0, 4) + "%03d".formatted(firstNumber + 1));
    }

    // ==================== Transferts ====================

    @Test
    void transfer_with_loss_creates_three_linked_movements() throws Exception {
        String supplier = wineSupplier();
        String source = createTank("100");
        String destination = createTank("100");
        receive(supplier, "Bordeaux", fill(source, "50")).andExpect(status().isCreated());

        String json = body(postJson("/api/cuverie/transfers", """
                {"sourceTankId": "%s", "destinationTankId": "%s", "volumeHl": 30, "lossHl": 0.5}
                """.formatted(source, destination))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3))));

        List<String> types = JsonPath.read(json, "$[*].type");
        assertThat(types).containsExactly("TRANSFER_OUT", "LOSS", "TRANSFER_IN");
        List<String> transferIds = JsonPath.read(json, "$[*].transferId");
        assertThat(transferIds).doesNotContainNull().containsOnly(transferIds.getFirst());

        assertThat(volumeOf(source)).isEqualByComparingTo("19.5");
        assertThat(volumeOf(destination)).isEqualByComparingTo("30");
    }

    @Test
    void transfer_that_empties_the_source_frees_it() throws Exception {
        String supplier = wineSupplier();
        String source = createTank("100");
        String destination = createTank("100");
        String lotId = JsonPath.read(body(receive(supplier, "Bordeaux", fill(source, "40"))), "$.id");

        postJson("/api/cuverie/transfers", """
                {"sourceTankId": "%s", "destinationTankId": "%s", "emptySource": true, "lossHl": 1}
                """.formatted(source, destination))
                .andExpect(status().isCreated());

        getAsUser("/api/cuverie/tanks/" + source)
                .andExpect(jsonPath("$.state").value("EMPTY"))
                .andExpect(jsonPath("$.wineLot").value(nullValue()));
        getAsUser("/api/cuverie/tanks/" + destination).andExpect(jsonPath("$.wineLot.id").value(lotId));
        assertThat(volumeOf(destination)).isEqualByComparingTo("39");
    }

    @Test
    void transfer_into_a_tank_holding_another_wine_is_refused() throws Exception {
        String supplier = wineSupplier();
        String source = createTank("100");
        String destination = createTank("100");
        receive(supplier, "Bordeaux", fill(source, "40")).andExpect(status().isCreated());
        receive(supplier, "Graves", fill(destination, "10")).andExpect(status().isCreated());

        postJson("/api/cuverie/transfers", """
                {"sourceTankId": "%s", "destinationTankId": "%s", "volumeHl": 5}
                """.formatted(source, destination))
                .andExpect(status().isConflict());

        // Rien n'a bougé dans la cuve d'origine
        assertThat(volumeOf(source)).isEqualByComparingTo("40");
    }

    @Test
    void transfer_plus_loss_beyond_source_volume_is_refused() throws Exception {
        String supplier = wineSupplier();
        String source = createTank("100");
        String destination = createTank("100");
        receive(supplier, "Bordeaux", fill(source, "40")).andExpect(status().isCreated());

        postJson("/api/cuverie/transfers", """
                {"sourceTankId": "%s", "destinationTankId": "%s", "volumeHl": 40, "lossHl": 0.5}
                """.formatted(source, destination))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Volume insuffisant")));
    }

    // ==================== Pertes et jaugeages ====================

    @Test
    void loss_lowers_the_volume_and_cannot_exceed_it() throws Exception {
        String tank = createTank("100");
        receive(wineSupplier(), "Bordeaux", fill(tank, "20")).andExpect(status().isCreated());

        postJson("/api/cuverie/tanks/" + tank + "/losses", """
                {"volumeHl": 0.75, "notes": "Évaporation"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LOSS"))
                .andExpect(jsonPath("$.volumeHl").value(-0.75));
        assertThat(volumeOf(tank)).isEqualByComparingTo("19.25");

        postJson("/api/cuverie/tanks/" + tank + "/losses", """
                {"volumeHl": 50}
                """).andExpect(status().isConflict());
    }

    @Test
    void measurement_records_the_difference_as_an_adjustment() throws Exception {
        String tank = createTank("100");
        receive(wineSupplier(), "Bordeaux", fill(tank, "20")).andExpect(status().isCreated());

        postJson("/api/cuverie/tanks/" + tank + "/measurements", """
                {"measuredHl": 19.6}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.volumeHl").value(-0.4));
        assertThat(volumeOf(tank)).isEqualByComparingTo("19.6");

        // Même volume : rien à ajuster
        postJson("/api/cuverie/tanks/" + tank + "/measurements", """
                {"measuredHl": 19.6}
                """).andExpect(status().isConflict());
    }

    @Test
    void measuring_zero_empties_and_frees_the_tank() throws Exception {
        String tank = createTank("100");
        receive(wineSupplier(), "Bordeaux", fill(tank, "0.3")).andExpect(status().isCreated());

        postJson("/api/cuverie/tanks/" + tank + "/measurements", """
                {"measuredHl": 0}
                """).andExpect(status().isCreated());

        getAsUser("/api/cuverie/tanks/" + tank).andExpect(jsonPath("$.state").value("EMPTY"));
    }

    // ==================== Statuts et contenance ====================

    @Test
    void a_tank_with_wine_cannot_go_to_cleaning() throws Exception {
        String tank = createTank("100");
        receive(wineSupplier(), "Bordeaux", fill(tank, "10")).andExpect(status().isCreated());

        putJson("/api/cuverie/tanks/" + tank + "/status", """
                {"status": "CLEANING"}
                """).andExpect(status().isConflict());
        postEmpty("/api/cuverie/tanks/" + tank + "/archive").andExpect(status().isConflict());
    }

    @Test
    void a_tank_in_cleaning_cannot_receive_wine() throws Exception {
        String tank = createTank("100");
        putJson("/api/cuverie/tanks/" + tank + "/status", """
                {"status": "CLEANING"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLEANING"));

        receive(wineSupplier(), "Bordeaux", fill(tank, "10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("en nettoyage")));
    }

    @Test
    void capacity_cannot_drop_below_the_current_volume() throws Exception {
        String tank = createTank("100");
        receive(wineSupplier(), "Bordeaux", fill(tank, "60")).andExpect(status().isCreated());

        putJson("/api/cuverie/tanks/" + tank, """
                {"capacityHl": 50}
                """).andExpect(status().isConflict());
        putJson("/api/cuverie/tanks/" + tank, """
                {"label": "Agrandie ?", "capacityHl": 60}
                """).andExpect(status().isOk());
    }

    // ==================== Lots et analyses ====================

    @Test
    void wine_lot_list_shows_the_volume_left_in_tanks() throws Exception {
        String supplier = wineSupplier();
        String tankA = createTank("100");
        String tankB = createTank("100");
        receive(supplier, "Bordeaux", fill(tankA, "30"), fill(tankB, "25")).andExpect(status().isCreated());

        String json = body(getAsUser("/api/cuverie/wine-lots?inTank=true&supplierId=" + supplier)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].tankCount").value(2)));
        assertThat(decimal(json, "$.content[0].volumeHl")).isEqualByComparingTo("55");
    }

    @Test
    void analyses_can_be_added_listed_and_deleted() throws Exception {
        String tank = createTank("100");
        String lot = JsonPath.read(body(receive(wineSupplier(), "Bordeaux", fill(tank, "30"))), "$.id");

        String analysis = JsonPath.read(body(postJson("/api/cuverie/wine-lots/" + lot + "/analyses", """
                {"tankId": "%s", "analyzedOn": "2026-11-02", "alcohol": 13.5, "freeSo2": 28, "totalSo2": 85,
                 "notes": "Légèrement trouble : filtration conseillée"}
                """.formatted(tank)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tankCode").exists())), "$.id");

        String analyses = body(getAsUser("/api/cuverie/wine-lots/" + lot + "/analyses")
                .andExpect(jsonPath("$", hasSize(1))));
        assertThat(decimal(analyses, "$[0].freeSo2")).isEqualByComparingTo("28");

        mockMvc.perform(delete("/api/cuverie/analyses/" + analysis).with(user()))
                .andExpect(status().isNoContent());
        getAsUser("/api/cuverie/wine-lots/" + lot + "/analyses").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void an_empty_analysis_is_refused() throws Exception {
        String lot = JsonPath.read(body(receive(wineSupplier(), "Bordeaux", fill(createTank("10"), "5"))), "$.id");

        postJson("/api/cuverie/wine-lots/" + lot + "/analyses", """
                {"analyzedOn": "2026-11-02"}
                """).andExpect(status().isBadRequest());
    }

    // ==================== Méthodes utilitaires ====================

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

    private static String fill(String tankId, String volumeHl) {
        return """
                {"tankId": "%s", "volumeHl": %s}""".formatted(tankId, volumeHl);
    }

    private ResultActions receive(String supplierId, String appellation, String... fills) throws Exception {
        return postJson("/api/cuverie/receptions", """
                {"supplierId": "%s", "appellation": "%s", "color": "RED", "vintage": 2023,
                 "daeNumber": "26FRA0000000001", "fills": [%s]}
                """.formatted(supplierId, appellation, String.join(", ", fills)));
    }

    private java.math.BigDecimal volumeOf(String tankId) throws Exception {
        return decimal(body(getAsUser("/api/cuverie/tanks/" + tankId).andExpect(status().isOk())), "$.volumeHl");
    }
}
