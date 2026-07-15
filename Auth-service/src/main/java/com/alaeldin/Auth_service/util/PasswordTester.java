package com.alaeldin.Auth_service.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility to test which password matches the current database hash.
 */
public class PasswordTester {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        // The hash currently in the database
        String dbHash = "$2a$12$mZN8VqNKLZwJLnXNJrLXHuqSHmfPYYP3tgCe6lqoL8N5wqBdH7Ady";

        // The hash from the migration file
        String migrationHash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgfl4i8VToBf7yDQDRHfGe";

        String[] testPasswords = {
            "SecurePass123!",
            "Admin@1234",
            "admin",
            "admin123",
            "password",
            "SecurePass123",
            "admin@1234",
            "Admin@12  34",
            "Admin@1234 ",
            " Admin@1234"
        };

        System.out.println("=".repeat(70));
        System.out.println("PASSWORD HASH TESTING");
        System.out.println("=".repeat(70));
        System.out.println();

        System.out.println("Database Hash:  " + dbHash);
        System.out.println("Migration Hash: " + migrationHash);
        System.out.println();

        System.out.println("Testing against DATABASE hash:");
        System.out.println("-".repeat(70));
        boolean foundDbMatch = false;
        for (String pwd : testPasswords) {
            boolean matches = encoder.matches(pwd, dbHash);
            System.out.printf("  %-25s : %s%n", "\"" + pwd + "\"", matches ? "✓✓✓ MATCH ✓✓✓" : "✗ no match");
            if (matches && !foundDbMatch) {
                foundDbMatch = true;
                System.out.println("  >>> DATABASE PASSWORD FOUND: \"" + pwd + "\"");
            }
        }

        System.out.println();
        System.out.println("Testing against MIGRATION FILE hash:");
        System.out.println("-".repeat(70));
        boolean foundMigrationMatch = false;
        for (String pwd : testPasswords) {
            boolean matches = encoder.matches(pwd, migrationHash);
            System.out.printf("  %-25s : %s%n", "\"" + pwd + "\"", matches ? "✓✓✓ MATCH ✓✓✓" : "✗ no match");
            if (matches && !foundMigrationMatch) {
                foundMigrationMatch = true;
                System.out.println("  >>> MIGRATION PASSWORD FOUND: \"" + pwd + "\"");
            }
        }

        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("GENERATING NEW HASHES:");
        System.out.println("=".repeat(70));
        System.out.println("For 'SecurePass123!': ");
        System.out.println("  " + encoder.encode("SecurePass123!"));
        System.out.println();
        System.out.println("For 'Admin@1234': ");
        System.out.println("  " + encoder.encode("Admin@1234"));
        System.out.println();
    }
}

