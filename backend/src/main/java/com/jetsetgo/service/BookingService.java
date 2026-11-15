package com.jetsetgo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jetsetgo.domain.Booking;
import com.jetsetgo.domain.Booking.Passenger;
import com.jetsetgo.domain.Booking.TripDetails;
import com.jetsetgo.domain.BookingStatus;
import com.jetsetgo.domain.Airport;
import com.jetsetgo.domain.Flight;
import com.jetsetgo.repository.BookingRepository;
import com.jetsetgo.repository.AirportRepository;
import com.jetsetgo.repository.FlightRepository;
import com.jetsetgo.service.fare.FarePolicyResolver;

@Service
public class BookingService {
    private static final int MAX_PASSENGERS = 6;
    private static final Pattern SEAT_NUMBER = Pattern.compile("[1-9][0-9]?[A-F]");
    private final BookingRepository bookings;
    private final FlightRepository flights;
    private final AirportRepository airports;
    private final MongoTemplate mongo;
    private final PaymentProcessor payments;
    private final SeatInventoryService seatInventory;
    private final FarePolicyResolver farePolicies;

    public BookingService(BookingRepository bookings, FlightRepository flights, AirportRepository airports,
                         MongoTemplate mongo, PaymentProcessor payments,
                         SeatInventoryService seatInventory, FarePolicyResolver farePolicies) {
        this.bookings = bookings;
        this.flights = flights;
        this.airports = airports;
        this.mongo = mongo;
        this.payments = payments;
        this.seatInventory = seatInventory;
        this.farePolicies = farePolicies;
    }

    public Booking create(String userId, String idempotencyKey, CreateBooking request) {
        return bookings.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseGet(() -> reserveAndCreate(userId, idempotencyKey, request));
    }

    private Booking reserveAndCreate(String userId, String idempotencyKey, CreateBooking request) {
        Flight flight = flights.findById(request.flightId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        ensureBookable(flight);
        Flight returnFlight = null;
        if (request.returnFlightId() != null && !request.returnFlightId().isBlank()) {
            if (request.returnFlightId().equals(flight.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return flight must be different");
            }
            returnFlight = flights.findById(request.returnFlightId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Return flight not found"));
            ensureBookable(returnFlight);
            if (!flight.originCode().equalsIgnoreCase(returnFlight.destinationCode())
                    || !flight.destinationCode().equalsIgnoreCase(returnFlight.originCode())
                    || !returnFlight.departure().isAfter(flight.arrival())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Return flight must be a later scheduled flight on the reverse route");
            }
        } else if (request.returnSeatNumbers() != null && !request.returnSeatNumbers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Return seats require a return flight");
        }

        List<PassengerInput> requestedPassengers = request.passengers();
        if (requestedPassengers == null || requestedPassengers.isEmpty()) {
            requestedPassengers = List.of(new PassengerInput(request.passengerName(), null, null));
        }
        if (requestedPassengers.size() > MAX_PASSENGERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A booking can include at most six passengers");
        }
        List<Passenger> passengers = requestedPassengers.stream().map(this::normalizePassenger).toList();
        List<String> seatNumbers = normalizeSeats(request.seatNumbers(), request.seatNumber(),
                passengers.size(), "outbound");
        List<String> returnSeatNumbers = returnFlight == null ? List.of()
                : normalizeSeats(request.returnSeatNumbers(), null, passengers.size(), "return");
        String travelClass = normalizeTravelClass(request.travelClass());
        boolean international = isInternational(flight);
        if (international) {
            validateTravelDocuments(passengers);
        }
        if (returnFlight != null && isInternational(returnFlight) != international) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Outbound and return flights must use the same domestic or international journey type");
        }
        if (returnFlight != null && !flight.currency().equals(returnFlight.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Outbound and return flights must use the same currency");
        }

        String bookingId = UUID.randomUUID().toString();
        List<ReservedSeat> reservedSeats = new ArrayList<>();
        try {
            seatInventory.ensureInitialized(flight);
            reserveSeats(flight.id(), seatNumbers, travelClass, bookingId, reservedSeats);
            if (returnFlight != null) {
                seatInventory.ensureInitialized(returnFlight);
                reserveSeats(returnFlight.id(), returnSeatNumbers, travelClass, bookingId, reservedSeats);
            }
        } catch (RuntimeException failure) {
            releaseSeats(reservedSeats, bookingId);
            throw failure;
        }

        BigDecimal baseFare = returnFlight == null ? flight.price()
                : flight.price().add(returnFlight.price());
        BigDecimal amount = farePolicies.calculate(travelClass, baseFare, passengers.size());
        TripDetails tripDetails = new TripDetails(returnFlight == null ? null : returnFlight.id(),
                seatNumbers, returnSeatNumbers, travelClass, passengers);
        Instant createdAt = Instant.now();
        Booking booking = new Booking(bookingId, UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                userId, flight.id(), seatNumbers.getFirst(), passengers.getFirst().name(),
                request.passengerEmail().trim().toLowerCase(), amount, flight.currency(),
                BookingStatus.PENDING_PAYMENT, null, null, createdAt, idempotencyKey, tripDetails,
                createdAt.plusSeconds(15 * 60L));
        try {
            return bookings.save(booking);
        } catch (DuplicateKeyException duplicate) {
            releaseSeats(reservedSeats, bookingId);
            return bookings.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> duplicate);
        } catch (RuntimeException failure) {
            releaseSeats(reservedSeats, bookingId);
            throw failure;
        }
    }

    public List<Booking> forUser(String userId) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Booking getForUser(String userId, String bookingId) {
        return bookings.findById(bookingId)
                .filter(booking -> booking.userId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    public Booking cancel(String userId, String bookingId) {
        Booking booking = getForUser(userId, bookingId);
        if (booking.status() == BookingStatus.CANCELLED) return booking;
        Flight flight = flights.findById(booking.flightId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        Flight returnFlight = booking.tripDetails() == null || booking.tripDetails().returnFlightId() == null
                ? null : flights.findById(booking.tripDetails().returnFlightId()).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Return flight not found"));
        if (!flight.departure().isAfter(Instant.now())
                || returnFlight != null && !returnFlight.departure().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A departed flight cannot be cancelled");
        }
        BookingStatus originalStatus = booking.status();
        if (originalStatus != BookingStatus.CONFIRMED && originalStatus != BookingStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is already being cancelled");
        }
        Booking cancelling = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(booking.id()).and("status").is(originalStatus)),
                Update.update("status", BookingStatus.CANCELLING),
                FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (cancelling == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has already changed");
        }
        if (originalStatus == BookingStatus.CONFIRMED) {
            try {
                payments.refund(booking);
            } catch (RuntimeException failure) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.id())
                                .and("status").is(BookingStatus.CANCELLING)),
                        Update.update("status", originalStatus), Booking.class);
                throw failure;
            }
        }
        Booking cancelled = bookings.save(cancelling.cancelled());
        List<String> outboundSeats = booking.tripDetails() == null || booking.tripDetails().seatNumbers().isEmpty()
                ? List.of(booking.seatNumber()) : booking.tripDetails().seatNumbers();
        outboundSeats.forEach(seat -> seatInventory.release(booking.flightId(), seat, booking.id()));
        if (returnFlight != null) {
            booking.tripDetails().returnSeatNumbers().forEach(seat ->
                    seatInventory.release(returnFlight.id(), seat, booking.id()));
        }
        return cancelled;
    }

    private void ensureBookable(Flight flight) {
        if (!"SCHEDULED".equals(flight.status()) || Boolean.TRUE.equals(flight.templateDisabled())
                || !flight.departure().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This flight is not available for booking");
        }
    }

    private void reserveSeats(String flightId, List<String> seats, String travelClass,
                              String reservationId, List<ReservedSeat> reservedSeats) {
        for (String seat : seats) {
            seatInventory.reserve(flightId, seat, travelClass, reservationId);
            reservedSeats.add(new ReservedSeat(flightId, seat));
        }
    }

    private List<String> normalizeSeats(List<String> requested, String primarySeat,
                                        int passengerCount, String leg) {
        List<String> seats = requested == null || requested.isEmpty()
                ? primarySeat == null ? List.of() : List.of(primarySeat)
                : requested.stream().map(seat -> seat == null ? "" : seat.trim().toUpperCase(Locale.ROOT)).toList();
        if (seats.size() != passengerCount || seats.stream().anyMatch(seat -> !SEAT_NUMBER.matcher(seat).matches())
                || seats.stream().distinct().count() != seats.size()
                || primarySeat != null && !seats.getFirst().equalsIgnoreCase(primarySeat.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose one unique " + leg + " seat per passenger");
        }
        return seats;
    }

    private Passenger normalizePassenger(PassengerInput passenger) {
        if (passenger == null || passenger.name() == null || passenger.name().isBlank()
                || passenger.name().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passenger names are required");
        }
        String nationality = passenger.nationality() == null ? ""
                : passenger.nationality().trim().toUpperCase(Locale.ROOT);
        if (!nationality.isBlank() && !nationality.matches("[A-Z]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nationality must be a two-letter country code");
        }
        return new Passenger(passenger.name().trim(), nationality.isBlank() ? null : nationality,
                passenger.passportExpiryDate());
    }

    private String normalizeTravelClass(String travelClass) {
        String normalized = travelClass == null || travelClass.isBlank()
                ? "ECONOMY" : travelClass.trim().toUpperCase(Locale.ROOT);
        farePolicies.resolve(normalized);
        return normalized;
    }

    private boolean isInternational(Flight flight) {
        Airport origin = airports.findById(flight.originCode()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Airport catalog data is required to book a flight"));
        Airport destination = airports.findById(flight.destinationCode()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Airport catalog data is required to book a flight"));
        if (origin.countryCode() == null || origin.countryCode().isBlank()
                || destination.countryCode() == null || destination.countryCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Airport country data is required to book a flight");
        }
        return !origin.countryCode().equalsIgnoreCase(destination.countryCode());
    }

    private void validateTravelDocuments(List<Passenger> passengers) {
        for (Passenger passenger : passengers) {
            if (passenger.nationality() == null || passenger.passportExpiryDate() == null
                    || !passenger.passportExpiryDate().isAfter(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "International demo bookings require each passenger's nationality and a future passport expiry date");
            }
        }
    }

    private void releaseSeats(List<ReservedSeat> seats, String reservationId) {
        seats.forEach(seat -> seatInventory.release(seat.flightId(), seat.seatNumber(), reservationId));
    }

    private record ReservedSeat(String flightId, String seatNumber) {}

    public record CreateBooking(String flightId, String seatNumber,
                                String passengerName, String passengerEmail, String returnFlightId,
                                List<String> seatNumbers, List<String> returnSeatNumbers,
                                List<PassengerInput> passengers, String travelClass) {}
    public record PassengerInput(String name, String nationality, LocalDate passportExpiryDate) {}
}
