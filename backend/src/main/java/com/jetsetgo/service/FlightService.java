package com.jetsetgo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jetsetgo.domain.Flight;
import com.jetsetgo.repository.FlightRepository;

@Service
public class FlightService {
    private final FlightRepository flights;

    public FlightService(FlightRepository flights) {
        this.flights = flights;
    }

    public List<Flight> search(String origin, String destination, LocalDate date, String sort) {
        List<Flight> results;
        if (origin != null && !origin.isBlank() && destination != null && !destination.isBlank()) {
            results = flights.findByOriginCodeIgnoreCaseAndDestinationCodeIgnoreCase(origin.trim(), destination.trim());
        } else {
            results = flights.findAll();
        }
        Comparator<Flight> order = switch (sort == null ? "departure" : sort.toLowerCase()) {
            case "price" -> Comparator.comparing(Flight::price);
            case "duration" -> Comparator.comparing(flight ->
                    java.time.Duration.between(flight.departure(), flight.arrival()));
            default -> Comparator.comparing(Flight::departure);
        };
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Instant startOfDay = date == null ? null : date.atStartOfDay(zone).toInstant();
        Instant startOfNextDay = date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
        return results.stream()
                .filter(flight -> "SCHEDULED".equals(flight.status()))
                .filter(flight -> origin == null || origin.isBlank()
                        || flight.originCode().equalsIgnoreCase(origin.trim()))
                .filter(flight -> destination == null || destination.isBlank()
                        || flight.destinationCode().equalsIgnoreCase(destination.trim()))
                .filter(flight -> startOfDay == null || (!flight.departure().isBefore(startOfDay)
                        && flight.departure().isBefore(startOfNextDay)))
                .sorted(order)
                .toList();
    }

    public Flight get(String id) {
        return flights.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Flight not found"));
    }
}
