package com.pierr.thyrsio_api.referentiel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * La tâche de fond qui place les adresses sur la carte : à chaque passage, elle prend les adresses
 * jamais localisées (ou modifiées depuis) et les cherche une par une, au rythme imposé par le géocodeur.
 *
 * Rien n'est verrouillé pendant la recherche : le résultat n'est enregistré que si l'adresse
 * n'a pas changé entre-temps (même requête), sinon il sera recalculé au passage suivant.
 */
public class GeocodingJob implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GeocodingJob.class);
    static final int BATCH_SIZE = 20;

    private final ClientRepository clientRepository;
    private final ClientDeliveryAddressRepository addressRepository;
    private final SupplierRepository supplierRepository;
    private final Geocoder geocoder;
    private final TransactionTemplate transaction;

    /** Les passages demandés après un enregistrement : un seul fil, et un seul passage en attente à la fois. */
    private final ExecutorService onDemand = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "geocoding");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean queued = new AtomicBoolean();

    public GeocodingJob(ClientRepository clientRepository, ClientDeliveryAddressRepository addressRepository,
                        SupplierRepository supplierRepository, Geocoder geocoder, TransactionTemplate transaction) {
        this.clientRepository = clientRepository;
        this.addressRepository = addressRepository;
        this.supplierRepository = supplierRepository;
        this.geocoder = geocoder;
        this.transaction = transaction;
    }

    private record Pending(UUID id, GeocodingRequest request) {
        String query() {
            return request != null ? request.query() : null;
        }
    }

    private interface Recorder {
        int record(UUID id, String query, Double latitude, Double longitude, Boolean approximate, Instant at);
    }

    @Scheduled(fixedDelayString = "${thyrsio.geocoding.interval:PT1M}", initialDelayString = "PT5S")
    public void run() {
        int located = runOnce();
        if (located > 0) {
            log.info("Géocodage : {} adresse(s) traitée(s)", located);
        }
    }

    /**
     * Une adresse vient d'être enregistrée : un passage dès la fin de la transaction,
     * sans attendre le prochain passage planifié.
     */
    public void requestRun() {
        Runnable submit = () -> {
            if (queued.compareAndSet(false, true)) {
                onDemand.execute(() -> {
                    queued.set(false);
                    run();
                });
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    @Override
    public void close() {
        onDemand.shutdownNow();
    }

    /** Un passage : renvoie le nombre d'adresses traitées (trouvées ou introuvables). */
    public synchronized int runOnce() {
        try {
            int done = process(transaction.execute(status -> clientRepository
                    .findTop20ByGeocodingGeocodedAtIsNullOrderByCreatedAtDesc().stream()
                    .map(client -> new Pending(client.getId(), client.geocodingRequest()))
                    .toList()), clientRepository::recordLocation);
            done += process(transaction.execute(status -> addressRepository
                    .findTop20ByGeocodingGeocodedAtIsNullOrderByCreatedAtDesc().stream()
                    .map(address -> new Pending(address.getId(), address.geocodingRequest()))
                    .toList()), addressRepository::recordLocation);
            done += process(transaction.execute(status -> supplierRepository
                    .findTop20ByGeocodingGeocodedAtIsNullOrderByCreatedAtDesc().stream()
                    .map(supplier -> new Pending(supplier.getId(), supplier.geocodingRequest()))
                    .toList()), supplierRepository::recordLocation);
            return done;
        } catch (Geocoder.GeocodingUnavailableException e) {
            // Pas de réseau, service saturé... : rien n'est marqué, on réessaiera au prochain passage
            log.warn("Géocodage reporté : {}", e.getMessage());
            return 0;
        }
    }

    private int process(List<Pending> pending, Recorder recorder) {
        if (!pending.isEmpty()) {
            log.debug("Géocodage : {} adresse(s) à localiser", pending.size());
        }
        int done = 0;
        for (Pending item : pending) {
            Optional<GeoLocation> location = item.request() != null
                    ? geocoder.locate(item.request())
                    : Optional.empty(); // pas de ville : rien à chercher
            done += transaction.execute(status -> recorder.record(item.id(), item.query(),
                    location.map(GeoLocation::latitude).orElse(null),
                    location.map(GeoLocation::longitude).orElse(null),
                    location.map(GeoLocation::approximate).orElse(null),
                    Instant.now()));
        }
        return done;
    }
}
