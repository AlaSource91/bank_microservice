package com.alaeldin.Auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies that the Spring application context loads without errors.
 *
 * <p>{@code @TestPropertySource} overrides two settings that require live infrastructure:
 * <ul>
 *   <li>{@code hibernate.dialect} — prevents Hibernate 7 from attempting a JDBC connection
 *       just to auto-detect the dialect (same reason as in {@code application.yaml}).</li>
 *   <li>{@code spring.flyway.enabled=false} — skips schema migration so the test does not
 *       require a real MySQL database to be running.</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
    "spring.flyway.enabled=false"
})
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}


