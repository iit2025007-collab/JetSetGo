package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.RouteTemplate;
import com.jetsetgo.repository.RouteTemplateRepository;

class RollingFlightScheduleGeneratorTest {
    private final RouteTemplateRepository templates = org.mockito.Mockito.mock(RouteTemplateRepository.class);
    private final MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
    private final BulkOperations writes = org.mockito.Mockito.mock(BulkOperations.class);
    private final RollingFlightScheduleGenerator generator =
            new RollingFlightScheduleGenerator(templates, mongo);

    @BeforeEach
    void setUpBulkOperations() {
        org.mockito.Mockito.when(mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Flight.class)).thenReturn(writes);
        org.mockito.Mockito.when(writes.upsert(org.mockito.ArgumentMatchers.any(Query.class),
                org.mockito.ArgumentMatchers.any(Update.class))).thenReturn(writes);
    }

    @Test
    void createsStableOvernightInstanceUsingBothIanaTimezones() {
        RouteTemplate route = new RouteTemplate("route-del-sin", "Demo Air", "JG 1",
                "DEL", "Delhi", "SIN", "Singapore", "23:30", 250, List.of(1, 2, 3, 4, 5, 6, 7),
                "Asia/Kolkata", "Asia/Singapore", new BigDecimal("1000"), "INR", 48,
                "Illustrative aircraft", true);
        LocalDate departureDate = LocalDate.of(2030, 1, 2);

        Flight first = generator.createInstance(route, departureDate);
        Flight second = generator.createInstance(route, departureDate);

        assertEquals("route-del-sin:2030-01-02", first.id());
        assertEquals(first.id(), second.id());
        assertEquals("2030-01-03T06:10", first.arrival().atZone(java.time.ZoneId.of("Asia/Singapore"))
                .toLocalDateTime().toString());
        assertEquals("2030-01-02T23:30", first.departure().atZone(java.time.ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime().toString());
    }

    @Test
    void createsThreeDailyDemoFlightsForAnUnseededScheduledRoute() {
        Airport amd = new Airport("AMD", "Ahmedabad", "Ahmedabad Airport", "Gujarat",
                "India", "IN", null, true, "large_airport", 23.0772, 72.6347, "TEST");
        Airport hyd = new Airport("HYD", "Hyderabad", "Hyderabad Airport", "Telangana",
                "India", "IN", null, true, "large_airport", 17.2313, 78.4299, "TEST");
        LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(2);

        List<Flight> generated = generator.ensureDemoRouteInstances(amd, hyd, date);

        assertEquals(3, generated.size());
        assertEquals("AMD", generated.getFirst().originCode());
        assertEquals("HYD", generated.getFirst().destinationCode());
        assertTrue(generated.stream().allMatch(flight -> flight.dataSource().equals("DEMO")));
        assertTrue(generated.stream().allMatch(flight -> flight.status().equals("SCHEDULED")));
        assertTrue(generated.stream().allMatch(flight -> flight.departure().isBefore(flight.arrival())));
        assertTrue(generated.stream().allMatch(flight -> flight.originTimeZone().equals("Asia/Kolkata")));
        org.mockito.Mockito.verify(writes, org.mockito.Mockito.times(3))
                .upsert(org.mockito.ArgumentMatchers.any(Query.class), org.mockito.ArgumentMatchers.any(Update.class));
        org.mockito.Mockito.verify(writes).execute();
    }

    @Test
    void doesNotGenerateDemoFlightsFromAirportsWithoutScheduledPassengerService() {
        Airport origin = new Airport("ABC", "Example", "Example Airport", "Region",
                "Exampleland", "XX", null, false, "small_airport", 0.0, 0.0, "TEST");
        Airport destination = new Airport("DEF", "Sample", "Sample Airport", "Region",
                "Exampleland", "XX", null, true, "medium_airport", 1.0, 1.0, "TEST");

        assertEquals(List.of(), generator.ensureDemoRouteInstances(
                origin, destination, LocalDate.now().plusDays(1)));
    }
}
