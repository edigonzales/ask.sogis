package ch.so.agi.ask.model;

import java.util.List;

/**
 * Konsistente Antwort an den Client. Ab sofort können mehrere Intents pro
 * Benutzeranfrage abgearbeitet werden. Jeder Intent wird als separater Step
 * zurückgegeben, sodass der Client MapActions sequentiell abspielen kann.
 */
public record ChatResponse(String requestId, List<Step> steps, String overallStatus) {
    public record Step(IntentType intent, String status, String message, List<MapAction> mapActions,
            List<Choice> choices) {
    }
}
