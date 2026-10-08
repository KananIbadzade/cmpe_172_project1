package edu.sjsu.cmpe172.advising.dto;

import edu.sjsu.cmpe172.advising.auth.SessionUser;

public record UserResponse(
        long id,
        String email,
        String fullName,
        String role
) {
    public static UserResponse from(SessionUser user) {
        return new UserResponse(user.id(), user.email(), user.fullName(), user.role().name());
    }
}
