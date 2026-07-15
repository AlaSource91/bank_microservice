package com.alaeldin.account_service.dto;


import com.alaeldin.account_service.constant.AccountStatus;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UpdateStatusAccountRequest {

    private Long id;
    private AccountStatus status;
}
