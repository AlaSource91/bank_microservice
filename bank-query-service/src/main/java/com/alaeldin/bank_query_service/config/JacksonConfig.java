package com.alaeldin.bank_query_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for JSON serialization/deserialization.
 * Configures ObjectMapper with proper settings for the application.
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates and configures an ObjectMapper bean for JSON processing.
     * Configures Java 8 date/time support and other serialization settings.
     *
     * @return configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Auto-discover and register modules including JavaTimeModule for Java 8 date/time API
        mapper.findAndRegisterModules();

        // Disable writing dates as timestamps (use ISO-8601 format instead)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Configure to fail on unknown properties during deserialization (optional, can be disabled)
        // mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}

