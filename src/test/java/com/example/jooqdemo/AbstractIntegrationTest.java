package com.example.jooqdemo;

import com.example.jooqdemo.service.LibraryService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

/**
 * Shared base for all jOOQ demo integration tests.
 *
 * <p>Starts a real MariaDB instance via Testcontainers ({@link TestContainerConfig}).
 * {@code @ServiceConnection} in the config class automatically wires the container's
 * URL/user/password into the Spring datasource. Flyway is run manually before each test
 * (clean + migrate) to guarantee a known starting state.
 *
 * <p>The MariaDB container is started with {@code allowMultiQueries=true} to support
 * jOOQ's MULTISET emulation on MariaDB.
 */
@SpringBootTest(properties = "spring.flyway.enabled=false")
@Import(TestContainerConfig.class)
abstract class AbstractIntegrationTest {

    @Autowired
    protected LibraryService libraryService;

    @Autowired
    protected DataSource dataSource;

    @BeforeEach
    void resetSchema() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
