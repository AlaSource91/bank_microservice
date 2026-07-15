package com.alaeldin.Auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Auth Service.
 *
 * <ul>
 *   <li>{@link EnableScheduling} activates the outbox-publisher polling loop
 *       ({@code @Scheduled} in {@code OutboxPublisher}) — without this annotation
 *       no scheduled methods will ever fire.</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableDiscoveryClient

public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
