package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.repository.FlightRepository;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {
    @Mock FlightRepository repository;
    @InjectMocks FlightService service;

    @Test
    void appliesOneSidedAirportFiltersAndExcludesInactiveFlights() {
        Flight delToBom = flight("a", "DEL", "BOM", "SCHEDULED");
        Flight bomToBlr = flight("b", "BOM", "BLR", "SCHEDULED");
        Flight delayed = flight("c", "DEL", "BLR", "DELAYED");
        when(repository.findAll()).thenReturn(List.of(delToBom, bomToBlr, delayed));

        assertEquals(List.of(delToBom), service.search("DEL", null, null));
        assertEquals(List.of(bomToBlr), service.search(null, "BLR", null));
    }

    private static Flight flight(String id, String origin, String destination, String status) {
        return new Flight(id, "Airline", id, origin, origin, destination, destination,
                Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T12:00:00Z"),
                new BigDecimal("1000"), "INR", 10, status);
    }
}
