package edu.sjsu.cmpe172.advising.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.cmpe172.advising.auth.SessionAuth;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Advisor-only access to /metrics. Actuator endpoints use their own handler mapping,
 * which {@link AuthInterceptor} does not cover, so this check has to be a servlet filter.
 * /health stays public for Docker and load-balancer probes.
 */
@Component
public class MetricsAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public MetricsAccessFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/metrics");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SessionUser user = SessionAuth.getUserOrNull(request.getSession(false));
        if (user == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }
        if (!user.hasRole(UserRole.ADVISOR)) {
            reject(response, HttpStatus.FORBIDDEN, "Advisor role required");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
