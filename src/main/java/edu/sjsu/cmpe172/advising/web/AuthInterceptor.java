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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Server-side session gate. Public browse/login stay open; role prefixes enforce RBAC.
 * Browser (non-/api) requests are redirected; API calls get ProblemDetail exceptions.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();

        if (isPublic(path)) {
            return true;
        }

        SessionUser user = SessionAuth.getUserOrNull(request.getSession(false));
        if (user == null) {
            return rejectUnauthenticated(path, request, response);
        }

        if (isAdvisorPath(path) && !user.hasRole(UserRole.ADVISOR)) {
            return rejectForbidden(path, response, "Advisor role required");
        }

        if (isStudentPath(path) && !user.hasRole(UserRole.STUDENT)) {
            return rejectForbidden(path, response, "Student role required");
        }

        return true;
    }

    private static boolean isPublic(String path) {
        if ("/".equals(path)
                || "/login".equals(path)
                || "/logout".equals(path)
                || "/slots".equals(path)
                || "/api/status".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/slots")) {
            return true;
        }
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/");
    }

    private static boolean isAdvisorPath(String path) {
        return path.startsWith("/api/advisor") || path.startsWith("/advisor");
    }

    private static boolean isStudentPath(String path) {
        return path.startsWith("/api/student")
                || path.startsWith("/my-appointments")
                || path.startsWith("/book");
    }

    private static boolean isApi(String path) {
        return path.startsWith("/api/");
    }

    private static boolean rejectUnauthenticated(
            String path, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isApi(path)) {
            throw new UnauthorizedException("Authentication required");
        }
        String redirect = "/login?error=" + url("Please log in first");
        response.sendRedirect(request.getContextPath() + redirect);
        return false;
    }

    private static boolean rejectForbidden(String path, HttpServletResponse response, String message)
            throws IOException {
        if (isApi(path)) {
            throw new ForbiddenException(message);
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
        return false;
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
