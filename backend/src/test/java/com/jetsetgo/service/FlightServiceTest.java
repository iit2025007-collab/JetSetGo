package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.data.domain.Pageable;
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
    @Mock FlightScheduleGenerator schedules;
    @InjectMocks FlightService service;

    @Test
    void appliesOneSidedAirportFiltersAndIncludesDelayedCustomerStatus() {
        Flight delToBom = flight("a", "DEL", "BOM", "SCHEDULED");
        Flight bomToBlr = flight("b", "BOM", "BLR", "SCHEDULED");
        Flight delayed = flight("c", "DEL", "BLR", "DELAYED");
        when(repository.findAll()).thenReturn(List.of(delToBom, bomToBlr, delayed));

        assertEquals(List.of(delToBom, delayed), service.search("DEL", null, null, null, null, null, null, null));
        assertEquals(List.of(bomToBlr, delayed), service.search(null, "BLR", null, null, null, null, null, null));
        assertEquals(List.of(delToBom, bomToBlr, delayed), service.search(null, null,
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

    @Test
    void generalFlightBrowseIsBoundedToUpcomingResults() {
        List<Flight> upcoming = IntStream.range(0, 45)
                .mapToObj(index -> flight("browse-" + index, "DEL", "BOM", "SCHEDULED"))
                .toList();
        when(repository.findUpcomingForBrowsing(any(), any(), any(Pageable.class))).thenReturn(upcoming);

        List<Flight> results = service.search(null, null, null, "departure", null, null, null, null);

        assertEquals(40, results.size());
        assertTrue(results.stream().allMatch(flight -> flight.dataSource().equals("DEMO")));
    }

    @Test
    void filteredGeneralBrowseDoesNotDiscardFlightsOutsideTheUnfilteredPage() {
        Flight match = flight("filtered", "DEL", "BOM", "SCHEDULED");
        when(repository.findAll()).thenReturn(List.of(match));

        assertEquals(List.of(match), service.search(null, null, null, "departure", null, null, "Airline", null));
    }

    @Test
    void createsAndPersistsDemoFlightsForAnUnseededRouteAndDate() {
        Airport amd = airport("AMD", "IN");
        Airport hyd = airport("HYD", "IN");
        java.time.LocalDate date = java.time.LocalDate.now().plusDays(2);
        Flight generated = new Flight("demo-route-AMD-HYD-" + date + "-1",
                "JetSetGo Demonstration Air", "JG 1001", "AMD", "Ahmedabad", "HYD", "Hyderabad",
                date.atTime(8, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant(),
                date.atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant(),
                new BigDecimal("5000"), "INR", 48, "SCHEDULED", "DEMO",
                "Asia/Kolkata", "Asia/Kolkata", "Illustrative demo aircraft", null, null, null, false);
        when(airportRepository.findById("AMD")).thenReturn(Optional.of(amd));
        when(airportRepository.findById("HYD")).thenReturn(Optional.of(hyd));
        when(repository.findByOriginCodeIgnoreCaseAndDestinationCodeIgnoreCase("AMD", "HYD"))
                .thenReturn(List.of());
        when(schedules.ensureDemoRouteInstances(amd, hyd, date)).thenReturn(List.of(generated));

        List<Flight> results = service.search("AMD", "HYD", date, "departure", "domestic", "IN", null, null);

        assertEquals(List.of(generated), results);
        org.mockito.Mockito.verify(schedules).ensureDemoRouteInstances(amd, hyd, date);
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
