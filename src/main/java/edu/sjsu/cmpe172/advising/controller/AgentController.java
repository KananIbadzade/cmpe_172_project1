package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.ai.ReminderAgent;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Advisor-only controls for the autonomous agents (RBAC comes from the /api/advisor prefix). */
@RestController
@RequestMapping("/api/advisor/agents")
public class AgentController {

    private static final int MAX_WINDOW_HOURS = 168;

    private final ReminderAgent reminderAgent;
    private final int defaultWindowHours;

    public AgentController(ReminderAgent reminderAgent, @Value("${app.reminders.window-hours}") int defaultWindowHours) {
        this.reminderAgent = reminderAgent;
        this.defaultWindowHours = defaultWindowHours;
    }

    /** Runs the reminder agent now, for demos. {@code windowHours} overrides the configured window. */
    @PostMapping("/reminders/run")
    public ReminderAgent.RunResult runReminders(@RequestParam(required = false) Integer windowHours) {
        int hours = windowHours == null ? defaultWindowHours : windowHours;
        if (hours < 1 || hours > MAX_WINDOW_HOURS) {
            throw new BadRequestException("windowHours must be between 1 and " + MAX_WINDOW_HOURS);
        }
        return reminderAgent.run(hours);
    }
}
