package com.pierr.thyrsio_api.referentiel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ArticleLookup {

    private final ArticleRepository articleRepository;

    public ArticleLookup(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ArticleInfo> findById(UUID id) {
        return articleRepository.findById(id).map(article -> new ArticleInfo(
                article.getId(),
                article.getCode(),
                article.getLabel(),
                article.getUnit().getCode(),
                article.getUnit().getDecimals(),
                article.getCategory().isLotTracked(),
                article.isActive()
        ));
    }
}
