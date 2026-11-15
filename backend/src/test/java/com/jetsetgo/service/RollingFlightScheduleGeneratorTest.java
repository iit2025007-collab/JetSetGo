package com.jetsetgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;
import com.jetsetgo.repository.RouteTemplateRepository;

class RollingFlightScheduleGeneratorTest {
    private final RollingFlightScheduleGenerator generator =
            new RollingFlightScheduleGenerator(org.mockito.Mockito.mock(RouteTemplateRepository.class),
                    org.mockito.Mockito.mock(MongoTemplate.class));

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
}
