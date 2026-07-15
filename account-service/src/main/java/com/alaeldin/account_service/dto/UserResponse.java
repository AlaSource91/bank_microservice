package com.alaeldin.account_service.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter

@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse
{
   private Long userId;
   private String firstName;
   private String middleName;
   private String lastName;
   private String email;
   private String phone;
   private Boolean isActive;

}
