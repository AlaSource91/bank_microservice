package com.alaeldin.api_gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Rate Limiter Configuration for API Gateway.
 * 
 * <p>Provides different key resolution strategies for rate limiting:
 * <ul>
 *   <li>User-based: Rate limit by authenticated user</li>
 *   <li>IP-based: Rate limit by client IP address</li>
 *   <li>Path-based: Rate limit by request path</li>
 * </ul>
 * 
 * @author Alaeldin
 * @version 1.0
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    /**
     * Primary key resolver - uses user ID from JWT if available, 
     * falls back to IP address for unauthenticated requests.
     */
    @Primary
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Try to get user ID from JWT claims (set by JwtAuthenticationFilter)
            String userId = exchange.getAttribute("userId");
            
            if (userId != null && !userId.isEmpty()) {
                log.debug("Rate limiting by userId: {}", userId);
                return Mono.just(userId);
            }
            
            // Fallback to IP address for unauthenticated requests
            String ipAddress = Objects.requireNonNull(
                exchange.getRequest().getRemoteAddress()
            ).getAddress().getHostAddress();
            
            log.debug("Rate limiting by IP: {}", ipAddress);
            return Mono.just(ipAddress);
        };
    }

    /**
     * IP-based key resolver - uses client IP address.
     * Useful for public endpoints without authentication.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ipAddress = Objects.requireNonNull(
                exchange.getRequest().getRemoteAddress()
            ).getAddress().getHostAddress();
            
            log.debug("IP-based rate limiting: {}", ipAddress);
            return Mono.just(ipAddress);
        };
    }

    /**
     * Path-based key resolver - uses request path.
     * Useful for global rate limiting on specific endpoints.
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            log.debug("Path-based rate limiting: {}", path);
            return Mono.just(path);
        };
    }

    /**
     * API key resolver - uses API key from header if present.
     * Useful for third-party API consumers.
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            
            if (apiKey != null && !apiKey.isEmpty()) {
                log.debug("Rate limiting by API key: {}", apiKey);
                return Mono.just(apiKey);
            }
            
            // Fallback to IP address
            String ipAddress = Objects.requireNonNull(
                exchange.getRequest().getRemoteAddress()
            ).getAddress().getHostAddress();
            
            log.debug("Rate limiting by IP (no API key): {}", ipAddress);
            return Mono.just(ipAddress);
        };
    }

    /**
     * Combined key resolver - uses user ID + path for granular control.
     */
    @Bean
    public KeyResolver combinedKeyResolver() {
        return exchange -> {
            String userId = exchange.getAttribute("userId");
            String path = exchange.getRequest().getPath().value();
            
            String key = (userId != null ? userId : "anonymous") + ":" + path;
            log.debug("Combined rate limiting key: {}", key);
            return Mono.just(key);
        };
    }
}

