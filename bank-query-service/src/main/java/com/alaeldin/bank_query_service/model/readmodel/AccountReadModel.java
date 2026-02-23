package com.alaeldin.bank_query_service.model.readmodel;


import com.alaeldin.bank_query_service.constant.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountReadModel {

    @Id
    private String id;
    @Indexed(unique = true)
    private String accountNumber;
    @Indexed
    private String accountHolderName;
    private String accountType;
    private BigDecimal balance;
    private AccountStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String version;
    private String applicationName;


}
