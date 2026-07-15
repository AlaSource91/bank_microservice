package com.alaeldin.bank_simulator_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * Internal API controller for inter-service communication.
 * This endpoint should NOT be exposed to the public internet.
 * Used by Auth Service to fetch user account information.
 * 
 * Security Note: In production, this should be secured via:
 * - Internal network access only (not exposed via API Gateway)
 * - Service-to-service authentication (e.g., mutual TLS, API keys)
 * - IP whitelisting
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class InternalAccountController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Retrieves all account numbers associated with a specific user.
     * This is an internal endpoint for service-to-service communication only.
     * 
     * @param userId the user ID (must be a valid Long)
     * @return ResponseEntity containing list of account numbers, empty list if none found
     */
    @GetMapping("/{userId}/accounts")
    public ResponseEntity<List<String>> getUserAccounts(@PathVariable Long userId) {
        try {
            log.info("Internal API: Fetching accounts for userId={}", userId);
            
            if (userId == null || userId <= 0) {
                log.warn("Invalid userId received: {}", userId);
                return ResponseEntity.badRequest().body(Collections.emptyList());
            }
            
            // Query to fetch account numbers for the user
            String sql = "SELECT account_number FROM bank_account WHERE user_id = ?";
            List<String> accounts = jdbcTemplate.queryForList(sql, String.class, userId);
            
            log.debug("Found {} account(s) for userId={}", accounts.size(), userId);
            
            return ResponseEntity.ok(accounts);
            
        } catch (DataAccessException e) {
            log.error("Database error while fetching accounts for userId={}: {}", 
                    userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
                    
        } catch (Exception e) {
            log.error("Unexpected error while fetching accounts for userId={}: {}", 
                    userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }
    
    /**
     * Retrieves basic account information for a specific account number.
     * Used for validation purposes during transaction processing.
     * 
     * @param accountNumber the account number
     * @return ResponseEntity with account existence and status
     */
    @GetMapping("/accounts/{accountNumber}/exists")
    public ResponseEntity<Boolean> accountExists(@PathVariable String accountNumber) {
        try {
            log.debug("Checking if account exists: {}", accountNumber);
            
            String sql = "SELECT COUNT(*) FROM bank_account WHERE account_number = ? AND status = 'ACTIVE'";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountNumber);
            
            boolean exists = count != null && count > 0;
            log.debug("Account {} exists: {}", accountNumber, exists);
            
            return ResponseEntity.ok(exists);
            
        } catch (Exception e) {
            log.error("Error checking account existence for {}: {}", accountNumber, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }
    
    /**
     * Health check endpoint for this internal API.
     * 
     * @return status message
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Internal Account API is running");
    }
}

