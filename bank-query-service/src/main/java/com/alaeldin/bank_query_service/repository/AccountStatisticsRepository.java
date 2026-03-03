package com.alaeldin.bank_query_service.repository;

import com.alaeldin.bank_query_service.model.readmodel.AccountStatisticsReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * MongoDB repository for AccountStatisticsReadModel (CQRS read-side).
 * Provides methods to query account statistics from the read model collection.
 */
@Repository
public interface AccountStatisticsRepository
        extends MongoRepository<AccountStatisticsReadModel, String>
{
    /**
     * Find a single statistics record by account number and exact date.
     */
    Optional<AccountStatisticsReadModel> findByAccountNumberAndDate(String accountNumber, LocalDate date);

    /**
     * Find paginated statistics for an account within a date range, ordered by date descending.
     */
    Page<AccountStatisticsReadModel> findByAccountNumberAndDateBetweenOrderByDateDesc(
            String accountNumber, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
