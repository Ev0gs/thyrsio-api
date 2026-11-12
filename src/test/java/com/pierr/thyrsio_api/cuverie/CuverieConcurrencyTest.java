package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.ApiIntegrationTest;
import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.cuverie.dto.*;
import com.pierr.thyrsio_api.referentiel.SupplierKind;
import com.pierr.thyrsio_api.referentiel.SupplierService;
import com.pierr.thyrsio_api.referentiel.dto.SupplierRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Comme StockConcurrencyTest : les services directement, plusieurs threads, un top départ commun. */
class CuverieConcurrencyTest extends ApiIntegrationTest {

    @Autowired CuverieService cuverieService;
    @Autowired TankService tankService;
    @Autowired SupplierService supplierService;

    /**
     * 5 hl en cuve, 8 pertes de 1 hl en même temps : le verrou sur la cuve fait passer
     * exactement 5 pertes, refuse les 3 autres, et la cuve finit vide (et libérée).
     */
    @Test
    void parallel_losses_never_make_a_tank_negative() throws Exception {
        UUID tank = tankService.create(new CreateTankRequest(unique("C"), null, new BigDecimal("10"))).id();
        UUID supplier = supplierService.create(new SupplierRequest(
                unique("Château"), SupplierKind.WINE, null, null, null, null)).id();
        cuverieService.receive(new ReceptionRequest(supplier, "Bordeaux", WineColor.RED, null, null, null, null,
                null, null, null, List.of(new TankFill(tank, new BigDecimal("5")))), TEST_USER_ID);

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    startSignal.await();
                    try {
                        cuverieService.recordLoss(tank, new LossRequest(BigDecimal.ONE, null, null, null), TEST_USER_ID);
                        return true;
                    } catch (BusinessRuleException refused) {
                        return false;
                    }
                }));
            }
            startSignal.countDown();

            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(5);
            TankResponse after = tankService.get(tank);
            assertThat(after.volumeHl()).isEqualByComparingTo("0");
            assertThat(after.wineLot()).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * A → B et B → A lancés en même temps, plusieurs fois : grâce à l'ordre de verrouillage,
     * aucun interblocage (deadlock), et le vin total ne change pas.
     */
    @Test
    void crossed_transfers_do_not_deadlock() throws Exception {
        UUID supplier = supplierService.create(new SupplierRequest(
                unique("Château"), SupplierKind.WINE, null, null, null, null)).id();
        UUID tankA = tankService.create(new CreateTankRequest(unique("C"), null, new BigDecimal("100"))).id();
        UUID tankB = tankService.create(new CreateTankRequest(unique("C"), null, new BigDecimal("100"))).id();
        cuverieService.receive(new ReceptionRequest(supplier, "Bordeaux", WineColor.RED, null, null, null, null,
                null, null, null, List.of(new TankFill(tankA, new BigDecimal("40")),
                new TankFill(tankB, new BigDecimal("40")))), TEST_USER_ID);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                boolean aToB = i % 2 == 0;
                results.add(pool.submit(() -> {
                    startSignal.await();
                    cuverieService.transfer(new TransferRequest(aToB ? tankA : tankB, aToB ? tankB : tankA,
                            BigDecimal.ONE, null, null, null, null), TEST_USER_ID);
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);  // un deadlock lèverait une exception ici
            }

            BigDecimal total = tankService.get(tankA).volumeHl().add(tankService.get(tankB).volumeHl());
            assertThat(total).isEqualByComparingTo("80");
        } finally {
            pool.shutdownNow();
        }
    }
}
