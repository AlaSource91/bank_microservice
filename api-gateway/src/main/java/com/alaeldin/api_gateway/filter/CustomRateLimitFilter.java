package com.alaeldin.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Custom Rate Limiting Filter using Redis with Token Bucket algorithm.
 * 
 * <p>This filter implements the Token Bucket algorithm for distributed rate limiting:
 * <ul>
 *   <li><b>Token Bucket Algorithm:</b> Allows burst traffic while maintaining average rate</li>
 *   <li><b>Refill Rate:</b> Tokens are added at a constant rate (tokens/second)</li>
 *   <li><b>Burst Capacity:</b> Maximum tokens that can be stored</li>
 *   <li><b>Request Cost:</b> Each request consumes tokens</li>
 * </ul>
 * 
 * <p><b>Features:</b>
 * <ul>
 *   <li>Distributed rate limiting using Redis</li>
 *   <li>Token bucket algorithm with configurable refill rate</li>
 *   <li>Burst capacity support for traffic spikes</li>
 *   <li>Atomic operations using Lua scripts</li>
 *   <li>User-based or IP-based key resolution</li>
 * </ul>
 * 
 * <p><b>Example:</b> refillRate=10, burstCapacity=20
 * <ul>
 *   <li>Allows up to 20 requests instantly (burst)</li>
 *   <li>Refills at 10 tokens/second</li>
 *   <li>Sustained rate: 10 requests/second</li>
 * </ul>
 * 
 * @author Alaeldin
 * @version 3.0 - Token Bucket Algorithm
 */
@Slf4j
@Component
public class CustomRateLimitFilter extends AbstractGatewayFilterFactory<CustomRateLimitFilter.Config> {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:token_bucket:";
    private static final String TOKENS_KEY_SUFFIX = ":tokens";
    private static final String TIMESTAMP_KEY_SUFFIX = ":timestamp";

    private final ReactiveStringRedisTemplate redisTemplate;

    // Lua script for atomic token bucket operations
    private static final String TOKEN_BUCKET_LUA_SCRIPT = 
        "local tokens_key = KEYS[1]\n" +
        "local timestamp_key = KEYS[2]\n" +
        "local rate = tonumber(ARGV[1])\n" +
        "local capacity = tonumber(ARGV[2])\n" +
        "local now = tonumber(ARGV[3])\n" +
        "local requested = tonumber(ARGV[4])\n" +
        "local ttl = tonumber(ARGV[5])\n" +
        "\n" +
        "local tokens = tonumber(redis.call('get', tokens_key))\n" +
        "if tokens == nil then\n" +
        "  tokens = capacity\n" +
        "end\n" +
        "\n" +
        "local last_refreshed = tonumber(redis.call('get', timestamp_key))\n" +
        "if last_refreshed == nil then\n" +
        "  last_refreshed = now\n" +
        "end\n" +
        "\n" +
        "local delta = math.max(0, now - last_refreshed)\n" +
        "local filled_tokens = math.min(capacity, tokens + (delta * rate))\n" +
        "local allowed = filled_tokens >= requested\n" +
        "local new_tokens = filled_tokens\n" +
        "\n" +
        "if allowed then\n" +
        "  new_tokens = filled_tokens - requested\n" +
        "end\n" +
        "\n" +
        "redis.call('setex', tokens_key, ttl, new_tokens)\n" +
        "redis.call('setex', timestamp_key, ttl, now)\n" +
        "\n" +
        "return { allowed, new_tokens }";

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> tokenBucketScript;

    @Autowired
    public CustomRateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = RedisScript.of(TOKEN_BUCKET_LUA_SCRIPT, List.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Get key for rate limiting
            String key = resolveKey(exchange);
            String baseKey = RATE_LIMIT_KEY_PREFIX + key;

            log.debug("Rate limiting check for key: {} (refillRate={}, burstCapacity={})",
                key, config.getRefillRate(), config.getBurstCapacity());

            // Apply token bucket algorithm
            return allowRequest(baseKey, config)
                .flatMap(result -> {
                    boolean allowed = result.isAllowed();
                    double remainingTokens = result.getTokensRemaining();

                    // Add rate limit headers
                    exchange.getResponse().getHeaders().add("X-RateLimit-Replenish-Rate",
                        String.valueOf(config.getRefillRate()));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Burst-Capacity",
                        String.valueOf(config.getBurstCapacity()));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",
                        String.valueOf((int) Math.floor(remainingTokens)));

                    if (!allowed) {
                        log.warn("Rate limit exceeded for key: {} (tokens remaining: {})",
                            key, remainingTokens);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After-Seconds",
                            String.valueOf(calculateRetryAfter(config.getRefillRate())));

                        return exchange.getResponse().setComplete();
                    }

                    log.debug("Request allowed for key: {} (tokens remaining: {})",
                        key, remainingTokens);
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> {
                    // If Redis is down, log error and allow request (fail-open strategy)
                    log.error("Redis error in rate limiting for key: {}. Allowing request. Error: {}",
                        key, ex.getMessage());
                    return chain.filter(exchange);
                });
        };
    }

    /**
     * Resolves the key to use for rate limiting.
     */
    private String resolveKey(org.springframework.web.server.ServerWebExchange exchange) {
        // Try user ID first (set by JwtAuthenticationFilter)
        String userId = exchange.getAttribute("userId");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }
        
        // Fallback to IP address
        String ipAddress = Objects.requireNonNull(
            exchange.getRequest().getRemoteAddress()
        ).getAddress().getHostAddress();
        
        return "ip:" + ipAddress;
    }

    /**
     * Applies Token Bucket algorithm to check if request should be allowed.
     * Uses Redis Lua script for atomic operations.
     *
     * @param baseKey Redis key prefix for this rate limiter
     * @param config Rate limiter configuration
     * @return RateLimitResult indicating if request is allowed
     */
    private Mono<RateLimitResult> allowRequest(String baseKey, Config config) {
        String tokensKey = baseKey + TOKENS_KEY_SUFFIX;
        String timestampKey = baseKey + TIMESTAMP_KEY_SUFFIX;

        // Calculate rate in tokens per second
        double rate = config.getRefillRate();
        int capacity = config.getBurstCapacity();
        int requested = config.getRequestedTokens();
        long now = Instant.now().getEpochSecond();
        int ttl = config.getTtl();

        List<String> keys = Arrays.asList(tokensKey, timestampKey);
        List<String> args = Arrays.asList(
            String.valueOf(rate),
            String.valueOf(capacity),
            String.valueOf(now),
            String.valueOf(requested),
            String.valueOf(ttl)
        );

        return redisTemplate.execute(tokenBucketScript, keys, args)
            .next() // Convert Flux to Mono
            .map(response -> {
                @SuppressWarnings("unchecked")
                List<Long> result = (List<Long>) response;
                boolean allowed = result.get(0) == 1L;
                double tokensRemaining = result.get(1).doubleValue();

                return new RateLimitResult(allowed, tokensRemaining);
            })
            .defaultIfEmpty(new RateLimitResult(true, capacity))
            .onErrorResume(ex -> {
                log.error("Error executing token bucket script: {}", ex.getMessage());
                return Mono.just(new RateLimitResult(true, capacity)); // Fail-open
            });
    }

    /**
     * Calculates retry-after seconds based on refill rate.
     */
    private int calculateRetryAfter(double refillRate) {
        if (refillRate <= 0) {
            return 1;
        }
        // Time to refill 1 token
        return (int) Math.ceil(1.0 / refillRate);
    }

    /**
     * Result of rate limit check.
     */
    private static class RateLimitResult {
        private final boolean allowed;
        private final double tokensRemaining;

        public RateLimitResult(boolean allowed, double tokensRemaining) {
            this.allowed = allowed;
            this.tokensRemaining = tokensRemaining;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public double getTokensRemaining() {
            return tokensRemaining;
        }
    }

    /**
     * Configuration class for Token Bucket rate limiter.
     *
     * <p><b>Token Bucket Parameters:</b>
     * <ul>
     *   <li><b>refillRate:</b> Tokens added per second (sustained rate)</li>
     *   <li><b>burstCapacity:</b> Maximum tokens in bucket (allows bursts)</li>
     *   <li><b>requestedTokens:</b> Tokens consumed per request</li>
     *   <li><b>ttl:</b> Time-to-live for Redis keys in seconds</li>
     * </ul>
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>refillRate=10, burstCapacity=20: 10 req/sec sustained, 20 req burst</li>
     *   <li>refillRate=1, burstCapacity=5: 1 req/sec sustained, 5 req burst</li>
     *   <li>refillRate=100, burstCapacity=100: 100 req/sec, no burst</li>
     * </ul>
     */
    public static class Config {
        /**
         * Rate at which tokens are added to the bucket (tokens per second).
         * This is the sustained rate limit.
         * Default: 10 tokens/second
         */
        private double refillRate = 10.0;

        /**
         * Maximum number of tokens the bucket can hold.
         * This allows for burst traffic.
         * Default: 20 tokens
         */
        private int burstCapacity = 20;

        /**
         * Number of tokens consumed per request.
         * Default: 1 token
         */
        private int requestedTokens = 1;

        /**
         * Time-to-live for Redis keys in seconds.
         * Keys will expire after this duration of inactivity.
         * Default: 60 seconds
         */
        private int ttl = 60;

        // Getters and Setters

        public double getRefillRate() {
            return refillRate;
        }

        public void setRefillRate(double refillRate) {
            if (refillRate <= 0) {
                throw new IllegalArgumentException("Refill rate must be positive");
            }
            this.refillRate = refillRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            if (burstCapacity < requestedTokens) {
                throw new IllegalArgumentException("Burst capacity must be >= requested tokens");
            }
            this.burstCapacity = burstCapacity;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public void setRequestedTokens(int requestedTokens) {
            if (requestedTokens <= 0) {
                throw new IllegalArgumentException("Requested tokens must be positive");
            }
            this.requestedTokens = requestedTokens;
        }

        public int getTtl() {
            return ttl;
        }

        public void setTtl(int ttl) {
            if (ttl <= 0) {
                throw new IllegalArgumentException("TTL must be positive");
            }
            this.ttl = ttl;
        }
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("refillRate", "burstCapacity", "requestedTokens");
    }
}

