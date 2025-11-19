package ch.so.agi.ask.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.ChatRequest;
import ch.so.agi.ask.model.ChatResponse;
import ch.so.agi.ask.model.PlannerOutput;

/**
 * Zentraler Ablaufkoordinator zwischen HTTP-Controller, {@link PlannerLlm},
 * {@link McpClient} und {@link ActionPlanner}. Er übernimmt die vom Planner
 * gelieferten Intents (Steps), orchestriert die MCP-Ausführung der vorgeschlagenen
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

        // 2) ToolCalls je Step ausführen (MCP) und ActionPlans erzeugen
        List<ChatResponse.Step> steps = buildSteps(plan);

        // 3) Finale ChatResponse inklusive Gesamtstatus
        return new ChatResponse(plan.requestId(), steps, aggregateStatus(steps));
    }

    private List<ChatResponse.Step> buildSteps(PlannerOutput plan) {
        List<ChatResponse.Step> steps = new ArrayList<>();
        if (plan.steps() == null) {
            return steps;
        }

        for (PlannerOutput.Step step : plan.steps()) {
            PlannerOutput.Result aggResult = executeToolCalls(step);
            System.out.println(aggResult);

            ActionPlan ap = actionPlanner.toActionPlan(step.intent(), aggResult);
            var message = Optional.ofNullable(aggResult).map(PlannerOutput.Result::message).orElse(ap.message());
            steps.add(new ChatResponse.Step(step.intent(), ap.status(), message, ap.mapActions(), ap.choices()));
        }
        return steps;
    }

    /**
     * Führt alle vom Planner vorgeschlagenen Capabilities eines einzelnen Steps
     * aus und liefert das aktuellste {@link PlannerOutput.Result}. Dabei bleibt der
     * vom Tool gesetzte Status (z. B. {@code success}, {@code needs_clarification})
     * erhalten, sodass der ActionPlanner konsistente Entscheidungen treffen kann.
     */
    private PlannerOutput.Result executeToolCalls(PlannerOutput.Step step) {
        if (step == null) {
            return null;
        }
        PlannerOutput.Result current = step.result();
        if (step.toolCalls() == null || step.toolCalls().isEmpty())
            return current;

        // Sehr einfache Aggregation: wir nehmen das Result der letzten ToolCall-Ausführung.
        // In echt: mergen/akkumulieren, Fehlerbehandlung, Tracing, Timeouts, …
        PlannerOutput.Result last = current;
        for (PlannerOutput.ToolCall tc : step.toolCalls()) {
            last = mcpClient.execute(tc.capabilityId(), tc.args());
        }
        return last;
    }

    private String aggregateStatus(List<ChatResponse.Step> steps) {
        if (steps == null || steps.isEmpty()) {
            return "ok";
        }
        if (steps.stream().anyMatch(s -> "error".equals(s.status()))) {
            return "error";
        }
        if (steps.stream().anyMatch(s -> "needs_clarification".equals(s.status()))) {
            return "needs_clarification";
        }
        if (steps.stream().anyMatch(s -> "needs_user_choice".equals(s.status()))) {
            return "needs_user_choice";
        }
        return "ok";
    }
}