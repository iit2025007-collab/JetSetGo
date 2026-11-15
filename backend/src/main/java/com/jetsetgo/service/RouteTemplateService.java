package com.jetsetgo.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.RouteTemplate;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.BookingRepository;
import com.jetsetgo.repository.RouteTemplateRepository;

@Service
public class RouteTemplateService {
    private final RouteTemplateRepository templates;
    private final AirportRepository airports;
    private final BookingRepository bookings;
    private final FlightScheduleGenerator schedules;

    public RouteTemplateService(RouteTemplateRepository templates, AirportRepository airports,
                                BookingRepository bookings, FlightScheduleGenerator schedules) {
        this.templates = templates;
        this.airports = airports;
        this.bookings = bookings;
        this.schedules = schedules;
    }

    public List<RouteTemplate> list() {
        return templates.findAll();
    }

    public RouteTemplate create(TemplateInput input) {
        RouteTemplate template = toTemplate("admin-route-" + java.util.UUID.randomUUID(), input, true);
        RouteTemplate saved = templates.save(template);
        schedules.generateRollingWindow(LocalDate.now(), RollingFlightScheduleGenerator.WINDOW_DAYS);
        return saved;
    }

    public RouteTemplate update(String id, TemplateInput input) {
        RouteTemplate current = find(id);
        RouteTemplate updated = toTemplate(id, input, current.active());
        RouteTemplate saved = templates.save(updated);
        schedules.updateFutureInstances(saved, protectedFlightIds());
        schedules.generateRollingWindow(LocalDate.now(), RollingFlightScheduleGenerator.WINDOW_DAYS);
        return saved;
    }

    public RouteTemplate setActive(String id, boolean active) {
        RouteTemplate updated = find(id).withActive(active);
        RouteTemplate saved = templates.save(updated);
        Set<String> protectedIds = protectedFlightIds();
        schedules.setTemplateEnabled(id, active, protectedIds);
        if (active) {
            schedules.generateRollingWindow(LocalDate.now(), RollingFlightScheduleGenerator.WINDOW_DAYS);
        }
        return saved;
    }

    private RouteTemplate find(String id) {
        return templates.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Route template not found"));
    }

    private Set<String> protectedFlightIds() {
        Set<String> protectedIds = new HashSet<>();
        for (Booking booking : bookings.findAll()) {
            protectedIds.add(booking.flightId());
            if (booking.tripDetails() != null && booking.tripDetails().returnFlightId() != null) {
                protectedIds.add(booking.tripDetails().returnFlightId());
            }
        }
        return protectedIds;
    }

    private RouteTemplate toTemplate(String id, TemplateInput input, boolean active) {
        if (input == null || input.originCode() == null || input.destinationCode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route details are required");
        }
        String originCode = input.originCode().trim().toUpperCase(Locale.ROOT);
        String destinationCode = input.destinationCode().trim().toUpperCase(Locale.ROOT);
        if (originCode.equals(destinationCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Origin and destination airports must be different");
        }
        Airport origin = airports.findById(originCode).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origin airport is not in the airport catalog"));
        Airport destination = airports.findById(destinationCode).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination airport is not in the airport catalog"));
        List<Integer> days = input.operatingDays() == null ? List.of() : input.operatingDays().stream()
                .distinct().sorted().toList();
        if (days.isEmpty() || days.stream().anyMatch(day -> day < 1 || day > 7)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose one or more valid operating days (1=Monday through 7=Sunday)");
        }
        if (input.airline() == null || input.airline().isBlank()
                || input.flightNumber() == null || input.flightNumber().isBlank()
                || input.originCity() == null || input.originCity().isBlank()
                || input.destinationCity() == null || input.destinationCity().isBlank()
                || input.capacity() < 6 || input.capacity() > 360
                || input.durationMinutes() < 20 || input.durationMinutes() > 1800
                || input.baseFare() == null || input.baseFare().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route template values are invalid");
        }
        try {
            LocalTime.parse(input.departureLocalTime());
            ZoneId.of(input.originTimeZone());
            ZoneId.of(input.destinationTimeZone());
        } catch (DateTimeException | NullPointerException invalidTime) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use a valid local departure time and IANA timezones");
        }
        String currency = input.currency() == null || input.currency().isBlank()
                ? "INR" : input.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must be a three-letter code");
        }
        return new RouteTemplate(id, input.airline().trim(), input.flightNumber().trim().toUpperCase(Locale.ROOT),
                originCode, input.originCity().trim(), destinationCode, input.destinationCity().trim(),
                input.departureLocalTime(), input.durationMinutes(), days, input.originTimeZone(),
                input.destinationTimeZone(), input.baseFare(), currency, input.capacity(),
                input.aircraft() == null || input.aircraft().isBlank() ? null : input.aircraft().trim(), active);
    }

    public record TemplateInput(String airline, String flightNumber,
                                String originCode, String originCity,
                                String destinationCode, String destinationCity,
                                String departureLocalTime, int durationMinutes,
                                List<Integer> operatingDays, String originTimeZone,
                                String destinationTimeZone, java.math.BigDecimal baseFare,
                                String currency, int capacity, String aircraft) {
    }
}
