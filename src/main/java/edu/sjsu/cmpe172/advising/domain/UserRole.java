package edu.sjsu.cmpe172.advising.domain;

/**
 * Values stored in {@code users.role}. Matches the PostgreSQL CHECK constraint.
 */
public enum UserRole {
    STUDENT,
    ADVISOR;

    public static UserRole fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("User role is required");
        }
        return UserRole.valueOf(value.trim().toUpperCase());
    }
}
