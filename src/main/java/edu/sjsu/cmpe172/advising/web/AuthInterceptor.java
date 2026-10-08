package edu.sjsu.cmpe172.advising.web;

import edu.sjsu.cmpe172.advising.auth.SessionAuth;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.service.exception.ForbiddenException;
import edu.sjsu.cmpe172.advising.service.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Server-side session gate. Public browse/login stay open; role prefixes enforce RBAC.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        if (isPublic(path)) {
            return true;
        }

        SessionUser user = SessionAuth.getUserOrNull(request.getSession(false));
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }

        if (path.startsWith("/api/advisor") && !user.hasRole(UserRole.ADVISOR)) {
            throw new ForbiddenException("Advisor role required");
        }

        if (path.startsWith("/api/student") && !user.hasRole(UserRole.STUDENT)) {
            throw new ForbiddenException("Student role required");
        }

        return true;
    }

    private static boolean isPublic(String path) {
        if ("/".equals(path) || "/login".equals(path) || "/logout".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/slots")) {
            return true;
        }
        // Static resources for the future Thymeleaf UI.
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/");
    }
}
