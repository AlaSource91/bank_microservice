package com.alaeldin.api_gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for API Gateway.
 * 
 * <p>Handles all exceptions that occur during request processing and returns
 * standardized JSON error responses to clients.</p>
 * 
 * <p>This handler has higher priority (-2) than the default Spring error handler
 * to ensure all gateway exceptions are caught and properly formatted.</p>
 * 
 * @author Alaeldin
 * @version 1.0
 */
@Slf4j
@Component
@Order(-2) // Higher priority than default error handler
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        String path = exchange.getRequest().getURI().getPath();
        
        log.error("Error occurred - Path: {}, Error: {}", path, ex.getMessage(), ex);

        HttpStatus status = determineHttpStatus(ex);
        String message = determineErrorMessage(ex);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorResponse = buildErrorResponse(status, message, path);
        byte[] bytes = errorResponse.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Determines appropriate HTTP status code based on exception type.
     * 
     * @param ex the exception
     * @return HTTP status code
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        return switch (ex) {
            case UnauthorizedException ue -> HttpStatus.UNAUTHORIZED;
            case RateLimitExceededException rle -> HttpStatus.TOO_MANY_REQUESTS;
            case ResponseStatusException rse -> HttpStatus.resolve(rse.getStatusCode().value());
            case IllegalArgumentException iae -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Determines appropriate error message based on exception type.
     * 
     * @param ex the exception
     * @return error message
     */
    private String determineErrorMessage(Throwable ex) {
        return switch (ex) {
            case UnauthorizedException ue -> ue.getMessage();
            case RateLimitExceededException rle -> rle.getMessage();
            case ResponseStatusException rse -> rse.getReason();
            case IllegalArgumentException iae -> iae.getMessage();
            default -> "An unexpected error occurred";
        };
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
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now().toString());
        errorDetails.put("status", status.value());
        errorDetails.put("error", status.getReasonPhrase());
        errorDetails.put("message", message);
        errorDetails.put("path", path);

        try {
            return objectMapper.writeValueAsString(errorDetails);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            // Fallback to simple JSON
            return String.format(
                    "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                    LocalDateTime.now(), status.value(), status.getReasonPhrase(), 
                    escapeJson(message), escapeJson(path)
            );
        }
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
}

