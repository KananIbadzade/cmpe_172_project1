package edu.sjsu.cmpe172.advising.service.exception;

/** Thrown by AI endpoints when no Gemini API key is set. */
public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException() {
        super("AI assistant is not configured");
    }
}
