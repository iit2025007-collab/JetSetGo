package com.jetsetgo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
    private final BookingService bookingService;

    public AdminService(FlightRepository flights, BookingRepository bookings,
                        UserRepository users, MongoTemplate mongo,
                        BookingService bookingService) {
        this.flights = flights;
        this.bookings = bookings;
        this.users = users;
        this.mongo = mongo;
        this.bookingService = bookingService;
    }

    public Summary summary() {
        List<Booking> allBookings = bookings.findAll();
        BigDecimal revenue = allBookings.stream()
                .filter(booking -> booking.status() == BookingStatus.CONFIRMED)
                .map(Booking::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long cancelled = allBookings.stream()
                .filter(booking -> booking.status() == BookingStatus.CANCELLED).count();
        List<Flight> allFlights = flights.findAll();
        Instant now = Instant.now();
        long activeFlights = allFlights.stream().filter(flight ->
                List.of("SCHEDULED", "DELAYED").contains(flight.status())
                        && flight.departure().isAfter(now)).count();
        long upcomingDepartures = allFlights.stream().filter(flight ->
                flight.departure().isAfter(now) && flight.departure().isBefore(now.plusSeconds(86400))
                        && List.of("SCHEDULED", "DELAYED").contains(flight.status())).count();
        long confirmed = allBookings.stream().filter(booking ->
                booking.status() == BookingStatus.CONFIRMED).count();
        long reserved = allBookings.stream().filter(booking ->
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT,
                        BookingStatus.PAYMENT_PROCESSING).contains(booking.status()))
                .mapToLong(AdminService::seatCount).sum();
        long capacity = allFlights.stream().filter(flight -> flight.departure().isAfter(now))
                .mapToLong(Flight::capacity).sum();
        double occupancy = capacity == 0 ? 0 : Math.min(100.0, reserved * 100.0 / capacity);
        return new Summary(flights.count(), activeFlights, upcomingDepartures, allBookings.size(),
                confirmed, users.count(), revenue, cancelled, occupancy);
    }

    public List<Flight> flights(String origin, String destination, LocalDate date, String flightNumber) {
        return flights.findAll().stream()
                .filter(flight -> origin == null || origin.isBlank()
                        || flight.originCode().equalsIgnoreCase(origin.trim()))
                .filter(flight -> destination == null || destination.isBlank()
                        || flight.destinationCode().equalsIgnoreCase(destination.trim()))
                .filter(flight -> flightNumber == null || flightNumber.isBlank()
                        || flight.flightNumber().toLowerCase(Locale.ROOT)
                        .contains(flightNumber.trim().toLowerCase(Locale.ROOT)))
                .filter(flight -> date == null || isOnLocalDate(flight, date))
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
                request.capacity(), "SCHEDULED", "DEMO", request.originTimeZone(),
                request.destinationTimeZone(), request.aircraft(), request.originTerminal(),
                request.destinationTerminal(), null, false));
        return flight;
    }

    public Flight updateFlight(String flightId, NewFlight request) {
        Flight existing = flights.findById(flightId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        if (!existing.departure().isAfter(Instant.now()) || existing.routeTemplateId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Edit the recurring route template instead, or preserve this historical flight");
        }
        if (bookings.existsByFlightId(flightId)
                || mongo.exists(Query.query(Criteria.where("tripDetails.returnFlightId").is(flightId)), Booking.class)
                || mongo.exists(Query.query(Criteria.where("flightId").is(flightId)
                        .and("available").is(false)), SeatInventory.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A flight with booking history cannot be edited");
        }
        validateTimes(request.departure(), request.arrival());
        if (request.originCode().equalsIgnoreCase(request.destinationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Origin and destination airports must be different");
        }
        mongo.remove(Query.query(Criteria.where("flightId").is(flightId)), SeatInventory.class);
        Flight updated = new Flight(existing.id(), request.airline().trim(),
                request.flightNumber().trim().toUpperCase(), request.originCode().trim().toUpperCase(),
                request.originCity().trim(), request.destinationCode().trim().toUpperCase(),
                request.destinationCity().trim(), request.departure(), request.arrival(), request.price(),
                "INR", request.capacity(), existing.status(), "DEMO", request.originTimeZone(),
                request.destinationTimeZone(), request.aircraft(), request.originTerminal(),
                request.destinationTerminal(), null, false);
        return flights.save(updated);
    }

    public Flight changeStatus(String flightId, String status) {
        String normalized = status == null ? "" : status.toUpperCase();
        if (!FLIGHT_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported flight status");
        }
        Flight flight = flights.findById(flightId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        boolean validTransition = switch (flight.status()) {
            case "SCHEDULED" -> List.of("DELAYED", "CANCELLED", "DEPARTED").contains(normalized);
            case "DELAYED" -> List.of("SCHEDULED", "CANCELLED", "DEPARTED").contains(normalized);
            case "DEPARTED" -> "COMPLETED".equals(normalized);
            default -> false;
        };
        if (!validTransition) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Flight status cannot transition from " + flight.status() + " to " + normalized);
        }
        if (("DEPARTED".equals(normalized) && flight.departure().isAfter(Instant.now()))
                || ("COMPLETED".equals(normalized) && flight.arrival().isAfter(Instant.now()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A flight cannot be marked departed or completed before its scheduled time");
        }
        Flight updated = new Flight(flight.id(), flight.airline(), flight.flightNumber(),
                flight.originCode(), flight.originCity(), flight.destinationCode(),
                flight.destinationCity(), flight.departure(), flight.arrival(), flight.price(),
                flight.currency(), flight.capacity(), normalized, flight.dataSource(),
                flight.originTimeZone(), flight.destinationTimeZone(), flight.aircraft(),
                flight.originTerminal(), flight.destinationTerminal(), flight.routeTemplateId(),
                flight.templateDisabled());
        return flights.save(updated);
    }

    public void deleteFlight(String flightId) {
        Flight flight = flights.findById(flightId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        if (flight.routeTemplateId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Disable the route template instead of deleting a generated flight");
        }
        if (bookings.existsByFlightId(flightId)
                || mongo.exists(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("tripDetails.returnFlightId")
                                .is(flightId)), Booking.class)
                || mongo.exists(Query.query(Criteria.where("flightId").is(flightId)
                        .and("available").is(false)), SeatInventory.class)) {
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

    public List<Booking> bookings(String status) {
        return bookings.findAll().stream()
                .filter(booking -> status == null || status.isBlank()
                        || booking.status().name().equalsIgnoreCase(status.trim()))
                .sorted(java.util.Comparator.comparing(Booking::createdAt).reversed()).toList();
    }

    public Booking cancelBooking(String bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return bookingService.cancel(booking.userId(), bookingId);
    }

    public List<UserSummary> users() {
        return users.findAll().stream().map(user ->
                new UserSummary(user.id(), user.name(), user.email(), user.role())).toList();
    }

    private static long seatCount(Booking booking) {
        if (booking.tripDetails() == null || booking.tripDetails().seatNumbers().isEmpty()) {
            return 1;
        }
        return booking.tripDetails().seatNumbers().size() + booking.tripDetails().returnSeatNumbers().size();
    }

    private static boolean isOnLocalDate(Flight flight, LocalDate date) {
        try {
            ZoneId zone = flight.originTimeZone() == null || flight.originTimeZone().isBlank()
                    ? ZoneId.of("Asia/Kolkata") : ZoneId.of(flight.originTimeZone());
            return flight.departure().atZone(zone).toLocalDate().equals(date);
        } catch (java.time.DateTimeException invalidZone) {
            return false;
        }
    }

    private static void validateTimes(Instant departure, Instant arrival) {
        if (departure == null || arrival == null || !departure.isAfter(Instant.now())
                || !arrival.isAfter(departure)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Flight arrival and departure times are invalid");
        }
    }

    public record Summary(long totalFlights, long activeFlights, long upcomingDepartures,
                          long totalBookings, long confirmedBookings, long registeredUsers,
                          BigDecimal confirmedRevenue, long cancelledBookings, double seatOccupancy) {}
    public record NewFlight(String airline, String flightNumber, String originCode,
                            String originCity, String destinationCode, String destinationCity,
                            Instant departure, Instant arrival, BigDecimal price, int capacity,
                            String originTimeZone, String destinationTimeZone, String aircraft,
                            String originTerminal, String destinationTerminal) {}
    public record UserSummary(String id, String name, String email, AppUser.Role role) {}
}
