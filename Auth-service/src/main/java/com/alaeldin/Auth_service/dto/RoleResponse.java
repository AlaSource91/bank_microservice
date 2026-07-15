package com.alaeldin.Auth_service.dto;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String description;
    /** Flat set of permission keys, e.g. {@code "ACCOUNT:READ"}. */
    private Set<String> permissions;
}
