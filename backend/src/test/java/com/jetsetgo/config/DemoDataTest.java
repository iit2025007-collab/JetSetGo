package com.jetsetgo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jetsetgo.domain.Airport;
import com.jetsetgo.repository.AirportRepository;

@ExtendWith(MockitoExtension.class)
class DemoDataTest {
    @Mock AirportRepository airports;

    @Test
    void seedsMissingAirportsAndRepairsIncompleteRecordsWithoutReplacingImportedOnes() {
        Airport incompleteDelhi = new Airport("DEL", "Delhi", "Indira Gandhi International Airport",
                "", "India", "", "Asia/Kolkata", true, "large_airport", null, null, null);
        Airport importedMumbai = new Airport("BOM", "Mumbai", "Chhatrapati Shivaji Maharaj International Airport",
                "Maharashtra", "India", "IN", null, true, "large_airport", null, null, "OURAIRPORTS");
        when(airports.findAllById(any())).thenReturn(List.of(incompleteDelhi, importedMumbai));

        List<Airport> seeded = DemoData.seedCuratedAirports(airports);

        Airport repairedDelhi = seeded.stream()
                .filter(airport -> airport.code().equals("DEL"))
                .findFirst()
                .orElseThrow();
        assertEquals("IN", repairedDelhi.countryCode());
        assertEquals("CURATED", repairedDelhi.dataSource());
        assertTrue(seeded.stream().anyMatch(airport -> airport.code().equals("LKO")));
        assertFalse(seeded.stream().anyMatch(airport -> airport.code().equals("BOM")));
        verify(airports).saveAll(seeded);
    }
}
