package ch.so.agi.ask.core;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.ChatRequest;
import ch.so.agi.ask.model.ChatResponse;
import ch.so.agi.ask.model.PlannerOutput;

@Service
public class ChatOrchestrator {

    private final PlannerLlm plannerLlm;
    private final McpClient mcpClient;
    private final ActionPlanner actionPlanner;

    public ChatOrchestrator(PlannerLlm plannerLlm, McpClient mcpClient, ActionPlanner actionPlanner) {
        this.plannerLlm = plannerLlm;
        this.mcpClient = mcpClient;
        this.actionPlanner = actionPlanner;
    }

    public ChatResponse handleUserPrompt(ChatRequest req) {
        // 1) LLM-Plan (Intent + ToolCalls) erzeugen
        PlannerOutput plan = plannerLlm.plan(req.sessionId(), req.userMessage());

        // 2) ToolCalls ausführen (MCP), Ergebnis in plan.result „auffüllen“/korrigieren
        PlannerOutput.Result aggResult = executeToolCalls(plan);

        // 3) Aus domänennahen Result-Items → MapActions/Choices ableiten
        // (Policy/Templates)
        ActionPlan ap = actionPlanner.toActionPlan(plan.intent(), aggResult);

        // 4) Finale ChatResponse
        return new ChatResponse(plan.requestId(), plan.intent(), ap.status(),
                Optional.ofNullable(aggResult.message()).orElse(ap.message()), ap.mapActions(), ap.choices(),
                Map.of("raw", aggResult) // optional
        );
    }

    private PlannerOutput.Result executeToolCalls(PlannerOutput plan) {
        PlannerOutput.Result current = plan.result();
        if (plan.toolCalls() == null || plan.toolCalls().isEmpty())
            return current;

        // Sehr einfache Aggregation: wir nehmen das Result der letzten
        // ToolCall-Ausführung.
        // In echt: mergen/akkumulieren, Fehlerbehandlung, Tracing, Timeouts, …
        PlannerOutput.Result last = current;
        for (PlannerOutput.ToolCall tc : plan.toolCalls()) {
            last = mcpClient.execute(tc.capabilityId(), tc.args());
        }
        return last;
    }
}