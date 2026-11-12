package com.pierr.thyrsio_api.referentiel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Le géocodage par Nominatim, le service d'OpenStreetMap. Gratuit, sans clé, mais sa charte impose
 * au plus une requête par seconde et un User-Agent qui identifie l'application :
 * https://operations.osmfoundation.org/policies/nominatim/
 *
 * Une adresse est cherchée complète, puis, si elle est introuvable, réduite au code postal et à la ville
 * (position approximative), puis à la ville seule.
 */
public class NominatimGeocoder implements Geocoder {

    private static final long MIN_INTERVAL_MS = 1100;

    private final RestClient restClient;
    private long lastCallAt;

    public NominatimGeocoder(RestClient.Builder builder, GeocodingProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                .build();
    }

    @Override
    public synchronized Optional<GeoLocation> locate(GeocodingRequest request) {
        for (Attempt attempt : attempts(request)) {
            List<Place> places = search(attempt);
            if (places != null && !places.isEmpty()) {
                Place place = places.getFirst();
                return Optional.of(new GeoLocation(Double.parseDouble(place.lat()), Double.parseDouble(place.lon()),
                        attempt.approximate()));
            }
        }
        return Optional.empty();
    }

    private record Attempt(String query, String countryCode, boolean approximate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String lat, String lon) {
    }

    static List<Attempt> attempts(GeocodingRequest request) {
        if (request.freeText() != null) {
            return List.of(new Attempt(request.freeText(), null, false));
        }
        String country = request.countryCode() != null ? request.countryCode().toLowerCase(Locale.ROOT) : null;
        String town = join(request.postalCode(), request.city());
        List<Attempt> attempts = new ArrayList<>();
        if (request.street() != null) {
            attempts.add(new Attempt(request.street() + ", " + town, country, false));
        }
        attempts.add(new Attempt(town, country, true));
        if (request.postalCode() != null) {
            attempts.add(new Attempt(request.city(), country, true));
        }
        return attempts;
    }

    private List<Place> search(Attempt attempt) {
        throttle();
        Map<String, Object> variables = new HashMap<>();
        variables.put("q", attempt.query());
        variables.put("cc", attempt.countryCode());
        try {
            return restClient.get()
                    .uri(builder -> {
                        builder.path("/search")
                                .queryParam("q", "{q}")
                                .queryParam("format", "jsonv2")
                                .queryParam("limit", 1);
                        if (attempt.countryCode() != null) {
                            builder.queryParam("countrycodes", "{cc}");
                        }
                        return builder.build(variables);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Place>>() {
                    });
        } catch (RestClientResponseException e) {
            // 403 : User-Agent refusé ou adresse IP bloquée ; 429 : trop de requêtes
            throw new GeocodingUnavailableException("Nominatim refuse la requête (HTTP "
                    + e.getStatusCode().value() + ") : " + e.getResponseBodyAsString().lines().findFirst().orElse(""), e);
        } catch (RestClientException e) {
            throw new GeocodingUnavailableException("Nominatim injoignable : " + e.getMessage(), e);
        }
    }

    /** Au plus une requête par seconde, comme l'exige la charte de Nominatim. */
    private void throttle() {
        long wait = lastCallAt + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GeocodingUnavailableException("Géocodage interrompu", e);
            }
        }
        lastCallAt = System.currentTimeMillis();
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
    }
}
