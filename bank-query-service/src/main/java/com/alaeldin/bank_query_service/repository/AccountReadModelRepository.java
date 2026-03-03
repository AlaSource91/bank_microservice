package com.alaeldin.bank_query_service.repository;

import com.alaeldin.bank_query_service.model.readmodel.AccountReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for AccountReadModel (CQRS read-side).
 * Provides methods to query account data from the read model collection.
 */
@Repository
public interface AccountReadModelRepository extends MongoRepository<AccountReadModel, String> {

    /**
     * Find an account by account number.
     *
     * @param accountNumber the account number
     * @return Optional containing the account if found
     */
    Optional<AccountReadModel> findByAccountNumber(String accountNumber);

    /**
     * Find accounts by holder name (case-insensitive) with pagination.
     *
     * @param accountHolderName the account holder name
     * @param pageable pagination info
     * @return Page of accounts matching the holder name
     */
    Page<AccountReadModel> findByAccountHolderNameContainingIgnoreCase(String accountHolderName, Pageable pageable);

    /**
     * Find accounts by status with pagination.
     *
     * @param status the account status
     * @param pageable pagination info
     * @return List of accounts with the given status
     */
    Page<AccountReadModel> findByStatus(String status, Pageable pageable);

    /**
     * Search accounts by holder name and status with pagination.
     *
     * @param accountHolderName the account holder name (regex pattern)
     * @param status the account status
     * @param pageable pagination info
     * @return Page of accounts matching both criteria
     */

    Page<AccountReadModel> findByAccountHolderNameContainingIgnoreCaseAndStatus(
            String accountHolderName,
            String status,
            Pageable pageable
    );

    /**
     * Find accounts by holder name and status with pagination.
     *
     * @param accountHolderName the account holder name
     * @param status the account status
     * @param pageable pagination info
     * @return List of accounts matching both criteria
     */
    Page<AccountReadModel> findByAccountHolderNameAndStatus(String accountHolderName, String status, Pageable pageable);

    /**
     * Find accounts with balance greater than specified amount.
     *
     * @param amount the minimum balance amount
     * @return List of accounts with balance greater than amount
     */
    List<AccountReadModel> findByBalanceGreaterThan(BigDecimal amount);

    /**
     * Find accounts with balance less than specified amount.
     *
     * @param amount the maximum balance amount
     * @return List of accounts with balance less than amount
     */
    List<AccountReadModel> findByBalanceLessThan(BigDecimal amount);

    /**
     * Find accounts with balance within a specified range.
     *
     * @param min the minimum balance (inclusive)
     * @param max the maximum balance (inclusive)
     * @return List of accounts with balance in the specified range
     */
    List<AccountReadModel> findByBalanceBetween(BigDecimal min, BigDecimal max);

    /**
     * Count accounts by status.
     *
     * @param status the account status
     * @return count of accounts with the given status
     */
    Long countByStatus(String status);

}
