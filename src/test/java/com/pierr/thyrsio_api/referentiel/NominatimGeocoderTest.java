package com.pierr.thyrsio_api.referentiel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class NominatimGeocoderTest {

    private MockRestServiceServer server;
    private NominatimGeocoder geocoder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        geocoder = new NominatimGeocoder(builder,
                new GeocodingProperties(true, "https://nominatim.test", "Thyrsio test", Duration.ofMinutes(1)));
    }

    @Test
    void finds_the_full_address_restricted_to_its_country() {
        server.expect(requestTo(startsWith("https://nominatim.test/search")))
                .andExpect(queryParam("q", "10%20quai%20des%20Chartrons%2C%2033000%20Bordeaux"))
                .andExpect(queryParam("countrycodes", "fr"))
                .andExpect(header(HttpHeaders.USER_AGENT, "Thyrsio test"))
                .andRespond(withSuccess("""
                        [{"place_id": 1, "lat": "44.8530", "lon": "-0.5700", "display_name": "Quai des Chartrons"}]
                        """, MediaType.APPLICATION_JSON));

        var location = geocoder.locate(
                GeocodingRequest.structured("10 quai des Chartrons", "33000", "Bordeaux", "FR"));

        assertThat(location).contains(new GeoLocation(44.853, -0.57, false));
        server.verify();
    }

    @Test
    void falls_back_to_the_town_when_the_street_is_unknown() {
        server.expect(requestTo(startsWith("https://nominatim.test/search")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://nominatim.test/search")))
                .andExpect(queryParam("q", "1000%20Bruxelles"))
                .andRespond(withSuccess("""
                        [{"lat": "50.8467", "lon": "4.3525"}]
                        """, MediaType.APPLICATION_JSON));

        var location = geocoder.locate(
                GeocodingRequest.structured("Rue Imaginaire 999", "1000", "Bruxelles", "BE"));

        assertThat(location).contains(new GeoLocation(50.8467, 4.3525, true));
        server.verify();
    }

    @Test
    void reports_an_unavailable_service_instead_of_an_unknown_address() {
        server.expect(requestTo(startsWith("https://nominatim.test/search")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> geocoder.locate(GeocodingRequest.freeText("Lieu-dit Les Graves, 33210 Langon")))
                .isInstanceOf(Geocoder.GeocodingUnavailableException.class);
    }
}
