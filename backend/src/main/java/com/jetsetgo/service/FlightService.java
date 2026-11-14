package com.jetsetgo.service;

import java.time.Instant;
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

    public List<Flight> search(String origin, String destination, Instant departureAfter) {
        List<Flight> results;
        if (origin != null && !origin.isBlank() && destination != null && !destination.isBlank()) {
            results = flights.findByOriginCodeIgnoreCaseAndDestinationCodeIgnoreCase(origin.trim(), destination.trim());
        } else {
            results = flights.findAll();
        }
        return results.stream()
                .filter(flight -> "SCHEDULED".equals(flight.status()))
                .filter(flight -> origin == null || origin.isBlank()
                        || flight.originCode().equalsIgnoreCase(origin.trim()))
                .filter(flight -> destination == null || destination.isBlank()
                        || flight.destinationCode().equalsIgnoreCase(destination.trim()))
                .filter(flight -> departureAfter == null || !flight.departure().isBefore(departureAfter))
                .sorted(Comparator.comparing(Flight::departure))
                .toList();
    }

    public Flight get(String id) {
        return flights.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Flight not found"));
    }
}
