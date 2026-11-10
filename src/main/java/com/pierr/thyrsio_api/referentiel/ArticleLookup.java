package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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

    /** Tous les articles (actifs seulement, par défaut), triés par libellé : pour les vues du stock. */
    @Transactional(readOnly = true)
    public List<ArticleInfo> findAll(boolean includeArchived) {
        List<Specification<Article>> filters = includeArchived
                ? List.of()
                : List.of(ArticleSpecifications.isActive());
        return articleRepository.findAll(Specification.allOf(filters), Sort.by("label")).stream()
                .map(ArticleLookup::toInfo)
                .toList();
    }

    private static ArticleInfo toInfo(Article article) {
        return new ArticleInfo(
                article.getId(),
                article.getCode(),
                article.getLabel(),
                article.getCategory().getCode(),
                article.getCategory().getLabel(),
                article.getUnit().getCode(),
                article.getUnit().getDecimals(),
                article.getCategory().isLotTracked(),
                article.getAlertThreshold(),
                article.isActive()
        );
    }
}