package com.alaeldin.bank_query_service.mapper;

import com.alaeldin.bank_query_service.dto.AccountQueryResponse;
import com.alaeldin.bank_query_service.model.readmodel.AccountReadModel;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface AccountQueryMapper {

     AccountReadModel toEntity(AccountQueryResponse accountQueryResponse);
    AccountQueryResponse toDto(AccountReadModel accountReadModel);

}
