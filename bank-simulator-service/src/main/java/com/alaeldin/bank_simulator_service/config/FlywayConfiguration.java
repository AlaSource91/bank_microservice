package com.alaeldin.bank_simulator_service.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Flyway Configuration - Ensures database migrations run before Hibernate
 *
 * This configuration explicitly creates and runs Flyway migrations
 * BEFORE Spring initializes JPA/Hibernate, preventing Hibernate from
 * attempting to create tables.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(Flyway.class)
public class FlywayConfiguration {

    /**
     * Create and execute Flyway bean explicitly
     * This ensures migrations run BEFORE Hibernate initialization
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .outOfOrder(false)
                .validateOnMigrate(false)
                .cleanDisabled(true)
                .load();

        // Execute migrations immediately
        flyway.migrate();

        return flyway;
    }
}







