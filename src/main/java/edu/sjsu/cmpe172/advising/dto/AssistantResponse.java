package edu.sjsu.cmpe172.advising.dto;

import java.util.List;

/** {@code sources} are the policy sections the answer was grounded in. */
public record AssistantResponse(String answer, List<String> sources) {}
