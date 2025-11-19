package ch.so.agi.ask.mcp;


import ch.so.agi.ask.model.McpToolCapability;
import ch.so.agi.ask.model.PlannerOutput;

import java.util.Map;

public interface ToolRegistry {

    /**
     * Führt ein Tool mit der gegebenen capabilityId und args aus.
     */
    PlannerOutput.Result execute(McpToolCapability capabilityId, Map<String,Object> args);

    /**
     * Optional: Tools auflisten (für Debug/Prompt-Generierung usw.).
     */
    Map<McpToolCapability, ToolDescriptor> listTools();

    record ToolDescriptor(
            McpToolCapability capability,
            String description,
            Class<?> beanType,
            String methodName
    ) {}
}
