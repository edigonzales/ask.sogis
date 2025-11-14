package ch.so.agi.ask.core;

import java.util.List;

import ch.so.agi.ask.model.Choice;
import ch.so.agi.ask.model.MapAction;

public record ActionPlan(String status, String message, List<MapAction> mapActions, List<Choice> choices) {
    public static ActionPlan ok(List<MapAction> actions, String msg) {
        return new ActionPlan("ok", msg, actions, List.of());
    }

    public static ActionPlan needsUserChoice(List<Choice> choices, String msg) {
        return new ActionPlan("needs_user_choice", msg, List.of(), choices);
    }

    public static ActionPlan needsClarification(String msg) {
        return new ActionPlan("needs_clarification", msg, List.of(), List.of());
    }

    public static ActionPlan error(String msg) {
        return new ActionPlan("error", msg, List.of(), List.of());
    }
}
