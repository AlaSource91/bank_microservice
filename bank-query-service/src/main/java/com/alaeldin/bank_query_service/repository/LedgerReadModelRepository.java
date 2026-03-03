package com.alaeldin.bank_query_service.repository;

import com.alaeldin.bank_query_service.model.readmodel.LedgerReadModel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedgerReadModelRepository extends MongoRepository<LedgerReadModel, String> {

    Optional<LedgerReadModel> findByTransactionReference(String transactionReference);

    Optional<LedgerReadModel> findByLedgerEntryId(String ledgerEntryId);
}