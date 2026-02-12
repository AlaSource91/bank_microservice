package com.alaeldin.bank_simulator_service.mapper;

import com.alaeldin.bank_simulator_service.dto.BankAccountRequest;
import com.alaeldin.bank_simulator_service.dto.BankAccountResponse;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between BankAccount entities and their DTOs.
 * Handles bidirectional mapping between BankAccount entity and BankAccountRequest/BankAccountResponse DTOs.
 *
 * Configuration:
 * - componentModel: "spring" - Integrates with Spring dependency injection
 * - unmappedTargetPolicy: IGNORE - Ignores unmapped target properties
 * - nullValueMappingStrategy: RETURN_NULL - Returns null for null source properties during mapping
 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface BankAccountMapper {

    /**
     * Converts a BankAccountRequest DTO to a BankAccount entity.
     * Used when creating a new bank account from client request data.
     *
     * @param bankAccountRequest the request DTO containing account creation data
     * @return a new BankAccount entity populated from the request data
     */
    BankAccount toEntity(BankAccountRequest bankAccountRequest);


    /**
     * Converts a BankAccount entity to a BankAccountResponse DTO.
     * Used when returning account information to clients.
     *
     * @param bankAccount the BankAccount entity to convert
     * @return a BankAccountResponse DTO populated from the entity
     */
    BankAccountResponse toDto(BankAccount bankAccount);

    /**
     * Updates an existing BankAccount entity with data from a BankAccountRequest DTO.
     * Used for partial updates of bank account information.
     *
     * @param bankAccountRequest the request DTO containing updated account data
     * @param bankAccount the existing BankAccount entity to update (will be modified in-place)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(BankAccountRequest bankAccountRequest, @MappingTarget BankAccount bankAccount);
}


