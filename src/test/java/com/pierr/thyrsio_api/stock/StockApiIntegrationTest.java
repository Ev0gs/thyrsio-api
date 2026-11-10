package com.pierr.thyrsio_api.stock;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockApiIntegrationTest extends ApiIntegrationTest {

    // ==================== Mouvements et soldes ====================

    @Test
    void stock_requires_authentication() throws Exception {
        mockMvc.perform(get("/api/stock/balances"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reception_then_consumption_updates_balance() throws Exception {
        String article = createArticle("BOUCHON");
        String lot = createLot(article, "L-1");

        move(article, lot, "RECEPTION", "10000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotNumber").value("L-1"));
        move(article, lot, "CONSUMPTION", "3000")
                .andExpect(status().isCreated());

        assertThat(stockOf(article)).isEqualByComparingTo("7000");
    }

    @Test
    void consumption_beyond_stock_is_refused_and_stock_is_unchanged() throws Exception {
        String article = createArticle("BOUCHON");
        String lot = createLot(article, "L-1");
        move(article, lot, "RECEPTION", "100").andExpect(status().isCreated());

        move(article, lot, "CONSUMPTION", "150")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Stock insuffisant")));

        // La transaction a été annulée : ni mouvement, ni solde modifié
        assertThat(stockOf(article)).isEqualByComparingTo("100");
    }

    @Test
    void adjustment_accepts_a_signed_quantity() throws Exception {
        String article = createArticle("ETIQUETTE");
        move(article, null, "RECEPTION", "10").andExpect(status().isCreated());

        move(article, null, "ADJUSTMENT", "-3").andExpect(status().isCreated());

        assertThat(stockOf(article)).isEqualByComparingTo("7");
    }

    @Test
    void too_many_decimals_for_the_unit_returns_400() throws Exception {
        String article = createArticle("ETIQUETTE");  // unité U : 0 décimale

        move(article, null, "RECEPTION", "2.5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("décimales")));
    }

    @Test
    void out_movement_with_negative_quantity_returns_400() throws Exception {
        String article = createArticle("ETIQUETTE");

        // Le sens vient du type : une consommation se saisit en positif
        move(article, null, "CONSUMPTION", "-5").andExpect(status().isBadRequest());
    }

    @Test
    void automatic_movement_types_cannot_be_entered_manually() throws Exception {
        String article = createArticle("ETIQUETTE");

        move(article, null, "SHIPMENT", "5").andExpect(status().isBadRequest());
    }

    // ==================== Règles sur les lots ====================

    @Test
    void lot_is_required_for_a_lot_tracked_article() throws Exception {
        String article = createArticle("BOUCHON");

        move(article, null, "RECEPTION", "10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("lot est obligatoire")));
    }

    @Test
    void lot_is_forbidden_for_an_untracked_article() throws Exception {
        String article = createArticle("ETIQUETTE");

        move(article, UUID.randomUUID().toString(), "RECEPTION", "10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("n'est pas suivi par lot")));
    }

    @Test
    void lot_must_belong_to_the_article() throws Exception {
        String articleA = createArticle("BOUCHON");
        String articleB = createArticle("BOUCHON");
        String lotOfA = createLot(articleA, "L-A");

        move(articleB, lotOfA, "RECEPTION", "10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("n'appartient pas")));
    }

    // ==================== Verrou de catégorie (référentiel ↔ stock) ====================

    @Test
    void article_with_stock_cannot_switch_lot_tracking() throws Exception {
        String article = createArticle("BOUCHON");
        move(article, createLot(article, "L-1"), "RECEPTION", "10").andExpect(status().isCreated());

        // BOUCHON (suivi par lot) -> ETIQUETTE (non suivi) : refusé
        putJson("/api/referentiel/articles/" + article, """
                {"label": "Bouchon", "categoryCode": "ETIQUETTE"}
                """).andExpect(status().isConflict());

        // BOUCHON -> CAPSULE (suivie par lot elle aussi) : permis
        putJson("/api/referentiel/articles/" + article, """
                {"label": "Bouchon", "categoryCode": "CAPSULE"}
                """).andExpect(status().isOk());
    }

    @Test
    void article_without_stock_can_switch_lot_tracking() throws Exception {
        String article = createArticle("BOUCHON");

        putJson("/api/referentiel/articles/" + article, """
                {"label": "Finalement une étiquette", "categoryCode": "ETIQUETTE"}
                """).andExpect(status().isOk());
    }

    // ==================== Inventaire ====================

    @Test
    void validated_inventory_aligns_stock_on_counted_quantities() throws Exception {
        String article = createArticle("ETIQUETTE");
        move(article, null, "RECEPTION", "100").andExpect(status().isCreated());
        String inventory = createInventory();

        String draft = body(postJson("/api/stock/inventories/" + inventory + "/lines", """
                {"articleId": "%s", "countedQuantity": 95}
                """.formatted(article)).andExpect(status().isCreated()));
        assertThat(decimal(draft, "$.lines[0].theoreticalQuantity")).isEqualByComparingTo("100");
        assertThat(decimal(draft, "$.lines[0].difference")).isEqualByComparingTo("-5");  // aperçu

        String validated = body(postEmpty("/api/stock/inventories/" + inventory + "/validate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED")));
        assertThat(decimal(validated, "$.lines[0].difference")).isEqualByComparingTo("-5");  // figé

        assertThat(stockOf(article)).isEqualByComparingTo("95");
        mockMvc.perform(get("/api/stock/movements")
                        .param("articleId", article).param("type", "INVENTORY").with(user()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void inventory_with_uncounted_lines_cannot_be_validated() throws Exception {
        String article = createArticle("ETIQUETTE");
        String inventory = createInventory();
        postJson("/api/stock/inventories/" + inventory + "/lines", """
                {"articleId": "%s"}
                """.formatted(article)).andExpect(status().isCreated());

        postEmpty("/api/stock/inventories/" + inventory + "/validate")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("pas encore comptée")));
    }

    @Test
    void validated_inventory_is_read_only() throws Exception {
        String article = createArticle("ETIQUETTE");
        String inventory = createInventory();
        String withLine = body(postJson("/api/stock/inventories/" + inventory + "/lines", """
                {"articleId": "%s", "countedQuantity": 0}
                """.formatted(article)).andExpect(status().isCreated()));
        String lineId = JsonPath.read(withLine, "$.lines[0].id");
        postEmpty("/api/stock/inventories/" + inventory + "/validate").andExpect(status().isOk());

        putJson("/api/stock/inventories/" + inventory + "/lines/" + lineId, """
                {"countedQuantity": 3}
                """).andExpect(status().isConflict());
        postEmpty("/api/stock/inventories/" + inventory + "/cancel").andExpect(status().isConflict());
    }

    @Test
    void same_line_cannot_be_added_twice() throws Exception {
        String article = createArticle("ETIQUETTE");
        String inventory = createInventory();
        String line = """
                {"articleId": "%s"}
                """.formatted(article);

        postJson("/api/stock/inventories/" + inventory + "/lines", line).andExpect(status().isCreated());
        postJson("/api/stock/inventories/" + inventory + "/lines", line).andExpect(status().isConflict());
    }

    @Test
    void overview_sums_lots_and_flags_articles_below_threshold() throws Exception {
        // Seuil de 5000, stock réparti sur deux lots : 1200 + 3000 = 4200 → sous le seuil
        String article = JsonPath.read(body(postJson("/api/referentiel/articles", """
                {"code": "%s", "label": "Bouchon vue d'ensemble", "categoryCode": "BOUCHON", "alertThreshold": 5000}
                """.formatted(unique("BOUCHON"))).andExpect(status().isCreated())), "$.id");
        move(article, createLot(article, "L-A"), "RECEPTION", "1200").andExpect(status().isCreated());
        move(article, createLot(article, "L-B"), "RECEPTION", "3000").andExpect(status().isCreated());
        String empty = createArticle("ETIQUETTE");  // jamais de stock → rupture

        String json = body(getAsUser("/api/stock/overview").andExpect(status().isOk()));

        String bouchon = "$[?(@.articleId == '" + article + "')]";
        assertThat(decimal(json, bouchon + ".quantity[0]")).isEqualByComparingTo("4200");
        assertThat(JsonPath.<List<Integer>>read(json, bouchon + ".lotsInStock")).containsExactly(2);
        assertThat(JsonPath.<List<String>>read(json, bouchon + ".status")).containsExactly("BELOW_THRESHOLD");
        assertThat(JsonPath.<List<String>>read(json, "$[?(@.articleId == '" + empty + "')].status"))
                .containsExactly("OUT_OF_STOCK");
    }

    // ==================== Méthodes utilitaires ====================

    private String createInventory() throws Exception {
        return JsonPath.read(body(postJson("/api/stock/inventories", """
                {"label": "%s", "prefill": false}
                """.formatted(unique("INV")))
                .andExpect(status().isCreated())), "$.id");
    }
}