package com.alaeldin.Auth_service.component;

import com.alaeldin.Auth_service.util.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserUtil {

    private final HttpServletRequest request;

    public CurrentUserUtil(HttpServletRequest request) {
        this.request = request;
    }

    public String getUserName()
    {
        String userName = request.getHeader(GatewayHeaders.HEADER_USERNAME);
        if (userName == null || userName.isEmpty())
        {
            throw new RuntimeException("Username Header  is null or empty");
        }

        return userName;
    }

    public Long getUserId()
    {
        String userIdHeader = request.getHeader(GatewayHeaders.HEADER_USER_ID);
        if (userIdHeader == null || userIdHeader.isEmpty())
        {
            throw new RuntimeException("UserId Header  is null or empty");
        }
        try
        {
            return Long.parseLong(userIdHeader);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("UserId Header is invalid");
        }
    }

    public String getRole()
    {
        String role = request.getHeader(GatewayHeaders.HEADER_USER_ROLE);
        if (role == null || role.isEmpty())
        {
            throw new RuntimeException("Role Header is null or empty");
        }

        return role;
    }

    public boolean isAdmin()
    {
        String role = request.getHeader(GatewayHeaders.HEADER_USER_ROLE);
        return role != null && role.equals("ADMIN");
    }
}
