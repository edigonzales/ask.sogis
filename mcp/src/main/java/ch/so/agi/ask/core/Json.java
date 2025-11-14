package ch.so.agi.ask.core;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Planner JSON parse failed", e);
        }
    }
}
