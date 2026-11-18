package edu.sjsu.cmpe172.advising.dto;

/** One saved chat message; {@code role} is "user" or "assistant". */
public record ChatHistoryMessage(String role, String text) {}
