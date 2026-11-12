package com.pierr.thyrsio_api.referentiel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** Le géocodage tourne seulement si thyrsio.geocoding.enabled=true (profil dev, production). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "thyrsio.geocoding", name = "enabled", havingValue = "true")
@EnableScheduling
class GeocodingConfig {

    @Bean
    @ConditionalOnMissingBean(Geocoder.class)
    Geocoder nominatimGeocoder(GeocodingProperties properties) {
        return new NominatimGeocoder(RestClient.builder(), properties);
    }

    @Bean
    GeocodingJob geocodingJob(ClientRepository clientRepository, ClientDeliveryAddressRepository addressRepository,
                              SupplierRepository supplierRepository, Geocoder geocoder,
                              PlatformTransactionManager transactionManager) {
        return new GeocodingJob(clientRepository, addressRepository, supplierRepository, geocoder,
                new TransactionTemplate(transactionManager));
    }
}
