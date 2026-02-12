package com.alaeldin.bank_simulator_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Bank Simulator Service Application
 * Provides bank account and transaction management APIs
 *
 * Note: @EnableDiscoveryClient removed as Eureka is disabled in application.yml
 * Flyway handles database migrations automatically on startup
 */

@EnableCaching
@EnableRetry
@SpringBootApplication
public class BankSimulatorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankSimulatorServiceApplication.class, args);
	}

}
