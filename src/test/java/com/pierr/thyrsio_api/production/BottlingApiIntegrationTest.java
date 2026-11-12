package com.pierr.thyrsio_api.production;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BottlingApiIntegrationTest extends ApiIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Paris"));

    // ==================== Planification ====================

    @Test
    void a_bottling_produces_a_finished_good() throws Exception {
        String notAFinishedGood = createArticle("ETIQUETTE");

        plan(notAFinishedGood, "AT_PRODUCER", null, TODAY, 1000, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("n'est pas un produit fini")));
    }

    @Test
    void a_bottling_on_site_needs_a_source_tank() throws Exception {
        plan(createArticle("PRODUIT_FINI"), "ON_SITE", null, TODAY, 1000, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("cuve")));
    }

    @Test
    void planned_bottling_gets_a_code_and_the_wine_it_needs() throws Exception {
        String json = body(plan(createArticle("PRODUIT_FINI"), "AT_PRODUCER", null, TODAY.plusDays(3), 6000, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("MB-\\d{2}-\\d{3}")))
                .andExpect(jsonPath("$.status").value("PLANNED")));
        assertThat(decimal(json, "$.wineNeededHl")).isEqualByComparingTo("45");  // 6 000 × 75 cl
    }

    @Test
    void shortages_are_computed_in_date_order() throws Exception {
        String label = createArticle("ETIQUETTE");
        move(label, null, "RECEPTION", "5000").andExpect(status().isCreated());
        String product = createArticle("PRODUIT_FINI");

        // Lundi : 4 000 sur 5 000, complet. Jeudi : 3 000, mais il n'en reste que 1 000 → 2 000 manquent.
        String first = id(plan(product, "AT_PRODUCER", null, TODAY.plusDays(1), 4000, materials(label, "4000")));
        String second = id(plan(product, "AT_PRODUCER", null, TODAY.plusDays(4), 3000, materials(label, "3000")));
        // Dans un mois : manquante aussi, mais trop loin pour une alerte
        String later = id(plan(product, "AT_PRODUCER", null, TODAY.plusDays(30), 1000, materials(label, "1000")));

        getAsUser("/api/production/bottlings/" + first)
                .andExpect(jsonPath("$.shortageCount").value(0))
                .andExpect(jsonPath("$.alert").value(false));

        String json = body(getAsUser("/api/production/bottlings/" + second)
                .andExpect(jsonPath("$.shortageCount").value(1))
                .andExpect(jsonPath("$.alert").value(true)));
        assertThat(decimal(json, "$.materials[0].reservedBefore")).isEqualByComparingTo("4000");
        assertThat(decimal(json, "$.materials[0].shortageQuantity")).isEqualByComparingTo("2000");

        getAsUser("/api/production/bottlings/" + later)
                .andExpect(jsonPath("$.shortageCount").value(1))
                .andExpect(jsonPath("$.alert").value(false));
    }

    @Test
    void updating_a_bottling_replaces_its_materials() throws Exception {
        String label = createArticle("ETIQUETTE");
        String carton = createArticle("CARTON");
        String product = createArticle("PRODUIT_FINI");
        String bottling = id(plan(product, "AT_PRODUCER", null, TODAY.plusDays(2), 600,
                materials(label, "600") + "," + materials(carton, "100")));

        // Même étiquette avec une autre quantité, le carton retiré
        putJson("/api/production/bottlings/" + bottling,
                request(product, "AT_PRODUCER", null, TODAY.plusDays(2), 1200, "[" + material(label, "1200") + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedBottles").value(1200))
                .andExpect(jsonPath("$.materials", hasSize(1)))
                .andExpect(jsonPath("$.materials[0].plannedQuantity").value(1200));
    }

    @Test
    void a_cancelled_bottling_can_no_longer_change() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String bottling = id(plan(product, "AT_PRODUCER", null, TODAY, 100, "[]"));

        postEmpty("/api/production/bottlings/" + bottling + "/cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        putJson("/api/production/bottlings/" + bottling, request(product, "AT_PRODUCER", null, TODAY, 200, "[]"))
                .andExpect(status().isConflict());
    }

    // ==================== Clôture ====================

    @Test
    void closing_consumes_materials_draws_wine_and_stocks_bottles() throws Exception {
        String cork = createArticle("BOUCHON");
        String corkLot = createLot(cork, "L-CLOSE");
        move(cork, corkLot, "RECEPTION", "1000").andExpect(status().isCreated());
        String label = createArticle("ETIQUETTE");
        move(label, null, "RECEPTION", "1000").andExpect(status().isCreated());
        String product = createArticle("PRODUIT_FINI");
        String tank = tankWithWine("10");

        String bottling = id(plan(product, "ON_SITE", tank, TODAY, 1200,
                materials(cork, "1200") + "," + materials(label, "1200")));

        String json = body(close(bottling, 880, 20, "6.6", consumption(cork, corkLot, "900"), consumption(label, null, "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.lotNumber", matchesPattern("L\\d{5}-\\d+")))
                .andExpect(jsonPath("$.consumptions", hasSize(2))));

        assertThat(stockOf(cork)).isEqualByComparingTo("100");
        assertThat(stockOf(label)).isEqualByComparingTo("100");
        assertThat(stockOf(product)).isEqualByComparingTo("880");
        assertThat(decimal(body(getAsUser("/api/cuverie/tanks/" + tank)), "$.volumeHl")).isEqualByComparingTo("3.4");

        // Le lot des bouteilles porte le numéro de la mise
        String lotNumber = JsonPath.read(json, "$.lotNumber");
        getAsUser("/api/stock/lots?articleId=" + product)
                .andExpect(jsonPath("$[0].lotNumber").value(lotNumber));

        // Une seconde clôture est refusée
        close(bottling, 880, 0, "1").andExpect(status().isConflict());
    }

    @Test
    void closing_with_insufficient_stock_changes_nothing() throws Exception {
        String label = createArticle("ETIQUETTE");
        move(label, null, "RECEPTION", "100").andExpect(status().isCreated());
        String product = createArticle("PRODUIT_FINI");
        String tank = tankWithWine("10");
        String bottling = id(plan(product, "ON_SITE", tank, TODAY, 500, materials(label, "500")));

        close(bottling, 500, 0, "3.75", consumption(label, null, "500"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Stock insuffisant")));

        // Tout ou rien : ni étiquettes consommées, ni vin tiré, ni bouteilles en stock
        assertThat(stockOf(label)).isEqualByComparingTo("100");
        assertThat(stockOf(product)).isEqualByComparingTo("0");
        assertThat(decimal(body(getAsUser("/api/cuverie/tanks/" + tank)), "$.volumeHl")).isEqualByComparingTo("10");
        getAsUser("/api/production/bottlings/" + bottling).andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void a_future_bottling_cannot_be_closed() throws Exception {
        String bottling = id(plan(createArticle("PRODUIT_FINI"), "AT_PRODUCER", null, TODAY.plusDays(5), 100, "[]"));

        close(bottling, 100, 0, null).andExpect(status().isConflict());
    }

    @Test
    void list_returns_the_bottlings_touching_the_period() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        // Du J+40 au J+42 : comprise dans une période qui commence le J+41
        postJson("/api/production/bottlings", request(product, "AT_PRODUCER", null, TODAY.plusDays(40),
                TODAY.plusDays(42), 100, "[]")).andExpect(status().isCreated());

        String json = body(getAsUser("/api/production/bottlings?from=" + TODAY.plusDays(41) + "&to=" + TODAY.plusDays(45)));
        List<String> products = JsonPath.read(json, "$.content[*].finishedArticleId");
        assertThat(products).contains(product);
    }

    // ==================== Le stock des produits finis ====================

    @Test
    void stock_overview_separates_finished_goods_from_dry_goods() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String label = createArticle("ETIQUETTE");

        List<String> dryGoods = JsonPath.read(body(getAsUser("/api/stock/overview")), "$[*].articleId");
        assertThat(dryGoods).contains(label).doesNotContain(product);

        List<String> finished = JsonPath.read(body(getAsUser("/api/stock/overview?finishedGoods=true")), "$[*].articleId");
        assertThat(finished).contains(product).doesNotContain(label);
    }

    // ==================== Méthodes utilitaires ====================

    private ResultActions plan(String product, String location, String tankId, LocalDate start, int bottles,
                               String materials) throws Exception {
        String list = materials.startsWith("[") ? materials : "[" + materials + "]";
        return postJson("/api/production/bottlings", request(product, location, tankId, start, bottles, list));
    }

    private static String request(String product, String location, String tankId, LocalDate start, int bottles,
                                  String materials) {
        return request(product, location, tankId, start, start, bottles, materials);
    }

    private static String request(String product, String location, String tankId, LocalDate start, LocalDate end,
                                  int bottles, String materials) {
        String tank = tankId == null ? "null" : "\"" + tankId + "\"";
        return """
                {"location": "%s", "contractor": "Embouteillage Test", "startDate": "%s", "endDate": "%s",
                 "finishedArticleId": "%s", "plannedBottles": %d, "bottleVolumeCl": 75,
                 "sourceTankId": %s, "materials": %s}
                """.formatted(location, start, end, product, bottles, tank, materials);
    }

    private static String material(String articleId, String quantity) {
        return """
                {"articleId": "%s", "plannedQuantity": %s}""".formatted(articleId, quantity);
    }

    /** Une ou plusieurs matières, séparées par des virgules par l'appelant. */
    private static String materials(String articleId, String quantity) {
        return material(articleId, quantity);
    }

    private static String consumption(String articleId, String lotId, String quantity) {
        String lot = lotId == null ? "null" : "\"" + lotId + "\"";
        return """
                {"articleId": "%s", "lotId": %s, "quantity": %s}""".formatted(articleId, lot, quantity);
    }

    private ResultActions close(String bottlingId, int produced, int broken, String wineHl, String... consumptions)
            throws Exception {
        return postJson("/api/production/bottlings/" + bottlingId + "/close", """
                {"producedBottles": %d, "brokenBottles": %d, "wineUsedHl": %s, "consumptions": [%s]}
                """.formatted(produced, broken, wineHl == null ? "null" : wineHl, String.join(",", consumptions)));
    }

    private String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result.andExpect(status().isCreated())), "$.id");
    }

    /** Une cuve qui contient le volume demandé d'un vin tout juste reçu. */
    private String tankWithWine(String volumeHl) throws Exception {
        String supplier = JsonPath.read(body(postJson("/api/referentiel/suppliers", """
                {"name": "%s", "kind": "WINE"}
                """.formatted(unique("Château"))).andExpect(status().isCreated())), "$.id");
        String tank = JsonPath.read(body(postJson("/api/cuverie/tanks", """
                {"code": "%s", "capacityHl": 100}
                """.formatted(unique("C"))).andExpect(status().isCreated())), "$.id");
        postJson("/api/cuverie/receptions", """
                {"supplierId": "%s", "appellation": "Bordeaux", "color": "RED",
                 "fills": [{"tankId": "%s", "volumeHl": %s}]}
                """.formatted(supplier, tank, volumeHl)).andExpect(status().isCreated());
        return tank;
    }
}
