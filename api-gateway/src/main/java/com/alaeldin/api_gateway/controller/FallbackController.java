package com.alaeldin.api_gateway.controller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
/**
 * Fallback Controller for Circuit Breaker.
 * 
 * <p>Provides fallback responses when downstream services are unavailable
 * or experiencing issues. This ensures graceful degradation of the system.</p>
 * 
 * <p>Each fallback endpoint corresponds to a specific service and returns
 * a standardized error response indicating service unavailability.</p>
 * 
 * <p><strong>Updated to handle all HTTP methods</strong> (GET, POST, PUT, DELETE, PATCH)
 * to prevent 405 METHOD_NOT_ALLOWED errors when circuit breaker is open.</p>
 * 
 * @author Alaeldin
 * @version 2.0
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {
    /**
     * Fallback for Auth Service.
     * Triggered when the Auth Service is down or circuit is open.
     * Handles all HTTP methods to prevent 405 errors.
     * 
     * @return ResponseEntity with error details
     */
    @RequestMapping(value = "/auth", method = {
            RequestMethod.GET, 
            RequestMethod.POST, 
            RequestMethod.PUT, 
            RequestMethod.DELETE, 
            RequestMethod.PATCH
    })
    public ResponseEntity<Map<String, Object>> authFallback() {
        log.warn("⚠ Auth Service is currently unavailable - Fallback triggered");
        return buildFallbackResponse(
                "Auth Service is temporarily unavailable. Please try again later."
        );
    }
    /**
     * Fallback for Bank Query Service.
     * Triggered when the Bank Query Service is down or circuit is open.
     * Handles all HTTP methods to prevent 405 errors.
     * 
     * @return ResponseEntity with error details
     */
    @RequestMapping(value = "/query", method = {
            RequestMethod.GET, 
            RequestMethod.POST, 
            RequestMethod.PUT, 
            RequestMethod.DELETE, 
            RequestMethod.PATCH
    })
    public ResponseEntity<Map<String, Object>> queryFallback() {
        log.warn("⚠ Bank Query Service is currently unavailable - Fallback triggered");
        return buildFallbackResponse(
                "Bank Query Service is temporarily unavailable. Please try again later."
        );
    }
    /**
     * Fallback for Account Service.
     * Triggered when the Account Service is down or circuit is open.
     * Handles all HTTP methods to prevent 405 errors.
     * 
     * @return ResponseEntity with error details
     */
    @RequestMapping(value = "/account", method = {
            RequestMethod.GET, 
            RequestMethod.POST, 
            RequestMethod.PUT, 
            RequestMethod.DELETE, 
            RequestMethod.PATCH
    })
    public ResponseEntity<Map<String, Object>> accountFallback() {
        log.warn("⚠ Account Service is currently unavailable - Fallback triggered");
        return buildFallbackResponse(
                "Account Service is temporarily unavailable. Please try again later."
        );
    }
    
    /**
     * Fallback for Bank Simulator Service.
     * Triggered when the Bank Simulator Service is down or circuit is open.
     * Handles all HTTP methods to prevent 405 errors.
     * 
     * @return ResponseEntity with error details
     */
    @RequestMapping(value = "/simulator", method = {
            RequestMethod.GET, 
            RequestMethod.POST, 
            RequestMethod.PUT, 
            RequestMethod.DELETE, 
            RequestMethod.PATCH
    })
    public ResponseEntity<Map<String, Object>> simulatorFallback() {
        log.warn("⚠ Bank Simulator Service is currently unavailable - Fallback triggered");
        return buildFallbackResponse(
                "Bank Simulator Service is temporarily unavailable. Please try again later."
        );
    }
    /**
     * Fallback for Admin Service.
     * Triggered when the Admin Service is down or circuit is open.
     * Handles all HTTP methods to prevent 405 errors.
     * 
     * @return ResponseEntity with error details
     */
    @RequestMapping(value = "/admin", method = {
            RequestMethod.GET, 
            RequestMethod.POST, 
            RequestMethod.PUT, 
            RequestMethod.DELETE, 
            RequestMethod.PATCH
    })
    public ResponseEntity<Map<String, Object>> adminFallback() {
        log.warn("⚠ Admin Service is currently unavailable - Fallback triggered");
        return buildFallbackResponse(
                "Admin Service is temporarily unavailable. Please try again later."
        );
    }
    /**
     * Builds a standardized fallback response.
     * 
     * @param message custom error message
     * @return ResponseEntity with error details
     */
    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        response.put("message", message);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
