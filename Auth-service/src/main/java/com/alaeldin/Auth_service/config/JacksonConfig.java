package com.alaeldin.Auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Jackson configuration for JSON serialization/deserialization.
 *
 * Spring Boot 3.x uses Jackson 2.x (com.fasterxml.jackson package).
 * Key changes from older versions:
 *  - WRITE_DATES_AS_TIMESTAMPS was removed from SerializationFeature;
 *    ISO-8601 output is the default when Jackson discovers JavaTimeModule
 *    via findAndAddModules().
 *  - Use JsonMapper.builder() instead of new ObjectMapper() directly.
 *
 * Defining this bean causes Spring Boot's Jackson auto-configuration to
 * back off, so all settings must be applied here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                // Auto-discovers and registers all Jackson modules on the classpath
                // (e.g. JavaTimeModule for ISO-8601 date/time serialization)
                .findAndAddModules()
                // Do not fail when the JSON payload contains fields unknown to the POJO
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }
}
