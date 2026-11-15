package com.jetsetgo.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("flights")
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
        String destinationTimeZone) {

    public Flight(String id, String airline, String flightNumber, String originCode,
                  String originCity, String destinationCode, String destinationCity,
                  Instant departure, Instant arrival, BigDecimal price, String currency,
                  int capacity, String status, String dataSource) {
        this(id, airline, flightNumber, originCode, originCity, destinationCode, destinationCity,
                departure, arrival, price, currency, capacity, status, dataSource, null, null);
    }
}
