package edu.sjsu.cmpe172.advising.controller;

import edu.sjsu.cmpe172.advising.ai.AssistantService;
import edu.sjsu.cmpe172.advising.dto.AssistantRequest;
import edu.sjsu.cmpe172.advising.dto.AssistantResponse;
import edu.sjsu.cmpe172.advising.service.exception.BadRequestException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public policy FAQ: answers come only from the advising policy document. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final int MAX_QUESTION_LENGTH = 500;

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/ask")
    public AssistantResponse ask(@RequestBody AssistantRequest body) {
        if (body == null || body.question() == null || body.question().isBlank()) {
            throw new BadRequestException("question is required");
        }
        if (body.question().length() > MAX_QUESTION_LENGTH) {
            throw new BadRequestException("question must be at most " + MAX_QUESTION_LENGTH + " characters");
        }
        return assistant.ask(body.question());
    }
}
