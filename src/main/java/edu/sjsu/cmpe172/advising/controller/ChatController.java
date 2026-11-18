package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.ai.AiChat;
import edu.sjsu.cmpe172.advising.ai.ChatService;
import edu.sjsu.cmpe172.advising.auth.SessionAuth;
import edu.sjsu.cmpe172.advising.auth.SessionUser;
import edu.sjsu.cmpe172.advising.domain.UserRole;
import edu.sjsu.cmpe172.advising.dto.ChatHistoryMessage;
import edu.sjsu.cmpe172.advising.dto.ChatRequest;
import edu.sjsu.cmpe172.advising.dto.ChatResponse;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Student-only AI endpoints (RBAC comes from the /api/student prefix). */
@RestController
@RequestMapping("/api/student/chat")
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final AiChat ai;
    private final ChatService chatService;

    public ChatController(AiChat ai, ChatService chatService) {
        this.ai = ai;
        this.chatService = chatService;
    }

    /** One chat turn. Each student has one saved conversation, so it outlives login sessions and restarts. */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest body, HttpServletRequest request) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        if (body == null || body.message() == null || body.message().isBlank()) {
            throw new BadRequestException("message is required");
        }
        if (body.message().length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        return chatService.handle(user, conversationId(user), body.message());
    }

    /** The student's saved conversation, oldest message first. */
    @GetMapping("/history")
    public List<ChatHistoryMessage> history(HttpServletRequest request) {
        SessionUser user = SessionAuth.requireRole(request.getSession(false), UserRole.STUDENT);
        return chatService.history(conversationId(user));
    }

    private static String conversationId(SessionUser user) {
        return "student-" + user.id();
    }

    /** Smoke test: one prompt to Gemini, returns the reply and the model name. */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        String reply = ai.client().prompt("Reply with one short sentence saying hello.").call().content();
        return Map.of("reply", reply, "model", ai.model());
    }
}
