package com.pierr.thyrsio_api.production;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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
    void a_bottling_has_at_least_one_production() throws Exception {
        postJson("/api/production/bottlings", """
                {"location": "AT_PRODUCER", "productions": []}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("au moins une production")));
    }

    @Test
    void planned_bottling_gets_a_code_and_the_wine_it_needs() throws Exception {
        String json = body(plan(createArticle("PRODUIT_FINI"), "AT_PRODUCER", null, TODAY.plusDays(3), 6000, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("MB-\\d{2}-\\d{3}")))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.plannedBottles").value(6000)));
        assertThat(decimal(json, "$.productions[0].wineNeededHl")).isEqualByComparingTo("45");  // 6 000 × 75 cl
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
        assertThat(decimal(json, "$.productions[0].materials[0].reservedBefore")).isEqualByComparingTo("4000");
        assertThat(decimal(json, "$.productions[0].materials[0].shortageQuantity")).isEqualByComparingTo("2000");

        getAsUser("/api/production/bottlings/" + later)
                .andExpect(jsonPath("$.shortageCount").value(1))
                .andExpect(jsonPath("$.alert").value(false));
    }

    @Test
    void the_morning_production_reserves_before_the_afternoon_one() throws Exception {
        String label = createArticle("ETIQUETTE");
        move(label, null, "RECEPTION", "1000").andExpect(status().isCreated());
        String morningProduct = createArticle("PRODUIT_FINI");
        String afternoonProduct = createArticle("PRODUIT_FINI");
        String appellation = createAppellation();

        // L'après-midi est saisie en premier : l'ordre de la journée l'emporte quand même
        String json = body(postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                production(null, afternoonProduct, null, TODAY.plusDays(2), "AFTERNOON", 800, appellation, 2023,
                        materials(label, "800")),
                production(null, morningProduct, null, TODAY.plusDays(2), "MORNING", 600, appellation, 2023,
                        materials(label, "600"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(TODAY.plusDays(2).toString()))
                .andExpect(jsonPath("$.endDate").value(TODAY.plusDays(2).toString()))
                .andExpect(jsonPath("$.plannedBottles").value(1400))
                .andExpect(jsonPath("$.productions", hasSize(2)))
                .andExpect(jsonPath("$.productions[0].slot").value("MORNING"))
                .andExpect(jsonPath("$.productions[0].finishedArticleId").value(morningProduct))
                .andExpect(jsonPath("$.productions[1].slot").value("AFTERNOON")));
        // Le matin prend 600 des 1 000 étiquettes ; il en manque 400 l'après-midi
        assertThat(decimal(json, "$.productions[0].materials[0].shortageQuantity")).isEqualByComparingTo("0");
        assertThat(decimal(json, "$.productions[1].materials[0].shortageQuantity")).isEqualByComparingTo("400");
    }

    @Test
    void updating_a_bottling_replaces_its_materials() throws Exception {
        String label = createArticle("ETIQUETTE");
        String carton = createArticle("CARTON");
        String product = createArticle("PRODUIT_FINI");
        String appellation = createAppellation();
        String json = body(postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                production(null, product, null, TODAY.plusDays(2), "FULL_DAY", 600, appellation, 2024,
                        materials(label, "600") + "," + materials(carton, "100"))))
                .andExpect(status().isCreated()));
        String bottling = JsonPath.read(json, "$.id");
        String production = JsonPath.read(json, "$.productions[0].id");
        String lotNumber = JsonPath.read(json, "$.productions[0].lotNumber");

        // Même étiquette avec une autre quantité, le carton retiré ; la production garde son numéro de lot
        putJson("/api/production/bottlings/" + bottling, bottling("AT_PRODUCER",
                production(production, product, null, TODAY.plusDays(2), "FULL_DAY", 1200, appellation, 2024,
                        materials(label, "1200"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedBottles").value(1200))
                .andExpect(jsonPath("$.productions[0].id").value(production))
                .andExpect(jsonPath("$.productions[0].lotNumber").value(lotNumber))
                .andExpect(jsonPath("$.productions[0].materials", hasSize(1)))
                .andExpect(jsonPath("$.productions[0].materials[0].plannedQuantity").value(1200));
    }

    @Test
    void a_cancelled_bottling_can_no_longer_change() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String bottling = id(plan(product, "AT_PRODUCER", null, TODAY, 100, "[]"));

        postEmpty("/api/production/bottlings/" + bottling + "/cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.productions[0].status").value("CANCELLED"));
        putJson("/api/production/bottlings/" + bottling, bottling("AT_PRODUCER",
                production(null, product, null, TODAY, "FULL_DAY", 200, createAppellation(), 2024, "")))
                .andExpect(status().isConflict());
    }

    // ==================== Numéros de lot ====================

    @Test
    void each_production_gets_its_lot_number_when_planned() throws Exception {
        String appellation = createAppellation();
        List<String> codes = JsonPath.read(body(getAsUser("/api/referentiel/appellations")),
                "$[?(@.id == '" + appellation + "')].code");
        String code = codes.getFirst();
        String product = createArticle("PRODUIT_FINI");

        // L + sigle + millésime + numéro d'ordre, avant même le début de la mise
        String json = body(postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                production(null, product, null, TODAY.plusDays(10), "MORNING", 600, appellation, 2024, ""),
                production(null, product, null, TODAY.plusDays(10), "AFTERNOON", 600, appellation, 2024, ""),
                production(null, product, null, TODAY.plusDays(11), "FULL_DAY", 600, appellation, null, "")))
                .andExpect(status().isCreated()));
        List<String> lots = JsonPath.read(json, "$.productions[*].lotNumber");
        assertThat(lots).containsExactly("L" + code + "2401", "L" + code + "2402", "L" + code + "NM01");

        // Une autre mise, le même vin : la suite du compteur
        String next = body(postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                production(null, product, null, TODAY.plusDays(20), "FULL_DAY", 600, appellation, 2024, "")))
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(next, "$.productions[0].lotNumber")).isEqualTo("L" + code + "2403");
    }

    @Test
    void a_lot_number_is_never_given_twice() throws Exception {
        String appellation = createAppellation();
        String product = createArticle("PRODUIT_FINI");
        String json = body(postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                production(null, product, null, TODAY.plusDays(10), "FULL_DAY", 600, appellation, 2023, ""),
                production(null, product, null, TODAY.plusDays(11), "FULL_DAY", 600, appellation, 2023, "")))
                .andExpect(status().isCreated()));
        String bottling = JsonPath.read(json, "$.id");
        String first = JsonPath.read(json, "$.productions[0].id");
        String firstLot = JsonPath.read(json, "$.productions[0].lotNumber");
        String secondLot = JsonPath.read(json, "$.productions[1].lotNumber");

        // La seconde production est retirée, puis une autre ajoutée : elle ne reprend pas le numéro libéré
        String updated = body(putJson("/api/production/bottlings/" + bottling, bottling("AT_PRODUCER",
                production(first, product, null, TODAY.plusDays(10), "FULL_DAY", 600, appellation, 2023, ""),
                production(null, product, null, TODAY.plusDays(12), "FULL_DAY", 600, appellation, 2023, "")))
                .andExpect(status().isOk()));
        List<String> lots = JsonPath.read(updated, "$.productions[*].lotNumber");
        assertThat(lots).hasSize(2).contains(firstLot).doesNotContain(secondLot);

        // Changer le millésime donne un nouveau numéro ; l'ancien reste perdu
        String changed = body(putJson("/api/production/bottlings/" + bottling, bottling("AT_PRODUCER",
                production(first, product, null, TODAY.plusDays(10), "FULL_DAY", 600, appellation, 2022, "")))
                .andExpect(status().isOk()));
        String newLot = JsonPath.read(changed, "$.productions[0].lotNumber");
        assertThat(newLot).endsWith("2201").isNotEqualTo(firstLot);
    }

    @Test
    void appellation_codes_are_letters_only() throws Exception {
        postJson("/api/referentiel/appellations", """
                {"code": "BX2", "name": "%s"}
                """.formatted(unique("Appellation"))).andExpect(status().isBadRequest());
        // Bordeaux / BX vient de la migration
        postJson("/api/referentiel/appellations", """
                {"code": "bx", "name": "%s"}
                """.formatted(unique("Appellation"))).andExpect(status().isConflict());
    }

    // ==================== Clôture ====================

    @Test
    void closing_consumes_materials_draws_wine_and_stocks_bottles() throws Exception {
        String cork = createArticle("BOUCHON");
        move(cork, null, "RECEPTION", "1000").andExpect(status().isCreated());
        String label = createArticle("ETIQUETTE");
        move(label, null, "RECEPTION", "1000").andExpect(status().isCreated());
        String product = createArticle("PRODUIT_FINI");
        String tank = tankWithWine("10");

        String planned = body(plan(product, "ON_SITE", tank, TODAY, 1200,
                materials(cork, "1200") + "," + materials(label, "1200")).andExpect(status().isCreated()));
        String bottling = JsonPath.read(planned, "$.id");
        String lotNumber = JsonPath.read(planned, "$.productions[0].lotNumber");

        close(planned, 0, 880, 20, "6.6", consumption(cork, "900"), consumption(label, "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.producedBottles").value(880))
                .andExpect(jsonPath("$.productions[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.productions[0].lotNumber").value(lotNumber))
                .andExpect(jsonPath("$.productions[0].consumptions", hasSize(2)));

        assertThat(stockOf(cork)).isEqualByComparingTo("100");
        assertThat(stockOf(label)).isEqualByComparingTo("100");
        assertThat(stockOf(product)).isEqualByComparingTo("880");
        assertThat(decimal(body(getAsUser("/api/cuverie/tanks/" + tank)), "$.volumeHl")).isEqualByComparingTo("3.4");

        // Les bouteilles entrent en stock sous le numéro attribué à la planification
        getAsUser("/api/stock/lots?articleId=" + product)
                .andExpect(jsonPath("$[0].lotNumber").value(lotNumber));

        // Une seconde clôture est refusée
        close(planned, 0, 880, 0, "1").andExpect(status().isConflict());
        getAsUser("/api/production/bottlings/" + bottling).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void the_morning_production_closes_before_the_afternoon_one() throws Exception {
        String morningProduct = createArticle("PRODUIT_FINI");
        String afternoonProduct = createArticle("PRODUIT_FINI");
        String appellation = createAppellation();
        String planned = body(postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                production(null, morningProduct, null, TODAY, "MORNING", 600, appellation, 2023, ""),
                production(null, afternoonProduct, null, TODAY, "AFTERNOON", 900, appellation, 2023, "")))
                .andExpect(status().isCreated()));

        // Le matin est fait : la mise est en cours
        String inProgress = body(close(planned, 0, 590, 10, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.productions[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.productions[1].status").value("PLANNED")));
        assertThat(stockOf(morningProduct)).isEqualByComparingTo("590");

        // L'après-midi aussi : la mise est clôturée
        close(inProgress, 1, 900, 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.producedBottles").value(1490));
        assertThat(stockOf(afternoonProduct)).isEqualByComparingTo("900");
    }

    @Test
    void cancelling_a_bottling_keeps_what_was_already_closed() throws Exception {
        String appellation = createAppellation();
        String done = createArticle("PRODUIT_FINI");
        String planned = body(postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                production(null, done, null, TODAY, "MORNING", 100, appellation, 2023, ""),
                production(null, createArticle("PRODUIT_FINI"), null, TODAY.plusDays(1), "FULL_DAY", 100,
                        appellation, 2023, "")))
                .andExpect(status().isCreated()));
        String bottling = JsonPath.read(planned, "$.id");
        close(planned, 0, 100, 0, null).andExpect(status().isOk());

        postEmpty("/api/production/bottlings/" + bottling + "/cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.productions[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.productions[1].status").value("CANCELLED"));
        assertThat(stockOf(done)).isEqualByComparingTo("100");
    }

    @Test
    void a_material_is_consumed_once_without_any_lot() throws Exception {
        String label = createArticle("ETIQUETTE");
        move(label, null, "RECEPTION", "1000").andExpect(status().isCreated());
        String product = createArticle("PRODUIT_FINI");
        String tank = tankWithWine("10");
        String planned = body(plan(product, "ON_SITE", tank, TODAY, 500, materials(label, "500"))
                .andExpect(status().isCreated()));

        // Deux lignes pour la même étiquette : refusé (il n'y a plus de lots à distinguer)
        close(planned, 0, 500, 0, "3.75", consumption(label, "300"), consumption(label, "200"))
                .andExpect(status().isBadRequest());

        close(planned, 0, 500, 0, "3.75", consumption(label, "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productions[0].consumptions[0].quantity").value(500))
                .andExpect(jsonPath("$.productions[0].consumptions[0].lotNumber").doesNotExist())
                .andExpect(jsonPath("$.productions[0].materials[0].lotTracked").doesNotExist());
        assertThat(stockOf(label)).isEqualByComparingTo("500");
    }

    @Test
    void closing_with_insufficient_stock_changes_nothing() throws Exception {
        String label = createArticle("ETIQUETTE");
        move(label, null, "RECEPTION", "100").andExpect(status().isCreated());
        String product = createArticle("PRODUIT_FINI");
        String tank = tankWithWine("10");
        String planned = body(plan(product, "ON_SITE", tank, TODAY, 500, materials(label, "500"))
                .andExpect(status().isCreated()));
        String bottling = JsonPath.read(planned, "$.id");

        close(planned, 0, 500, 0, "3.75", consumption(label, "500"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Stock insuffisant")));

        // Tout ou rien : ni étiquettes consommées, ni vin tiré, ni bouteilles en stock
        assertThat(stockOf(label)).isEqualByComparingTo("100");
        assertThat(stockOf(product)).isEqualByComparingTo("0");
        assertThat(decimal(body(getAsUser("/api/cuverie/tanks/" + tank)), "$.volumeHl")).isEqualByComparingTo("10");
        getAsUser("/api/production/bottlings/" + bottling).andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void a_future_production_cannot_be_closed() throws Exception {
        String planned = body(plan(createArticle("PRODUIT_FINI"), "AT_PRODUCER", null, TODAY.plusDays(5), 100, "[]")
                .andExpect(status().isCreated()));

        close(planned, 0, 100, 0, null).andExpect(status().isConflict());
    }

    @Test
    void list_returns_the_bottlings_touching_the_period() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        // Du J+40 au J+42 : comprise dans une période qui commence le J+41
        postJson("/api/production/bottlings", bottling("AT_PRODUCER",
                productionOver(product, TODAY.plusDays(40), TODAY.plusDays(42), 100, createAppellation())))
                .andExpect(status().isCreated());

        String json = body(getAsUser("/api/production/bottlings?from=" + TODAY.plusDays(41) + "&to=" + TODAY.plusDays(45)));
        List<String> products = JsonPath.read(json, "$.content[*].productions[*].finishedArticleId");
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

    /** Une mise d'une seule production, sur une journée. */
    private ResultActions plan(String product, String location, String tankId, LocalDate start, int bottles,
                               String materials) throws Exception {
        String list = materials.startsWith("[") ? materials.substring(1, materials.length() - 1) : materials;
        return postJson("/api/production/bottlings", bottling(location,
                production(null, product, tankId, start, "FULL_DAY", bottles, createAppellation(), 2024, list)));
    }

    private static String bottling(String location, String... productions) {
        return """
                {"location": "%s", "contractor": "Embouteillage Test", "productions": [%s]}
                """.formatted(location, String.join(",", productions));
    }

    private static String production(String id, String product, String tankId, LocalDate day, String slot,
                                     int bottles, String appellationId, Integer vintage, String materials) {
        return """
                {"id": %s, "startDate": "%s", "slot": "%s", "finishedArticleId": "%s", "plannedBottles": %d,
                 "bottleVolumeCl": 75, "sourceTankId": %s, "appellationId": "%s", "vintage": %s, "materials": [%s]}
                """.formatted(quoted(id), day, slot, product, bottles, quoted(tankId), appellationId, vintage, materials);
    }

    private static String productionOver(String product, LocalDate start, LocalDate end, int bottles,
                                         String appellationId) {
        return """
                {"startDate": "%s", "endDate": "%s", "finishedArticleId": "%s", "plannedBottles": %d,
                 "bottleVolumeCl": 75, "appellationId": "%s", "vintage": 2024, "materials": []}
                """.formatted(start, end, product, bottles, appellationId);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** Une matière, ou plusieurs séparées par des virgules par l'appelant. */
    private static String materials(String articleId, String quantity) {
        return """
                {"articleId": "%s", "plannedQuantity": %s}""".formatted(articleId, quantity);
    }

    private static String consumption(String articleId, String quantity) {
        return """
                {"articleId": "%s", "quantity": %s}""".formatted(articleId, quantity);
    }

    /** Clôture la production n° index (dans l'ordre de la journée) de la mise décrite par ce JSON. */
    private ResultActions close(String bottlingJson, int index, int produced, int broken, String wineHl,
                                String... consumptions) throws Exception {
        String bottling = JsonPath.read(bottlingJson, "$.id");
        String production = JsonPath.read(bottlingJson, "$.productions[" + index + "].id");
        return postJson("/api/production/bottlings/" + bottling + "/productions/" + production + "/close", """
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
