package com.pierr.thyrsio_api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    // Démarre un Postgres 18 dans Docker pour les tests.
    // @ServiceConnection branche automatiquement la datasource Spring dessus,
    // à la place de la base de développement.
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgreSQLContainer() {
        return new PostgreSQLContainer("postgres:18");
    }
}
