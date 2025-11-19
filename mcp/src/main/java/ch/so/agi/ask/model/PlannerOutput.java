package ch.so.agi.ask.model;

import java.util.List;
import java.util.Map;

public record PlannerOutput(
        String requestId,
        IntentType intent,
        List<ToolCall> toolCalls,
        Result result
      ) {
        public record ToolCall(McpToolCapability capabilityId, Map<String,Object> args) {}
        public record Result(
          String status, // ok | needs_user_choice | needs_clarification | error
          List<Map<String,Object>> items, // domänennahe Items (z.B. Geocode-Treffer)
          String message
        ) {}
      }
