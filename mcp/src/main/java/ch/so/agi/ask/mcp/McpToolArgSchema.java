package ch.so.agi.ask.mcp;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Optional annotation to document the expected structure of a tool argument.
 * <p>
 * Most MCP tools accept a {@code Map<String, Object>} argument. Use this
 * annotation to provide a concise schema (e.g. expected keys and formats)
 * so the planner prompt can be generated automatically without manual
 * maintenance.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface McpToolArgSchema {
    /**
     * Human-readable schema description, for example a JSON-like structure
     * of expected keys.
     */
    String value();
}
