package com.jetsetgo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.AppUser;
import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.domain.SeatInventory;
import com.jetsetgo.repository.BookingRepository;
import com.jetsetgo.repository.FlightRepository;
import com.jetsetgo.repository.UserRepository;

@Service
public class AdminService {
    private static final List<String> FLIGHT_STATUSES =
            List.of("SCHEDULED", "DELAYED", "CANCELLED", "DEPARTED", "COMPLETED");
    private final FlightRepository flights;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final MongoTemplate mongo;

    public AdminService(FlightRepository flights, BookingRepository bookings,
                        UserRepository users, MongoTemplate mongo) {
        this.flights = flights;
        this.bookings = bookings;
        this.users = users;
        this.mongo = mongo;
    }

    public Summary summary() {
        List<Booking> allBookings = bookings.findAll();
        BigDecimal revenue = allBookings.stream()
                .filter(booking -> booking.status() == BookingStatus.CONFIRMED)
                .map(Booking::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long cancelled = allBookings.stream()
                .filter(booking -> booking.status() == BookingStatus.CANCELLED).count();
        return new Summary(flights.count(), allBookings.size(), users.count(), revenue, cancelled);
    }

    public List<Flight> flights() {
        return flights.findAll().stream()
                .sorted(java.util.Comparator.comparing(Flight::departure)).toList();
    }

    public Flight addFlight(NewFlight request) {
        if (request.originCode().equalsIgnoreCase(request.destinationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Origin and destination airports must be different");
        }
        if (!request.departure().isAfter(Instant.now()) || !request.arrival().isAfter(request.departure())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Flight arrival and departure times are invalid");
        }
        Flight flight = flights.save(new Flight(UUID.randomUUID().toString(),
                request.airline().trim(), request.flightNumber().trim().toUpperCase(),
                request.originCode().trim().toUpperCase(), request.originCity().trim(),
                request.destinationCode().trim().toUpperCase(), request.destinationCity().trim(),
                request.departure(), request.arrival(), request.price(), "INR",
                request.capacity(), "SCHEDULED", "DEMO"));
        List<SeatInventory> seatRows = new ArrayList<>();
        for (int seat = 0; seat < request.capacity(); seat++) {
            String number = (seat / 6 + 1) + String.valueOf((char) ('A' + seat % 6));
            seatRows.add(new SeatInventory(SeatInventory.idFor(flight.id(), number),
                    flight.id(), number, true));
        }
        mongo.insertAll(seatRows);
        return flight;
    }

    public Flight changeStatus(String flightId, String status) {
        String normalized = status == null ? "" : status.toUpperCase();
        if (!FLIGHT_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported flight status");
        }
        Flight flight = flights.findById(flightId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        Flight updated = new Flight(flight.id(), flight.airline(), flight.flightNumber(),
                flight.originCode(), flight.originCity(), flight.destinationCode(),
                flight.destinationCity(), flight.departure(), flight.arrival(), flight.price(),
                flight.currency(), flight.capacity(), normalized, flight.dataSource(),
                flight.originTimeZone(), flight.destinationTimeZone());
        return flights.save(updated);
    }

    public void deleteFlight(String flightId) {
        if (!flights.existsById(flightId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found");
        }
        if (bookings.existsByFlightId(flightId)
                || mongo.exists(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("tripDetails.returnFlightId")
                                .is(flightId)), Booking.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A flight with booking history cannot be deleted");
        }
        flights.deleteById(flightId);
        mongo.remove(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("flightId").is(flightId)),
                SeatInventory.class);
    }

    public List<Booking> bookings() {
        return bookings.findAll().stream()
                .sorted(java.util.Comparator.comparing(Booking::createdAt).reversed()).toList();
    }

    public List<UserSummary> users() {
        return users.findAll().stream().map(user ->
                new UserSummary(user.id(), user.name(), user.email(), user.role())).toList();
    }

    public record Summary(long totalFlights, long totalBookings, long registeredUsers,
                          BigDecimal confirmedRevenue, long cancelledBookings) {}
    public record NewFlight(String airline, String flightNumber, String originCode,
                            String originCity, String destinationCode, String destinationCity,
                            Instant departure, Instant arrival, BigDecimal price, int capacity) {}
    public record UserSummary(String id, String name, String email, AppUser.Role role) {}
}
