package ch.so.agi.ask.core;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.IntentType;
import ch.so.agi.ask.model.McpToolCapability;
import ch.so.agi.ask.model.PlannerOutput;

import java.util.*;

/**
 * Spring-AI-gestützter Planner, der Intent und ToolCalls (Capabilities) aus der
 * Benutzereingabe extrahiert. Liefert ein {@link PlannerOutput}, das dem
 * Sequenzschritt "PlannerLlm" aus dem README entspricht und als Ausgangspunkt
 * für Orchestrator &amp; ActionPlanner dient.
 */
@Service
public class PlannerLlm {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Du bist ein "Planner" für eine interaktive Kartenanwendung. Dir stehen verschiedene
            MCP-Funktionen zur Verfügung.

            AUFGABE:
            - Du erhältst eine Benutzereingabe in natürlicher Sprache (z.B. Deutsch).
            - Du bestimmst ein oder mehrere Intents (Absichten) wie z.B.:
              - "%s"   => Gehe zu einer Adresse und zeige sie auf der Karte.
              - "%s"     => Lade einen Kartenlayer (Themenkarte).
              - "%s"   => Suche nach einem Ort (Stadt, Berg, See, etc.).
            - Wenn der User mehrere Aktionen verlangt, erzeugst du mehrere Schritte (steps) und ordnest sie
              in der gewünschten Ausführungsreihenfolge an.
            - Du planst MINIMALE Aufrufe von "Capabilities" (MCP-Funktionen), z.B.:
              - "%s"    => Wandelt einen Adress-String in Koordinaten um.
              - "%s"  => Findet passende Layer zu einem Thema.

            WICHTIG:
            - Du rufst SELBST KEINE Capabilities aus, du erzeugst nur den Plan.
            - Du erzeugst KEINE MapActions (setView, addLayer, etc.).
            - Du gibst NUR ein JSON-Objekt zurück, kein Fließtext.

            AUSGABEFORMAT (JSON, KEIN MARKDOWN):

            {
              "requestId": "string",            // z.B. UUID oder kurzer String
              "steps": [
                {
                  "intent": "%s | %s | %s | ...",
                  "toolCalls": [
                    {
                      "capabilityId": "string",     // z.B. "%s" oder "%s"
                      "args": {
                        // Beispiel für %s:
                        // "q": "Langendorfstrasse 19b, Solothurn"
                        // Beispiel für %s:
                        // "query": "Gewässerschutzkarte"
                      }
                    }
                  ],
                  "result": {
                    "status": "pending",            // Der Aufrufer führt die ToolCalls aus und füllt das Ergebnis.
                    "items": [],
                    "message": ""
                  }
                }
              ]
            }

            REGELN:
            - "steps" ist eine geordnete Liste. Jeder Eintrag beschreibt exakt einen Intent.
            - "toolCalls" darf leer sein, wenn du alles aus dem Kontext beantworten kannst, aber standardmäßig
              sollst du für geo-/Layer-Fragen mindestens eine passende Capability vorschlagen.
            - Wenn der User z.B. "Gehe zur Adresse Langendorfstrasse 19b in Solothurn" schreibt:
              - steps: [ { "intent": "%s", "toolCalls": [ { "capabilityId": "%s", "args": { "q": "<vollständige Adresse>" } } ] } ]
            - Wenn der User z.B. "Lade mir die Gewässerschutzkarte" schreibt:
              - steps: [ { "intent": "%s", "toolCalls": [ { "capabilityId": "%s", "args": { "query": "Gewässerschutz" } } ] } ]
            - Wenn der User "Gehe zur Adresse ... und lade die Gewässerschutzkarte" schreibt, erzeugst du zwei Schritte
              (erst goto_address, dann load_layer).

            ANTWORT:
            - Gib nur das JSON-Objekt entsprechend dem Schema zurück.
            - Keine Kommentare, kein Markdown, kein zusätzlicher Text.
            """.formatted(
            IntentType.GOTO_ADDRESS.id(),
            IntentType.LOAD_LAYER.id(),
            IntentType.SEARCH_PLACE.id(),
            McpToolCapability.GEO_GEOCODE.id(),
            McpToolCapability.LAYERS_SEARCH.id(),
            IntentType.GOTO_ADDRESS.id(),
            IntentType.LOAD_LAYER.id(),
            IntentType.SEARCH_PLACE.id(),
            McpToolCapability.GEO_GEOCODE.id(),
            McpToolCapability.LAYERS_SEARCH.id(),
            McpToolCapability.GEO_GEOCODE.id(),
            McpToolCapability.LAYERS_SEARCH.id(),
            IntentType.GOTO_ADDRESS.id(),
            McpToolCapability.GEO_GEOCODE.id(),
            IntentType.LOAD_LAYER.id(),
            McpToolCapability.LAYERS_SEARCH.id());

    public PlannerLlm(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Baut den Prompt aus System- und User-Message, ruft das Planner-LLM und
     * deserialisiert das JSON in das interne {@link PlannerOutput} (mit Steps,
     * ToolCalls und initialem Result-Status {@code pending}).
     */
    public PlannerOutput plan(String sessionId, String userMessage) {
        List<Message> messages = List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userMessage));

        var prompt = new Prompt(messages);
        var content = chatClient.prompt(prompt).call().content(); // JSON string

        // Deserialisieren in PlannerOutput (ObjectMapper empfohlen)
        return Json.read(content, PlannerOutput.class);
    }
}
