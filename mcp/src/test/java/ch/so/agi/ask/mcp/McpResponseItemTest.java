package ch.so.agi.ask.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class McpResponseItemTest {

    @Test
    void derivesCentroidFromGeometryWhenMissing() {
        Map<String, Object> geometry = Map.of(
                "type", "Polygon",
                "coordinates", List.of(
                        List.of(
                                List.of(0d, 0d),
                                List.of(2d, 0d),
                                List.of(2d, 2d),
                                List.of(0d, 2d)
                        )));
        Map<String, Object> item = Map.of("payload", Map.of("geometry", geometry));

        assertThat(McpResponseItem.centroid(item)).containsExactly(1d, 1d);
    }

    @Test
    void derivesExtentFromGeometry() {
        Map<String, Object> geometry = Map.of(
                "type", "LineString",
                "coordinates", List.of(
                        List.of(1d, 2d),
                        List.of(3d, 5d),
                        List.of(2d, 4d)
                ));
        Map<String, Object> item = Map.of("payload", Map.of("geometry", geometry));

        assertThat(McpResponseItem.extent(item)).containsExactly(1d, 2d, 3d, 5d);
    }

    @Test
    void normalizesGeometryAndPayload() {
        Map<String, Object> raw = Map.of("payload", Map.of("centroid", List.of(7, "8")));

        assertThat(McpResponseItem.centroid(raw)).containsExactly(7d, 8d);
        assertThat(McpResponseItem.geometry(raw)).isEmpty();
    }
}
