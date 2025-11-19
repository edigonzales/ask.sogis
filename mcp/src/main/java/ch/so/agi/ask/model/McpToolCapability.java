package ch.so.agi.ask.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates the MCP tool identifiers that can be produced by the planner and
 * consumed by the MCP client/registry.
 */
public enum McpToolCapability {
    GEO_GEOCODE("geo.geocode"),
    LAYERS_SEARCH("layers.search");

    private final String id;

    McpToolCapability(String id) {
        this.id = id;
    }

    @JsonValue
    public String id() {
        return id;
    }

    @JsonCreator
    public static McpToolCapability fromId(String value) {
        for (McpToolCapability capability : values()) {
            if (capability.id.equalsIgnoreCase(value)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("Unknown MCP tool capability: " + value);
    }
}
