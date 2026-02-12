package com.alaeldin.bank_simulator_service.repository;

import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BankTransaction entity.
 * Provides database access operations for bank transaction management.
 * Extends JpaRepository to inherit standard CRUD operations and query capabilities.
 *
 * <p>Custom query methods:</p>
 * <ul>
 *   <li>findByReferenceId - Retrieve a specific transaction by its unique reference ID</li>
 *   <li>findByStatus - Retrieve all transactions with a specific status</li>
 *   <li>findBySourceAccount - Retrieve all transactions from a specific source account</li>
 *   <li>findByDestinationAccount - Retrieve all transactions to a specific destination account</li>
 * </ul>
 *
 * @see com.alaeldin.bank_simulator_service.model.BankTransaction
 */
@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    /**
     * Retrieves a bank transaction by its unique reference ID.
     * Reference IDs are unique across the system, so at most one transaction will be returned.
     *
     * @param referenceId the unique reference ID to search for (e.g., "TXN-2024-001")
     * @return an Optional containing the BankTransaction if found, or empty if not found
     */
    Optional<BankTransaction> findByReferenceId(String referenceId);

    /**
     * Retrieves all bank transactions with a specific status.
     * Multiple transactions can have the same status, so this returns a list.
     *
     * @param status the transaction status to filter by (e.g., COMPLETED, FAILED, PENDING)
     * @return a List of BankTransactions with the specified status. Returns an empty list if no transactions found.
     */
    List<BankTransaction> findByStatus(StatusTransaction status);

    /**
     * Retrieves all bank transactions originating from a specific source account.
     * Multiple transactions can originate from the same account, so this returns a list.
     *
     * @param sourceAccount the source account to filter by
     * @return a List of BankTransactions from the specified source account. Returns an empty list if no transactions found.
     */
    List<BankTransaction> findBySourceAccount(BankAccount sourceAccount);

    /**
     * Retrieves all bank transactions sent to a specific destination account.
     * Multiple transactions can be sent to the same account, so this returns a list.
     *
     * @param destinationAccount the destination account to filter by
     * @return a List of BankTransactions to the specified destination account. Returns an empty list if no transactions found.
     */
    List<BankTransaction> findByDestinationAccount(BankAccount destinationAccount);
}
