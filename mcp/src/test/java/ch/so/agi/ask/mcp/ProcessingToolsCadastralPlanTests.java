package ch.so.agi.ask.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ch.so.agi.ask.config.LandregPrintProperties;
import ch.so.agi.ask.mcp.ToolResult.Status;

class ProcessingToolsCadastralPlanTests {

    private LandregPrintProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LandregPrintProperties();
        properties.setService("http://example.com/landreg/print");
        properties.setAllowedScales(List.of(100, 200, 500));
        properties.setAllowedGridIntervals(List.of(10, 20, 50, 100, 200));
        properties.setGridIntervalTargetDivisor(3);
        properties.setLayoutWidth(0.196);
        properties.setLayoutHeight(0.244);
    }

    @Test
    void buildPrintRequestExpandsExtentAndChoosesScale() {
        ProcessingTools tools = new ProcessingTools(RestClient.builder(), properties);

        ProcessingTools.PrintRequest request = tools.buildPrintRequest(List.of(2600d, 1200d, 2610d, 1205d));

        assertThat(request.scaleDenominator()).isEqualTo(100);
        assertThat(request.gridInterval()).isEqualTo(10);
        assertThat(request.extent().get(2)).isGreaterThan(2610d);
        assertThat(request.extent().get(3)).isGreaterThan(1205d);
    }

    @Test
    void createsPdfItemFromPrintService() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        ProcessingTools tools = new ProcessingTools(builder, properties);

        server.expect(requestTo(properties.getService()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("TEMPLATE=A4-Hoch")))
                .andRespond(withSuccess("%PDF-1.7".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF));

        Map<String, Object> geometry = Map.of("type", "Polygon",
                "coordinates", List.of(List.of(List.of(0d, 0d), List.of(0d, 10d), List.of(10d, 10d), List.of(10d, 0d),
                        List.of(0d, 0d))));

        ProcessingTools.ProcessingResult result = tools
                .getCadastralPlanByGeometry(Map.of("geometry", geometry));

        server.verify();

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        Map<String, Object> payload = McpResponseItem.payload(result.items().getFirst());

        assertThat(payload.get("pdfUrl")).asString().startsWith("data:application/pdf");
        assertThat(payload.get("extent")).isInstanceOf(List.class);
        assertThat(payload.get("geometry")).isInstanceOf(Map.class);
        assertThat(payload.get("scaleDenominator")).isEqualTo(100);
    }
}
