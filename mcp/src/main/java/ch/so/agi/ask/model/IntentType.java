package ch.so.agi.ask.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Central enumeration of intents the planner can emit and the ActionPlanner
 * understands. Serialized/deserialized as the lowercase identifiers used in the
 * system prompt (e.g. {@code "goto_address"}).
 */
public enum IntentType {
    GOTO_ADDRESS("goto_address"),
    LOAD_LAYER("load_layer"),
    SEARCH_PLACE("search_place"),
    OEREB_EXTRACT("oereb_extract"),
    GEOTHERMAL_PROBE_ASSESSMENT("geothermal_probe_assessment"),
    CADASTRAL_PLAN("cadastral_plan");

    private final String id;

    IntentType(String id) {
        this.id = id;
    }

    @JsonValue
    public String id() {
        return id;
    }

    @JsonCreator
    public static IntentType fromId(String value) {
        for (IntentType intent : values()) {
            if (intent.id.equalsIgnoreCase(value)) {
                return intent;
            }
        }
        throw new IllegalArgumentException("Unknown intent type: " + value);
    }
}
