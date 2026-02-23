package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.constant.AccountEventType;
import com.alaeldin.bank_query_service.model.event.AccountEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventHandleRegistry {
    private final AccountEventHandler accountEventHandler;
    private TransactionEventHandler transactionEventHandler;
    public void handleEvent(AccountEvent accountEvent, String accountNumber,String id) {

        String eventType = accountEvent.getEventType();
        log.debug("Received event of type: {}", eventType);
        switch (eventType)
        {
            case "ACCOUNT_CREATED":
                accountEventHandler.handleAccountCreated(accountEvent);
                break;
            case "ACCOUNT_FROZEN":
                accountEventHandler.handleAccountFrozen(accountEvent, accountNumber);
                 break;
            case "ACCOUNT_UPDATED":
                accountEventHandler.handleAccountUpdated(accountEvent,id);
                break;
            default:
                log.warn("Unhandled event type: {}", eventType);
        }
    }
}
