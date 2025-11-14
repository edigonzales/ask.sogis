package ch.so.agi.ask.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String sessionId, @NotBlank String userMessage) {
}
