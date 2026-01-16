package ch.so.agi.ask.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.IntentType;
import ch.so.agi.ask.model.McpToolCapability;
import ch.so.agi.ask.model.PlannerOutput;
import ch.so.agi.ask.mcp.ToolRegistry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring-AI-gestützter Planner, der Intent und ToolCalls (Capabilities) aus der
 * Benutzereingabe extrahiert. Liefert ein {@link PlannerOutput}, das dem
 * Sequenzschritt "PlannerLlm" aus dem README entspricht und als Ausgangspunkt
 * für Orchestrator & ActionPlanner dient.
 */
@Service
public class PlannerLlm {
    private static final Logger log = LoggerFactory.getLogger(PlannerLlm.class);

    private final ChatClient chatClient;
    private final ChatMemoryStore chatMemoryStore;

    private final ToolRegistry toolRegistry;

    public PlannerLlm(ChatClient chatClient, ChatMemoryStore chatMemoryStore, ToolRegistry toolRegistry) {
        this.chatClient = chatClient;
        this.chatMemoryStore = chatMemoryStore;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Baut den Prompt aus System- und User-Message, ruft das Planner-LLM und
     * deserialisiert das JSON in das interne {@link PlannerOutput} (mit Steps,
     * ToolCalls und initialem Result-Status {@code pending}).
     */
    public PlannerOutput plan(String sessionId, String userMessage) {
        String safeUserMessage = Optional.ofNullable(userMessage).orElse("");
        List<Message> history = new ArrayList<>(chatMemoryStore.getMessages(sessionId));
        UserMessage latestUserMessage = new UserMessage(safeUserMessage);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(buildSystemPrompt()));
        messages.addAll(history);
        messages.add(latestUserMessage);

        var prompt = new Prompt(messages);
        log.info(prompt.toString());
        log.info("*******************************");
        var content = chatClient.prompt(prompt).call().content(); // JSON string
        log.info(content);
        log.info("*******************************");

        chatMemoryStore.appendMessages(sessionId, List.of(latestUserMessage, new AssistantMessage(content)));

        // Deserialisieren in PlannerOutput (ObjectMapper empfohlen)
        return Json.read(content, PlannerOutput.class);
    }

    private String buildSystemPrompt() {
        String capabilitySection = toolRegistry.listTools().values().stream()
                .sorted(Comparator.comparing(td -> td.capability().id()))
                .map(td -> {
                    String params = td.params().isEmpty()
                            ? "              Params: (keine Parameter dokumentiert)"
                            : "              Params:\n" + td.params().stream()
                                    .map(param -> {
                                        String base = "                * %s%s (%s)".formatted(param.name(),
                                                param.required() ? " (required)" : "",
                                                Optional.ofNullable(param.type()).filter(s -> !s.isBlank())
                                                        .orElse("unknown"));
                                        String description = Optional.ofNullable(param.description())
                                                .filter(s -> !s.isBlank())
                                                .map(s -> " - " + s)
                                                .orElse("");
                                        String schema = Optional.ofNullable(param.schema())
                                                .filter(s -> !s.isBlank())
                                                .map(s -> "\n                  Schema: " + s)
                                                .orElse("");
                                        return base + description + schema;
                                    })
                                    .collect(Collectors.joining("\n"));

                    return "            - \"%s\": %s\n%s".formatted(td.capability().id(),
                            Optional.ofNullable(td.description()).orElse(""), params);
                })
                .collect(Collectors.joining("\n"));
        if (capabilitySection.isBlank()) {
            capabilitySection = "            - (keine Capabilities registriert)";
        }

        return """
                Du bist ein "Planner" für eine interaktive Kartenanwendung. Dir stehen verschiedene
                MCP-Funktionen zur Verfügung.

                VERFÜGBARE CAPABILITIES (capabilityId: Beschreibung):
                %s

                AUFGABE:
                - Du erhältst eine Benutzereingabe in natürlicher Sprache (z.B. Deutsch).
                - Du bestimmst ein oder mehrere Intents (Absichten) wie z.B.:
                  - "%s"   => Gehe zu einer Adresse und zeige sie auf der Karte.
                  - "%s"   => Lade einen Kartenlayer (Themenkarte).
                  - "%s"   => Suche nach einem Ort (Stadt, Berg, See, etc.).
                  - "%s"   => Hole einen ÖREB-Auszug für ein Grundstück.
                  - "%s"   => Prüfe die Machbarkeit einer Erdwärmesonde an einer Koordinate.
                  - "%s"   => Erzeuge einen Grundbuchplan (PDF) für ein Grundstück (EGRID).
                - Wenn der User mehrere Aktionen verlangt, erzeugst du mehrere Schritte (steps) und ordnest sie
                  in der gewünschten Ausführungsreihenfolge an.
                - Du planst MINIMALE Aufrufe gemäss den unter "VERFÜGBARE CAPABILITIES" gelisteten "Capabilities"
                  (MCP-Funktionen).
                - Ein Schritt (step) kann mehrere Aufrufe von "Capabilities" (MCP-Funktionen) enthalten.
                - Falls aus der Benutzereingabe hervorgeht, dass es sich nur um einen Intent (eine Absicht)
                  handelt, erzeuge auch zwingend nur einen einzelnen Step.

                WICHTIG:
                - Du rufst SELBST KEINE Capabilities aus, du erzeugst nur den Plan.
                - Du erzeugst KEINE MapActions (setView, addLayer, etc.).
                - Du gibst NUR ein JSON-Objekt zurück, kein Fliesstext.

                AUSGABEFORMAT (JSON, KEIN MARKDOWN):

                {
                  "requestId": "string",            // z.B. UUID oder kurzer String
                  "steps": [
                    {
                      "intent": "%s | %s | %s | %s | %s | %s | ...",
                      "toolCalls": [
                        {
                          "capabilityId": "string", // z.B. "%s" oder "%s"
                          "args": {
                            // Beispiel für %s:
                            // "q": "Langendorfstrasse 19b, Solothurn"
                            // Beispiel für %s:
                            // "query": "Gewässerschutzkarte"
                          }
                        }
                      ],
                      "result": {
                        "status": "pending", // Der Aufrufer führt die ToolCalls aus und füllt das Ergebnis.
                        "items": [],
                        "message": "",
                      }
                    }
                  ]
                }

                REGELN:
                - "steps" ist eine geordnete Liste. Jeder Eintrag beschreibt exakt einen Intent.
                - "toolCalls" darf leer sein, wenn du alles aus dem Kontext beantworten kannst, aber standardmässig
                  sollst du für Lokalisierungs-/Layer-/Fragen mindestens eine passende Capability vorschlagen.
                - Typische Wörter wie "Karte", "Layer" oder "Ebene" kannst du in zusammengesetzten Wörtern für die Suche von 
                  Layern ignorieren, z.B. "Gewässerschutzkarte" => "Gewässerschutz" oder "Ortsplanungsebene" => "Ortsplanung".
                - Offensichtliche Orthografiefehler korrigierst du selbständig, z.B. "Strase" => "Strasse", "Gewässr" => "Gewässer".
                - Wenn der User z.B. "Gehe zur Adresse Langendorfstrasse 19b in Solothurn" schreibt:
                  - steps: [ { "intent": "%s", "toolCalls": [ { "capabilityId": "%s", "args": { "q": "<vollständige Adresse>" } } ] } ]
                - Wenn der User z.B. "Lade mir die Gewässerschutzkarte" schreibt:
                  - steps: [ { "intent": "%s", "toolCalls": [ { "capabilityId": "%s", "args": { "query": "Gewässerschutz" } } ] } ]
                - Wenn der User "Gehe zur Adresse ... und lade die Gewässerschutzkarte" schreibt, erzeugst du zwei Schritte
                  (erst goto_address, dann load_layer).
                - Wenn der User "Ich will einen ÖREB-Auszug an der Koordinate 2607717, 1228737" schreibt, erzeugst du einen Schritt
                  (oereb_extract) mit mehreren (zwei) tool calls:
                  - steps: [ { "intent": "%s", "toolCalls": [ { "capabilityId": "%s", "args": { "x": "2607717", "y": "1228737" } },
                    { "capabilityId": "%s", "args": { "egrid": "CH1234567891012" } }] } ]
                - Wenn der User "mache mir einen Grundbuchplan für Grundstück 123 in Messen" schreibt, erzeuge einen Schritt
                  (cadastral_plan) mit zwei ToolCalls:
                  - steps: [ { "intent": "%s", "toolCalls": [ { "capabilityId": "%s", "args": { "number": "123", "municipality": "Messen" } },
                    { "capabilityId": "%s", "args": { "geometry": { /* GeoJSON aus Auswahl */ } } } ] } ]

                ANTWORT:
                - Gib nur das JSON-Objekt entsprechend dem Schema zurück.
                - Keine Kommentare, kein Markdown, kein zusätzlicher Text.
                """.formatted(
                capabilitySection,
                IntentType.GOTO_ADDRESS.id(),
                IntentType.LOAD_LAYER.id(),
                IntentType.SEARCH_PLACE.id(),
                IntentType.OEREB_EXTRACT.id(),
                IntentType.GEOTHERMAL_PROBE_ASSESSMENT.id(),
                IntentType.CADASTRAL_PLAN.id(),
                IntentType.GOTO_ADDRESS.id(),
                IntentType.LOAD_LAYER.id(),
                IntentType.SEARCH_PLACE.id(),
                IntentType.OEREB_EXTRACT.id(),
                IntentType.GEOTHERMAL_PROBE_ASSESSMENT.id(),
                IntentType.CADASTRAL_PLAN.id(),
                McpToolCapability.GEOLOCATION_GEOCODE.id(),
                McpToolCapability.LAYERS_SEARCH.id(),
                McpToolCapability.GEOLOCATION_GEOCODE.id(),
                McpToolCapability.LAYERS_SEARCH.id(),
                IntentType.GOTO_ADDRESS.id(),
                McpToolCapability.GEOLOCATION_GEOCODE.id(),
                IntentType.LOAD_LAYER.id(),
                McpToolCapability.LAYERS_SEARCH.id(),
                IntentType.OEREB_EXTRACT.id(),
                McpToolCapability.OEREB_EGRID_BY_XY.id(),
                McpToolCapability.OEREB_EXTRACT_BY_ID.id(),
                IntentType.CADASTRAL_PLAN.id(),
                McpToolCapability.FEATURE_SEARCH_EGRID_BY_NUMBER_AND_MUNICIPALITY.id(),
                McpToolCapability.PROCESSING_CADASTRAL_PLAN_BY_GEOMETRY.id());
    }

}
