package ch.so.agi.ask.api;

import ch.so.agi.ask.core.ChatOrchestrator;
import ch.so.agi.ask.model.ChatRequest;
import ch.so.agi.ask.model.ChatResponse;
import ch.so.agi.ask.model.SessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatOrchestrator orchestrator;

    public ChatController(ChatOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return ResponseEntity.ok(orchestrator.handleUserPrompt(req));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearSession(@Valid @RequestBody SessionRequest request) {
        orchestrator.clearSession(request.sessionId());
        return ResponseEntity.noContent().build();
    }
}
