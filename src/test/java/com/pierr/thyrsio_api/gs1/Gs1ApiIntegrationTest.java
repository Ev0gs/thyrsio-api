package com.pierr.thyrsio_api.gs1;

import com.jayway.jsonpath.JsonPath;
import com.pierr.thyrsio_api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Gs1ApiIntegrationTest extends ApiIntegrationTest {

    @Test
    void generates_the_next_gtin_of_the_prefix_for_a_bottle_and_a_carton() throws Exception {
        String prefix = randomPrefix(9);  // 9 chiffres : références sur 3 chiffres, 1 000 gencodes
        String prefixId = createPrefix(prefix);
        String product = createArticle("PRODUIT_FINI");

        generate(prefixId, product, "BOTTLE", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gtin").value(Gtins.withCheckDigit(prefix + "001")))
                .andExpect(jsonPath("$.prefix").value(prefix))
                .andExpect(jsonPath("$.finishedArticleId").value(product))
                .andExpect(jsonPath("$.unitsPerPack").value(1))
                .andExpect(jsonPath("$.packagingLabel").value("Bouteille"));
        generate(prefixId, product, "CARTON", 6)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gtin").value(Gtins.withCheckDigit(prefix + "002")))
                .andExpect(jsonPath("$.unitsPerPack").value(6));
        generate(prefixId, product, "WOODEN_CASE", 6).andExpect(status().isCreated());

        getAsUser("/api/gs1/gtins?articleId=" + product)
                .andExpect(jsonPath("$.content.length()").value(3));
        getAsUser("/api/gs1/prefixes")
                .andExpect(jsonPath("$[?(@.prefix == '" + prefix + "')].used").value(3))
                .andExpect(jsonPath("$[?(@.prefix == '" + prefix + "')].remaining").value(997));
    }

    @Test
    void one_active_gtin_per_product_and_packaging_and_a_gtin_is_never_given_again() throws Exception {
        String prefix = randomPrefix(9);
        String prefixId = createPrefix(prefix);
        String product = createArticle("PRODUIT_FINI");
        String first = JsonPath.read(body(generate(prefixId, product, "CARTON", 6)
                .andExpect(status().isCreated())), "$.id");

        // Le carton de 6 a déjà son gencode ; un carton de 12 est un autre conditionnement
        generate(prefixId, product, "CARTON", 6)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("carton de 6")));
        generate(prefixId, product, "CARTON", 12).andExpect(status().isCreated());

        // Retiré : on peut en créer un autre, mais le numéro retiré ne revient pas
        postEmpty("/api/gs1/gtins/" + first + "/retire")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));
        generate(prefixId, product, "CARTON", 6)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gtin").value(Gtins.withCheckDigit(prefix + "003")));
    }

    @Test
    void a_full_prefix_refuses_new_gtins() throws Exception {
        String prefix = randomPrefix(11);  // 11 chiffres : 10 références, dont la 0 n'est pas utilisée
        String prefixId = createPrefix(prefix);
        for (int i = 1; i <= 9; i++) {
            generate(prefixId, createArticle("PRODUIT_FINI"), "BOTTLE", null).andExpect(status().isCreated());
        }
        generate(prefixId, createArticle("PRODUIT_FINI"), "BOTTLE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("est plein")));
    }

    @Test
    void only_finished_goods_get_a_gtin_and_a_pack_has_several_bottles() throws Exception {
        String prefixId = createPrefix(randomPrefix(9));
        generate(prefixId, createArticle("ETIQUETTE"), "BOTTLE", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("n'est pas un produit fini")));
        generate(prefixId, createArticle("PRODUIT_FINI"), "CARTON", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("nombre de bouteilles")));
        // Une bouteille, c'est toujours 1
        generate(prefixId, createArticle("PRODUIT_FINI"), "BOTTLE", 6)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitsPerPack").value(1));
    }

    @Test
    void an_existing_gtin_is_checked_then_can_be_assigned_later() throws Exception {
        String prefix = randomPrefix(9);
        createPrefix(prefix);
        String valid = Gtins.withCheckDigit(prefix + "500");
        String wrongKey = valid.substring(0, 12) + ((valid.charAt(12) - '0' + 1) % 10);

        record(wrongKey, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("n'est pas un EAN-13 valide")));

        // Saisi avec des espaces, comme sur l'étiquette ; réservé, sans produit
        String spaced = valid.charAt(0) + " " + valid.substring(1, 7) + " " + valid.substring(7);
        String id = JsonPath.read(body(record(spaced, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gtin").value(valid))
                .andExpect(jsonPath("$.prefix").value(prefix))
                .andExpect(jsonPath("$.finishedArticleId").doesNotExist())), "$.id");
        record(valid, null).andExpect(status().isConflict());

        String product = createArticle("PRODUIT_FINI");
        postJson("/api/gs1/gtins/" + id + "/assign", """
                {"finishedArticleId": "%s", "packaging": "BOTTLE"}
                """.formatted(product)).andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedArticleId").value(product));
        // Attribué une fois pour toutes
        postJson("/api/gs1/gtins/" + id + "/assign", """
                {"finishedArticleId": "%s", "packaging": "BOTTLE"}
                """.formatted(createArticle("PRODUIT_FINI"))).andExpect(status().isConflict());

        // La génération reprend après le plus grand déjà pris, saisi à la main compris
        String prefixId = JsonPath.<List<String>>read(body(getAsUser("/api/gs1/prefixes")),
                "$[?(@.prefix == '" + prefix + "')].id").getFirst();
        generate(prefixId, product, "CARTON", 6)
                .andExpect(jsonPath("$.gtin").value(Gtins.withCheckDigit(prefix + "501")));
    }

    @Test
    void prefixes_cannot_overlap() throws Exception {
        String prefix = randomPrefix(9);
        createPrefix(prefix);
        postJson("/api/gs1/prefixes", """
                {"prefix": "%s"}
                """.formatted(prefix + "1")).andExpect(status().isConflict());
        postJson("/api/gs1/prefixes", """
                {"prefix": "12345"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void search_finds_a_gtin_by_its_digits_or_its_product() throws Exception {
        String prefix = randomPrefix(9);
        String prefixId = createPrefix(prefix);
        String product = JsonPath.read(body(postJson("/api/referentiel/articles", """
                {"code": "%s", "label": "Château Recherche 2022", "categoryCode": "PRODUIT_FINI"}
                """.formatted(unique("PF"))).andExpect(status().isCreated())), "$.id");
        String gtin = JsonPath.read(body(generate(prefixId, product, "BOTTLE", null)), "$.gtin");

        List<String> byDigits = JsonPath.read(body(getAsUser("/api/gs1/gtins?q=" + gtin.substring(3, 11))), "$.content[*].gtin");
        assertThat(byDigits).contains(gtin);
        List<String> byProduct = JsonPath.read(body(getAsUser("/api/gs1/gtins?q=recherche")), "$.content[*].gtin");
        assertThat(byProduct).contains(gtin);
    }

    // ==================== Méthodes utilitaires ====================

    /** Un préfixe tiré au hasard, qui commence par 3 (la France) ; deux préfixes de même longueur ne se recouvrent pas. */
    private static String randomPrefix(int length) {
        StringBuilder prefix = new StringBuilder("3");
        while (prefix.length() < length) {
            prefix.append(ThreadLocalRandom.current().nextInt(10));
        }
        return prefix.toString();
    }

    private String createPrefix(String prefix) throws Exception {
        return JsonPath.read(body(postJson("/api/gs1/prefixes", """
                {"prefix": "%s", "label": "Préfixe de test"}
                """.formatted(prefix)).andExpect(status().isCreated())), "$.id");
    }

    private ResultActions generate(String prefixId, String product, String packaging, Integer units) throws Exception {
        return postJson("/api/gs1/gtins/generate", """
                {"prefixId": "%s", "finishedArticleId": "%s", "packaging": "%s", "unitsPerPack": %s}
                """.formatted(prefixId, product, packaging, units));
    }

    private ResultActions record(String gtin, String product) throws Exception {
        return postJson("/api/gs1/gtins", """
                {"gtin": "%s", "finishedArticleId": %s, "packaging": "BOTTLE"}
                """.formatted(gtin, product == null ? "null" : "\"" + product + "\""));
    }
}
