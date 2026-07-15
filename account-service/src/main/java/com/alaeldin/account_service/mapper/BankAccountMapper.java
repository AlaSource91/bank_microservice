package com.alaeldin.account_service.mapper;

import com.alaeldin.account_service.dto.AccountRequest;
import com.alaeldin.account_service.dto.AccountResponse;
import com.alaeldin.account_service.model.BankAccount;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public  interface BankAccountMapper {

    BankAccount toEntity(AccountRequest accountRequest);

    AccountResponse toAccountResponse(BankAccount bankAccount);

}
