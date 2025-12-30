package ch.so.agi.ask.mcp;

import java.util.List;
import java.util.Map;

public interface ToolResult {
    Status status();
    List<Map<String,Object>> items();
    String message();

    enum Status {
        SUCCESS,
        NEEDS_USER_CHOICE,
        ERROR
    }
}
