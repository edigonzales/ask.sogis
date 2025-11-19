package ch.so.agi.ask.model;

import java.util.List;

public record ChatResponse(String requestId, IntentType intent, // z.B. goto_address, load_layer, …
        String status, // ok | needs_user_choice | needs_clarification | error
        String message, List<MapAction> mapActions, List<Choice> choices, Object data) {
}
