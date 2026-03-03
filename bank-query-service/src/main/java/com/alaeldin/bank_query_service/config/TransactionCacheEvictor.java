package com.alaeldin.bank_query_service.config;

import com.alaeldin.bank_query_service.model.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Evicts transaction-related cache entries from Redis after a new event is
 * persisted to the read model.
 *
 * <p>Cache name / key conventions (must match {@code @Cacheable} declarations):</p>
 * <ul>
 *   <li>{@code transactionHistory} — key: {@code <ACCOUNT>_<PAGE>_<SIZE>_<SORT>}</li>
 *   <li>{@code transactions}       — key: {@code <TRANSACTION_ID_UPPERCASE>}</li>
 * </ul>
 *
 * <p>Uses Redis {@code SCAN} instead of {@code KEYS} to avoid blocking the server
 * on large keyspaces.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionCacheEvictor {

    // RedisTemplate<String, String> matches the bean declared in RedisConfig.
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Evicts all {@code transactionHistory} cache entries that involve either
     * the source or destination account, and the {@code transactions} entry for
     * the specific transaction ID, and all {@code searchTransactions} entries
     * since any new transaction can fall within a previously cached date range.
     *
     * @param event the transaction event whose accounts/ID should be evicted
     */
    public void evict(TransactionEvent event) {
        evictHistoryForAccount(event.getSourceAccount());
        evictHistoryForAccount(event.getDestinationAccount());
        evictSingleTransaction(event.getTransactionId());
        evictSearchTransactions();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Evicts all {@code transactionHistory} pages that include the given account,
     * whether the account appears as source or destination.
     *
     * <p>Key format: {@code transactionHistory::<ACCOUNT>_<PAGE>_<SIZE>_<SORT>}</p>
     */
    private void evictHistoryForAccount(String accountNumber) {
        if (!StringUtils.hasText(accountNumber)) {
            log.warn("Skipping transactionHistory eviction — accountNumber is blank or null");
            return;
        }
        String normalized = accountNumber.trim().toUpperCase();
        // Keys where this account is the subject (source or destination passed as single param)
        deleteByPattern("transactionHistory::" + normalized + "_*");
        log.debug("Evicted transactionHistory cache for account: {}", normalized);
    }

    /**
     * Evicts all {@code searchTransactions} cache entries.
     *
     * <p>A new transaction can fall inside any previously cached date-range window,
     * so the entire {@code searchTransactions} cache must be invalidated.
     * The cache name matches the {@code @Cacheable(value = "searchTransactions")}
     * declaration in {@code TransactionQueryService}.</p>
     *
     * <p>Key format: {@code searchTransactions::search_<startDate>_<endDate>_<page>_<size>_<sort>}</p>
     */
    private void evictSearchTransactions() {
        deleteByPattern("searchTransactions::*");
        log.debug("Evicted all searchTransactions cache entries");
    }

    /**
     * Evicts the single-transaction cache entry for the given transaction ID.
     *
     * <p>Key format: {@code transactions::<TRANSACTION_ID_UPPERCASE>}</p>
     */
    private void evictSingleTransaction(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            log.warn("Skipping transactions eviction — transactionId is blank or null");
            return;
        }
        String key = "transactions::" + transactionId.trim().toUpperCase();
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Evicted transactions cache for transactionId: {} (deleted={})", transactionId, deleted);
    }

    /**
     * Scans Redis for keys matching {@code pattern} using {@code SCAN} (non-blocking)
     * and deletes them in batches of 100.
     *
     * @param pattern Redis glob-style pattern
     */
    private void deleteByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            List<String> batch = new ArrayList<>(100);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 100) {
                    redisTemplate.delete(batch);
                    log.debug("Evicted batch of {} cache key(s) matching pattern: {}", batch.size(), pattern);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
                log.debug("Evicted {} cache key(s) matching pattern: {}", batch.size(), pattern);
            }
        } catch (Exception e) {
            log.error("Error scanning Redis keys for pattern '{}': {}", pattern, e.getMessage(), e);
        }
    }
}
