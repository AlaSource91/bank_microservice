package com.alaeldin.bank_simulator_service.util;

import java.time.Year;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for generating unique bank account numbers.
 * Provides helper methods for account number generation following a specific format.
 *
 * <p>Account Number Format: {PREFIX}{YEAR}{RANDOM_DIGITS}</p>
 * <p>Example: AE202412345678 (AE = prefix, 2024 = year, 12345678 = random 8-digit number)</p>
 *
 * <p>Thread Safety:</p>
 * Uses ThreadLocalRandom for thread-safe random number generation in multi-threaded environments.
 *
 * <p>Usage:</p>
 * <pre>
 * String accountNumber = AccountNumberUtil.generateAccountNumber();
 * // Output: AE202412345678
 * </pre>
 *
 * @see java.util.concurrent.ThreadLocalRandom
 */
public final class AccountNumberUtil {

    /**
     * The country prefix for account numbers (United Arab Emirates).
     */
    private static final String ACCOUNT_PREFIX = "AE";

    /**
     * The minimum random number value (8 digits, starting from 10000000).
     */
    private static final int RANDOM_MIN = 10_000_000;

    /**
     * The maximum random number value (8 digits, ending at 99999999).
     */
    private static final int RANDOM_MAX = 100_000_000;

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     *
     * @throws AssertionError if instantiation is attempted
     */
    private AccountNumberUtil() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    /**
     * Generates a unique bank account number.
     * The account number consists of:
     * <ul>
     *   <li>Country prefix: AE (United Arab Emirates)</li>
     *   <li>Current year: 4 digits (e.g., 2024, 2025, 2026)</li>
     *   <li>Random number: 8 digits (10000000 to 99999999)</li>
     * </ul>
     *
     * <p>Total format: {PREFIX}{YEAR}{8-DIGIT-RANDOM} = 14 characters</p>
     *
     * <p>Examples of generated account numbers:</p>
     * <ul>
     *   <li>AE202412345678</li>
     *   <li>AE202487654321</li>
     *   <li>AE202499999999</li>
     * </ul>
     *
     * <p>Thread Safety:</p>
     * This method is thread-safe and can be safely called from multiple threads
     * without synchronization, as it uses ThreadLocalRandom which maintains a
     * separate instance per thread.
     *
     * @return a unique account number as a String in the format: {PREFIX}{YEAR}{8-DIGIT-RANDOM}
     *         Example: "AE202412345678"
     */
    public static String generateAccountNumber() {
        // Get current year (e.g., 2024, 2025, 2026)
        int currentYear = Year.now().getValue();

        // Generate random 8-digit number (10000000 to 99999999)
        int randomDigits = ThreadLocalRandom.current().nextInt(RANDOM_MIN, RANDOM_MAX);

        // Combine prefix, year, and random digits to create account number
        return ACCOUNT_PREFIX + currentYear + randomDigits;
    }
}
