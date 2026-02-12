package com.alaeldin.bank_simulator_service.repository;

import com.alaeldin.bank_simulator_service.model.TransactionLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface TransactionLedgerRepository
        extends JpaRepository<TransactionLedger,Long> {

    Page<TransactionLedger> findByAccountIdOrderByEntryDateDesc(
            Long accountId, Pageable pageable
    );

    @Query("SELECT tl.balanceAfter FROM TransactionLedger tl " +
            "WHERE tl.account.id = :id AND tl.entryDate <= :date " +
            "ORDER BY tl.entryDate DESC LIMIT 1")
    Optional<BigDecimal> getBalanceAsOf(
            @Param("id") Long id,
            @Param("date") LocalDateTime date);
}
