package com.alaeldin.Auth_service.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class to generate BCrypt password hashes for database seeding.
 * Run this class to generate hashes for migration files.
 */
public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        // Generate hash for SecurePass123!
        String password = "SecurePass123!";
        String hash = encoder.encode(password);

        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println();

        // Verify the hash
        System.out.println("Verification: " + encoder.matches(password, hash));
        System.out.println();

        // Also generate for Admin@1234 for reference
        String adminPassword = "Admin@1234";
        String adminHash = encoder.encode(adminPassword);
        System.out.println("Password: " + adminPassword);
        System.out.println("BCrypt Hash: " + adminHash);
        System.out.println("Verification: " + encoder.matches(adminPassword, adminHash));
    }
}

