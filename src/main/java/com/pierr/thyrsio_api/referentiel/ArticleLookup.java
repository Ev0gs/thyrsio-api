package com.pierr.thyrsio_api.referentiel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArticleLookup {

    private final ArticleRepository articleRepository;

    public ArticleLookup(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ArticleInfo> findById(UUID id) {
        return articleRepository.findById(id).map(ArticleLookup::toInfo);
    }

    /** Plusieurs articles en une seule requête, indexés par id : pour enrichir une liste sans N+1. */
    @Transactional(readOnly = true)
    public Map<UUID, ArticleInfo> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return articleRepository.findByIdIn(ids).stream()
                .map(ArticleLookup::toInfo)
                .collect(Collectors.toMap(ArticleInfo::id, Function.identity()));
    }

    private static ArticleInfo toInfo(Article article) {
        return new ArticleInfo(
                article.getId(),
                article.getCode(),
                article.getLabel(),
                article.getUnit().getCode(),
                article.getUnit().getDecimals(),
                article.getCategory().isLotTracked(),
                article.isActive()
        );
    }
}