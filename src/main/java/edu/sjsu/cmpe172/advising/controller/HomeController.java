package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.dto.SystemStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class HomeController {

    private final String system;
    private final String environment;
    private final String milestone;

    public HomeController(
            @Value("${spring.application.name}") String system,
            @Value("${app.environment}") String environment,
            @Value("${app.milestone}") String milestone) {
        this.system = system;
        this.environment = environment;
        this.milestone = milestone;
    }

    @GetMapping("/")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(system, "UP", environment, Instant.now(), milestone);
    }
}
