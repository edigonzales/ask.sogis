package ch.so.agi.ask.core;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.ChatRequest;
import ch.so.agi.ask.model.ChatResponse;
import ch.so.agi.ask.model.PlannerOutput;

/**
 * Zentraler Ablaufkoordinator zwischen HTTP-Controller, {@link PlannerLlm},
 * {@link McpClient} und {@link ActionPlanner}. Er übernimmt den vom Planner
 * gelieferten Intent, orchestriert die MCP-Ausführung der vorgeschlagenen
 * ToolCalls und baut daraus gemeinsam mit dem ActionPlanner die finale
 * {@code ChatResponse}, wie im README-Sequenzdiagramm beschrieben.
 */
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

    /**
     * Führt den vollständigen Interaktionszyklus aus: Planner-Lauf, MCP-Tool-
     * Ausführung, Ableitung der MapActions/Choices und Rückgabe der konsistenten
     * Response an den REST-Controller.
     */
    public ChatResponse handleUserPrompt(ChatRequest req) {
        System.out.println(req);
        
        // 1) LLM-Plan (Intent + ToolCalls) erzeugen
        PlannerOutput plan = plannerLlm.plan(req.sessionId(), req.userMessage());
        System.out.println(plan);

        // 2) ToolCalls ausführen (MCP), Ergebnis in plan.result „auffüllen“/korrigieren
        PlannerOutput.Result aggResult = executeToolCalls(plan);
        System.out.println(aggResult);

        // 3) Intent + Result in MapActions/Choices überführen (Policy/Templates)
        ActionPlan ap = actionPlanner.toActionPlan(plan.intent(), aggResult);

        // 4) Finale ChatResponse
        return new ChatResponse(plan.requestId(), plan.intent(), ap.status(),
                Optional.ofNullable(aggResult.message()).orElse(ap.message()), ap.mapActions(), ap.choices(),
                Map.of("raw", aggResult) // optional
        );
    }

    /**
     * Führt alle vom Planner vorgeschlagenen Capabilities aus und liefert das
     * aktuellste {@link PlannerOutput.Result}. Dabei bleibt der vom Tool
     * gesetzte Status (z. B. {@code success}, {@code needs_clarification}) erhalten,
     * sodass der ActionPlanner konsistente Entscheidungen treffen kann.
     */
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