package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.Article;
import com.pierr.thyrsio_api.referentiel.Supplier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ArticleResponse(
        UUID id,
        String code,
        String label,
        String categoryCode,
        String categoryLabel,
        String unitCode,
        UUID mainSupplierId,
        String mainSupplierName,
        String supplierReference,
        BigDecimal alertThreshold,
        boolean active,
        Instant createdAt
) {
    public static ArticleResponse from(Article article) {
        Supplier supplier = article.getMainSupplier();
        return new ArticleResponse(
                article.getId(),
                article.getCode(),
                article.getLabel(),
                article.getCategory().getCode(),
                article.getCategory().getLabel(),
                article.getUnit().getCode(),
                supplier == null ? null : supplier.getId(),
                supplier == null ? null : supplier.getName(),
                article.getSupplierReference(),
                article.getAlertThreshold(),
                article.isActive(),
                article.getCreatedAt()
        );
    }
}
