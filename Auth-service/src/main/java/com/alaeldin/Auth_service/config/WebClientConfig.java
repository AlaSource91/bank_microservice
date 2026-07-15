package com.alaeldin.Auth_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${bank.simulator.url:http://localhost:8081}")
    private String bankSimulatorUrl;

    @Bean
    public WebClient bankSimulatorWebClient() {
        return WebClient.builder()
                .baseUrl(bankSimulatorUrl)
                .build();
    }

}
