package edu.sjsu.cmpe172.advising.auth;

import edu.sjsu.cmpe172.advising.domain.User;
import edu.sjsu.cmpe172.advising.domain.UserRole;

import java.io.Serializable;

/**
 * Lightweight principal stored in the HTTP session (never includes password hash).
 */
public record SessionUser(
        long id,
        String email,
        String fullName,
        UserRole role
) implements Serializable {

    public static SessionUser from(User user) {
        return new SessionUser(
                user.id(),
                user.email(),
                user.fullName(),
                UserRole.fromDatabase(user.role()));
    }

    public boolean hasRole(UserRole expected) {
        return role == expected;
    }
}
