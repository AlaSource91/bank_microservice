package com.alaeldin.api_gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Redis Rate Limiter Bean Configuration.
 * 
 * <p>Configures Redis-based rate limiting for distributed rate limiting
 * across multiple gateway instances.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Token bucket algorithm for smooth rate limiting</li>
 *   <li>Configurable replenish rate and burst capacity per route</li>
 *   <li>Redis for distributed state management</li>
 * </ul>
 * 
 * @author Alaeldin
 * @version 1.0
 */
@Slf4j
@Configuration
public class RedisRateLimiterBeanConfig {

    /**
     * Default rate limiter for general API requests.
     * 100 requests per second with burst capacity of 200.
     */
    @Bean
    @Primary
    public RedisRateLimiter defaultRateLimiter() {
        log.info("Configuring default Redis rate limiter: 100 req/s, burst: 200");
        return new RedisRateLimiter(100, 200, 1);
    }

    /**
     * Strict rate limiter for sensitive operations (admin, transfers).
     * 10 requests per second with burst capacity of 20.
     */
    @Bean
    public RedisRateLimiter strictRateLimiter() {
        log.info("Configuring strict Redis rate limiter: 10 req/s, burst: 20");
        return new RedisRateLimiter(10, 20, 1);
    }

    /**
     * Relaxed rate limiter for public read-only endpoints.
     * 200 requests per second with burst capacity of 400.
     */
    @Bean
    public RedisRateLimiter relaxedRateLimiter() {
        log.info("Configuring relaxed Redis rate limiter: 200 req/s, burst: 400");
        return new RedisRateLimiter(200, 400, 1);
    }
}

