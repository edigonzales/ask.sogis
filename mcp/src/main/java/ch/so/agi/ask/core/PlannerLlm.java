package ch.so.agi.ask.core;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

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
            - Du bestimmst einen Intent (Absicht) wie z.B.:
              - "goto_address"   => Gehe zu einer Adresse und zeige sie auf der Karte.
              - "load_layer"     => Lade einen Kartenlayer (Themenkarte).
              - "search_place"   => Suche nach einem Ort (Stadt, Berg, See, etc.).
            - Du planst MINIMALE Aufrufe von "Capabilities" (MCP-Funktionen), z.B.:
              - "geo.geocode"    => Wandelt einen Adress-String in Koordinaten um.
              - "layers.search"  => Findet passende Layer zu einem Thema.

            WICHTIG:
            - Du rufst SELBST KEINE Capabilities aus, du erzeugst nur den Plan.
            - Du erzeugst KEINE MapActions (setView, addLayer, etc.).
            - Du gibst NUR ein JSON-Objekt zurück, kein Fließtext.

            AUSGABEFORMAT (JSON, KEIN MARKDOWN):

            {
              "requestId": "string",            // eine zufällige ID, z.B. UUID oder kurzer String
              "intent": "goto_address | load_layer | search_place | ...",
              "toolCalls": [
                {
                  "capabilityId": "string",     // z.B. "geo.geocode" oder "layers.search"
                  "args": {                     // JSON-Objekt mit den Argumenten für diese Capability
                    // Beispiel für geo.geocode:
                    // "q": "Langendorfstrasse 19b, Solothurn"
                    // Beispiel für layers.search:
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

            REGELN:
            - "toolCalls" darf leer sein, wenn du alles aus dem Kontext beantworten kannst, aber standardmäßig
              sollst du für geo-/Layer-Fragen mindestens eine passende Capability vorschlagen.
            - Wenn der User z.B. "Gehe zur Adresse Langendorfstrasse 19b in Solothurn" schreibt:
              - intent: "goto_address"
              - toolCalls: [ { "capabilityId": "geo.geocode", "args": { "q": "<vollständige Adresse>" } } ]
            - Wenn der User z.B. "Lade mir die Gewässerschutzkarte" schreibt:
              - intent: "load_layer"
              - toolCalls: [ { "capabilityId": "layers.search", "args": { "query": "Gewässerschutz" } } ]

            ANTWORT:
            - Gib nur das JSON-Objekt entsprechend dem Schema zurück.
            - Keine Kommentare, kein Markdown, kein zusätzlicher Text.
            """;

    public PlannerLlm(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Baut den Prompt aus System- und User-Message, ruft das Planner-LLM und
     * deserialisiert das JSON in das interne {@link PlannerOutput} (mit Intent,
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
