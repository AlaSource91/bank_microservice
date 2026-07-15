package com.alaeldin.Auth_service.controller;

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
     * @return list of account numbers, empty list if none found
     */
    @GetMapping("/{userId}/accounts")
    public ResponseEntity<List<String>> getUserAccounts(@PathVariable Long userId) {
        try {
            log.info("Internal API: Fetching accounts for userId={}", userId);
            
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
     * Health check endpoint for this internal API.
     * 
     * @return status message
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Internal Account API is running");
    }
}
