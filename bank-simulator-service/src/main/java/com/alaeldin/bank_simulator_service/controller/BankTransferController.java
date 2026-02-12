package com.alaeldin.bank_simulator_service.controller;

import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.alaeldin.bank_simulator_service.dto.TransferRequest;
import com.alaeldin.bank_simulator_service.dto.TransferResponse;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import com.alaeldin.bank_simulator_service.service.BankTransactionService;
import com.alaeldin.bank_simulator_service.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/transfer")
public class BankTransferController {

    private final IdempotencyService idempotencyService;
    private final BankTransactionService bankTransactionService;

    public BankTransferController(IdempotencyService idempotencyService, BankTransactionService bankTransactionService) {
        this.idempotencyService = idempotencyService;
        this.bankTransactionService = bankTransactionService;
    }

    /**
     * Execute a bank transfer with idempotency support.
     *
     * This endpoint is idempotent. Clients MUST provide a unique Idempotency-Key header
     * for each independent transfer request.
     *
     * Behavior:
     * - First request: Processes transfer, returns 201 CREATED
     * - Retry with same key (within 24h): Returns cached result, returns 200 OK
     * - Different body with same key: Returns 400 Bad Request
     * - Concurrent request with same key: Returns 409 Conflict
     *
     * @param idempotencyKey Unique request ID (UUID format, required)
     * @param transferRequest Transfer details (fromAccountId, toAccountId, amount)
     * @return TransferResponse with transaction details
     */
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
           @Validated @RequestBody TransferRequest transferRequest) {

        // Step 1: Check if already processed (exactly-once guarantee)
        Optional<BankTransaction> cached = idempotencyService
                .checkIdempotencyKey(idempotencyKey, transferRequest);

        if (cached.isPresent()) {
            log.info("Idempotency key {} found in cache, returning cached response", idempotencyKey);
            BankTransaction transaction = cached.get();

            // Reload transaction with accounts properly loaded to avoid null account details
            if (transaction.getId() != null) {
                transaction = bankTransactionService.getTransactionWithAccounts(transaction.getId());
            }

            return ResponseEntity.ok(toDTO(transaction));
        }

        try {
            // Step 2: Record that processing has started
            idempotencyService.recordStart(idempotencyKey, transferRequest);

            // Step 3: Process the actual transfer
            BankTransaction txn = bankTransactionService.processTransfer(transferRequest);

            // Step 4: Record successful completion
            idempotencyService.recordSuccess(idempotencyKey, txn);

            // Step 5: Return created response
            return ResponseEntity.status(201).body(toDTO(txn));

        } catch (IllegalStateException e) {
            // Concurrent request detected (transaction already in progress)
            log.warn("Concurrent request detected for idempotency key {}: {}",
                    idempotencyKey, e.getMessage());
            idempotencyService.recordFailure(idempotencyKey, e.getMessage());
            return ResponseEntity.status(409).body(TransferResponse.builder()
                    .referenceId(null)
                    .amount(null)
                    .status(StatusTransaction.FAILED)
                    .build());

        } catch (IllegalArgumentException e) {
            // Request body doesn't match previous request with same key
            log.warn("Request validation failed for idempotency key {}: {}",
                    idempotencyKey, e.getMessage());
            idempotencyService.recordFailure(idempotencyKey, e.getMessage());
            return ResponseEntity.status(400).body(TransferResponse.builder()
                    .referenceId(null)
                    .amount(null)
                    .status(StatusTransaction.PROCESSING)
                    .build());

        } catch (RuntimeException e) {
            // Processing failed (insufficient balance, account not found, etc.)
            log.error("Transaction processing failed for idempotency key {}: {}",
                    idempotencyKey, e.getMessage());
            idempotencyService.recordFailure(idempotencyKey, e.getMessage());
            return ResponseEntity.status(400).body(TransferResponse.builder()
                    .referenceId(null)
                    .amount(null)
                    .status(StatusTransaction.FAILED)
                    .build());

        } catch (Exception e) {
            // Unexpected errors
            log.error("Unexpected error processing transfer for idempotency key {}: {}",
                    idempotencyKey, e.getMessage(), e);
            try {
                idempotencyService.recordFailure(idempotencyKey,
                        "Unexpected error: " + e.getClass().getSimpleName());
            } catch (Exception recordingError) {
                log.error("Failed to record failure", recordingError);
            }
            return ResponseEntity.status(500).body(TransferResponse.builder()
                    .referenceId(null)
                    .amount(null)
                    .status(StatusTransaction.FAILED)
                    .build());
        }
    }

    /**
     * Converts a BankTransaction model to TransferResponse DTO.
     *
     * @param txn The bank transaction to convert
     * @return TransferResponse DTO with reference ID, amount, and status
     */
    private TransferResponse toDTO(BankTransaction txn) {
        return TransferResponse.builder()
                .referenceId(txn.getReferenceId())
                .sourceAccount(txn.getSourceAccount())
                .destinationAccount(txn.getDestinationAccount())
                .amount(txn.getAmount())
                .description(txn.getDescription())
                .status(txn.getStatus())
                .errorCode(txn.getErrorCode())
                .errorMessage(txn.getErrorMessage())
                .transactionDate(txn.getTransactionDate())
                .completedAt(txn.getCompletedAt())
                .failedAt(txn.getFailedAt())
                .createdAt(txn.getCreatedAt())
                .updatedAt(txn.getUpdatedAt())
                .build();
    }
}
