package com.alaeldin.bank_simulator_service.component;

import com.alaeldin.bank_simulator_service.util.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserUtil
{

    private final HttpServletRequest request;

    public  CurrentUserUtil(HttpServletRequest request)
    {
        this.request = request;
    }

    /**
     * Function getUserName From Header
     * @return UserName String
     */
    public String getUserName()
    {
        String username = request.getHeader(GatewayHeaders.HEADER_USERNAME);
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("Username header is missing. Request must be authenticated through API Gateway.");
        }
        return username;
    }

    /**
     * Function getUserId From Header
     * @return UserName String
     */
    public Long getUserId()
    {
        String userIdHeader = request.getHeader(GatewayHeaders.HEADER_USER_ID);
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            throw new IllegalStateException("User ID header is missing. Request must be authenticated through API Gateway.");
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid User ID header format: " + userIdHeader, e);
        }
    }

    /**
     * Function getRoleFromHeader
     * @return Role String
     */
    public String getRole()
    {
        String role = request.getHeader(GatewayHeaders.HEADER_USER_ROLE);
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalStateException("User role header is missing. Request must be authenticated through API Gateway.");
        }
        return role;
    }

    public boolean isAdmin()
    {
        String role = request.getHeader(GatewayHeaders.HEADER_USER_ROLE);
        return "ADMIN".equals(role);
    }
}
