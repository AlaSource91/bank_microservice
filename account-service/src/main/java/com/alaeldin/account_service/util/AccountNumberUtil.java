package com.alaeldin.account_service.util;

import java.time.Year;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;

public class AccountNumberUtil
{
    private static final String ACCOUNT_PREFIX = "AE";
    private static final int RANDOM_MIN = 10_000_000;
    private static final int RANDOM_MAX = 100_000_000;

   private AccountNumberUtil()
   {
       throw new AssertionError("Cannot instantiate utility class");
   }

   public static String generateAccountNumber()
   {
       int currentYear = Year.now().getValue();

       //Generate random 8-digit number (10000000 to 99999999)
       int  randomDigits = ThreadLocalRandom.current().nextInt(RANDOM_MIN, RANDOM_MAX);

       return ACCOUNT_PREFIX + currentYear + randomDigits;
   }
}
