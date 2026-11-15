package com.jetsetgo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.FlightRepository;

@Service
public class FlightService {
    private final FlightRepository flights;
    private final AirportRepository airports;

    public FlightService(FlightRepository flights, AirportRepository airports) {
        this.flights = flights;
        this.airports = airports;
    }

    public List<Flight> search(String origin, String destination, LocalDate date, String sort,
                               String mode, String countryCode, String airline, BigDecimal maxPrice) {
        String normalizedMode = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!normalizedMode.isBlank() && !normalizedMode.equals("domestic")
                && !normalizedMode.equals("international")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mode must be domestic or international");
        }
        Airport originAirport = airport(origin);
        Airport destinationAirport = airport(destination);
        if ("domestic".equals(normalizedMode)) {
            if (countryCode == null || countryCode.isBlank()
                    || originAirport == null || destinationAirport == null
                    || !countryCode.equalsIgnoreCase(originAirport.countryCode())
                    || !countryCode.equalsIgnoreCase(destinationAirport.countryCode())) {
                return List.of();
            }
        }
        if ("international".equals(normalizedMode)) {
            if (originAirport == null || destinationAirport == null
                    || originAirport.countryCode() == null || destinationAirport.countryCode() == null
                    || originAirport.countryCode().isBlank() || destinationAirport.countryCode().isBlank()
                    || originAirport.countryCode().equalsIgnoreCase(destinationAirport.countryCode())) {
                return List.of();
            }
        }
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
        ZoneId zone = timeZone(originAirport, results);
        Instant startOfDay = date == null ? null : date.atStartOfDay(zone).toInstant();
        Instant startOfNextDay = date == null ? null : date.plusDays(1).atStartOfDay(zone).toInstant();
        return results.stream()
                .filter(flight -> "SCHEDULED".equals(flight.status()))
                .filter(flight -> origin == null || origin.isBlank()
                        || flight.originCode().equalsIgnoreCase(origin.trim()))
                .filter(flight -> destination == null || destination.isBlank()
                        || flight.destinationCode().equalsIgnoreCase(destination.trim()))
                .filter(flight -> airline == null || airline.isBlank()
                        || flight.airline().equalsIgnoreCase(airline.trim()))
                .filter(flight -> maxPrice == null || flight.price().compareTo(maxPrice) <= 0)
                .filter(flight -> startOfDay == null || (!flight.departure().isBefore(startOfDay)
                        && flight.departure().isBefore(startOfNextDay)))
                .sorted(order)
                .toList();
    }

    private Airport airport(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return airports.findById(code.trim().toUpperCase(Locale.ROOT)).orElse(null);
    }

    private static ZoneId timeZone(Airport airport, List<Flight> flights) {
        if (airport != null && airport.timeZone() != null && !airport.timeZone().isBlank()) {
            try {
                return ZoneId.of(airport.timeZone());
            } catch (DateTimeException ignored) {
                // Fall through to the local demo schedule's default.
            }
        }
        if (!flights.isEmpty() && flights.getFirst().originTimeZone() != null
                && !flights.getFirst().originTimeZone().isBlank()) {
            try {
                return ZoneId.of(flights.getFirst().originTimeZone());
            } catch (DateTimeException ignored) {
                // Fall through to the local demo schedule's default.
            }
        }
        return ZoneId.of("Asia/Kolkata");
    }

    public Flight get(String id) {
        return flights.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Flight not found"));
    }
}
