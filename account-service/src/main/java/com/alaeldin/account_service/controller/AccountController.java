package com.alaeldin.account_service.controller;

import com.alaeldin.account_service.dto.AccountRequest;
import com.alaeldin.account_service.dto.AccountResponse;
import com.alaeldin.account_service.dto.UpdateStatusAccountRequest;
import com.alaeldin.account_service.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest accountRequest)
    {
          AccountResponse accountResponse = accountService.createAccount(accountRequest);

          return ResponseEntity.status(HttpStatus.CREATED).body(accountResponse);
    }

    @PutMapping("/")
    public ResponseEntity<AccountResponse> updateAccountStatus(@Valid @RequestBody UpdateStatusAccountRequest accountRequest)
    {
        return ResponseEntity.ok(accountService.changeStatusAccount(accountRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long id)
    {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}
