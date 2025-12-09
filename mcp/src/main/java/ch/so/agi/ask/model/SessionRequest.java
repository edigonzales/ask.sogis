package ch.so.agi.ask.model;

import jakarta.validation.constraints.NotBlank;

public record SessionRequest(@NotBlank String sessionId) {
}
