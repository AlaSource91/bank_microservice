package com.alaeldin.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.jboss.logging.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Global Logging Filter for API Gateway.
 * 
 * <p>Logs all incoming requests and outgoing responses with timing information.
 * Helps with debugging, monitoring, and auditing.</p>
 * 
 * <p>This filter runs with HIGHEST_PRECEDENCE to capture all requests before
 * any other processing occurs.</p>
 * 
 * @author Alaeldin
 * @version 1.0
 */
@Slf4j
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static String MDC_CORRELATION_ID = "correlationId";
    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Instant startTime = Instant.now();

        /**********************************************
         * GET OR CREATE CORRELATION ID
         ************************************************/
        String correlationId = Optional.ofNullable(request.getHeaders().getFirst(CORRELATION_ID_HEADER))
                .orElse(UUID.randomUUID().toString());
         //========================================
        //  PUT INTO MDC (FOR LOGGING)
       // ===========================================
        MDC.put(MDC_CORRELATION_ID, correlationId);

        // ==============================
        // 3. ADD TO REQUEST HEADERS
        // ==============================
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(MDC_CORRELATION_ID, correlationId).build();

        // Store start time in exchange attributes
        exchange.getAttributes().put(START_TIME_ATTRIBUTE, startTime);

        log.info("→ Incoming Request [{}]: {} {} - Client: {} - Headers: {}",
                correlationId,
                request.getMethod(),
                request.getURI().getPath(),
                getClientIp(request),
                sanitizeHeaders(request.getHeaders()));

        // Continue the filter chain and log response
        return chain.filter(exchange)
                .doOnError(throwable -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("✗ Request Failed [{}]: {} {} - Error: {} - Duration: {}ms",
                            correlationId,
                            request.getMethod(),
                            request.getURI().getPath(),
                            throwable.getMessage(),
                            duration.toMillis());
                })
                .then(Mono.fromRunnable(() -> {
                    ServerHttpResponse response = exchange.getResponse();
                    Duration duration = Duration.between(startTime, Instant.now());

                    log.info("← Outgoing Response [{}]: {} {} - Status: {} - Duration: {}ms",
                            correlationId,
                            request.getMethod(),
                            request.getURI().getPath(),
                            response.getStatusCode(),
                            duration.toMillis());

                    // ==============================
                    // 4. CLEAN MDC (VERY IMPORTANT)
                    // ==============================
                    MDC.clear();
                }));
    }

    /**
     * Extracts client IP address from request.
     * Checks X-Forwarded-For header first, then falls back to remote address.
     * 
     * @param request ServerHttpRequest
     * @return client IP address
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    /**
     * Sanitizes request headers to avoid logging sensitive information.
     * Removes Authorization, Cookie, and other sensitive headers.
     * 
     * @param headers HttpHeaders
     * @return sanitized headers map
     */
    private Map<String, String> sanitizeHeaders(org.springframework.http.HttpHeaders headers) {
        Map<String, String> sanitized = new HashMap<>();
        
        headers.forEach((key, value) -> {
            // Skip sensitive headers
            if (isSensitiveHeader(key)) {
                sanitized.put(key, "***REDACTED***");
            } else {
                sanitized.put(key, String.join(",", value));
            }
        });
        
        return sanitized;
    }

    /**
     * Checks if a header contains sensitive information.
     * 
     * @param headerName header name
     * @return true if sensitive, false otherwise
     */
    private boolean isSensitiveHeader(String headerName) {
        String lowerCase = headerName.toLowerCase();
        return lowerCase.equals("authorization") 
                || lowerCase.equals("cookie") 
                || lowerCase.equals("set-cookie")
                || lowerCase.contains("token")
                || lowerCase.contains("password");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

