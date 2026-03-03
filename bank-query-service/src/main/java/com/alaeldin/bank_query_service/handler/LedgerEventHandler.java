package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.model.event.LedgerEvent;
import com.alaeldin.bank_query_service.model.readmodel.LedgerReadModel;
import com.alaeldin.bank_query_service.repository.LedgerReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LedgerEventHandler {

    private final LedgerReadModelRepository ledgerReadModelRepository;

    public void handleLedgerEntryCreated(LedgerEvent ledgerEntryEvent) {
        log.info("LedgerEventHandler handleLedgerEntryCreated: {}", ledgerEntryEvent);

        try {
            // Check for duplicate entry (idempotency)
            if (ledgerReadModelRepository.findByLedgerEntryId(ledgerEntryEvent.getLedgerEntryId()).isPresent()) {
                log.info("⚠️ LedgerEntry already exists for ledgerEntryId: {}, skipping duplicate",
                        ledgerEntryEvent.getLedgerEntryId());
                return;
            }

            LedgerReadModel ledgerReadModel = LedgerReadModel.builder()
                    .id(ledgerEntryEvent.getId())
                    .accountNumber(ledgerEntryEvent.getAccountNumber())
                    .transactionReference(ledgerEntryEvent.getTransactionReference())
                    .entryType(ledgerEntryEvent.getEntryType())
                    .ledgerEntryId(ledgerEntryEvent.getLedgerEntryId())
                    .amount(ledgerEntryEvent.getAmount())
                    .balanceBefore(ledgerEntryEvent.getBalanceBefore())
                    .balanceAfter(ledgerEntryEvent.getBalanceAfter())
                    .entryDate(ledgerEntryEvent.getEntryDate())
                    .description(ledgerEntryEvent.getDescription())
                    .build();

            ledgerReadModelRepository.save(ledgerReadModel);
            log.info("LedgerReadModel saved: ledgerEntryId={}, account={}, entryType={}",
                    ledgerReadModel.getLedgerEntryId(),
                    ledgerReadModel.getAccountNumber(),
                    ledgerReadModel.getEntryType());

            // The LedgerEvent already carries the accountNumber it belongs to.
            // No need to look up the TransactionReadModel — that avoids a race condition
            // where the LEDGER event arrives before TRANSACTION_COMPLETED is processed.


        } catch (Exception e) {
            log.error("LedgerEventHandler handleLedgerEntryCreated error: ", e);
            throw e;
        }
    }
}
