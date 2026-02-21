package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.AccountStatus;
import com.alaeldin.bank_simulator_service.constant.AccountType;
import com.alaeldin.bank_simulator_service.dto.BankAccountRequest;
import com.alaeldin.bank_simulator_service.dto.BankAccountResponse;
import com.alaeldin.bank_simulator_service.exception.AccountHolderNameAlreadyExist;
import com.alaeldin.bank_simulator_service.mapper.BankAccountMapper;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.repository.BankAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankAccountService Unit Test")
public class BankAccountServiceTest {
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private BankAccountMapper bankAccountMapper;
    @InjectMocks
    private BankAccountService bankAccountService;


    //==========================================================
    // Test Data Builders (Helper methods to create test data)
    //==========================================================

    private BankAccountRequest createValidRequest() {

        return BankAccountRequest.builder()
                .accountHolderName("Alaeldin Musa")
                .balance(new BigDecimal("1000.00"))
                .accountType(AccountType.PERSONAL)
                .build();
    }

    private BankAccount createMockAccount(Long id
            , String accountNumber, String holderName) {
        return BankAccount.builder()
                .id(id)
                .accountNumber(accountNumber)
                .accountHolderName(holderName)
                .balance(new BigDecimal("2000.00"))
                .accountType(AccountType.PERSONAL)
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();


    }

    private BankAccountResponse createMockResponse(String accountNumber, String holderName) {
        return BankAccountResponse
                .builder()
                .accountNumber(accountNumber)
                .accountHolderName(holderName)
                .balance(new BigDecimal("2000.00"))
                .accountType(AccountType.PERSONAL)
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    //==========================================================
    // Test Cases : Create Account Test
    //==========================================================
    @Nested
    @DisplayName("Create Account Tests")
    class CreateAccountTests {
        @Test
        @DisplayName("Should  Successfully create Bank Account with valid request")
        void shouldCreateBakAccount_whenValidRequest_thenAccountCreated() {
            //Given
            BankAccountRequest request = createValidRequest();
            BankAccount bankAccount = createMockAccount(1L, "1234567890", "Alaeldin Musa");
            BankAccountResponse expectedResponse =
                    createMockResponse("1234567890", "Alaeldin Musa");
            //When
            when(bankAccountRepository
                    .existsByAccountHolderName("Alaeldin Musa"))
                    .thenReturn(false);

            when(bankAccountMapper.toEntity(request))
                    .thenReturn(bankAccount);

            when(bankAccountRepository.save(any(BankAccount.class)))
                    .thenReturn(bankAccount);

            when(bankAccountMapper.toDto(bankAccount))
                    .thenReturn(expectedResponse);

            //Act
            BankAccountResponse result = bankAccountService.createBankAccount(request);

            //Assert
            assertThat(result).isNotNull();
            assertThat(result.getAccountNumber()).isNotNull();
            assertThat(result.getAccountHolderName()).isEqualTo("Alaeldin Musa");
            assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("2000.00"));
            assertThat(result.getAccountType()).isEqualTo(AccountType.PERSONAL);
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

            // Verify mock interactions
            verify(bankAccountRepository, times(1)).existsByAccountHolderName("Alaeldin Musa");
            verify(bankAccountMapper, times(1)).toEntity(request);
            verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
            verify(bankAccountMapper, times(1)).toDto(bankAccount);
        }
    }

    @Test
    @DisplayName("Should throw exception when trying to create account with duplicate holder name")
    void shouldThrowException_whenHolderNameExists_thenAccountHolderNameAlreadyExist() {

        //Arrange
        BankAccountRequest request = BankAccountRequest.builder()
                .accountHolderName("Alaeldin Musa")
                .balance(new BigDecimal("2000.00"))
                .accountType(AccountType.PERSONAL)
                .build();

        when(bankAccountRepository.existsByAccountHolderName("Alaeldin Musa"))
                .thenReturn(true);
        //Act & Assert
        assertThatThrownBy(() -> bankAccountService.createBankAccount(request))
                .isInstanceOf(AccountHolderNameAlreadyExist.class)
                .hasMessageContaining("Alaeldin Musa");
        //Verify
        verify(bankAccountRepository).existsByAccountHolderName("Alaeldin Musa");
        verify(bankAccountRepository, never()).save(any(BankAccount.class));
        verify(bankAccountMapper, never()).toEntity(any());
    }

    @Test
    @DisplayName("Should set default values when creating Account")
    void shouldSetDefaultValues_whenCreatingAccount_thenDefaultsAreSet() {
        //Arrange
        BankAccountRequest request = BankAccountRequest.builder()
                .accountHolderName("Alaeldin Musa")
                .balance(new BigDecimal("2000.00"))
                .accountType(AccountType.PERSONAL)
                .build();

        BankAccount bankAccount = new BankAccount();
        when(bankAccountRepository.existsByAccountHolderName(anyString()))
                .thenReturn(false);

        when(bankAccountMapper.toEntity(request))
                .thenReturn(bankAccount);
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(
                invocation -> {

                    BankAccount savedAccount = invocation.getArgument(0);
                    savedAccount.setId(1L);

                    return savedAccount;
                }
        );
        //Act
        bankAccountService.createBankAccount(request);

        //Assert
        ArgumentCaptor<BankAccount> accountCaptor =
                ArgumentCaptor.forClass(BankAccount.class);

        verify(bankAccountRepository).save(accountCaptor.capture());
        BankAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getAccountNumber())
                .isNotNull();
        assertThat(savedAccount.getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);

        assertThat(savedAccount.getCreatedAt())
                .isNotNull();
        assertThat(savedAccount.getUpdatedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("Should  Retrieve all accounts with pagination")
    void shouldRetrieveAllAccounts_withPagination_thenAccountsRetrieved() {
        List<BankAccount> bankAccountList = List.of(
                createMockAccount(1L, "1213344444", "Alaeldin Musa"),
                createMockAccount(2L, "1213344445", "John Doe")
        );

        Page<BankAccount> accountPage = new PageImpl
                <>(bankAccountList,
                PageRequest.of(0, 10),
                bankAccountList.size());

        when(bankAccountRepository.findAll(any(Pageable.class))).
                thenReturn(accountPage);

        when(bankAccountMapper.toDto(any(BankAccount.class)))
                .thenReturn(new BankAccountResponse());

        //Act
        Page<BankAccountResponse> result = bankAccountService
                .getAllAccounts(0, 10);

        //Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
        verify(bankAccountRepository).findAll(any(Pageable.class));
        verify(bankAccountMapper, times(2))
                .toDto(any(BankAccount.class));
    }

    @Test
    @DisplayName("should throw exception when page number is negative")
    void shouldThrowException_whenPageIsNegative_thenIllegalArgumentException() {
        //Act & Assert
        assertThatThrownBy(() -> bankAccountService.getAllAccounts(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page number cannot be negative. Provided: -1");

        verifyNoMoreInteractions(bankAccountRepository, bankAccountMapper);
    }

    @Test
    @DisplayName("Should throw exception when page size is zero or negative")
    void shouldThrowException_whenSizeIsInvalid_thenIllegalArgumentException() {
        //Act & Assert
        assertThatThrownBy(() -> bankAccountService
                .getAllAccounts(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be greater than 0. Provided: 0");

        assertThatThrownBy(() -> bankAccountService.getAllAccounts(0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be greater than 0. Provided: -1");

        verifyNoMoreInteractions(bankAccountRepository, bankAccountMapper);
    }

    @Test
    @DisplayName("Should retrieve Account by ID")
    void shouldRetrieveAccountById_thenAccountRetrieved() {

        // Arrange
        BankAccount bankAccount =
                createMockAccount(1L, "1234567890", "Alaeldin Musa");

        BankAccountResponse bankAccountResponse =
                BankAccountResponse.builder()
                        .accountNumber("1234567890")
                        .accountHolderName("Alaeldin Musa")
                        .balance(new BigDecimal("2000.00"))
                        .accountType(AccountType.PERSONAL)
                        .accountStatus(AccountStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(bankAccountRepository.findById(1L))
                .thenReturn(Optional.of(bankAccount));

        when(bankAccountMapper.toDto(bankAccount))
                .thenReturn(bankAccountResponse);
        //Act
        BankAccountResponse result = bankAccountService
                .getAccountById(bankAccount.getId());

        //Assert
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo("1234567890");
        assertThat(result.getAccountHolderName()).isEqualTo("Alaeldin Musa");


        //verify
        verify(bankAccountRepository).findById(bankAccount.getId());
        verify(bankAccountMapper).toDto(bankAccount);
    }

    @Test
    @DisplayName("Should throw exception when ID is null")
    void shouldThrowException_whenIdIsNull_thenIllegalArgumentException() {
        assertThatThrownBy(() -> bankAccountService.getAccountById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account ID cannot be null");

        verifyNoMoreInteractions(bankAccountRepository, bankAccountMapper);
    }

    @Test
    @DisplayName("Should throw exception when ID is zero or negative")
    void shouldThrowException_whenIdIsNonPositive_thenIllegalArgumentException() {

        assertThatThrownBy(() -> bankAccountService.getAccountById(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account ID must be greater than 0. Provided: 0");

        assertThatThrownBy(()-> bankAccountService.getAccountById(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account ID must be greater than 0. Provided: -1");
        verifyNoMoreInteractions(bankAccountRepository, bankAccountMapper);
    }
}