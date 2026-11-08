package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Les filtres de la liste des clients, sur le modèle d'ArticleSpecifications. */
final class ClientSpecifications {

    private ClientSpecifications() {
    }

    static Specification<Client> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    static Specification<Client> inCountry(String countryCode) {
        return (root, query, cb) -> cb.equal(root.get("countryCode"), countryCode);
    }

    /** Recherche dans le code ou la raison sociale, sans tenir compte des majuscules. */
    static Specification<Client> matches(String text) {
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("name")), pattern)
        );
    }
}