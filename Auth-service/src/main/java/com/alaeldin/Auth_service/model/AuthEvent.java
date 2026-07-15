package com.alaeldin.Auth_service.model;


import com.alaeldin.Auth_service.constant.AuthEventType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthEvent {

    @Builder.Default
    private String eventId = java.util.UUID.randomUUID().toString();
    private AuthEventType eventType;
    private Long userId;
    private Set<String> roles;
    private String ipAddress;
    @Builder.Default
    private LocalDateTime timeStamp = LocalDateTime.now();
}
