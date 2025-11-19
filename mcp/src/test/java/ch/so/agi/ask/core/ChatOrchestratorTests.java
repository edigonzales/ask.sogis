package ch.so.agi.ask.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.so.agi.ask.model.ChatRequest;
import ch.so.agi.ask.model.ChatResponse;
import ch.so.agi.ask.model.IntentType;
import ch.so.agi.ask.model.McpToolCapability;
import ch.so.agi.ask.model.PlannerOutput;

class ChatOrchestratorTests {

    @Test
    void orchestratesMultipleStepsAndAggregatesToOk() {
        PlannerLlm planner = mock(PlannerLlm.class);
        McpClient mcpClient = mock(McpClient.class);
        ActionPlanner actionPlanner = new ActionPlanner();
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner);

        var gotoStep = new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.GEO_GEOCODE,
                        Map.of("q", "Langendorfstrasse 19b, Solothurn"))),
                new PlannerOutput.Result("pending", List.of(), "Adresse wird gesucht"));

        var layerStep = new PlannerOutput.Step(IntentType.LOAD_LAYER,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.LAYERS_SEARCH, Map.of("query", "Gewässerschutz"))),
                new PlannerOutput.Result("pending", List.of(), null));

        when(planner.plan("sess-1", "Bitte zentrieren und Layer laden"))
                .thenReturn(new PlannerOutput("req-1", List.of(gotoStep, layerStep)));

        when(mcpClient.execute(eq(McpToolCapability.GEO_GEOCODE), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok",
                        List.of(Map.of("coord", List.of(2609767.1, 1228437.4), "id", "7568", "crs", "EPSG:2056")),
                        "Adresse Langendorfstrasse 19b zentriert."));

        when(mcpClient.execute(eq(McpToolCapability.LAYERS_SEARCH), anyMap()))
                .thenReturn(new PlannerOutput.Result("ok",
                        List.of(Map.of("layerId", "ch.sg.gws", "type", "wmts", "source",
                                Map.of("url", "https://tiles.example"))),
                        "Gewässerschutz-Layer geladen."));

        ChatResponse response = orchestrator
                .handleUserPrompt(new ChatRequest("sess-1", "Bitte zentrieren und Layer laden"));

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
        ChatOrchestrator orchestrator = new ChatOrchestrator(planner, mcpClient, actionPlanner);

        var gotoStep = new PlannerOutput.Step(IntentType.GOTO_ADDRESS,
                List.of(new PlannerOutput.ToolCall(McpToolCapability.GEO_GEOCODE, Map.of("q", "Solothurn"))),
                new PlannerOutput.Result("pending", List.of(), null));

        when(planner.plan(anyString(), anyString())).thenReturn(new PlannerOutput("req-2", List.of(gotoStep)));

        when(mcpClient.execute(eq(McpToolCapability.GEO_GEOCODE), anyMap())).thenReturn(new PlannerOutput.Result(
                "ok",
                List.of(Map.of("id", "opt-1", "label", "Solothurn Stadt", "coord", List.of(2608000d, 1229000d)),
                        Map.of("id", "opt-2", "label", "Solothurn Kanton", "coord", List.of(2610000d, 1230000d))),
                "Mehrere Treffer gefunden."));

        ChatResponse response = orchestrator.handleUserPrompt(new ChatRequest("sess-2", "Solothurn"));

        assertThat(response.overallStatus()).isEqualTo("needs_user_choice");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).choices()).hasSize(2);
        assertThat(response.steps().get(0).status()).isEqualTo("needs_user_choice");
    }
}
