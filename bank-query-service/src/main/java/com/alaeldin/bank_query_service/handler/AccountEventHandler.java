package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.model.event.AccountEvent;
import com.alaeldin.bank_query_service.model.readmodel.AccountReadModel;
import com.alaeldin.bank_query_service.repository.AccountReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

/**
 * Handler for account-related events in the CQRS read model.
 * Processes events and updates the MongoDB read model accordingly.
 * Cache eviction is applied after each operation to ensure cache consistency.
 * Note: @Transactional is not used as MongoDB document operations are atomic by default.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountEventHandler
{
   private final AccountReadModelRepository accountReadModelRepository;

    /**
     * Handles account creation events and evicts all relevant caches.
     * Cache eviction ensures that queries for the new account return fresh data.
     */
    @Caching(evict = {
            @CacheEvict(value = "accountDetails", key = "#accountEvent.accountNumber.trim().toUpperCase()"),
            @CacheEvict(value = "accountBalance", key = "#accountEvent.accountNumber.trim().toUpperCase()"),
            @CacheEvict(value = "accountSearch", allEntries = true),
            @CacheEvict(value = "allAccounts", allEntries = true)
    })
    public void handleAccountCreated(AccountEvent accountEvent)
   {
       log.info("AccountEventHandler handleAccountCreated: {}", accountEvent.toString());

       try
       {
           if (accountReadModelRepository.findByAccountNumber(accountEvent.getAccountNumber()).isPresent())
           {
               log.info("Account Already Exists with account number: {}", accountEvent.getAccountNumber());
               return;
           }

           // Create a new read model entity based on the event data
           AccountReadModel accountReadModel =  AccountReadModel
                   .builder()
                   .id(accountEvent.getId())
                     .accountNumber(accountEvent.getAccountNumber())
                   .accountHolderName(accountEvent.getAccountHolderName())
                     .accountType(accountEvent.getAccountType().toString())
                   .balance(accountEvent.getBalance())
                   .status(accountEvent.getStatus())
                     .createdAt(accountEvent.getTimestamp())
                   .updatedAt(null)
                   .version(accountEvent.getVersion())
                     .applicationName(accountEvent.getApplicationName())
                   .build();
           accountReadModelRepository.save(accountReadModel);
           log.info("AccountReadModel created successfully and cache evicted for account number: {}", accountEvent.getAccountNumber());
       }
       catch (Exception e)
       {
              log.error("Error handling AccountCreated event: {}", e.getMessage());
              throw e;
       }
   }

    /**
     * Handles account frozen events and evicts all relevant caches.
     * Only status changes, but we evict all account-related caches for consistency.
     */
    @Caching(evict = {
            @CacheEvict(value = "accountDetails", key = "#accountNumber.trim().toUpperCase()"),
            @CacheEvict(value = "accountBalance", key = "#accountNumber.trim().toUpperCase()"),
            @CacheEvict(value = "accountSearch", allEntries = true),
            @CacheEvict(value = "allAccounts", allEntries = true)
    })
    public void handleAccountFrozen(AccountEvent accountEvent, String accountNumber) {

       log.info("AccountEventHandler handleAccountFrozen: {}", accountEvent.toString());

       try {
           AccountReadModel accountReadModelExist = accountReadModelRepository.findByAccountNumber(accountNumber)
                   .orElseThrow(() -> new RuntimeException("Account not found with account number: " + accountNumber));

           accountReadModelExist.setStatus(accountEvent.getStatus());
           accountReadModelExist.setUpdatedAt(accountEvent.getTimestamp());
           accountReadModelRepository.save(accountReadModelExist);
              log.info("AccountReadModel updated and cache evicted successfully for account number: {}", accountNumber);
       }
       catch (Exception e){
              log.error("Error handling AccountFrozen event: {}", e.getMessage());
              throw e;
       }
   }

    /**
     * Handles account updated events and evicts all relevant caches.
     * This ensures that any cached account data is refreshed with the latest changes.
     */
    @Caching(evict = {
            @CacheEvict(value = "accountDetails", key = "#accountEvent.accountNumber.trim().toUpperCase()"),
            @CacheEvict(value = "accountBalance", key = "#accountEvent.accountNumber.trim().toUpperCase()"),
            @CacheEvict(value = "accountSearch", allEntries = true),
            @CacheEvict(value = "allAccounts", allEntries = true)
    })
    public void handleAccountUpdated(AccountEvent accountEvent, String id) {

        log.info("AccountEventHandler handleAccountUpdated for id: {}", id);

        try {
            AccountReadModel accountReadModelExist = accountReadModelRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));

            accountReadModelExist.setAccountNumber(accountEvent.getAccountNumber());
            accountReadModelExist.setAccountHolderName(accountEvent.getAccountHolderName());
            accountReadModelExist.setAccountType(accountEvent.getAccountType().toString());
            accountReadModelExist.setBalance(accountEvent.getBalance());
            accountReadModelExist.setStatus(accountEvent.getStatus());
            accountReadModelExist.setUpdatedAt(accountEvent.getTimestamp());
            accountReadModelRepository.save(accountReadModelExist);
            log.info("AccountReadModel updated and cache evicted successfully for id: {}", id);
        }
        catch (Exception e){
            log.error("Error handling AccountUpdated event: {}", e.getMessage());
            throw e;
        }
    }
}
