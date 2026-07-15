package com.alaeldin.account_service.component;

import com.alaeldin.account_service.util.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserUtil {

    private final HttpServletRequest request;

    public CurrentUserUtil(HttpServletRequest request) {
        this.request = request;
    }

    /**
     *  Function getUserName  From Header
     * @return UserName String
     */
    public  String getUserName() {

        String userName = request.getHeader(GatewayHeaders.HEADER_USER_NAME);

        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("UserName Header is Missing");
        }

        return userName;
    }
    public String getToken()
    {
        String token = request.getHeader(GatewayHeaders.HEADER_TOKEN);
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token is Missing");
        }

        return token;
    }
    public  Long getUserId()
    {
        String userId  = request.getHeader(GatewayHeaders.HEADER_USER_ID);
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("UserId Header is Missing");
        }
        try{
            return Long.parseLong(userId);
    }
        catch (NumberFormatException e) {
          throw new IllegalStateException("Invalid User ID header format: " + userId, e);
        }
    }

    public  String getRole()
    {
        String role = request.getHeader(GatewayHeaders.HEADER_USER_ROLE);
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("UserRole Header is Missing");
        }
        return role;
    }

    public boolean isAdmin()
    {
        String role = request.getHeader(GatewayHeaders.HEADER_USER_ROLE);
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("UserRole Header is Missing");
        }
        return role.equals("ADMIN");
    }
}
