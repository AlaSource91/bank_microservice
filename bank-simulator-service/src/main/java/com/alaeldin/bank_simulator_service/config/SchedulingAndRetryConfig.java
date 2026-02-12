package com.alaeldin.bank_simulator_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class for enabling scheduling and retry mechanisms.
 *
 * <p>Enabled Features:</p>
 * <ul>
 *   <li>@EnableScheduling - Enables @Scheduled annotation support for periodic tasks</li>
 *   <li>@EnableRetry - Enables @Retryable annotation support for automatic retries</li>
 * </ul>
 *
 * @see org.springframework.scheduling.annotation.Scheduled
 * @see org.springframework.retry.annotation.Retryable
 */
@Configuration
@EnableScheduling
@EnableRetry
public class SchedulingAndRetryConfig {
    // Configuration class - no additional beans needed
    // @EnableScheduling and @EnableRetry annotations activate the respective features
}
