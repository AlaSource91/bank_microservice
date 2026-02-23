package com.alaeldin.bank_query_service.repository;

import com.alaeldin.bank_query_service.model.readmodel.AccountStatisticsReadModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for AccountStatisticsReadModel (CQRS read-side).
 * Provides methods to query account statistics from the read model collection.
 */
@Repository
public interface AccountStatisticsRepository
extends MongoRepository<AccountStatisticsReadModel, String>
{
    Optional<AccountStatisticsReadModel> findByAccountNumberAndDate(String accountNumber, LocalDate date);
    List<AccountStatisticsReadModel> findByAccountNumberAndDateBetweenOrderByDateDesc(String accountNumber);
}
