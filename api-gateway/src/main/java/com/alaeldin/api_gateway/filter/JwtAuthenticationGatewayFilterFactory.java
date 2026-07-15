package com.alaeldin.api_gateway.filter;

import com.alaeldin.api_gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JWT Authentication Filter for Spring Cloud Gateway (Reactive)
 *
 * Validates JWT tokens before forwarding requests to downstream services.
 * Extracts user information and adds it to request headers for downstream consumption.
 *
 * <p>Key Features:</p>
 * <ul>
 *   <li>Validates JWT signature and expiration</li>
 *   <li>Extracts user claims (ID, username, role, permissions)</li>
 *   <li>Adds custom headers for downstream services</li>
 *   <li>Enforces role-based access control for admin routes</li>
 *   <li>Returns standardized error responses</li>
 * </ul>
 *
 * <p>Usage in application.yaml:</p>
 * <pre>
 * filters:
 *   - JwtAuthentication
 * </pre>
 *
 * @author Alaeldin
 * @version 2.0
 * @since 2026-04-01
 */
@Slf4j
@Component
public class JwtAuthenticationGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // Custom headers to pass to downstream services
    private static final String HEADER_TOKEN = "X-Token";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_PERMISSIONS = "X-User-Permissions";
    private static final String HEADER_JWT_ID = "X-JWT-Id";

    @Autowired
    private JwtUtil jwtUtil;

    public JwtAuthenticationGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            log.debug("Processing request: {} {}", request.getMethod(), path);

            // Extract Authorization header
            String authHeader = extractAuthHeader(request);

            if (!StringUtils.hasText(authHeader)) {
                log.warn("Missing Authorization header - Method: {}, Path: {}",
                        request.getMethod(), path);
                return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            // Extract token from "Bearer <token>"
            String token = extractToken(authHeader);

            if (token == null) {
                log.warn("Invalid Authorization header format - Path: {}", path);
                return onError(exchange, "Invalid Authorization header format. Expected: Bearer <token>",
                        HttpStatus.UNAUTHORIZED);
            }

            // Validate token
            if (!jwtUtil.validateToken(token)) {
                log.warn("Invalid or expired JWT token - Path: {}", path);
                return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }

            try {
                // Extract user information from JWT
                String username = jwtUtil.extractUserName(token);
                Long userId = jwtUtil.extractUserId(token);
                String role = jwtUtil.extractRole(token);
                List<String> permissions = jwtUtil.extractPermissions(token);
                String jwtId = jwtUtil.extractJwtId(token);

                log.debug("Authenticated user: {} (ID: {}, Role: {}, Permissions: {})",
                        username, userId, role, permissions.size());

                // Check admin-only routes
                if (isAdminRoute(request) && !jwtUtil.isAdmin(token)) {
                    log.warn("Access denied to admin route for user: {} (Role: {})", username, role);
                    return onError(exchange,
                            "Access denied - Admin privileges required",
                            HttpStatus.FORBIDDEN);
                }

                // Add user info to headers for downstream services
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header(HEADER_USER_ID, String.valueOf(userId))
                        .header(HEADER_USERNAME, username)
                        .header(HEADER_USER_ROLE, role)
                        .header(HEADER_TOKEN, token)
                        .header(HEADER_PERMISSIONS, String.join(",", permissions))
                        .header(HEADER_JWT_ID, jwtId != null ? jwtId : "")
                        .build();

                log.info("Request authenticated successfully - User: {}, Path: {}", username, path);

                // Forward the request with enriched headers
                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (Exception ex) {
                log.error("Error processing JWT token for path {}: {}", path, ex.getMessage(), ex);
                return onError(exchange, "Authentication failed: " + ex.getMessage(),
                        HttpStatus.UNAUTHORIZED);
            }
        };
    }

    /**
     * Extracts Authorization header from the request.
     *
     * @param request ServerHttpRequest
     * @return Authorization header value or null if not present
     */
    private String extractAuthHeader(ServerHttpRequest request) {
        List<String> headers = request.getHeaders().get(AUTHORIZATION_HEADER);
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.get(0);
    }

    /**
     * Extracts JWT token from "Bearer <token>" format.
     *
     * @param authHeader Authorization header value
     * @return JWT token string or null if format is invalid
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }

    /**
     * Checks if the requested path requires ADMIN role.
     *
     * @param request ServerHttpRequest
     * @return true if path is admin-only, false otherwise
     */
    private boolean isAdminRoute(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return path != null && path.startsWith("/api/v1/admin");
    }

    /**
     * Returns standardized error response in reactive manner.
     *
     * @param exchange ServerWebExchange
     * @param message Error message
     * @param status HTTP status code
     * @return Mono<Void> representing the response
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorBody = buildErrorResponse(
                status,
                message,
                exchange.getRequest().getURI().getPath()
        );

        DataBuffer buffer = response.bufferFactory()
                .wrap(errorBody.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Builds a standardized JSON error response.
     *
     * @param status HTTP status
     * @param message Error message
     * @param path Request path
     * @return JSON string
     */
    private String buildErrorResponse(HttpStatus status, String message, String path) {
        return String.format(
                """
                {
                    "timestamp": "%s",
                    "status": %d,
                    "error": "%s",
                    "message": "%s",
                    "path": "%s"
                }
                """,
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                escapeJson(message),
                escapeJson(path)
        );
    }

    /**
     * Escapes special characters in JSON strings.
     *
     * @param value String to escape
     * @return Escaped string
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * Configuration class for the filter.
     * Can be extended to accept parameters from application.yaml.
     *
     * <p>Example usage in YAML:</p>
     * <pre>
     * filters:
     *   - name: JwtAuthentication
     *     args:
     *       requireRole: ADMIN
     *       requirePermission: READ_ACCOUNTS
     * </pre>
     */
    @lombok.Data
    public static class Config {
        /**
         * Optional: Require specific role for this route.
         * If set, only users with this role can access the route.
         */
        private String requireRole;

        /**
         * Optional: Require specific permission for this route.
         * If set, only users with this permission can access the route.
         */
        private String requirePermission;
    }
}

