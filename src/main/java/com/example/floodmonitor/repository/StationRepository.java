package com.example.floodmonitor.repository;

import com.example.floodmonitor.model.*;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class StationRepository {

    // Thread-sichere Maps, da der Simulator im Hintergrund laeuft und Controller gleichzeitig lesen
    private final Map<String, Station> stations = new ConcurrentHashMap<>();
    private final Map<String, List<Measurement>> measurements = new ConcurrentHashMap<>();

    public StationRepository() {
        initStation(new Station("ST-001", "Wien Reichsbrücke", "Donau",
                new Location(48.2272, 16.4093), 250.0, 450.0, 600.0, true));

        initStation(new Station("ST-002", "Korneuburg", "Donau",
                new Location(48.3444, 16.3156), 280.0, 500.0, 650.0, true));

        initStation(new Station("ST-003", "Schärding", "Inn",
                new Location(48.4561, 13.4319), 300.0, 520.0, 700.0, true));
    }

    private void initStation(Station station) {
        stations.put(station.getId(), station);
        measurements.put(station.getId(), new CopyOnWriteArrayList<>());
    }

    public List<Station> findAllStations() {
        return new ArrayList<>(stations.values());
    }

    public Optional<Station> findStationById(String id) {
        return Optional.ofNullable(stations.get(id));
    }

    public void addMeasurement(String stationId, Measurement measurement) {
        measurements.computeIfAbsent(stationId, k -> new CopyOnWriteArrayList<>()).add(measurement);
    }

    public List<Measurement> findMeasurementsByStationId(String stationId) {
        return new ArrayList<>(measurements.getOrDefault(stationId, Collections.emptyList()));
    }

    public Optional<Measurement> findLatestMeasurement(String stationId) {
        List<Measurement> list = measurements.get(stationId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(list.size() - 1));
    }
}