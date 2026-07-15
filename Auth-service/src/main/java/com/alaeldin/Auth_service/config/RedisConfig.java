package com.alaeldin.Auth_service.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * Redis configuration for the Auth Service.
 *
 * <p>Provides two beans:</p>
 * <ul>
 *   <li>{@link RedisTemplate} — low-level Redis operations (token blacklist, permission
 *       cache invalidation) using {@link StringRedisSerializer} for both keys and values.</li>
 *   <li>{@link CacheManager} — drives Spring's {@code @Cacheable} / {@code @CacheEvict}
 *       annotations with per-cache TTL tuning and a custom Jackson serialiser that handles
 *       Java time types and polymorphic objects correctly.</li>
 * </ul>
 *
 * <p>Key namespaces used at runtime:</p>
 * <pre>
 *   auth:blacklist:{jti}       — blacklisted JWT tokens (TTL = remaining token lifetime)
 *   auth:permissions:{userId}  — cached permission list  (TTL = 5 min)
 * </pre>
 *
 * <p>Cache TTL summary:</p>
 * <pre>
 *   registerCache        10 min   – short-lived registration flow data
 *   loginCache            2 hr    – active session / recent login events
 *   passwordChangeCache  10 min   – sensitive; short window is intentional
 *   accountLockCache     30 min   – security-relevant, moderate window
 *   roleChangeCache      10 min   – permission changes, short window
 *   rolesCache           10 min   – role lookups
 *   loginAttemptCache     5 min   – brute-force attempt counter
 *   idempotencyKeyCache  24 hr    – must outlive any reasonable retry window
 *   default               5 min
 * </pre>
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    // ── TTL constants ────────────────────────────────────────────────────────
    private static final Duration TTL_DEFAULT       = Duration.ofMinutes(5);
    private static final Duration TTL_SHORT         = Duration.ofMinutes(10);
    private static final Duration TTL_LOGIN_ATTEMPT = Duration.ofMinutes(5);
    private static final Duration TTL_ACCOUNT_LOCK  = Duration.ofMinutes(30);
    private static final Duration TTL_LOGIN         = Duration.ofHours(2);
    private static final Duration TTL_IDEMPOTENCY   = Duration.ofHours(24);

    // ── Cache name constants ─────────────────────────────────────────────────
    public static final String CACHE_REGISTER         = "registerCache";
    public static final String CACHE_LOGIN            = "loginCache";
    public static final String CACHE_PASSWORD_CHANGE  = "passwordChangeCache";
    public static final String CACHE_ACCOUNT_LOCK     = "accountLockCache";
    public static final String CACHE_ROLE_CHANGE      = "roleChangeCache";
    public static final String CACHE_ROLES            = "rolesCache";
    public static final String CACHE_LOGIN_ATTEMPT    = "loginAttemptCache";
    public static final String CACHE_IDEMPOTENCY_KEY  = "idempotencyKeyCache";

    // ─────────────────────────────────────────────────────────────
    //  Beans
    // ─────────────────────────────────────────────────────────────

    /**
     * Low-level Redis template with String keys and String values.
     * Used for manual Redis operations such as JWT token blacklisting
     * and permission-cache invalidation.
     *
     * @param factory Redis connection factory from Spring Boot auto-configuration
     * @return configured {@link RedisTemplate} with {@link StringRedisSerializer}
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        log.info("[RedisConfig] RedisTemplate configured with StringRedisSerializer");
        return template;
    }

    /**
     * Cache manager with per-cache TTL configuration and Jackson-based serialisation.
     *
     * <p>Uses a custom Jackson serialiser that supports polymorphic types and Java time
     * modules, making it suitable for caching DTOs and domain objects.</p>
     *
     * @param factory Redis connection factory
     * @return configured {@link RedisCacheManager}
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisSerializer<Object> jacksonSerializer = createJacksonSerializer();

        RedisCacheConfiguration defaultConfig      = buildCacheConfig(TTL_DEFAULT,      jacksonSerializer);
        RedisCacheConfiguration registerConfig     = buildCacheConfig(TTL_SHORT,         jacksonSerializer);
        RedisCacheConfiguration loginConfig        = buildCacheConfig(TTL_LOGIN,         jacksonSerializer);
        RedisCacheConfiguration passwordConfig     = buildCacheConfig(TTL_SHORT,         jacksonSerializer);
        RedisCacheConfiguration accountLockConfig  = buildCacheConfig(TTL_ACCOUNT_LOCK,  jacksonSerializer);
        RedisCacheConfiguration roleChangeConfig   = buildCacheConfig(TTL_SHORT,         jacksonSerializer);
        RedisCacheConfiguration rolesConfig        = buildCacheConfig(TTL_SHORT,         jacksonSerializer);
        RedisCacheConfiguration loginAttemptConfig = buildCacheConfig(TTL_LOGIN_ATTEMPT, jacksonSerializer);
        RedisCacheConfiguration idempotencyConfig  = buildCacheConfig(TTL_IDEMPOTENCY,   jacksonSerializer);

        log.info("[RedisConfig] CacheManager initialised with {} named caches", 8);
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(CACHE_REGISTER,        registerConfig)
                .withCacheConfiguration(CACHE_LOGIN,           loginConfig)
                .withCacheConfiguration(CACHE_PASSWORD_CHANGE, passwordConfig)
                .withCacheConfiguration(CACHE_ACCOUNT_LOCK,    accountLockConfig)
                .withCacheConfiguration(CACHE_ROLE_CHANGE,     roleChangeConfig)
                .withCacheConfiguration(CACHE_ROLES,           rolesConfig)
                .withCacheConfiguration(CACHE_LOGIN_ATTEMPT,   loginAttemptConfig)
                .withCacheConfiguration(CACHE_IDEMPOTENCY_KEY, idempotencyConfig)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a {@link RedisCacheConfiguration} with the given TTL, String key serialiser,
     * and the supplied value serialiser. Null values are never cached.
     */
    private static RedisCacheConfiguration buildCacheConfig(
            Duration ttl, RedisSerializer<Object> valueSerializer) {

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(valueSerializer));
    }

    /**
     * Creates a Jackson-based {@link RedisSerializer} that:
     * <ul>
     *   <li>Registers all Jackson modules (including {@code JavaTimeModule}) automatically.</li>
     *   <li>Disables {@code FAIL_ON_EMPTY_BEANS} so lazy-loaded JPA proxies don't break.</li>
     *   <li>Enables polymorphic type handling so deserialised objects are restored to their
     *       original concrete types.</li>
     * </ul>
     */
    private static RedisSerializer<Object> createJacksonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(@Nullable Object value) {
                if (value == null) return new byte[0];
                try {
                    return objectMapper.writeValueAsBytes(value);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Redis serialisation failed for type: " + value.getClass().getName(), e);
                }
            }

            @Override
            public @Nullable Object deserialize(byte  [] bytes) {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return objectMapper.readValue(bytes, Object.class);
                } catch (Exception e) {
                    throw new RuntimeException("Redis deserialisation failed", e);
                }
            }
        };
    }
}
