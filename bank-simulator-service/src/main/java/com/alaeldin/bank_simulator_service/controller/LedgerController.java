package com.alaeldin.bank_simulator_service.controller;

import com.alaeldin.bank_simulator_service.dto.LedgerEntriesPage;
import com.alaeldin.bank_simulator_service.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * REST Controller for ledger operations and cache testing.
 */
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
@Slf4j
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * Get account balance at a specific point in time.
     * This method demonstrates the caching behavior.
     */
    @GetMapping("/balance/{accountId}")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable Long accountId,
            @RequestParam(required = false) String dateTime) {

        LocalDateTime queryTime = dateTime != null ?
            LocalDateTime.parse(dateTime) :
            LocalDateTime.now();

        log.info("🔍 API Request - Balance for account: {} at time: {}", accountId, queryTime);

        BigDecimal balance = ledgerService.getBalanceAsOf(accountId, queryTime);

        return ResponseEntity.ok(balance);
    }

    /**
     * Get paginated ledger entries for an account.
     * Returns cacheable DTO structure.
     */
    @GetMapping("/entries/{accountId}")
    public ResponseEntity<LedgerEntriesPage> getLedgerEntries(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info(" API Request - Ledger entries for account: {} (page: {}, size: {})", accountId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        LedgerEntriesPage entries = ledgerService.getLedgerEntriesForAccount(accountId, pageable);

        return ResponseEntity.ok(entries);
    }

    /**
     * Clear cache for testing purposes.
     */
    @DeleteMapping("/cache/{accountId}")
    public ResponseEntity<String> clearCache(@PathVariable Long accountId) {
        log.info(" API Request - Clear cache for account: {}", accountId);
        ledgerService.clearAccountCache(accountId);
        return ResponseEntity.ok("Cache cleared for account: " + accountId);
    }

    /**
     * Clear all caches.
     */
    @DeleteMapping("/cache")
    public ResponseEntity<String> clearAllCaches() {
        log.info(" API Request - Clear all ledger caches");
        ledgerService.clearAllCaches();
        return ResponseEntity.ok("All ledger caches cleared");
    }

    /**
     * Get cache status information.
     */
    @GetMapping("/cache/status")
    public ResponseEntity<String> getCacheStatus() {
        String status = ledgerService.getCacheStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Test cache behavior by making multiple requests.
     */
    @GetMapping("/test/cache/{accountId}")
    public ResponseEntity<String> testCache(@PathVariable Long accountId) {
        log.info(" Starting cache test for account: {}", accountId);

        LocalDateTime testTime = LocalDateTime.now().withSecond(0).withNano(0); // Round to minute

        // First call - should be cache MISS
        long start1 = System.currentTimeMillis();
        BigDecimal balance1 = ledgerService.getBalanceAsOf(accountId, testTime);
        long duration1 = System.currentTimeMillis() - start1;
        log.info("First call (MISS): {}ms - Balance: {}", duration1, balance1);

        // Second call - should be cache HIT
        long start2 = System.currentTimeMillis();
        BigDecimal balance2 = ledgerService.getBalanceAsOf(accountId, testTime);
        long duration2 = System.currentTimeMillis() - start2;
        log.info("⚡ Second call (HIT): {}ms - Balance: {}", duration2, balance2);

        String result = String.format("Cache test completed:\n" +
                "First call (MISS): %dms\n" +
                "Second call (HIT): %dms\n" +
                "Speed improvement: %.1fx\n" +
                "Balance: %s",
                duration1, duration2,
                (double) duration1 / Math.max(duration2, 1),
                balance1);

        return ResponseEntity.ok(result);
    }
}
