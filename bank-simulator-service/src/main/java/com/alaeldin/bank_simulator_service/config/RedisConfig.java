package com.alaeldin.bank_simulator_service.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Configuration for caching and template setup
 * Configured for Spring Boot 4.0+ with custom JSON serialization for BigDecimal support
 */
@Configuration
@EnableCaching
public class RedisConfig {

    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    /**
     * Configure RedisTemplate with String keys serialization
     * Suitable for storing JSON-serialized objects as strings
     *
     * @param factory RedisConnectionFactory
     * @return Configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Use StringRedisSerializer for both keys and values
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Create a custom Jackson-based Redis serializer for caching complex types
     * This serializer properly handles BigDecimal, Lists, Maps, and other Java objects
     *
     * @return Custom RedisSerializer using Jackson ObjectMapper
     */
    private RedisSerializer<Object> createJacksonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Register JavaTimeModule for LocalDateTime support
        objectMapper.findAndRegisterModules();

        // Disable FAIL_ON_EMPTY_BEANS to allow serialization of entities with lazy-loaded fields
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Enable polymorphic type handling for proper serialization/deserialization
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Create custom serializer
        return new RedisSerializer<Object>() {
            @Override
            public byte[] serialize(Object o) {
                if (o == null) {
                    return new byte[0];
                }
                try {
                    return objectMapper.writeValueAsBytes(o);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing object", e);
                }
            }

            @Override
            public Object deserialize(byte[] bytes) {
                if (bytes == null || bytes.length == 0) {
                    return null;
                }
                try {
                    return objectMapper.readValue(bytes, Object.class);
                } catch (Exception e) {
                    throw new RuntimeException("Error deserializing object", e);
                }
            }
        };
    }

    /**
     * Configure Cache Manager with specific TTL for different cache types.
     * Uses custom Jackson serializer to handle BigDecimal and other complex types.
     *
     * <p>Cache Configurations:</p>
     * <ul>
     *   <li>Default: 5 minutes TTL</li>
     *   <li>accountBalance: 5 minutes TTL</li>
     *   <li>accountVersions: 5 minutes TTL</li>
     *   <li>idempotencyKeys: 24 hours TTL</li>
     * </ul>
     *
     * @param factory RedisConnectionFactory
     * @return Configured CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // Use string serializer for keys
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Use custom Jackson serializer for values
        RedisSerializer<Object> jacksonSerializer = createJacksonSerializer();

        // Default cache configuration - 5 minutes TTL
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(stringRedisSerializer)
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer)
                );

        // Account balance cache - 5 minutes TTL
        RedisCacheConfiguration accountBalanceConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(stringRedisSerializer)
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer)
                );

        // Account versions cache - 5 minutes TTL
        RedisCacheConfiguration accountVersionsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(stringRedisSerializer)
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer)
                );

        // Idempotency cache - 24 hours TTL
        RedisCacheConfiguration idempotencyCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(stringRedisSerializer)
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer)
                );

        RedisCacheConfiguration ledgerConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(stringRedisSerializer)
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer)
                );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultCacheConfig)
                .withCacheConfiguration("accountBalance", accountBalanceConfig)
                .withCacheConfiguration("accountVersions", accountVersionsConfig)
                .withCacheConfiguration("idempotencyKeys", idempotencyCacheConfig)
                .withCacheConfiguration("ledgerBalances", ledgerConfig)
                .withCacheConfiguration("accountLedgerEntries", ledgerConfig)
                .build();
    }
}
