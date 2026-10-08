package edu.sjsu.cmpe172.advising.auth;

import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.UnauthorizedException;
import jakarta.servlet.http.HttpSession;

/**
 * Read/write helpers for the authenticated session principal.
 */
public final class SessionAuth {

    private SessionAuth() {}

    public static void setUser(HttpSession session, SessionUser user) {
        session.setAttribute(SessionKeys.USER, user);
    }

    public static void clear(HttpSession session) {
        session.removeAttribute(SessionKeys.USER);
        session.invalidate();
    }

    public static SessionUser getUserOrNull(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SessionKeys.USER);
        return value instanceof SessionUser sessionUser ? sessionUser : null;
    }

    public static SessionUser requireUser(HttpSession session) {
        SessionUser user = getUserOrNull(session);
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return user;
    }

    public static SessionUser requireRole(HttpSession session, UserRole role) {
        SessionUser user = requireUser(session);
        if (!user.hasRole(role)) {
            throw new ForbiddenException("Requires role " + role.name());
        }
        return user;
    }
}
