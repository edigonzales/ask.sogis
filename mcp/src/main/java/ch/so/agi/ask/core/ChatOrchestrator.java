package ch.so.agi.ask.core;

import java.util.*;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
    private final ChatMemoryStore chatMemoryStore;
    private final PendingChoiceStore pendingChoiceStore;

    public ChatOrchestrator(PlannerLlm plannerLlm, McpClient mcpClient, ActionPlanner actionPlanner,
            ChatMemoryStore chatMemoryStore, PendingChoiceStore pendingChoiceStore) {
        this.plannerLlm = plannerLlm;
        this.mcpClient = mcpClient;
        this.actionPlanner = actionPlanner;
        this.chatMemoryStore = chatMemoryStore;
        this.pendingChoiceStore = pendingChoiceStore;
    }

    /**
     * Führt den vollständigen Interaktionszyklus aus: Planner-Lauf, MCP-Tool-
     * Ausführung, Ableitung der MapActions/Choices und Rückgabe der konsistenten
     * Response an den REST-Controller.
     */
    public ChatResponse handleUserPrompt(ChatRequest req) {
        System.out.println(req);

        if (req.choiceId() != null && !req.choiceId().isBlank()) {
            return handleChoiceFollowUp(req);
        }

        // 1) LLM-Plan (Intent + ToolCalls) erzeugen
        PlannerOutput plan = plannerLlm.plan(req.sessionId(), req.userMessage());
        System.out.println(plan);

        // 2) ToolCalls je Step ausführen (MCP) und ActionPlans erzeugen
        List<ChatResponse.Step> steps = buildSteps(req.sessionId(), plan);

        // 3) Finale ChatResponse inklusive Gesamtstatus
        return new ChatResponse(plan.requestId(), steps, aggregateStatus(steps));
    }

    public void clearSession(String sessionId) {
        chatMemoryStore.deleteSession(sessionId);
        pendingChoiceStore.clear(sessionId);
    }

    private List<ChatResponse.Step> buildSteps(String sessionId, PlannerOutput plan) {
        List<ChatResponse.Step> steps = new ArrayList<>();
        if (plan.steps() == null) {
            return steps;
        }

        for (PlannerOutput.Step step : plan.steps()) {
            PlannerOutput.Result aggResult = executeToolCalls(sessionId, plan.requestId(), step, 0, null);
            System.out.println("aggResult: " + aggResult);

            ActionPlan ap = actionPlanner.toActionPlan(step.intent(), aggResult);
            var message = Optional.ofNullable(aggResult).map(PlannerOutput.Result::message).orElse(ap.message());
            steps.add(new ChatResponse.Step(step.intent(), ap.status(), message, ap.mapActions(), ap.choices()));
        }
        return steps;
    }

    private ChatResponse handleChoiceFollowUp(ChatRequest req) {
        var contextOpt = pendingChoiceStore.consume(req.sessionId());
        if (contextOpt.isEmpty()) {
            var step = new ChatResponse.Step(null, "error",
                    "Es liegt keine offene Auswahl für diese Sitzung vor.", List.of(), List.of());
            return new ChatResponse(UUID.randomUUID().toString(), List.of(step), aggregateStatus(List.of(step)));
        }

        PendingChoiceStore.PendingChoiceContext context = contextOpt.get();
        chatMemoryStore.appendMessage(req.sessionId(), new UserMessage("User choice: " + req.choiceId()));
        Map<String, Object> selectedItem = resolveSelectedItem(context.choiceItems(), req.choiceId());
        if (selectedItem == null) {
            var step = new ChatResponse.Step(context.step().intent(), "error",
                    "Die gewählte Option konnte nicht gefunden werden.", List.of(), List.of());
            return new ChatResponse(context.requestId(), List.of(step), aggregateStatus(List.of(step)));
        }

        PlannerOutput.Result result = executeToolCalls(req.sessionId(), context.requestId(), context.step(),
                context.nextToolCallIndex(), selectedItem);
        ActionPlan ap = actionPlanner.toActionPlan(context.step().intent(), result);
        var message = Optional.ofNullable(result).map(PlannerOutput.Result::message).orElse(ap.message());
        List<ChatResponse.Step> steps = List
                .of(new ChatResponse.Step(context.step().intent(), ap.status(), message, ap.mapActions(), ap.choices()));
        return new ChatResponse(context.requestId(), steps, aggregateStatus(steps));
    }

    /**
     * Führt alle vom Planner vorgeschlagenen Capabilities eines einzelnen Steps
     * aus und liefert das aktuellste {@link PlannerOutput.Result}. Dabei bleibt der
     * vom Tool gesetzte Status (z. B. {@code success}, {@code needs_clarification})
     * erhalten, sodass der ActionPlanner konsistente Entscheidungen treffen kann.
     */
    private PlannerOutput.Result executeToolCalls(String sessionId, String requestId, PlannerOutput.Step step, int startIndex,
            Map<String, Object> initialSelection) {
        if (step == null) {
            return null;
        }
        PlannerOutput.Result current = step.result();
        if (step.toolCalls() == null || step.toolCalls().isEmpty())
            return current;

        // Sehr einfache Aggregation: wir nehmen das Result der letzten ToolCall-Ausführung.
        // In echt: mergen/akkumulieren, Fehlerbehandlung, Tracing, Timeouts, …
        PlannerOutput.Result last = current;
        Map<String, Object> selection = initialSelection;
        System.out.println("initialSelection: " + initialSelection);
        List<PlannerOutput.ToolCall> toolCalls = step.toolCalls();
        System.out.println("toolCalls: " + toolCalls);
        for (int i = Math.max(0, startIndex); i < toolCalls.size(); i++) {
            PlannerOutput.ToolCall tc = toolCalls.get(i);
            Map<String, Object> args = new HashMap<>();
            if (tc.args() != null) {
                args.putAll(tc.args());
            }
            if (selection != null && !selection.isEmpty()) {
                System.out.println("selection: " + selection);
                
                args.put("selection", selection);
                Object id = selection.get("id");
                if (id != null) {
                    args.put("id", id);
                }
                Object egrid = Optional.ofNullable(selection.get("egrid")).orElse(id);
                if (egrid != null) {
                    args.put("egrid", egrid);
                }
            }

            last = mcpClient.execute(tc.capabilityId(), args);
            chatMemoryStore.appendMessage(sessionId,
                    new AssistantMessage("Tool %s result: %s".formatted(tc.capabilityId().id(), Json.write(last))));

            boolean hasNextToolCall = i < toolCalls.size() - 1;
            if (hasNextToolCall && last != null && last.items() != null && last.items().size() > 1) {
                pendingChoiceStore.save(sessionId,
                        new PendingChoiceStore.PendingChoiceContext(requestId, step, i + 1, last.items()));
                String message = Optional.ofNullable(last.message()).orElse("Bitte wähle eine Option.");
                return new PlannerOutput.Result("needs_user_choice", last.items(), message);
            }

            selection = (last != null && last.items() != null && !last.items().isEmpty()) ? last.items().get(0) : null;
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

    private Map<String, Object> resolveSelectedItem(List<Map<String, Object>> choiceItems, String choiceId) {
        if (choiceItems == null || choiceItems.isEmpty() || choiceId == null) {
            return null;
        }
        return choiceItems.stream().filter(item -> choiceId.equals(String.valueOf(item.get("id")))).findFirst().orElse(null);
    }
}
