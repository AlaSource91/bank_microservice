package com.alaeldin.Auth_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@NoArgsConstructor
public class AddNewUserRequest {

    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phone;
    private String nationalId;
    private String identityFilePath;
    private String password;

    @JsonSetter
    public void setEmail(String email) {
        this.email = email != null ? email.trim() : null;
    }
    @JsonSetter("password")
    public void setPassword(String password) {
        this.password = password != null ? password.trim() : null;
    }



}
