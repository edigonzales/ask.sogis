package ch.so.agi.ask.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Konfiguriert den Landregister-Print-Service und druckrelevante Parameter für
 * den Grundbuchplan. Standardwerte folgen dem bisherigen Print-Setup; können
 * aber via {@code application.properties} überschrieben werden.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "landreg.print")
public class LandregPrintProperties {
    private String service = "https://geo.so.ch/api/v1/landreg/print";
    private String template = "A4-Hoch";
    private String srs = "EPSG:2056";
    private double layoutWidth = 0.196;
    private double layoutHeight = 0.244;
    private int dpi = 200;
    private int gridIntervalTargetDivisor = 3;
    private long storageTtlSeconds = 600;
    private String storageDirectory = "";
    private String downloadBasePath = "/api/prints";

    private List<Integer> allowedScales = new ArrayList<>(Arrays.asList(100, 150, 200, 250, 500, 750, 1000, 2000, 2500,
            3000, 4000, 5000, 7500, 10000, 20000, 25000, 50000, 100000, 200000, 250000, 500000, 1000000));

    private List<Integer> allowedGridIntervals = new ArrayList<>(
            Arrays.asList(10, 20, 25, 50, 100, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 2500, 3000, 4000, 5000,
                    7500, 10000, 20000, 50000, 100000, 200000, 500000, 1000000));

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getSrs() {
        return srs;
    }

    public void setSrs(String srs) {
        this.srs = srs;
    }

    public double getLayoutWidth() {
        return layoutWidth;
    }

    public void setLayoutWidth(double layoutWidth) {
        this.layoutWidth = layoutWidth;
    }

    public double getLayoutHeight() {
        return layoutHeight;
    }

    public void setLayoutHeight(double layoutHeight) {
        this.layoutHeight = layoutHeight;
    }

    public int getDpi() {
        return dpi;
    }

    public void setDpi(int dpi) {
        this.dpi = dpi;
    }

    public int getGridIntervalTargetDivisor() {
        return gridIntervalTargetDivisor;
    }

    public void setGridIntervalTargetDivisor(int gridIntervalTargetDivisor) {
        this.gridIntervalTargetDivisor = gridIntervalTargetDivisor;
    }

    public long getStorageTtlSeconds() {
        return storageTtlSeconds;
    }

    public void setStorageTtlSeconds(long storageTtlSeconds) {
        this.storageTtlSeconds = storageTtlSeconds;
    }

    public String getStorageDirectory() {
        return storageDirectory;
    }

    public void setStorageDirectory(String storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    public String getDownloadBasePath() {
        return downloadBasePath;
    }

    public void setDownloadBasePath(String downloadBasePath) {
        this.downloadBasePath = downloadBasePath;
    }

    public List<Integer> getAllowedScales() {
        return allowedScales;
    }

    public void setAllowedScales(List<Integer> allowedScales) {
        this.allowedScales = allowedScales;
    }

    public List<Integer> getAllowedGridIntervals() {
        return allowedGridIntervals;
    }

    public void setAllowedGridIntervals(List<Integer> allowedGridIntervals) {
        this.allowedGridIntervals = allowedGridIntervals;
    }
}
