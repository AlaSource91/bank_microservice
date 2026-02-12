package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.IdempotencyStatus;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import com.alaeldin.bank_simulator_service.model.TransactionIdempotency;
import com.alaeldin.bank_simulator_service.repository.TransactionIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing transaction idempotency.
 *
 * Prevents duplicate processing of transactions by tracking idempotency keys.
 * Uses caching to optimize lookups and ensure exactly-once semantics.
 *
 * Features:
 * - Idempotency key validation and tracking
 * - Transaction status management (PROCESSING → COMPLETED/FAILED)
 * - Request hash validation for consistency
 * - 24-hour cache TTL for idempotency keys
 * - Comprehensive logging for audit trails
 *
 * @author Alaeldin Musa
 * @version 2.0
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService
{
    private final TransactionIdempotencyRepository transactionIdempotencyRepository;
    private final ObjectMapper objectMapper;

    private static final int IDEMPOTENCY_TTL_HOURS = 24;
    private static final String CACHE_NAME = "idempotencyKeys";

    /**
     * Checks if a transaction with the given idempotency key already exists and is valid.
     *
     * This is the primary cache lookup method. If found and not expired:
     * - If COMPLETED: returns the original transaction (exactly-once guarantee)
     * - If IN_PROGRESS: throws exception to prevent concurrent processing
     * - If FAILED: returns empty (allows retry)
     * - If EXPIRED: returns empty (beyond TTL window)
     *
     * Only COMPLETED transactions are cached. IN_PROGRESS and FAILED states
     * always check database.
     *
     * @param key The idempotency key to check (must be non-empty)
     * @param req The request object to validate hash consistency
     * @return An Optional containing the existing BankTransaction if found and completed,
     *         or empty if not found, expired, or failed
     * @throws IllegalStateException if a transaction with the given key is still in
     * progress
     * @throws IllegalArgumentException if key is null or empty or request hash mismatch
     */
    @Cacheable(value = CACHE_NAME, key = "#key"
            , condition = "#result != null && #result.isPresent()")
    public Optional<BankTransaction> checkIdempotencyKey(
            String key,Object req)
    {
        validateKey(key);

        log.debug("Checking idempotency for key: {}", maskKey(key));

        Optional<TransactionIdempotency> record = transactionIdempotencyRepository
                .findByIdempotencyKey(key);

        if (record.isEmpty())
        {
            log.debug("No idempotency record found for key: {}", maskKey(key));
            return Optional.empty();
        }

        TransactionIdempotency idempotencyRecord = record.get();
        String newHash = calculateHash(key, req);

        // Validate request body hasn't changed (hash mismatch = different request)
        if (!newHash.equals(idempotencyRecord.getRequestHash()))
        {
            log.warn("Request body does not match previous request for idempotency key: {}", maskKey(key));
            throw new IllegalArgumentException(
                "Request body does not match previous request for idempotency key: " + key);
        }

        if (idempotencyRecord.isExpired())
        {
            log.warn("Idempotency record expired for key: {}", maskKey(key));
            return Optional.empty();
        }

        // Handle status cases
        switch (idempotencyRecord.getStatus())
        {
            case IN_PROGRESS:
                log.warn("Concurrent processing detected for key: {}", maskKey(key));
                throw new IllegalStateException(
                    "Transaction is already being processed for idempotency key: " + key);

            case COMPLETED:
                log.info("Returning cached transaction for idempotency key: {}", maskKey(key));
                return Optional.ofNullable(idempotencyRecord.getTransaction());

            case FAILED:
                log.debug("Previous attempt failed for idempotency key: {}", maskKey(key));
                return Optional.empty();

            default:
                log.warn("Unknown status for idempotency key: {}", maskKey(key));
                return Optional.empty();
        }
    }

    /**
     * Records the start of transaction processing with an idempotency key.
     *
     * This method marks a transaction as IN_PROGRESS to prevent concurrent processing
     * of the same request. The entry will automatically expire after 24 hours.
     *
     * Processing flow:
     * 1. Validate idempotency key format
     * 2. Check for existing key (prevent duplicates)
     * 3. Calculate request hash for consistency validation
     * 4. Create and persist idempotency record
     * 5. Log successful registration
     *
     * @param key The idempotency key for the transaction (must be unique and non-empty)
     * @throws IllegalStateException if the idempotency key already exists
     * @throws IllegalArgumentException if key is null or empty
     */
    public void recordStart(String key, Object req)
    {
        validateKey(key);

        log.debug("Recording start of transaction processing for key: {}", maskKey(key));

        // Validate idempotency key doesn't already exist
        if (transactionIdempotencyRepository.existsByIdempotencyKey(key))
        {
            log.warn("Idempotency key already registered, rejecting duplicate for key: {}", maskKey(key));
            throw new IllegalStateException(
                "Idempotency key already exists. This request is either being processed or has already been completed: " + key);
        }

        // Create and populate the idempotency record
        LocalDateTime now = LocalDateTime.now();
        String requestHash = calculateHash(key,req);

        TransactionIdempotency transactionIdempotency = TransactionIdempotency.builder()
                .idempotencyKey(key)
                .status(IdempotencyStatus.IN_PROGRESS)
                .createdAt(now)
                .expiresAt(now.plusHours(IDEMPOTENCY_TTL_HOURS))
                .requestHash(requestHash)
                .build();

        // Save to repository
        transactionIdempotencyRepository.save(transactionIdempotency);
        log.info("Successfully recorded transaction start - Key: {}, Hash: {}",
                maskKey(key), maskKey(requestHash));
    }

    /**
     * Records successful completion of a transaction.
     *
     * Updates the idempotency record with:
     * - Transaction reference for lookup
     * - COMPLETED status for future identical requests
     * - Completion timestamp for audit trail
     * - Updates cache with the completed transaction
     *
     * @param key The idempotency key for the transaction
     * @param txn The completed BankTransaction (must be persisted)
     * @throws IllegalArgumentException if key is null/empty or txn is null
     * @throws RuntimeException if idempotency record not found (shouldn't happen if recordStart was called)
     */
    @CachePut(value = CACHE_NAME, key = "#key")
    public Optional<BankTransaction> recordSuccess(String key, BankTransaction txn)
    {
        validateKey(key);
        validateTransaction(txn);

        log.debug("Recording success for idempotency key: {} with transaction: {}",
                maskKey(key), txn.getReferenceId());

        Optional<TransactionIdempotency> recordOpt = transactionIdempotencyRepository.findByIdempotencyKey(key);

        if (recordOpt.isEmpty()) {
            log.error("Idempotency record not found for key: {}. This should not happen if recordStart() was called before processing.",
                    maskKey(key));
            throw new RuntimeException(
                "Idempotency record not found for key: " + key +
                ". Ensure recordStart() was called before recordSuccess()");
        }

        TransactionIdempotency record = recordOpt.get();

        // Update record with transaction and completion status
        record.setTransaction(txn);
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setCompletedAt(LocalDateTime.now());

        // Serialize transaction response as JSON for caching
        try {
            String responseJson = objectMapper.writeValueAsString(txn);
            record.setResponseData(responseJson);
        } catch (Exception e) {
            log.warn("Failed to serialize transaction response to JSON for key: {}, using fallback",
                    maskKey(key), e);
            // Fallback to simple JSON structure
            String fallbackJson = String.format("{\"transactionId\":%d,\"referenceId\":\"%s\",\"status\":\"COMPLETED\",\"timestamp\":\"%s\"}",
                    txn.getId(),
                    txn.getReferenceId(),
                    LocalDateTime.now().toString());
            record.setResponseData(fallbackJson);
        }

        transactionIdempotencyRepository.save(record);

        log.info("Successfully recorded transaction completion - Key: {}, TransactionID: {}, Status: COMPLETED",
                maskKey(key), txn.getId());

        return Optional.of(txn);
    }

    /**
     * Records failure of a transaction processing attempt.
     *
     * Updates the idempotency record with:
     * - FAILED status indicating retry is allowed
     * - Error message for debugging
     * - Completion timestamp for audit trail
     * - Evicts cache to clear IN_PROGRESS state
     *
     * Note: Failure status allows clients to retry the request with the same idempotency key.
     * This is different from COMPLETED status which strictly prevents reprocessing.
     * If no idempotency record exists (e.g., validation failed before recordStart),
     * this method will log a warning but not throw an exception.
     *
     * @param key The idempotency key for the transaction
     * @param errorMessage Detailed error message explaining the failure (max 500 chars)
     * @throws IllegalArgumentException if key is null/empty or message is null
     */
    @CacheEvict(value = CACHE_NAME, key = "#key")
    public void recordFailure(String key, String errorMessage)
    {
        validateKey(key);

        if (errorMessage == null || errorMessage.trim().isEmpty())
        {
            throw new IllegalArgumentException("Error message cannot be null or empty");
        }

        log.debug("Recording failure for idempotency key: {} with error: {}",
                maskKey(key), errorMessage);

        Optional<TransactionIdempotency> recordOpt = transactionIdempotencyRepository.findByIdempotencyKey(key);

        if (recordOpt.isEmpty()) {
            log.warn("No idempotency record found for key: {}. This may occur if validation failed before recordStart() was called. Error: {}",
                    maskKey(key), errorMessage);
            return; // Gracefully handle missing record
        }

        TransactionIdempotency record = recordOpt.get();

        // Update record with failure information
        record.setStatus(IdempotencyStatus.FAILED);

        // Create error response as proper JSON
        try {
            var errorResponse = new java.util.HashMap<String, Object>();
            errorResponse.put("error", errorMessage);
            errorResponse.put("status", "FAILED");
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            String jsonErrorMessage = objectMapper.writeValueAsString(errorResponse);
            record.setResponseData(jsonErrorMessage);
        } catch (Exception e) {
            log.warn("Failed to serialize error response to JSON for key: {}, using fallback",
                    maskKey(key), e);
            // Fallback to manually escaped JSON
            String fallbackJson = String.format("{\"error\":\"%s\",\"status\":\"FAILED\",\"timestamp\":\"%s\"}",
                    escapeJsonString(errorMessage),
                    LocalDateTime.now().toString());
            record.setResponseData(fallbackJson);
        }

        record.setCompletedAt(LocalDateTime.now());

        transactionIdempotencyRepository.save(record);

        log.warn("Successfully recorded transaction failure - Key: {}, Error: {}",
                maskKey(key), errorMessage);
    }

    /**
     * Escapes special characters in a string for safe JSON inclusion.
     *
     * @param str the string to escape
     * @return the escaped string safe for JSON
     */
    private String escapeJsonString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Validates the idempotency key format and content.
     *
     * Requirements:
     * - Non-null
     * - Non-empty after trimming
     * - Reasonable length (at least 5 chars, typically UUID format)
     *
     * @param key The key to validate
     * @throws IllegalArgumentException if key is invalid
     */
    private void validateKey(String key)
    {
        if (key == null)
        {
            throw new IllegalArgumentException("Idempotency key cannot be null");
        }

        if (key.trim().isEmpty())
        {
            throw new IllegalArgumentException("Idempotency key cannot be empty");
        }

        if (key.length() < 5)
        {
            throw new IllegalArgumentException(
                "Idempotency key must be at least 5 characters (got " + key.length() + ")");
        }
    }

    /**
     * Validates that a BankTransaction is properly populated.
     *
     * @param txn The transaction to validate
     * @throws IllegalArgumentException if transaction is null or missing required fields
     */
    private void validateTransaction(BankTransaction txn)
    {
        if (txn == null)
        {
            throw new IllegalArgumentException("BankTransaction cannot be null");
        }

        if (txn.getId() == null)
        {
            throw new IllegalArgumentException("BankTransaction must have an ID");
        }
    }

    /**
     * Calculates SHA-256 hash of the request payload and idempotency key.
     *
     * Used to verify request consistency. If a client retries with the same key
     * but different request body, the hash will differ.
     *
     * Combines key and serialized payload to ensure:
     * - Same request = same hash
     * - Different request = different hash
     *
     * @param key The idempotency key
     * @param payload The request object to hash
     * @return SHA-256 hash as hex string
     * @throws RuntimeException if payload serialization fails
     */
    private String calculateHash(String key, Object payload)
    {
        try
        {
            // Serialize payload to JSON (sorted keys for consistency)
            String json = objectMapper.writeValueAsString(payload);

            // Combine key and payload JSON, then hash
            String combined = key + ":" + json;
            String hash = DigestUtils.sha256Hex(combined);

            log.debug("Calculated hash for key: {} = {}", maskKey(key), maskKey(hash));
            return hash;

        }
        catch (Exception e)
        {
            log.error("Failed to calculate idempotency hash for key: {}", maskKey(key), e);
            throw new RuntimeException(
                "Failed to build idempotency hash for key: " + key, e);
        }
    }

    /**
     * Masks sensitive parts of a key for logging.
     * Shows first 5 and last 3 characters only.
     *
     * Example: "uuid-12345-67890-abcde" → "uuid-...bcde"
     *
     * @param value The value to mask
     * @return Masked string for safe logging
     */
    private String maskKey(String value)
    {
        if (value == null || value.length() <= 8)
        {
            return "***";
        }
        return value.substring(0, 5) + "..." + value.substring(value.length() - 4);
    }
}
