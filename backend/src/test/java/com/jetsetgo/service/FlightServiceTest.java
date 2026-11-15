package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.Airport;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.FlightRepository;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {
    @Mock FlightRepository repository;
    @Mock AirportRepository airportRepository;
    @InjectMocks FlightService service;

    @Test
    void appliesOneSidedAirportFiltersAndExcludesInactiveFlights() {
        Flight delToBom = flight("a", "DEL", "BOM", "SCHEDULED");
        Flight bomToBlr = flight("b", "BOM", "BLR", "SCHEDULED");
        Flight delayed = flight("c", "DEL", "BLR", "DELAYED");
        when(repository.findAll()).thenReturn(List.of(delToBom, bomToBlr, delayed));

        assertEquals(List.of(delToBom), service.search("DEL", null, null, null, null, null, null, null));
        assertEquals(List.of(bomToBlr), service.search(null, "BLR", null, null, null, null, null, null));
        assertEquals(List.of(delToBom, bomToBlr), service.search(null, null,
                java.time.LocalDate.of(2030, 1, 1), null, null, null, null, null));
    }

    @Test
    void domesticSearchRequiresBothAirportsToBeInSelectedCountry() {
        Flight delToBom = flight("a", "DEL", "BOM", "SCHEDULED");
        when(airportRepository.findById("DEL")).thenReturn(Optional.of(airport("DEL", "IN")));
        when(airportRepository.findById("BOM")).thenReturn(Optional.of(airport("BOM", "IN")));
        when(repository.findByOriginCodeIgnoreCaseAndDestinationCodeIgnoreCase("DEL", "BOM"))
                .thenReturn(List.of(delToBom));

        assertEquals(List.of(delToBom), service.search(
                "DEL", "BOM", null, "departure", "domestic", "IN", null, null));
        assertEquals(List.of(), service.search(
                "DEL", "BOM", null, "departure", "domestic", "US", null, null));
    }

    @Test
    void internationalSearchRejectsSameCountryRoutes() {
        when(airportRepository.findById("DEL")).thenReturn(Optional.of(airport("DEL", "IN")));
        when(airportRepository.findById("BOM")).thenReturn(Optional.of(airport("BOM", "IN")));

        assertEquals(List.of(), service.search(
                "DEL", "BOM", null, "departure", "international", null, null, null));
    }

    @Test
    void internationalSearchAllowsDifferentCountriesAndAppliesAirlineAndFareFilters() {
        Flight airIndia = new Flight("a", "Air India", "AI 1", "DEL", "Delhi", "DXB", "Dubai",
                Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T12:00:00Z"),
                new BigDecimal("1400"), "INR", 10, "SCHEDULED", "DEMO");
        Flight otherAirline = new Flight("b", "Other Air", "OA 1", "DEL", "Delhi", "DXB", "Dubai",
                Instant.parse("2030-01-01T11:00:00Z"), Instant.parse("2030-01-01T13:00:00Z"),
                new BigDecimal("900"), "INR", 10, "SCHEDULED", "DEMO");
        when(airportRepository.findById("DEL")).thenReturn(Optional.of(airport("DEL", "IN")));
        when(airportRepository.findById("DXB")).thenReturn(Optional.of(airport("DXB", "AE")));
        when(repository.findByOriginCodeIgnoreCaseAndDestinationCodeIgnoreCase("DEL", "DXB"))
                .thenReturn(List.of(airIndia, otherAirline));

        assertEquals(List.of(airIndia), service.search(
                "DEL", "DXB", null, "price", "international", null, "Air India", new BigDecimal("1500")));
    }

    private static Flight flight(String id, String origin, String destination, String status) {
        return new Flight(id, "Airline", id, origin, origin, destination, destination,
                Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T12:00:00Z"),
                new BigDecimal("1000"), "INR", 10, status, "DEMO");
    }

    private static Airport airport(String code, String countryCode) {
        return new Airport(code, code, code + " airport", "", countryCode, countryCode,
                "Asia/Kolkata", true, "large_airport", null, null, "TEST");
    }
}
