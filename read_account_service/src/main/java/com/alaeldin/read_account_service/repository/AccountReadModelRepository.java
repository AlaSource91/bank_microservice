package com.alaeldin.read_account_service.repository;

import com.alaeldin.read_account_service.model.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for Account Read Model (CQRS read-side)
 * Provides methods to Query account data from the read model collection
 */
@Repository
public interface AccountReadModelRepository extends MongoRepository<Account, String> {

    /**
     * Find an account By Account Number
     *
     * @param accountNumber the accountNumber
     * @return Optional containing the account if found
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     *Find accounts by holder Name (case-insensitive) with pagination.
     *
     * @param accountHolderName the account holder name
     * @return Page of accounts matching the holder name
     */
    Page<Account> findByAccountHolderNameContainingIgnoreCase(String accountHolderName , Pageable pageable);

}
