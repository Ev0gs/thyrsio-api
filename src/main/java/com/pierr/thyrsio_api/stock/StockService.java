package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.referentiel.ArticleLookup;
import com.pierr.thyrsio_api.stock.dto.BalanceResponse;
import com.pierr.thyrsio_api.stock.dto.CreateMovementRequest;
import com.pierr.thyrsio_api.stock.dto.MovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockService {

    private final StockMovementRepository movementRepository;
    private final StockBalanceRepository balanceRepository;
    private final LotRepository lotRepository;
    private final ArticleLookup articleLookup;

    public StockService(StockMovementRepository movementRepository,
                        StockBalanceRepository balanceRepository,
                        LotRepository lotRepository,
                        ArticleLookup articleLookup) {
        this.movementRepository = movementRepository;
        this.balanceRepository = balanceRepository;
        this.lotRepository = lotRepository;
        this.articleLookup = articleLookup;
    }

    // ========== Écriture ==========

    /** Saisie depuis l'API : seuls les types prévus pour une saisie manuelle sont acceptés. */
    @Transactional
    public MovementResponse recordManual(CreateMovementRequest request, UUID userId) {
        if (!request.type().isManualEntry()) {
            throw new IllegalArgumentException(
                    "Les mouvements " + request.type() + " sont créés par l'application, pas saisis à la main");
        }
        StockMovement movement = record(new StockMovementCommand(
                request.articleId(), request.lotId(), request.type(), request.quantity(),
                request.occurredAt(), request.reference(), request.notes(), userId));
        ArticleInfo article = articleLookup.findById(movement.getArticleId()).orElseThrow();
        return MovementResponse.from(movement, article);
    }

    /**
     * LE point d'entrée unique pour faire bouger le stock : API, et plus tard Achats, Production, Expéditions.
     * Le mouvement et le solde sont enregistrés dans la même transaction.
     */
    @Transactional
    public StockMovement record(StockMovementCommand command) {
        ArticleInfo article = articleLookup.findById(command.articleId())
                .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + command.articleId()));

        BigDecimal signedQuantity = command.type().signedQuantity(command.quantity());
        checkDecimals(command.quantity(), article);
        if (!article.active() && signedQuantity.signum() > 0) {
            throw new BusinessRuleException("L'article " + article.code() + " est archivé : aucune entrée possible");
        }
        Lot lot = resolveLot(article, command.lotId());

        // Verrouille le solde jusqu'à la fin de la transaction
        StockBalance balance = lockBalance(article.id(), lot);
        BigDecimal available = balance.getQuantity();
        if (available.add(signedQuantity).signum() < 0) {
            throw new BusinessRuleException("Stock insuffisant pour " + describe(article, lot) + " : "
                    + plain(available) + " " + article.unitCode() + " disponible(s), "
                    + plain(signedQuantity.negate()) + " demandé(s)");
        }
        balance.apply(signedQuantity);
        balanceRepository.save(balance); // indispensable pour un solde tout neuf, sans effet sinon

        StockMovement movement = new StockMovement(
                article.id(), lot, command.type(), signedQuantity,
                command.occurredAt() != null ? command.occurredAt() : Instant.now(),
                blankToNull(command.reference()), blankToNull(command.notes()), command.createdBy());
        return movementRepository.save(movement);
    }

    /** Résultat d'un alignement d'inventaire : ce qu'il y avait, et l'écart appliqué. */
    record InventoryAdjustment(BigDecimal expected, BigDecimal delta) {
    }

    /**
     * Amène le stock d'un article (et d'un lot) à la quantité comptée, avec un mouvement INVENTORY pour l'écart.
     * Package-private : appelé seulement par InventoryService, dans sa transaction.
     * Pas de refus sur un article archivé : l'inventaire constate la réalité.
     */
    InventoryAdjustment adjustTo(ArticleInfo article, Lot lot, BigDecimal countedQuantity,
                                 Instant occurredAt, String reference, UUID userId) {
        StockBalance balance = lockBalance(article.id(), lot);
        BigDecimal expected = balance.getQuantity();
        BigDecimal delta = countedQuantity.subtract(expected);
        if (delta.signum() != 0) {
            balance.apply(delta);
            balanceRepository.save(balance);
            movementRepository.save(new StockMovement(
                    article.id(), lot, MovementType.INVENTORY, delta, occurredAt, reference, null, userId));
        }
        return new InventoryAdjustment(expected, delta);
    }

    // ========== Lecture ==========

    @Transactional(readOnly = true)
    public Page<MovementResponse> listMovements(UUID articleId, UUID lotId, MovementType type,
                                                Instant from, Instant to, Pageable pageable) {
        List<Specification<StockMovement>> filters = new ArrayList<>();
        if (articleId != null) filters.add(StockSpecifications.movementOfArticle(articleId));
        if (lotId != null) filters.add(StockSpecifications.movementOfLot(lotId));
        if (type != null) filters.add(StockSpecifications.movementOfType(type));
        if (from != null) filters.add(StockSpecifications.occurredFrom(from));
        if (to != null) filters.add(StockSpecifications.occurredBefore(to));

        Page<StockMovement> page = movementRepository.findAll(Specification.allOf(filters), pageable);
        // Une seule requête au référentiel pour tous les articles de la page
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                page.stream().map(StockMovement::getArticleId).collect(Collectors.toSet()));
        return page.map(movement -> MovementResponse.from(movement, articles.get(movement.getArticleId())));
    }

    @Transactional(readOnly = true)
    public Page<BalanceResponse> listBalances(UUID articleId, boolean includeEmpty, Pageable pageable) {
        List<Specification<StockBalance>> filters = new ArrayList<>();
        if (articleId != null) filters.add(StockSpecifications.balanceOfArticle(articleId));
        if (!includeEmpty) filters.add(StockSpecifications.balanceNotEmpty());

        Page<StockBalance> page = balanceRepository.findAll(Specification.allOf(filters), pageable);
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(
                page.stream().map(StockBalance::getArticleId).collect(Collectors.toSet()));
        return page.map(balance -> BalanceResponse.from(balance, articles.get(balance.getArticleId())));
    }

    // ========== Méthodes utilitaires ==========

    /** Package-private : réutilisé par InventoryService. */
    Lot resolveLot(ArticleInfo article, UUID lotId) {
        if (!article.lotTracked()) {
            if (lotId != null) {
                throw new IllegalArgumentException(
                        "L'article " + article.code() + " n'est pas suivi par lot : ne précisez pas de lot");
            }
            return null;
        }
        if (lotId == null) {
            throw new IllegalArgumentException("L'article " + article.code() + " est suivi par lot : le lot est obligatoire");
        }
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("Lot inconnu : " + lotId));
        if (!lot.getArticleId().equals(article.id())) {
            throw new IllegalArgumentException(
                    "Le lot " + lot.getLotNumber() + " n'appartient pas à l'article " + article.code());
        }
        return lot;
    }

    /** Le solde existant, verrouillé ; ou un nouveau solde à zéro s'il n'y a encore jamais eu de stock. */
    private StockBalance lockBalance(UUID articleId, Lot lot) {
        Optional<StockBalance> existing = (lot == null)
                ? balanceRepository.findForUpdateByArticleIdAndLotIsNull(articleId)
                : balanceRepository.findForUpdateByArticleIdAndLot(articleId, lot);
        return existing.orElseGet(() -> new StockBalance(articleId, lot));
    }

    /** Package-private : réutilisé par InventoryService. */
    static void checkDecimals(BigDecimal quantity, ArticleInfo article) {
        if (quantity.stripTrailingZeros().scale() > article.unitDecimals()) {
            throw new IllegalArgumentException("Trop de décimales pour l'unité " + article.unitCode()
                    + " : " + article.unitDecimals() + " au maximum");
        }
    }

    private static String describe(ArticleInfo article, Lot lot) {
        return lot == null ? article.code() : article.code() + " (lot " + lot.getLotNumber() + ")";
    }

    /** 7000.0000 → "7000" pour les messages d'erreur. */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}