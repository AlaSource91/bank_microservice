package com.alaeldin.bank_query_service.service;

import com.alaeldin.bank_query_service.repository.AccountStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountStatisticsService
{
    private final AccountStatisticsRepository accountStatisticsRepository;


}
