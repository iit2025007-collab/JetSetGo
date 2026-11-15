package com.jetsetgo.domain;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("route_templates")
public record RouteTemplate(
        @Id String id,
        String airline,
        String flightNumber,
        String originCode,
        String originCity,
        String destinationCode,
        String destinationCity,
        String departureLocalTime,
        int durationMinutes,
        List<Integer> operatingDays,
        String originTimeZone,
        String destinationTimeZone,
        BigDecimal baseFare,
        String currency,
        int capacity,
        String aircraft,
        boolean active) {
    public RouteTemplate {
        operatingDays = operatingDays == null ? List.of() : List.copyOf(operatingDays);
    }

    public RouteTemplate withActive(boolean value) {
        return new RouteTemplate(id, airline, flightNumber, originCode, originCity, destinationCode,
                destinationCity, departureLocalTime, durationMinutes, operatingDays, originTimeZone,
                destinationTimeZone, baseFare, currency, capacity, aircraft, value);
    }
}
