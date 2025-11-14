package ch.so.agi.ask.core;

import org.springframework.stereotype.Component;

import ch.so.agi.ask.mcp.ToolRegistry;
import ch.so.agi.ask.model.PlannerOutput;

import java.util.Map;

@Component
public class McpClient {

    private final ToolRegistry toolRegistry;

    public McpClient(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public PlannerOutput.Result execute(String capabilityId, Map<String, Object> args) {
        return toolRegistry.execute(capabilityId, args);
    }
}
