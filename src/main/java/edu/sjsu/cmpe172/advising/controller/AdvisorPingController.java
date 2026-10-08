package edu.sjsu.cmpe172.advising.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Advisor-only stub used to exercise RBAC until the provider dashboard lands in Phase 5.
 */
@RestController
@RequestMapping("/api/advisor")
public class AdvisorPingController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("role", "ADVISOR", "status", "ok");
    }
}
