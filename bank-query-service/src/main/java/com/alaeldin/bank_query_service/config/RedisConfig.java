package com.alaeldin.bank_query_service.config;

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
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration for the Query Service.
 * Configures RedisTemplate for idempotency checks and CacheManager for query result caching.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Configure RedisTemplate with String serialization for both keys and values.
     * Used primarily for idempotency key storage in the event consumer.
     *
     * @param factory RedisConnectionFactory auto-configured by Spring Boot
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
     * Create a custom Jackson-based Redis serializer for caching complex types.
     * This serializer properly handles BigDecimal, LocalDateTime, Page objects, and other Java objects.
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
        // This is critical for PageImpl deserialization
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Create custom serializer that handles all types including PageImpl
        return new RedisSerializer<Object>() {
            @Override
            public byte[] serialize(Object value) {
                if (value == null) {
                    return new byte[0];
                }
                try {
                    return objectMapper.writeValueAsBytes(value);
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
                    // Use ObjectMapper with type information to properly deserialize
                    return objectMapper.readValue(bytes, Object.class);
                } catch (Exception e) {
                    throw new RuntimeException("Error deserializing object", e);
                }
            }
        };
    }

    /**
     * Configure Cache Manager with specific TTL for different cache types.
     * Cache Configurations:
     * - accountDetails: 5 minutes TTL (for individual account queries)
     * - accountBalance: 5 minutes TTL (for balance queries)
     * - accountSearch: 3 minutes TTL (for search results)
     * - allAccounts: 3 minutes TTL (for paginated account lists)
     * - accounts: 5 minutes TTL (legacy/generic account cache)
     * - transactions: 3 minutes TTL (for transaction queries)
     * - statistics: 10 minutes TTL (for statistical data)
     * - default: 5 minutes TTL (300 seconds)
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

        // Default cache configuration (5 minutes TTL)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(stringRedisSerializer)
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer)
                );

        // Specific cache configurations with custom TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Account-related caches - 5 minutes for individual queries
        cacheConfigurations.put("accountDetails", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("accountBalance", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("accounts", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("transactionHistory", defaultConfig.entryTtl(Duration.ofSeconds(60)));
        // Search and list caches - 3 minutes (shorter TTL for dynamic results)
        cacheConfigurations.put("accountSearch", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("allAccounts", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("transactions", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("searchTransactions",defaultConfig.entryTtl(Duration.ofSeconds(30)));
        // Transaction cache - 60 seconds (short TTL due to high volatility of transaction data)

        // Statistics cache - 10 minutes
        cacheConfigurations.put("statistics", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}

