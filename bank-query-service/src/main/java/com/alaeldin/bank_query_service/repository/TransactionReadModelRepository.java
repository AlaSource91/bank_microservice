package com.alaeldin.bank_query_service.repository;

import com.alaeldin.bank_query_service.model.readmodel.TransactionReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TransactionReadModelRepository
        extends MongoRepository<TransactionReadModel, String> {

    Optional<TransactionReadModel> findByTransactionId(String transactionId);

    Page<TransactionReadModel> findBySourceAccountNumberOrderByTransactionDateDesc(
            String sourceAccountNumber, Pageable pageable);

    Page<TransactionReadModel> findByDestinationAccountNumberOrderByTransactionDateDesc(
            String destinationAccountNumber, Pageable pageable);

    @Query("{ $or: [ { sourceAccountNumber: ?0 }, { destinationAccountNumber: ?0 } ] }")
    Page<TransactionReadModel> findByAccountNumberOrderByTransactionDateDesc(
            String accountNumber, Pageable pageable);

    @Query("{ 'transactionDate': { $gte: ?0, $lte: ?1 } }")
    Page<TransactionReadModel> findByTransactionDateBetween(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    @Query("{ 'amount': { $gte: ?0, $lte: ?1 } }")
    Page<TransactionReadModel> findByAmountRange(
            BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);

    Long countByStatus(String status);

    @Query(value = "{ 'sourceAccountNumber': ?0 }", count = true)
    Long countBySourceAccountNumber(String sourceAccountNumber);

    Page<TransactionReadModel> findBySourceAccountNumberOrDestinationAccountNumber(
            String sourceAccountNumber, String destinationAccountNumber, Pageable pageable);

    /**
     * Count transactions where the account is either source or destination within a date range.
     */
    @Query(value = "{ $or: [ { sourceAccountNumber: ?0 }, { destinationAccountNumber: ?0 } ], transactionDate: { $gte: ?1, $lte: ?2 } }", count = true)
    Long countByAccountNumberAndTransactionDateBetween(
            String accountNumber, LocalDateTime start, LocalDateTime end);

    /**
     * Sum debit amounts (outgoing) for a source account within a date range.
     */
    @Aggregation(pipeline = {
            "{ $match: { sourceAccountNumber: ?0, transactionDate: { $gte: ?1, $lte: ?2 } } }",
            "{ $group: { _id: null, total: { $sum: '$amount' } } }"
    })
    BigDecimal sumBySourceAccountNumberAndTransactionDateBetween(
            String sourceAccountNumber, LocalDateTime start, LocalDateTime end);

    /**
     * Sum credit amounts (incoming) for a destination account within a date range.
     */
    @Aggregation(pipeline = {
            "{ $match: { destinationAccountNumber: ?0, transactionDate: { $gte: ?1, $lte: ?2 } } }",
            "{ $group: { _id: null, total: { $sum: '$amount' } } }"
    })
    BigDecimal sumByDestinationAccountNumberAndTransactionDateBetween(
            String destinationAccountNumber, LocalDateTime start, LocalDateTime end);

    @Aggregation(pipeline = {
            "{ $match: { destinationAccountNumber: ?0, transactionDate: { $lt: ?1 } } }",
            "{ $group: { _id: null, total: { $sum: '$amount' } } }"
    })
    BigDecimal sumCreditBefore(String accountNumber, LocalDateTime date);

    @Aggregation(pipeline = {
            "{ $match: { sourceAccountNumber: ?0, transactionDate: { $lt: ?1 } } }",
            "{ $group: { _id: null, total: { $sum: '$amount' } } }"
    })
    BigDecimal sumDebitBefore(String accountNumber, LocalDateTime date);
}



