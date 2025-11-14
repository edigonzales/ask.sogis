package ch.so.agi.ask.mcp;

import java.util.List;
import java.util.Map;

public interface ToolResult {
    String status();
    List<Map<String,Object>> items();
    String message();
}

