package ch.so.agi.ask.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ch.so.agi.ask.core.ChatMemoryStore;
import ch.so.agi.ask.core.InMemoryChatMemoryStore;
import ch.so.agi.ask.model.ChatRequest;
import ch.so.agi.ask.model.ChatResponse;
import ch.so.agi.ask.model.IntentType;
import ch.so.agi.ask.model.McpToolCapability;
import ch.so.agi.ask.model.PlannerOutput;
import ch.so.agi.ask.mcp.McpResponseItem;
import ch.so.agi.ask.core.PendingChoiceStore;
import ch.so.agi.ask.core.InMemoryPendingChoiceStore;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class ChatOrchestratorTests {

    @Test
    void orchestratesMultipleStepsAndAggregatesToOk() {
        PlannerLlm planner = mock(PlannerLlm.class);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        PendingChoiceStore pendingChoiceStore = new InMemoryPendingChoiceStore();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner, chatMemoryStore,
                pendingChoiceStore);

        var gotoStep = new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                        Map.of("q", "Langendorfstrasse 19b, Solothurn"))),
                new PlannerOutput.Result("pending", List.of(), "Adresse wird gesucht"));

        var layerStep = new PlannerOutput.Step(IntentType.LOAD_LAYER,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.LAYERS_SEARCH, Map.of("query", "Gewässerschutz"))),
                new PlannerOutput.Result("pending", List.of(), null));

        when(planner.plan("sess-1", "Bitte zentrieren und Layer laden"))
                .thenReturn(new PlannerOutput("req-1", List.of(gotoStep, layerStep)));

        when(mcpClient.execute(eq(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok",
                        List.of(Map.of("coord", List.of(2609767.1, 1228437.4), "id", "7568", "crs", "EPSG:2056")),
                        "Adresse Langendorfstrasse 19b zentriert."));

        when(mcpClient.execute(eq(McpToolCapability.LAYERS_SEARCH), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok",
                        List.of(Map.of("layerId", "ch.sg.gws", "type", "wmts", "source",
                                Map.of("url", "https://tiles.example"))),
                        "Gewässerschutz-Layer geladen."));

        ChatResponse response = orchestrator
                .handleUserPrompt(new ChatRequest("sess-1", "Bitte zentrieren und Layer laden", null));

        assertThat(response.overallStatus()).isEqualTo("ok");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).intent()).isEqualTo(IntentType.GOTO_ADDRESS);
        assertThat(response.steps().get(0).mapActions()).hasSize(2);
        assertThat(response.steps().get(1).intent()).isEqualTo(IntentType.LOAD_LAYER);
        assertThat(response.steps().get(1).mapActions()).hasSize(1);
    }

    @Test
    void overallStatusReflectsMostCriticalStep() {
        PlannerLlm planner = mock(PlannerLlm.class);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        PendingChoiceStore pendingChoiceStore = new InMemoryPendingChoiceStore();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner, chatMemoryStore,
                pendingChoiceStore);

        var gotoStep = new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS, Map.of("q", "Solothurn"))),
                new PlannerOutput.Result("pending", List.of(), null));

        when(planner.plan(anyString(), anyString())).thenReturn(new PlannerOutput("req-2", List.of(gotoStep)));

        when(mcpClient.execute(eq(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS), anyMap())).thenReturn(new PlannerOutput.Result(
                "ok",
                List.of(Map.of("id", "opt-1", "label", "Solothurn Stadt", "coord", List.of(2608000d, 1229000d)),
                        Map.of("id", "opt-2", "label", "Solothurn Kanton", "coord", List.of(2610000d, 1230000d))),
                "Mehrere Treffer gefunden."));

        ChatResponse response = orchestrator.handleUserPrompt(new ChatRequest("sess-2", "Solothurn", null));

        assertThat(response.overallStatus()).isEqualTo("needs_user_choice");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).choices()).hasSize(2);
        assertThat(response.steps().get(0).status()).isEqualTo("needs_user_choice");
    }

    @Test
    void returnsChoiceStepWhenIntermediateToolRequiresSelection() {
        PlannerLlm planner = mock(PlannerLlm.class);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        PendingChoiceStore pendingChoiceStore = new InMemoryPendingChoiceStore();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner, chatMemoryStore,
                pendingChoiceStore);

        var step = new PlannerOutput.Step(IntentType.OEREB_EXTRACT,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.OEREB_EGRID_BY_XY,
                        Map.of("coord", List.of(2600000d, 1200000d))),
                        new PlannerOutput.ToolCall(McpToolCapability.OEREB_EXTRACT_BY_ID, Map.of())),
                new PlannerOutput.Result("pending", List.of(), "Koordinate wird geprüft"));

        when(planner.plan(anyString(), anyString())).thenReturn(new PlannerOutput("req-oereb", List.of(step)));

        when(mcpClient.execute(eq(McpToolCapability.OEREB_EGRID_BY_XY), anyMap())).thenReturn(new PlannerOutput.Result("ok",
                List.of(Map.of("id", "SO0200001234", "label", "EGRID 1234", "coord", List.of(2600000d, 1200000d)),
                        Map.of("id", "SO0200005678", "label", "EGRID 5678", "coord", List.of(2600000d, 1200000d))),
                "Mehrere Grundstücke gefunden."));

        ChatResponse response = orchestrator
                .handleUserPrompt(new ChatRequest("sess-oereb", "ÖREB Auszug bitte", null));

        assertThat(response.overallStatus()).isEqualTo("needs_user_choice");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).status()).isEqualTo("needs_user_choice");
        assertThat(response.steps().get(0).choices()).hasSize(2);
        assertThat(response.steps().get(0).message()).contains("Mehrere Grundstücke");

        var pending = pendingChoiceStore.consume("sess-oereb");
        assertThat(pending).isPresent();
        assertThat(pending.get().nextToolCallIndex()).isEqualTo(1);
    }

    @Test
    void resolvesFinalChoiceSelectionsFromPayloadIds() {
        PlannerLlm planner = mock(PlannerLlm.class);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        PendingChoiceStore pendingChoiceStore = new InMemoryPendingChoiceStore();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner, chatMemoryStore,
                pendingChoiceStore);

        var step = new PlannerOutput.Step(IntentType.LOAD_LAYER,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.LAYERS_SEARCH, Map.of("query", "wald"))),
                new PlannerOutput.Result("pending", List.of(), ""));

        when(planner.plan(anyString(), anyString())).thenReturn(new PlannerOutput("req-layer", List.of(step)));

        Map<String, Object> layerA = new McpResponseItem("layer",
                Map.of("id", "layer-a", "label", "Layer A", "layerId", "layer-a", "type", "wms",
                        "source", Map.of("url", "https://geo.so.ch/api/wms", "LAYERS", "layer-a")),
                List.of(), Map.of()).toMap();
        Map<String, Object> layerB = new McpResponseItem("layer",
                Map.of("id", "layer-b", "label", "Layer B", "layerId", "layer-b", "type", "wms",
                        "source", Map.of("url", "https://geo.so.ch/api/wms", "LAYERS", "layer-b")),
                List.of(), Map.of()).toMap();

        when(mcpClient.execute(eq(McpToolCapability.LAYERS_SEARCH), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok", List.of(layerA, layerB), "Mehrere Layer gefunden."));

        ChatResponse response = orchestrator.handleUserPrompt(new ChatRequest("sess-layer", "Layer wald", null));

        assertThat(response.overallStatus()).isEqualTo("needs_user_choice");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).choices()).hasSize(2);

        ChatResponse selectionResponse = orchestrator
                .handleUserPrompt(new ChatRequest("sess-layer", null, "layer-a"));

        assertThat(selectionResponse.overallStatus()).isEqualTo("ok");
        assertThat(selectionResponse.steps()).hasSize(1);
        assertThat(selectionResponse.steps().get(0).status()).isEqualTo("ok");
        assertThat(selectionResponse.steps().get(0).mapActions()).hasSize(1);
    }

    @Test
    void plannerPromptIncludesHistoryForFollowUps() {
        ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        var toolRegistry = mock(ch.so.agi.ask.mcp.ToolRegistry.class);
        when(toolRegistry.listTools()).thenReturn(Map.of(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                new ch.so.agi.ask.mcp.ToolRegistry.ToolDescriptor(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                        "", Object.class, "", List.of(
                                new ch.so.agi.ask.mcp.ToolRegistry.ToolParamDescriptor("args",
                                        "Query string that represents an address", true,
                                        "Map<String, Object>", "{ 'q': 'string - full address query' }")))));
        PlannerLlm planner = new PlannerLlm(chatClient, chatMemoryStore, toolRegistry);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        PendingChoiceStore pendingChoiceStore = new InMemoryPendingChoiceStore();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner, chatMemoryStore,
                pendingChoiceStore);

        var firstPlan = new PlannerOutput("req-1",
                List.of(new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                        List.of(new PlannerOutput.ToolCall(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                                Map.of("q", "Langendorfstrasse 19b, Solothurn"))),
                        new PlannerOutput.Result("pending", List.of(), ""))));

        var followUpPlan = new PlannerOutput("req-2",
                List.of(new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                        List.of(new PlannerOutput.ToolCall(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                                Map.of("q", "Langendorfstrasse 9, Solothurn"))),
                        new PlannerOutput.Result("pending", List.of(), ""))));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        when(chatClient.prompt(promptCaptor.capture()).call().content())
                .thenReturn(Json.write(firstPlan), Json.write(followUpPlan));

        when(mcpClient.execute(eq(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok",
                        List.of(Map.of("coord", List.of(2609767.1, 1228437.4), "id", "7568", "crs", "EPSG:2056",
                                "label", "Langendorfstrasse 19b, Solothurn")),
                        "Adresse gefunden"));

        orchestrator.handleUserPrompt(new ChatRequest("sess-3", "Langendorfstrasse 19b, Solothurn", null));
        orchestrator.handleUserPrompt(new ChatRequest("sess-3", "Nummer 9", null));

        List<Prompt> prompts = promptCaptor.getAllValues();
        assertThat(prompts).hasSize(2);

        String systemPrompt = messageText(prompts.get(0).getInstructions().get(0));
        assertThat(systemPrompt).contains("geolocation.geocode.address");
        assertThat(systemPrompt).contains("Params:");
        assertThat(systemPrompt).contains("args (required)");

        List<Message> secondPromptMessages = prompts.get(1).getInstructions();
        assertThat(secondPromptMessages.stream()
                .anyMatch(m -> m instanceof UserMessage && messageText(m).contains("Nummer 9"))).isTrue();
        assertThat(secondPromptMessages.stream().filter(m -> !(m instanceof SystemMessage)).map(this::messageText)
                .anyMatch(content -> content.contains("Langendorfstrasse 19b"))).isTrue();
        assertThat(secondPromptMessages.stream().filter(m -> !(m instanceof SystemMessage)).map(this::messageText)
                .anyMatch(content -> content.contains("Tool geolocation.geocode.address result"))).isTrue();
    }

    @Test
    void clearingSessionRemovesHistoryFromFuturePrompts() {
        ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        var toolRegistry = mock(ch.so.agi.ask.mcp.ToolRegistry.class);
        when(toolRegistry.listTools()).thenReturn(Map.of(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                new ch.so.agi.ask.mcp.ToolRegistry.ToolDescriptor(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                        "", Object.class, "", List.of(
                                new ch.so.agi.ask.mcp.ToolRegistry.ToolParamDescriptor("args",
                                        "Query string that represents an address", true,
                                        "Map<String, Object>", "{ 'q': 'string - full address query' }")))));
        PlannerLlm planner = new PlannerLlm(chatClient, chatMemoryStore, toolRegistry);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        PendingChoiceStore pendingChoiceStore = new InMemoryPendingChoiceStore();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner, chatMemoryStore,
                pendingChoiceStore);

        var firstPlan = new PlannerOutput("req-1",
                List.of(new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                        List.of(new PlannerOutput.ToolCall(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                                Map.of("q", "Langendorfstrasse 19b, Solothurn"))),
                        new PlannerOutput.Result("pending", List.of(), ""))));

        var secondPlan = new PlannerOutput("req-2",
                List.of(new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                        List.of(new PlannerOutput.ToolCall(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS,
                                Map.of("q", "Langendorfstrasse 9, Solothurn"))),
                        new PlannerOutput.Result("pending", List.of(), ""))));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        when(chatClient.prompt(promptCaptor.capture()).call().content())
                .thenReturn(Json.write(firstPlan), Json.write(secondPlan));

        when(mcpClient.execute(eq(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok",
                        List.of(Map.of("id", "7568", "coord", List.of(2609767.1, 1228437.4), "crs", "EPSG:2056")),
                        "Adresse gefunden"));

        orchestrator.handleUserPrompt(new ChatRequest("sess-4", "Langendorfstrasse 19b, Solothurn", null));
        orchestrator.clearSession("sess-4");
        orchestrator.handleUserPrompt(new ChatRequest("sess-4", "Nummer 9", null));

        List<Prompt> prompts = promptCaptor.getAllValues();
        assertThat(prompts).hasSize(2);

        List<Message> secondPromptMessages = prompts.get(1).getInstructions();
        assertThat(secondPromptMessages.stream().filter(m -> !(m instanceof SystemMessage)).map(this::messageText)
                .anyMatch(content -> content.contains("Langendorfstrasse 19b"))).isFalse();
        assertThat(secondPromptMessages.stream().filter(m -> !(m instanceof SystemMessage))
                .anyMatch(m -> m instanceof UserMessage && messageText(m).contains("Nummer 9"))).isTrue();
    }

    @Test
    void carriesSelectionPayloadToFollowingToolCalls() {
        PlannerLlm planner = mock(PlannerLlm.class);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        ChatMemoryStore chatMemoryStore = new InMemoryChatMemoryStore();
        PendingChoiceStore pendingChoiceStore = new InMemoryPendingChoiceStore();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner, chatMemoryStore,
                pendingChoiceStore);

        var step = new PlannerOutput.Step(IntentType.GEOTHERMAL_PROBE_ASSESSMENT,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS, Map.of("q", "addr")),
                        new PlannerOutput.ToolCall(McpToolCapability.PROCESSING_GEOTHERMAL_BORE_INFO_BY_XY, Map.of())),
                new PlannerOutput.Result("pending", List.of(), ""));

        when(planner.plan(anyString(), anyString())).thenReturn(new PlannerOutput("req-sel", List.of(step)));

        Map<String, Object> selectionItem = new McpResponseItem("geolocation",
                Map.of("id", "123", "label", "Test address", "coord", List.of(2600000d, 1200000d), "crs", "EPSG:2056"),
                List.of(), Map.of()).toMap();

        when(mcpClient.execute(eq(McpToolCapability.GEOLOCATION_GEOCODE_ADDRESS), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok", List.of(selectionItem), "Adresse gefunden"));

        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        when(mcpClient.execute(eq(McpToolCapability.PROCESSING_GEOTHERMAL_BORE_INFO_BY_XY), argsCaptor.capture()))
                .thenReturn(new PlannerOutput.Result("ok", List.of(), "Geothermie geprüft"));

        orchestrator.handleUserPrompt(new ChatRequest("sess-sel", "Adresse prüfen", null));

        Map<String, Object> forwardedArgs = argsCaptor.getValue();
        assertThat(forwardedArgs.get("coord")).isEqualTo(List.of(2600000d, 1200000d));
        assertThat(forwardedArgs.get("x")).isEqualTo(2600000d);
        assertThat(forwardedArgs.get("y")).isEqualTo(1200000d);
        assertThat(forwardedArgs.get("crs")).isEqualTo("EPSG:2056");
        assertThat(forwardedArgs.get("selection")).isInstanceOf(Map.class);
        Map<String, Object> selection = (Map<String, Object>) forwardedArgs.get("selection");
        assertThat(selection).doesNotContainKey("payload");
        assertThat(selection.get("coord")).isEqualTo(List.of(2600000d, 1200000d));
    }

    private String messageText(Message message) {
        if (message instanceof AbstractMessage abstractMessage) {
            return abstractMessage.getText();
        }
        return String.valueOf(message);
    }
}
