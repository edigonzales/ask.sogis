package ch.so.agi.ask.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OerebToolsTests {

    @SuppressWarnings("unchecked")
    @Test
    void parsesMultipleParcelsWithGeometryAndType() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ns5:GetEGRIDResponse xmlns:ns1="http://www.interlis.ch/geometry/1.0" xmlns:ns3="http://schemas.geo.admin.ch/V_D/OeREB/2.0/ExtractData" xmlns:ns5="http://schemas.geo.admin.ch/V_D/OeREB/2.0/Extract">
                  <ns5:egrid>CH955832730623</ns5:egrid>
                  <ns5:number>198</ns5:number>
                  <ns5:identDN>SO0200002457</ns5:identDN>
                  <ns5:type>
                    <ns3:Code>RealEstate</ns3:Code>
                    <ns3:Text>
                      <ns3:LocalisedText>
                        <ns3:Language>de</ns3:Language>
                        <ns3:Text>Liegenschaft</ns3:Text>
                      </ns3:LocalisedText>
                    </ns3:Text>
                  </ns5:type>
                  <ns5:limit>
                    <ns1:surface>
                      <ns1:exterior>
                        <ns1:polyline>
                          <ns1:coord><ns1:c1>2600483.775</ns1:c1><ns1:c2>1215520.876</ns1:c2></ns1:coord>
                          <ns1:coord><ns1:c1>2600484.721</ns1:c1><ns1:c2>1215521.387</ns1:c2></ns1:coord>
                          <ns1:coord><ns1:c1>2600508.524</ns1:c1><ns1:c2>1215534.428</ns1:c2></ns1:coord>
                        </ns1:polyline>
                      </ns1:exterior>
                    </ns1:surface>
                  </ns5:limit>
                  <ns5:egrid>CH710620327442</ns5:egrid>
                  <ns5:number>531</ns5:number>
                  <ns5:identDN>SO0200002457</ns5:identDN>
                  <ns5:type>
                    <ns3:Code>Distinct_and_permanent_rights.BuildingRight</ns3:Code>
                    <ns3:Text>
                      <ns3:LocalisedText>
                        <ns3:Language>de</ns3:Language>
                        <ns3:Text>Baurecht</ns3:Text>
                      </ns3:LocalisedText>
                    </ns3:Text>
                  </ns5:type>
                  <ns5:limit>
                    <ns1:surface>
                      <ns1:exterior>
                        <ns1:polyline>
                          <ns1:coord><ns1:c1>2600483.775</ns1:c1><ns1:c2>1215520.876</ns1:c2></ns1:coord>
                          <ns1:coord><ns1:c1>2600504.639</ns1:c1><ns1:c2>1215491.94</ns1:c2></ns1:coord>
                          <ns1:coord><ns1:c1>2600548.307</ns1:c1><ns1:c2>1215438.065</ns1:c2></ns1:coord>
                        </ns1:polyline>
                      </ns1:exterior>
                    </ns1:surface>
                  </ns5:limit>
                </ns5:GetEGRIDResponse>
                """;

        OerebTools tools = new OerebTools(RestClient.builder());
        Method parse = OerebTools.class.getDeclaredMethod("parseResponse", String.class, List.class);
        parse.setAccessible(true);

        List<Map<String, Object>> items = (List<Map<String, Object>>) parse.invoke(tools, xml,
                List.of(2600513.0, 1215519.0));

        assertThat(items).hasSize(2);
        Map<String, Object> first = items.getFirst();
        Map<String, Object> second = items.get(1);

        assertThat(first.get("egrid")).isEqualTo("CH955832730623");
        assertThat(first.get("propertyType")).isEqualTo("Liegenschaft");
        assertThat(first.get("geometry")).isInstanceOf(Map.class);
        assertThat(second.get("label")).asString().contains("Baurecht");
        assertThat(second.get("geometry")).isInstanceOf(Map.class);
        assertThat(second.get("coord")).isInstanceOf(List.class);
    }

    @Test
    void extractByIdAddsUrlsAndGeometryFromSelection() {
        OerebTools tools = new OerebTools(RestClient.builder());
        Map<String, Object> geometry = Map.of("type", "Polygon", "coordinates", List.of(List.of(List.of(1d, 2d))));
        Map<String, Object> result = tools.getOerebExtractById(
                Map.of("selection", Map.of("egrid", "CH123", "coord", List.of(1d, 2d), "geometry", geometry)))
                .items().getFirst();

        assertThat(result.get("pdfUrl")).isEqualTo("https://geo.so.ch/api/oereb/extract/pdf/?EGRID=CH123");
        assertThat(result.get("mapUrl")).isEqualTo("https://geo.so.ch/map/?oereb_egrid=CH123");
        assertThat(result.get("geometry")).isEqualTo(geometry);
        assertThat(result.get("coord")).isEqualTo(List.of(1d, 2d));
    }
}
