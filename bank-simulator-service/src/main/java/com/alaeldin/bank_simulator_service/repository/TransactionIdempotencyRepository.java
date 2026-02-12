package com.alaeldin.bank_simulator_service.repository;

import com.alaeldin.bank_simulator_service.model.TransactionIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionIdempotencyRepository extends JpaRepository<TransactionIdempotency, String> {

    Optional<TransactionIdempotency> findByIdempotencyKey(String key);

    boolean existsByIdempotencyKey(String key);
}
