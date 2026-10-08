package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.auth.SessionAuth;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.dto.LoginRequest;
import edu.sjsu.cmpe172.advising.dto.UserResponse;
import edu.sjsu.cmpe172.advising.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SessionUser user = authService.authenticate(request.email(), request.password());
        HttpSession session = httpRequest.getSession(true);
        SessionAuth.setUser(session, user);
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            SessionAuth.clear(session);
        }
        return Map.of("status", "logged_out");
    }

    @GetMapping("/api/me")
    public UserResponse me(HttpServletRequest httpRequest) {
        SessionUser user = SessionAuth.requireUser(httpRequest.getSession(false));
        return UserResponse.from(user);
    }
}
