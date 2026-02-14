package com.alaeldin.bank_simulator_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bank Simulator Service Application
 * Provides bank account and transaction management APIs with outbox pattern support
 *
 * Features:
 * - Bank account management with optimistic locking
 * - Transaction processing with idempotency guarantees
 * - Outbox pattern for reliable event publishing
 * - Redis-based caching and deduplication
 * - Scheduled background tasks for event publishing
 * - Automatic database migrations via Flyway
 *
 * Migration Notes:
 * - Flyway handles database schema migrations automatically on startup
 * - If you encounter "Duplicate column name" errors, it usually means:
 *   1. A migration failed partially and left the database in an inconsistent state
 *   2. You may need to run: mvn flyway:repair or manually clean the failed migration
 *   3. For outbox_events table issues, you can run the repair script in project root
 *
 * Annotations:
 * @EnableCaching      - Enables Spring Cache abstraction for Redis caching
 * @EnableRetry        - Enables retry capabilities for failed operations
 * @EnableScheduling   - Enables scheduled tasks (OutboxPublisher background processing)
 *
 * Note: @EnableDiscoveryClient removed as Eureka is disabled in application.yml
 */

@EnableCaching
@EnableRetry
@EnableScheduling  // Added for scheduled OutboxPublisher tasks
@SpringBootApplication
public class BankSimulatorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankSimulatorServiceApplication.class, args);
	}

}
