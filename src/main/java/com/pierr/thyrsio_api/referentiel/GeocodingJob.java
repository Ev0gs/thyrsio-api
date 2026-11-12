package com.pierr.thyrsio_api.referentiel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La tâche de fond qui place les adresses sur la carte : à chaque passage, elle prend les adresses
 * jamais localisées (ou modifiées depuis) et les cherche une par une, au rythme imposé par le géocodeur.
 *
 * Rien n'est verrouillé pendant la recherche : le résultat n'est enregistré que si l'adresse
 * n'a pas changé entre-temps (même requête), sinon il sera recalculé au passage suivant.
 */
public class GeocodingJob {

    private static final Logger log = LoggerFactory.getLogger(GeocodingJob.class);
    static final int BATCH_SIZE = 20;

    private final ClientRepository clientRepository;
    private final ClientDeliveryAddressRepository addressRepository;
    private final SupplierRepository supplierRepository;
    private final Geocoder geocoder;
    private final TransactionTemplate transaction;

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

    @Scheduled(fixedDelayString = "${thyrsio.geocoding.interval:PT1M}", initialDelayString = "PT15S")
    public void run() {
        int located = runOnce();
        if (located > 0) {
            log.info("Géocodage : {} adresse(s) traitée(s)", located);
        }
    }

    /** Un passage : renvoie le nombre d'adresses traitées (trouvées ou introuvables). */
    public int runOnce() {
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
