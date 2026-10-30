package com.example.floodmonitor.service;

import com.example.floodmonitor.model.*;
import com.example.floodmonitor.repository.StationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Service
public class SimulationService {

    private final StationRepository stationRepository;
    private final Random random = new Random();

    @Value("${simulation.enabled:true}")
    private boolean simulationEnabled;

    public SimulationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
        initInitialMeasurements();
    }

    private void initInitialMeasurements() {
        for (Station station : stationRepository.findAllStations()) {
            if (station.isActive()) {
                double initialWaterLevel = station.getNormalWaterLevel();
                double initialFlowRate = Math.round(initialWaterLevel * 2.3 * 10.0) / 10.0;
                Measurement m = new Measurement(
                        station.getId(),
                        Instant.now(),
                        initialWaterLevel,
                        initialFlowRate,
                        0.0,
                        15.0,
                        100,
                        StationStatus.ONLINE,
                        WarningLevel.NORMAL
                );
                stationRepository.addMeasurement(station.getId(), m);
            }
        }
    }

    @Scheduled(fixedDelayString = "${simulation.interval:10000}")
    public void runSimulation() {
        if (!simulationEnabled) {
            return;
        }

        for (Station station : stationRepository.findAllStations()) {
            if (!station.isActive()) {
                continue;
            }
            simulateMeasurementForStation(station);
        }
    }

    public Measurement simulateMeasurementForStation(Station station) {
        Optional<Measurement> prevOpt = stationRepository.findLatestMeasurement(station.getId());

        double prevWater = prevOpt.map(Measurement::getWaterLevel).orElse(station.getNormalWaterLevel());
        int prevBattery = prevOpt.map(Measurement::getBatteryLevel).orElse(100);
        double prevTemp = prevOpt.map(Measurement::getTemperature).orElse(15.0);

        // 1. Niederschlag simulieren (0 bis ca. 15 mm/h mit Hang zu trockenen Werten)
        double rainfall = Math.round(Math.max(0.0, (random.nextDouble() * 15.0) - 2.0) * 10.0) / 10.0;

        // 2. Wasserstand abhaengig von vorherigem Wert und Niederschlag berechnen
        // Viel Regen laesst Wasser steigen, wenig/kein Regen laesst Pegel leicht sinken
        double waterDelta = (rainfall > 3.0 ? (rainfall * 1.5) : -2.0) + (random.nextDouble() * 4.0 - 2.0);
        double waterLevel = Math.round(Math.max(20.0, prevWater + waterDelta) * 10.0) / 10.0;

        // 3. Durchflussmenge ist direkt an den Wasserstand gekoppelt
        double flowRate = Math.round(Math.max(10.0, waterLevel * 2.3 + (random.nextDouble() * 10.0 - 5.0)) * 10.0) / 10.0;

        // 4. Lufttemperatur schwankt leicht um +/- 0.5 Grad
        double temperature = Math.round((prevTemp + (random.nextDouble() - 0.5)) * 10.0) / 10.0;

        // 5. Akku entlaedt sich realistisch langsam (1% alle paar Ticks)
        int batteryLevel = Math.max(0, prevBattery - (random.nextInt(4) == 0 ? 1 : 0));

        // 6. Technischer Zustand (unter 15 % Akku wechselt Status auf MAINTENANCE)
        StationStatus status = StationStatus.ONLINE;
        if (batteryLevel < 15) {
            status = StationStatus.MAINTENANCE;
        }

        // 7. Warnstufe serverseitig berechnen
        WarningLevel warningLevel = calculateWarningLevel(station, waterLevel, status);

        Measurement measurement = new Measurement(
                station.getId(),
                Instant.now(),
                waterLevel,
                flowRate,
                rainfall,
                temperature,
                batteryLevel,
                status,
                warningLevel
        );

        stationRepository.addMeasurement(station.getId(), measurement);
        return measurement;
    }

    public WarningLevel calculateWarningLevel(Station station, double waterLevel, StationStatus status) {
        if (status == StationStatus.OFFLINE) {
            return WarningLevel.UNKNOWN;
        }
        if (waterLevel >= station.getCriticalWaterLevel()) {
            return WarningLevel.CRITICAL;
        }
        if (waterLevel >= station.getWarningWaterLevel()) {
            return WarningLevel.WARNING;
        }
        return WarningLevel.NORMAL;
    }
}