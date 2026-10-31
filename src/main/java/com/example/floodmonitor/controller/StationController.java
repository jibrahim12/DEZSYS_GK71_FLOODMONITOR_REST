package com.example.floodmonitor.controller;

import com.example.floodmonitor.model.*;
import com.example.floodmonitor.service.StationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping(value = "/api/v1", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
@CrossOrigin(origins = "*") // Erlaubt spaeter den Zugriff vom Webclient
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    /**
     * Ruft alle Stationen ab mit optionalen Filtern (river, isActive, warningLevel).
     */
    @GetMapping("/stations")
    public ResponseEntity<StationListWrapper> getAllStations(
            @RequestParam(required = false) String river,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) WarningLevel warningLevel) {
        List<Station> stations = stationService.getStations(river, isActive, warningLevel);
        return ResponseEntity.ok(new StationListWrapper(stations));
    }

    /**
     * Ruft eine einzelne Station anhand ihrer ID ab.
     */
    @GetMapping("/stations/{stationId}")
    public ResponseEntity<Station> getStationById(@PathVariable String stationId) {
        return stationService.getStationById(stationId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("Station " + stationId + " wurde nicht gefunden"));
    }

    /**
     * Ruft den aktuellsten Messwert einer Station ab.
     */
    @GetMapping("/stations/{stationId}/measurements/latest")
    public ResponseEntity<Measurement> getLatestMeasurement(@PathVariable String stationId) {
        stationService.getStationById(stationId)
                .orElseThrow(() -> new NoSuchElementException("Station " + stationId + " wurde nicht gefunden"));

        return stationService.getLatestMeasurement(stationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Ruft historische Messwerte ab mit optionalen Filtern (from, to, limit, warningLevel).
     */
    @GetMapping("/stations/{stationId}/measurements")
    public ResponseEntity<MeasurementListWrapper> getMeasurements(
            @PathVariable String stationId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) WarningLevel warningLevel) {

        stationService.getStationById(stationId)
                .orElseThrow(() -> new NoSuchElementException("Station " + stationId + " wurde nicht gefunden"));

        Instant fromInstant = null;
        Instant toInstant = null;

        try {
            if (from != null) {
                fromInstant = Instant.parse(from);
            }
            if (to != null) {
                toInstant = Instant.parse(to);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ungueltiges Datumsformat. Bitte ISO-8601 UTC verwenden (z. B. 2026-05-01T10:00:00Z)");
        }

        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new IllegalArgumentException("Parameter 'from' darf zeitlich nicht nach 'to' liegen");
        }

        if (limit != null && (limit <= 0 || limit > 1000)) {
            throw new IllegalArgumentException("Parameter 'limit' muss zwischen 1 und 1000 liegen");
        }

        List<Measurement> measurements = stationService.getMeasurements(stationId, fromInstant, toInstant, limit, warningLevel);
        return ResponseEntity.ok(new MeasurementListWrapper(measurements));
    }

    /**
     * Ruft statistische Auswertungen fuer eine Station ab.
     */
    @GetMapping("/stations/{stationId}/stats")
    public ResponseEntity<StationStats> getStationStats(@PathVariable String stationId) {
        stationService.getStationById(stationId)
                .orElseThrow(() -> new NoSuchElementException("Station " + stationId + " wurde nicht gefunden"));

        return ResponseEntity.ok(stationService.calculateStats(stationId));
    }

    /**
     * Ruft aktive Warnmeldungen (WARNING und CRITICAL) ab.
     */
    @GetMapping("/alerts")
    public ResponseEntity<AlertListWrapper> getAlerts(
            @RequestParam(required = false) WarningLevel warningLevel,
            @RequestParam(required = false) String river) {
        return ResponseEntity.ok(new AlertListWrapper(stationService.getAlerts(warningLevel, river)));
    }
}