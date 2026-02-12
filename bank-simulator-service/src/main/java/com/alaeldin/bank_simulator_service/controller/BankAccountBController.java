package com.alaeldin.bank_simulator_service.controller;

import com.alaeldin.bank_simulator_service.dto.BankAccountRequest;
import com.alaeldin.bank_simulator_service.dto.BankAccountResponse;
import com.alaeldin.bank_simulator_service.service.BankAccountService;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/v1/bank-b/accounts/")
@Validated
public class BankAccountBController {

    private final BankAccountService bankAccountService;

    public BankAccountBController(BankAccountService bankAccountService)
    {
        this.bankAccountService = bankAccountService;
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<BankAccountResponse>> getAllBankAccounts(
            @RequestParam(value = "page", defaultValue = "0") @Min(value = 0, message = "Page number cannot be negative") int page,
            @RequestParam(value = "size", defaultValue = "10") @Min(value = 1, message = "Page size must be at least 1") int size
    ) {
        log.info("Received request to retrieve all bank accounts - Page: {}, Size: {}", page, size);

        // Call service to retrieve paginated accounts
        org.springframework.data.domain.Page<BankAccountResponse> bankAccounts = bankAccountService.getAllAccounts(page, size);

        log.debug("Successfully retrieved {} accounts from page {}", bankAccounts.getNumberOfElements(), page);

        // Return response with HTTP 200 OK status
        return new ResponseEntity<>(bankAccounts, HttpStatus.OK);
    }

    /**
     * Retrieves a bank account by account number.
     * This endpoint returns a single bank account matching the provided account number.
     *
     * <p>HTTP Method: GET</p>
     * <p>Endpoint: GET /api/v1/accounts/{accountNumber}</p>
     *
     * <p>Path Parameters:</p>
     * <ul>
     *   <li>accountNumber (required): The unique account number to retrieve</li>
     * </ul>
     *
     * <p>Response:</p>
     * <ul>
     *   <li>HTTP 200 OK: Successfully retrieved the account</li>
     *   <li>HTTP 404 Not Found: Account with given number does not exist</li>
     *   <li>HTTP 500 Internal Server Error: Unexpected server error</li>
     * </ul>
     *
     * @param accountNumber the account number to retrieve
     * @return ResponseEntity containing BankAccountResponse with HTTP 200 OK status
     * @see BankAccountResponse
     */
    @GetMapping("/{accountNumber}")
    public ResponseEntity<BankAccountResponse> getBankAccountByAccountNumber(
            @PathVariable("accountNumber") String accountNumber) {

        log.info("Received request to retrieve bank account with account number: {}", accountNumber);

        BankAccountResponse bankAccountResponse = bankAccountService.getAccountByAccountNumber(accountNumber);

        log.debug("Successfully retrieved bank account with account number: {}", accountNumber);

        return new ResponseEntity<>(bankAccountResponse, HttpStatus.OK);
    }

    /**
     * Retrieves bank accounts by holder name.
     * This endpoint returns a paginated list of all bank accounts matching the provided holder name.
     *
     * <p>HTTP Method: GET</p>
     * <p>Endpoint: GET /api/v1/accounts/holder-name/{holderName}</p>
     *
     * <p>Path Parameters:</p>
     * <ul>
     *   <li>holderName (required): The name of the account holder to search for</li>
     * </ul>
     *
     * <p>Query Parameters:</p>
     * <ul>
     *   <li>page (optional): Page number (0-indexed, default = 0, must be >= 0)</li>
     *   <li>size (optional): Number of records per page (default = 10, must be >= 1)</li>
     * </ul>
     *
     * <p>Response:</p>
     * <ul>
     *   <li>HTTP 200 OK: Successfully retrieved paginated accounts</li>
     *   <li>HTTP 400 Bad Request: Invalid pagination parameters</li>
     *   <li>HTTP 500 Internal Server Error: Unexpected server error</li>
     * </ul>
     *
     * @param holderName the account holder name to search for
     * @param page       the page number (0-indexed, default = 0, must be >= 0)
     * @param size       the number of records per page (default = 10, must be >= 1)
     * @return ResponseEntity containing Page of BankAccountResponse with HTTP 200 OK status
     * @see BankAccountResponse
     */
    @GetMapping("/holder-name/{holderName}")
    public ResponseEntity<org.springframework.data.domain.Page<BankAccountResponse>> getBankAccountByHolderName(
            @PathVariable("holderName") String holderName,
            @RequestParam(value = "page", defaultValue = "0") @Min(value = 0, message = "Page number cannot be negative") int page,
            @RequestParam(value = "size", defaultValue = "10") @Min(value = 1, message = "Page size must be at least 1") int size) {

        log.info("Received request to retrieve bank accounts with holder name: {} - Page: {}, Size: {}", holderName, page, size);

        org.springframework.data.domain.Page<BankAccountResponse> bankAccountResponse = bankAccountService.getAccountByHolderName(holderName, page, size);

        log.debug("Successfully retrieved {} accounts for holder name: {}", bankAccountResponse.getNumberOfElements(), holderName);

        return new ResponseEntity<>(bankAccountResponse, HttpStatus.OK);
    }

    /**
     * Creates a new bank account.
     * This endpoint creates a new bank account with the provided account details.
     *
     * <p>HTTP Method: POST</p>
     * <p>Endpoint: POST /api/v1/accounts</p>
     *
     * <p>Request Body:</p>
     * <ul>
     *   <li>accountHolderName (required): Name of the account holder (2-100 characters)</li>
     *   <li>balance (required): Initial account balance (must be >= 0)</li>
     *   <li>accountType (required): Type of account (PERSONAL or BUSINESS)</li>
     *   <li>status (required): Initial account status</li>
     * </ul>
     *
     * <p>Response:</p>
     * <ul>
     *   <li>HTTP 201 Created: Account successfully created</li>
     *   <li>HTTP 400 Bad Request: Invalid request body or validation failure</li>
     *   <li>HTTP 409 Conflict: Account holder name already exists</li>
     *   <li>HTTP 500 Internal Server Error: Unexpected server error</li>
     * </ul>
     *
     * @param bankAccountRequest the bank account creation request containing account details
     * @return ResponseEntity containing the created BankAccountResponse with HTTP 201 Created status
     * @see BankAccountRequest
     * @see BankAccountResponse
     */
    @PostMapping
    public ResponseEntity<BankAccountResponse> createBankAccount(
            @RequestBody @Validated BankAccountRequest bankAccountRequest) {

        log.info("Received request to create a new bank account for holder: {}", bankAccountRequest.getAccountHolderName());

        BankAccountResponse createdAccount = bankAccountService.createBankAccount(bankAccountRequest);

        log.debug("Successfully created bank account with account number: {}", createdAccount.getAccountNumber());

        return new ResponseEntity<>(createdAccount, HttpStatus.CREATED);
    }

    /**
     * Updates an existing bank account.
     * This endpoint updates the details of an existing bank account by its ID.
     *
     * <p>HTTP Method: PUT</p>
     * <p>Endpoint: PUT /api/v1/accounts/{id}</p>
     *
     * <p>Path Parameters:</p>
     * <ul>
     *   <li>id (required): The ID of the account to update</li>
     * </ul>
     *
     * <p>Request Body:</p>
     * <ul>
     *   <li>accountHolderName (required): Updated account holder name (2-100 characters)</li>
     *   <li>balance (required): Updated account balance (must be >= 0)</li>
     *   <li>accountType (required): Updated account type (PERSONAL or BUSINESS)</li>
     *   <li>status (required): Updated account status</li>
     * </ul>
     *
     * <p>Response:</p>
     * <ul>
     *   <li>HTTP 200 OK: Account successfully updated</li>
     *   <li>HTTP 400 Bad Request: Invalid request body or validation failure</li>
     *   <li>HTTP 404 Not Found: Account with given ID does not exist</li>
     *   <li>HTTP 500 Internal Server Error: Unexpected server error</li>
     * </ul>
     *
     * @param id                 the account ID to update
     * @param bankAccountRequest the bank account update request containing updated details
     * @return ResponseEntity containing the updated BankAccountResponse with HTTP 200 OK status
     * @see BankAccountRequest
     * @see BankAccountResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<BankAccountResponse> updateBankAccount(
            @PathVariable Long id,
            @RequestBody @Validated BankAccountRequest bankAccountRequest) {

        log.info("Received request to update bank account with ID: {}", id);

        BankAccountResponse updatedAccount = bankAccountService.updateAccount(id, bankAccountRequest);

        log.debug("Successfully updated bank account with account ID: {}", id);

        return new ResponseEntity<>(updatedAccount, HttpStatus.OK);
    }

    /**
     * Deletes a bank account.
     * This endpoint deletes an existing bank account by its ID.
     *
     * <p>HTTP Method: DELETE</p>
     * <p>Endpoint: DELETE /api/v1/accounts/{id}</p>
     *
     * <p>Path Parameters:</p>
     * <ul>
     *   <li>id (required): The ID of the account to delete</li>
     * </ul>
     *
     * <p>Response:</p>
     * <ul>
     *   <li>HTTP 204 No Content: Account successfully deleted</li>
     *   <li>HTTP 404 Not Found: Account with given ID does not exist</li>
     *   <li>HTTP 500 Internal Server Error: Unexpected server error</li>
     * </ul>
     *
     * @param id the account ID to delete
     * @return ResponseEntity with HTTP 204 No Content status
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBankAccount(@PathVariable("id") Long id) {

        log.info("Received request to delete bank account with ID: {}", id);

        bankAccountService.deleteAccount(id);

        log.debug("Successfully deleted bank account with ID: {}", id);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<org.springframework.data.domain.Page<BankAccountResponse>> getBankAccountsByStatus(
            @PathVariable("status") String status,
            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "Page number cannot be negative") int page,
            @RequestParam(value = "size", defaultValue = "10")
            @Min(value = 1, message = "Page size must be at least 1") int size) {

        log.info("Received request to retrieve bank accounts with status: {} - Page: {}, Size: {}", status, page, size);
        Page<BankAccountResponse> bankAccountResponses  = bankAccountService.getAccountsByStatus(status, page, size);
        log.debug("Successfully retrieved {} accounts with status: {}", bankAccountResponses.getNumberOfElements(), status);

        return new ResponseEntity<>(bankAccountResponses, HttpStatus.OK);
    }

    @PutMapping("/accounts/{accountNumber}/freeze")
    public ResponseEntity<String> freezeAccount(
            @PathVariable String accountNumber) {

        log.info("Received request to freeze account with account number: {}", accountNumber);

        bankAccountService.freezeAccount(accountNumber);

        log.info("Successfully froze account with account number: {}", accountNumber);

        return ResponseEntity.ok(
                "Account with account number: " + accountNumber + " has been frozen."
        );
    }

    @GetMapping("/balance/{accountNumber}")
    public ResponseEntity<BigDecimal> getAccountBalance(@PathVariable("accountNumber") String accountNumber) {

        log.info("Received request to retrieve balance for account number: {}", accountNumber);
        BigDecimal balance = bankAccountService.getBalance(accountNumber);

        return new ResponseEntity<>(balance, HttpStatus.OK);
    }

    @PutMapping("accounts/{accountNumber}/balance")
    public ResponseEntity<String> updateAccountBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {

        log.info("Received request to update balance for account number: {} with amount: {}", accountNumber, amount);
        bankAccountService.updateBalance(accountNumber, amount);
        log.info("Successfully updated balance for account number: {} with amount: {}", accountNumber, amount);
        return ResponseEntity.ok(
                "Balance for account with account number: " + accountNumber + " has been updated by amount: " + amount
        );
    }

}
