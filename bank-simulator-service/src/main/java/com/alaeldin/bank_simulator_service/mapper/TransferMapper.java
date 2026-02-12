package com.alaeldin.bank_simulator_service.mapper;

import com.alaeldin.bank_simulator_service.dto.TransferResponse;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface TransferMapper {

    // Only keeping the response mapping as the request mapping is handled manually in service
    TransferResponse toDto(BankTransaction bankTransaction);
}
