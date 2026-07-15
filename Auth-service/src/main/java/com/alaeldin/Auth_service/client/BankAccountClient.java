package com.alaeldin.Auth_service.client;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankAccountClient {

    private final WebClient bankSimulatorWebClient;

    public List<String> getUserAccounts(Long userId)
    {
       try
       {
          log.debug("Fetching accounts for userId={}", userId);
          List<String> accounts = bankSimulatorWebClient
                  .get()
                  .uri("/internal/users/{userId}/accounts", userId)
                  .retrieve()
                  .bodyToMono(new ParameterizedTypeReference<List<String>>(){})
                  .timeout(Duration.ofSeconds(5))
                  .block();

           log.debug("Retrieved {} accounts for userId={}",
                   accounts != null ? accounts.size() : 0, userId);

           return accounts != null ? accounts : Collections.emptyList();
       }
       catch(WebClientResponseException e)

       {
           log.error("Error fetching accounts for userId={}: {} - {}",
                   userId, e.getStatusCode(), e.getMessage());
           return Collections.emptyList();
       }
       catch(Exception e){

           log.error("Unexpected error fetching accounts for userId={}: {}",
                   userId, e.getMessage());
           return Collections.emptyList();
       }
    }

}
