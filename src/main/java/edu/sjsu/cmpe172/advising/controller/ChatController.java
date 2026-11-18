package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.ai.AiChat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Student-only AI endpoints (RBAC comes from the /api/student prefix). */
@RestController
@RequestMapping("/api/student/chat")
public class ChatController {

    private final AiChat ai;

    public ChatController(AiChat ai) {
        this.ai = ai;
    }

    /** Smoke test: one prompt to Gemini, returns the reply and the model name. */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        String reply = ai.client().prompt("Reply with one short sentence saying hello.").call().content();
        return Map.of("reply", reply, "model", ai.model());
    }
}
