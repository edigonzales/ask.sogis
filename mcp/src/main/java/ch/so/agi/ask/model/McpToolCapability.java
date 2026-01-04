package ch.so.agi.ask.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates the MCP tool identifiers that can be produced by the planner and
 * consumed by the MCP client/registry.
 */
public enum McpToolCapability {
    GEOLOCATION_GEOCODE("geolocation.geocode"),
    LAYERS_SEARCH("layers.search"),
    OEREB_EGRID_BY_XY("oereb.egridByXY"),
    OEREB_EXTRACT_BY_ID("oereb.extractById"),
    FEATURE_SEARCH_EGRID_BY_NUMBER_AND_MUNICIPALITY("featureSearch.getEgridByNumberAndMunicipality"),
    PROCESSING_GEOTHERMAL_BORE_INFO_BY_XY("processing.getGeothermalBoreInfoByXY"),
    PROCESSING_CADASTRAL_PLAN_BY_EGRID("processing.getCadastralPlanByEgrid");

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
