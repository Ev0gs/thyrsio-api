package com.pierr.thyrsio_api.sales;

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

/** Commandes, couverture par le stock et les mises, expéditions : tout le parcours de vente. */
class SalesAndShippingApiIntegrationTest extends ApiIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Paris"));

    // ==================== Commandes ====================

    @Test
    void only_finished_goods_can_be_ordered() throws Exception {
        String client = client("FOB");

        createOrder(client, TODAY.plusDays(5), line(createArticle("ETIQUETTE"), 100))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("n'est pas un produit fini")));
    }

    @Test
    void a_new_order_is_a_draft_with_the_client_defaults_and_can_be_confirmed() throws Exception {
        String client = client("FOB");
        String order = id(createOrder(client, TODAY.plusDays(5), line(createArticle("PRODUIT_FINI"), 600))
                .andExpect(jsonPath("$.code", matchesPattern("CC-\\d{2}-\\d{3}")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.incoterm").value("FOB"))
                .andExpect(jsonPath("$.deliveryAddress.city").value("Yokohama")));

        postEmpty("/api/sales/orders/" + order + "/confirm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        postEmpty("/api/sales/orders/" + order + "/confirm").andExpect(status().isConflict());
    }

    @Test
    void an_empty_order_cannot_be_confirmed() throws Exception {
        String order = id(createOrder(client(null), TODAY, ""));

        postEmpty("/api/sales/orders/" + order + "/confirm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("aucune ligne")));
    }

    // ==================== Couverture ====================

    @Test
    void stock_goes_to_the_earliest_loading_and_the_rest_is_to_plan() throws Exception {
        String product = productInStock(1000);
        String client = client(null);
        // Chargée dans 5 jours : 800 sur 1 000 en stock. Dans 10 jours : 600, il n'en reste que 200.
        String first = confirmedOrder(client, TODAY.plusDays(5), product, 800);
        String second = confirmedOrder(client, TODAY.plusDays(10), product, 600);

        getAsUser("/api/sales/orders/" + first)
                .andExpect(jsonPath("$.lines[0].fromStock").value(800))
                .andExpect(jsonPath("$.lines[0].missing").value(0));
        String json = body(getAsUser("/api/sales/orders/" + second)
                .andExpect(jsonPath("$.lines[0].fromStock").value(200))
                .andExpect(jsonPath("$.lines[0].missing").value(400)));

        String lineId = JsonPath.read(json, "$.lines[0].id");
        List<Integer> missing = JsonPath.read(body(getAsUser("/api/sales/order-lines/to-plan")),
                "$[?(@.lineId == '" + lineId + "')].missing");
        assertThat(missing).containsExactly(400);
    }

    @Test
    void a_planned_bottling_covers_the_order_line_it_serves() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String order = confirmedOrder(client(null), TODAY.plusDays(10), product, 3600);
        String lineId = JsonPath.read(body(getAsUser("/api/sales/orders/" + order)), "$.lines[0].id");

        planBottling(product, 6000, allocation(lineId, 3600))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations[0].quantity").value(3600))
                .andExpect(jsonPath("$.allocations[0].orderCode", startsWith("CC-")));

        getAsUser("/api/sales/orders/" + order)
                .andExpect(jsonPath("$.lines[0].fromBottlings").value(3600))
                .andExpect(jsonPath("$.lines[0].missing").value(0))
                .andExpect(jsonPath("$.lines[0].allocations", hasSize(1)));

        // Une seconde mise ne peut pas réserver ce que la première couvre déjà
        planBottling(product, 1000, allocation(lineId, 500))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("n'attend plus que 0")));
    }

    @Test
    void a_bottling_only_serves_orders_for_its_own_product() throws Exception {
        String order = confirmedOrder(client(null), TODAY.plusDays(10), createArticle("PRODUIT_FINI"), 100);
        String lineId = JsonPath.read(body(getAsUser("/api/sales/orders/" + order)), "$.lines[0].id");

        planBottling(createArticle("PRODUIT_FINI"), 100, allocation(lineId, 100))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("autre produit")));
    }

    @Test
    void a_line_served_by_a_bottling_cannot_be_removed() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String client = client(null);
        String order = confirmedOrder(client, TODAY.plusDays(10), product, 500);
        String lineId = JsonPath.read(body(getAsUser("/api/sales/orders/" + order)), "$.lines[0].id");
        planBottling(product, 500, allocation(lineId, 500)).andExpect(status().isCreated());

        putJson("/api/sales/orders/" + order, """
                {"orderDate": "%s", "lines": []}
                """.formatted(TODAY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("retirez d'abord")));
    }

    // ==================== Expéditions ====================

    @Test
    void shipping_takes_bottles_out_of_stock_and_moves_the_order_forward() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String lot = createLot(product, "L26300-1");
        move(product, lot, "RECEPTION", "1000").andExpect(status().isCreated());
        String order = confirmedOrder(client(null), TODAY, product, 800);
        String lineId = JsonPath.read(body(getAsUser("/api/sales/orders/" + order)), "$.lines[0].id");

        String shipment = id(createShipment(order, shipmentLine(lineId, lot, 500))
                .andExpect(jsonPath("$.code", matchesPattern("EX-\\d{2}-\\d{3}")))
                .andExpect(jsonPath("$.status").value("DRAFT")));
        // En préparation : rien n'est encore sorti
        assertThat(stockOf(product)).isEqualByComparingTo("1000");

        postEmpty("/api/shipping/shipments/" + shipment + "/ship")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.lines[0].lotNumber").value("L26300-1"))
                .andExpect(jsonPath("$.deliveryAddressText", containsString("Yokohama")));

        assertThat(stockOf(product)).isEqualByComparingTo("500");
        getAsUser("/api/sales/orders/" + order)
                .andExpect(jsonPath("$.status").value("PARTIALLY_SHIPPED"))
                .andExpect(jsonPath("$.lines[0].shippedQuantity").value(500))
                .andExpect(jsonPath("$.shipments", hasSize(1)));

        // Il ne reste que 300 bouteilles à expédier
        createShipment(order, shipmentLine(lineId, lot, 400))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("n'attend plus que 300")));

        // Les 300 dernières : la commande est expédiée
        String last = id(createShipment(order, shipmentLine(lineId, lot, 300)));
        postEmpty("/api/shipping/shipments/" + last + "/ship").andExpect(status().isOk());
        getAsUser("/api/sales/orders/" + order).andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void shipping_more_than_the_lot_holds_changes_nothing() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String lot = createLot(product, "L-PETIT");
        move(product, lot, "RECEPTION", "10").andExpect(status().isCreated());
        String order = confirmedOrder(client(null), TODAY, product, 50);
        String lineId = JsonPath.read(body(getAsUser("/api/sales/orders/" + order)), "$.lines[0].id");
        String shipment = id(createShipment(order, shipmentLine(lineId, lot, 50)));

        postEmpty("/api/shipping/shipments/" + shipment + "/ship")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Stock insuffisant")));

        assertThat(stockOf(product)).isEqualByComparingTo("10");
        getAsUser("/api/sales/orders/" + order).andExpect(jsonPath("$.status").value("CONFIRMED"));
        getAsUser("/api/shipping/shipments/" + shipment).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void a_lot_of_another_product_cannot_be_loaded() throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String otherLot = createLot(createArticle("PRODUIT_FINI"), "L-AUTRE");
        String order = confirmedOrder(client(null), TODAY, product, 50);
        String lineId = JsonPath.read(body(getAsUser("/api/sales/orders/" + order)), "$.lines[0].id");

        createShipment(order, shipmentLine(lineId, otherLot, 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("pas un lot du produit")));
    }

    @Test
    void an_order_being_prepared_for_shipping_cannot_be_cancelled() throws Exception {
        String product = productInStock(100);
        String order = confirmedOrder(client(null), TODAY, product, 50);
        String json = body(getAsUser("/api/sales/orders/" + order));
        String lineId = JsonPath.read(json, "$.lines[0].id");
        String lot = JsonPath.read(body(getAsUser("/api/stock/lots?articleId=" + product)), "$[0].id");
        String shipment = id(createShipment(order, shipmentLine(lineId, lot, 50)));

        postEmpty("/api/sales/orders/" + order + "/cancel").andExpect(status().isConflict());

        postEmpty("/api/shipping/shipments/" + shipment + "/cancel")
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        postEmpty("/api/sales/orders/" + order + "/cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void a_draft_order_cannot_be_shipped() throws Exception {
        String product = productInStock(100);
        String order = id(createOrder(client(null), TODAY, line(product, 10)));
        String lineId = JsonPath.read(body(getAsUser("/api/sales/orders/" + order)), "$.lines[0].id");
        String lot = JsonPath.read(body(getAsUser("/api/stock/lots?articleId=" + product)), "$[0].id");

        createShipment(order, shipmentLine(lineId, lot, 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("doit être confirmée")));
    }

    // ==================== Méthodes utilitaires ====================

    /** Un client, avec une adresse de livraison par défaut à Yokohama. */
    private String client(String incoterm) throws Exception {
        String id = JsonPath.read(body(postJson("/api/referentiel/clients", """
                {"code": "%s", "name": "Client Test", "countryCode": "JP", "defaultIncoterm": %s}
                """.formatted(unique("CLI"), incoterm == null ? "null" : "\"" + incoterm + "\""))
                .andExpect(status().isCreated())), "$.id");
        postJson("/api/referentiel/clients/" + id + "/delivery-addresses", """
                {"label": "Entrepôt", "addressLine1": "1-2-3 Daikoku", "city": "Yokohama",
                 "countryCode": "JP", "defaultAddress": true}
                """).andExpect(status().isCreated());
        return id;
    }

    /** Un produit fini avec du stock sur un lot. */
    private String productInStock(int bottles) throws Exception {
        String product = createArticle("PRODUIT_FINI");
        String lot = createLot(product, unique("L"));
        move(product, lot, "RECEPTION", String.valueOf(bottles)).andExpect(status().isCreated());
        return product;
    }

    private static String line(String articleId, int quantity) {
        return """
                {"articleId": "%s", "quantity": %d, "unitPrice": 8.50}""".formatted(articleId, quantity);
    }

    private ResultActions createOrder(String clientId, LocalDate loading, String lines) throws Exception {
        return postJson("/api/sales/orders", """
                {"clientId": "%s", "customerReference": "PO-TEST", "requestedLoadingDate": "%s", "lines": [%s]}
                """.formatted(clientId, loading, lines));
    }

    private String confirmedOrder(String clientId, LocalDate loading, String product, int quantity) throws Exception {
        String order = id(createOrder(clientId, loading, line(product, quantity)));
        postEmpty("/api/sales/orders/" + order + "/confirm").andExpect(status().isOk());
        return order;
    }

    private static String allocation(String lineId, int quantity) {
        return """
                {"orderLineId": "%s", "quantity": %d}""".formatted(lineId, quantity);
    }

    private ResultActions planBottling(String product, int bottles, String allocations) throws Exception {
        return postJson("/api/production/bottlings", """
                {"location": "AT_PRODUCER", "startDate": "%s", "endDate": "%s", "finishedArticleId": "%s",
                 "plannedBottles": %d, "bottleVolumeCl": 75, "materials": [], "allocations": [%s]}
                """.formatted(TODAY.plusDays(3), TODAY.plusDays(3), product, bottles, allocations));
    }

    private static String shipmentLine(String lineId, String lotId, int quantity) {
        return """
                {"orderLineId": "%s", "lotId": "%s", "quantity": %d}""".formatted(lineId, lotId, quantity);
    }

    private ResultActions createShipment(String orderId, String... lines) throws Exception {
        return postJson("/api/shipping/shipments", """
                {"orderId": "%s", "plannedDate": "%s", "carrier": "Transports Test", "lines": [%s]}
                """.formatted(orderId, TODAY, String.join(",", lines)));
    }

    private String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result.andExpect(status().isCreated())), "$.id");
    }
}
