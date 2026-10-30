package com.example.floodmonitor.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "stationStats")
public class StationStats {
    private double minWaterLevel;
    private double maxWaterLevel;
    private double avgWaterLevel;
    private double avgFlowRate;
    private double totalRainfall;
    private int measurementCount;
    private int warningCount;
    private int criticalCount;

    public StationStats() {
    }

    public StationStats(double minWaterLevel, double maxWaterLevel, double avgWaterLevel,
                        double avgFlowRate, double totalRainfall, int measurementCount,
                        int warningCount, int criticalCount) {
        this.minWaterLevel = minWaterLevel;
        this.maxWaterLevel = maxWaterLevel;
        this.avgWaterLevel = avgWaterLevel;
        this.avgFlowRate = avgFlowRate;
        this.totalRainfall = totalRainfall;
        this.measurementCount = measurementCount;
        this.warningCount = warningCount;
        this.criticalCount = criticalCount;
    }
    public double getMinWaterLevel() {
        return minWaterLevel;
    }
    public double getMaxWaterLevel() {
        return maxWaterLevel;
    }
    public double getAvgWaterLevel() {
        return avgWaterLevel;
    }
    public double getAvgFlowRate() {
        return avgFlowRate;
    }
    public double getTotalRainfall() {
        return totalRainfall;
    }
    public int getMeasurementCount() {
        return measurementCount;
    }
    public int getWarningCount() {
        return warningCount;
    }
    public int getCriticalCount() {
        return criticalCount;
    }
}