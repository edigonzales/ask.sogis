package ch.so.agi.ask.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import ch.so.agi.ask.config.LandregPrintProperties;
import ch.so.agi.ask.mcp.PrintFileStorage;
import java.time.Clock;

class ProcessingToolsTest {

    private static final String SAMPLE_XML = """
            <GetFeatureInfoResponse>
                <Layer name="Bohrtiefenabfrage Erdwärmesonden" layername="ch.so.afu.ewsabfrage.abfrage"
                    layerinfo="ch.so.afu.ewsabfrage.abfrage">
                    <Feature id="">
                        <HtmlContent inline="1">
            &lt;table class=&quot;attribute-list&quot;&gt;
             &lt;tbody&gt;
              &lt;tr&gt;
                &lt;td class=&quot;identify-attr-title wrap&quot;&gt;&lt;i&gt;Resultat:&lt;/i&gt;&lt;/td&gt;
                &lt;td class=&quot;identify-attr-value wrap&quot;&gt;Das Erstellen von Erdwärmesonden ist an diesem Standort bis in eine Tiefe von 400 Metern möglich. Für weitere Angaben klicken Sie bitte auf den PDF-Link.&lt;/td&gt;
              &lt;/tr&gt;
              &lt;tr&gt;
                  &lt;td class=&quot;identify-attr-title wrap&quot;&gt;&lt;i&gt;PDF-Link:&lt;/i&gt;&lt;/td&gt;
              \t  \t
              \t      &lt;td class=&quot;identify-attr-value wrap&quot;&gt;&lt;a href='https://dox42.so.ch/dox42restservice.ashx?Operation=GenerateDocument&amp;ReturnAction.Format=pdf&amp;DocTemplate=c%3a%5cdox42Server%5ctemplates%5cAFU%5cEWS_moeglich.docx&amp;InputParam.p_koordinate_x=2600565&amp;InputParam.p_koordinate_y=1215512&amp;InputParam.p_grundstueck=198%20(Messen)&amp;InputParam.p_gemeinde=Messen&amp;InputParam.p_tiefe=400&amp;InputParam.p_tiefe_gruende=Malmkalke&amp;InputParam.p_gw=false' target='_blank'&gt;https://dox42.so.ch&lt;/a&gt;&lt;/td&gt;
              \t  \t
              &lt;/tr&gt;
             &lt;/tbody&gt;
            &lt;/table&gt;</HtmlContent>
                        <Attribute name="geometry" value="POINT(2600564.998625 1215511.7379660942)" type="derived" />
                        <Attribute name="resultat" value="Das Erstellen von Erdwärmesonden ist an diesem Standort bis in eine Tiefe von 400 Metern möglich. Für weitere Angaben klicken Sie bitte auf den PDF-Link." attrname="resultat" />
                        <Attribute name="tiefe" value="898" attrname="tiefe" />
                        <Attribute name="unsicherheit" value="77" attrname="unsicherheit" />
                        <Attribute name="tiefe_gruende" value="Malmkalke" attrname="tiefe_gruende" />
                        <Attribute name="pdf_link" value="&lt;a href=&#39;https://dox42.so.ch/dox42restservice.ashx?Operation=GenerateDocument&amp;ReturnAction.Format=pdf&amp;DocTemplate=c%3a%5cdox42Server%5ctemplates%5cAFU%5cEWS_moeglich.docx&amp;InputParam.p_koordinate_x=2600565&amp;InputParam.p_koordinate_y=1215512&amp;InputParam.p_grundstueck=198%20(Messen)&amp;InputParam.p_gemeinde=Messen&amp;InputParam.p_tiefe=400&amp;InputParam.p_tiefe_gruende=Malmkalke&amp;InputParam.p_gw=false&#39; target=&#39;_blank&#39;&gt;https://dox42.so.ch&lt;/a&gt;" attrname="pdf_link" />
                    </Feature>
                </Layer>
            </GetFeatureInfoResponse>
            """;

    @Test
    void parseFeatureInfo_extractsResultTextAndPdfLink() throws Exception {
        LandregPrintProperties properties = new LandregPrintProperties();
        ProcessingTools tools = new ProcessingTools(RestClient.builder(), properties,
                new PrintFileStorage(properties, Clock.systemUTC()));

        ProcessingTools.ParsedFeature parsed = tools.parseFeatureInfo(SAMPLE_XML);

        assertThat(parsed.resultText())
                .contains("Erdwärmesonden")
                .contains("400");
        assertThat(parsed.linkInfo()).isNotNull();
        assertThat(parsed.linkInfo().href()).contains("dox42restservice");
        assertThat(parsed.linkInfo().label()).isEqualTo("https://dox42.so.ch");
    }
}
