package com.example.jooqdemo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerConfig {

    private static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>("mariadb:11")
                    .withDatabaseName("jooq_demo")
                    .withUrlParam("allowMultiQueries", "true")
                    .withReuse(false);

    static {
        MARIADB.start();
    }

    @Bean
    @ServiceConnection
    public MariaDBContainer<?> mariaDBContainer() {
        return MARIADB;
    }
}
