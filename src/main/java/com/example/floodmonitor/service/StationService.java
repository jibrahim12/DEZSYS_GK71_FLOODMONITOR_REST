package com.example.floodmonitor.service;

import com.example.floodmonitor.model.*;
import com.example.floodmonitor.repository.StationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service fuer Geschaeftslogik, Filterung und statistische Berechnungen.
 */
@Service
public class StationService {

    private final StationRepository repository;

    public StationService(StationRepository repository) {
        this.repository = repository;
    }

    public List<Station> getStations(String river, Boolean isActive, WarningLevel warningLevel) {
        return repository.findAllStations().stream()
                .filter(s -> river == null || s.getRiver().equalsIgnoreCase(river))
                .filter(s -> isActive == null || s.isActive() == isActive)
                .filter(s -> {
                    if (warningLevel == null) return true;
                    Optional<Measurement> latest = repository.findLatestMeasurement(s.getId());
                    return latest.map(m -> m.getWarningLevel() == warningLevel).orElse(warningLevel == WarningLevel.UNKNOWN);
                })
                .collect(Collectors.toList());
    }

    public Optional<Station> getStationById(String id) {
        return repository.findStationById(id);
    }

    public Optional<Measurement> getLatestMeasurement(String stationId) {
        return repository.findLatestMeasurement(stationId);
    }

    public List<Measurement> getMeasurements(String stationId, Instant from, Instant to, Integer limit, WarningLevel warningLevel) {
        List<Measurement> list = repository.findMeasurementsByStationId(stationId);

        return list.stream()
                .filter(m -> from == null || !m.getTimestamp().isBefore(from))
                .filter(m -> to == null || !m.getTimestamp().isAfter(to))
                .filter(m -> warningLevel == null || m.getWarningLevel() == warningLevel)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())) // Neueste Messung zuerst
                .limit(limit != null ? limit : 50)
                .collect(Collectors.toList());
    }

    public List<Alert> getAlerts(WarningLevel warningLevel, String river) {
        List<Alert> alerts = new ArrayList<>();
        for (Station station : repository.findAllStations()) {
            if (river != null && !station.getRiver().equalsIgnoreCase(river)) {
                continue;
            }
            Optional<Measurement> latest = repository.findLatestMeasurement(station.getId());
            if (latest.isPresent()) {
                Measurement m = latest.get();
                if (m.getWarningLevel() == WarningLevel.WARNING || m.getWarningLevel() == WarningLevel.CRITICAL) {
                    if (warningLevel == null || m.getWarningLevel() == warningLevel) {
                        alerts.add(new Alert(station.getId(), station.getName(), station.getRiver(),
                                m.getWarningLevel(), m.getWaterLevel(), m.getTimestamp()));
                    }
                }
            }
        }
        return alerts;
    }

    public StationStats calculateStats(String stationId) {
        List<Measurement> list = repository.findMeasurementsByStationId(stationId);
        if (list.isEmpty()) {
            return new StationStats(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0);
        }

        double minWater = list.stream().mapToDouble(Measurement::getWaterLevel).min().orElse(0.0);
        double maxWater = list.stream().mapToDouble(Measurement::getWaterLevel).max().orElse(0.0);
        double avgWater = Math.round(list.stream().mapToDouble(Measurement::getWaterLevel).average().orElse(0.0) * 10.0) / 10.0;
        double avgFlow = Math.round(list.stream().mapToDouble(Measurement::getFlowRate).average().orElse(0.0) * 10.0) / 10.0;
        double totalRain = Math.round(list.stream().mapToDouble(Measurement::getRainfall).sum() * 10.0) / 10.0;
        int count = list.size();
        int warnings = (int) list.stream().filter(m -> m.getWarningLevel() == WarningLevel.WARNING).count();
        int criticals = (int) list.stream().filter(m -> m.getWarningLevel() == WarningLevel.CRITICAL).count();

        return new StationStats(minWater, maxWater, avgWater, avgFlow, totalRain, count, warnings, criticals);
    }
}