package com.alaeldin.api_gateway.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limit Management Controller.
 * 
 * <p>Provides endpoints to monitor and manage rate limits for the API Gateway.</p>
 * 
 * <p><b>Security:</b> These endpoints should be restricted to admins only in production.</p>
 * 
 * @author Alaeldin
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gateway/rate-limit")
public class RateLimitController {

    @Autowired(required = false)
    private ReactiveStringRedisTemplate redisTemplate;

    /**
     * Get rate limit status for a specific key.
     * 
     * @param key the rate limit key (user ID or IP)
     * @return rate limit status
     */
    @GetMapping("/status/{key}")
    public Mono<ResponseEntity<RateLimitStatus>> getRateLimitStatus(@PathVariable String key) {
        if (redisTemplate == null) {
            return Mono.just(ResponseEntity.ok(
                new RateLimitStatus(key, 0, 0, "Redis not configured")
            ));
        }

        String redisKey = "request_rate_limiter.{" + key + "}.tokens";
        
        return redisTemplate.opsForValue()
            .get(redisKey)
            .map(tokens -> {
                try {
                    int remaining = Integer.parseInt(tokens);
                    return ResponseEntity.ok(
                        new RateLimitStatus(key, remaining, 0, "Active")
                    );
                } catch (NumberFormatException e) {
                    return ResponseEntity.ok(
                        new RateLimitStatus(key, 0, 0, "Invalid data")
                    );
                }
            })
            .defaultIfEmpty(ResponseEntity.ok(
                new RateLimitStatus(key, 0, 0, "No data found")
            ))
            .onErrorResume(ex -> {
                log.error("Error fetching rate limit status for key: {}", key, ex);
                return Mono.just(ResponseEntity.ok(
                    new RateLimitStatus(key, 0, 0, "Error: " + ex.getMessage())
                ));
            });
    }

    /**
     * Reset rate limit for a specific key (admin only).
     * 
     * @param key the rate limit key to reset
     * @return success message
     */
    @DeleteMapping("/reset/{key}")
    public Mono<ResponseEntity<Map<String, String>>> resetRateLimit(@PathVariable String key) {
        if (redisTemplate == null) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Redis not configured");
            return Mono.just(ResponseEntity.badRequest().body(response));
        }

        String redisKey = "request_rate_limiter.{" + key + "}.*";
        
        log.info("Resetting rate limit for key: {}", key);
        
        // Delete the rate limit key from Redis
        return redisTemplate.delete(redisKey)
            .map(count -> {
                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Rate limit reset for key: " + key);
                response.put("keysDeleted", String.valueOf(count));
                return ResponseEntity.ok(response);
            })
            .onErrorResume(ex -> {
                log.error("Error resetting rate limit for key: {}", key, ex);
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", ex.getMessage());
                return Mono.just(ResponseEntity.internalServerError().body(response));
            });
    }

    /**
     * Get overall rate limiting configuration and statistics.
     * 
     * @return rate limiting info
     */
    @GetMapping("/info")
    public Mono<ResponseEntity<RateLimitInfo>> getRateLimitInfo() {
        RateLimitInfo info = new RateLimitInfo();
        info.setEnabled(redisTemplate != null);
        info.setType(redisTemplate != null ? "Redis (Distributed)" : "In-Memory (Single Instance)");
        
        Map<String, RateLimitConfig> configs = new HashMap<>();
        configs.put("auth-service", new RateLimitConfig(10, 20));
        configs.put("bank-query-service", new RateLimitConfig(20, 40));
        configs.put("bank-simulator-service", new RateLimitConfig(15, 30));
        configs.put("admin-service", new RateLimitConfig(5, 10));
        
        info.setConfigs(configs);
        
        return Mono.just(ResponseEntity.ok(info));
    }

    /**
     * Health check for Redis connection.
     * 
     * @return health status
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> checkRedisHealth() {
        if (redisTemplate == null) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Redis template not configured");
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
        }

        return redisTemplate.execute(connection -> connection.ping())
            .next()
            .map(pong -> {
                Map<String, String> response = new HashMap<>();
                response.put("status", "healthy");
                response.put("message", "Redis connection active");
                response.put("response", pong);
                return ResponseEntity.ok(response);
            })
            .onErrorResume(ex -> {
                Map<String, String> response = new HashMap<>();
                response.put("status", "unhealthy");
                response.put("message", ex.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
            });
    }

    // ═════════════════ DTOs ═════════════════

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitStatus {
        private String key;
        private int remainingTokens;
        private long resetTimeSeconds;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitInfo {
        private boolean enabled;
        private String type;
        private Map<String, RateLimitConfig> configs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitConfig {
        private int replenishRate;
        private int burstCapacity;
    }
}

