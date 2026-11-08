package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.common.DuplicateCodeException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.referentiel.ArticleLookup;
import com.pierr.thyrsio_api.stock.dto.CreateLotRequest;
import com.pierr.thyrsio_api.stock.dto.LotResponse;
import com.pierr.thyrsio_api.stock.dto.UpdateLotRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LotService {

    private final LotRepository lotRepository;
    // Le service public du référentiel : jamais ArticleRepository directement
    private final ArticleLookup articleLookup;

    public LotService(LotRepository lotRepository, ArticleLookup articleLookup) {
        this.lotRepository = lotRepository;
        this.articleLookup = articleLookup;
    }

    /** Les lots d'un article, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<LotResponse> listByArticle(UUID articleId) {
        // Un seul appel au référentiel pour toute la liste
        ArticleInfo article = findArticle(articleId);
        return lotRepository.findByArticleIdOrderByCreatedAtDesc(articleId).stream()
                .map(lot -> LotResponse.from(lot, article))
                .toList();
    }

    @Transactional(readOnly = true)
    public LotResponse get(UUID id) {
        Lot lot = findLot(id);
        return LotResponse.from(lot, findArticle(lot.getArticleId()));
    }

    @Transactional
    public LotResponse create(CreateLotRequest request) {
        ArticleInfo article = findArticle(request.articleId());
        if (!article.active()) {
            throw new IllegalArgumentException("Article archivé : " + article.code());
        }
        if (!article.lotTracked()) {
            throw new IllegalArgumentException("L'article " + article.code() + " n'est pas suivi par lot");
        }

        String lotNumber = request.lotNumber().trim();
        if (lotRepository.existsByArticleIdAndLotNumber(article.id(), lotNumber)) {
            throw new DuplicateCodeException("Lot de " + article.code(), lotNumber);
        }

        Lot lot = new Lot(article.id(), lotNumber);
        lot.setExpiryDate(request.expiryDate());
        lot.setNotes(blankToNull(request.notes()));
        return LotResponse.from(lotRepository.save(lot), article);
    }

    @Transactional
    public LotResponse update(UUID id, UpdateLotRequest request) {
        Lot lot = findLot(id);
        lot.setExpiryDate(request.expiryDate());
        lot.setNotes(blankToNull(request.notes()));
        return LotResponse.from(lot, findArticle(lot.getArticleId()));
    }

    // --- Méthodes utilitaires ---

    private Lot findLot(UUID id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot", id));
    }

    /** Un article inconnu dans la requête est une erreur du client (400). */
    private ArticleInfo findArticle(UUID articleId) {
        return articleLookup.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + articleId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}