package com.pierr.thyrsio_api.stock;

import com.pierr.thyrsio_api.ApiIntegrationTest;
import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.referentiel.ArticleService;
import com.pierr.thyrsio_api.referentiel.dto.CreateArticleRequest;
import com.pierr.thyrsio_api.stock.dto.AddInventoryLineRequest;
import com.pierr.thyrsio_api.stock.dto.CountInventoryLineRequest;
import com.pierr.thyrsio_api.stock.dto.CreateInventoryRequest;
import com.pierr.thyrsio_api.stock.dto.CreateLotRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pas de MockMvc ici : on appelle les services directement pour maîtriser
 * les threads et les transactions. La classe est dans le package stock
 * pour pouvoir appeler Inventory.cancel(), qui est package-private.
 */
class StockConcurrencyTest extends ApiIntegrationTest {

    @Autowired StockService stockService;
    @Autowired LotService lotService;
    @Autowired ArticleService articleService;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired PlatformTransactionManager transactionManager;

    /**
     * 5 bouchons en stock, 8 consommations de 1 lancées exactement en même temps.
     * Sans le verrou pessimiste, plusieurs transactions liraient "5" en même temps
     * et le stock finirait faux (ou la base refuserait sans explication).
     * Avec le verrou : exactement 5 réussissent, 3 sont refusées, et le stock tombe à 0.
     */
    @Test
    void parallel_consumptions_never_make_stock_negative() throws Exception {
        UUID article = articleService.create(new CreateArticleRequest(
                unique("BOUCHON"), "Bouchon concurrence", "BOUCHON", null, null, null, null)).id();
        UUID lot = lotService.create(new CreateLotRequest(article, "L-CONC", null, null)).id();
        stockService.record(command(article, lot, MovementType.RECEPTION, "5"));

        int attempts = 8; // reste sous la taille du pool de connexions (10 par défaut)
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    startSignal.await();  // tous les threads attendent le top départ
                    try {
                        stockService.record(command(article, lot, MovementType.CONSUMPTION, "1"));
                        return true;
                    } catch (BusinessRuleException insufficientStock) {
                        return false;
                    }
                }));
            }
            startSignal.countDown();  // top départ

            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(5);
            assertThat(stockService.listBalances(article, true, Pageable.unpaged())
                    .getContent().getFirst().quantity()).isEqualByComparingTo("0");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Alice ouvre l'inventaire. Pendant ce temps, Bob saisit un comptage et enregistre.
     * Quand Alice veut annuler à partir de sa version périmée, @Version la bloque.
     */
    @Test
    void stale_inventory_change_is_rejected_by_optimistic_locking() throws Exception {
        UUID article = articleService.create(new CreateArticleRequest(
                unique("ETIQUETTE"), "Étiquette concurrence", "ETIQUETTE", null, null, null, null)).id();
        UUID inventoryId = inventoryService.create(
                new CreateInventoryRequest(unique("INV"), null, null, false), TEST_USER_ID).id();
        UUID lineId = inventoryService.addLine(inventoryId,
                new AddInventoryLineRequest(article, null, null, null)).lines().getFirst().id();

        TransactionTemplate aliceTx = new TransactionTemplate(transactionManager);
        TransactionTemplate bobTx = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW : la transaction de Bob est indépendante et validée avant celle d'Alice
        bobTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> aliceTx.executeWithoutResult(status -> {
            // 1. Alice charge l'inventaire : elle voit la version N
            Inventory seenByAlice = inventoryRepository.findWithLinesById(inventoryId).orElseThrow();

            // 2. Bob compte une ligne et valide sa transaction : la version passe à N+1
            bobTx.executeWithoutResult(bobStatus -> inventoryService.countLine(
                    inventoryId, lineId, new CountInventoryLineRequest(new BigDecimal("8"), null)));

            // 3. Alice annule à partir de la version N : UPDATE ... WHERE version = N → 0 ligne
            seenByAlice.cancel();
            inventoryRepository.flush();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // L'annulation d'Alice n'a pas eu lieu, le comptage de Bob est bien là
        var inventory = inventoryService.get(inventoryId);
        assertThat(inventory.status()).isEqualTo(InventoryStatus.DRAFT);
        assertThat(inventory.lines().getFirst().countedQuantity()).isEqualByComparingTo("8");
    }

    private static StockMovementCommand command(UUID article, UUID lot, MovementType type, String quantity) {
        return new StockMovementCommand(article, lot, type, new BigDecimal(quantity), null, null, null, TEST_USER_ID);
    }
}