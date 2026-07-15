package com.alaeldin.account_service.repository;

import com.alaeldin.account_service.constant.AccountStatus;
import com.alaeldin.account_service.model.BankAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface AccountRepository  extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByAccountNumber(String accountNumber);
    Optional<BankAccount> findByUserId(Long userId);
    Optional<BankAccount> findByAccountHolderName(String accountHolderName);
    Page<BankAccount> findByAccountStatus(AccountStatus accountStatus, Pageable pageable);
    @Query("SELECT b.balance FROM BankAccount b WHERE b.accountNumber =:accountNumber")
    public BigDecimal findBalanceByAccountNumber(String accountNumber);
}
