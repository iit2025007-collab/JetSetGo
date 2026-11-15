package com.jetsetgo.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("flights")
@CompoundIndex(name = "flight_route_departure_idx",
        def = "{'originCode': 1, 'destinationCode': 1, 'departure': 1}")
@CompoundIndex(name = "flight_template_departure_idx", def = "{'routeTemplateId': 1, 'departure': 1}")
public record Flight(
        @Id String id,
        String airline,
        String flightNumber,
        String originCode,
        String originCity,
        String destinationCode,
        String destinationCity,
        Instant departure,
        Instant arrival,
        BigDecimal price,
        String currency,
        int capacity,
        String status,
        String dataSource,
        String originTimeZone,
        String destinationTimeZone,
        String aircraft,
        String originTerminal,
        String destinationTerminal,
        String routeTemplateId,
        Boolean templateDisabled) {

    public Flight(String id, String airline, String flightNumber, String originCode,
                  String originCity, String destinationCode, String destinationCity,
                  Instant departure, Instant arrival, BigDecimal price, String currency,
                  int capacity, String status, String dataSource) {
        this(id, airline, flightNumber, originCode, originCity, destinationCode, destinationCity,
                departure, arrival, price, currency, capacity, status, dataSource, null, null,
                null, null, null, null, false);
    }

    public Flight(String id, String airline, String flightNumber, String originCode,
                  String originCity, String destinationCode, String destinationCity,
                  Instant departure, Instant arrival, BigDecimal price, String currency,
                  int capacity, String status, String dataSource, String originTimeZone,
                  String destinationTimeZone) {
        this(id, airline, flightNumber, originCode, originCity, destinationCode, destinationCity,
                departure, arrival, price, currency, capacity, status, dataSource, originTimeZone,
                destinationTimeZone, null, null, null, null, false);
    }
}
