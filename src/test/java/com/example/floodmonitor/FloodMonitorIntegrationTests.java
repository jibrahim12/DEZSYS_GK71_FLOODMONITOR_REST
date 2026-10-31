package com.example.floodmonitor;

import com.example.floodmonitor.model.*;
import com.example.floodmonitor.repository.StationRepository;
import com.example.floodmonitor.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.example.floodmonitor.FloodMonitorApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "simulation.enabled=false") // Tests deterministisch halten!
class FloodMonitorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        // Sicherstellen, dass ST-001 Messdaten für Tests enthält
        if (stationRepository.findMeasurementsByStationId("ST-001").isEmpty()) {
            Station s = stationRepository.findStationById("ST-001").orElseThrow();
            simulationService.simulateMeasurementForStation(s);
        }
    }

    // 1. Abruf aller Stationen als JSON
    @Test
    @DisplayName("1. Abruf aller Stationen als JSON")
    void testGetAllStationsAsJson() throws Exception {
        mockMvc.perform(get("/api/v1/stations")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stations", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.stations[0].id").exists());
    }

    // 2. Abruf aller Stationen als XML
    @Test
    @DisplayName("2. Abruf aller Stationen als XML")
    void testGetAllStationsAsXml() throws Exception {
        mockMvc.perform(get("/api/v1/stations")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/stations/station").nodeCount(greaterThanOrEqualTo(3)));
    }

    // 3. Abruf einer existierenden Station
    @Test
    @DisplayName("3. Abruf einer existierenden Station")
    void testGetExistingStation() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ST-001"))
                .andExpect(jsonPath("$.name").value("Wien Reichsbrücke"));
    }

    // 4. Abruf einer unbekannten Station (404)
    @Test
    @DisplayName("4. Abruf einer unbekannten Station liefert 404")
    void testGetUnknownStation() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // 5. Abruf des aktuellsten Messwerts
    @Test
    @DisplayName("5. Abruf des aktuellsten Messwerts")
    void testGetLatestMeasurement() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-001/measurements/latest")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value("ST-001"))
                .andExpect(jsonPath("$.waterLevel").isNumber());
    }

    // 6. Filterung nach Zeitraum
    @Test
    @DisplayName("6. Filterung nach Zeitraum")
    void testFilterByTimeRange() throws Exception {
        Instant now = Instant.now();
        Instant past = now.minus(2, ChronoUnit.HOURS);
        Instant future = now.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", past.toString())
                        .param("to", future.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements").isArray());
    }

    // 7. Ungültiger Zeitraum (from nach to liefert 400)
    @Test
    @DisplayName("7. Ungueltiger Zeitraum (from nach to) liefert 400 Bad Request")
    void testInvalidTimeRange() throws Exception {
        Instant now = Instant.now();
        Instant later = now.plus(1, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", later.toString())
                        .param("to", now.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // 8. Warnstufe NORMAL berechnen
    @Test
    @DisplayName("8. Warnstufe NORMAL berechnen")
    void testCalculateWarningLevelNormal() {
        Station station = new Station("ST-TEST", "Test", "Testfluss", null, 200, 400, 600, true);
        WarningLevel level = simulationService.calculateWarningLevel(station, 250.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.NORMAL, level);
    }

    // 9. Warnstufe WARNING berechnen
    @Test
    @DisplayName("9. Warnstufe WARNING berechnen")
    void testCalculateWarningLevelWarning() {
        Station station = new Station("ST-TEST", "Test", "Testfluss", null, 200, 400, 600, true);
        WarningLevel level = simulationService.calculateWarningLevel(station, 450.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.WARNING, level);
    }

    // 10. Warnstufe CRITICAL berechnen
    @Test
    @DisplayName("10. Warnstufe CRITICAL berechnen")
    void testCalculateWarningLevelCritical() {
        Station station = new Station("ST-TEST", "Test", "Testfluss", null, 200, 400, 600, true);
        WarningLevel level = simulationService.calculateWarningLevel(station, 650.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.CRITICAL, level);
    }

    // 11. Nicht unterstütztes Format liefert 406 Not Acceptable
    @Test
    @DisplayName("11. Nicht unterstuetztes Format liefert 406 Not Acceptable")
    void testUnsupportedFormatReturns406() throws Exception {
        mockMvc.perform(get("/api/v1/stations")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotAcceptable());
    }

    // 12. Maximales Limit Validierung (negativ oder > 1000 liefert 400)
    @Test
    @DisplayName("12. Maximales Limit Validierung liefert 400")
    void testLimitValidation() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("limit", "1500")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}