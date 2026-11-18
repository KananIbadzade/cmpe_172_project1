package edu.sjsu.cmpe172.advising.service.exception;

/** The AI provider failed (quota, network, blocked request). The student can simply try again. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException() {
        super("AI assistant is temporarily unavailable. Please try again.");
    }
}
