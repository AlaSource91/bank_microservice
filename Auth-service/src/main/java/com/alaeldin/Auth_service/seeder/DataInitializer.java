package com.alaeldin.Auth_service.seeder;

import com.alaeldin.Auth_service.repository.PermissionRepository;
import com.alaeldin.Auth_service.repository.ResourceRepository;
import com.alaeldin.Auth_service.repository.RoleRepository;
import com.alaeldin.Auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup verification runner that checks whether Flyway migrations have
 * seeded the expected reference data into the database.
 *
 * <p>Runs once after the application context is fully started. It does
 * <strong>not</strong> insert or modify any data — that is Flyway's
 * responsibility. This class only reads counts and logs warnings when
 * expected rows are missing so that misconfigured deployments are caught
 * immediately on startup rather than silently failing at runtime.</p>
 *
 * <p>Expected Flyway migrations:</p>
 * <ul>
 *   <li>V7 — seed {@code resources} table</li>
 *   <li>V8 — seed {@code roles} and {@code role_permissions} tables</li>
 *   <li>V9 — seed {@code permissions} table</li>
 *   <li>V10 — create default {@code admin} user</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ResourceRepository   resourceRepository;
    private final RoleRepository       roleRepository;
    private final UserRepository       userRepository;
    private final PermissionRepository permissionRepository;

    // ─────────────────────────────────────────────────────────────
    //  Startup check
    // ─────────────────────────────────────────────────────────────

    /**
     * Reads row counts from every reference-data table and logs a warning
     * for each table that appears empty.
     *
     * <p>All queries run in a single read-only transaction so only one
     * database connection is borrowed from the pool.</p>
     *
     * @param args application arguments (not used)
     */
    @Override
    @Transactional(readOnly = true)
    public void run(@NonNull ApplicationArguments args) {
        try {
            long resourceCount   = resourceRepository.count();
            long roleCount       = roleRepository.count();
            long permissionCount = permissionRepository.count();
            long userCount       = userRepository.count();

            log.info("[DataInitializer] ─────────────────────────────────────────");
            log.info("[DataInitializer] Auth Service startup verification:");
            log.info("[DataInitializer]   Resources loaded   : {}", resourceCount);
            log.info("[DataInitializer]   Roles loaded       : {}", roleCount);
            log.info("[DataInitializer]   Permissions loaded : {}", permissionCount);
            log.info("[DataInitializer]   Users registered   : {}", userCount);
            log.info("[DataInitializer] ─────────────────────────────────────────");

            if (resourceCount == 0) {
                log.warn("[DataInitializer] NO RESOURCES FOUND — Flyway V7 migration may have failed!");
            }
            if (roleCount == 0) {
                log.warn("[DataInitializer] NO ROLES FOUND — Flyway V8 migration may have failed!");
            }
            if (permissionCount == 0) {
                log.warn("[DataInitializer] NO PERMISSIONS FOUND — Flyway V9 migration may have failed!");
            }
            if (!userRepository.existsByEmail("admin@bank.com")) {
                log.warn("[DataInitializer] Admin user not found — Flyway V10 migration may have failed!");
            } else {
                log.info("[DataInitializer] Default admin user verified OK");
            }

        } catch (Exception ex) {
            log.error("[DataInitializer] Startup verification failed — check database connectivity: {}",
                    ex.getMessage(), ex);
        }
    }
}
