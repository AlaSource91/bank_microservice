package com.alaeldin.bank_simulator_service.mapper;

import com.alaeldin.bank_simulator_service.model.TransactionIdempotency;
import com.alaeldin.bank_simulator_service.dto.TransactionIdempotencyDto;

public interface TransactionIdempotencyMapper
{
    /**
     * Converts a TransactionIdempotencyDto to a TransactionIdempotency entity.
     * @param dto dto to be converted
     * @return the converted TransactionIdempotency entity
     */
     TransactionIdempotency toEntity(TransactionIdempotencyDto dto);

    /**
     * Converts a TransactionIdempotency entity to a TransactionIdempotencyDto.
     * @param entity the TransactionIdempotency entity to be converted
     * @return the converted TransactionIdempotencyDto
     */
     TransactionIdempotencyDto toDto(TransactionIdempotency entity);

}
