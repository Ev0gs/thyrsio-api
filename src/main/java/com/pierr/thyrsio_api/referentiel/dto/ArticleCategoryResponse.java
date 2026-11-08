package com.pierr.thyrsio_api.referentiel.dto;

import com.pierr.thyrsio_api.referentiel.ArticleCategory;

import java.util.UUID;

public record ArticleCategoryResponse(
        UUID id,
        String code,
        String label,
        String defaultUnitCode,
        Boolean lotTracked,
        Boolean regulated,
        Boolean active
) {
    public static ArticleCategoryResponse from(ArticleCategory articleCategory) {
        return new ArticleCategoryResponse(
                articleCategory.getId(),
                articleCategory.getCode(),
                articleCategory.getLabel(),
                articleCategory.getDefaultUnit().getCode(),
                articleCategory.isLotTracked(),
                articleCategory.isRegulated(),
                articleCategory.isActive()
        );
    }
}
